/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.messenger;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Build;
import android.graphics.Point;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.MotionEvent;
import android.view.Window;
import android.widget.HorizontalScrollView;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.inputmethod.InputMethodManager;

import androidx.core.view.WindowCompat;
import androidx.viewpager.widget.ViewPager;

import android.graphics.Typeface;

import java.util.HashMap;

import org.telegram.ui.ActionBar.Theme;

public final class AndroidUtilities {

    public static final Point displaySize = new Point();
    public static final int LIGHT_STATUS_BAR_OVERLAY = 0x0f000000;
    public static final int DARK_STATUS_BAR_OVERLAY = 0x33000000;
    public static float density = 1.0f;
    public static int statusBarHeight;
    public static int navigationBarHeight;
    public static boolean makingGlobalBlurBitmap;
    public static final String STICKERS_PLACEHOLDER_PACK_NAME = "Placeholders";

    /** Shared temporary RectF for drawing operations. Matches upstream AndroidUtilities.rectTmp. */
    public static final RectF rectTmp = new RectF();
    /** Shared temporary RectF (second instance). Matches upstream AndroidUtilities.rectTmp2. */
    public static final RectF rectTmp2 = new RectF();
    public static final android.graphics.Rect rectTmp2I = new android.graphics.Rect();
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

    /**
     * Rounded density-pixel conversion. Matches upstream {@code AndroidUtilities.dpr}.
     * Used by some Telegram-derived components for fractional dp positioning.
     */
    public static int dpr(float value) {
        return value == 0 ? 0 : Math.round(density * value);
    }

    public static float dpf2(float value) {
        return density * value;
    }

    public static float distance(float x1, float y1, float x2, float y2) {
        return (float) Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
    }

    /** ADAPTED: 4-arg RectF lerp variant matches upstream AndroidUtilities.lerp. */
    public static void lerp(RectF a, RectF b, float f, RectF to) {
        if (to != null && a != null && b != null) {
            to.set(
                a.left + (b.left - a.left) * f,
                a.top + (b.top - a.top) * f,
                a.right + (b.right - a.right) * f,
                a.bottom + (b.bottom - a.bottom) * f
            );
        }
    }

    public static float lerp(float from, float to, float progress) {
        return from + (to - from) * progress;
    }

    public static int lerp(int a, int b, float f) {
        return (int) (a + f * (b - a));
    }

    public static float lerp(float a, float b, float c, float middle, float f) {
        if (f < middle) return lerp(a, b, f / middle);
        return lerp(b, c, (f - middle) / (1.0f - middle));
    }

    public static float ilerp(int x, int a, int b) {
        return (float) (x - a) / (b - a);
    }

    public static float ilerp(float x, float a, float b) {
        return (x - a) / (b - a);
    }

    /** 3-point interpolation: at t=-1 returns atMinusOne, t=0 returns atZero, t=1 returns atPlusOne. */
    public static float lerp3(float atMinusOne, float atZero, float atPlusOne, float t) {
        if (t <= 0) {
            return atZero + (atMinusOne - atZero) * -t;
        } else {
            return atZero + (atPlusOne - atZero) * t;
        }
    }

    /** 3-point color interpolation matching upstream AndroidUtilities.lerpColor3. */
    public static int lerpColor3(int atMinusOne, int atZero, int atPlusOne, float t) {
        float r, g, b, a;
        if (t <= 0) {
            float p = -t;
            r = Color.red(atZero) + (Color.red(atMinusOne) - Color.red(atZero)) * p;
            g = Color.green(atZero) + (Color.green(atMinusOne) - Color.green(atZero)) * p;
            b = Color.blue(atZero) + (Color.blue(atMinusOne) - Color.blue(atZero)) * p;
            a = Color.alpha(atZero) + (Color.alpha(atMinusOne) - Color.alpha(atZero)) * p;
        } else {
            float p = t;
            r = Color.red(atZero) + (Color.red(atPlusOne) - Color.red(atZero)) * p;
            g = Color.green(atZero) + (Color.green(atPlusOne) - Color.green(atZero)) * p;
            b = Color.blue(atZero) + (Color.blue(atPlusOne) - Color.blue(atZero)) * p;
            a = Color.alpha(atZero) + (Color.alpha(atPlusOne) - Color.alpha(atZero)) * p;
        }
        return Color.argb(
            Math.max(0, Math.min(255, Math.round(a))),
            Math.max(0, Math.min(255, Math.round(r))),
            Math.max(0, Math.min(255, Math.round(g))),
            Math.max(0, Math.min(255, Math.round(b)))
        );
    }

