package io.github.baba983.nativedo.topic;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Region;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.graphics.ColorUtils;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.RecyclerListView;

/**
 * The list a topic's posts are drawn in.
 *
 * <p>A {@link ChatMessageCell} builds the avatar's image but does not draw it: upstream's chat list
 * draws it after the row, so the avatar can sit at the bottom of a run of messages by one author and
 * overlap the next row. The position is therefore the row's business, and this list is where the row
 * finishes — the same place upstream does it, in `drawChild`.</p>
 *
 * <p>It is also where a leftward drag on a post answers it, ported from upstream's chat list
 * (`ChatActivity`'s `ChatListRecyclerView`: `processTouchEvent`, `drawReplyButton`, `onDraw`, and
 * the `slidingView` helpers): the dragged bubble slides left, upstream's reply disc is painted next
 * to it, and letting go past the threshold opens the composer for that post.</p>
 */
public class PostListView extends RecyclerListView {

    /** Where a row's author comes from; the screen owns the participants. */
    public interface AvatarSource {
        TLRPC.User user(long senderId);
    }

    /** What a completed leftward drag asks for: the post that was let go of. */
    public interface SwipeReplyListener {
        void onSwipeReply(ChatMessageCell cell);
    }

    private AvatarSource avatarSource;
    private TextSelectionHelper.ChatListTextSelectionHelper textSelectionHelper;
    private SwipeReplyListener swipeReplyListener;
    private java.util.function.Consumer<ChatMessageCell> searchHighlighter;

    public void setSearchHighlighter(java.util.function.Consumer<ChatMessageCell> highlighter) {
        searchHighlighter = highlighter;
        invalidateViews();
    }

    /**
     * The initials a row shows while its author's picture is on its way. The cell builds one of its
     * own but does not hand it out, and the picture is given to the image loader without it: the
     * first ask would leave the bubble's avatar without anything to draw. The colour and the letters
     * come from the same {@link AvatarDrawable} the cell uses, so what is drawn before the picture
     * arrives is what would have been drawn anyway.
     */
    private final java.util.HashMap<Long, org.telegram.ui.Components.AvatarDrawable> placeholders =
            new java.util.HashMap<>();

    // The swipe: whose gesture is being tracked, where it started and how far it has come. Ported
    // from ChatActivity's ChatListRecyclerView fields, same names and same units.
    private View slidingView;
    private boolean maybeStartTrackingSlidingView;
    private boolean startedTrackingSlidingView;
    private int startedTrackingPointerId;
    private int startedTrackingX;
    private int startedTrackingY;
    private long lastTrackingAnimationTime;
    private float trackAnimationProgress;
    private float endTrackingX;
    private boolean wasTrackingVibrate;

    /**
     * What the screen tells the list about the next-topic pull: whether the reader may make it now,
     * and what to do when one is let go past the threshold.
     */
    public interface NextTopicPullHost {
        /** Whether the reader may pull from the end of this list to another topic right now. */
        boolean canPullNextTopic();

        /** The reader let go past the threshold at the end of the list. */
        void onPullNextTopic();
    }

    /**
     * The pull the reader makes once the list is at the end of the topic's real stream: how far it
     * has come, in pixels, from the end. The screen reads it to draw the card, and the manager below
     * is the only thing that moves it (as the reference's own ListManager moves its chat).
     */
    private float pullingDownOffset;
    private ValueAnimator pullingDownBackAnimator;
    private NextTopicPullHost nextTopicPullHost;
    /**
     * The card the pull reveals. Drawn by this list's own {@code dispatchDraw}, after its children
     * (a card drawn in {@code onDraw} would sit under the post bubbles), so it repaints as the pull
     * grows. It cannot be drawn by the screen's frame instead: a {@code ViewGroup}'s own
     * {@code dispatchDraw} output is recorded in that parent's display list, and invalidating a
     * child does not rebuild it.
     */
    private final NextTopicPullDrawable pullDrawable = new NextTopicPullDrawable(this);

    /** The card the pull reveals; the screen names the candidate and drives its finish animation. */
    NextTopicPullDrawable pullDrawable() {
        return pullDrawable;
    }

    /**
     * Sets what the screen says about the pull. The list never guesses a candidate itself; the
     * screen owns the site's suggestion and the states that forbid the pull.
     */
    public void setNextTopicPullHost(NextTopicPullHost host) {
        nextTopicPullHost = host;
    }

    /** How far the pull from the end has come, in pixels; 0 when there is none. */
    public float getPullingDownOffset() {
        return pullingDownOffset;
    }

    /**
     * Puts the pull back to rest; the screen calls this when a release asked for the next topic but
     * the screen could not take it, so the card does not stay up over the list.
     */
    public void cancelNextTopicPull() {
        if (pullingDownOffset == 0) {
            return;
        }
        animatePullingDownBack();
    }

