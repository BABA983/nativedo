/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.messenger;

import android.annotation.SuppressLint;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.graphics.Point;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.TypefaceSpan;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.view.HapticFeedbackConstants;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.widget.EdgeEffect;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.core.view.WindowCompat;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.exifinterface.media.ExifInterface;
import androidx.viewpager.widget.ViewPager;

import android.graphics.Typeface;
import android.text.TextPaint;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackgroundGradientDrawable;
import org.telegram.ui.Components.MotionBackgroundDrawable;

public final class AndroidUtilities {

    public static final int REPLACING_TAG_TYPE_LINK = 0;
    public static final int REPLACING_TAG_TYPE_BOLD = 1;
    public static final int REPLACING_TAG_TYPE_LINKBOLD = 2;
    public static final int REPLACING_TAG_TYPE_LINK_NBSP = 3;
    public static final int REPLACING_TAG_TYPE_UNDERLINE = 4;

    public static final int FLAG_TAG_BR = 1;
    public static final int FLAG_TAG_BOLD = 2;
    public static final int FLAG_TAG_COLOR = 4;
    public static final int FLAG_TAG_URL = 8;
    public static final int FLAG_TAG_ALL = FLAG_TAG_BR | FLAG_TAG_BOLD | FLAG_TAG_URL;

    public static final Point displaySize = new Point();
    public static final int LIGHT_STATUS_BAR_OVERLAY = 0x0f000000;
    public static final int DARK_STATUS_BAR_OVERLAY = 0x33000000;
    public static float density = 1.0f;
    public static int statusBarHeight;
    public static int navigationBarHeight;
    public static boolean usingHardwareInput;
    public static boolean makingGlobalBlurBitmap;
    public static final String STICKERS_PLACEHOLDER_PACK_NAME = "Placeholders";

    /** Shared temporary RectF for drawing operations. Matches upstream AndroidUtilities.rectTmp. */
    public static final RectF rectTmp = new RectF();
    /** Shared temporary Rect for integer bounds/hit tests. Matches upstream AndroidUtilities.rectTmp2. */
    public static final Rect rectTmp2 = new Rect();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static Vibrator vibrator;
    private static int adjustOwnerClassGuid;
    private static float xdpi;
    private static float ydpi;

    private AndroidUtilities() {
    }

    @SuppressLint("PrivateApi")
    public static String getSystemProperty(String key) {
        try {
            Class<?> props = Class.forName("android.os.SystemProperties");
            return (String) props.getMethod("get", String.class).invoke(null, key);
        } catch (Exception ignore) {
        }
        return null;
    }

    public static int getThumbForNameOrMime(String name, String mime, boolean media) {
        return R.drawable.msg_round_file_s;
    }

