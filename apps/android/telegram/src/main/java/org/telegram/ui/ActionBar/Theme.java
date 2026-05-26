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
package org.telegram.ui.ActionBar;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextPaint;
import android.util.SparseIntArray;

import java.util.ArrayList;
import java.util.HashMap;

import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

public final class Theme {

    public interface ThemeDelegate {
        void didSetNewTheme();
    }

    /** Stub of upstream {@code Theme.getBaseThemeKey(TLRPC.ThemeSettings)} — returns "" until ThemeDocument is wired. */
    public static String getBaseThemeKey(Object themeSettings) {
        return "";
    }

    /** Stub of upstream {@code Theme.roundMessageInset} — round-bubble side inset in px. */
    public static int roundMessageInset = 0;

    /** Stub of upstream {@code Theme.checkCurrentRemoteTheme(boolean)}. */
    public static void checkCurrentRemoteTheme(boolean force) {
    }

    /** Stub of upstream {@code Theme.createInsetRoundRectDrawable}. */
    public static android.graphics.drawable.Drawable createInsetRoundRectDrawable(int inset, int rad, int color) {
        return new android.graphics.drawable.ColorDrawable(color);
    }

    public static android.graphics.drawable.Drawable createInsetRoundRectDrawable(int inset, int radTL, int radTR, int radBR, int radBL) {
        return new android.graphics.drawable.ColorDrawable(0);
    }

    public static android.graphics.drawable.Drawable createInsetRoundRectDrawable(int inset, int rad, int color, int pressedColor) {
        return new android.graphics.drawable.ColorDrawable(color);
    }

    /**
     * Compile-only shim mirroring Telegram's {@code Theme.Colorable}.
     * Used by views that need to update their colors when the theme changes.
     */
    public interface Colorable {
        void updateColors();
    }

    /**
     * Compile-only shim mirroring Telegram's {@code Theme.ResourcesProvider}.
     * Telegram lets fragments override theme color lookups by passing a
     * provider; this milestone always reads from the global {@link Theme}, so
     * the methods are here only to preserve upstream constructor signatures
     * and let L0 ports compile. See AGENTS.md Hard Rules.
     */
    public interface ResourcesProvider {
        default int getColor(int key) {
            return Theme.getColor(key);
        }

        default Integer getColorOrNull(int key) {
            return Theme.getColor(key);
        }

        default Drawable getDrawable(String drawableKey) {
            return null;
        }

        /** ADAPTED: getPaint stub for RecyclerListView compatibility. */
        default Paint getPaint(String paintKey) {
            return Theme.getThemePaint(paintKey);
        }
    }

    public static final int RIPPLE_MASK_CIRCLE_20DP = 1;
    public static final int RIPPLE_MASK_ALL = 2;
    /** ADAPTED: rounded-rect ripple mask used by ScrollSlidingTextTabStrip selector. */
    public static final int RIPPLE_MASK_ROUNDRECT_6DP = 7;

    public static int colorsCount;

    // Additional Theme keys referenced by ported UI code (defined just after colorsCount so forward refs work).
    public static final int key_featuredStickers_addButton = colorsCount++;
    public static final int key_featuredStickers_addButtonPressed = colorsCount++;
    public static final int key_featuredStickers_buttonText = colorsCount++;
    public static final int key_featuredStickers_buttonProgress = colorsCount++;
    public static final int key_buttonNeutral = colorsCount++;
    public static final int key_buttonNeutralText = colorsCount++;
    public static final int key_chat_editMediaButton = colorsCount++;
    public static final int key_chat_messagePanelIcons = colorsCount++;
    public static final int key_chat_messagePanelText = colorsCount++;
    public static final int key_player_progress = colorsCount++;
    public static final int key_player_progressBackground = colorsCount++;
    public static final int key_player_progressCachedBackground = colorsCount++;
    public static final int key_text_RedBold = colorsCount++;
    public static final int key_text_RedRegular = colorsCount++;

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
    public static final int key_glass_tabSelected = colorsCount++;
    public static final int key_glass_tabSelectedText = colorsCount++;
    public static final int key_glass_tabUnselected = colorsCount++;
    public static final int key_telegram_color = colorsCount++;
    public static final int key_iv_background = colorsCount++;
    public static final int key_iv_backgroundGray = colorsCount++;
    public static final int key_iv_navigationBackground = colorsCount++;
    public static final int key_actionBarActionModeDefaultSelector = colorsCount++;
    public static final int key_chat_messagePanelBackground = colorsCount++;
    public static final int key_chat_messagePanelShadow = colorsCount++;
    public static final int key_graySection = colorsCount++;
    public static final int key_graySectionText = colorsCount++;
    public static final int key_windowBackgroundWhiteBlueHeader = colorsCount++;
    public static final int key_windowBackgroundGrayShadow = colorsCount++;
    public static final int key_dialogBackground = colorsCount++;
    public static final int key_fastScrollInactive = colorsCount++;
    public static final int key_fastScrollActive = colorsCount++;
    public static final int key_fastScrollText = colorsCount++;
    public static final int key_settings_listSelector = colorsCount++;
    public static final int key_progressCircle = colorsCount++;

