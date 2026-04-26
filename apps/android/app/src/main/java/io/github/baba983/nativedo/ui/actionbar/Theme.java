/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/ActionBar/Theme.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/Theme.java
 *
 * NativeDo milestone slice: keeps Telegram theme key names, current theme state,
 * color lookup, selector drawable helpers, and apply-theme notification; omits
 * Telegram wallpaper, remote theme, theme editor, and ThemeDescription systems.
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

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.util.SparseIntArray;

import java.util.ArrayList;
import java.util.HashMap;

import io.github.baba983.nativedo.core.AndroidUtilities;

public final class Theme {

    public interface ThemeDelegate {
        void didSetNewTheme();
    }

    public static final int RIPPLE_MASK_CIRCLE_20DP = 1;
    public static final int RIPPLE_MASK_ALL = 2;

    public static int colorsCount;
    public static final int key_windowBackgroundWhite = colorsCount++;
    public static final int key_windowBackgroundGray = colorsCount++;
    public static final int key_windowBackgroundWhiteBlackText = colorsCount++;
    public static final int key_windowBackgroundWhiteGrayText = colorsCount++;
    public static final int key_windowBackgroundWhiteGrayText2 = colorsCount++;
    public static final int key_windowBackgroundWhiteBlueText = colorsCount++;
    public static final int key_divider = colorsCount++;
    public static final int key_listSelector = colorsCount++;
    public static final int key_actionBarDefault = colorsCount++;
    public static final int key_actionBarDefaultSelector = colorsCount++;
    public static final int key_actionBarDefaultIcon = colorsCount++;
    public static final int key_actionBarDefaultTitle = colorsCount++;
    public static final int key_actionBarDefaultSubtitle = colorsCount++;
    public static final int key_actionBarDefaultSubmenuItem = colorsCount++;
    public static final int key_actionBarDefaultSubmenuItemIcon = colorsCount++;
    public static final int key_actionBarDefaultSubmenuBackground = colorsCount++;
    public static final int key_actionBarDefaultSubmenuSeparator = colorsCount++;

    public static final class ThemeInfo {
        public String name;
        public SparseIntArray colors = new SparseIntArray();
        private boolean dark;

        private ThemeInfo(String name, boolean dark) {
            this.name = name;
            this.dark = dark;
        }

        public boolean isDark() {
            return dark;
        }

        public boolean isLight() {
            return !dark;
        }

        public String getKey() {
            return name;
        }
    }

    private static final String THEME_CONFIG = "themeconfig";
    private static final String THEME_CONFIG_ACTIVE = "theme";
    private static final String THEME_NATIVE_DO = "NativeDo";
    private static final String THEME_DARK_BLUE = "Dark Blue";

    private static final ArrayList<ThemeDelegate> themeDelegates = new ArrayList<>();
    private static final ArrayList<ThemeInfo> themes = new ArrayList<>();
    private static final HashMap<String, ThemeInfo> themesDict = new HashMap<>();
    private static final SparseIntArray currentColors = new SparseIntArray();
    private static final int[] defaultColors = ThemeColors.createDefaultColors();
    private static final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static Context applicationContext;
    private static ThemeInfo currentTheme;
    private static ThemeInfo currentNightTheme;
    private static ThemeInfo currentDayTheme;
    private static ThemeInfo defaultTheme;
    private static int themeGeneration;

    static {
        ThemeInfo nativeDo = createNativeDoTheme();
        themes.add(currentDayTheme = defaultTheme = nativeDo);
        themesDict.put(nativeDo.getKey(), nativeDo);

        ThemeInfo darkBlue = createDarkBlueTheme();
        themes.add(currentNightTheme = darkBlue);
        themesDict.put(darkBlue.getKey(), darkBlue);

        applyThemeInternal(defaultTheme);
    }

    private Theme() {
    }