    /** Set RectF in dp units, matching upstream AndroidUtilities.setRectD. */
    public static void setRectD(RectF rect, float left, float top, float right, float bottom) {
        rect.set(dpf2(left), dpf2(top), dpf2(right), dpf2(bottom));
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

    public static void setRectToRect(Matrix matrix, RectF src, RectF dst, int rotation, int invert, boolean translate) {
        float sx;
        float sy;
        boolean xLarger = false;
        if (rotation == 90 || rotation == 270) {
            sx = dst.height() / src.width();
            sy = dst.width() / src.height();
        } else {
            sx = dst.width() / src.width();
            sy = dst.height() / src.height();
        }
        if (sx < sy) {
            sx = sy;
            xLarger = true;
        } else {
            sy = sx;
        }
        if (translate) {
            matrix.setTranslate(dst.left, dst.top);
        }
        if (rotation == 90) {
            matrix.preRotate(90);
            if (invert == 1) {
                matrix.preScale(-1, 1);
            } else if (invert == 2) {
                matrix.preScale(1, -1);
            }
            matrix.preTranslate(0, -dst.width());
        } else if (rotation == 180) {
            matrix.preRotate(180);
            if (invert == 1) {
                matrix.preScale(-1, 1);
            } else if (invert == 2) {
                matrix.preScale(1, -1);
            }
            matrix.preTranslate(-dst.width(), -dst.height());
        } else if (rotation == 270) {
            matrix.preRotate(270);
            if (invert == 1) {
                matrix.preScale(-1, 1);
            } else if (invert == 2) {
                matrix.preScale(1, -1);
            }
            matrix.preTranslate(-dst.height(), 0);
        } else if (invert == 1) {
            matrix.preScale(-1, 1);
            matrix.preTranslate(-dst.width(), 0);
        } else if (invert == 2) {
            matrix.preScale(1, -1);
            matrix.preTranslate(0, -dst.height());
        }

        if (xLarger) {
            matrix.preTranslate((dst.width() - src.width() * sx) / 2, 0);
        } else {
            matrix.preTranslate(0, (dst.height() - src.height() * sy) / 2);
        }
        matrix.preScale(sx, sy);
        matrix.preTranslate(-src.left, -src.top);
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

    public static void setLightNavigationBar(android.app.Dialog dialog, boolean enable) {
        if (dialog != null && dialog.getWindow() != null) {
            setLightNavigationBar(dialog.getWindow(), enable);
        }
    }

    public static void setLightStatusBar(View view, boolean enable) {
    }

    public static void setLightNavigationBar(View view, boolean enable) {
    }

    public static CharSequence replaceSingleTag(String text, Runnable action) {
        return text;
    }

    public static void setScrollViewEdgeEffectColor(HorizontalScrollView scrollView, int color) {
    }

    public static void setViewPagerEdgeEffectColor(ViewPager viewPager, int color) {
    }

    public static void setNavigationBarColor(android.app.Dialog dialog, int color) {
        if (dialog != null && dialog.getWindow() != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            dialog.getWindow().setNavigationBarColor(color);
        }
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

    // ── Telegram-shaped helpers required by ported components ──────────

    private static Boolean isTabletValue;

    /**
     * Returns {@code true} on screens ≥ 600dp shortest side.
     * Matches upstream {@code AndroidUtilities.isTablet()}.
     */
    public static boolean isTablet() {
        if (isTabletValue == null) {
            isTabletValue = Math.min(displaySize.x, displaySize.y) >= dp(600);
        }
        return isTabletValue;
    }

    public static int getStatusBarHeight(Context context) {
        if (context == null) return 0;
        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resourceId > 0 ? context.getResources().getDimensionPixelSize(resourceId) : 0;
    }

    private static Typeface typefaceBold;

    /**
     * Returns a bold typeface. Matches upstream {@code AndroidUtilities.bold()}.
     * Caches the result so repeated calls are cheap.
     */
    public static Typeface bold() {
        if (typefaceBold == null) {
            typefaceBold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD);
        }
        return typefaceBold;
    }

    private static Typeface typefaceMedium;

    /**
     * Returns a medium typeface. Matches upstream {@code AndroidUtilities.medium()}.
     * Falls back to bold on Android versions without a distinct medium weight.
     */
    public static Typeface medium() {
        if (typefaceMedium == null) {
            typefaceMedium = Typeface.create(Typeface.DEFAULT, Typeface.BOLD);
        }
        return typefaceMedium;
    }

    /**
     * Returns a typeface with the given style. Matches upstream
     * {@code AndroidUtilities.getTypeface(String)} for the common case.
     */
    public static Typeface getTypeface(String fontName) {
        return Typeface.DEFAULT;
    }

    /**
     * Returns {@code true} when the device is in right-to-left layout.
     * NativeDo does not support RTL; always returns {@code false}.
     * Matches upstream {@code AndroidUtilities.isRTL} field.
     */
    public static boolean isRTL = false;

    /**
     * Unpacks the first int from a long packed with {@code packA/B}.
     * Matches upstream {@code AndroidUtilities.unpackA(long)}.
     */
    public static int unpackA(long v) {
        return (int) (v >> 32);
    }

    /**
     * Unpacks the second int from a long packed with {@code packA/B}.
     * Matches upstream {@code AndroidUtilities.unpackB(long)}.
     */
    public static int unpackB(long v) {
        return (int) v;
    }

    /**
     * Packs two ints into a long.
     */
    public static long pack(int a, int b) {
        return ((long) a << 32) | (b & 0xFFFFFFFFL);
    }

    /** Shared decelerate interpolator. Matches upstream AndroidUtilities.decelerateInterpolator. */
    public static final DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator();

    /** Shared accelerate interpolator. Matches upstream AndroidUtilities.accelerateInterpolator. */
    public static final android.view.animation.AccelerateInterpolator accelerateInterpolator = new android.view.animation.AccelerateInterpolator();
    public static final String TYPEFACE_ROBOTO_MEDIUM = "fonts/rmedium.ttf";

    public static int screenRefreshRate = 60;

    /** Stub of upstream {@code AndroidUtilities.getPhotoSize()} — long-side cap for default photo download. */
    public static int getPhotoSize() {
        return getPhotoSize(false);
    }

    public static int getPhotoSize(boolean huge) {
        return huge ? 2560 : 1280;
    }

    /** Stub of upstream {@code AndroidUtilities.getPatternColor}. */
    public static int getPatternColor(int color) {
        return 0xFFFFFFFF;
    }

    /** Stub of upstream {@code AndroidUtilities.getPatternSideColor}. */
    public static int getPatternSideColor(int color) {
        return 0xFFFFFFFF;
    }

    public static int getOffsetColor(int color1, int color2, float progress, float alpha) {
        int blended = androidx.core.graphics.ColorUtils.blendARGB(color1, color2, progress);
        return androidx.core.graphics.ColorUtils.setAlphaComponent(blended, Math.round(Color.alpha(blended) * alpha));
    }

    public static int blendOver(int foreground, int background) {
        return androidx.core.graphics.ColorUtils.compositeColors(foreground, background);
    }

    public static void vibrateCursor(View view) {
        if (view != null) {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.TEXT_HANDLE_MOVE);
        }
    }

    public static boolean hasDialogOnTop(org.telegram.ui.ActionBar.BaseFragment fragment) {
        return false;
    }

    /** Stub of upstream {@code AndroidUtilities.createEmptyFile}. */
    public static boolean createEmptyFile(java.io.File file) {
        try {
            if (file.exists()) {
                return true;
            }
            java.io.File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            return file.createNewFile();
        } catch (java.io.IOException e) {
            return false;
        }
    }

    /** Stub of upstream {@code AndroidUtilities.parseInt}. */
    public static int parseInt(String s) {
        if (s == null) return 0;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public static Integer parseInt(CharSequence s) {
        if (s == null) return 0;
        try {
            return Integer.parseInt(s.toString());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /** Stub of upstream {@code AndroidUtilities.getRealScreenSize}. */
    public static android.graphics.Point getRealScreenSize() {
        return new android.graphics.Point(1080, 1920);
    }

    /** Stub of upstream {@code AndroidUtilities.roundMessageInset} — extra inset for round-video bubbles. */
    public static int roundMessageInset = 0;

    /** Stub of upstream {@code AndroidUtilities.recycleBitmaps}. */
    public static void recycleBitmaps(java.util.ArrayList<android.graphics.Bitmap> bitmaps) {
        if (bitmaps == null) return;
        for (android.graphics.Bitmap b : bitmaps) {
            if (b != null && !b.isRecycled()) {
                b.recycle();
            }
        }
    }

    /** Stub of upstream {@code AndroidUtilities.getPath(Uri)}. */
    public static String getPath(android.net.Uri uri) {
        return uri == null ? null : uri.getPath();
    }

    /** Stub of upstream {@code AndroidUtilities.createVideoThumbnail}. */
    public static android.graphics.Bitmap createVideoThumbnail(String filePath, int kind) {
        return null;
    }

    /** Stub of upstream {@code AndroidUtilities.getCacheDir()}. */
    public static java.io.File getCacheDir() {
        if (ApplicationLoader.applicationContext != null) {
            return ApplicationLoader.applicationContext.getCacheDir();
        }
        return new java.io.File(System.getProperty("java.io.tmpdir", "/tmp"));
    }

    /** Stub of upstream {@code AndroidUtilities.formatFileSize}. */
    public static String formatFileSize(long size) {
        return formatFileSize(size, false);
    }

    /** Stub of upstream {@code AndroidUtilities.getImageOrientation}. */
    public static android.util.Pair<Integer, Integer> getImageOrientation(java.io.InputStream is) {
        return new android.util.Pair<>(0, 0);
    }

    public static android.util.Pair<Integer, Integer> getImageOrientation(java.io.File file) {
        return new android.util.Pair<>(0, 0);
    }

    public static android.util.Pair<Integer, Integer> getImageOrientation(String path) {
        return new android.util.Pair<>(0, 0);
    }

    public static int getAverageColor(int color1, int color2) {
        return color1;
    }

    public static int getAverageColor(android.graphics.Bitmap bitmap) {
        return 0xFF808080;
    }

    public static void recycleBitmap(android.graphics.Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    public static void addMediaToGallery(String filePath) {
    }

    public static void addMediaToGallery(java.io.File file) {
    }

    public static void getBitmapFromSurface(android.view.SurfaceView surfaceView, android.graphics.Bitmap bitmap, Runnable onDone) {
        if (onDone != null) onDone.run();
    }

    public static java.util.ArrayList<java.io.File> getRootDirs() {
        return new java.util.ArrayList<>();
    }

    public static java.util.ArrayList<java.io.File> getDataDirs() {
        return new java.util.ArrayList<>();
    }

    public static String formatFileSize(long size, boolean removeZero) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.1f KB", size / 1024.0);
        } else if (size < 1024L * 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.1f MB", size / (1024.0 * 1024));
        } else {
            return String.format(java.util.Locale.US, "%.1f GB", size / (1024.0 * 1024 * 1024));
        }
    }

    /** ADAPTED: simple ViewGroup forEach helper (subset of upstream AndroidUtilities.forEachViews). */
    public static void forEachViews(android.view.ViewGroup container, java.util.function.Consumer<View> action) {
        if (container == null || action == null) return;
        for (int i = 0, n = container.getChildCount(); i < n; i++) {
            action.accept(container.getChildAt(i));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // The following helpers are copied from upstream AndroidUtilities. They
    // are referenced by PhotoViewer / ImageReceiver / etc. NativeDo's
    // simplified AndroidUtilities omitted them; we add them back as ports of
    // the upstream implementation so vendor code compiles unchanged.
    // ─────────────────────────────────────────────────────────────────────

    public static int touchSlop;
    public static boolean isInMultiwindow;
    public static boolean incorrectDisplaySizeFix;

    public static void updateViewVisibilityAnimated(View view, boolean show) {
        updateViewVisibilityAnimated(view, show, 1f, true, true);
    }

    public static void updateViewVisibilityAnimated(View view, boolean show, float scaleFactor, boolean animated) {
        updateViewVisibilityAnimated(view, show, scaleFactor, true, animated);
    }

    public static void updateViewVisibilityAnimated(View view, boolean show, float scaleFactor, boolean goneOnHide, boolean animated) {
        updateViewVisibilityAnimated(view, show, scaleFactor, goneOnHide, 1f, animated, null);
    }

    public static void updateViewVisibilityAnimated(View view, boolean show, float scaleFactor, boolean goneOnHide, float maxAlpha, boolean animated, android.animation.ValueAnimator.AnimatorUpdateListener onUpdate) {
        if (view == null) {
            return;
        }
        if (view.getParent() == null) {
            animated = false;
        }
        if (!animated) {
            view.animate().setListener(null).cancel();
            view.setVisibility(show ? View.VISIBLE : (goneOnHide ? View.GONE : View.INVISIBLE));
            view.setTag(show ? 1 : null);
            view.setAlpha(maxAlpha);
            view.setScaleX(1f);
            view.setScaleY(1f);
        } else if (show && view.getTag() == null) {
            view.animate().setListener(null).cancel();
            if (view.getVisibility() != View.VISIBLE) {
                view.setVisibility(View.VISIBLE);
                view.setAlpha(0f);
                view.setScaleX(scaleFactor);
                view.setScaleY(scaleFactor);
            }
            view.animate().alpha(maxAlpha).scaleY(1f).scaleX(1f).setDuration(150).setUpdateListener(onUpdate).start();
            view.setTag(1);
        } else if (!show && view.getTag() != null) {
            view.animate().setListener(null).cancel();
            view.animate().alpha(0).scaleY(scaleFactor).scaleX(scaleFactor).setListener(new org.telegram.ui.Components.HideViewAfterAnimation(view, goneOnHide ? View.GONE : View.INVISIBLE)).setDuration(150).setUpdateListener(onUpdate).start();
            view.setTag(null);
        }
    }

    public static void removeFromParent(View view) {
        if (view != null && view.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) view.getParent()).removeView(view);
        }
    }

    public static void addToClipboard(CharSequence str) {
        try {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("label", str);
            clipboard.setPrimaryClip(clip);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static String formatShortDuration(int duration) {
        return formatLongDuration(duration, false);
    }

    public static String formatLongDuration(int duration) {
        return formatLongDuration(duration, true);
    }

    private static String formatLongDuration(int duration, boolean longFormat) {
        int h = duration / 3600;
        int m = duration / 60 % 60;
        int s = duration % 60;
        if (longFormat || h != 0) {
            return String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, s);
        }
        return String.format(java.util.Locale.ROOT, "%d:%02d", m, s);
    }

    public static String formatTimestamp(int seconds) {
        return formatShortDuration(seconds);
    }

    public static void logFlagSecure() {
        // No-op stub. Upstream logs whether the activity has FLAG_SECURE set.
    }

    public static boolean shouldShowUrlInAlert(String url) {
        // NativeDo doesn't intercept URLs; always let WebView/Browser handle them.
        return false;
    }

    public static boolean shouldShowClipboardToast() {
        return android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S;
    }

    public static boolean isAccessibilityScreenReaderEnabled() {
        try {
            android.view.accessibility.AccessibilityManager am = (android.view.accessibility.AccessibilityManager)
                    ApplicationLoader.applicationContext.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE);
            return am != null && am.isEnabled() && am.isTouchExplorationEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean checkInlinePermissions(android.app.Activity context) {
        // Upstream checks Settings.canDrawOverlays. PhotoViewer guards PiP behind this.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
            return true;
        }
        return android.provider.Settings.canDrawOverlays(context);
    }

    public static boolean isInPictureInPictureMode(android.app.Activity activity) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && activity != null) {
            return activity.isInPictureInPictureMode();
        }
        return false;
    }

    public static void addLinksSafe(android.text.Spannable spannable, int linkMask, boolean sensitive, boolean internalOnly) {
        try {
            android.text.util.Linkify.addLinks(spannable, linkMask);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void openForView(org.telegram.tgnet.TLObject obj, android.app.Activity activity) {
        // No-op stub: NativeDo doesn't yet have a "open file with external app" flow.
    }

    public static void openForView(MessageObject messageObject, android.app.Activity parentActivity, org.telegram.ui.ActionBar.Theme.ResourcesProvider resourcesProvider, boolean useDownloadFolder) {
        // No-op stub.
    }

    public static byte[] getStringBytes(String src) {
        try {
            return src.getBytes("UTF-8");
        } catch (Exception ignore) {
            return src.getBytes();
        }
    }

    public static String readRes(int resId) {
        if (ApplicationLoader.applicationContext == null) {
            return "";
        }
        try (java.io.InputStream inputStream = ApplicationLoader.applicationContext.getResources().openRawResource(resId);
             java.util.Scanner scanner = new java.util.Scanner(inputStream).useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        } catch (Exception e) {
            FileLog.e(e);
            return "";
        }
    }

    public static Activity findActivity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    public static String getHostAuthority(String url, boolean removeWww) {
        if (url == null) {
            return "";
        }
        String host = "";
        try {
            host = Uri.parse(url).getHost();
        } catch (Exception ignore) {
        }
        if (host == null) {
            return "";
        }
        host = host.toLowerCase(java.util.Locale.US);
        if (removeWww && host.startsWith("www.")) {
            host = host.substring(4);
        }
        return host;
    }

    public static String replaceTags(String value) {
        return value == null ? "" : value;
    }

    public static boolean isPunctuationCharacter(char c) {
        return c == ' ' || c == '\n' || c == '.' || c == ',' || c == ':' || c == ';' || c == '!' || c == '?';
    }

    public static boolean isRTL(CharSequence value) {
        if (value == null || value.length() == 0) {
            return false;
        }
        byte direction = Character.getDirectionality(value.charAt(0));
        return direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                || direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
                || direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING
                || direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE;
    }

    public static boolean showKeyboard(View view) {
        if (view == null) {
            return false;
        }
        InputMethodManager imm = view.getContext().getSystemService(InputMethodManager.class);
        if (imm != null) {
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
            return true;
        }
        return false;
    }

    public static void updateViewShow(View view, boolean show, boolean scale, boolean animated) {
        if (view == null) {
            return;
        }
        view.setVisibility(show ? View.VISIBLE : View.GONE);
        view.setAlpha(show ? 1f : 0f);
        if (scale) {
            view.setScaleX(show ? 1f : .8f);
            view.setScaleY(show ? 1f : .8f);
        }
    }

    public static CharSequence highlightText(CharSequence text, String query, Theme.ResourcesProvider resourcesProvider) {
        if (TextUtils.isEmpty(query) || TextUtils.isEmpty(text)) {
            return null;
        }
        String source = text.toString().toLowerCase();
        String needle = query.toLowerCase();
        SpannableStringBuilder builder = SpannableStringBuilder.valueOf(text);
        int index = source.indexOf(needle);
        while (index >= 0) {
            builder.setSpan(
                new ForegroundColorSpan(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider)),
                index,
                Math.min(index + needle.length(), text.length()),
                0
            );
            index = source.indexOf(needle, index + 1);
        }
        return builder;
    }

    public static String translitSafe(String value) {
        return value == null ? "" : value;
    }

    public static CharSequence replaceNewLines(CharSequence value) {
        return value == null ? "" : value.toString().replace('\n', ' ');
    }

    public static void shakeView(View view) {
    }

    public static String formapMapUrl(int account, double lat, double lon, int width, int height, boolean marker, int zoom, int scale) {
        return "";
    }

    public static int dp2(int value) {
        return dp(value);
    }

    public static void getBitmapFromSurface(android.view.Surface surface, android.graphics.Bitmap bitmap) {
    }

    public static void getBitmapFromSurface(android.view.SurfaceView surfaceView, android.graphics.Bitmap bitmap) {
    }

    /** Matches upstream {@code AndroidUtilities.emptyMotionEvent()} — a cancelled, zeroed event. */
    public static MotionEvent emptyMotionEvent() {
        return MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
    }

    /**
     * NativeDo adaptation of upstream {@code shakeViewSpring(View, float)} —
     * upstream uses androidx SpringAnimation plus id tags; this is an equivalent
     * translationX wobble that avoids new id resources for a non-core path.
     */
    public static void shakeViewSpring(View view, float shiftDp) {
        if (view == null) {
            return;
        }
        final float t = view.getTranslationX();
        final int shift = dp(shiftDp);
        ObjectAnimator a = ObjectAnimator.ofFloat(view, View.TRANSLATION_X, t, t + shift, t - shift, t + shift * 0.6f, t - shift * 0.6f, t);
        a.setDuration(300);
        a.start();
    }

    /**
     * NativeDo adaptation of upstream {@code replaceArrows} — upstream replaces
     * arrow glyphs with ColoredImageSpan-backed icons; the icon drawable and
     * multi-char replace helper are absent locally, so this passes text through.
     */
    public static CharSequence replaceArrows(CharSequence text, boolean link, int translateX, int translateY) {
        return text;
    }
}
