// PORT-MODE: Exact Component Port
// UPSTREAM-SCOPE: org/telegram/messenger/AndroidUtilities.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// EXACT-SUBSYSTEMS: display-metrics, animation-helper
// HOST-WIRING:
//   - Uses AndroidX WindowCompat for NativeDo edge-to-edge status bar control.
// ADAPTED-BACKEND:
//   - None.
// ADAPTED-PRODUCT:
//   - Keeps only display metrics, dp conversion, keyboard, status-bar, cascade, and UI-thread helpers used by current ports.
//   - Adds Telegram's applySpring animator helper for copied interaction animations.
//   - Adds navigation-bar appearance wiring used by NativeDo browser-derived white pages.
//   - Adds software blur snapshot helpers for BottomSheetTabsOverlay because NativeDo does not
//     ship Telegram's native Utilities.stackBlurBitmap pipeline.
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

import android.animation.Animator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.os.Build;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.Window;
import android.view.animation.Interpolator;
import android.view.inputmethod.InputMethodManager;

import androidx.core.view.WindowCompat;

import io.github.baba983.nativedo.ui.actionbar.Theme;

public final class AndroidUtilities {

    public static final Point displaySize = new Point();
    public static final int LIGHT_STATUS_BAR_OVERLAY = 0x0f000000;
    public static final int DARK_STATUS_BAR_OVERLAY = 0x33000000;
    public static float density = 1.0f;
    public static int statusBarHeight;
    public static int navigationBarHeight;
    public static boolean makingGlobalBlurBitmap;
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

    public static float distance(float x1, float y1, float x2, float y2) {
        return (float) Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
    }

    public static float lerp(float from, float to, float progress) {
        return from + (to - from) * progress;
    }

    public static float computePerceivedBrightness(int color) {
        return (android.graphics.Color.red(color) * 0.2126f + android.graphics.Color.green(color) * 0.7152f + android.graphics.Color.blue(color) * 0.0722f) / 255f;
    }

    public static void adjustSaturationColorMatrix(ColorMatrix colorMatrix, float saturation) {
        if (colorMatrix == null) {
            return;
        }
        float x = 1 + saturation;
        final float lumR = 0.3086f;
        final float lumG = 0.6094f;
        final float lumB = 0.0820f;

        colorMatrix.postConcat(new ColorMatrix(new float[]{
                lumR * (1 - x) + x, lumG * (1 - x), lumB * (1 - x), 0, 0,
                lumR * (1 - x), lumG * (1 - x) + x, lumB * (1 - x), 0, 0,
                lumR * (1 - x), lumG * (1 - x), lumB * (1 - x) + x, 0, 0,
                0, 0, 0, 1, 0
        }));
    }

    public static Bitmap makeBlurBitmap(View view) {
        return makeBlurBitmap(view, 6f, 7);
    }

    public static Bitmap makeBlurBitmap(View view, float downscale, int maxRadius) {
        if (view == null || view.getWidth() <= 0 || view.getHeight() <= 0) {
            return null;
        }
        int width = Math.max(1, (int) (view.getWidth() / downscale));
        int height = Math.max(1, (int) (view.getHeight() / downscale));
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(1.0f / downscale, 1.0f / downscale);
        canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        view.draw(canvas);
        stackBlurBitmap(bitmap, Math.max(maxRadius, Math.max(width, height) / 180));
        return bitmap;
    }

    public static float cascade(float fullAnimationT, float position, float count, float waveLength) {
        if (count <= 0) {
            return fullAnimationT;
        }
        final float waveDuration = 1f / count * Math.min(waveLength, count);
        final float waveOffset = position / count * (1f - waveDuration);
        return Math.max(0f, Math.min(1f, (fullAnimationT - waveOffset) / waveDuration));
    }

    public static void applySpring(Animator animator, double stiffness, double damping) {
        applySpring(animator, stiffness, damping, 1, 0);
    }

    public static void applySpring(Animator animator, double stiffness, double damping, double mass, double initialVelocity) {
        final double w0 = Math.sqrt(stiffness / mass);
        final double zeta = damping / (2.0 * Math.sqrt(stiffness * mass));
        final double wd;
        final double a;
        final double b;
        if (zeta < 1) {
            wd = w0 * Math.sqrt(1.0 - zeta * zeta);
            a = 1.0;
            b = (zeta * w0 + -initialVelocity) / wd;
        } else {
            wd = 0.0;
            a = 1.0;
            b = -initialVelocity + w0;
        }
        final double threshold = 0.0025;
        final double duration = Math.log(threshold) / (-zeta * w0);
        animator.setDuration((long) (duration * 1000L));
        animator.setInterpolator(new Interpolator() {
            @Override
            public float getInterpolation(float t) {
                if (zeta < 1) {
                    return (float) (1.0 - Math.exp(-t * zeta * w0) * (a * Math.cos(wd * t) + b * Math.sin(wd * t)));
                } else {
                    return (float) (1.0 - (a + b * t) * Math.exp(-t * w0));
                }
            }
        });
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
        runOnUIThread(runnable, 0);
    }

