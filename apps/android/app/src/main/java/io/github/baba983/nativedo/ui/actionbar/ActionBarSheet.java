// PORT-MODE: Feature Workflow Port
// WORKFLOW-MAP: docs/telegram-port/browser.md
// UPSTREAM-SCOPE: org/telegram/ui/ActionBar/BaseFragment.java#showAsSheet; org/telegram/ui/ActionBar/BottomSheet.java#transitionFromRight
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// EXACT-SUBSYSTEMS: transitionFromRight-animation
// HOST-WIRING:
//   - Hosts the sheet as a NativeDo BaseFragment.AttachedSheet child of ActionBarLayout instead of an Android Dialog.
//   - Uses NativeDo ActionBarLayout as the nested sheet navigation stack.
// ADAPTED-PRODUCT:
//   - Keeps Telegram's transitionFromRight-style 48dp + alpha motion for browser feature sheets.
// OMITTED:
//   - Telegram BottomSheet Dialog window, nested-scroll dismissal, background padding, and navigation-bar drawing internals.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's BaseFragment.showAsSheet(...) and
 * BottomSheet transitionFromRight sheet animation.
 *
 * Copyright (C) Telegram FZ-LLC
 * Copyright (C) 2026 NativeDo contributors
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.actionbar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.core.graphics.ColorUtils;

import io.github.baba983.nativedo.core.AndroidUtilities;
import io.github.baba983.nativedo.ui.components.CubicBezierInterpolator;
import io.github.baba983.nativedo.ui.components.LayoutHelper;

final class ActionBarSheet implements BaseFragment.AttachedSheet {

    private final BaseFragment hostFragment;
    private final ActionBarLayout actionBarLayout;
    private final WindowView windowView;
    private final BaseFragment.BottomSheetParams params;
    private ValueAnimator openAnimator;
    private float openProgress;
    private boolean dismissing;
    private boolean released;

    ActionBarSheet(BaseFragment hostFragment, BaseFragment rootFragment, BaseFragment.BottomSheetParams params) {
        this.hostFragment = hostFragment;
        this.params = params;

        Context context = hostFragment.getContext();
        actionBarLayout = new ActionBarLayout(context);
        actionBarLayout.setRootDismissRunnable(this::dismiss);
        actionBarLayout.addFragmentToStack(rootFragment);

        windowView = new WindowView(context);
        windowView.addView(actionBarLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.START));
    }

    ActionBarLayout getActionBarLayout() {
        return actionBarLayout;
    }

    boolean show() {
        if (released || hostFragment == null) {
            return false;
        }
        ViewGroup parent = getParentView();
        if (parent == null) {
            return false;
        }
        hostFragment.addSheet(this);
        removeFromParent(windowView);
        openProgress = 0f;
        primeOpenState();
        parent.addView(windowView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.START));
        parent.bringChildToFront(windowView);
        updateTranslation();
        animateOpen(true, true, params == null ? null : params.onOpenAnimationFinished);
        return true;
    }

    @Override
    public View getWindowView() {
        return windowView;
    }

    @Override
    public boolean isShown() {
        return !released && windowView.isAttachedToWindow();
    }

    @Override
    public void dismiss() {
        if (released || dismissing) {
            return;
        }
        dismissing = true;
        if (params != null && params.onPreFinished != null) {
            params.onPreFinished.run();
        }
        animateOpen(false, true, this::release);
    }

    @Override
    public void release() {
        if (released) {
            return;
        }
        released = true;
        dismissing = false;
        if (openAnimator != null) {
            openAnimator.cancel();
            openAnimator = null;
        }
        BaseFragment current = actionBarLayout.getLastFragment();
        if (current != null) {
            current.onPause();
        }
        actionBarLayout.setRootDismissRunnable(null);
        removeFromParent(windowView);
        hostFragment.removeSheet(this);
        if (params != null && params.onDismiss != null) {
            params.onDismiss.run();
        }
    }

    @Override
    public boolean isFullyVisible() {
        return !released && !dismissing && openProgress >= 1f;
    }

    @Override
    public boolean attachedToParent() {
        return windowView.isAttachedToWindow();
    }

    @Override
    public boolean onAttachedBackPressed() {
        if (!actionBarLayout.handleBackPressed()) {
            dismiss();
        }
        return true;
    }

    @Override
    public boolean showDialog(Dialog dialog) {
        return false;
    }

    @Override
    public void setKeyboardHeightFromParent(int keyboardHeight) {
    }

    @Override
    public boolean isAttachedLightStatusBar() {
        BaseFragment current = actionBarLayout.getLastFragment();
        return current == null || current.isLightStatusBar();
    }

    @Override
    public int getNavigationBarColor(int color) {
        BaseFragment current = actionBarLayout.getLastFragment();
        int target = current == null ? Theme.getColor(Theme.key_windowBackgroundGray) : current.getNavigationBarColor();
        return ColorUtils.blendARGB(color, target, openProgress);
    }

    @Override
    public void setOnDismissListener(Runnable onDismiss) {
        if (params != null) {
            params.onDismiss = onDismiss;
        }
    }

    private void animateOpen(boolean open, boolean animated, Runnable callback) {
        if (openAnimator != null) {
            openAnimator.cancel();
        }
        float target = open ? 1f : 0f;
        if (!animated) {
            openProgress = target;
            applyOpenProgress();
            if (callback != null) {
                callback.run();
            }
            return;
        }
        if (open) {
            primeOpenState();
        }
        openAnimator = ValueAnimator.ofFloat(openProgress, target);
        openAnimator.addUpdateListener(animation -> {
            openProgress = (float) animation.getAnimatedValue();
            applyOpenProgress();
            updateTranslation();
        });
        openAnimator.setDuration(open ? 250 : 200);
        openAnimator.setInterpolator(open ? CubicBezierInterpolator.EASE_OUT_QUINT : CubicBezierInterpolator.DEFAULT);
        openAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (cancelled) {
                    return;
                }
                openProgress = target;
                applyOpenProgress();
                updateTranslation();
                if (callback != null) {
                    callback.run();
                }
            }
        });
        openAnimator.start();
    }

    private void primeOpenState() {
        actionBarLayout.setTranslationX(AndroidUtilities.dp(48));
        actionBarLayout.setAlpha(0f);
    }

    private void applyOpenProgress() {
        actionBarLayout.setTranslationX(AndroidUtilities.dp(48) * (1f - openProgress));
        actionBarLayout.setAlpha(openProgress);
    }

    private void updateTranslation() {
        hostFragment.notifyParentSystemBarsChanged();
        windowView.invalidate();
    }

    private ViewGroup getParentView() {
        if (hostFragment.getParentLayout() instanceof ViewGroup) {
            return (ViewGroup) hostFragment.getParentLayout();
        }
        if (hostFragment.getFragmentView() != null && hostFragment.getFragmentView().getParent() instanceof ViewGroup) {
            return (ViewGroup) hostFragment.getFragmentView().getParent();
        }
        return null;
    }

    private void removeFromParent(View view) {
        if (view != null && view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }

    private class WindowView extends FrameLayout implements BaseFragment.AttachedSheetWindow {
        WindowView(Context context) {
            super(context);
            setWillNotDraw(false);
            setClickable(true);
            setFocusable(true);
            setFocusableInTouchMode(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (0x60 * openProgress)));
        }
    }
}
