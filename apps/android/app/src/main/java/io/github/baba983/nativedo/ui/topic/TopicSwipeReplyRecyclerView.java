/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Native port of ChatActivity's swipe-to-reply ChatListRecyclerView subsystem
 * from TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java.
 */
package io.github.baba983.nativedo.ui.topic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import io.github.baba983.nativedo.core.model.Post;
import io.github.baba983.nativedo.ui.topic.cell.TopicPostCell;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.RecyclerListView;

public class TopicSwipeReplyRecyclerView extends RecyclerListView {

    public static class SwipeReplyLayoutManager extends LinearLayoutManager {
        private boolean canScrollVertically = true;

        public SwipeReplyLayoutManager(Context context) {
            super(context);
        }

        public void setCanScrollVertically(boolean canScrollVertically) {
            this.canScrollVertically = canScrollVertically;
        }

        boolean isSwipeReplyVerticalScrollEnabled() {
            return canScrollVertically;
        }

        @Override
        public boolean canScrollVertically() {
            return canScrollVertically && super.canScrollVertically();
        }
    }

    public interface Delegate {
        boolean canStartSwipeReply();
        void onSwipeReply(@NonNull TopicPostCell cell, @NonNull Post post);
    }

    private static final float SPRING_MULTIPLIER = 2000f;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final Drawable replyIcon;