    public static void runOnUIThread(Runnable runnable, long delay) {
        if (runnable == null) {
            return;
        }
        if (delay <= 0) {
            mainHandler.post(runnable);
        } else {
            mainHandler.postDelayed(runnable, delay);
        }
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

    public static void setLightNavigationBar(Window window, boolean enable) {
        if (window == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        View decorView = window.getDecorView();
        WindowCompat.getInsetsController(window, decorView).setAppearanceLightNavigationBars(enable);
    }

    private static void stackBlurBitmap(Bitmap bitmap, int radius) {
        if (bitmap == null || radius < 1) {
            return;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width == 0 || height == 0) {
            return;
        }

        int pixelCount = width * height;
        int[] pixels = new int[pixelCount];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        int[] red = new int[pixelCount];
        int[] green = new int[pixelCount];
        int[] blue = new int[pixelCount];

        int widthMinusOne = width - 1;
        int heightMinusOne = height - 1;
        int div = radius + radius + 1;

        int[] divSumLookup = new int[256 * (((div + 1) >> 1) * ((div + 1) >> 1))];
        for (int i = 0; i < divSumLookup.length; i++) {
            divSumLookup[i] = i / (((div + 1) >> 1) * ((div + 1) >> 1));
        }

        int[][] stack = new int[div][3];
        int[] min = new int[Math.max(width, height)];

        int yi = 0;
        int yw = 0;
        for (int y = 0; y < height; y++) {
            int rInSum = 0;
            int gInSum = 0;
            int bInSum = 0;
            int rSum = 0;
            int gSum = 0;
            int bSum = 0;
            int rOutSum = 0;
            int gOutSum = 0;
            int bOutSum = 0;

            for (int i = -radius; i <= radius; i++) {
                int pixel = pixels[yi + clamp(i, 0, widthMinusOne)];
                int[] sir = stack[i + radius];
                sir[0] = (pixel >> 16) & 0xff;
                sir[1] = (pixel >> 8) & 0xff;
                sir[2] = pixel & 0xff;
                int rbs = radius + 1 - Math.abs(i);
                rSum += sir[0] * rbs;
                gSum += sir[1] * rbs;
                bSum += sir[2] * rbs;
                if (i > 0) {
                    rInSum += sir[0];
                    gInSum += sir[1];
                    bInSum += sir[2];
                } else {
                    rOutSum += sir[0];
                    gOutSum += sir[1];
                    bOutSum += sir[2];
                }
            }

            int stackPointer = radius;
            for (int x = 0; x < width; x++) {
                red[yi] = divSumLookup[rSum];
                green[yi] = divSumLookup[gSum];
                blue[yi] = divSumLookup[bSum];

                rSum -= rOutSum;
                gSum -= gOutSum;
                bSum -= bOutSum;

                int stackStart = stackPointer - radius + div;
                int[] sir = stack[stackStart % div];

                rOutSum -= sir[0];
                gOutSum -= sir[1];
                bOutSum -= sir[2];

                if (y == 0) {
                    min[x] = Math.min(x + radius + 1, widthMinusOne);
                }
                int pixel = pixels[yw + min[x]];

                sir[0] = (pixel >> 16) & 0xff;
                sir[1] = (pixel >> 8) & 0xff;
                sir[2] = pixel & 0xff;

                rInSum += sir[0];
                gInSum += sir[1];
                bInSum += sir[2];

                rSum += rInSum;
                gSum += gInSum;
                bSum += bInSum;

                stackPointer = (stackPointer + 1) % div;
                sir = stack[stackPointer % div];

                rOutSum += sir[0];
                gOutSum += sir[1];
                bOutSum += sir[2];

                rInSum -= sir[0];
                gInSum -= sir[1];
                bInSum -= sir[2];

                yi++;
            }
            yw += width;
        }

        for (int x = 0; x < width; x++) {
            int rInSum = 0;
            int gInSum = 0;
            int bInSum = 0;
            int rSum = 0;
            int gSum = 0;
            int bSum = 0;
            int rOutSum = 0;
            int gOutSum = 0;
            int bOutSum = 0;
            int yp = -radius * width;

            for (int i = -radius; i <= radius; i++) {
                yi = Math.max(0, yp) + x;
                int[] sir = stack[i + radius];
                sir[0] = red[yi];
                sir[1] = green[yi];
                sir[2] = blue[yi];
                int rbs = radius + 1 - Math.abs(i);
                rSum += red[yi] * rbs;
                gSum += green[yi] * rbs;
                bSum += blue[yi] * rbs;
                if (i > 0) {
                    rInSum += sir[0];
                    gInSum += sir[1];
                    bInSum += sir[2];
                } else {
                    rOutSum += sir[0];
                    gOutSum += sir[1];
                    bOutSum += sir[2];
                }
                if (i < heightMinusOne) {
                    yp += width;
                }
            }

            yi = x;
            int stackPointer = radius;
            for (int y = 0; y < height; y++) {
                pixels[yi] = (pixels[yi] & 0xff000000)
                        | (divSumLookup[rSum] << 16)
                        | (divSumLookup[gSum] << 8)
                        | divSumLookup[bSum];

                rSum -= rOutSum;
                gSum -= gOutSum;
                bSum -= bOutSum;

                int stackStart = stackPointer - radius + div;
                int[] sir = stack[stackStart % div];

                rOutSum -= sir[0];
                gOutSum -= sir[1];
                bOutSum -= sir[2];

                if (x == 0) {
                    min[y] = Math.min(y + radius + 1, heightMinusOne) * width;
                }
                int p = x + min[y];

                sir[0] = red[p];
                sir[1] = green[p];
                sir[2] = blue[p];

                rInSum += sir[0];
                gInSum += sir[1];
                bInSum += sir[2];

                rSum += rInSum;
                gSum += gInSum;
                bSum += bInSum;

                stackPointer = (stackPointer + 1) % div;
                sir = stack[stackPointer];

                rOutSum += sir[0];
                gOutSum += sir[1];
                bOutSum += sir[2];

                rInSum -= sir[0];
                gInSum -= sir[1];
                bInSum -= sir[2];

                yi += width;
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
