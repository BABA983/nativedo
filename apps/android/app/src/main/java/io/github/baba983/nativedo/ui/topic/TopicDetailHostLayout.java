/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Native port of the AdjustPanLayoutHelper host in
 * TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java
 * (ChatActivityFragmentView).
 */
package io.github.baba983.nativedo.ui.topic;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;

/**
 * TopicDetail-facing host for the same AdjustPanLayoutHelper lifecycle ChatActivity
 * owns on {@code ChatActivityFragmentView}:
 * <ul>
 *   <li>helper-owned hierarchy height hold during the 250ms keyboard transition</li>
 *   <li>parent translation with fixed ActionBar counter-translation</li>
 *   <li>250ms post-attach / resume guard</li>
 *   <li>emoji popup start offset</li>
 * </ul>
 * Hierarchy-height checks stay off, matching ChatActivity (which never calls
 * {@link AdjustPanLayoutHelper#setCheckHierarchyHeight(boolean)}). BottomSheetTabs
 * resize is absorbed through ActionBarLayout margins + the helper's own
 * {@code getExpandedHeight()} term, not a second hierarchy probe.
 */
public class TopicDetailHostLayout extends TopicPostScrimHostLayout {

    public interface PanTransitionDelegate {
        void onPanTransitionStart(boolean keyboardVisible, int contentHeight);

        void onPanTranslationUpdate(float y, float progress, boolean keyboardVisible);

        void onPanTransitionEnd();

        int getPanStartOffset();

        default boolean isPanHeightAnimationEnabled() {
            return true;
        }
    }

    private final AdjustPanLayoutHelper adjustPanLayoutHelper;
    private View fixedTopView;
    private PanTransitionDelegate panTransitionDelegate;
    private boolean released;
    private long panAnimationsEnabledAt;
    private View fixedContentClipView;
    private int fixedContentClipTop;
    private final Rect fixedContentClipBounds = new Rect();
    private final OnLayoutChangeListener fixedContentClipLayoutListener = (view, left, top, right, bottom,
            oldLeft, oldTop, oldRight, oldBottom) -> updateFixedContentClip();

    public TopicDetailHostLayout(@NonNull Context context) {
        super(context);
        adjustPanLayoutHelper = new AdjustPanLayoutHelper(this) {
            @Override
            protected void onTransitionStart(boolean keyboardVisible, int contentHeight) {
                if (panTransitionDelegate != null) {
                    panTransitionDelegate.onPanTransitionStart(keyboardVisible, contentHeight);
                }
            }

            @Override
            protected void onPanTranslationUpdate(float y, float progress, boolean keyboardVisible) {
                // Match ChatActivityFragmentView: counter-translate the ActionBar so it
                // stays pinned in screen space while the helper pans the root.
                if (fixedTopView != null) {
                    fixedTopView.setTranslationY(y);
                }
                if (panTransitionDelegate != null) {
                    panTransitionDelegate.onPanTranslationUpdate(y, progress, keyboardVisible);
                }
            }

            @Override
            protected void onTransitionEnd() {
                if (panTransitionDelegate != null) {
                    panTransitionDelegate.onPanTransitionEnd();
                }
            }

            @Override
            protected boolean heightAnimationEnabled() {
                return isAttachedToWindow()
                        && !released
                        && !AndroidUtilities.isInMultiwindow
                        && System.currentTimeMillis() >= panAnimationsEnabledAt
                        && (panTransitionDelegate == null || panTransitionDelegate.isPanHeightAnimationEnabled());
            }

            @Override
            protected int startOffset() {
                return getCurrentPanStartOffset();
            }
        };
    }

    public void setFixedTopView(@Nullable View fixedTopView) {
        this.fixedTopView = fixedTopView;
    }

    public void setPanTransitionDelegate(@Nullable PanTransitionDelegate panTransitionDelegate) {
        this.panTransitionDelegate = panTransitionDelegate;
    }

    @NonNull
    public AdjustPanLayoutHelper getAdjustPanLayoutHelper() {
        return adjustPanLayoutHelper;
    }