    public static void init(Context context) {
        if (context == null) {
            return;
        }
        applicationContext = context.getApplicationContext();
        SharedPreferences preferences = applicationContext.getSharedPreferences(THEME_CONFIG, Activity.MODE_PRIVATE);
        ThemeInfo themeInfo = themesDict.get(preferences.getString(THEME_CONFIG_ACTIVE, defaultTheme.getKey()));
        if (themeInfo == null) {
            themeInfo = defaultTheme;
        }
        if (themeInfo != currentTheme) {
            applyTheme(themeInfo, false, themeInfo == currentNightTheme);
        }
    }

    public static ThemeInfo getTheme(String key) {
        return themesDict.get(key);
    }

    public static ThemeInfo getCurrentTheme() {
        return currentDayTheme != null ? currentDayTheme : defaultTheme;
    }

    public static ThemeInfo getCurrentNightTheme() {
        return currentNightTheme;
    }

    public static ThemeInfo getActiveTheme() {
        return currentTheme;
    }

    public static boolean isCurrentThemeNight() {
        return currentTheme == currentNightTheme;
    }

    public static boolean isCurrentThemeDark() {
        return currentTheme != null && currentTheme.isDark();
    }

    public static int getThemeGeneration() {
        return themeGeneration;
    }

    public static void toggleTheme() {
        applyTheme(isCurrentThemeDark() ? defaultTheme : currentNightTheme);
    }

    public static void applyTheme(ThemeInfo themeInfo) {
        applyTheme(themeInfo, true, themeInfo == currentNightTheme);
    }

    public static void applyTheme(ThemeInfo themeInfo, boolean save, boolean nightTheme) {
        if (themeInfo == null) {
            return;
        }
        if (!nightTheme) {
            currentDayTheme = themeInfo;
        }
        applyThemeInternal(themeInfo);
        if (save && applicationContext != null) {
            applicationContext
                    .getSharedPreferences(THEME_CONFIG, Activity.MODE_PRIVATE)
                    .edit()
                    .putString(THEME_CONFIG_ACTIVE, themeInfo.getKey())
                    .apply();
        }
        notifyThemeChanged();
    }

    public static int getColor(int key) {
        int index = currentColors.indexOfKey(key);
        if (index >= 0) {
            int color = currentColors.valueAt(index);
            if (key == key_windowBackgroundWhite || key == key_windowBackgroundGray || key == key_actionBarDefault) {
                color |= 0xff000000;
            }
            return color;
        }
        return getDefaultColor(key);
    }

    public static int getDefaultColor(int key) {
        if (key < 0 || key >= defaultColors.length) {
            return 0xffff0000;
        }
        int value = defaultColors[key];
        return value == 0 ? 0xffff0000 : value;
    }

    public static RippleDrawable createSelectorDrawable(int color) {
        return createSelectorDrawable(color, RIPPLE_MASK_CIRCLE_20DP);
    }

    public static RippleDrawable createSelectorDrawable(int color, int maskType) {
        Drawable maskDrawable = null;
        if (maskType == RIPPLE_MASK_ALL) {
            maskDrawable = new ColorDrawable(Color.WHITE);
        } else if (maskType == RIPPLE_MASK_CIRCLE_20DP) {
            maskDrawable = new Drawable() {
                @Override
                public void draw(Canvas canvas) {
                    Rect bounds = getBounds();
                    maskPaint.setColor(Color.WHITE);
                    canvas.drawCircle(bounds.centerX(), bounds.centerY(), AndroidUtilities.dp(20), maskPaint);
                }

                @Override
                public void setAlpha(int alpha) {
                }

                @Override
                public void setColorFilter(ColorFilter colorFilter) {
                }

                @Override
                public int getOpacity() {
                    return PixelFormat.UNKNOWN;
                }
            };
        }
        RippleDrawable rippleDrawable = new RippleDrawable(ColorStateList.valueOf(color), null, maskDrawable);
        if (maskType == RIPPLE_MASK_CIRCLE_20DP) {
            rippleDrawable.setRadius(AndroidUtilities.dp(20));
        }
        return rippleDrawable;
    }