    public static void makeGlobalBlurBitmap(Utilities.Callback<Bitmap> onBitmapDone, float amount) {
        if (onBitmapDone == null) {
            return;
        }
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.TRANSPARENT);
        onBitmapDone.run(bitmap);
    }

    public static void init(Context context) {
        if (context == null) {
            return;
        }
        FileLog.d("AndroidUtilities.init density=" + context.getResources().getDisplayMetrics().density
                + " displaySize=" + context.getResources().getDisplayMetrics().widthPixels + "x" + context.getResources().getDisplayMetrics().heightPixels);
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

    /** Matches upstream AndroidUtilities.lerp(Rect, RectF, float, RectF). */
    public static void lerp(Rect a, RectF b, float f, RectF to) {
        if (to != null && a != null && b != null) {
            to.set(
                a.left + (b.left - a.left) * f,
                a.top + (b.top - a.top) * f,
                a.right + (b.right - a.right) * f,
                a.bottom + (b.bottom - a.bottom) * f
            );
        }
    }

    /** Matches upstream AndroidUtilities.lerp(RectF, Rect, float, RectF). */
    public static void lerp(RectF a, Rect b, float f, RectF to) {
        if (to != null && a != null && b != null) {
            to.set(
                a.left + (b.left - a.left) * f,
                a.top + (b.top - a.top) * f,
                a.right + (b.right - a.right) * f,
                a.bottom + (b.bottom - a.bottom) * f
            );
        }
    }

    /** Matches upstream AndroidUtilities.lerp(Rect, Rect, float, Rect). */
    public static void lerp(Rect a, Rect b, float f, Rect to) {
        if (to != null && a != null && b != null) {
            to.set(
                (int) (a.left + (b.left - a.left) * f),
                (int) (a.top + (b.top - a.top) * f),
                (int) (a.right + (b.right - a.right) * f),
                (int) (a.bottom + (b.bottom - a.bottom) * f)
            );
        }
    }

    public static float lerp(float from, float to, float progress) {
        return from + (to - from) * progress;
    }

    public static float lerp(float[] ab, float f) {
        return lerp(ab[0], ab[1], f);
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

    public static float lerp3(float atMinusOne, float atZero, float atPlusOne, float t) {
        if (t < 0) return lerp(atZero, atMinusOne, -t);
        return lerp(atZero, atPlusOne, t);
    }

    public static int lerpColor(int a, int b, float t) {
        return Color.argb(
            lerp(Color.alpha(a), Color.alpha(b), t),
            lerp(Color.red(a),   Color.red(b),   t),
            lerp(Color.green(a), Color.green(b), t),
            lerp(Color.blue(a),  Color.blue(b),  t)
        );
    }

    public static int lerpColor3(int atMinusOne, int atZero, int atPlusOne, float t) {
        if (t < 0) return lerpColor(atZero, atMinusOne, -t);
        return lerpColor(atZero, atPlusOne, t);
    }

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
        float tx, sx;
        float ty, sy;
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
        }

        if (translate) {
            tx = -src.left * sx;
            ty = -src.top * sy;
        } else {
            tx = dst.left - src.left * sx;
            ty = dst.top - src.top * sy;
        }

        float diff;
        if (xLarger) {
            diff = dst.width() - src.width() * sy;
        } else {
            diff = dst.height() - src.height() * sy;
        }
        diff = diff / 2.0f;
        if (xLarger) {
            tx += diff;
        } else {
            ty += diff;
        }

        matrix.preScale(sx, sy);
        if (translate) {
            matrix.preTranslate(tx, ty);
        }
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

    public static CharSequence getTrimmedString(CharSequence src) {
        if (src == null || src.length() == 0) {
            return src;
        }
        while (src.length() > 0 && (src.charAt(0) == '\n' || src.charAt(0) == ' ')) {
            src = src.subSequence(1, src.length());
        }
        while (src.length() > 0 && (src.charAt(src.length() - 1) == '\n' || src.charAt(src.length() - 1) == ' ')) {
            src = src.subSequence(0, src.length() - 1);
        }
        return src;
    }

    public static void setLightStatusBar(android.app.Dialog dialog, boolean enable) {
        if (dialog != null) {
            setLightStatusBar(dialog.getWindow(), enable);
        }
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
        if (view != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            changeSetSystemUiVisibility(view, View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR, enable);
        }
    }

    public static void setLightNavigationBar(View view, boolean enable) {
        if (view != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            changeSetSystemUiVisibility(view, View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR, enable);
        }
    }

    private static void changeSetSystemUiVisibility(View view, int flag, boolean enable) {
        final int oldFlags = view.getSystemUiVisibility();
        final int newFlags = enable ? (oldFlags | flag) : (oldFlags & ~flag);
        if (oldFlags != newFlags) {
            view.setSystemUiVisibility(newFlags);
        }
    }

    public static SpannableStringBuilder replaceSingleTag(String str, Runnable runnable) {
        return replaceSingleTag(str, -1, REPLACING_TAG_TYPE_LINKBOLD, runnable);
    }

    public static SpannableStringBuilder replaceSingleTag(String str, int colorKey, int type, Runnable runnable) {
        return replaceSingleTag(str, colorKey, type, runnable, null);
    }

    public static SpannableStringBuilder replaceSingleTag(String str, int colorKey, int type, Runnable runnable, Theme.ResourcesProvider resourcesProvider) {
        int startIndex = str.indexOf("**");
        int endIndex = str.indexOf("**", startIndex + 1);
        str = str.replace("**", "");
        int index = -1;
        int len = 0;
        if (startIndex >= 0 && endIndex >= 0 && endIndex - startIndex > 2) {
            len = endIndex - startIndex - 2;
            index = startIndex;
        }
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(str);
        if (index >= 0) {
            if (type == REPLACING_TAG_TYPE_LINK || type == REPLACING_TAG_TYPE_LINK_NBSP || type == REPLACING_TAG_TYPE_LINKBOLD || type == REPLACING_TAG_TYPE_UNDERLINE) {
                spannableStringBuilder.setSpan(new ClickableSpan() {
                    @Override
                    public void updateDrawState(@NonNull TextPaint ds) {
                        super.updateDrawState(ds);
                        ds.setUnderlineText(type == REPLACING_TAG_TYPE_UNDERLINE);
                        if (colorKey >= 0) {
                            ds.setColor(Theme.getColor(colorKey, resourcesProvider));
                        }
                        if (type == REPLACING_TAG_TYPE_LINKBOLD) {
                            ds.setTypeface(AndroidUtilities.bold());
                        }
                    }

                    @Override
                    public void onClick(@NonNull View view) {
                        if (runnable != null) {
                            runnable.run();
                        }
                    }
                }, index, index + len, 0);
            } else {
                spannableStringBuilder.setSpan(new CharacterStyle() {
                    @Override
                    public void updateDrawState(TextPaint textPaint) {
                        textPaint.setTypeface(AndroidUtilities.bold());
                        int wasAlpha = textPaint.getAlpha();
                        textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider));
                        textPaint.setAlpha(wasAlpha);
                    }
                }, index, index + len, 0);
            }
        }
        return spannableStringBuilder;
    }

    public static void setScrollViewEdgeEffectColor(HorizontalScrollView scrollView, int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scrollView.setEdgeEffectColor(color);
        } else {
            try {
                Field field = HorizontalScrollView.class.getDeclaredField("mEdgeGlowLeft");
                field.setAccessible(true);
                EdgeEffect mEdgeGlowLeft = (EdgeEffect) field.get(scrollView);
                if (mEdgeGlowLeft != null) {
                    mEdgeGlowLeft.setColor(color);
                }
                field = HorizontalScrollView.class.getDeclaredField("mEdgeGlowRight");
                field.setAccessible(true);
                EdgeEffect mEdgeGlowRight = (EdgeEffect) field.get(scrollView);
                if (mEdgeGlowRight != null) {
                    mEdgeGlowRight.setColor(color);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }


    public static void setScrollViewEdgeEffectColor(ScrollView scrollView, int color) {
        if (scrollView == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 29) {
            scrollView.setTopEdgeEffectColor(color);
            scrollView.setBottomEdgeEffectColor(color);
        } else {
            try {
                Field field = ScrollView.class.getDeclaredField("mEdgeGlowTop");
                field.setAccessible(true);
                EdgeEffect mEdgeGlowTop = (EdgeEffect) field.get(scrollView);
                if (mEdgeGlowTop != null) {
                    mEdgeGlowTop.setColor(color);
                }
                field = ScrollView.class.getDeclaredField("mEdgeGlowBottom");
                field.setAccessible(true);
                EdgeEffect mEdgeGlowBottom = (EdgeEffect) field.get(scrollView);
                if (mEdgeGlowBottom != null) {
                    mEdgeGlowBottom.setColor(color);
                }
            } catch (Exception ignore) {
            }
        }
    }

    public static void setViewPagerEdgeEffectColor(ViewPager viewPager, int color) {
        try {
            Field field = ViewPager.class.getDeclaredField("mLeftEdge");
            field.setAccessible(true);
            EdgeEffect mLeftEdge = (EdgeEffect) field.get(viewPager);
            if (mLeftEdge != null) {
                mLeftEdge.setColor(color);
            }
            field = ViewPager.class.getDeclaredField("mRightEdge");
            field.setAccessible(true);
            EdgeEffect mRightEdge = (EdgeEffect) field.get(viewPager);
            if (mRightEdge != null) {
                mRightEdge.setColor(color);
            }
        } catch (Exception ignore) {
        }
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

    public static void requestAdjustResize(Activity activity, int classGuid) {
        if (activity == null) {
            return;
        }
        requestAdjustResize(activity.getWindow(), classGuid);
    }

    public static void requestAdjustResize(Window window, int classGuid) {
        if (window == null || isTablet()) {
            return;
        }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        adjustOwnerClassGuid = classGuid;
    }

    public static void removeAdjustResize(Activity activity, int classGuid) {
        if (activity == null || isTablet()) {
            return;
        }
        if (adjustOwnerClassGuid == classGuid) {
            activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        }
    }

    public static int getStatusBarHeight(Context context) {
        if (context == null) return 0;
        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resourceId > 0 ? context.getResources().getDimensionPixelSize(resourceId) : 0;
    }

    private static Typeface typefaceBold;
    private static final HashMap<String, Typeface> typefaceCache = new HashMap<>();

    /**
     * Returns a bold typeface. Matches upstream {@code AndroidUtilities.bold()}.
     * Caches the result so repeated calls are cheap.
     */
    public static Typeface bold() {
        if (typefaceBold == null) {
            typefaceBold = getTypeface(TYPEFACE_ROBOTO_MEDIUM);
            if (typefaceBold == null) {
                typefaceBold = Typeface.create("sans-serif-medium", Typeface.NORMAL);
            }
        }
        return typefaceBold;
    }

    private static Typeface typefaceMedium;
    private static Typeface typefaceItalic;
    private static Typeface typefaceBoldItalic;

    /**
     * Returns a medium typeface. Matches upstream {@code AndroidUtilities.medium()}.
     */
    public static Typeface medium() {
        if (typefaceMedium == null) {
            typefaceMedium = getTypeface(TYPEFACE_ROBOTO_MEDIUM);
            if (typefaceMedium == null) {
                typefaceMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
            }
        }
        return typefaceMedium;
    }

    /**
     * Returns a typeface with the given style. The Telegram medium face is bundled;
     * font assets not used by NativeDo continue to map to system styles.
     */
    public static Typeface getTypeface(String fontName) {
        if (fontName == null) {
            return Typeface.DEFAULT;
        }
        if (TYPEFACE_ROBOTO_MEDIUM.equals(fontName)) {
            synchronized (typefaceCache) {
                Typeface cached = typefaceCache.get(fontName);
                if (cached != null) {
                    return cached;
                }
                if (ApplicationLoader.applicationContext == null) {
                    return null;
                }
                try {
                    Typeface typeface;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        typeface = new Typeface.Builder(
                            ApplicationLoader.applicationContext.getAssets(), fontName
                        ).setWeight(700).build();
                    } else {
                        typeface = Typeface.createFromAsset(
                            ApplicationLoader.applicationContext.getAssets(), fontName
                        );
                    }
                    typefaceCache.put(fontName, typeface);
                    return typeface;
                } catch (Exception e) {
                    FileLog.e("Could not get typeface '" + fontName + "' because " + e.getMessage());
                    return null;
                }
            }
        }
        if (fontName.endsWith("rmono.ttf")) {
            return Typeface.MONOSPACE;
        }
        if (fontName.endsWith("rmediumitalic.ttf")) {
            if (typefaceBoldItalic == null) {
                typefaceBoldItalic = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC);
            }
            return typefaceBoldItalic;
        }
        if (fontName.endsWith("ritalic.ttf")) {
            if (typefaceItalic == null) {
                typefaceItalic = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC);
            }
            return typefaceItalic;
        }
        if (fontName.endsWith("rextrabold.ttf") || fontName.endsWith("mw_bold.ttf")) {
            return bold();
        }
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
    /**
     * Upstream name for bold-italic. NativeDo does not ship this italic font asset;
     * {@link #getTypeface(String)} maps this path suffix to system BOLD_ITALIC.
     */
    public static final String TYPEFACE_ROBOTO_MEDIUM_ITALIC = "fonts/rmediumitalic.ttf";
    public static final String TYPEFACE_ROBOTO_ITALIC = "fonts/ritalic.ttf";
    public static final String TYPEFACE_ROBOTO_MONO = "fonts/rmono.ttf";

    public static int screenRefreshRate = 60;
    public static int screenMaxRefreshRate = 60;

    public static int getShadowHeight() {
        if (density >= 4.0f) {
            return 3;
        } else if (density >= 2.0f) {
            return 2;
        } else {
            return 1;
        }
    }

    public static void setPreferredMaxRefreshRate(WindowManager wm, View windowView, WindowManager.LayoutParams params) {
        if (wm == null) return;
        if (Math.abs(params.preferredRefreshRate - screenMaxRefreshRate) > 0.2) {
            params.preferredRefreshRate = screenMaxRefreshRate;
            if (windowView.isAttachedToWindow()) {
                try {
                    wm.updateViewLayout(windowView, params);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
    }

    /** Stub of upstream {@code AndroidUtilities.getPhotoSize()} — long-side cap for default photo download. */
    public static int getPhotoSize() {
        return getPhotoSize(false);
    }

    public static int getPhotoSize(boolean huge) {
        return huge ? 2560 : 1280;
    }

    public static int getPatternColor(int color) {
        return getPatternColor(color, false);
    }

    public static int getPatternColor(int color, boolean alwaysDark) {
        float[] hsb = new float[3];
        Color.colorToHSV(color, hsb);
        if (hsb[1] > 0.0f || (hsb[2] < 1.0f && hsb[2] > 0.0f)) {
            hsb[1] = Math.min(1.0f, hsb[1] + (alwaysDark ? 0.15f : 0.05f) + 0.1f * (1.0f - hsb[1]));
        }
        if (alwaysDark || hsb[2] > 0.5f) {
            hsb[2] = Math.max(0.0f, hsb[2] * 0.65f);
        } else {
            hsb[2] = Math.max(0.0f, Math.min(1.0f, 1.0f - hsb[2] * 0.65f));
        }
        return Color.HSVToColor(hsb) & (alwaysDark ? 0x99ffffff : 0x66ffffff);
    }

    public static int getPatternSideColor(int color) {
        float[] hsb = new float[3];
        Color.colorToHSV(color, hsb);
        hsb[1] = Math.min(1.0f, hsb[1] + 0.05f);
        if (hsb[2] > 0.5f) {
            hsb[2] = Math.max(0.0f, hsb[2] * 0.90f);
        } else {
            hsb[2] = Math.max(0.0f, hsb[2] * 0.90f);
        }
        return Color.HSVToColor(hsb) | 0xff000000;
    }

    public static int getOffsetColor(int color1, int color2, float offset, float alpha) {
        int rF = Color.red(color2);
        int gF = Color.green(color2);
        int bF = Color.blue(color2);
        int aF = Color.alpha(color2);
        int rS = Color.red(color1);
        int gS = Color.green(color1);
        int bS = Color.blue(color1);
        int aS = Color.alpha(color1);
        return Color.argb((int) ((aS + (aF - aS) * offset) * alpha), (int) (rS + (rF - rS) * offset), (int) (gS + (gF - gS) * offset), (int) (bS + (bF - bS) * offset));
    }

    public static int blendOver(int foreground, int background) {
        return androidx.core.graphics.ColorUtils.compositeColors(foreground, background);
    }

    public static Vibrator getVibrator() {
        if (vibrator == null) {
            vibrator = (Vibrator) ApplicationLoader.applicationContext.getSystemService(Context.VIBRATOR_SERVICE);
        }
        return vibrator;
    }

    public static void vibrateCursor(View view) {
        try {
            if (view == null || view.getContext() == null) return;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
            if (!((Vibrator) view.getContext().getSystemService(Context.VIBRATOR_SERVICE)).hasAmplitudeControl()) return;
            view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
        } catch (Exception ignore) {}
    }

    public static boolean hasDialogOnTop(org.telegram.ui.ActionBar.BaseFragment fragment) {
        return false;
    }

    public static void createEmptyFile(File f) {
        try {
            if (f.exists()) {
                return;
            }
            FileWriter writer = new FileWriter(f);
            writer.flush();
            writer.close();
        } catch (Throwable e) {
            FileLog.e(e, false);
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

    public static Point getRealScreenSize() {
        Point size = new Point();
        try {
            WindowManager windowManager = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE);
            windowManager.getDefaultDisplay().getRealSize(size);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return size;
    }

    /** Stub of upstream {@code AndroidUtilities.roundMessageInset} — extra inset for round-video bubbles. */
    public static int roundMessageInset = 0;

    public static void recycleBitmaps(List<Bitmap> bitmapToRecycle) {
        if (Build.VERSION.SDK_INT <= 23) {
            // cause to crash on API <= 23 — let GC reclaim pixels.
            return;
        }
        if (bitmapToRecycle != null && !bitmapToRecycle.isEmpty()) {
            ArrayList<WeakReference<Bitmap>> bitmapsToRecycleRef = new ArrayList<>();
            for (int i = 0; i < bitmapToRecycle.size(); i++) {
                Bitmap bitmap = bitmapToRecycle.get(i);
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmapsToRecycleRef.add(new WeakReference<>(bitmap));
                }
            }
            AndroidUtilities.runOnUIThread(() -> Utilities.globalQueue.postRunnable(() -> {
                for (int i = 0; i < bitmapsToRecycleRef.size(); i++) {
                    Bitmap bitmap = bitmapsToRecycleRef.get(i).get();
                    bitmapsToRecycleRef.get(i).clear();
                    if (bitmap != null && !bitmap.isRecycled()) {
                        try {
                            bitmap.recycle();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                }
            }), 36);
        }
    }

    public static String getPath(final Uri uri) {
        try {
            if (DocumentsContract.isDocumentUri(ApplicationLoader.applicationContext, uri)) {
                if (isExternalStorageDocument(uri)) {
                    final String docId = DocumentsContract.getDocumentId(uri);
                    final String[] split = docId.split(":");
                    final String type = split[0];
                    if ("primary".equalsIgnoreCase(type)) {
                        return Environment.getExternalStorageDirectory() + "/" + split[1];
                    }
                } else if (isDownloadsDocument(uri)) {
                    final String id = DocumentsContract.getDocumentId(uri);
                    final Uri contentUri = ContentUris.withAppendedId(
                            Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
                    return getDataColumn(ApplicationLoader.applicationContext, contentUri, null, null);
                } else if (isMediaDocument(uri)) {
                    final String docId = DocumentsContract.getDocumentId(uri);
                    final String[] split = docId.split(":");
                    final String type = split[0];
                    Uri contentUri = null;
                    switch (type) {
                        case "image":
                            contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                            break;
                        case "video":
                            contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                            break;
                        case "audio":
                            contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                            break;
                    }
                    final String selection = "_id=?";
                    final String[] selectionArgs = new String[]{split[1]};
                    return getDataColumn(ApplicationLoader.applicationContext, contentUri, selection, selectionArgs);
                }
            } else if ("content".equalsIgnoreCase(uri.getScheme())) {
                return getDataColumn(ApplicationLoader.applicationContext, uri, null, null);
            } else if ("file".equalsIgnoreCase(uri.getScheme())) {
                return uri.getPath();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    private static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {column};
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(index);
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    private static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    private static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    private static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    public static Bitmap createVideoThumbnail(String filePath, int kind) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(filePath);
            return retriever.getFrameAtTime(-1);
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            try {
                retriever.release();
            } catch (Exception ignore) {
            }
        }
        return null;
    }

    public static Uri getBitmapShareUri(Bitmap bitmap, String fileName, Bitmap.CompressFormat format) {
        File cachePath = getCacheDir();
        if (!cachePath.isDirectory()) {
            try {
                cachePath.mkdirs();
            } catch (Exception e) {
                FileLog.e(e);
                return null;
            }
        }
        File file = new File(cachePath, fileName);
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(format, 87, out);
            out.close();
            return FileProvider.getUriForFile(ApplicationLoader.applicationContext, ApplicationLoader.getApplicationId() + ".provider", file);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public static File getCacheDir() {
        if (ApplicationLoader.applicationContext == null) {
            return new File(System.getProperty("java.io.tmpdir", "/tmp"));
        }
        String state = null;
        try {
            state = Environment.getExternalStorageState();
        } catch (Exception ignore) {
        }

        if (state == null || state.startsWith(Environment.MEDIA_MOUNTED)) {
            try {
                File file;

                File[] dirs = ApplicationLoader.applicationContext.getExternalCacheDirs();
                file = dirs != null && dirs.length > 0 ? dirs[0] : null;
                if (!TextUtils.isEmpty(SharedConfig.storageCacheDir) && dirs != null) {
                    for (int a = 0; a < dirs.length; a++) {
                        if (dirs[a] != null && dirs[a].getAbsolutePath().startsWith(SharedConfig.storageCacheDir)) {
                            file = dirs[a];
                            break;
                        }
                    }
                }

                if (file != null && (file.exists() || file.mkdirs()) && file.canWrite()) {
                    return file;
                }
            } catch (Exception ignore) {
            }
        }
        try {
            File file = ApplicationLoader.applicationContext.getCacheDir();
            if (file != null) {
                return file;
            }
        } catch (Exception ignore) {
        }
        try {
            File file = ApplicationLoader.applicationContext.getFilesDir();
            if (file != null) {
                File cacheFile = new File(file, "cache/");
                cacheFile.mkdirs();
                if ((file.exists() || file.mkdirs()) && file.canWrite()) {
                    return cacheFile;
                }
            }
        } catch (Exception ignore) {
        }
        return new File(System.getProperty("java.io.tmpdir", "/tmp"));
    }

    public static File getLogsDir() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return null;
        }
        try {
            if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                File path = context.getExternalFilesDir(null);
                if (path != null) {
                    File dir = new File(path, "logs");
                    dir.mkdirs();
                    return dir;
                }
            }
        } catch (Exception ignore) {
        }
        try {
            File cacheDir = context.getCacheDir();
            if (cacheDir != null) {
                File dir = new File(cacheDir, "logs");
                dir.mkdirs();
                return dir;
            }
        } catch (Exception ignore) {
        }
        try {
            File filesDir = context.getFilesDir();
            if (filesDir != null) {
                File dir = new File(filesDir, "logs");
                dir.mkdirs();
                return dir;
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    public static String formatFileSize(long size) {
        return formatFileSize(size, false, false);
    }

    public static String formatFileSize(long size, boolean removeZero, boolean makeShort) {
        if (size == 0) {
            return String.format(Locale.US, "%d KB", 0);
        } else if (size < 1024) {
            return String.format(Locale.US, "%d B", size);
        } else if (size < 1024 * 1024) {
            float value = size / 1024.0f;
            if (removeZero && (value - (int) value) * 10 == 0) {
                return String.format(Locale.US, "%d KB", (int) value);
            } else {
                return String.format(Locale.US, "%.1f KB", value);
            }
        } else if (size < 1000 * 1024 * 1024) {
            float value = size / 1024.0f / 1024.0f;
            if (removeZero && (value - (int) value) * 10 == 0) {
                return String.format(Locale.US, "%d MB", (int) value);
            } else {
                return String.format(Locale.US, "%.1f MB", value);
            }
        } else {
            float value = (int) (size / 1024L / 1024L) / 1000.0f;
            if (removeZero && (value - (int) value) * 10 == 0) {
                return String.format(Locale.US, "%d GB", (int) value);
            } else if (makeShort) {
                return String.format(Locale.US, "%.1f GB", value);
            } else {
                return String.format(Locale.US, "%.2f GB", value);
            }
        }
    }

    public static Pair<Integer, Integer> getImageOrientation(InputStream is) {
        try {
            return getImageOrientationFromExif(new ExifInterface(is));
        } catch (Exception e) {
            FileLog.e(e);
        }
        return new Pair<>(0, 0);
    }

    public static Pair<Integer, Integer> getImageOrientation(File file) {
        try {
            return getImageOrientationFromExif(new ExifInterface(file));
        } catch (Exception e) {
            FileLog.e(e);
        }
        return new Pair<>(0, 0);
    }

    public static Pair<Integer, Integer> getImageOrientation(String path) {
        try {
            return getImageOrientationFromExif(new ExifInterface(path));
        } catch (Exception ignore) {
        }
        return new Pair<>(0, 0);
    }

    private static Pair<Integer, Integer> getImageOrientationFromExif(ExifInterface exif) {
        try {
            int orientation = 0, invert = 0;
            final int exifvalue = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (exifvalue) {
                case ExifInterface.ORIENTATION_NORMAL:
                    break;
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    invert = 1;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    orientation = 180;
                    break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    invert = 2;
                    break;
                case ExifInterface.ORIENTATION_TRANSPOSE:
                    invert = 2;
                    orientation = 270;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    orientation = 90;
                    break;
                case ExifInterface.ORIENTATION_TRANSVERSE:
                    invert = 1;
                    orientation = 270;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    orientation = 270;
                    break;
            }
            return new Pair<>(orientation, invert);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return new Pair<>(0, 0);
    }

    public static int getAverageColor(int color1, int color2) {
        int r1 = Color.red(color1);
        int r2 = Color.red(color2);
        int g1 = Color.green(color1);
        int g2 = Color.green(color2);
        int b1 = Color.blue(color1);
        int b2 = Color.blue(color2);
        return Color.argb(255, (r1 / 2 + r2 / 2), (g1 / 2 + g2 / 2), (b1 / 2 + b2 / 2));
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
        addMediaToGallery(filePath != null ? new File(filePath) : null);
    }

    public static void addMediaToGallery(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        intent.setData(android.net.Uri.fromFile(file));
        if (ApplicationLoader.applicationContext != null) {
            ApplicationLoader.applicationContext.sendBroadcast(intent);
        }
    }

    public static String generateFileName(int type, String extension) {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat sdf;
        if (type == 0) {
            sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        } else if (type == 1) {
            sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        } else {
            sdf = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US);
        }
        String fileName = "IMG_" + sdf.format(calendar.getTime());
        if (!TextUtils.isEmpty(extension)) {
            fileName += "." + extension;
        }
        return fileName;
    }

    /**
     * Upstream-shaped asset copy used by {@code Theme.getAssetFile}.
     * Writes {@code sourceFile} into {@code destFile}, creating parent dirs as needed.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static boolean copyFile(InputStream sourceFile, File destFile) throws java.io.IOException {
        if (destFile == null) {
            return false;
        }
        File parent = destFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileOutputStream out = new FileOutputStream(destFile)) {
            return copyFile(sourceFile, out);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static boolean copyFile(InputStream sourceFile, OutputStream destFile) {
        try {
            byte[] buf = new byte[4096];
            int len;
            while ((len = sourceFile.read(buf)) > 0) {
                destFile.write(buf, 0, len);
            }
            destFile.flush();
            return true;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        } finally {
            try {
                sourceFile.close();
            } catch (Exception ignore) {}
            try {
                destFile.close();
            } catch (Exception ignore) {}
        }
    }

    public static ArrayList<File> getRootDirs() {
        HashSet<String> pathes = new HashSet<>();
        ArrayList<File> result = null;
        File[] dirs = ApplicationLoader.applicationContext.getExternalFilesDirs(null);
        if (dirs != null) {
            for (int a = 0; a < dirs.length; a++) {
                if (dirs[a] == null) {
                    continue;
                }
                String path = dirs[a].getAbsolutePath();
                int idx = path.indexOf("/Android");
                if (idx >= 0) {
                    if (result == null) {
                        result = new ArrayList<>();
                    }
                    File file = new File(path.substring(0, idx));
                    if (file != null && !pathes.contains(file.getAbsolutePath())) {
                        pathes.add(file.getAbsolutePath());
                        result.add(file);
                    }
                }
            }
        }
        if (result == null) {
            result = new ArrayList<>();
        }
        if (result.isEmpty()) {
            File dir = Environment.getExternalStorageDirectory();
            if (dir != null && !pathes.contains(dir.getAbsolutePath())) {
                result.add(dir);
            }
        }
        return result;
    }

    public static ArrayList<File> getDataDirs() {
        ArrayList<File> result = null;
        File[] dirs = ApplicationLoader.applicationContext.getExternalFilesDirs(null);
        if (dirs != null) {
            for (int a = 0; a < dirs.length; a++) {
                if (dirs[a] == null) {
                    continue;
                }
                if (result == null) {
                    result = new ArrayList<>();
                }
                result.add(dirs[a]);
            }
        }
        if (result == null) {
            result = new ArrayList<>();
        }
        if (result.isEmpty()) {
            result.add(Environment.getExternalStorageDirectory());
        }
        return result;
    }

    public static String formatFileSize(long size, boolean removeZero) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", size / 1024.0);
        } else if (size < 1024L * 1024 * 1024) {
            return String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024));
        } else {
            return String.format(Locale.US, "%.1f GB", size / (1024.0 * 1024 * 1024));
        }
    }

    public static void forEachViews(View view, Consumer<View> consumer) {
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                consumer.accept(view);
                forEachViews(viewGroup.getChildAt(i), consumer);
            }
        } else {
            consumer.accept(view);
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

    public static void updateViewVisibilityAnimated(View view, boolean show, float scaleFactor, boolean goneOnHide, float maxAlpha, boolean animated, ValueAnimator.AnimatorUpdateListener onUpdate) {
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
            view.animate().alpha(0).scaleY(scaleFactor).scaleX(scaleFactor).setListener(new org.telegram.ui.Components.HideViewAfterAnimation(view, goneOnHide)).setDuration(150).setUpdateListener(onUpdate).start();
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
        return formatDuration(duration, false);
    }

    public static String formatTimestamp(int duration) {
        int h = duration / 3600;
        int m = duration / 60 % 60;
        int s = duration % 60;
        String str = "";
        if (h > 0) {
            str += String.format(Locale.US, "%dh", h);
        }
        if (m > 0) {
            str += String.format(Locale.US, h > 0 ? "%02dm" : "%dm", m);
        }
        str += String.format(Locale.US, h > 0 || m > 0 ? "%02ds" : "%ds", s);
        return str;
    }

    public static String formatLongDuration(int duration) {
        return formatDuration(duration, true);
    }

    public static String formatDuration(int duration, boolean isLong) {
        return formatDuration(duration, isLong, false);
    }

    public static String formatDuration(int duration, boolean isLong, boolean noSecondsIfHours) {
        int h = duration / 3600;
        int m = duration / 60 % 60;
        int s = duration % 60;
        if (h == 0) {
            if (isLong) {
                return String.format(Locale.US, "%02d:%02d", m, s);
            } else {
                return String.format(Locale.US, "%d:%02d", m, s);
            }
        } else {
            if (noSecondsIfHours) {
                return String.format(Locale.US, "%d:%02d", h, m);
            } else {
                return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
            }
        }
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
        try (InputStream inputStream = ApplicationLoader.applicationContext.getResources().openRawResource(resId);
             Scanner scanner = new Scanner(inputStream).useDelimiter("\\A")) {
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
        host = host.toLowerCase(Locale.US);
        if (removeWww && host.startsWith("www.")) {
            host = host.substring(4);
        }
        return host;
    }

    public static SpannableStringBuilder replaceTags(String str) {
        return replaceTags(str, FLAG_TAG_ALL);
    }

    public static SpannableStringBuilder replaceTags(String str, int flag, Object... args) {
        try {
            int start;
            int end;
            StringBuilder stringBuilder = new StringBuilder(str);
            if ((flag & FLAG_TAG_BR) != 0) {
                while ((start = stringBuilder.indexOf("<br>")) != -1) {
                    stringBuilder.replace(start, start + 4, "\n");
                }
                while ((start = stringBuilder.indexOf("<br/>")) != -1) {
                    stringBuilder.replace(start, start + 5, "\n");
                }
            }
            ArrayList<Integer> bolds = new ArrayList<>();
            if ((flag & FLAG_TAG_BOLD) != 0) {
                while ((start = stringBuilder.indexOf("<b>")) != -1) {
                    stringBuilder.replace(start, start + 3, "");
                    end = stringBuilder.indexOf("</b>");
                    if (end == -1) {
                        end = stringBuilder.indexOf("<b>");
                    }
                    stringBuilder.replace(end, end + 4, "");
                    bolds.add(start);
                    bolds.add(end);
                }
                while ((start = stringBuilder.indexOf("**")) != -1) {
                    stringBuilder.replace(start, start + 2, "");
                    end = stringBuilder.indexOf("**");
                    if (end >= 0) {
                        stringBuilder.replace(end, end + 2, "");
                        bolds.add(start);
                        bolds.add(end);
                    }
                }
            }
            if ((flag & FLAG_TAG_URL) != 0) {
                while ((start = stringBuilder.indexOf("**")) != -1) {
                    stringBuilder.replace(start, start + 2, "");
                    end = stringBuilder.indexOf("**");
                    if (end >= 0) {
                        stringBuilder.replace(end, end + 2, "");
                        bolds.add(start);
                        bolds.add(end);
                    }
                }
            }
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(stringBuilder);
            for (int a = 0; a < bolds.size() / 2; a++) {
                spannableStringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.bold()), bolds.get(a * 2), bolds.get(a * 2 + 1), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (args != null) {
                for (int a = 0; a < args.length; a++) {
                    if (args[a] instanceof CharacterStyle) {
                        spannableStringBuilder.setSpan((CharacterStyle) args[a], 0, spannableStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }
            return spannableStringBuilder;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return new SpannableStringBuilder(str);
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

    private static final int TAG_SHAKE_ANIMATION = R.string.AppName;

    public static void shakeView(View view) {
        if (view == null) {
            return;
        }
        final float N = 4;
        Object animator = view.getTag(TAG_SHAKE_ANIMATION);
        if (animator instanceof ValueAnimator) {
            ((ValueAnimator) animator).cancel();
        }
        ValueAnimator va = ValueAnimator.ofFloat(0, 1);
        va.addUpdateListener(anm -> {
            float x = (float) anm.getAnimatedValue();
            view.setTranslationX((float) ((4 * x * (1 - x)) * Math.sin(N * (x * Math.PI)) * dp(N)));
        });
        va.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                view.setTranslationX(0);
            }
        });
        va.setDuration(300);
        va.start();
        view.setTag(TAG_SHAKE_ANIMATION, va);
    }

    public static String formapMapUrl(int account, double lat, double lon, int width, int height, boolean marker, int zoom, int scale) {
        return "";
    }

    public static int dp2(int value) {
        return dp(value);
    }

    public static void getBitmapFromSurface(SurfaceView surfaceView, Bitmap bitmap) {
        if (surfaceView == null || !surfaceView.getHolder().getSurface().isValid()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            PixelCopy.request(surfaceView, bitmap, copyResult -> {
                countDownLatch.countDown();
            }, mainHandler);
            try {
                countDownLatch.await();
            } catch (InterruptedException e) {
                FileLog.e(e);
            }
        }
    }

    public static void getBitmapFromSurface(Surface surface, Bitmap bitmap) {
        if (surface == null || !surface.isValid()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            PixelCopy.request(surface, bitmap, copyResult -> {
                countDownLatch.countDown();
            }, mainHandler);
            try {
                countDownLatch.await();
            } catch (InterruptedException e) {
                FileLog.e(e);
            }
        }
    }

    public static void getBitmapFromSurface(SurfaceView surfaceView, Bitmap bitmap, Runnable onDone) {
        if (surfaceView == null || !surfaceView.getHolder().getSurface().isValid()) {
            if (onDone != null) onDone.run();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            PixelCopy.request(surfaceView, bitmap, copyResult -> {
                countDownLatch.countDown();
            }, mainHandler);
            try {
                countDownLatch.await();
            } catch (InterruptedException e) {
                FileLog.e(e);
            }
        }
        if (onDone != null) onDone.run();
    }

    private static Field mAttachInfoField;
    private static Field mStableInsetsField;

    public static String formatCount(int count) {
        if (count < 1000) return Integer.toString(count);

        ArrayList<String> strings = new ArrayList<>();
        while (count != 0) {
            int mod = count % 1000;
            count /= 1000;
            if (count > 0) {
                strings.add(String.format(Locale.ENGLISH, "%03d", mod));
            } else {
                strings.add(Integer.toString(mod));
            }
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = strings.size() - 1; i >= 0; i--) {
            stringBuilder.append(strings.get(i));
            if (i != 0) {
                stringBuilder.append(",");
            }
        }
        return stringBuilder.toString();
    }

    public static final String[] numbersSignatureArray = {"", "K", "M", "B", "T", "P"};

    public static String formatWholeNumber(int v, int dif) {
        if (v == 0) {
            return "0";
        }
        float num_ = v;
        int count = 0;
        if (dif == 0) dif = v;
        if (dif < 1000) {
            return AndroidUtilities.formatCount(v);
        }
        while (dif >= 1000 && count < numbersSignatureArray.length - 1) {
            dif /= 1000;
            num_ /= 1000;
            count++;
        }
        if (num_ < 0.1) {
            return "0";
        } else if ((num_ * 10) == (int) (num_ * 10)) {
            return String.format(Locale.ENGLISH, "%s%s", AndroidUtilities.formatCount((int) num_), numbersSignatureArray[count]);
        } else {
            return String.format(Locale.ENGLISH, "%.1f%s", (int) (num_ * 10) / 10f, numbersSignatureArray[count]);
        }
    }

    public static int getViewInset(View view) {
        if (view == null || view.getHeight() == AndroidUtilities.displaySize.y || view.getHeight() == AndroidUtilities.displaySize.y - statusBarHeight) {
            return 0;
        }
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                WindowInsets insets = view.getRootWindowInsets();
                return insets != null ? insets.getStableInsetBottom() : 0;
            } else {
                if (mAttachInfoField == null) {
                    mAttachInfoField = View.class.getDeclaredField("mAttachInfo");
                    mAttachInfoField.setAccessible(true);
                }
                Object mAttachInfo = mAttachInfoField.get(view);
                if (mAttachInfo != null) {
                    if (mStableInsetsField == null) {
                        mStableInsetsField = mAttachInfo.getClass().getDeclaredField("mStableInsets");
                        mStableInsetsField.setAccessible(true);
                    }
                    Rect insets = (Rect) mStableInsetsField.get(mAttachInfo);
                    return insets.bottom;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return 0;
    }

    public static float getNavigationBarThirdButtonsFactor(int navBarHeight) {
        return Math.max(0f, Math.min(1f, ((float) navBarHeight - dp(32)) / dp(16)));
    }

    public static boolean isSafeToShow(Context context) {
        if (context == null) {
            return false;
        }
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                Activity activity = (Activity) context;
                return !activity.isFinishing() && (Build.VERSION.SDK_INT < 17 || !activity.isDestroyed());
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return true;
    }

    /** Matches upstream {@code AndroidUtilities.emptyMotionEvent()} — a cancelled, zeroed event. */
    public static MotionEvent emptyMotionEvent() {
        return MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
    }

    public static void shakeViewSpring(View view) {
        shakeViewSpring(view, 10, null);
    }

    public static void shakeViewSpring(View view, float shiftDp) {
        shakeViewSpring(view, shiftDp, null);
    }

    public static void shakeViewSpring(View view, Runnable endCallback) {
        shakeViewSpring(view, 10, endCallback);
    }

    public static void shakeViewSpring(View view, float shiftDp, Runnable endCallback) {
        if (view == null) {
            return;
        }
        int shift = dp(shiftDp);
        if (view.getTag(R.id.spring_tag) != null) {
            ((SpringAnimation) view.getTag(R.id.spring_tag)).cancel();
        }
        Float wasX = (Float) view.getTag(R.id.spring_was_translation_x_tag);
        if (wasX != null) {
            view.setTranslationX(wasX);
        }
        view.setTag(R.id.spring_was_translation_x_tag, view.getTranslationX());

        float translationX = view.getTranslationX();
        SpringAnimation springAnimation = new SpringAnimation(view, DynamicAnimation.TRANSLATION_X, translationX)
                .setSpring(new SpringForce(translationX).setStiffness(600f))
                .setStartVelocity(-shift * 100)
                .addEndListener((animation, canceled, value, velocity) -> {
                    if (endCallback != null) endCallback.run();

                    view.setTranslationX(translationX);
                    view.setTag(R.id.spring_tag, null);
                    view.setTag(R.id.spring_was_translation_x_tag, null);
                });
        view.setTag(R.id.spring_tag, springAnimation);
        springAnimation.start();
    }

    /**
     * NativeDo adaptation of upstream {@code replaceArrows} — upstream replaces
     * arrow glyphs with ColoredImageSpan-backed icons; the icon drawable and
     * multi-char replace helper are absent locally, so this passes text through.
     */
    public static CharSequence replaceArrows(CharSequence text, boolean link, int translateX, int translateY) {
        return text;
    }

    public static float getAnimatorDurationScale() {
        try {
            if (ApplicationLoader.applicationContext == null) {
                return 1.0f;
            }
            return android.provider.Settings.Global.getFloat(
                    ApplicationLoader.applicationContext.getContentResolver(),
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                    1.0f
            );
        } catch (Exception e) {
            return 1.0f;
        }
    }

    public static void adjustBrightnessColorMatrix(ColorMatrix colorMatrix, float brightness) {
        if (colorMatrix == null) {
            return;
        }
        brightness *= 255;
        colorMatrix.postConcat(new ColorMatrix(new float[]{
                1, 0, 0, 0, brightness,
                0, 1, 0, 0, brightness,
                0, 0, 1, 0, brightness,
                0, 0, 0, 1, 0
        }));
    }

    public static void multiplyBrightnessColorMatrix(ColorMatrix colorMatrix, float v) {
        float[] m = colorMatrix.getArray();
        m[0] *= v;
        m[1] *= v;
        m[2] *= v;
        m[5] *= v;
        m[6] *= v;
        m[7] *= v;
        m[10] *= v;
        m[11] *= v;
        m[12] *= v;
    }

    public static int calcBitmapColor(Bitmap bitmap) {
        if (bitmap == null) {
            return 0;
        }
        try {
            Bitmap b = Bitmaps.createScaledBitmap(bitmap, 1, 1, true);
            if (b != null) {
                int bitmapColor = b.getPixel(0, 0);
                if (bitmap != b) {
                    b.recycle();
                }
                return bitmapColor;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return 0;
    }

    public static int[] calcDrawableColor(Drawable drawable) {
        int bitmapColor = 0xff000000;
        int[] result = new int[4];
        try {
            if (drawable instanceof BitmapDrawable) {
                bitmapColor = calcBitmapColor(((BitmapDrawable) drawable).getBitmap());
            } else if (drawable instanceof ColorDrawable) {
                bitmapColor = ((ColorDrawable) drawable).getColor();
            } else if (drawable instanceof BackgroundGradientDrawable) {
                int[] colors = ((BackgroundGradientDrawable) drawable).getColorsList();
                if (colors != null) {
                    if (colors.length > 1) {
                        bitmapColor = getAverageColor(colors[0], colors[1]);
                    } else if (colors.length > 0) {
                        bitmapColor = colors[0];
                    }
                }
            } else if (drawable instanceof MotionBackgroundDrawable) {
                result[0] = result[2] = Color.argb(0x2D, 0, 0, 0);
                result[1] = result[3] = Color.argb(0x3D, 0, 0, 0);
                return result;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        double[] hsv = rgbToHsv(Color.red(bitmapColor), Color.green(bitmapColor), Color.blue(bitmapColor));
        hsv[1] = Math.min(1.0, hsv[1] + 0.05 + 0.1 * (1.0 - hsv[1]));
        int[] rgb = hsvToRgb(hsv[0], hsv[1], Math.max(0, hsv[2] * 0.65));
        result[0] = Color.argb(0x66, rgb[0], rgb[1], rgb[2]);
        result[1] = Color.argb(0x88, rgb[0], rgb[1], rgb[2]);
        rgb = hsvToRgb(hsv[0], hsv[1], Math.max(0, hsv[2] * 0.72));
        result[2] = Color.argb(0x66, rgb[0], rgb[1], rgb[2]);
        result[3] = Color.argb(0x88, rgb[0], rgb[1], rgb[2]);
        return result;
    }

    public static double[] rgbToHsv(int r, int g, int b) {
        double rf = r / 255.0;
        double gf = g / 255.0;
        double bf = b / 255.0;
        double max = (rf > gf && rf > bf) ? rf : Math.max(gf, bf);
        double min = (rf < gf && rf < bf) ? rf : Math.min(gf, bf);
        double d = max - min;
        double s = max == 0 ? 0 : d / max;
        double h;
        if (max == min) {
            h = 0;
        } else if (rf > gf && rf > bf) {
            h = (gf - bf) / d + (gf < bf ? 6 : 0);
        } else if (gf > bf) {
            h = (bf - rf) / d + 2;
        } else {
            h = (rf - gf) / d + 4;
        }
        return new double[]{h / 6, s, max};
    }

    public static int[] hsvToRgb(double h, double s, double v) {
        double r = 0, g = 0, b = 0;
        double i = Math.floor(h * 6);
        double f = h * 6 - i;
        double p = v * (1 - s);
        double q = v * (1 - f * s);
        double t = v * (1 - (1 - f) * s);
        switch ((int) i % 6) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            case 5: r = v; g = p; b = q; break;
        }
        return new int[]{(int) (r * 255), (int) (g * 255), (int) (b * 255)};
    }

    public static boolean copyFile(File sourceFile, File destFile) throws IOException {
        if (sourceFile.equals(destFile)) {
            return true;
        }
        if (!destFile.exists()) {
            destFile.createNewFile();
        }
        try (FileInputStream source = new FileInputStream(sourceFile);
             FileOutputStream destination = new FileOutputStream(destFile)) {
            destination.getChannel().transferFrom(source.getChannel(), 0, source.getChannel().size());
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
        return true;
    }

    public static int getWallpaperRotation(int angle, boolean iOS) {
        angle += iOS ? 180 : -180;
        while (angle >= 360) angle -= 360;
        while (angle < 0) angle += 360;
        return angle;
    }

    public static boolean isValidWallChar(char ch) {
        return ch == '-' || ch == '~';
    }

    public static File getSharingDirectory() {
        return new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "sharing/");
    }

    public static int getColorDistance(int color1, int color2) {
        int r1 = Color.red(color1), g1 = Color.green(color1), b1 = Color.blue(color1);
        int r2 = Color.red(color2), g2 = Color.green(color2), b2 = Color.blue(color2);
        int rMean = (r1 + r2) / 2;
        int r = r1 - r2, g = g1 - g2, b = b1 - b2;
        return (((512 + rMean) * r * r) >> 8) + (4 * g * g) + (((767 - rMean) * b * b) >> 8);
    }

    public static Bitmap getScaledBitmap(float w, float h, String path, String streamPath, int streamOffset) {
        FileInputStream stream = null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            if (path != null) {
                BitmapFactory.decodeFile(path, options);
            } else {
                stream = new FileInputStream(streamPath);
                stream.getChannel().position(streamOffset);
                BitmapFactory.decodeStream(stream, null, options);
            }
            if (options.outWidth > 0 && options.outHeight > 0) {
                if (w > h && options.outWidth < options.outHeight) {
                    float temp = w;
                    w = h;
                    h = temp;
                }
                float scale = Math.min(options.outWidth / w, options.outHeight / h);
                options.inSampleSize = 1;
                while (options.inSampleSize < scale) {
                    options.inSampleSize *= 2;
                }
                options.inJustDecodeBounds = false;
                if (path != null) {
                    return BitmapFactory.decodeFile(path, options);
                }
                stream.getChannel().position(streamOffset);
                return BitmapFactory.decodeStream(stream, null, options);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            try {
                if (stream != null) stream.close();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return null;
    }
}