    private final FloatValueHolder slidingDrawableVisibilityProgress = new FloatValueHolder(0);
    private final SpringAnimation slidingDrawableVisibilitySpring = new SpringAnimation(slidingDrawableVisibilityProgress)
            .setMinValue(0f)
            .setMaxValue(SPRING_MULTIPLIER)
            .setSpring(new SpringForce(0)
                    .setStiffness(SpringForce.STIFFNESS_MEDIUM)
                    .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY))
            .addUpdateListener((animation, value, velocity) -> invalidate());
    private final FloatValueHolder slidingFillProgress = new FloatValueHolder(0);
    private final SpringAnimation slidingFillProgressSpring = new SpringAnimation(slidingFillProgress)
            .setMinValue(0f)
            .setSpring(new SpringForce(0)
                    .setStiffness(400f)
                    .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY))
            .addUpdateListener((animation, value, velocity) -> invalidate());
    private final FloatValueHolder slidingOuterRingProgress = new FloatValueHolder(0);
    private final SpringAnimation slidingOuterRingSpring = new SpringAnimation(slidingOuterRingProgress)
            .setMinValue(0f)
            .setSpring(new SpringForce(0)
                    .setStiffness(200f)
                    .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY))
            .addUpdateListener((animation, value, velocity) -> invalidate());

    private Delegate delegate;
    private View scrimmedChild;
    private TopicPostCell slidingView;
    private long expectedPostId = Long.MIN_VALUE;
    private boolean maybeStartTrackingSlidingView;
    private boolean startedTrackingSlidingView;
    private int startedTrackingX;
    private int startedTrackingY;
    private int startedTrackingPointerId;
    private long lastTrackingAnimationTime;
    private float trackAnimationProgress;
    private float endTrackingX;
    private boolean wasTrackingVibrate;
    private boolean slidingBeyondMax;

    public TopicSwipeReplyRecyclerView(Context context) {
        this(context, context.getResources().getDrawable(R.drawable.filled_button_reply).mutate());
    }

    TopicSwipeReplyRecyclerView(Context context, Drawable replyIcon) {
        super(context);
        setWillNotDraw(false);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeCap(Paint.Cap.ROUND);
        outlinePaint.setStrokeWidth(AndroidUtilities.dp(2f));
        this.replyIcon = replyIcon;
    }

    public boolean drawChildForBlur(@NonNull Canvas canvas, @NonNull View child, long drawingTime) {
        return super.drawChild(canvas, child, drawingTime);
    }

    public void setSwipeReplyDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    /**
     * Matches ChatActivity's selected-message drawing order: the list leaves the
     * selected child out of its regular pass, then the host draws it above the
     * full-screen scrim.
     */
    public void setScrimmedChild(@Nullable View child) {
        if (scrimmedChild == child) {
            return;
        }
        scrimmedChild = child;
        invalidate();
    }

    @Nullable
    View getScrimmedChild() {
        return scrimmedChild;
    }

    boolean drawScrimmedChild(Canvas canvas, View child) {
        if (child == null || child != scrimmedChild || child.getParent() != this) {
            return false;
        }
        if (child instanceof TopicPostCell) {
            ((TopicPostCell) child).setScrimForegroundDrawing(true);
            try {
                return super.drawChild(canvas, child, getDrawingTime());
            } finally {
                ((TopicPostCell) child).setScrimForegroundDrawing(false);
            }
        }
        return super.drawChild(canvas, child, getDrawingTime());
    }

    public boolean isTrackingSwipeReply() {
        return maybeStartTrackingSlidingView || startedTrackingSlidingView || slidingView != null;
    }

    public void cancelSwipeReply(boolean animated) {
        setCanScrollVertically(true);
        if (slidingView == null) {
            clearTrackingFlags();
            return;
        }
        if (animated && slidingView.getSlidingOffsetX() != 0f) {
            endTrackingX = slidingView.getSlidingOffsetX();
            lastTrackingAnimationTime = System.currentTimeMillis();
            trackAnimationProgress = 0f;
            maybeStartTrackingSlidingView = false;
            startedTrackingSlidingView = false;
            invalidate();
        } else {
            slidingView.setSlidingOffset(0f);
            slidingView = null;
            expectedPostId = Long.MIN_VALUE;
            endTrackingX = 0f;
            clearTrackingFlags();
            resetSprings();
            invalidate();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        boolean result = super.onInterceptTouchEvent(event);
        processTouchEvent(event);
        return result;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean result = super.onTouchEvent(event);
        processTouchEvent(event);
        return startedTrackingSlidingView || result;
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
        if (disallowIntercept && slidingView != null) {
            processTouchEvent(null);
        }
    }

    @Override
    public void onChildDetachedFromWindow(@NonNull View child) {
        if (child == scrimmedChild) {
            setScrimmedChild(null);
        }
        if (child == slidingView) {
            cancelSwipeReply(false);
        }
        super.onChildDetachedFromWindow(child);
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelSwipeReply(false);
        super.onDetachedFromWindow();
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (slidingView != null) {
            animateSlidingViewBack();
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        drawReplyButton(canvas);
    }

    @Override
    public boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == scrimmedChild) {
            return false;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    private void processTouchEvent(MotionEvent event) {
        if (event != null && event.getActionMasked() == MotionEvent.ACTION_DOWN &&
                !startedTrackingSlidingView && !maybeStartTrackingSlidingView && slidingView == null) {
            if (delegate == null || !delegate.canStartSwipeReply()) {
                return;
            }
            View view = findChildViewUnder(event.getX(), event.getY());
            if (!(view instanceof TopicPostCell)) {
                return;
            }
            TopicPostCell cell = (TopicPostCell) view;
            Post post = cell.getBoundPost();
            if (post == null || cell.isSwipeReplyGestureBlockedAt(
                    event.getX() - cell.getX(),
                    event.getY() - cell.getY())) {
                return;
            }
            slidingView = cell;
            expectedPostId = post.getId();
            startedTrackingPointerId = event.getPointerId(0);
            maybeStartTrackingSlidingView = true;
            startedTrackingX = (int) event.getX();
            startedTrackingY = (int) event.getY();
            return;
        }

        if (slidingView != null && event != null && event.getActionMasked() == MotionEvent.ACTION_MOVE &&
                event.getPointerId(0) == startedTrackingPointerId) {
            int dx = clampSwipeDx((int) event.getX() - startedTrackingX);
            int dy = Math.abs((int) event.getY() - startedTrackingY);
            if (shouldStartTrackingSwipe(
                    dx,
                    dy,
                    getScrollState() == RecyclerView.SCROLL_STATE_IDLE,
                    maybeStartTrackingSlidingView,
                    startedTrackingSlidingView)) {
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                slidingView.dispatchTouchEvent(cancel);
                super.onInterceptTouchEvent(cancel);
                cancel.recycle();
                stopScroll();
                setCanScrollVertically(false);
                maybeStartTrackingSlidingView = false;
                startedTrackingSlidingView = true;
                startedTrackingX = (int) event.getX();
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
            } else if (startedTrackingSlidingView) {
                if (hasReachedReplyThreshold(dx)) {
                    if (!wasTrackingVibrate) {
                        try {
                            performHapticFeedback(
                                    HapticFeedbackConstants.KEYBOARD_TAP,
                                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                        } catch (Exception ignore) {
                        }
                        wasTrackingVibrate = true;
                    }
                } else {
                    wasTrackingVibrate = false;
                }
                slidingView.setSlidingOffset(dx);
                invalidate();
            }
            return;
        }

        if (slidingView != null && (event == null ||
                (maybeStartTrackingSlidingView || startedTrackingSlidingView) &&
                event.getPointerId(0) == startedTrackingPointerId &&
                        (event.getActionMasked() == MotionEvent.ACTION_CANCEL ||
                                event.getActionMasked() == MotionEvent.ACTION_UP ||
                                event.getActionMasked() == MotionEvent.ACTION_POINTER_UP))) {
            TopicPostCell cell = slidingView;
            Post post = cell.getBoundPost();
            boolean shouldReply = event != null && event.getActionMasked() != MotionEvent.ACTION_CANCEL &&
                    hasReachedReplyThreshold(cell.getNonAnimationTranslationX(false)) &&
                    cell.isAttachedToWindow() && post != null && post.getId() == expectedPostId &&
                    delegate != null && delegate.canStartSwipeReply();
            if (shouldReply) {
                delegate.onSwipeReply(cell, post);
            }
            expectedPostId = Long.MIN_VALUE;
            endTrackingX = cell.getSlidingOffsetX();
            if (endTrackingX == 0f) {
                slidingView = null;
            }
            lastTrackingAnimationTime = System.currentTimeMillis();
            trackAnimationProgress = 0f;
            clearTrackingFlags();
            setCanScrollVertically(true);
            invalidate();
        }
    }

    private void animateSlidingViewBack() {
        if (slidingView == null || maybeStartTrackingSlidingView || startedTrackingSlidingView ||
                endTrackingX == 0f || slidingView.getSlidingOffsetX() == 0f) {
            return;
        }
        long now = System.currentTimeMillis();
        trackAnimationProgress += (now - lastTrackingAnimationTime) / 180f;
        trackAnimationProgress = Math.min(1f, trackAnimationProgress);
        lastTrackingAnimationTime = now;
        float translationX = endTrackingX *
                (1f - AndroidUtilities.decelerateInterpolator.getInterpolation(trackAnimationProgress));
        slidingView.setSlidingOffset(translationX);
        if (trackAnimationProgress == 1f || translationX == 0f) {
            slidingView.setSlidingOffset(0f);
            slidingView = null;
            expectedPostId = Long.MIN_VALUE;
            endTrackingX = 0f;
        }
        invalidate();
    }

    private void drawReplyButton(Canvas canvas) {
        if (slidingView == null) {
            return;
        }
        Paint actionBackgroundPaint = Theme.getThemePaint(Theme.key_paint_chatActionBackground);
        fillPaint.setColor(actionBackgroundPaint.getColor());
        fillPaint.setShader(actionBackgroundPaint.getShader());
        outlinePaint.setColor(actionBackgroundPaint.getColor());
        outlinePaint.setShader(actionBackgroundPaint.getShader());

        float fillProgress = slidingFillProgress.getValue() / SPRING_MULTIPLIER;
        if (fillProgress > 1f) {
            slidingBeyondMax = true;
        }
        float translationX = slidingView.getNonAnimationTranslationX(false);
        if (slidingDrawableVisibilityProgress.getValue() == 0f) {
            slidingFillProgressSpring.cancel();
            slidingFillProgressSpring.getSpring().setFinalPosition(0f);
            slidingFillProgress.setValue(0f);
            slidingOuterRingSpring.cancel();
            slidingOuterRingSpring.getSpring().setFinalPosition(0f);
            slidingOuterRingProgress.setValue(0f);
            slidingBeyondMax = false;
        }

        float progress = slidingFillProgressSpring.getSpring().getFinalPosition() != SPRING_MULTIPLIER
                ? clamp((-translationX - AndroidUtilities.dp(20f)) / AndroidUtilities.dp(30f), 0f, 1f)
                : 1f;
        if (progress == 1f && slidingFillProgressSpring.getSpring().getFinalPosition() != SPRING_MULTIPLIER) {
            slidingFillProgressSpring.getSpring().setFinalPosition(SPRING_MULTIPLIER);
            slidingFillProgressSpring.start();
            slidingOuterRingSpring.getSpring().setFinalPosition(SPRING_MULTIPLIER);
            slidingOuterRingSpring.start();
        }

        boolean visible = translationX <= -AndroidUtilities.dp(20f);
        float visibilityTarget = visible ? SPRING_MULTIPLIER : 0f;
        if (visibilityTarget != slidingDrawableVisibilitySpring.getSpring().getFinalPosition()) {
            slidingDrawableVisibilitySpring.getSpring().setFinalPosition(visibilityTarget);
            if (!slidingDrawableVisibilitySpring.isRunning()) {
                slidingDrawableVisibilitySpring.start();
            }
        }

        float iconProgress = slidingDrawableVisibilityProgress.getValue() / SPRING_MULTIPLIER;
        float scale = slidingBeyondMax ? fillProgress : iconProgress;
        float x = getMeasuredWidth() + translationX;
        float y = slidingView.getTop() + slidingView.getMeasuredHeight() / 2f;
        float radius = AndroidUtilities.dp(16f) * Math.max(0f, scale);
        float clearScale = slidingBeyondMax ? 0f : 1f - fillProgress;

        if (iconProgress != 0f) {
            rect.set(x - radius, y - radius, x + radius, y + radius);
            int oldOutlineAlpha = outlinePaint.getAlpha();
            if (fillProgress == 0f) {
                outlinePaint.setAlpha((int) (oldOutlineAlpha * iconProgress));
                canvas.drawArc(rect, -90f, 360f * progress, false, outlinePaint);
            }
            outlinePaint.setAlpha(oldOutlineAlpha);

            path.rewind();
            path.addRoundRect(rect, radius, radius, Path.Direction.CW);
            int oldFillAlpha = fillPaint.getAlpha();
            fillPaint.setAlpha((int) (oldFillAlpha * iconProgress * 0.6f * progress));
            canvas.drawPath(path, fillPaint);
            fillPaint.setAlpha(oldFillAlpha);

            if (clearScale != 0f) {
                rect.set(
                        x - AndroidUtilities.dp(16f) * clearScale,
                        y - AndroidUtilities.dp(16f) * clearScale,
                        x + AndroidUtilities.dp(16f) * clearScale,
                        y + AndroidUtilities.dp(16f) * clearScale);
                path.rewind();
                path.addRoundRect(rect, AndroidUtilities.dp(16f), AndroidUtilities.dp(16f), Path.Direction.CW);
                canvas.save();
                canvas.clipPath(path, Region.Op.DIFFERENCE);
            }

            rect.set(x - radius, y - radius, x + radius, y + radius);
            path.rewind();
            path.addRoundRect(rect, radius, radius, Path.Direction.CW);
            fillPaint.setAlpha((int) (oldFillAlpha * iconProgress * 0.4f));
            canvas.drawPath(path, fillPaint);
            fillPaint.setAlpha(oldFillAlpha);

            if (clearScale != 0f) {
                canvas.restore();
            }

            float outerProgress = slidingOuterRingProgress.getValue() / SPRING_MULTIPLIER;
            if (outerProgress > 0f && outerProgress < 1f) {
                float outerScale = 1f + outerProgress;
                float strokeWidth = (1f - outerProgress) * AndroidUtilities.dp(2f);
                if (strokeWidth > 0f) {
                    float oldWidth = outlinePaint.getStrokeWidth();
                    outlinePaint.setStrokeWidth(strokeWidth);
                    float outerRadius = AndroidUtilities.dp(16f) * outerScale;
                    rect.set(x - outerRadius, y - outerRadius, x + outerRadius, y + outerRadius);
                    canvas.drawRoundRect(rect, outerRadius, outerRadius, outlinePaint);
                    outlinePaint.setStrokeWidth(oldWidth);
                }
            }

            int halfWidth = (int) (replyIcon.getIntrinsicWidth() * scale / 2f);
            int halfHeight = (int) (replyIcon.getIntrinsicHeight() * scale / 2f);
            replyIcon.setColorFilter(new PorterDuffColorFilter(
                    Theme.getColor(Theme.key_chat_serviceIcon), PorterDuff.Mode.MULTIPLY));
            replyIcon.setAlpha((int) (255f * iconProgress));
            replyIcon.setBounds((int) x - halfWidth, (int) y - halfHeight,
                    (int) x + halfWidth, (int) y + halfHeight);
            replyIcon.draw(canvas);
            replyIcon.setAlpha(255);
        }
    }

    private void clearTrackingFlags() {
        maybeStartTrackingSlidingView = false;
        startedTrackingSlidingView = false;
        wasTrackingVibrate = false;
    }

    private void setCanScrollVertically(boolean canScrollVertically) {
        RecyclerView.LayoutManager layoutManager = getLayoutManager();
        if (layoutManager instanceof SwipeReplyLayoutManager) {
            ((SwipeReplyLayoutManager) layoutManager).setCanScrollVertically(canScrollVertically);
        }
    }

    private void resetSprings() {
        slidingDrawableVisibilitySpring.cancel();
        slidingFillProgressSpring.cancel();
        slidingOuterRingSpring.cancel();
        slidingDrawableVisibilityProgress.setValue(0f);
        slidingFillProgress.setValue(0f);
        slidingOuterRingProgress.setValue(0f);
        slidingDrawableVisibilitySpring.getSpring().setFinalPosition(0f);
        slidingFillProgressSpring.getSpring().setFinalPosition(0f);
        slidingOuterRingSpring.getSpring().setFinalPosition(0f);
        slidingBeyondMax = false;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    static int clampSwipeDx(int dx) {
        return Math.max(AndroidUtilities.dp(-80f), Math.min(0, dx));
    }

    static boolean shouldStartTrackingSwipe(
            int dx,
            int dy,
            boolean scrollIdle,
            boolean maybeTracking,
            boolean alreadyTracking) {
        return scrollIdle && maybeTracking && !alreadyTracking &&
                dx <= -AndroidUtilities.getPixelsInCM(0.4f, true) && Math.abs(dx) / 3 > dy;
    }

    static boolean hasReachedReplyThreshold(float offsetX) {
        return Math.abs(offsetX) >= AndroidUtilities.dp(50f);
    }
}
