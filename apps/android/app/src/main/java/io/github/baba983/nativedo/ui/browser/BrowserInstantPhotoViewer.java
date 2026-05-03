// PORT-MODE: Exact Component Port
// WORKFLOW-MAP: docs/telegram-port/browser.md
// UPSTREAM-SCOPE: org/telegram/ui/PhotoViewer.java; org/telegram/ui/PinchToZoomHelper.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// EXACT-SUBSYSTEMS: gesture, animation, measurement
// HOST-WIRING:
//   - Draws a single NativeDo bitmap/URL overlay instead of Telegram ImageReceiver, PhotoViewerProvider,
//     pageBlocksAdapter, FileLoader, and ActionBar chrome.
// ADAPTED-BACKEND:
//   - Loads the selected image URL directly for Browser Instant View.
// OMITTED:
//   - Multi-image paging, video/document media, spoilers, Telegram action bar, grouped photos strip,
//     edit/crop modes, PiP, and account/file-loader integration.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/PhotoViewer.java and org/telegram/ui/PinchToZoomHelper.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram
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
package io.github.baba983.nativedo.ui.browser;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.Scroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.baba983.nativedo.core.AndroidUtilities;
import io.github.baba983.nativedo.ui.components.CubicBezierInterpolator;

final class BrowserInstantPhotoViewer extends FrameLayout {

    private static final ExecutorService LOAD_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private static final float MIN_SCALE = 1.0f;
    private static final float MAX_SCALE = 3.0f;
    private static final float PINCH_MIN_SCALE = 0.85f;
    private static final float PINCH_MAX_SCALE = 3.25f;
    private static final long OPEN_DURATION = 250L;
    private static final long CLOSE_DURATION = 250L;
    private static final long MOVE_DURATION = 250L;
    private static final float ZOOM_SCALE = 0.1f;

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint imagePaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Matrix drawMatrix = new Matrix();
    private final RectF baseRect = new RectF();
    private final RectF currentRect = new RectF();
    private final RectF animationRect = new RectF();
    private final GestureDetector gestureDetector;
    private final DecelerateInterpolator interpolator = new DecelerateInterpolator(1.5f);
    private final Scroller scroller;
    private final float touchSlop;

    @Nullable private Bitmap bitmap;
    @Nullable private String pendingUrl;
    @Nullable private Runnable onDismiss;
    @Nullable private RectF sourceRect;

    private float openProgress = 1f;
    private boolean dismissing;

    private float scale = 1f;
    private float translationX;
    private float translationY;
    private float animateToScale = 1f;
    private float animateToX;
    private float animateToY;
    private float animationValue;
    private float minX;
    private float maxX;
    private float minY;
    private float maxY;

    private float moveStartX;
    private float moveStartY;
    private float dragY;
    private boolean moving;
    private boolean draggingDown;
    private boolean zooming;
    private boolean canDragDown;
    private boolean doubleTap;
    private boolean discardTap;

    private float pinchStartDistance;
    private float pinchStartScale = 1f;
    private float pinchCenterX;
    private float pinchCenterY;
    private float pinchStartX;
    private float pinchStartY;

    @Nullable private VelocityTracker velocityTracker;
    @Nullable private ValueAnimator openAnimator;
    @Nullable private ValueAnimator moveAnimator;