    // ScrollSlidingTextTabStrip keys.
    public static final int key_actionBarTabLine = colorsCount++;
    public static final int key_actionBarTabActiveText = colorsCount++;
    public static final int key_actionBarTabUnactiveText = colorsCount++;
    public static final int key_actionBarTabSelector = colorsCount++;

    // PullForegroundDrawable keys.
    public static final int key_chats_archivePullDownBackground = colorsCount++;
    public static final int key_chats_archivePullDownBackgroundActive = colorsCount++;
    public static final int key_avatar_backgroundArchivedHidden = colorsCount++;

    // DialogCell / TopicCell keys.
    public static final int key_chats_name = colorsCount++;
    public static final int key_chats_nameMessage = colorsCount++;
    public static final int key_chats_message = colorsCount++;
    public static final int key_chats_date = colorsCount++;
    public static final int key_chats_pinnedIcon = colorsCount++;
    public static final int key_chats_pinnedOverlay = colorsCount++;
    public static final int key_chats_unreadCounter = colorsCount++;
    public static final int key_chats_unreadCounterText = colorsCount++;
    public static final int key_chats_unreadCounterMuted = colorsCount++;
    public static final int key_chats_actionBackground = colorsCount++;
    public static final int key_avatar_text = colorsCount++;

    // Avatar background palette — mirrors upstream Theme.keys_avatar_background /
    // keys_avatar_background2. Order: Red, Orange, Violet, Green, Cyan, Blue, Pink.
    public static final int key_avatar_backgroundRed = colorsCount++;
    public static final int key_avatar_backgroundOrange = colorsCount++;
    public static final int key_avatar_backgroundViolet = colorsCount++;
    public static final int key_avatar_backgroundGreen = colorsCount++;
    public static final int key_avatar_backgroundCyan = colorsCount++;
    public static final int key_avatar_backgroundBlue = colorsCount++;
    public static final int key_avatar_backgroundPink = colorsCount++;
    public static final int key_avatar_background2Red = colorsCount++;
    public static final int key_avatar_background2Orange = colorsCount++;
    public static final int key_avatar_background2Violet = colorsCount++;
    public static final int key_avatar_background2Green = colorsCount++;
    public static final int key_avatar_background2Cyan = colorsCount++;
    public static final int key_avatar_background2Blue = colorsCount++;
    public static final int key_avatar_background2Pink = colorsCount++;

    public static final int[] keys_avatar_background = {
            key_avatar_backgroundRed, key_avatar_backgroundOrange, key_avatar_backgroundViolet,
            key_avatar_backgroundGreen, key_avatar_backgroundCyan, key_avatar_backgroundBlue,
            key_avatar_backgroundPink
    };
    public static final int[] keys_avatar_background2 = {
            key_avatar_background2Red, key_avatar_background2Orange, key_avatar_background2Violet,
            key_avatar_background2Green, key_avatar_background2Cyan, key_avatar_background2Blue,
            key_avatar_background2Pink
    };

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

