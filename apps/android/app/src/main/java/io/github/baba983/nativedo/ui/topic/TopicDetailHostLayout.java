/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Native port of the AdjustPanLayoutHelper host in
 * TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java
 * (ChatActivityFragmentView).
 */
package io.github.baba983.nativedo.ui.topic;

import android.content.Context;
import android.view.View;

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