    /**
     * The list's manager: upstream's chat-list manager, plus the pull from the end of the topic's
     * stream. Mirrors {@code ChatActivity.ChatLayoutManager.scrollVerticallyBy} for the end of the
     * list instead of the top of a channel.
     */
    public androidx.recyclerview.widget.GridLayoutManagerFixed createChatLayoutManager(Context context) {
        return new EndOfListPullLayoutManager(context);
    }

    private class EndOfListPullLayoutManager
            extends androidx.recyclerview.widget.GridLayoutManagerFixed {

        EndOfListPullLayoutManager(Context context) {
            super(context, 1000, androidx.recyclerview.widget.LinearLayoutManager.VERTICAL, false);
        }

        @Override
        public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
            // A reverse drag while the pull is up gives the pull back first, before the list moves
            // (the reference's own rule, same direction).
            if (dy < 0 && pullingDownOffset != 0) {
                pullingDownOffset += dy;
                if (pullingDownOffset < 0) {
                    dy = (int) pullingDownOffset;
                    pullingDownOffset = 0;
                } else {
                    dy = 0;
                }
                PostListView.this.invalidate();
            }

            int scrolled = super.scrollVerticallyBy(dy, recycler, state);

            // Only an upward drag the list could not take - the real end, with the whole stream
            // loaded and nothing else claiming the gesture - grows the pull. The damping and the
            // 110dp/160dp ramp are the reference's.
            boolean allow = nextTopicPullHost != null && nextTopicPullHost.canPullNextTopic()
                    && PostListView.this.getScrollState() == RecyclerView.SCROLL_STATE_DRAGGING
                    && !hasTextSelection()
                    && !PostListView.this.canScrollVertically(1);
            if (allow && dy > 0 && scrolled == 0) {
                if (pullingDownBackAnimator != null) {
                    pullingDownBackAnimator.removeAllListeners();
                    pullingDownBackAnimator.cancel();
                }
                float k;
                if (pullingDownOffset < AndroidUtilities.dp(NextTopicPullDrawable.PULL_THRESHOLD_DP)) {
                    float progress = pullingDownOffset / AndroidUtilities.dp(NextTopicPullDrawable.PULL_THRESHOLD_DP);
                    k = 0.65f * (1f - progress) + 0.45f * progress;
                } else if (pullingDownOffset < AndroidUtilities.dp(160)) {
                    float progress = (pullingDownOffset - AndroidUtilities.dp(NextTopicPullDrawable.PULL_THRESHOLD_DP)) / AndroidUtilities.dp(50);
                    k = 0.45f * (1f - progress) + 0.05f * progress;
                } else {
                    k = 0.05f;
                }
                pullingDownOffset += dy * k;
                PostListView.this.invalidate();
            }

            // The reference's own overscroll change: while the pull is up the list must not draw its
            // own glow over the card.
            int overScroll = pullingDownOffset == 0
                    ? View.OVER_SCROLL_ALWAYS : View.OVER_SCROLL_NEVER;
            if (PostListView.this.getOverScrollMode() != overScroll) {
                PostListView.this.setOverScrollMode(overScroll);
            }
            return scrolled;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        if (e != null && pullingDownOffset != 0
                && (e.getActionMasked() == MotionEvent.ACTION_UP
                || e.getActionMasked() == MotionEvent.ACTION_CANCEL)) {
            onPullingDownGestureEnd(e.getActionMasked() == MotionEvent.ACTION_UP);
        }
        return super.dispatchTouchEvent(e);
    }

    /**
     * The release, ported from the reference's chat list {@code onTouchEvent}: a let-go past the
     * threshold with a candidate to go to asks the screen for it; anything under the threshold, and
     * every {@code ACTION_CANCEL}, only puts the pull back.
     */
    private void onPullingDownGestureEnd(boolean actionUp) {
        float progress = Math.min(1f, pullingDownOffset / AndroidUtilities.dp(NextTopicPullDrawable.PULL_THRESHOLD_DP));
        if (actionUp && progress >= 1f && nextTopicPullHost != null
                && nextTopicPullHost.canPullNextTopic()) {
            nextTopicPullHost.onPullNextTopic();
            return;
        }
        animatePullingDownBack();
    }

    private void animatePullingDownBack() {
        if (pullingDownBackAnimator != null) {
            pullingDownBackAnimator.removeAllListeners();
            pullingDownBackAnimator.cancel();
        }
        pullingDownBackAnimator = ValueAnimator.ofFloat(pullingDownOffset, 0f);
        pullingDownBackAnimator.addUpdateListener(animation -> {
            pullingDownOffset = (float) animation.getAnimatedValue();
            invalidate();
        });
        pullingDownBackAnimator.setDuration(200);
        pullingDownBackAnimator.setInterpolator(AndroidUtilities.decelerateInterpolator);
        pullingDownBackAnimator.start();
    }