    public int getCurrentPanStartOffset() {
        return panTransitionDelegate != null ? Math.max(0, panTransitionDelegate.getPanStartOffset()) : 0;
    }

    public void delayPanAnimations(long delayMs) {
        panAnimationsEnabledAt = System.currentTimeMillis() + Math.max(0L, delayMs);
    }

    /**
     * Chat-shaped escape hatch used when a non-keyboard height change is about to
     * land (for example BottomSheetTabs visibility or an attached browser sheet).
     */
    public void ignoreNextPanHeightAnimation() {
        if (!released) {
            adjustPanLayoutHelper.ignoreOnce();
        }
    }

    public void stopPanTransition() {
        if (!released) {
            adjustPanLayoutHelper.stopTransition();
        }
    }

    public boolean isPanAnimationInProgress() {
        return !released && adjustPanLayoutHelper.animationInProgress();
    }

    /** Clips edge-to-edge list content below fixed top chrome without changing its padding model. */
    public void setFixedContentTopClip(@Nullable View content, int topInset) {
        if (fixedContentClipView != content) {
            if (fixedContentClipView != null) {
                fixedContentClipView.removeOnLayoutChangeListener(fixedContentClipLayoutListener);
                fixedContentClipView.setClipBounds(null);
            }
            fixedContentClipView = content;
            if (fixedContentClipView != null) {
                fixedContentClipView.addOnLayoutChangeListener(fixedContentClipLayoutListener);
            }
        }
        fixedContentClipTop = Math.max(0, topInset);
        updateFixedContentClip();
    }

    private void updateFixedContentClip() {
        if (fixedContentClipView == null || fixedContentClipTop == 0 ||
                fixedContentClipView.getWidth() == 0 || fixedContentClipView.getHeight() == 0) {
            if (fixedContentClipView != null) {
                fixedContentClipView.setClipBounds(null);
            }
            return;
        }
        fixedContentClipBounds.set(
                0,
                Math.min(fixedContentClipTop, fixedContentClipView.getHeight()),
                fixedContentClipView.getWidth(),
                fixedContentClipView.getHeight());
        fixedContentClipView.setClipBounds(fixedContentClipBounds);
    }

    /** Returns the real laid-out extent reserved by a fixed overlay's children. */
    public static int getOverlayContentHeight(@Nullable ViewGroup overlay) {
        if (overlay == null) {
            return 0;
        }
        int contentHeight = 0;
        boolean laidOut = overlay.getHeight() > 0;
        for (int index = 0; index < overlay.getChildCount(); index++) {
            View child = overlay.getChildAt(index);
            if (child.getVisibility() == View.GONE) {
                continue;
            }
            ViewGroup.LayoutParams layoutParams = child.getLayoutParams();
            int layoutHeight = layoutParams != null ? layoutParams.height : 0;
            int childHeight = Math.max(child.getMeasuredHeight(), layoutHeight);
            if (laidOut && childHeight > 0) {
                int childTop = child.getTop() + Math.round(child.getTranslationY());
                contentHeight = Math.max(contentHeight, overlay.getHeight() - childTop);
            } else {
                contentHeight = Math.max(contentHeight, childHeight);
            }
        }
        return Math.max(0, contentHeight);
    }

    public void releasePanLayoutHelper() {
        if (released) {
            return;
        }
        released = true;
        adjustPanLayoutHelper.onDetach();
        setTranslationY(0f);
        if (fixedTopView != null) {
            fixedTopView.setTranslationY(0f);
        }
        fixedTopView = null;
        if (fixedContentClipView != null) {
            fixedContentClipView.removeOnLayoutChangeListener(fixedContentClipLayoutListener);
            fixedContentClipView.setClipBounds(null);
        }
        fixedContentClipView = null;
        panTransitionDelegate = null;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!released) {
            delayPanAnimations(250L);
            adjustPanLayoutHelper.onAttach();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        adjustPanLayoutHelper.onDetach();
        super.onDetachedFromWindow();
    }
}
