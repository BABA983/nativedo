// UPSTREAM-LEVEL: L1
// UPSTREAM-PATH:  org/telegram/ui/ArticleViewer.java — searchPanel
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// SUBSYSTEMS: custom-drawing=L1, animation=L0, keyboard-tracking=L1
// HOST-WIRING:
//   - Standalone FrameLayout attached to BrowserSheet.containerView / BrowserFragment root.
//   - Uses Telegram's msg_go_up.png, msg_go_down.png, and compose_panel_shadow.png drawables.
//   - Keyboard height is tracked via ViewTreeObserver to match upstream keyboard-panel coupling.
// ADAPTED:
//   - Keeps upstream searchPanel height (51dp), buttons (48x48dp), count text style (15sp bold),
//     and 320ms EASE_OUT_QUINT show/hide animation.
//   - Uses programmatic background paint instead of Theme.chat_composeBackgroundPaint.
// OMITTED:
//   - KeyboardNotifier with AdjustPanLayoutHelper integration (uses simpler onGlobalLayout tracking).
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's org/telegram/ui/ArticleViewer.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram
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
package io.github.baba983.nativedo.ui.browser;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import io.github.baba983.nativedo.R;
import io.github.baba983.nativedo.core.AndroidUtilities;
import io.github.baba983.nativedo.ui.actionbar.Theme;
import io.github.baba983.nativedo.ui.components.CubicBezierInterpolator;
import io.github.baba983.nativedo.ui.components.LayoutHelper;

public class BrowserSearchPanel extends FrameLayout {

    public interface Callback {
        void onFindPrevious();

        void onFindNext();
    }

    private final Drawable shadowDrawable;
    private final Paint backgroundPaint = new Paint();
    private final TextView countText;
    private final ImageView upButton;
    private final ImageView downButton;
    private Callback callback;

    private float searchPanelTranslation;
    private float searchPanelAlpha;
    private ValueAnimator panelAnimator;
    private int keyboardHeight;
    private ViewTreeObserver.OnGlobalLayoutListener keyboardTracker;

