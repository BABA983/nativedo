// PORT-MODE: Exact Component Port (animation/recycling subsystems)
// UPSTREAM-SCOPE: org/telegram/messenger/ImageReceiver.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// HOST-WIRING:
//   - Bitmap loading remains owned by NativeDo Coil/OkHttp call sites.
//   - Cross-fade timing uses SystemClock (upstream: ImageLoader-driven animation ticks).
// ADAPTED-BACKEND:
//   - No FileLoader / ImageLoader integration; bitmaps are set directly via
//     setImageBitmap / setThumbBitmap instead of ImageLocation.
//   - AnimatedFileDrawable, BlurringShader, and multi-layer decorators omitted.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/messenger/ImageReceiver.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/messenger/ImageReceiver.java
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

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Telegram-shaped image receiver with thumb → full cross-fade support.
 *
 * <p>Matches the public API surface that {@code PhotoViewer} and
 * {@code ClippingImageView} expect from upstream's {@code ImageReceiver}.
 */
public class ImageReceiver {

    // ── Constants (mirror upstream) ────────────────────────────────────

    public static final int DEFAULT_CROSSFADE_DURATION = 150;

    // ── Public types ────────────────────────────────────────────────────

    public enum ScaleType {
        FIT_CENTER,
        CENTER_CROP
    }

    /** Mirrors upstream {@code ImageReceiver.ImageReceiverDelegate}. */
    public interface ImageReceiverDelegate {
        void didSetImage(ImageReceiver imageReceiver, boolean set, boolean thumb, boolean memCache);
    }

    // ── Drawing state ───────────────────────────────────────────────────

    private final RectF imageCoords = new RectF();
    private final RectF drawRegion = new RectF();
    private final RectF bitmapRect = new RectF();
    private final RectF roundRect = new RectF();
    private final Matrix shaderMatrix = new Matrix();
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint roundPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Path roundPath = new Path();
    private final int[] roundRadius = new int[]{0, 0, 0, 0};

    @Nullable private View parentView;
    @Nullable private Bitmap bitmap;
    @Nullable private BitmapShader bitmapShader;
    private float currentAlpha = 1f;
    private boolean visible = true;
    private ScaleType scaleType = ScaleType.FIT_CENTER;

    // ── Thumb / cross-fade (matching upstream shape) ────────────────────

    @Nullable private Bitmap thumbBitmap;
    @Nullable private BitmapShader thumbShader;
    private byte crossfadeAlpha = 1;
    private boolean crossfadeWithThumb;
    private int crossfadeDuration = DEFAULT_CROSSFADE_DURATION;
    private long crossfadeStartTime;
    private boolean isCrossfading;

    // ── Orientation (used by PhotoViewer's ClippingImageView) ───────────

    private int orientation;
    private int invert;
    private boolean allowStartAnimation;

    // ── Delegate (mirror upstream callback) ─────────────────────────────

    @Nullable private ImageReceiverDelegate delegate;
    private boolean invalidateAll;

    // ── Construction ────────────────────────────────────────────────────

    public ImageReceiver() {
    }

    public ImageReceiver(@Nullable View parentView) {
        this.parentView = parentView;
    }

    // ── Parent view ─────────────────────────────────────────────────────

    public void setParentView(@Nullable View view) {
        parentView = view;
    }

    @Nullable
    public View getParentView() {
        return parentView;
    }

    // ── Full bitmap ─────────────────────────────────────────────────────