    /** Mirrors upstream {@code Theme.avatar_backgroundPaint} — shared paint AvatarDrawable draws into. */
    public static final Paint avatar_backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Shared paint pool for cell-level views (TopicCell etc). Naming mirrors
    // upstream Theme.dialogs_*Paint so call sites stay aligned. Text size and
    // typeface are configured once in init(); colors are refreshed on every
    // applyThemeInternal() so theme switches re-tint the pool atomically.
    public static final TextPaint dialogs_namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    public static final TextPaint dialogs_messagePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    public static final TextPaint dialogs_timePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    public static final TextPaint dialogs_countTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    public static final Paint dialogs_countPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Muted counter background (gray pill for muted chats). */
    public static final Paint dialogs_countGrayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public static final Paint dialogs_pinnedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Pinned row background overlay — mirrors upstream DialogCell key_chats_pinnedOverlay. */
    public static final Paint dialogs_pinnedBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Small pin icon drawn next to time on pinned rows — mirrors upstream dialogs_pinnedDrawable2. */
    public static Drawable dialogs_pinnedDrawable2;
    /** NativeDo extension: bottom hairline separator paint shared across list cells. */
    public static final Paint dialogs_dividerPaint = new Paint();

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
        createDialogsResources();
        // Load vendored pin icon — mirrors upstream Theme.createDialogsResources().
        dialogs_pinnedDrawable2 = context.getResources().getDrawable(R.drawable.msg_pin_mini).mutate();
        SharedPreferences preferences = applicationContext.getSharedPreferences(THEME_CONFIG, Activity.MODE_PRIVATE);
        ThemeInfo themeInfo = themesDict.get(preferences.getString(THEME_CONFIG_ACTIVE, defaultTheme.getKey()));
        if (themeInfo == null) {
            themeInfo = defaultTheme;
        }
        if (themeInfo != currentTheme) {
            applyTheme(themeInfo, false, themeInfo == currentNightTheme);
        } else {
            // Same theme — still need to push current colors into the shared
            // paint pool now that text sizes / typefaces have been set.
            applyDialogsColors();
        }
    }

    /**
     * Configure the shared dialogs paint pool. Sizes depend on
     * {@link AndroidUtilities#density} so this must run after
     * {@code AndroidUtilities.init()}. Color is set separately in
     * {@link #applyDialogsColors()} since it follows the active theme.
     */
    private static void createDialogsResources() {
        dialogs_namePaint.setTextSize(AndroidUtilities.dp(16));
        dialogs_namePaint.setTypeface(AndroidUtilities.bold());
        dialogs_messagePaint.setTextSize(AndroidUtilities.dp(14));
        dialogs_timePaint.setTextSize(AndroidUtilities.dp(13));
        dialogs_countTextPaint.setTextSize(AndroidUtilities.dp(13));
        dialogs_countTextPaint.setTypeface(AndroidUtilities.bold());
    }

    /** Simplified port of upstream setDrawableColor — handles ShapeDrawable and falls through to PorterDuff. */
    public static void setDrawableColor(Drawable drawable, int color) {
        if (drawable == null) {
            return;
        }
        if (drawable instanceof android.graphics.drawable.ShapeDrawable) {
            ((android.graphics.drawable.ShapeDrawable) drawable).getPaint().setColor(color);
        } else {
            drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        }
    }

    /** Port of upstream setDrawableColorByKey. */
    public static void setDrawableColorByKey(Drawable drawable, int key) {
        setDrawableColor(drawable, getColor(key));
    }

    private static void applyDialogsColors() {
        dialogs_namePaint.setColor(getColor(key_chats_name));
        dialogs_messagePaint.setColor(getColor(key_chats_message));
        dialogs_timePaint.setColor(getColor(key_chats_date));
        dialogs_countTextPaint.setColor(getColor(key_chats_unreadCounterText));
        dialogs_countPaint.setColor(getColor(key_chats_unreadCounter));
        dialogs_countGrayPaint.setColor(getColor(key_chats_unreadCounterMuted));
        dialogs_pinnedPaint.setColor(getColor(key_chats_pinnedIcon));
        dialogs_pinnedBgPaint.setColor(getColor(key_chats_pinnedOverlay));
        setDrawableColorByKey(dialogs_pinnedDrawable2, key_chats_pinnedIcon);
        dialogs_dividerPaint.setColor(getColor(key_divider));
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

    public static int getColor(int key, ResourcesProvider resourcesProvider) {
        if (resourcesProvider != null) {
            Integer color = resourcesProvider.getColorOrNull(key);
            if (color != null) {
                return color;
            }
        }
        return getColor(key);
    }

    /** ADAPTED: NativeDo has no animated color blending; alias to getColor. */
    public static int getNonAnimatedColor(int key) {
        return getColor(key);
    }

    public static int multAlpha(int color, float multiply) {
        if (multiply == 1f) {
            return color;
        }
        int alpha = Math.max(0, Math.min(0xFF, (int) (Color.alpha(color) * multiply)));
        return (color & 0x00ffffff) | (alpha << 24);
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

    /** ADAPTED: 3-arg overload with radius for RecyclerListView selector. */
    public static RippleDrawable createSelectorDrawable(int color, int maskType, int radius) {
        Drawable maskDrawable = null;
        if (maskType == RIPPLE_MASK_ROUNDRECT_6DP) {
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            g.setColor(Color.WHITE);
            g.setCornerRadius(radius > 0 ? radius : AndroidUtilities.dp(6));
            maskDrawable = g;
            return new RippleDrawable(ColorStateList.valueOf(color), null, maskDrawable);
        }
        if (maskType == RIPPLE_MASK_ALL) {
            maskDrawable = new ColorDrawable(Color.WHITE);
        } else if (maskType == RIPPLE_MASK_CIRCLE_20DP) {
            maskDrawable = new Drawable() {
                @Override
                public void draw(Canvas canvas) {
                    Rect bounds = getBounds();
                    maskPaint.setColor(Color.WHITE);
                    canvas.drawCircle(bounds.centerX(), bounds.centerY(), radius, maskPaint);
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
        rippleDrawable.setRadius(radius);
        return rippleDrawable;
    }

    public static RippleDrawable getSelectorDrawable(boolean whiteBackground) {
        return createSelectorDrawable(
            getColor(whiteBackground ? key_actionBarDefaultSelector : key_listSelector),
            RIPPLE_MASK_ALL
        );
    }

    /** ADAPTED: getSelectorDrawable by color key for RecyclerListView. */
    public static Drawable getSelectorDrawable(int color, boolean whiteBackground) {
        return createSelectorDrawable(color, RIPPLE_MASK_ALL);
    }

    /** ADAPTED: createRadSelectorDrawable for RecyclerListView rounded selectors. */
    public static Drawable createRadSelectorDrawable(int color, int topRadius, int bottomRadius) {
        Drawable maskDrawable = new Drawable() {
            private final Paint maskPaintLocal = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path maskPath = new Path();
            private final RectF maskRect = new RectF();
            @Override
            public void draw(Canvas canvas) {
                Rect bounds = getBounds();
                maskPaintLocal.setColor(Color.WHITE);
                maskRect.set(bounds);
                maskPath.reset();
                float[] radii = new float[]{
                    topRadius, topRadius, topRadius, topRadius,
                    bottomRadius, bottomRadius, bottomRadius, bottomRadius
                };
                maskPath.addRoundRect(maskRect, radii, Path.Direction.CW);
                canvas.drawPath(maskPath, maskPaintLocal);
            }
            @Override public void setAlpha(int alpha) {}
            @Override public void setColorFilter(ColorFilter colorFilter) {}
            @Override public int getOpacity() { return PixelFormat.UNKNOWN; }
        };
        return new RippleDrawable(ColorStateList.valueOf(color), null, maskDrawable);
    }

    /** ADAPTED: createSimpleSelectorRoundRectDrawable for RecyclerListView. */
    public static Drawable createSimpleSelectorRoundRectDrawable(int radius, int color, int selectorColor, int backgroundColor) {
        Drawable maskDrawable = new Drawable() {
            private final Paint maskPaintLocal = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF maskRect = new RectF();
            @Override
            public void draw(Canvas canvas) {
                Rect bounds = getBounds();
                maskPaintLocal.setColor(Color.WHITE);
                maskRect.set(bounds);
                canvas.drawRoundRect(maskRect, radius, radius, maskPaintLocal);
            }
            @Override public void setAlpha(int alpha) {}
            @Override public void setColorFilter(ColorFilter colorFilter) {}
            @Override public int getOpacity() { return PixelFormat.UNKNOWN; }
        };
        return new RippleDrawable(ColorStateList.valueOf(selectorColor), null, maskDrawable);
    }

    /** ADAPTED: setSelectorDrawableColor for RecyclerListView. */
    public static void setSelectorDrawableColor(Drawable drawable, int color, boolean animated) {
        if (drawable instanceof RippleDrawable) {
            ((RippleDrawable) drawable).setColor(ColorStateList.valueOf(color));
        }
    }

    /** ADAPTED: setMaskDrawableRad for RecyclerListView. */
    public static void setMaskDrawableRad(Drawable drawable, int topRad, int bottomRad) {
        // No-op: mask radius changes are not critical for NativeDo's use case
    }

    /** ADAPTED: createRoundRectDrawable for RecyclerListView section backgrounds. */
    public static Drawable createRoundRectDrawable(int radius, int color) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        return new Drawable() {
            private final RectF rect = new RectF();
            @Override
            public void draw(Canvas canvas) {
                rect.set(getBounds());
                canvas.drawRoundRect(rect, radius, radius, paint);
            }
            @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
            @Override public void setColorFilter(ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
            @Override public int getOpacity() { return PixelFormat.TRANSPARENT; }
        };
    }

    /** ADAPTED: getThemeDrawable stub for RecyclerListView. */
    public static Drawable getThemeDrawable(String key) {
        return null;
    }

    /** ADAPTED: getThemePaint stub for RecyclerListView. */
    public static Paint getThemePaint(String paintKey) {
        return new Paint();
    }

    public static Drawable createCircleDrawable(int size, int color) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        return new Drawable() {
            private final RectF rect = new RectF();

            @Override
            public void draw(Canvas canvas) {
                Rect bounds = getBounds();
                rect.set(bounds);
                canvas.drawOval(rect, paint);
            }

            @Override
            public void setAlpha(int alpha) {
                paint.setAlpha(alpha);
            }

            @Override
            public void setColorFilter(ColorFilter colorFilter) {
                paint.setColorFilter(colorFilter);
            }

            @Override
            public int getOpacity() {
                return PixelFormat.TRANSPARENT;
            }

            @Override
            public int getIntrinsicWidth() {
                return size;
            }

            @Override
            public int getIntrinsicHeight() {
                return size;
            }
        };
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
        applyDialogsColors();
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
        themeInfo.colors.put(key_glass_tabSelected, 0xff1e7755);
        themeInfo.colors.put(key_glass_tabSelectedText, 0xff1e7755);
        themeInfo.colors.put(key_glass_tabUnselected, 0xff20251f);
        themeInfo.colors.put(key_telegram_color, 0xff1e7755);
        themeInfo.colors.put(key_iv_background, 0xffffffff);
        themeInfo.colors.put(key_iv_backgroundGray, 0xfff0f0f0);
        themeInfo.colors.put(key_iv_navigationBackground, 0xffffffff);
        themeInfo.colors.put(key_actionBarActionModeDefaultSelector, 0xffe2e2e2);
        themeInfo.colors.put(key_chat_messagePanelBackground, 0xffffffff);
        themeInfo.colors.put(key_chat_messagePanelShadow, 0xff000000);
        themeInfo.colors.put(key_graySection, 0xfff5f4ed);
        themeInfo.colors.put(key_graySectionText, 0xff6b6f63);
        themeInfo.colors.put(key_windowBackgroundWhiteBlueHeader, 0xff1e7755);
        themeInfo.colors.put(key_windowBackgroundGrayShadow, 0xffdfdace);
        themeInfo.colors.put(key_dialogBackground, 0xffffffff);
        themeInfo.colors.put(key_fastScrollInactive, 0xffc4c4c4);
        themeInfo.colors.put(key_fastScrollActive, 0xff999999);
        themeInfo.colors.put(key_fastScrollText, 0xffffffff);
        themeInfo.colors.put(key_settings_listSelector, 0xffe8e8d8);
        themeInfo.colors.put(key_progressCircle, 0xff1e7755);

        // ScrollSlidingTextTabStrip
        themeInfo.colors.put(key_actionBarTabLine, 0xff1e7755);
        themeInfo.colors.put(key_actionBarTabActiveText, 0xff1e7755);
        themeInfo.colors.put(key_actionBarTabUnactiveText, 0xff6b6f63);
        themeInfo.colors.put(key_actionBarTabSelector, 0x14000000);

        // PullForegroundDrawable
        themeInfo.colors.put(key_chats_archivePullDownBackground, 0xffc6c9ce);
        themeInfo.colors.put(key_chats_archivePullDownBackgroundActive, 0xff66a9e0);
        themeInfo.colors.put(key_avatar_backgroundArchivedHidden, 0xffc6c9ce);

        // DialogCell / TopicCell
        themeInfo.colors.put(key_chats_name, 0xff20251f);
        themeInfo.colors.put(key_chats_nameMessage, 0xff1e7755);
        themeInfo.colors.put(key_chats_message, 0xff6b6f63);
        themeInfo.colors.put(key_chats_date, 0xff95968a);
        themeInfo.colors.put(key_chats_pinnedIcon, 0xff95968a);
        themeInfo.colors.put(key_chats_pinnedOverlay, 0xfff0eee5);
        themeInfo.colors.put(key_chats_unreadCounter, 0xff1e7755);
        themeInfo.colors.put(key_chats_unreadCounterText, 0xffffffff);
        themeInfo.colors.put(key_chats_unreadCounterMuted, 0xffbcbeb1);
        themeInfo.colors.put(key_chats_actionBackground, 0xff1e7755);
        themeInfo.colors.put(key_avatar_text, 0xffffffff);

        // Avatar palette — matches upstream Theme defaults so monogram avatars
        // look identical across NativeDo and Telegram screenshots.
        themeInfo.colors.put(key_avatar_backgroundRed, 0xffFF845E);
        themeInfo.colors.put(key_avatar_backgroundOrange, 0xffFEBB5B);
        themeInfo.colors.put(key_avatar_backgroundViolet, 0xffB694F9);
        themeInfo.colors.put(key_avatar_backgroundGreen, 0xff9AD164);
        themeInfo.colors.put(key_avatar_backgroundCyan, 0xff5BCBE3);
        themeInfo.colors.put(key_avatar_backgroundBlue, 0xff5CAFFA);
        themeInfo.colors.put(key_avatar_backgroundPink, 0xffFF8AAC);
        themeInfo.colors.put(key_avatar_background2Red, 0xffD45246);
        themeInfo.colors.put(key_avatar_background2Orange, 0xffF68136);
        themeInfo.colors.put(key_avatar_background2Violet, 0xff6C61DF);
        themeInfo.colors.put(key_avatar_background2Green, 0xff46BA43);
        themeInfo.colors.put(key_avatar_background2Cyan, 0xff359AD4);
        themeInfo.colors.put(key_avatar_background2Blue, 0xff408ACF);
        themeInfo.colors.put(key_avatar_background2Pink, 0xffD95574);
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
        themeInfo.colors.put(key_glass_tabSelected, -10177041);
        themeInfo.colors.put(key_glass_tabSelectedText, -10177041);
        themeInfo.colors.put(key_glass_tabUnselected, -1);
        themeInfo.colors.put(key_telegram_color, -10177041);
        themeInfo.colors.put(key_iv_background, 0xff000000);
        themeInfo.colors.put(key_iv_backgroundGray, 0xff1f1f1f);
        themeInfo.colors.put(key_iv_navigationBackground, 0xff000000);
        themeInfo.colors.put(key_actionBarActionModeDefaultSelector, 0xff3a3a3a);
        themeInfo.colors.put(key_chat_messagePanelBackground, 0xff1c1c1e);
        themeInfo.colors.put(key_chat_messagePanelShadow, 0xff000000);
        themeInfo.colors.put(key_graySection, -15393241);
        themeInfo.colors.put(key_graySectionText, -8549479);
        themeInfo.colors.put(key_windowBackgroundWhiteBlueHeader, -10177041);
        themeInfo.colors.put(key_windowBackgroundGrayShadow, -1795162112);
        themeInfo.colors.put(key_dialogBackground, -14141368);
        themeInfo.colors.put(key_fastScrollInactive, 0xff3a3a3a);
        themeInfo.colors.put(key_fastScrollActive, 0xff5a5a5a);
        themeInfo.colors.put(key_fastScrollText, 0xffffffff);
        themeInfo.colors.put(key_settings_listSelector, 0xff2a2a2a);
        themeInfo.colors.put(key_progressCircle, -10177041);

        // ScrollSlidingTextTabStrip
        themeInfo.colors.put(key_actionBarTabLine, -10177041);
        themeInfo.colors.put(key_actionBarTabActiveText, -10177041);
        themeInfo.colors.put(key_actionBarTabUnactiveText, -8549479);
        themeInfo.colors.put(key_actionBarTabSelector, 0x1fffffff);

        // PullForegroundDrawable
        themeInfo.colors.put(key_chats_archivePullDownBackground, 0xff424850);
        themeInfo.colors.put(key_chats_archivePullDownBackgroundActive, 0xff336699);
        themeInfo.colors.put(key_avatar_backgroundArchivedHidden, 0xff424850);

        // DialogCell / TopicCell
        themeInfo.colors.put(key_chats_name, 0xffffffff);
        themeInfo.colors.put(key_chats_nameMessage, -10177041);
        themeInfo.colors.put(key_chats_message, 0xff8a8a8a);
        themeInfo.colors.put(key_chats_date, 0xff7a7a7a);
        themeInfo.colors.put(key_chats_pinnedIcon, 0xff7a7a7a);
        themeInfo.colors.put(key_chats_pinnedOverlay, 0xff1a1f26);
        themeInfo.colors.put(key_chats_unreadCounter, -10177041);
        themeInfo.colors.put(key_chats_unreadCounterText, 0xffffffff);
        themeInfo.colors.put(key_chats_unreadCounterMuted, 0xff464a52);
        themeInfo.colors.put(key_chats_actionBackground, -10177041);
        themeInfo.colors.put(key_avatar_text, 0xffffffff);

        // Avatar palette — share the light palette since both gradient stops
        // already sit comfortably under white monogram text. Matches upstream
        // dark-blue.attheme behavior (only Red/Orange override; rest fall back).
        themeInfo.colors.put(key_avatar_backgroundRed, 0xffFF845E);
        themeInfo.colors.put(key_avatar_backgroundOrange, 0xffFEBB5B);
        themeInfo.colors.put(key_avatar_backgroundViolet, 0xffB694F9);
        themeInfo.colors.put(key_avatar_backgroundGreen, 0xff9AD164);
        themeInfo.colors.put(key_avatar_backgroundCyan, 0xff5BCBE3);
        themeInfo.colors.put(key_avatar_backgroundBlue, 0xff5CAFFA);
        themeInfo.colors.put(key_avatar_backgroundPink, 0xffFF8AAC);
        themeInfo.colors.put(key_avatar_background2Red, 0xffD45246);
        themeInfo.colors.put(key_avatar_background2Orange, 0xffF68136);
        themeInfo.colors.put(key_avatar_background2Violet, 0xff6C61DF);
        themeInfo.colors.put(key_avatar_background2Green, 0xff46BA43);
        themeInfo.colors.put(key_avatar_background2Cyan, 0xff359AD4);
        themeInfo.colors.put(key_avatar_background2Blue, 0xff408ACF);
        themeInfo.colors.put(key_avatar_background2Pink, 0xffD95574);
        return themeInfo;
    }
}
