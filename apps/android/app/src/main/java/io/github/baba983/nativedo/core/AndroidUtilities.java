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
 * and UI-thread helpers used by the navigation shell; omits Telegram app-wide utilities.
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
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

public final class AndroidUtilities {

    public static final Point displaySize = new Point();
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
}