    private float springMultiplier = 2000f;

    private Paint outlineActionBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint outlineActionBackgroundDarkenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private FloatValueHolder slidingDrawableVisibilityProgress = new FloatValueHolder(0);
    private SpringAnimation slidingDrawableVisibilitySpring = new SpringAnimation(slidingDrawableVisibilityProgress)
            .setMinValue(0f)
            .setMaxValue(springMultiplier)
            .setSpring(new SpringForce(0)
                    .setStiffness(SpringForce.STIFFNESS_MEDIUM)
                    .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY))
            .addUpdateListener((animation, value, velocity) -> invalidate());
    private FloatValueHolder slidingFillProgress = new FloatValueHolder(0);
    private SpringAnimation slidingFillProgressSpring = new SpringAnimation(slidingFillProgress)
            .setMinValue(0f)
            .setSpring(new SpringForce(0)
                    .setStiffness(400f)
                    .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY))
            .addUpdateListener((animation, value, velocity) -> invalidate());
    private FloatValueHolder slidingOuterRingProgress = new FloatValueHolder(0);
    private SpringAnimation slidingOuterRingSpring = new SpringAnimation(slidingOuterRingProgress)
            .setMinValue(0f)
            .setSpring(new SpringForce(0)
                    .setStiffness(200f)
                    .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY))
            .addUpdateListener((animation, value, velocity) -> invalidate());
    private boolean slidingBeyondMax;
    private Path path = new Path();

    public PostListView(Context context) {
        super(context);
        DefaultItemAnimator animator = new DefaultItemAnimator() {
            @Override
            protected void onAddAnimationUpdate(RecyclerView.ViewHolder holder) {
                // ChatMessageCell bakes alpha into its reply/reaction drawing. ViewPropertyAnimator
                // bypasses its setAlpha override, unlike upstream's chat-list ObjectAnimator.
                holder.itemView.invalidate();
            }
        };
        // A rich body has one attached cell. Asset rebinds must not cross-fade two owners of it.
        animator.setSupportsChangeAnimations(false);
        setItemAnimator(animator);
        outlineActionBackgroundPaint.setStyle(Paint.Style.STROKE);
        outlineActionBackgroundPaint.setStrokeCap(Paint.Cap.ROUND);
        outlineActionBackgroundPaint.setStrokeWidth(AndroidUtilities.dp(2));
        outlineActionBackgroundDarkenPaint.setStyle(Paint.Style.STROKE);
        outlineActionBackgroundDarkenPaint.setStrokeCap(Paint.Cap.ROUND);
        outlineActionBackgroundDarkenPaint.setStrokeWidth(AndroidUtilities.dp(2));
    }

    public void setAvatarSource(AvatarSource source) {
        avatarSource = source;
    }

    /** The selection a touch may cancel; the screen owns it, the list forwards the touches. */
    public void setTextSelectionHelper(TextSelectionHelper.ChatListTextSelectionHelper helper) {
        textSelectionHelper = helper;
    }

    /** What a completed leftward drag on a post reports. */
    public void setSwipeReplyListener(SwipeReplyListener listener) {
        swipeReplyListener = listener;
    }

    public boolean hasTextSelection() {
        return textSelectionHelper != null && textSelectionHelper.isInSelectionMode();
    }

    /**
     * A selection that starts takes the gesture over; the screen calls this from its selection
     * callback, the way ChatActivity's callback ends a sliding view the same way.
     */
    public void cancelSlidingView() {
        if (slidingView != null) {
            slidingViewSetOffset(0);
            slidingView = null;
        }
        maybeStartTrackingSlidingView = false;
        startedTrackingSlidingView = false;
    }

    /**
     * Upstream gates the vertical scroll while a slide is being tracked
     * (`chatLayoutManager.setCanScrollVertically`); this list's manager is the same upstream one.
     */
    private void setCanScrollVertically(boolean value) {
        if (getLayoutManager() instanceof androidx.recyclerview.widget.GridLayoutManagerFixed) {
            ((androidx.recyclerview.widget.GridLayoutManagerFixed) getLayoutManager()).setCanScrollVertically(value);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (textSelectionHelper != null) {
            textSelectionHelper.checkSelectionCancel(e);
        }
        boolean result = super.onInterceptTouchEvent(e);
        processTouchEvent(e);
        return result;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (textSelectionHelper != null) {
            textSelectionHelper.checkSelectionCancel(e);
        }
        boolean result = super.onTouchEvent(e);
        processTouchEvent(e);
        return startedTrackingSlidingView || result;
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
        if (slidingView != null) {
            processTouchEvent(null);
        }
    }

    /** The touch half of upstream's swipe: ChatActivity's chat list `processTouchEvent`. */
    private void processTouchEvent(MotionEvent e) {
        if (e != null && e.getAction() == MotionEvent.ACTION_DOWN && !startedTrackingSlidingView && !maybeStartTrackingSlidingView && slidingView == null) {
            View view = getPressedChildView();
            if (view instanceof ChatMessageCell) {
                if (slidingView != null) {
                    slidingViewSetOffset(0);
                }
                slidingView = view;
                MessageObject message = getSlidingMessageObject();
                if (
                    hasTextSelection() ||
                    message == null ||
                    message.isEphemeral() && message.isOut()
                ) {
                    slidingViewSetOffset(0);
                    slidingView = null;
                    return;
                }
                startedTrackingPointerId = e.getPointerId(0);
                maybeStartTrackingSlidingView = true;
                startedTrackingX = (int) e.getX();
                startedTrackingY = (int) e.getY();
            }
        } else if (slidingView != null && e != null && e.getAction() == MotionEvent.ACTION_MOVE && e.getPointerId(0) == startedTrackingPointerId) {
            int dx = Math.max(AndroidUtilities.dp(-80), Math.min(0, (int) (e.getX() - startedTrackingX)));
            int dy = Math.abs((int) e.getY() - startedTrackingY);
            if (getScrollState() == SCROLL_STATE_IDLE && maybeStartTrackingSlidingView && !startedTrackingSlidingView && dx <= -AndroidUtilities.getPixelsInCM(0.4f, true) && Math.abs(dx) / 3 > dy) {
                MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
                slidingView.onTouchEvent(event);
                super.onInterceptTouchEvent(event);
                event.recycle();
                setCanScrollVertically(false);
                maybeStartTrackingSlidingView = false;
                startedTrackingSlidingView = true;
                startedTrackingX = (int) e.getX();
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
            } else if (startedTrackingSlidingView) {
                if (Math.abs(dx) >= AndroidUtilities.dp(50)) {
                    if (!wasTrackingVibrate) {
                        try {
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                        } catch (Exception ignore) {}
                        wasTrackingVibrate = true;
                    }
                } else {
                    wasTrackingVibrate = false;
                }
                slidingViewSetOffset(dx);
                if (slidingView instanceof ChatMessageCell) {
                    setGroupTranslationX((ChatMessageCell) slidingView, dx);
                }
                invalidate();
            }
        } else if (slidingView != null && (e == null || e.getPointerId(0) == startedTrackingPointerId && (e.getAction() == MotionEvent.ACTION_CANCEL || e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_POINTER_UP))) {
            if (e != null && e.getAction() != MotionEvent.ACTION_CANCEL && Math.abs(getSlidingNonAnimationTranslationX(false)) >= AndroidUtilities.dp(50)) {
                if (swipeReplyListener != null && slidingView instanceof ChatMessageCell) {
                    swipeReplyListener.onSwipeReply((ChatMessageCell) slidingView);
                }
            }
            endTrackingX = slidingViewGetOffsetX();
            if (endTrackingX == 0) {
                slidingView = null;
            }
            lastTrackingAnimationTime = System.currentTimeMillis();
            trackAnimationProgress = 0.0f;
            invalidate();
            maybeStartTrackingSlidingView = false;
            startedTrackingSlidingView = false;
            setCanScrollVertically(true);
        }
    }

    private MessageObject getSlidingMessageObject() {
        if (slidingView instanceof ChatMessageCell) return ((ChatMessageCell) slidingView).getMessageObject();
        return null;
    }

    private float getSlidingNonAnimationTranslationX(boolean update) {
        if (slidingView instanceof ChatMessageCell) return ((ChatMessageCell) slidingView).getNonAnimationTranslationX(update);
        return 0;
    }

    private void slidingViewSetOffset(float offset) {
        if (slidingView instanceof ChatMessageCell) ((ChatMessageCell) slidingView).setSlidingOffset(offset);
    }

    private float slidingViewGetOffsetX() {
        if (slidingView instanceof ChatMessageCell) return ((ChatMessageCell) slidingView).getSlidingOffsetX();
        return 0;
    }

    private void setGroupTranslationX(ChatMessageCell view, float dx) {
        MessageObject.GroupedMessages group = view.getCurrentMessagesGroup();
        if (group == null) {
            return;
        }
        int count = getChildCount();
        for (int a = 0; a < count; a++) {
            View child = getChildAt(a);
            if (child == view || !(child instanceof ChatMessageCell)) {
                continue;
            }
            ChatMessageCell cell = (ChatMessageCell) child;
            if (cell.getCurrentMessagesGroup() == group) {
                cell.setSlidingOffset(dx);
                cell.invalidate();
            }
        }
        invalidate();
    }

    /** The release deceleration: ChatActivity's chat list `onDraw`. */
    @Override
    public void onDraw(Canvas c) {
        super.onDraw(c);
        if (slidingView != null) {
            float translationX = slidingViewGetOffsetX();
            if (!maybeStartTrackingSlidingView && !startedTrackingSlidingView && endTrackingX != 0 && translationX != 0) {
                long newTime = System.currentTimeMillis();
                long dt = newTime - lastTrackingAnimationTime;
                trackAnimationProgress += dt / 180.0f;
                if (trackAnimationProgress > 1.0f) {
                    trackAnimationProgress = 1.0f;
                }
                lastTrackingAnimationTime = newTime;
                translationX = endTrackingX * (1.0f - AndroidUtilities.decelerateInterpolator.getInterpolation(trackAnimationProgress));
                if (translationX == 0) {
                    endTrackingX = 0;
                }
                if (slidingView instanceof ChatMessageCell) {
                    setGroupTranslationX((ChatMessageCell) slidingView, translationX);
                }
                slidingViewSetOffset(translationX);
                if (trackAnimationProgress == 1f || trackAnimationProgress == 0f) {
                    slidingViewSetOffset(0);
                    slidingView = null;
                }
                invalidate();
            }
            drawReplyButton(c);
        }
    }

    /**
     * The card the pull reveals, over the children: a card drawn in {@code onDraw} would sit under
     * the post bubbles it is meant to cover, so it is drawn after they are (this list is the view
     * that repaints as the pull grows, which is also why the card is drawn here and not in the
     * screen's frame - a {@code ViewGroup}'s recorded display list is not rebuilt when a child is
     * invalidated).
     */
    @Override
    protected void dispatchDraw(Canvas canvas) {
        // ChatActivity lifts the messages with the pull, exposing the next-topic card below them.
        canvas.save();
        canvas.translate(0, -pullingDownOffset);
        super.dispatchDraw(canvas);
        canvas.restore();
        drawNextTopicPull(canvas);
    }

    /**
     * The reference's own placement above the composer: the list's own height less the band it
     * keeps for the composer.
     */
    private void drawNextTopicPull(Canvas canvas) {
        if (pullingDownOffset <= 0f) {
            pullDrawable.reset();
            return;
        }
        pullDrawable.setWidth(getWidth());
        float anchor = getHeight() - getPaddingBottom();
        float progress = Math.min(1f, pullingDownOffset / AndroidUtilities.dp(NextTopicPullDrawable.PULL_THRESHOLD_DP));
        pullDrawable.draw(canvas, this, anchor, progress, 1f);
    }

    /** The disc the drag paints: ChatActivity's chat list `drawReplyButton`. */
    private void drawReplyButton(Canvas canvas) {
        if (slidingView == null || Thread.currentThread() != Looper.getMainLooper().getThread()) {
            return;
        }
        Paint chatActionBackgroundPaint = Theme.getThemePaint(Theme.key_paint_chatActionBackground);
        Paint chatActionBackgroundDarkenPaint = Theme.chat_actionBackgroundGradientDarkenPaint;
        if (outlineActionBackgroundPaint.getColor() != chatActionBackgroundPaint.getColor()) {
            outlineActionBackgroundPaint.setColor(chatActionBackgroundPaint.getColor());
        }
        if (outlineActionBackgroundDarkenPaint.getColor() != chatActionBackgroundDarkenPaint.getColor()) {
            outlineActionBackgroundDarkenPaint.setColor(chatActionBackgroundDarkenPaint.getColor());
        }
        if (outlineActionBackgroundPaint.getShader() != chatActionBackgroundPaint.getShader()) {
            outlineActionBackgroundPaint.setShader(chatActionBackgroundPaint.getShader());
        }
        if (outlineActionBackgroundDarkenPaint.getShader() != chatActionBackgroundDarkenPaint.getShader()) {
            outlineActionBackgroundDarkenPaint.setShader(chatActionBackgroundDarkenPaint.getShader());
        }

        float fillProgress = slidingFillProgress.getValue() / springMultiplier;
        int wasDarkenColor = outlineActionBackgroundDarkenPaint.getColor();

        if (fillProgress > 1) {
            slidingBeyondMax = true;
        }

        float translationX = getSlidingNonAnimationTranslationX(false);
        if (slidingDrawableVisibilityProgress.getValue() == 0) {
            slidingFillProgressSpring.cancel();
            slidingFillProgressSpring.getSpring().setFinalPosition(0);
            slidingFillProgress.setValue(0f);
            slidingOuterRingSpring.cancel();
            slidingOuterRingSpring.getSpring().setFinalPosition(0);
            slidingOuterRingProgress.setValue(0f);
            slidingBeyondMax = false;
        }
        float progress;
        if (slidingFillProgressSpring.getSpring().getFinalPosition() != springMultiplier) {
            progress = androidx.core.math.MathUtils.clamp((-translationX - AndroidUtilities.dp(20)) / AndroidUtilities.dp(30), 0, 1);
        } else {
            progress = 1f;
        }

        if (progress == 1f && slidingFillProgressSpring.getSpring().getFinalPosition() != springMultiplier) {
            slidingFillProgressSpring.getSpring().setFinalPosition(springMultiplier);
            slidingFillProgressSpring.start();

            slidingOuterRingSpring.getSpring().setFinalPosition(springMultiplier);
            slidingOuterRingSpring.start();
        }

        boolean visible = translationX <= -AndroidUtilities.dp(20);
        float endVisibleValue = visible ? springMultiplier : 0;
        if (endVisibleValue != slidingDrawableVisibilitySpring.getSpring().getFinalPosition()) {
            slidingDrawableVisibilitySpring.getSpring().setFinalPosition(endVisibleValue);
            if (!slidingDrawableVisibilitySpring.isRunning()) {
                slidingDrawableVisibilitySpring.start();
            }
        }

        float iconProgress = slidingDrawableVisibilityProgress.getValue() / springMultiplier;
        MessageObject slidingMsg = getSlidingMessageObject();
        float x = getMeasuredWidth() + translationX * (slidingMsg != null && slidingMsg.isOut() ? 0.5f : 1f);
        float y = slidingView.getTop() + slidingView.getMeasuredHeight() / 2f;
        float scale = slidingBeyondMax ? fillProgress : iconProgress;

        float clearScale = slidingBeyondMax ? 0f : 1f - fillProgress;

        boolean isDark = ColorUtils.calculateLuminance(Theme.getColor(Theme.key_windowBackgroundWhite)) <= 0.5f;
        if (iconProgress != 0) {
            AndroidUtilities.rectTmp.set((int) (x - AndroidUtilities.dp(16) * scale + outlineActionBackgroundPaint.getStrokeWidth() / 2f), (int) (y - AndroidUtilities.dp(16) * scale + outlineActionBackgroundPaint.getStrokeWidth() / 2f), (int) (x + AndroidUtilities.dp(16) * scale - outlineActionBackgroundPaint.getStrokeWidth() / 2f), (int) (y + AndroidUtilities.dp(16) * scale - outlineActionBackgroundPaint.getStrokeWidth() / 2f));
            Theme.applyServiceShaderMatrix(getMeasuredWidth(), AndroidUtilities.displaySize.y, 0, getY() + AndroidUtilities.rectTmp.top);
            if (fillProgress == 0) {
                int outlineAlpha = outlineActionBackgroundPaint.getAlpha();
                outlineActionBackgroundPaint.setAlpha((int) (outlineAlpha * iconProgress));
                canvas.drawArc(AndroidUtilities.rectTmp, -90, 360 * progress, false, outlineActionBackgroundPaint);
                outlineActionBackgroundPaint.setAlpha(outlineAlpha);

                if (Theme.hasGradientService()) {
                    outlineAlpha = outlineActionBackgroundDarkenPaint.getAlpha();
                    if (isDark) {
                        outlineActionBackgroundDarkenPaint.setColor(Color.WHITE);
                    }
                    outlineActionBackgroundDarkenPaint.setAlpha((int) (outlineAlpha * iconProgress));
                    canvas.drawArc(AndroidUtilities.rectTmp, -90, 360 * progress, false, outlineActionBackgroundDarkenPaint);
                }
            }
        }
        AndroidUtilities.rectTmp.set((int) (x - AndroidUtilities.dp(16) * scale), (int) (y - AndroidUtilities.dp(16) * scale), (int) (x + AndroidUtilities.dp(16) * scale), (int) (y + AndroidUtilities.dp(16) * scale));
        Theme.applyServiceShaderMatrix(getMeasuredWidth(), AndroidUtilities.displaySize.y, 0, getY() + AndroidUtilities.rectTmp.top);
        path.rewind();
        path.addRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(16) * scale, AndroidUtilities.dp(16) * scale, Path.Direction.CW);

        int wasAlpha = chatActionBackgroundPaint.getAlpha();
        chatActionBackgroundPaint.setAlpha((int) (iconProgress * 0.6f * progress * wasAlpha));
        canvas.drawPath(path, chatActionBackgroundPaint);
        chatActionBackgroundPaint.setAlpha(wasAlpha);

        if (Theme.hasGradientService()) {
            wasAlpha = Theme.chat_actionBackgroundGradientDarkenPaint.getAlpha();
            if (isDark) {
                Theme.chat_actionBackgroundGradientDarkenPaint.setColor(Color.WHITE);
            }
            Theme.chat_actionBackgroundGradientDarkenPaint.setAlpha((int) (iconProgress * 0.6f * progress * wasAlpha));
            canvas.drawPath(path, Theme.chat_actionBackgroundGradientDarkenPaint);
            Theme.chat_actionBackgroundGradientDarkenPaint.setAlpha(wasAlpha);
        }

        if (clearScale != 0f) {
            AndroidUtilities.rectTmp.set((int) (x - AndroidUtilities.dp(16) * clearScale), (int) (y - AndroidUtilities.dp(16) * clearScale), (int) (x + AndroidUtilities.dp(16) * clearScale), (int) (y + AndroidUtilities.dp(16) * clearScale));
            path.rewind();
            path.addRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(16), AndroidUtilities.dp(16), Path.Direction.CW);

            canvas.save();
            canvas.clipPath(path, Region.Op.DIFFERENCE);
        }

        AndroidUtilities.rectTmp.set((int) (x - AndroidUtilities.dp(16) * scale), (int) (y - AndroidUtilities.dp(16) * scale), (int) (x + AndroidUtilities.dp(16) * scale), (int) (y + AndroidUtilities.dp(16) * scale));
        Theme.applyServiceShaderMatrix(getMeasuredWidth(), AndroidUtilities.displaySize.y, 0, getY() + AndroidUtilities.rectTmp.top);
        path.rewind();
        path.addRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(16) * scale, AndroidUtilities.dp(16) * scale, Path.Direction.CW);

        wasAlpha = chatActionBackgroundPaint.getAlpha();
        chatActionBackgroundPaint.setAlpha((int) (iconProgress * 0.4f * wasAlpha));
        canvas.drawPath(path, chatActionBackgroundPaint);
        chatActionBackgroundPaint.setAlpha(wasAlpha);

        if (Theme.hasGradientService()) {
            wasAlpha = Theme.chat_actionBackgroundGradientDarkenPaint.getAlpha();
            if (isDark) {
                Theme.chat_actionBackgroundGradientDarkenPaint.setColor(Color.WHITE);
            }
            Theme.chat_actionBackgroundGradientDarkenPaint.setAlpha((int) (iconProgress * 0.4f * wasAlpha));
            canvas.drawPath(path, Theme.chat_actionBackgroundGradientDarkenPaint);
            Theme.chat_actionBackgroundGradientDarkenPaint.setAlpha(wasAlpha);
        }
        if (clearScale != 0f) {
            canvas.restore();
        }

        float outerRingProgress = slidingOuterRingProgress.getValue() / springMultiplier;
        if (outerRingProgress != 0 && outerRingProgress != 1) {
            float outScale = 1f + outerRingProgress;

            float wasWidth = outlineActionBackgroundPaint.getStrokeWidth();
            float width = (1f - outerRingProgress) * wasWidth;
            if (width != 0f) {
                AndroidUtilities.rectTmp.set((int) (x - AndroidUtilities.dp(16) * outScale + width), (int) (y - AndroidUtilities.dp(16) * outScale + width), (int) (x + AndroidUtilities.dp(16) * outScale - width), (int) (y + AndroidUtilities.dp(16) * outScale - width));
                Theme.applyServiceShaderMatrix(getMeasuredWidth(), AndroidUtilities.displaySize.y, 0, getY() + AndroidUtilities.rectTmp.top);

                wasAlpha = outlineActionBackgroundPaint.getAlpha();
                outlineActionBackgroundPaint.setAlpha((int) (wasAlpha * iconProgress));

                outlineActionBackgroundPaint.setStrokeWidth(width);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(16) * outScale, AndroidUtilities.dp(16) * outScale, outlineActionBackgroundPaint);
                outlineActionBackgroundPaint.setStrokeWidth(wasWidth);

                outlineActionBackgroundPaint.setAlpha(wasAlpha);

                if (Theme.hasGradientService()) {
                    wasAlpha = outlineActionBackgroundDarkenPaint.getAlpha();
                    if (isDark) {
                        outlineActionBackgroundDarkenPaint.setColor(Color.WHITE);
                    }
                    outlineActionBackgroundDarkenPaint.setAlpha((int) (wasAlpha * iconProgress));

                    outlineActionBackgroundDarkenPaint.setStrokeWidth(width);
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(16) * outScale, AndroidUtilities.dp(16) * outScale, outlineActionBackgroundDarkenPaint);
                    outlineActionBackgroundDarkenPaint.setStrokeWidth(wasWidth);
                }
            }
        }

        int alpha = (int) (iconProgress * 0xFF);
        android.graphics.drawable.Drawable replyIconDrawable = Theme.getThemeDrawable(Theme.key_drawable_replyIcon);
        replyIconDrawable.setAlpha(alpha);
        replyIconDrawable.setBounds((int) (x - replyIconDrawable.getIntrinsicWidth() / 2 * scale), (int) (y - replyIconDrawable.getIntrinsicHeight() / 2 * scale), (int) (x + replyIconDrawable.getIntrinsicWidth() / 2 * scale), (int) (y + replyIconDrawable.getIntrinsicHeight() / 2 * scale));
        replyIconDrawable.draw(canvas);
        replyIconDrawable.setAlpha(255);

        outlineActionBackgroundDarkenPaint.setColor(wasDarkenColor);
        chatActionBackgroundDarkenPaint.setColor(wasDarkenColor);
    }

    @Override
    public boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (searchHighlighter != null && child instanceof ChatMessageCell) {
            searchHighlighter.accept((ChatMessageCell) child);
        }
        // Only the bubble at the bottom of a floor's run carries the author's avatar: a chat draws it
        // under a run of messages by one author, and a bubble the run pins to the one below it is
        // not that last bubble.
        boolean lastOfRun = child instanceof ChatMessageCell && !((ChatMessageCell) child).isPinnedBottom();
        if (lastOfRun) {
            ensureAvatar((ChatMessageCell) child);
        }
        boolean result = super.drawChild(canvas, child, drawingTime);
        if (lastOfRun) {
            drawAvatar(canvas, (ChatMessageCell) child);
        }
        return result;
    }

    /**
     * Points the row at the avatar that was downloaded for its author. The cell asks upstream for
     * the author's photo whenever it is bound or attached again, and that photo is a file location
     * nothing fetches from (there is no Telegram account behind it). What the image loader can read
     * is the file our client downloaded, named by its path — the same way a post's own images are
     * drawn — and it is handed over here, after the cell's own decisions, so nothing takes it back.
     */
    private void ensureAvatar(ChatMessageCell cell) {
        ImageReceiver avatar = cell.getAvatarImage();
        if (avatar == null || avatarSource == null || cell.getMessageObject() == null
                || cell.getMessageObject().messageOwner == null) {
            return;
        }
        // A picture is here (this draw finds it) or ours is already being read: nothing to hand over.
        if (avatar.getBitmap() != null || avatar.getImageDrawable() != null) {
            return;
        }
        // The cell's own ask is a file location with no path — the picture is named by where it was
        // downloaded to, and only a location that carries that path can be read.
        org.telegram.messenger.ImageLocation current = avatar.getImageLocation();
        if (current != null && current.path != null) {
            return;
        }
        TLRPC.User author = avatarSource.user(
                org.telegram.messenger.MessageObject.getFromChatId(cell.getMessageObject().messageOwner));
        java.io.File file = author == null ? null : DiscourseAvatars.INSTANCE.localFile(author);
        if (file == null) {
            return;
        }
        avatar.setImage(
                org.telegram.messenger.ImageLocation.getForPath(file.getAbsolutePath()),
                "50_50", placeholder(avatar, author), null, null, 0);
    }

    /**
     * What the spot shows until the picture is read: the initials the cell itself put there, or one
     * of our own when it has none — the hand-over would otherwise leave the spot empty.
     */
    private org.telegram.ui.Components.AvatarDrawable placeholder(ImageReceiver avatar, TLRPC.User author) {
        if (avatar.getDrawable() instanceof org.telegram.ui.Components.AvatarDrawable) {
            return (org.telegram.ui.Components.AvatarDrawable) avatar.getDrawable();
        }
        org.telegram.ui.Components.AvatarDrawable drawable = placeholders.get(author.id);
        if (drawable == null) {
            drawable = new org.telegram.ui.Components.AvatarDrawable();
            drawable.setInfo(0, author);
            placeholders.put(author.id, drawable);
        }
        return drawable;
    }

    /**
     * Upstream's own placement: the avatar hangs below the bubble's last line, never above the
     * row's top and never below the row's bottom, so it stays inside the row it belongs to.
     */
    private void drawAvatar(Canvas canvas, ChatMessageCell cell) {
        ImageReceiver avatar = cell.getAvatarImage();
        if (avatar == null) {
            return;
        }
        int top = childTop(cell);
        int y = top + (int) cell.getPaddingTopAnimated() + cell.getLayoutHeight();
        y += (int) cell.getTransitionParams().deltaBottom;
        int cellBottom = (int) (cell.getY() + cell.getMeasuredHeight());
        if (y > cellBottom) {
            y = cellBottom;
        }
        if (y - AndroidUtilities.dp(48) < top) {
            y = top + AndroidUtilities.dp(48);
        }
        avatar.setImageY(y - AndroidUtilities.dp(44));
        avatar.setVisible(true, false);
        avatar.setAlpha(1f);
        // Upstream's chat list fades a new avatar in, and the fade is the only thing that ever
        // raises the image's own alpha: without it the cell draws the author's initials forever,
        // because this list draws a settled avatar rather than running that animation.
        avatar.setCurrentAlpha(1f);
        avatar.draw(canvas);
    }

    private int childTop(View child) {
        return (int) child.getY() + child.getPaddingTop();
    }
}