    public BrowserSearchPanel(Context context) {
        super(context);

        shadowDrawable = ContextCompat.getDrawable(context, R.drawable.compose_panel_shadow);
        if (shadowDrawable != null) {
            shadowDrawable.mutate();
        }
        setWillNotDraw(false);
        setOnTouchListener((v, event) -> true);
        setTranslationY(AndroidUtilities.dp(51));
        setVisibility(View.INVISIBLE);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        setPadding(0, AndroidUtilities.dp(3), 0, 0);

        backgroundPaint.setColor(Theme.getColor(Theme.key_chat_messagePanelBackground));

        countText = new TextView(context);
        countText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        countText.setTypeface(countText.getTypeface(), android.graphics.Typeface.BOLD);
        countText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        countText.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams countLp = LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        countLp.leftMargin = AndroidUtilities.dp(18);
        countLp.rightMargin = AndroidUtilities.dp(108);
        addView(countText, countLp);

        downButton = new ImageView(context);
        downButton.setScaleType(ImageView.ScaleType.CENTER);
        downButton.setImageResource(R.drawable.msg_go_down);
        downButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY));
        downButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
        downButton.setOnClickListener(v -> {
            if (callback != null) {
                callback.onFindNext();
            }
        });
        downButton.setContentDescription(context.getString(R.string.app_browser_search_next));
        addView(downButton, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.TOP));

        upButton = new ImageView(context);
        upButton.setScaleType(ImageView.ScaleType.CENTER);
        upButton.setImageResource(R.drawable.msg_go_up);
        upButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY));
        upButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
        upButton.setOnClickListener(v -> {
            if (callback != null) {
                callback.onFindPrevious();
            }
        });
        upButton.setContentDescription(context.getString(R.string.app_browser_search_previous));
        FrameLayout.LayoutParams upLp = LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.TOP);
        upLp.rightMargin = AndroidUtilities.dp(48);
        addView(upButton, upLp);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (shadowDrawable != null) {
            int bottom = shadowDrawable.getIntrinsicHeight();
            shadowDrawable.setBounds(0, 0, getMeasuredWidth(), bottom);
            shadowDrawable.draw(canvas);
            canvas.drawRect(0, bottom, getMeasuredWidth(), getMeasuredHeight(), backgroundPaint);
        } else {
            canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), backgroundPaint);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(51), MeasureSpec.EXACTLY));
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void updateResults(int index, int count) {
        boolean enabled = count > 0;
        upButton.setEnabled(enabled && index != 0);
        downButton.setEnabled(enabled && index != count - 1);
        upButton.setAlpha(upButton.isEnabled() ? 1.0f : 0.5f);
        downButton.setAlpha(downButton.isEnabled() ? 1.0f : 0.5f);

        if (count <= 0) {
            countText.setText(R.string.app_browser_search_no_results);
        } else if (count == 1) {
            countText.setText(R.string.app_browser_one_result);
        } else {
            countText.setText(getContext().getString(R.string.app_browser_count_of_results, index + 1, count));
        }
    }

    public void applyTheme() {
        backgroundPaint.setColor(Theme.getColor(Theme.key_chat_messagePanelBackground));
        if (shadowDrawable != null) {
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelShadow), PorterDuff.Mode.MULTIPLY));
        }
        countText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        upButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY));
        downButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY));
        upButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
        downButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
        invalidate();
    }

    public void show(boolean show, boolean animated) {
        if (show) {
            setVisibility(VISIBLE);
        }
        if (panelAnimator != null) {
            panelAnimator.cancel();
        }
        float target = show ? 1f : 0f;
        if (!animated) {
            searchPanelAlpha = target;
            setTranslationY((searchPanelTranslation = -keyboardHeight) + AndroidUtilities.dp(51) * (1f - searchPanelAlpha));
            if (!show) {
                setVisibility(INVISIBLE);
            }
            return;
        }
        panelAnimator = ValueAnimator.ofFloat(searchPanelAlpha, target);
        panelAnimator.addUpdateListener(animation -> {
            searchPanelAlpha = (float) animation.getAnimatedValue();
            setTranslationY((searchPanelTranslation = -keyboardHeight) + AndroidUtilities.dp(51) * (1f - searchPanelAlpha));
        });
        panelAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                searchPanelAlpha = target;
                setTranslationY((searchPanelTranslation = -keyboardHeight) + AndroidUtilities.dp(51) * (1f - searchPanelAlpha));
                if (!show) {
                    setVisibility(INVISIBLE);
                }
            }
        });
        panelAnimator.setDuration(320);
        panelAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        panelAnimator.start();
    }

    public void setKeyboardHeight(int height) {
        keyboardHeight = height;
        setTranslationY((searchPanelTranslation = -keyboardHeight) + AndroidUtilities.dp(51) * (1f - searchPanelAlpha));
    }

    public void attachKeyboardTracking(View rootView) {
        if (keyboardTracker != null) {
            rootView.getViewTreeObserver().removeOnGlobalLayoutListener(keyboardTracker);
        }
        keyboardTracker = () -> {
            android.graphics.Rect r = new android.graphics.Rect();
            rootView.getWindowVisibleDisplayFrame(r);
            int heightDiff = rootView.getHeight() - r.bottom;
            if (heightDiff > AndroidUtilities.dp(100)) {
                setKeyboardHeight(heightDiff);
            } else {
                setKeyboardHeight(0);
            }
        };
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(keyboardTracker);
    }

    public void detachKeyboardTracking(View rootView) {
        if (keyboardTracker != null && rootView != null) {
            rootView.getViewTreeObserver().removeOnGlobalLayoutListener(keyboardTracker);
        }
        keyboardTracker = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (keyboardTracker != null && getRootView() != null) {
            getRootView().getViewTreeObserver().removeOnGlobalLayoutListener(keyboardTracker);
        }
    }
}
