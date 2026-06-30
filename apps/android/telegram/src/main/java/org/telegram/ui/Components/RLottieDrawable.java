/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.R;

/**
 * NativeDo stub of org.telegram.ui.Components.RLottieDrawable (1473 lines upstream).
 *
 * <p>Extends {@link BitmapDrawable} to match upstream so {@code (BitmapDrawable)} casts
 * from ImageReceiver resolve.
 */
public class RLottieDrawable extends BitmapDrawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private Runnable onAnimationEndListener;
    private View masterParent;
    private int resId;
    private float progress;
    private int alpha = 255;
    private ColorFilter colorFilter;
    private ValueAnimator fallbackAnimator;

    public RLottieDrawable() {
        super((Bitmap) null);
    }

    public RLottieDrawable(int resId, String key, int w, int h) {
        super((Bitmap) null);
        this.resId = resId;
    }

    public RLottieDrawable(int resId, String key, int w, int h, boolean limitFps, int[] colorReplacement) {
        super((Bitmap) null);
        this.resId = resId;
    }

    public RLottieDrawable(int diceEmoji, int w, int h) {
        super((Bitmap) null);
    }

    public RLottieDrawable(String diceEmoji, int w, int h) {
        super((Bitmap) null);
    }

    public RLottieDrawable(java.io.File file, int data, int w, int h, org.telegram.messenger.utils.BitmapsCache.CacheOptions cacheOptions, boolean limitFps, int[] colorReplacement, String fitzModifier) {
        super((Bitmap) null);
    }

    public RLottieDrawable(java.io.File file, int w, int h, org.telegram.messenger.utils.BitmapsCache.CacheOptions cacheOptions, boolean limitFps, int[] colorReplacement, String fitzModifier) {
        super((Bitmap) null);
    }

    public RLottieDrawable(java.io.File file, int w, int h, org.telegram.messenger.utils.BitmapsCache.CacheOptions cacheOptions, boolean limitFps, int[] colorReplacement, int fitzModifier) {
        super((Bitmap) null);
    }

    public RLottieDrawable(java.io.File file, String json, int w, int h, org.telegram.messenger.utils.BitmapsCache.CacheOptions cacheOptions, boolean limitFps, int[] colorReplacement, String fitzModifier) {
        super((Bitmap) null);
    }

    public RLottieDrawable(java.io.File file, String json, int w, int h, org.telegram.messenger.utils.BitmapsCache.CacheOptions cacheOptions, boolean limitFps, int[] colorReplacement, int fitzModifier) {
        super((Bitmap) null);
    }

    public void setLimitFps(boolean value) {
    }

    public void prepareForGenerateCache() {
    }

    public void releaseForGenerateCache() {
    }

    public void setGeneratingFrame(int frame) {
    }

    public int getFramesCount() {
        return 0;
    }

    public int getNextFrame(Bitmap bitmap) {
        return 0;
    }

    public boolean skipFrameUpdate;

    public void start() {
        if (!isFallbackComposerIcon()) {
            if (onAnimationEndListener != null) {
                onAnimationEndListener.run();
            }
            return;
        }
        fallbackAnimator = ValueAnimator.ofFloat(progress, fallbackEndProgress());
        fallbackAnimator.setDuration(200);
        fallbackAnimator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidateSelf();
            if (masterParent != null) {
                masterParent.invalidate();
            }
        });
        fallbackAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                fallbackAnimator = null;
                if (onAnimationEndListener != null) {
                    onAnimationEndListener.run();
                }
            }
        });
        fallbackAnimator.start();
    }

    public void stop() {
        if (fallbackAnimator != null) {
            fallbackAnimator.cancel();
            fallbackAnimator = null;
        }
    }

    public boolean isRunning() {
        return fallbackAnimator != null;
    }

    public void recycle() {
    }

    public void recycle(boolean force) {
    }

    public boolean hasBitmap() {
        return false;
    }

    public void setMasterParent(View parent) {
        masterParent = parent;
    }

    public void setOnAnimationEndListener(Runnable onAnimationEndListener) {
        this.onAnimationEndListener = onAnimationEndListener;
    }

    public void setLayerColor(String layer, int color) {
    }

    public void beginApplyLayerColors() {
    }

    public void commitApplyLayerColors() {
    }

    public void replaceColors(int[] colors) {
    }

    public void setPlayInDirectionOfCustomEndFrame(boolean value) {
    }

    public boolean setCustomEndFrame(int frame) {
        return false;
    }

    public void setCurrentFrame(int frame) {
    }

    public void setProgress(float progress) {
        this.progress = progress;
        invalidateSelf();
    }

    public void setProgress(float progress, boolean async) {
        setProgress(progress);
    }

    public boolean isHeavyDrawable() {
        return false;
    }

    public Bitmap getAnimatedBitmap() {
        return null;
    }

    public void setAllowDrawFramesWhileCacheGenerating(boolean allow) {
    }

    public void addParentView(ImageReceiver receiver) {
    }

    public void removeParentView(ImageReceiver receiver) {
    }

    public void addParentView(View view) {
    }

    public void removeParentView(View view) {
    }

    public void setAutoRepeat(int autoRepeat) {
    }

    public void setAutoRepeatCount(int count) {
    }

    public void setAutoRepeatTimeout(long timeout) {
    }

    public void setAllowDecodeSingleFrame(boolean allow) {
    }

    public void setAllowVibration(boolean allow) {
    }

    public void setStartEndTime(long start, long end) {
    }

    public void setUseSharedQueue(boolean use) {
    }

    public void checkRepeat() {
    }

    public void drawInBackground(android.graphics.Canvas canvas, float x, float y, float w, float h, int alpha, android.graphics.ColorFilter colorFilter, int threadIndex) {
    }

    public void drawInternal(android.graphics.Canvas canvas, android.graphics.RectF rect, boolean drawInBackground, long currentTime, int threadIndex) {
    }

    public void restart(boolean force) {
    }

    @Override
    public void draw(Canvas canvas) {
        if (!isFallbackComposerIcon()) {
            return;
        }
        float p = fallbackDisplayProgress();
        int save = canvas.save();
        RectF bounds = new RectF(getBounds());
        if (bounds.isEmpty() && masterParent != null) {
            bounds.set(0, 0, masterParent.getWidth(), masterParent.getHeight());
        }
        drawSmileKeyboardFallback(canvas, bounds, p);
        canvas.restoreToCount(save);
    }

    private boolean isFallbackComposerIcon() {
        return resId == R.raw.smile_to_keyboard ||
                resId == R.raw.keyboard_to_smile ||
                resId == R.raw.keyboard_to_sticker ||
                resId == R.raw.keyboard_to_gif ||
                resId == R.raw.sticker_to_keyboard ||
                resId == R.raw.gif_to_keyboard ||
                resId == R.raw.smile_to_sticker ||
                resId == R.raw.smile_to_gif ||
                resId == R.raw.sticker_to_smile ||
                resId == R.raw.gif_to_smile;
    }

    private float fallbackEndProgress() {
        if (resId == R.raw.keyboard_to_smile) {
            return 1f;
        }
        return 1f;
    }

    private float fallbackDisplayProgress() {
        float p = Math.max(0f, Math.min(1f, progress));
        if (resId == R.raw.keyboard_to_smile) {
            return 1f - p;
        }
        if (resId == R.raw.keyboard_to_sticker || resId == R.raw.keyboard_to_gif) {
            return 1f;
        }
        if (resId == R.raw.gif_to_keyboard || resId == R.raw.sticker_to_keyboard) {
            return p;
        }
        return p;
    }

    private void drawSmileKeyboardFallback(Canvas canvas, RectF bounds, float progress) {
        float cx = bounds.centerX();
        float cy = bounds.centerY();
        float t = Math.max(0f, Math.min(1f, progress));
        paint.setColor(0xff000000);
        paint.setAlpha(alpha);
        paint.setColorFilter(colorFilter);
        paint.setStrokeWidth(AndroidUtilities.dp(2));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStyle(Paint.Style.STROKE);

        float halfWidth = lerp(AndroidUtilities.dp(10), AndroidUtilities.dp(11), t);
        float halfHeight = lerp(AndroidUtilities.dp(10), AndroidUtilities.dp(8), t);
        float radius = lerp(AndroidUtilities.dp(10), AndroidUtilities.dp(2), t);
        rect.set(cx - halfWidth, cy - halfHeight, cx + halfWidth, cy + halfHeight);
        canvas.drawRoundRect(rect, radius, radius, paint);

        int smileAlpha = (int) ((1f - Math.min(1f, t / 0.72f)) * alpha);
        if (smileAlpha > 0) {
            paint.setAlpha(smileAlpha);
            paint.setStyle(Paint.Style.FILL);
            float eyeRadius = lerp(AndroidUtilities.dp(1.4f), AndroidUtilities.dp(0.8f), t);
            float eyeY = lerp(cy - AndroidUtilities.dp(3), cy - AndroidUtilities.dp(5), t);
            float eyeSpread = lerp(AndroidUtilities.dp(4), AndroidUtilities.dp(7), t);
            canvas.drawCircle(cx - eyeSpread, eyeY, eyeRadius, paint);
            canvas.drawCircle(cx + eyeSpread, eyeY, eyeRadius, paint);

            paint.setStyle(Paint.Style.STROKE);
            rect.set(cx - AndroidUtilities.dp(5), cy - AndroidUtilities.dp(3), cx + AndroidUtilities.dp(5), cy + AndroidUtilities.dp(6));
            canvas.drawArc(rect, 25f, 130f, false, paint);
        }

        int keyboardAlpha = (int) (Math.max(0f, Math.min(1f, (t - 0.28f) / 0.72f)) * alpha);
        if (keyboardAlpha > 0) {
            paint.setAlpha(keyboardAlpha);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dp(1.7f));
            float keyTop = cy - AndroidUtilities.dp(3);
            int keySpacing = AndroidUtilities.dp(5);
            for (int row = 0; row <= 1; row++) {
                float y = keyTop + row * AndroidUtilities.dp(4);
                canvas.drawLine(cx - keySpacing, y, cx - keySpacing + AndroidUtilities.dp(1), y, paint);
                canvas.drawLine(cx, y, cx + AndroidUtilities.dp(1), y, paint);
                canvas.drawLine(cx + keySpacing, y, cx + keySpacing + AndroidUtilities.dp(1), y, paint);
            }
            canvas.drawLine(cx - AndroidUtilities.dp(5), cy + AndroidUtilities.dp(5), cx + AndroidUtilities.dp(5), cy + AndroidUtilities.dp(5), paint);
        }
        paint.setAlpha(255);
        paint.setColorFilter(null);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        this.colorFilter = colorFilter;
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
