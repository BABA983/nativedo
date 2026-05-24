/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/Components/glass/GlassTabView.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/Components/glass/GlassTabView.java
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
package io.github.baba983.nativedo.ui.main;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.core.graphics.ColorUtils;

import io.github.baba983.nativedo.R;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

public class GlassTabView extends FrameLayout implements MainTabsLayout.Tab {

    public enum TabIcon {
        HOME,
        PROFILE
    }

    private final TextView textView;
    private final ImageView iconView;
    private final TextPaint defaultTextPaint;
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint counterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint counterTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmpRectF = new RectF();
    private static final Typeface DEFAULT_TYPEFACE = Typeface.create(Typeface.DEFAULT, 500, false);
    private static final Typeface SELECTED_TYPEFACE = Typeface.create(Typeface.DEFAULT, 800, false);

    private float selectedFactor;
    private boolean selected;
    private float counterFactor;
    private String counterText;
    private ValueAnimator selectedAnimator;
    private ValueAnimator counterAnimator;
    private int colorSelected;
    private int colorSelectedText;
    private int colorDefault;
    private boolean hasVisualWidth;
    private float visualWidth;
    private boolean hasGestureSelectedOverride;
    private float gestureSelectedOverride;

    public GlassTabView(@NonNull Context context, @DrawableRes int iconResId) {
        super(context);
        setWillNotDraw(false);
        setClipChildren(false);

        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER);
        iconView.setImageResource(iconResId);
        addView(iconView, LayoutHelper.createFrame(24, 24, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
        ((LayoutParams) iconView.getLayoutParams()).topMargin = AndroidUtilities.dp(4);

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f);
        textView.setSingleLine();
        textView.setLines(1);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setTypeface(DEFAULT_TYPEFACE);
        textView.setGravity(Gravity.CENTER);
        defaultTextPaint = new TextPaint(textView.getPaint());
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
        ((LayoutParams) textView.getLayoutParams()).topMargin = AndroidUtilities.dp(28.33f);

        counterTextPaint.setColor(Color.WHITE);
        counterTextPaint.setTextAlign(Paint.Align.CENTER);
        counterTextPaint.setTextSize(AndroidUtilities.dp(10));
        counterTextPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        updateColors();
    }

    public static GlassTabView createMainTab(Context context, TabIcon tabIcon, @StringRes int stringRes) {
        GlassTabView tab = new GlassTabView(context, iconResId(tabIcon));
        tab.textView.setText(context.getString(stringRes));
        tab.updateColors();
        return tab;
    }

    public void setVisualWidth(float width) {
        hasVisualWidth = true;
        if (visualWidth != width) {
            visualWidth = width;
            checkVisualWidth();
            invalidate();
        }
    }

    public void setCounter(String text, boolean animated) {
        counterText = text;
        animateCounter(!TextUtils.isEmpty(text) ? 1f : 0f, animated);
    }

    public void setSelected(boolean selected, boolean animated) {
        if (this.selected == selected) {
            return;
        }
        this.selected = selected;
        animateSelected(selected ? 1f : 0f, animated);
        textView.setTypeface(selected ? SELECTED_TYPEFACE : DEFAULT_TYPEFACE);
    }

    public void setGestureSelectedOverride(float gestureSelectedOverride, boolean allow) {
        this.gestureSelectedOverride = gestureSelectedOverride;
        this.hasGestureSelectedOverride = allow;
        invalidate();
    }

    public void updateColors() {
        colorDefault = Theme.getColor(Theme.key_glass_tabUnselected);
        colorSelected = Theme.getColor(Theme.key_glass_tabSelected);
        colorSelectedText = Theme.getColor(Theme.key_glass_tabSelectedText);
        updateAnimatedColors();
        invalidate();
    }