    public static RippleDrawable getSelectorDrawable(boolean whiteBackground) {
        return createSelectorDrawable(getColor(whiteBackground ? key_actionBarDefaultSelector : key_listSelector));
    }

    public static void addThemeDelegate(ThemeDelegate delegate) {
        if (delegate != null && !themeDelegates.contains(delegate)) {
            themeDelegates.add(delegate);
        }
    }

    public static void removeThemeDelegate(ThemeDelegate delegate) {
        themeDelegates.remove(delegate);
    }

    private static void applyThemeInternal(ThemeInfo themeInfo) {
        currentTheme = themeInfo;
        currentColors.clear();
        SparseIntArray colors = themeInfo.colors;
        for (int i = 0; i < colors.size(); i++) {
            currentColors.put(colors.keyAt(i), colors.valueAt(i));
        }
        themeGeneration++;
    }

    private static void notifyThemeChanged() {
        ArrayList<ThemeDelegate> delegates = new ArrayList<>(themeDelegates);
        for (int i = 0; i < delegates.size(); i++) {
            delegates.get(i).didSetNewTheme();
        }
    }

    private static ThemeInfo createNativeDoTheme() {
        ThemeInfo themeInfo = new ThemeInfo(THEME_NATIVE_DO, false);
        themeInfo.colors.put(key_windowBackgroundWhite, 0xfffbfaf3);
        themeInfo.colors.put(key_windowBackgroundGray, 0xfff5f4ed);
        themeInfo.colors.put(key_windowBackgroundWhiteBlackText, 0xff20251f);
        themeInfo.colors.put(key_windowBackgroundWhiteGrayText, 0xff6b6f63);
        themeInfo.colors.put(key_windowBackgroundWhiteGrayText2, 0xff74786b);
        themeInfo.colors.put(key_windowBackgroundWhiteBlueText, 0xff1e7755);
        themeInfo.colors.put(key_divider, 0xffdfdace);
        themeInfo.colors.put(key_actionBarDefault, 0xfff5f4ed);
        themeInfo.colors.put(key_actionBarDefaultSubtitle, 0xff6b6f63);
        themeInfo.colors.put(key_actionBarDefaultSubmenuItem, 0xff1c2119);
        themeInfo.colors.put(key_actionBarDefaultSubmenuBackground, 0xfffbfaf3);
        themeInfo.colors.put(key_actionBarDefaultSubmenuSeparator, 0xffe6e1d5);
        return themeInfo;
    }

    private static ThemeInfo createDarkBlueTheme() {
        ThemeInfo themeInfo = new ThemeInfo(THEME_DARK_BLUE, true);
        themeInfo.colors.put(key_windowBackgroundWhite, -14866637);
        themeInfo.colors.put(key_windowBackgroundGray, -15393241);
        themeInfo.colors.put(key_windowBackgroundWhiteBlackText, -1);
        themeInfo.colors.put(key_windowBackgroundWhiteGrayText, -8549479);
        themeInfo.colors.put(key_windowBackgroundWhiteGrayText2, 1878130175);
        themeInfo.colors.put(key_windowBackgroundWhiteBlueText, -10177041);
        themeInfo.colors.put(key_divider, -1795162112);
        themeInfo.colors.put(key_listSelector, 350681087);
        themeInfo.colors.put(key_actionBarDefault, -14406343);
        themeInfo.colors.put(key_actionBarDefaultSelector, 516942079);
        themeInfo.colors.put(key_actionBarDefaultIcon, -1);
        themeInfo.colors.put(key_actionBarDefaultTitle, -1);
        themeInfo.colors.put(key_actionBarDefaultSubtitle, 2111566591);
        themeInfo.colors.put(key_actionBarDefaultSubmenuItem, -1);
        themeInfo.colors.put(key_actionBarDefaultSubmenuItemIcon, -1);
        themeInfo.colors.put(key_actionBarDefaultSubmenuBackground, -14141368);
        themeInfo.colors.put(key_actionBarDefaultSubmenuSeparator, -14733761);
        return themeInfo;
    }
}