    public void setImageBitmap(@Nullable Bitmap value) {
        bitmap = value;
        bitmapShader = value == null || value.isRecycled()
                ? null
                : new BitmapShader(value, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        roundPaint.setShader(bitmapShader);
        updateDrawRegion();
        invalidateParent();

        // If a thumb is already showing, start cross-fade from thumb → full.
        if (crossfadeWithThumb && thumbBitmap != null && bitmap != null
                && !thumbBitmap.isRecycled() && !bitmap.isRecycled()) {
            isCrossfading = true;
            crossfadeStartTime = SystemClock.elapsedRealtime();
        }
        notifyDelegate(false);
    }

    /**
     * Loads and displays the image at {@code url}. Mirrors upstream
     * {@code ImageReceiver.setImage(ImageLocation, thumbLocation, ...)} — the receiver
     * handles thumb population, async full-image loading, and cross-fade internally.
     *
     * <p>Cache is checked BEFORE clearing any state: a cached full bitmap is shown
     * synchronously without any blank/thumb frame.
     */
    public void setImage(@Nullable String url) {
        if (url == null) {
            bitmap = null;
            bitmapShader = null;
            thumbBitmap = null;
            thumbShader = null;
            isCrossfading = false;
            updateDrawRegion();
            invalidateParent();
            return;
        }

        // Sync path: full bitmap cached — show it immediately with no cross-fade.
        // Must clear any stale thumb from a previous session, otherwise
        // setImageBitmap() sees thumbBitmap != null and starts a 150ms cross-fade
        // from the old low-res thumb to the (identical) full bitmap, producing
        // a visible blur on re-entry.
        Bitmap cached = ImageLoader.get(url);
        if (cached != null) {
            thumbBitmap = null;
            thumbShader = null;
            isCrossfading = false;
            setImageBitmap(cached);
            return;
        }

        bitmap = null;
        bitmapShader = null;
        isCrossfading = false;
        thumbBitmap = null;
        thumbShader = null;

        Bitmap immediateThumb = ImageLoader.getThumbnail(url);
        if (immediateThumb != null) {
            setThumbBitmap(immediateThumb);
        }

        ImageReceiver self = this;
        ImageLoader.loadAsync(url, fullBitmap -> {
            if (fullBitmap != null) {
                Bitmap thumb = ImageLoader.getThumbnail(url);
                if (thumb == null) {
                    thumb = ImageLoader.createThumbnail(fullBitmap);
                    if (thumb != null) {
                        ImageLoader.putThumbnail(url, thumb);
                    }
                }
                self.setImageBitmap(fullBitmap);
            }
        });
    }

    @Nullable
    public Bitmap getBitmap() {
        return bitmap;
    }

    // ── Thumb bitmap ────────────────────────────────────────────────────

    public void setThumbBitmap(@Nullable Bitmap value) {
        thumbBitmap = value;
        thumbShader = value == null || value.isRecycled()
                ? null
                : new BitmapShader(value, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        // Don't overwrite drawRegion — the full bitmap defines the layout.
        // Just invalidate so the thumb is drawn on the next frame.
        invalidateParent();
        notifyDelegate(true);
    }

    @Nullable
    public Bitmap getThumbBitmap() {
        return thumbBitmap;
    }

    // ── Cross-fade control ──────────────────────────────────────────────

    /**
     * @param value 0 = disabled, 1 = normal, 2 = aggressive (matches upstream byte encoding)
     */
    public void setCrossfadeAlpha(byte value) {
        crossfadeAlpha = value;
    }

    public byte getCrossfadeAlpha() {
        return crossfadeAlpha;
    }

    public void setCrossfadeWithThumb(boolean value) {
        crossfadeWithThumb = value;
    }

    public boolean isCrossfadeWithThumb() {
        return crossfadeWithThumb;
    }

    public void setCrossfadeDuration(int duration) {
        crossfadeDuration = duration;
    }

    public int getCrossfadeDuration() {
        return crossfadeDuration;
    }

    // ── Image coords ────────────────────────────────────────────────────

    public void setImageCoords(float x, float y, float width, float height) {
        imageCoords.set(x, y, x + width, y + height);
        updateDrawRegion();
    }

    public void setImageCoords(@NonNull RectF bounds) {
        imageCoords.set(bounds);
        updateDrawRegion();
    }

    public float getImageX() {
        return drawRegion.left;
    }

    public float getImageY() {
        return drawRegion.top;
    }

    public float getImageX2() {
        return drawRegion.right;
    }

    public float getImageY2() {
        return drawRegion.bottom;
    }

    public float getImageWidth() {
        return drawRegion.width();
    }

    public float getImageHeight() {
        return drawRegion.height();
    }

    // ── Draw region (public API mirrors upstream) ───────────────────────

    @NonNull
    public RectF getDrawRegion() {
        return drawRegion;
    }

    public boolean isAspectFit() {
        return scaleType == ScaleType.FIT_CENTER;
    }

    // ── Scale type ──────────────────────────────────────────────────────

    public void setScaleType(@NonNull ScaleType value) {
        if (scaleType != value) {
            scaleType = value;
            updateDrawRegion();
            invalidateParent();
        }
    }

    // ── Round radius ────────────────────────────────────────────────────

    public void setRoundRadius(float radius) {
        int rounded = Math.max(0, Math.round(radius));
        for (int i = 0; i < roundRadius.length; i++) {
            roundRadius[i] = rounded;
        }
        invalidateParent();
    }

    public void setRoundRadius(int[] radius) {
        if (radius == null) {
            for (int i = 0; i < roundRadius.length; i++) {
                roundRadius[i] = 0;
            }
        } else {
            for (int i = 0; i < roundRadius.length; i++) {
                roundRadius[i] = i < radius.length ? Math.max(0, radius[i]) : 0;
            }
        }
        invalidateParent();
    }

    public int[] getRoundRadius() {
        return getRoundRadius(false);
    }

    public int[] getRoundRadius(boolean includingEmpty) {
        if (!includingEmpty) {
            boolean empty = true;
            for (int value : roundRadius) {
                if (value != 0) {
                    empty = false;
                    break;
                }
            }
            if (empty) {
                return null;
            }
        }
        return new int[]{roundRadius[0], roundRadius[1], roundRadius[2], roundRadius[3]};
    }

    // ── Orientation / invert (used by ClippingImageView close animation) ─

    public void setOrientation(int orientation) {
        setOrientation(orientation, 0);
    }

    public void setOrientation(int orientation, int invert) {
        this.orientation = orientation;
        this.invert = invert;
    }

    public int getOrientation() {
        return orientation;
    }

    public int getInvert() {
        return invert;
    }

    // ── Animation control (stubs until AnimatedFileDrawable is ported) ──

    public void setAllowStartAnimation(boolean value) {
        allowStartAnimation = value;
    }

    public void startAnimation() {
        // Stub: upstream would start the animated drawable loop.
    }

    // ── Delegate ────────────────────────────────────────────────────────

    public void setDelegate(@Nullable ImageReceiverDelegate value) {
        delegate = value;
    }

    @Nullable
    public ImageReceiverDelegate getDelegate() {
        return delegate;
    }

    public void setInvalidateAll(boolean value) {
        invalidateAll = value;
    }

    private void notifyDelegate(boolean thumb) {
        if (delegate != null) {
            boolean hasImage = bitmap != null && !bitmap.isRecycled();
            delegate.didSetImage(this, hasImage, thumb, false);
        }
    }

    // ── Alpha ───────────────────────────────────────────────────────────

    /** Mirrors upstream {@code ImageReceiver.setAlpha(float)}. */
    public void setAlpha(float value) {
        setCurrentAlpha(value);
    }

    public void setCurrentAlpha(float value) {
        currentAlpha = value;
        invalidateParent();
    }

    public float getCurrentAlpha() {
        return currentAlpha;
    }

    // ── Visibility ──────────────────────────────────────────────────────

    public void setVisible(boolean value, boolean invalidate) {
        if (visible != value) {
            visible = value;
            if (invalidate) {
                invalidateParent();
            }
        }
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean hasImageSet() {
        return bitmap != null && !bitmap.isRecycled();
    }

    // ── Lifecycle ───────────────────────────────────────────────────────

    public void onAttachedToWindow() {
    }

    public void onDetachedFromWindow() {
        // Clear cross-fade state so the next attach + setImageBitmap does not
        // cross-fade from a stale thumb. Mirrors upstream ImageReceiver cleanup
        // on window detach.
        thumbBitmap = null;
        thumbShader = null;
        isCrossfading = false;
    }

    // ── BitmapHolder ────────────────────────────────────────────────────

    @Nullable
    public BitmapHolder getBitmapSafe() {
        return bitmap == null || bitmap.isRecycled() ? null : new BitmapHolder(bitmap);
    }

    // ── Drawing ─────────────────────────────────────────────────────────

    public boolean draw(@NonNull Canvas canvas) {
        if (!visible || drawRegion.width() <= 0f || drawRegion.height() <= 0f) {
            return false;
        }

        boolean hasFull = bitmap != null && !bitmap.isRecycled();
        boolean hasThumb = thumbBitmap != null && !thumbBitmap.isRecycled();

        if (!hasFull && !hasThumb) {
            return false;
        }

        int baseAlpha = Math.max(0, Math.min(255, Math.round(255f * currentAlpha)));
        if (baseAlpha <= 0) {
            return false;
        }

        // Compute cross-fade progress: when only a thumb is set, it draws at full
        // alpha. When the full bitmap arrives during an active cross-fade, the
        // full alpha ramps from 0→255 over crossfadeDuration ms while the thumb
        // alpha ramps 255→0.
        float fullAlphaFraction = 1f;
        float thumbAlphaFraction = 0f;
        if (isCrossfading && hasFull && hasThumb && crossfadeAlpha != 0) {
            long elapsed = SystemClock.elapsedRealtime() - crossfadeStartTime;
            if (elapsed >= crossfadeDuration) {
                isCrossfading = false;
                fullAlphaFraction = 1f;
                thumbAlphaFraction = 0f;
            } else {
                fullAlphaFraction = (float) elapsed / (float) crossfadeDuration;
                thumbAlphaFraction = 1f - fullAlphaFraction;
            }
        } else if (hasFull) {
            fullAlphaFraction = 1f;
            thumbAlphaFraction = 0f;
        } else {
            // Only thumb available — draw it at full alpha.
            fullAlphaFraction = 0f;
            thumbAlphaFraction = 1f;
        }

        boolean hasRadius = false;
        for (int value : roundRadius) {
            if (value > 0) {
                hasRadius = true;
                break;
            }
        }

        // Draw thumb first (behind the full bitmap).
        if (hasThumb && thumbAlphaFraction > 0f) {
            int thumbAlpha = Math.round(baseAlpha * thumbAlphaFraction);
            drawBitmapInternal(canvas, thumbBitmap, thumbShader, thumbAlpha, hasRadius);
        }

        // Draw full bitmap on top with cross-fade alpha.
        if (hasFull && fullAlphaFraction > 0f) {
            int fullAlpha = Math.round(baseAlpha * fullAlphaFraction);
            drawBitmapInternal(canvas, bitmap, bitmapShader, fullAlpha, hasRadius);
        }

        return true;
    }

    /**
     * Internal draw for a single bitmap layer (thumb or full).
     */
    private void drawBitmapInternal(@NonNull Canvas canvas, @NonNull Bitmap bmp,
                                    @Nullable BitmapShader shader, int alpha, boolean hasRadius) {
        if (alpha <= 0) {
            return;
        }
        if (!hasRadius) {
            bitmapPaint.setAlpha(alpha);
            canvas.drawBitmap(bmp, null, drawRegion, bitmapPaint);
            bitmapPaint.setAlpha(255);
            return;
        }
        if (shader == null) {
            return;
        }
        bitmapRect.set(0, 0, bmp.getWidth(), bmp.getHeight());
        shaderMatrix.reset();
        AndroidUtilities.setRectToRect(shaderMatrix, bitmapRect, drawRegion, 0, 0,
                scaleType == ScaleType.CENTER_CROP);
        shader.setLocalMatrix(shaderMatrix);
        roundPaint.setAlpha(alpha);
        roundRect.set(drawRegion);
        roundPath.rewind();
        float[] radii = new float[]{
                roundRadius[0], roundRadius[0],
                roundRadius[1], roundRadius[1],
                roundRadius[2], roundRadius[2],
                roundRadius[3], roundRadius[3]
        };
        roundPath.addRoundRect(roundRect, radii, Path.Direction.CW);
        canvas.drawPath(roundPath, roundPaint);
        roundPaint.setAlpha(255);
    }

    // ── Internal helpers ────────────────────────────────────────────────

    private void updateDrawRegion() {
        if (bitmap == null || bitmap.isRecycled() || imageCoords.width() <= 0f || imageCoords.height() <= 0f) {
            drawRegion.set(imageCoords);
            return;
        }
        if (scaleType == ScaleType.CENTER_CROP) {
            drawRegion.set(imageCoords);
            return;
        }
        float scale = Math.min(imageCoords.width() / bitmap.getWidth(), imageCoords.height() / bitmap.getHeight());
        float width = bitmap.getWidth() * scale;
        float height = bitmap.getHeight() * scale;
        drawRegion.set(
                imageCoords.centerX() - width / 2f,
                imageCoords.centerY() - height / 2f,
                imageCoords.centerX() + width / 2f,
                imageCoords.centerY() + height / 2f
        );
    }

    private void invalidateParent() {
        if (parentView != null) {
            if (invalidateAll) {
                parentView.invalidate();
            } else {
                parentView.invalidate();
            }
        }
    }

    // ── GroupedPhotosListView support (mirrors upstream API) ────────────

    private int param;

    public void setParam(int value) {
        param = value;
    }

    public int getParam() {
        return param;
    }

    public boolean isInsideImage(float x, float y) {
        return drawRegion.contains(x, y);
    }

    public void setImageX(float value) {
        drawRegion.offset(value - drawRegion.left, 0);
    }

    public void setImageWidth(float value) {
        drawRegion.right = drawRegion.left + value;
    }

    public void setCurrentAccount(int account) {
        // NativeDo is single-account — no-op.
    }

    public void setAllowLoadingOnAttachedOnly(boolean value) {
        // NativeDo ImageReceiver doesn't defer loading — no-op.
    }

    // ── BitmapHolder ────────────────────────────────────────────────────

    public static class BitmapHolder {

        private boolean recycleOnRelease;
        public Bitmap bitmap;
        public Drawable drawable;
        public int orientation;

        public BitmapHolder(Bitmap b, String k, int o) {
            bitmap = b;
            orientation = o;
        }

        public BitmapHolder(Drawable d, String k, int o) {
            drawable = d;
            orientation = o;
            if (d instanceof BitmapDrawable) {
                bitmap = ((BitmapDrawable) d).getBitmap();
            }
        }

        public BitmapHolder(Bitmap b) {
            bitmap = b;
        }

        public int getWidth() {
            return bitmap != null ? bitmap.getWidth() : 0;
        }

        public int getHeight() {
            return bitmap != null ? bitmap.getHeight() : 0;
        }

        public boolean isRecycled() {
            return bitmap == null || bitmap.isRecycled();
        }

        public void release() {
            if (recycleOnRelease && bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            bitmap = null;
            drawable = null;
        }
    }
}
