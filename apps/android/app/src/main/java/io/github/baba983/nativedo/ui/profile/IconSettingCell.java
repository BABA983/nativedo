/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/SettingsActivity.SettingCell and its inner Background class.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/SettingsActivity.java
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
package io.github.baba983.nativedo.ui.profile;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

/**
 * Native port of Telegram's SettingsActivity.SettingCell.
 * A horizontal row with a gradient-background icon, title, optional subtitle, and optional value.
 * The icon sits inside a rounded-rect with a vertical LinearGradient + optional stroke border.
 */
public class IconSettingCell extends LinearLayout implements Theme.Colorable {

    private final Background iconBackground;
    private final FrameLayout iconLayout;
    private final ImageView iconView;
    private final LinearLayout textLayout;
    private final TextView titleView;
    private final TextView subtitleView;
    private final TextView valueView;
    private boolean twoLines;

    public IconSettingCell(Context context) {
        super(context);

        setOrientation(HORIZONTAL);
        setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));

        iconLayout = new FrameLayout(context);
        iconLayout.setBackground(iconBackground = new Background());

        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iconLayout.addView(iconView, LayoutHelper.createFrame(24, 24, Gravity.CENTER));

        textLayout = new LinearLayout(context);
        textLayout.setOrientation(VERTICAL);

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        textLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        textLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

        valueView = new TextView(context);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        valueView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        valueView.setSingleLine(true);
        valueView.setEllipsize(TextUtils.TruncateAt.END);

        if (org.telegram.messenger.LocaleController.isRTL) {
            addView(valueView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 20, 0, 0, 0));
            addView(textLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL, 20, 0, 18, 0));
            addView(iconLayout, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 18, 0));
        } else {
            addView(iconLayout, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL | Gravity.LEFT, 18, 0, 0, 0));
            addView(textLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL, 18, 0, 20, 0));
            addView(valueView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 20, 0));
        }

        updateColors();
    }

    @Override
    public void updateColors() {
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        valueView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        iconBackground.setDrawBorder(Theme.isCurrentThemeDark());
    }

    /**
     * Bind data to this cell — mirrors upstream SettingCell.set().
     */
    public void set(int iconColorTop, int iconColorBottom, int icon,
                    CharSequence title,
                    CharSequence subtitle,
                    CharSequence value) {
        iconLayout.setVisibility(icon != 0 ? View.VISIBLE : View.GONE);
        titleView.setTranslationX(icon == 0 ? dp(2) : 0);
        subtitleView.setTranslationX(icon == 0 ? dp(2) : 0);

        iconBackground.setColor(iconColorTop, iconColorBottom);
        iconView.setImageResource(icon);
        titleView.setText(title);
        subtitleView.setVisibility((twoLines = !TextUtils.isEmpty(subtitle)) ? View.VISIBLE : View.GONE);
        subtitleView.setText(subtitle);
        valueView.setVisibility(!TextUtils.isEmpty(value) ? View.VISIBLE : View.GONE);
        valueView.setText(value);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(dp(twoLines ? 60 : 50), MeasureSpec.EXACTLY)
        );
    }

    /**
     * Gradient icon background — native port of SettingsActivity.SettingCell.Background.
     * Draws a rounded-rect with a vertical LinearGradient fill and an optional stroke border
     * (white-to-transparent gradient) for dark theme.
     */
    public static class Background extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private LinearGradient gradient;
        private final LinearGradient strokeGradient;
        private boolean border;

        public Background() {
            strokePaint.setStyle(Paint.Style.STROKE);
            strokeGradient = new LinearGradient(0, 0, 0, dp(28),
                new int[] { 0x4dffffff, 0, 0x1affffff },
                new float[] { 0, 0.5f, 1 },
                Shader.TileMode.CLAMP);
            strokePaint.setShader(strokeGradient);
        }

        public void setColor(int topColor, int bottomColor) {
            gradient = new LinearGradient(0, 0, 0, dp(28),
                new int[] { topColor, bottomColor },
                new float[] { 0, 1 },
                Shader.TileMode.CLAMP);
            paint.setShader(gradient);
        }

        public void setDrawBorder(boolean drawBorder) {
            this.border = drawBorder;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            final float r = dp(10);
            AndroidUtilities.rectTmp.set(getBounds());
            canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, paint);

            if (border) {
                final float sw = dp(1);
                strokePaint.setStrokeWidth(sw);
                AndroidUtilities.rectTmp.inset(sw / 2.0f, sw / 2.0f);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, strokePaint);
            }
        }

        @Override
        public void setAlpha(int alpha) {}
        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {}
        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }
    }

    /**
     * Color pairs for gradient icon backgrounds — simplified from upstream IconBackgroundColors.
     * Only the colors we actually use in the profile page.
     */
    public static final class Colors {
        public static final int BLUE_TOP    = 0xFF1CA5ED;
        public static final int BLUE_BOTTOM = 0xFF1488E1;
        public static final int ORANGE_TOP    = 0xFFF09F1B;
        public static final int ORANGE_BOTTOM = 0xFFE18A11;
        public static final int GREEN_TOP    = 0xFF55CA47;
        public static final int GREEN_BOTTOM = 0xFF27B434;
        public static final int RED_TOP    = 0xFFF45255;
        public static final int RED_BOTTOM = 0xFFDF3955;
        public static final int PURPLE_TOP    = 0xFFC46EF4;
        public static final int PURPLE_BOTTOM = 0xFF9F55DF;
        public static final int CYAN_TOP    = 0xFF32C0CE;
        public static final int CYAN_BOTTOM = 0xFF1D9CC6;
        public static final int PINK_TOP    = 0xFFFF85A0;
        public static final int PINK_BOTTOM = 0xFFFF5580;
    }

    // --- UItem Factory ---

    private static final int VIEW_TYPE = 10_005;

    public static final class Factory implements UItem.ViewFactory {
        private static final Factory INSTANCE = new Factory();

        @Override
        public int getViewType() {
            return VIEW_TYPE;
        }

        @Override
        public View createView(Context context) {
            return new IconSettingCell(context);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter) {
            int iconColorTop    = (int) item.longValue;
            int iconColorBottom = (int) (item.longValue >>> 32);
            ((IconSettingCell) view).set(
                iconColorTop, iconColorBottom, item.iconResId,
                item.text,
                item.subtext,
                item.textValue
            );
            ((IconSettingCell) view).updateColors();
        }

        @Override
        public boolean isEnabled(UItem item) {
            return true;
        }

        /**
         * Create a UItem for this cell. Icon color top/bottom are packed into longValue
         * to match upstream SettingsActivity.SettingCell.Factory packing convention.
         */
        public static UItem of(int id, int iconColorTop, int iconColorBottom, int icon, CharSequence title) {
            return of(id, iconColorTop, iconColorBottom, icon, title, null, null);
        }

        public static UItem of(int id, int iconColorTop, int iconColorBottom, int icon, CharSequence title, CharSequence subtitle) {
            return of(id, iconColorTop, iconColorBottom, icon, title, subtitle, null);
        }

        public static UItem of(int id, int iconColorTop, int iconColorBottom, int icon, CharSequence title, CharSequence subtitle, CharSequence value) {
            final UItem item = UItem.ofFactory(INSTANCE);
            item.id = id;
            item.iconResId = icon;
            item.text = title;
            item.subtext = subtitle;
            item.textValue = value;
            item.longValue = ((long) iconColorBottom << 32) | (iconColorTop & 0xFFFFFFFFL);
            return item;
        }
    }
}
