/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Native port of the selected-message scrim host in
 * TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java.
 */
package io.github.baba983.nativedo.ui.topic;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

import io.github.baba983.nativedo.core.model.Post;
import io.github.baba983.nativedo.ui.topic.cell.TopicPostCell;

import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

public class TopicPostScrimHostLayout extends SizeNotifierFrameLayout {

    public interface ScrimProgressListener {
        void onScrimProgress(float progress);
    }

    private static final float MAX_SCRIM_ALPHA = 0.2f;
    private static final long SCRIM_ANIMATION_DURATION = 320L;

    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] hostLocation = new int[2];
    private final int[] viewLocation = new int[2];
    private final Rect scrimViewport = new Rect();

    private View scrimView;
    private TopicSwipeReplyRecyclerView scrimmedRecyclerView;
    private Object scrimOwner;
    private long expectedPostId = Long.MIN_VALUE;
    private boolean expectedTopicPostCell;
    private float scrimAlpha;
    private ValueAnimator scrimAnimator;
    private View viewportView;
    private View topOccluder;
    private View bottomOccluder;
    private Runnable onScrimStateChanged;
    private ScrimProgressListener scrimProgressListener;
    private boolean invalidScrimClearPosted;

    public TopicPostScrimHostLayout(Context context) {
        super(context);
        scrimPaint.setColor(Color.BLACK);
        setWillNotDraw(false);
    }

    public void setScrimViewport(View viewportView, View topOccluder, View bottomOccluder) {
        this.viewportView = viewportView;
        this.topOccluder = topOccluder;
        this.bottomOccluder = bottomOccluder;
        invalidate();
    }

    public void setOnScrimStateChanged(Runnable listener) {
        onScrimStateChanged = listener;
    }

    public void setScrimProgressListener(ScrimProgressListener listener) {
        scrimProgressListener = listener;
        notifyScrimProgress();
    }

    public boolean isScrimActive() {
        return scrimView != null;
    }

    public void showScrim(View view, Object owner, boolean animated) {
        if (view == null || owner == null) {
            return;
        }
        if (view instanceof TopicPostCell && ((TopicPostCell) view).getBoundPost() == null) {
            return;
        }
        boolean wasActive = scrimView != null;
        setScrimView(view, owner);
        animateScrimTo(MAX_SCRIM_ALPHA, animated, false);
        if (!wasActive) {
            notifyScrimStateChanged();
        }
    }

    public void hideScrim(Object owner, boolean animated) {
        if (owner == null || scrimOwner != owner || scrimView == null) {
            return;
        }
        animateScrimTo(0f, animated, true);
    }

    public void clearScrim() {
        cancelScrimAnimator();
        scrimAlpha = 0f;
        notifyScrimProgress();
        clearScrimView();
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (scrimView == null) {
            return;
        }
        if (!isScrimViewValid()) {
            postClearInvalidScrim();
            return;
        }

        scrimPaint.setAlpha((int) (255f * scrimAlpha));
        if (scrimPaint.getAlpha() > 0) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), scrimPaint);
        }

        if (!resolveScrimViewport(scrimViewport) || scrimViewport.isEmpty()) {
            return;
        }
        int save = canvas.save();
        canvas.clipRect(scrimViewport);
        boolean drewThroughRecyclerView = false;
        if (scrimmedRecyclerView != null && scrimView.getParent() == scrimmedRecyclerView) {
            int recyclerSave = canvas.save();
            getLocationInWindow(hostLocation);
            scrimmedRecyclerView.getLocationInWindow(viewLocation);
            canvas.translate(viewLocation[0] - hostLocation[0], viewLocation[1] - hostLocation[1]);
            drewThroughRecyclerView = scrimmedRecyclerView.drawScrimmedChild(canvas, scrimView);
            canvas.restoreToCount(recyclerSave);
        }
        if (!drewThroughRecyclerView) {
            drawScrimViewDirectly(canvas);
        }
        canvas.restoreToCount(save);
    }

    private void drawScrimViewDirectly(Canvas canvas) {
        getLocationInWindow(hostLocation);
        scrimView.getLocationInWindow(viewLocation);
        canvas.translate(viewLocation[0] - hostLocation[0], viewLocation[1] - hostLocation[1]);
        scrimView.draw(canvas);
    }

    boolean resolveScrimViewport(Rect out) {
        if (out == null || viewportView == null || !viewportView.isAttachedToWindow()) {
            return false;
        }
        getLocationInWindow(hostLocation);
        viewportView.getLocationInWindow(viewLocation);
        int left = viewLocation[0] - hostLocation[0];
        int top = viewLocation[1] - hostLocation[1];
        int right = left + viewportView.getWidth();
        int bottom = top + viewportView.getHeight();

        if (topOccluder != null && topOccluder.isAttachedToWindow() && topOccluder.getVisibility() == VISIBLE) {
            topOccluder.getLocationInWindow(viewLocation);
            top = Math.max(top, viewLocation[1] - hostLocation[1] + topOccluder.getHeight());
        }
        if (bottomOccluder != null && bottomOccluder.isAttachedToWindow() && bottomOccluder.getVisibility() == VISIBLE) {
            bottomOccluder.getLocationInWindow(viewLocation);
            bottom = Math.min(bottom, viewLocation[1] - hostLocation[1]);
        }
        out.set(left, top, right, bottom);
        return true;
    }

    private void setScrimView(View view, Object owner) {
        if (scrimView == view && scrimOwner == owner) {
            return;
        }
        if (scrimView instanceof TopicPostCell) {
            ((TopicPostCell) scrimView).setInvalidatesParent(false);
        }
        scrimView = view;
        setScrimmedRecyclerChild(view);
        scrimOwner = owner;
        expectedTopicPostCell = view instanceof TopicPostCell;
        expectedPostId = boundPostId(view);
        if (view instanceof TopicPostCell) {
            ((TopicPostCell) view).setInvalidatesParent(true);
        }
        invalidate();
    }

    private boolean isScrimViewValid() {
        if (scrimView == null || !scrimView.isAttachedToWindow()) {
            return false;
        }
        return !expectedTopicPostCell || expectedPostId == boundPostId(scrimView);
    }

    private static long boundPostId(View view) {
        if (!(view instanceof TopicPostCell)) {
            return Long.MIN_VALUE;
        }
        Post post = ((TopicPostCell) view).getBoundPost();
        return post != null ? post.getId() : Long.MIN_VALUE;
    }

    private void animateScrimTo(float target, boolean animated, boolean clearAfter) {
        cancelScrimAnimator();
        if (!animated) {
            scrimAlpha = target;
            notifyScrimProgress();
            if (clearAfter) {
                clearScrimView();
            }
            invalidate();
            return;
        }
        scrimAnimator = ValueAnimator.ofFloat(scrimAlpha, target);
        scrimAnimator.addUpdateListener(animation -> {
            scrimAlpha = (float) animation.getAnimatedValue();
            notifyScrimProgress();
            invalidate();
        });
        scrimAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        scrimAnimator.setDuration(SCRIM_ANIMATION_DURATION);
        scrimAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!cancelled && clearAfter && scrimAlpha == 0f) {
                    clearScrimView();
                    invalidate();
                }
                if (scrimAnimator == animation) {
                    scrimAnimator = null;
                }
            }
        });
        scrimAnimator.start();
    }

    private void cancelScrimAnimator() {
        if (scrimAnimator != null) {
            scrimAnimator.cancel();
            scrimAnimator = null;
        }
    }

    private void clearScrimView() {
        boolean wasActive = scrimView != null;
        if (scrimmedRecyclerView != null) {
            scrimmedRecyclerView.setScrimmedChild(null);
            scrimmedRecyclerView = null;
        }
        if (scrimView instanceof TopicPostCell) {
            ((TopicPostCell) scrimView).setInvalidatesParent(false);
            scrimView.invalidate();
        }
        scrimView = null;
        scrimOwner = null;
        expectedPostId = Long.MIN_VALUE;
        expectedTopicPostCell = false;
        invalidScrimClearPosted = false;
        if (wasActive) {
            notifyScrimStateChanged();
        }
    }

    private void setScrimmedRecyclerChild(View view) {
        TopicSwipeReplyRecyclerView nextRecyclerView = view.getParent() instanceof TopicSwipeReplyRecyclerView
                ? (TopicSwipeReplyRecyclerView) view.getParent()
                : null;
        if (scrimmedRecyclerView != null && scrimmedRecyclerView != nextRecyclerView) {
            scrimmedRecyclerView.setScrimmedChild(null);
        }
        scrimmedRecyclerView = nextRecyclerView;
        if (scrimmedRecyclerView != null) {
            scrimmedRecyclerView.setScrimmedChild(view);
        }
    }

    private void postClearInvalidScrim() {
        if (invalidScrimClearPosted) {
            return;
        }
        invalidScrimClearPosted = true;
        post(() -> {
            invalidScrimClearPosted = false;
            if (!isScrimViewValid()) {
                clearScrim();
            }
        });
    }

    private void notifyScrimStateChanged() {
        if (onScrimStateChanged != null) {
            onScrimStateChanged.run();
        }
    }

    private void notifyScrimProgress() {
        if (scrimProgressListener != null) {
            scrimProgressListener.onScrimProgress(Math.min(1f, scrimAlpha / MAX_SCRIM_ALPHA));
        }
    }
}
