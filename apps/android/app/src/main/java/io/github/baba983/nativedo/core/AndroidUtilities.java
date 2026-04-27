// UPSTREAM-LEVEL: L2
// UPSTREAM-PATH:  org/telegram/messenger/AndroidUtilities.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// SUBSYSTEMS: host-utility=L2, display-metrics=L2
// HOST-WIRING:
//   - Uses AndroidX WindowCompat for NativeDo edge-to-edge status bar control.
// ADAPTED:
//   - Keeps only display metrics, dp conversion, keyboard, status-bar, cascade, and UI-thread helpers used by current ports.
// OMITTED:
//   - Telegram app-wide utilities, account state, media/cache helpers, and broad UI helpers outside the current navigation shell.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/messenger/AndroidUtilities.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/messenger/AndroidUtilities.java
 *
 * NativeDo milestone slice: keeps display metrics, dp conversion, keyboard hiding,
 * status-bar appearance, cascade animation, and UI-thread helpers used by the
 * navigation shell; uses WindowCompat for the AndroidX edge-to-edge host and
 * omits Telegram app-wide utilities.
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
package io.github.baba983.nativedo.core;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;

import androidx.core.view.WindowCompat;

public final class AndroidUtilities {

    public static final Point displaySize = new Point();
    public static final int LIGHT_STATUS_BAR_OVERLAY = 0x0f000000;
    public static final int DARK_STATUS_BAR_OVERLAY = 0x33000000;
    public static float density = 1.0f;
    public static int statusBarHeight;
    public static int navigationBarHeight;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static float xdpi;
    private static float ydpi;

    private AndroidUtilities() {
    }

    public static void init(Context context) {
        if (context == null) {
            return;
        }
        final DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        density = metrics.density;
        xdpi = metrics.xdpi;
        ydpi = metrics.ydpi;
        displaySize.set(metrics.widthPixels, metrics.heightPixels);

        final int statusBarId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        statusBarHeight = statusBarId > 0 ? context.getResources().getDimensionPixelSize(statusBarId) : 0;

        final int navigationBarId = context.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        navigationBarHeight = navigationBarId > 0 ? context.getResources().getDimensionPixelSize(navigationBarId) : 0;
    }

    public static int dp(float value) {
        return value == 0 ? 0 : (int) Math.ceil(density * value);
    }

    public static float dpf2(float value) {
        return density * value;
    }

    public static float lerp(float from, float to, float progress) {
        return from + (to - from) * progress;
    }

    public static float cascade(float fullAnimationT, float position, float count, float waveLength) {
        if (count <= 0) {
            return fullAnimationT;
        }
        final float waveDuration = 1f / count * Math.min(waveLength, count);
        final float waveOffset = position / count * (1f - waveDuration);
        return Math.max(0f, Math.min(1f, (fullAnimationT - waveOffset) / waveDuration));
    }

    public static int getPixelsInCM(float cm, boolean isX) {
        final float dpi = isX ? xdpi : ydpi;
        if (dpi == 0) {
            return dp(cm * 160 / 2.54f);
        }
        return (int) Math.ceil(cm / 2.54f * dpi);
    }

    public static void hideKeyboard(View view) {
        if (view == null) {
            return;
        }
        final InputMethodManager imm = view.getContext().getSystemService(InputMethodManager.class);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public static void runOnUIThread(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        mainHandler.post(runnable);
    }

    public static void cancelRunOnUIThread(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        mainHandler.removeCallbacks(runnable);
    }

    public static void setLightStatusBar(Window window, boolean enable) {
        setLightStatusBar(window, enable, false);
    }

    public static void setLightStatusBar(Window window, boolean enable, boolean forceTransparentStatusbar) {
        if (window == null) {
            return;
        }
        View decorView = window.getDecorView();
        WindowCompat.getInsetsController(window, decorView).setAppearanceLightStatusBars(enable);
        int statusBarColor = forceTransparentStatusbar ? Color.TRANSPARENT : (enable ? LIGHT_STATUS_BAR_OVERLAY : DARK_STATUS_BAR_OVERLAY);
        if (window.getStatusBarColor() != statusBarColor) {
            window.setStatusBarColor(statusBarColor);
        }
    }
}