    BrowserInstantPhotoViewer(@NonNull Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(true);
        setFocusable(true);
        backgroundPaint.setColor(Color.BLACK);
        scroller = new Scroller(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                if (discardTap) {
                    return false;
                }
                if (scale > MIN_SCALE) {
                    animateTo(MIN_SCALE, 0, 0, true);
                } else {
                    dismiss();
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                if (scale == MIN_SCALE && translationY == 0 && translationX == 0) {
                    float targetX = (e.getX() - getWidth() / 2f) - ((e.getX() - getWidth() / 2f) - translationX) * (MAX_SCALE / scale);
                    float targetY = (e.getY() - getHeight() / 2f) - ((e.getY() - getHeight() / 2f) - translationY) * (MAX_SCALE / scale);
                    updateMinMax(MAX_SCALE);
                    targetX = clamp(targetX, minX, maxX);
                    targetY = clamp(targetY, minY, maxY);
                    animateTo(MAX_SCALE, targetX, targetY, true);
                } else {
                    animateTo(MIN_SCALE, 0, 0, true);
                }
                doubleTap = true;
                discardTap = true;
                return true;
            }

            @Override
            public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
                if (scale != MIN_SCALE) {
                    scroller.abortAnimation();
                    scroller.fling(
                            Math.round(translationX),
                            Math.round(translationY),
                            Math.round(velocityX),
                            Math.round(velocityY),
                            Math.round(minX),
                            Math.round(maxX),
                            Math.round(minY),
                            Math.round(maxY)
                    );
                    invalidate();
                }
                return false;
            }
        });
        gestureDetector.setIsLongpressEnabled(false);
    }

    void setOnDismissListener(@Nullable Runnable listener) {
        onDismiss = listener;
    }

    void show(@Nullable Bitmap initial, @Nullable String url, @Nullable RectF sourceRect) {
        bitmap = initial;
        pendingUrl = url;
        this.sourceRect = sourceRect == null ? null : new RectF(sourceRect);
        resetTransform();
        if (bitmap == null && url != null) {
            loadFromNetwork(url);
        }
        startOpenAnimation();
    }

    private void startOpenAnimation() {
        if (openAnimator != null) {
            openAnimator.cancel();
        }
        openProgress = 0f;
        openAnimator = ValueAnimator.ofFloat(0f, 1f);
        openAnimator.setDuration(OPEN_DURATION);
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
        cancelMoveAnimation();
        scroller.abortAnimation();
        if (openAnimator != null) {
            openAnimator.cancel();
        }
        openAnimator = ValueAnimator.ofFloat(openProgress, 0f);
        openAnimator.setDuration(CLOSE_DURATION);
        openAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        openAnimator.addUpdateListener(animation -> {
            openProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        openAnimator.addListener(new AnimatorListenerAdapter() {
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
        openAnimator.start();
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
                loaded = BrowserImageDecoder.decode(stream, connection.getContentType());
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
                    resetTransform();
                    invalidate();
                }
            });
        });
    }

    private void resetTransform() {
        scale = MIN_SCALE;
        translationX = 0;
        translationY = 0;
        animateToScale = MIN_SCALE;
        animateToX = 0;
        animateToY = 0;
        updateMinMax(scale);
    }

    private void updateBaseRect() {
        if (bitmap == null || getWidth() == 0 || getHeight() == 0) {
            baseRect.setEmpty();
            return;
        }
        float bitmapWidth = bitmap.getWidth();
        float bitmapHeight = bitmap.getHeight();
        if (bitmapWidth <= 0 || bitmapHeight <= 0) {
            baseRect.setEmpty();
            return;
        }
        float fit = Math.min(getWidth() / bitmapWidth, getHeight() / bitmapHeight);
        float width = bitmapWidth * fit;
        float height = bitmapHeight * fit;
        baseRect.set((getWidth() - width) / 2f, (getHeight() - height) / 2f, (getWidth() + width) / 2f, (getHeight() + height) / 2f);
    }

    private void updateCurrentRect() {
        updateBaseRect();
        float width = baseRect.width() * scale;
        float height = baseRect.height() * scale;
        float centerX = getWidth() / 2f + translationX;
        float centerY = getHeight() / 2f + translationY;
        currentRect.set(centerX - width / 2f, centerY - height / 2f, centerX + width / 2f, centerY + height / 2f);
    }

    private void updateMinMax(float targetScale) {
        updateBaseRect();
        float width = baseRect.width() * targetScale;
        float height = baseRect.height() * targetScale;
        if (width > getWidth()) {
            minX = -(width - getWidth()) / 2f;
            maxX = (width - getWidth()) / 2f;
        } else {
            minX = maxX = 0;
        }
        if (height > getHeight()) {
            minY = -(height - getHeight()) / 2f;
            maxY = (height - getHeight()) / 2f;
        } else {
            minY = maxY = 0;
        }
    }

    private void checkMinMax(boolean zoom) {
        updateMinMax(scale);
        animateTo(scale, clamp(translationX, minX, maxX), clamp(translationY, minY, maxY), zoom);
    }

    private void animateTo(float newScale, float newTx, float newTy, boolean zoom) {
        if (scale == newScale && translationX == newTx && translationY == newTy) {
            return;
        }
        cancelMoveAnimation();
        float startScale = scale;
        float startX = translationX;
        float startY = translationY;
        animateToScale = newScale;
        animateToX = newTx;
        animateToY = newTy;
        moveAnimator = ValueAnimator.ofFloat(0f, 1f);
        moveAnimator.setDuration(MOVE_DURATION);
        moveAnimator.setInterpolator(interpolator);
        moveAnimator.addUpdateListener(animation -> {
            animationValue = (float) animation.getAnimatedValue();
            scale = startScale + (animateToScale - startScale) * animationValue;
            translationX = startX + (animateToX - startX) * animationValue;
            translationY = startY + (animateToY - startY) * animationValue;
            updateMinMax(scale);
            invalidate();
        });
        moveAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                scale = animateToScale;
                translationX = animateToX;
                translationY = animateToY;
                moveAnimator = null;
                updateMinMax(scale);
                invalidate();
            }
        });
        moveAnimator.start();
    }

    private void cancelMoveAnimation() {
        if (moveAnimator != null) {
            moveAnimator.cancel();
            moveAnimator = null;
        }
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (dismissing) {
            return true;
        }
        if (event.getPointerCount() == 1) {
            gestureDetector.onTouchEvent(event);
            if (doubleTap) {
                doubleTap = false;
                moving = false;
                zooming = false;
                return true;
            }
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            discardTap = false;
            cancelMoveAnimation();
            scroller.abortAnimation();
            if (action == MotionEvent.ACTION_DOWN) {
                moveStartX = event.getX();
                dragY = moveStartY = event.getY();
                draggingDown = false;
                canDragDown = true;
                moving = false;
                zooming = false;
                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain();
                } else {
                    velocityTracker.clear();
                }
                velocityTracker.addMovement(event);
            } else if (event.getPointerCount() == 2) {
                pinchStartDistance = distance(event, 0, 1);
                pinchStartScale = scale;
                pinchCenterX = (event.getX(0) + event.getX(1)) / 2f;
                pinchCenterY = (event.getY(0) + event.getY(1)) / 2f;
                pinchStartX = translationX;
                pinchStartY = translationY;
                zooming = true;
                moving = false;
                draggingDown = false;
                canDragDown = false;
                discardTap = true;
                if (velocityTracker != null) {
                    velocityTracker.clear();
                }
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (zooming && event.getPointerCount() >= 2 && pinchStartDistance > 0) {
                discardTap = true;
                scale = clamp(distance(event, 0, 1) / pinchStartDistance * pinchStartScale, PINCH_MIN_SCALE, PINCH_MAX_SCALE);
                translationX = (pinchCenterX - getWidth() / 2f) - ((pinchCenterX - getWidth() / 2f) - pinchStartX) * (scale / pinchStartScale);
                translationY = (pinchCenterY - getHeight() / 2f) - ((pinchCenterY - getHeight() / 2f) - pinchStartY) * (scale / pinchStartScale);
                updateMinMax(scale);
                invalidate();
            } else if (event.getPointerCount() == 1) {
                if (velocityTracker != null) {
                    velocityTracker.addMovement(event);
                }
                float dx = Math.abs(event.getX() - moveStartX);
                float dy = Math.abs(event.getY() - dragY);
                if (dx > touchSlop || dy > touchSlop) {
                    discardTap = true;
                }
                if (canDragDown && !draggingDown && scale == MIN_SCALE && dy >= AndroidUtilities.dp(30) && dy / 2f > dx) {
                    draggingDown = true;
                    moving = false;
                    dragY = event.getY();
                    return true;
                } else if (draggingDown) {
                    translationY = event.getY() - dragY;
                    invalidate();
                } else {
                    float moveDx = moveStartX - event.getX();
                    float moveDy = moveStartY - event.getY();
                    if (scale != MIN_SCALE || moving) {
                        if (!moving) {
                            moveDx = 0;
                            moveDy = 0;
                            moving = true;
                            canDragDown = false;
                        }
                        moveStartX = event.getX();
                        moveStartY = event.getY();
                        updateMinMax(scale);
                        if (translationX < minX || translationX > maxX) {
                            moveDx /= 3f;
                        }
                        if (maxY == 0 && minY == 0) {
                            if (translationY - moveDy < minY) {
                                translationY = minY;
                                moveDy = 0;
                            } else if (translationY - moveDy > maxY) {
                                translationY = maxY;
                                moveDy = 0;
                            }
                        } else if (translationY < minY || translationY > maxY) {
                            moveDy /= 3f;
                        }
                        translationX -= moveDx;
                        translationY -= moveDy;
                        invalidate();
                    }
                }
            }
        } else if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            if (zooming) {
                if (scale < MIN_SCALE) {
                    updateMinMax(MIN_SCALE);
                    animateTo(MIN_SCALE, 0, 0, true);
                } else if (scale > MAX_SCALE) {
                    float targetX = (pinchCenterX - getWidth() / 2f) - ((pinchCenterX - getWidth() / 2f) - pinchStartX) * (MAX_SCALE / pinchStartScale);
                    float targetY = (pinchCenterY - getHeight() / 2f) - ((pinchCenterY - getHeight() / 2f) - pinchStartY) * (MAX_SCALE / pinchStartScale);
                    updateMinMax(MAX_SCALE);
                    animateTo(MAX_SCALE, clamp(targetX, minX, maxX), clamp(targetY, minY, maxY), true);
                } else {
                    checkMinMax(true);
                }
                zooming = false;
                moving = false;
            } else if (draggingDown) {
                if (Math.abs(dragY - event.getY()) > getHeight() / 6f) {
                    dismiss();
                } else {
                    animateTo(MIN_SCALE, 0, 0, false);
                }
                draggingDown = false;
            } else if (moving) {
                updateMinMax(scale);
                if (velocityTracker != null && scale != MIN_SCALE) {
                    velocityTracker.computeCurrentVelocity(1000);
                    scroller.fling(
                            Math.round(translationX),
                            Math.round(translationY),
                            Math.round(velocityTracker.getXVelocity()),
                            Math.round(velocityTracker.getYVelocity()),
                            Math.round(minX),
                            Math.round(maxX),
                            Math.round(minY),
                            Math.round(maxY)
                    );
                }
                checkMinMax(false);
                moving = false;
                canDragDown = true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                recycleVelocityTracker();
            }
        }
        return true;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (!scroller.isFinished() && scroller.computeScrollOffset()) {
            translationX = scroller.getCurrX();
            translationY = scroller.getCurrY();
            invalidate();
        }

        float dragProgress = scale == MIN_SCALE ? Math.min(Math.abs(translationY), getWidth() / 4f) / Math.max(1f, getWidth() / 4f) : 0f;
        int alpha = (int) Math.max(127, 255 * (1f - dragProgress));
        backgroundPaint.setAlpha((int) (alpha * openProgress));
        canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);

        if (bitmap == null) {
            return;
        }

        updateCurrentRect();
        if (openProgress < 1f && sourceRect != null) {
            lerpRect(sourceRect, currentRect, openProgress, animationRect);
            imagePaint.setAlpha((int) (255 * openProgress));
            canvas.drawBitmap(bitmap, null, animationRect, imagePaint);
            imagePaint.setAlpha(255);
            return;
        }

        canvas.save();
        if (openProgress < 1f) {
            float openingScale = 0.92f + 0.08f * openProgress;
            canvas.scale(openingScale, openingScale, getWidth() / 2f, getHeight() / 2f);
            imagePaint.setAlpha((int) (255 * openProgress));
        }
        if (draggingDown && translationY != 0) {
            float dismissScale = 1f + (1f - clamp(Math.abs(translationY) / Math.max(1f, getHeight() / 2f), 0f, 1f)) * ZOOM_SCALE;
            canvas.scale(dismissScale, dismissScale, getWidth() / 2f, getHeight() / 2f);
        }
        float bitmapScale = baseRect.width() / bitmap.getWidth() * scale;
        drawMatrix.reset();
        drawMatrix.postTranslate(-bitmap.getWidth() / 2f, -bitmap.getHeight() / 2f);
        drawMatrix.postScale(bitmapScale, bitmapScale);
        drawMatrix.postTranslate(getWidth() / 2f + translationX, getHeight() / 2f + translationY);
        canvas.drawBitmap(bitmap, drawMatrix, imagePaint);
        if (openProgress < 1f) {
            imagePaint.setAlpha(255);
        }
        canvas.restore();
    }

    @Override
    protected void onDetachedFromWindow() {
        recycleVelocityTracker();
        super.onDetachedFromWindow();
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

    private void recycleVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    private static float distance(@NonNull MotionEvent event, int first, int second) {
        return (float) Math.hypot(event.getX(second) - event.getX(first), event.getY(second) - event.getY(first));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void lerpRect(@NonNull RectF from, @NonNull RectF to, float progress, @NonNull RectF out) {
        out.set(
                from.left + (to.left - from.left) * progress,
                from.top + (to.top - from.top) * progress,
                from.right + (to.right - from.right) * progress,
                from.bottom + (to.bottom - from.bottom) * progress
        );
    }
}
