// PORT-MODE: Native Feature
// WORKFLOW-MAP: docs/telegram-port/browser.md
// TELEGRAM-PARITY:
//   - None. This is a current-phase NativeDo single-image overlay used by Browser Instant View.
// PARITY-BLOCKER:
//   - Exact Telegram PhotoViewer / PinchToZoomHelper gesture physics is deferred.
/*
 * This file is part of NativeDo.
 *
 * Copyright (C) 2026 NativeDo contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under terms of GNU General Public License version 2 or later as published by
 * the Free Software Foundation.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.browser;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.baba983.nativedo.ui.components.CubicBezierInterpolator;

final class BrowserInstantPhotoViewer extends FrameLayout {

    private static final ExecutorService LOAD_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private static final float MIN_SCALE = 1.0f;
    private static final float MAX_SCALE = 4.0f;
    private static final float DOUBLE_TAP_SCALE = 2.5f;
    private static final long OPEN_DURATION = 220L;
    private static final long CLOSE_DURATION = 200L;
    private static final long ZOOM_DURATION = 240L;
    private static final long DISMISS_SPRING_DURATION = 240L;

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix imageMatrix = new Matrix();
    private final Matrix savedMatrix = new Matrix();
    private final RectF imageBounds = new RectF();
    private final RectF measureBounds = new RectF();
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector tapDetector;

    @Nullable private Bitmap bitmap;
    @Nullable private String pendingUrl;
    @Nullable private Runnable onDismiss;

    private float openProgress;
    private float dismissOffsetY;
    private float dismissProgress;
    private boolean dismissing;

    private float startX, startY;
    private float lastTouchX, lastTouchY;
    private boolean dragging;
    private boolean multiTouch;
    private boolean handledByDetectors;

    @Nullable private ValueAnimator openAnimator;
    @Nullable private ValueAnimator zoomAnimator;
    @Nullable private ValueAnimator dismissOffsetAnimator;

    BrowserInstantPhotoViewer(@NonNull Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(true);
        setFocusable(true);
        backgroundPaint.setColor(Color.BLACK);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                cancelZoomAnimator();
                float factor = detector.getScaleFactor();
                float current = currentScale();
                float target = current * factor;
                target = Math.max(MIN_SCALE * .85f, Math.min(MAX_SCALE * 1.15f, target));
                float applied = target / current;
                imageMatrix.postScale(applied, applied, detector.getFocusX(), detector.getFocusY());
                clampMatrixOnTheFly();
                invalidate();
                return true;
            }
        });
        scaleDetector.setQuickScaleEnabled(false);

        tapDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                if (currentScale() > MIN_SCALE * 1.05f) {
                    animateZoomTo(MIN_SCALE, getWidth() / 2f, getHeight() / 2f);
                } else {
                    dismiss();
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                float current = currentScale();
                if (current > MIN_SCALE * 1.05f) {
                    animateZoomTo(MIN_SCALE, getWidth() / 2f, getHeight() / 2f);
                } else {
                    animateZoomTo(DOUBLE_TAP_SCALE, e.getX(), e.getY());
                }
                return true;
            }
        });
        tapDetector.setIsLongpressEnabled(false);
    }

    void setOnDismissListener(@Nullable Runnable listener) {
        this.onDismiss = listener;
    }

    void show(@Nullable Bitmap initial, @Nullable String url) {
        bitmap = initial;
        pendingUrl = url;
        if (bitmap != null) {
            resetMatrix();
        } else if (url != null) {
            loadFromNetwork(url);
        }
        startOpenAnimation();
    }

    private void startOpenAnimation() {
        if (openAnimator != null) {
            openAnimator.cancel();
        }
        openProgress = 0f;
        invalidate();
        openAnimator = ValueAnimator.ofFloat(0f, 1f).setDuration(OPEN_DURATION);
        openAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        openAnimator.addUpdateListener(animation -> {
            openProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        openAnimator.start();
    }

    void dismiss() {
        if (dismissing) {
            return;
        }
        dismissing = true;
        if (openAnimator != null) {
            openAnimator.cancel();
        }
        ValueAnimator close = ValueAnimator.ofFloat(openProgress, 0f).setDuration(CLOSE_DURATION);
        close.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        close.addUpdateListener(animation -> {
            openProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        close.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                ViewGroup parent = (ViewGroup) getParent();
                if (parent != null) {
                    parent.removeView(BrowserInstantPhotoViewer.this);
                }
                if (onDismiss != null) {
                    onDismiss.run();
                }
            }
        });
        close.start();
    }

    private void loadFromNetwork(@NonNull String url) {
        final String requested = url;
        LOAD_EXECUTOR.execute(() -> {
            Bitmap loaded = null;
            HttpURLConnection connection = null;
            InputStream stream = null;
            try {
                connection = (HttpURLConnection) new URL(requested).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setInstanceFollowRedirects(true);
                stream = connection.getInputStream();
                loaded = BitmapFactory.decodeStream(stream);
            } catch (Exception ignored) {
                loaded = null;
            } finally {
                try {
                    if (stream != null) {
                        stream.close();
                    }
                } catch (Exception ignored) {
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
            final Bitmap finalLoaded = loaded;
            MAIN_HANDLER.post(() -> {
                if (!requested.equals(pendingUrl)) {
                    return;
                }
                if (finalLoaded != null) {
                    bitmap = finalLoaded;
                    resetMatrix();
                    invalidate();
                }
            });
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (bitmap != null) {
            resetMatrix();
        }
    }

    private void resetMatrix() {
        if (bitmap == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        float bw = bitmap.getWidth();
        float bh = bitmap.getHeight();
        if (bw <= 0 || bh <= 0) {
            return;
        }
        float scale = Math.min(getWidth() / bw, getHeight() / bh);
        if (scale > 1f) {
            scale = 1f; // do not upscale on initial fit
        }
        float drawW = bw * scale;
        float drawH = bh * scale;
        float dx = (getWidth() - drawW) / 2f;
        float dy = (getHeight() - drawH) / 2f;
        imageMatrix.reset();
        imageMatrix.postScale(scale, scale);
        imageMatrix.postTranslate(dx, dy);
        dismissOffsetY = 0f;
        dismissProgress = 0f;
    }

    private float currentScale() {
        float[] values = new float[9];
        imageMatrix.getValues(values);
        return values[Matrix.MSCALE_X];
    }

    private void clampMatrixOnTheFly() {
        if (bitmap == null) {
            return;
        }
        // Soft clamp during gesture: only minimum scale (snap-back happens on UP).
        float scale = currentScale();
        float baseScale = baseFitScale();
        if (scale < baseScale) {
            float factor = baseScale / scale;
            imageMatrix.postScale(factor, factor, getWidth() / 2f, getHeight() / 2f);
        }
    }

    private float baseFitScale() {
        if (bitmap == null) {
            return 1f;
        }
        float bw = bitmap.getWidth();
        float bh = bitmap.getHeight();
        if (bw <= 0 || bh <= 0) {
            return 1f;
        }
        float fit = Math.min(getWidth() / bw, getHeight() / bh);
        return Math.min(1f, fit);
    }

    private void clampTranslation() {
        if (bitmap == null) {
            return;
        }
        imageBounds.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
        imageMatrix.mapRect(imageBounds);

        float dx = 0;
        float dy = 0;
        float w = getWidth();
        float h = getHeight();

        if (imageBounds.width() <= w) {
            dx = (w - imageBounds.width()) / 2f - imageBounds.left;
        } else if (imageBounds.left > 0) {
            dx = -imageBounds.left;
        } else if (imageBounds.right < w) {
            dx = w - imageBounds.right;
        }

        if (imageBounds.height() <= h) {
            dy = (h - imageBounds.height()) / 2f - imageBounds.top;
        } else if (imageBounds.top > 0) {
            dy = -imageBounds.top;
        } else if (imageBounds.bottom < h) {
            dy = h - imageBounds.bottom;
        }

        if (dx != 0 || dy != 0) {
            imageMatrix.postTranslate(dx, dy);
        }
    }

    private void animateZoomTo(float targetScale, float focusX, float focusY) {
        cancelZoomAnimator();
        float startScale = currentScale();
        Matrix start = new Matrix(imageMatrix);
        Matrix end = new Matrix(imageMatrix);
        float factor = targetScale / startScale;
        end.postScale(factor, factor, focusX, focusY);

        float[] startValues = new float[9];
        float[] endValues = new float[9];
        start.getValues(startValues);
        end.getValues(endValues);

        zoomAnimator = ValueAnimator.ofFloat(0f, 1f).setDuration(ZOOM_DURATION);
        zoomAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        zoomAnimator.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            float[] values = new float[9];
            for (int i = 0; i < 9; i++) {
                values[i] = startValues[i] + (endValues[i] - startValues[i]) * t;
            }
            imageMatrix.setValues(values);
            invalidate();
        });
        zoomAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                clampTranslation();
                invalidate();
            }
        });
        zoomAnimator.start();
    }

    private void cancelZoomAnimator() {
        if (zoomAnimator != null) {
            zoomAnimator.cancel();
            zoomAnimator = null;
        }
    }

    private void cancelDismissOffsetAnimator() {
        if (dismissOffsetAnimator != null) {
            dismissOffsetAnimator.cancel();
            dismissOffsetAnimator = null;
        }
    }

    private void springDismissOffsetBack() {
        cancelDismissOffsetAnimator();
        float startOffset = dismissOffsetY;
        float startProgress = dismissProgress;
        dismissOffsetAnimator = ValueAnimator.ofFloat(0f, 1f).setDuration(DISMISS_SPRING_DURATION);
        dismissOffsetAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        dismissOffsetAnimator.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            dismissOffsetY = startOffset * (1f - t);
            dismissProgress = startProgress * (1f - t);
            invalidate();
        });
        dismissOffsetAnimator.start();
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        int pointerCount = event.getPointerCount();
        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            multiTouch = true;
            cancelDismissOffsetAnimator();
            dismissOffsetY = 0f;
            dismissProgress = 0f;
            invalidate();
        }
        boolean scaleHandled = scaleDetector.onTouchEvent(event);
        boolean tapHandled = tapDetector.onTouchEvent(event);
        handledByDetectors = scaleHandled || tapHandled;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancelDismissOffsetAnimator();
                startX = event.getX();
                startY = event.getY();
                lastTouchX = startX;
                lastTouchY = startY;
                dragging = false;
                multiTouch = false;
                savedMatrix.set(imageMatrix);
                break;
            case MotionEvent.ACTION_MOVE:
                if (pointerCount == 1 && !scaleDetector.isInProgress()) {
                    float dx = event.getX() - lastTouchX;
                    float dy = event.getY() - lastTouchY;
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    if (currentScale() > baseFitScale() * 1.01f) {
                        imageMatrix.postTranslate(dx, dy);
                        clampTranslation();
                        invalidate();
                    } else {
                        // Drag-to-dismiss when at base scale.
                        dismissOffsetY = event.getY() - startY;
                        float maxRange = Math.max(1, getHeight() / 2f);
                        dismissProgress = Math.max(0f, Math.min(1f, Math.abs(dismissOffsetY) / maxRange));
                        dragging = true;
                        invalidate();
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (multiTouch) {
                    clampTranslation();
                    multiTouch = false;
                    invalidate();
                } else if (dragging) {
                    if (dismissProgress >= 0.30f) {
                        dismiss();
                    } else {
                        springDismissOffsetBack();
                    }
                    dragging = false;
                }
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float baseAlpha = openProgress;
        float dismissAlphaFactor = 1f - 0.85f * dismissProgress;
        backgroundPaint.setAlpha((int) (255 * baseAlpha * dismissAlphaFactor));
        canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);

        if (bitmap == null) {
            return;
        }

        canvas.save();
        if (openProgress < 1f) {
            float scale = 0.92f + 0.08f * openProgress;
            canvas.scale(scale, scale, getWidth() / 2f, getHeight() / 2f);
            backgroundPaint.setColor(Color.BLACK); // restore
        }
        if (dismissOffsetY != 0f) {
            canvas.translate(0, dismissOffsetY);
            float dismissScale = 1f - 0.06f * dismissProgress;
            canvas.scale(dismissScale, dismissScale, getWidth() / 2f, getHeight() / 2f);
        }
        Paint imagePaint = null;
        if (openProgress < 1f) {
            imagePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
            imagePaint.setAlpha((int) (255 * openProgress));
        }
        canvas.drawBitmap(bitmap, imageMatrix, imagePaint);
        canvas.restore();
    }

    @Override
    public boolean dispatchKeyEventPreIme(android.view.KeyEvent event) {
        if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK
                && event.getAction() == android.view.KeyEvent.ACTION_UP) {
            dismiss();
            return true;
        }
        return super.dispatchKeyEventPreIme(event);
    }

    boolean handleBackPressed() {
        if (!dismissing) {
            dismiss();
            return true;
        }
        return false;
    }
}
