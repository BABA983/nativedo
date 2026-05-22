// UPSTREAM-LEVEL: L1
// UPSTREAM-PATH:  org/telegram/ui/ActionBar/ActionBarPopupWindow.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// SUBSYSTEMS: popup-menu=L1, animation=L0
// HOST-WIRING:
//   - AndroidUtilities import rewritten to NativeDo.
// OMITTED:
//   - Swipe-back popup layouts, gap views, dimming, custom dismiss animations, and Telegram window edge cases.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/ActionBar/ActionBarPopupWindow.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBarPopupWindow.java
 *
 * NativeDo milestone slice: keeps PopupWindow-backed ActionBar menu display and
 * Telegram naming plus backScaleY/backAlpha popup background animation; omits
 * swipe-back popup layouts, gap views, dimming, and custom dismiss animations.
 *
 * Copyright (C) Telegram FZ-LLC
 * Copyright (C) 2026 NativeDo contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under terms of GNU General Public License version 2 or later as published by
 * the Free Software Foundation.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.actionbar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import io.github.baba983.nativedo.telegram.messenger.AndroidUtilities;

public class ActionBarPopupWindow extends PopupWindow {

    private AnimatorSet windowAnimatorSet;

    public static class ActionBarPopupWindowLayout extends LinearLayout {

        public boolean clipChildren;
        private float backScaleY = 1f;
        private int backAlpha = 255;
        private Drawable backgroundDrawable;
        private final Rect backgroundPaddings = new Rect();
        private final Rect backgroundRect = new Rect();

        public ActionBarPopupWindowLayout(android.content.Context context) {
            super(context);
            setOrientation(VERTICAL);
            setClipToPadding(false);
            setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
            setWillNotDraw(false);
        }

        public void setBackScaleY(float value) {
            if (backScaleY != value) {
                backScaleY = value;
                invalidate();
            }
        }

        public float getBackScaleY() {
            return backScaleY;
        }

        public void setBackAlpha(int value) {
            if (backAlpha != value) {
                backAlpha = value;
                invalidate();
            }
        }

        public int getBackAlpha() {
            return backAlpha;
        }

        @Override
        public void setBackgroundDrawable(Drawable drawable) {
            backgroundDrawable = drawable;
            if (backgroundDrawable != null) {
                backgroundDrawable.getPadding(backgroundPaddings);
            }
            invalidate();
        }

        public int getItemsCount() {
            return getChildCount();
        }

        public View getItemAt(int index) {
            return getChildAt(index);
        }

        public void scrollToTop() {
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            int saveCount = canvas.save();
            if (backgroundDrawable != null) {
                backgroundRect.set(0, 0, getMeasuredWidth(), (int) (getMeasuredHeight() * backScaleY));
                backgroundDrawable.setAlpha(backAlpha);
                backgroundDrawable.setBounds(backgroundRect);
                backgroundDrawable.draw(canvas);
                if (clipChildren) {
                    backgroundRect.left += backgroundPaddings.left;
                    backgroundRect.top += backgroundPaddings.top;
                    backgroundRect.right -= backgroundPaddings.right;
                    backgroundRect.bottom -= backgroundPaddings.bottom;
                    canvas.clipRect(backgroundRect);
                }
            }
            super.dispatchDraw(canvas);
            canvas.restoreToCount(saveCount);
        }
    }

    public ActionBarPopupWindow(View contentView, int width, int height) {
        super(contentView, width, height, true);
        setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        setOutsideTouchable(true);
        setClippingEnabled(true);
        setElevation(AndroidUtilities.dp(8));
    }

    public void startAnimation() {
        if (windowAnimatorSet != null) {
            return;
        }
        ActionBarPopupWindowLayout content = findPopupLayout();
        if (content == null) {
            return;
        }
        content.setTranslationY(0);
        content.setAlpha(1f);
        content.setPivotX(content.getMeasuredWidth());
        content.setPivotY(0);
        int count = content.getItemsCount();
        int visibleCount = 0;
        for (int i = 0; i < count; i++) {
            View child = content.getItemAt(i);
            child.setAlpha(0f);
            if (child.getVisibility() == View.VISIBLE) {
                visibleCount++;
            }
        }
        ValueAnimator childTranslations = ValueAnimator.ofFloat(0f, 1f);
        childTranslations.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            int count2 = content.getItemsCount();
            for (int i = 0; i < count2; i++) {
                View child = content.getItemAt(i);
                float childProgress = AndroidUtilities.cascade(progress, i, count2, 4);
                child.setTranslationY((1f - childProgress) * AndroidUtilities.dp(-6));
                child.setAlpha(childProgress * (child.isEnabled() ? 1f : 0.5f));
            }
        });
        content.clipChildren = true;
        windowAnimatorSet = new AnimatorSet();
        windowAnimatorSet.playTogether(
                ObjectAnimator.ofFloat(content, "backScaleY", 0f, 1f),
                ObjectAnimator.ofInt(content, "backAlpha", 0, 255),
                childTranslations
        );
        windowAnimatorSet.setDuration(150 + 16L * visibleCount);
        windowAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                windowAnimatorSet = null;
                content.clipChildren = false;
                for (int i = 0; i < content.getItemsCount(); i++) {
                    View child = content.getItemAt(i);
                    child.setTranslationY(0);
                    child.setAlpha(child.isEnabled() ? 1f : 0.5f);
                }
            }
        });
        windowAnimatorSet.start();
    }

    @Override
    public void dismiss() {
        if (windowAnimatorSet != null) {
            windowAnimatorSet.cancel();
            windowAnimatorSet = null;
        }
        super.dismiss();
    }

    public void dismiss(boolean animated) {
        dismiss();
    }

    private ActionBarPopupWindowLayout findPopupLayout() {
        View contentView = getContentView();
        if (contentView instanceof ActionBarPopupWindowLayout) {
            return (ActionBarPopupWindowLayout) contentView;
        }
        if (contentView instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) contentView;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                if (child instanceof ActionBarPopupWindowLayout) {
                    return (ActionBarPopupWindowLayout) child;
                }
            }
        }
        return null;
    }
}