    @Override
    public float measureTextWidth() {
        return defaultTextPaint.measureText(textView.getText().toString());
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        checkVisualWidth();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        final float viewWidth = hasVisualWidth ? visualWidth : getWidth();
        final float drawingSelectedFactor = hasGestureSelectedOverride ? gestureSelectedOverride : selectedFactor;
        if (drawingSelectedFactor > 0f) {
            final float alpha = CubicBezierInterpolator.DEFAULT.getInterpolation(drawingSelectedFactor);
            selectedPaint.setColor(ColorUtils.setAlphaComponent(colorSelected, (int) (23 * alpha)));
            tmpRectF.set(0, 0, viewWidth, getHeight());
            final float radius = Math.min(tmpRectF.width(), tmpRectF.height()) / 2f;
            final float scale = AndroidUtilities.lerp(0.6f, 1f, drawingSelectedFactor);
            canvas.save();
            canvas.scale(scale, scale, tmpRectF.centerX(), tmpRectF.centerY());
            canvas.drawRoundRect(tmpRectF, radius, radius, selectedPaint);
            canvas.restore();
        }

        super.dispatchDraw(canvas);

        if (counterFactor > 0f) {
            final float cx = viewWidth / 2f + AndroidUtilities.dpf2(11f);
            final float cy = AndroidUtilities.dpf2(10f);
            final float height = AndroidUtilities.dpf2(16f);
            final float textWidth = counterText == null ? 0f : counterTextPaint.measureText(counterText);
            final float width = Math.max(height, textWidth + AndroidUtilities.dp(8));
            tmpRectF.set(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f);
            counterPaint.setColor(Theme.getColor(Theme.key_telegram_color));
            canvas.save();
            canvas.scale(counterFactor, counterFactor, cx, cy);
            canvas.drawRoundRect(tmpRectF, AndroidUtilities.dpf2(8f), AndroidUtilities.dpf2(8f), counterPaint);
            if (counterText != null) {
                Paint.FontMetrics fm = counterTextPaint.getFontMetrics();
                canvas.drawText(counterText, cx, cy - (fm.ascent + fm.descent) / 2f, counterTextPaint);
            }
            canvas.restore();
        }
    }

    private void animateSelected(float target, boolean animated) {
        if (selectedAnimator != null) {
            selectedAnimator.cancel();
        }
        if (!animated) {
            selectedFactor = target;
            updateAnimatedColors();
            invalidate();
            return;
        }
        selectedAnimator = ValueAnimator.ofFloat(selectedFactor, target);
        selectedAnimator.setDuration(320);
        selectedAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        selectedAnimator.addUpdateListener(animation -> {
            selectedFactor = (float) animation.getAnimatedValue();
            updateAnimatedColors();
            invalidate();
        });
        selectedAnimator.start();
    }

    private void animateCounter(float target, boolean animated) {
        if (counterAnimator != null) {
            counterAnimator.cancel();
        }
        if (!animated) {
            counterFactor = target;
            invalidate();
            return;
        }
        counterAnimator = ValueAnimator.ofFloat(counterFactor, target);
        counterAnimator.setDuration(380);
        counterAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        counterAnimator.addUpdateListener(animation -> {
            counterFactor = (float) animation.getAnimatedValue();
            invalidate();
        });
        counterAnimator.start();
    }

    private void updateAnimatedColors() {
        final int color = ColorUtils.blendARGB(colorDefault, colorSelected, selectedFactor);
        final int colorText = ColorUtils.blendARGB(colorDefault, colorSelectedText, selectedFactor);
        iconView.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        textView.setTextColor(colorText);
    }

    private void checkVisualWidth() {
        if (!hasVisualWidth) {
            return;
        }
        final float offset = (visualWidth - getMeasuredWidth()) / 2f;
        iconView.setTranslationX(offset);
        textView.setTranslationX(offset);
    }

    @DrawableRes
    private static int iconResId(TabIcon tabIcon) {
        if (tabIcon == TabIcon.PROFILE) {
            return R.drawable.ic_main_tab_profile_24;
        }
        return R.drawable.ic_main_tab_home_24;
    }
}
