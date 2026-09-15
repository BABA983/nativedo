package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.Components.CubicBezierInterpolator;

public class PinchToZoomHelper {

    private final ViewGroup parentView;
    private final ViewGroup fragmentView;
    private final boolean isSimple;

    private ZoomOverlayView overlayView;
    private View child;
    private ImageReceiver childImage;
    private View childTextureViewContainer;

    private ImageReceiver fullImage = new ImageReceiver();
    private boolean inOverlayMode;

    float parentOffsetX;
    float parentOffsetY;

    float fragmentOffsetX;
    float fragmentOffsetY;

    float pinchCenterX;
    float pinchCenterY;

    private float imageX;
    private float imageY;
    private float imageHeight;
    private float imageWidth;

    private float fullImageHeight;
    private float fullImageWidth;

    private float finishProgress;
    private float progressToFullView;
    ValueAnimator finishTransition;
    private MessageObject messageObject;

    Callback callback;
    ClipBoundsListener clipBoundsListener;

    float pinchStartCenterX;
    float pinchStartCenterY;
    float pinchStartDistance;
    float pinchTranslationX;
    float pinchTranslationY;
    boolean isInPinchToZoomTouchMode;

    private int pointerId1, pointerId2;

    float pinchScale;

    private float enterProgress;
    private float[] clipTopBottom = new float[2];


    public PinchToZoomHelper(ViewGroup parentView, ViewGroup fragmentView) {
        this.parentView = parentView;
        this.fragmentView = fragmentView;
        this.isSimple = false;
    }

    public PinchToZoomHelper() {
        this.parentView = null;
        this.fragmentView = null;
        this.isSimple = true;
    }

    public void startZoom(View child, ImageReceiver image, View textureViewContainer, View textureView, MessageObject messageObject, int spoilerEffect2AttachIndex) {
        this.child = child;
        this.messageObject = messageObject;

        if (overlayView == null && !isSimple) {
            overlayView = new ZoomOverlayView(parentView.getContext());
            overlayView.setFocusable(false);
            overlayView.setFocusableInTouchMode(false);
            overlayView.setEnabled(false);
        }

        if (fullImage == null) {
            fullImage = new ImageReceiver();
            fullImage.setCrossfadeAlpha((byte) 2);
            fullImage.setCrossfadeWithOldImage(false);
            fullImage.onAttachedToWindow();
        }

        inOverlayMode = true;
        finishProgress = 1f;
        progressToFullView = 0f;

        if (!isSimple) {
            parentView.addView(overlayView);

            fullImage.setImageBitmap(image.getBitmap());
            fullImage.setCrossfadeAlpha((byte) 2);
            updateViewsLocation();

            imageX = image.getImageX();
            imageY = image.getImageY() + child.getPaddingTop();
            imageHeight = image.getImageHeight();
            imageWidth = image.getImageWidth();
            fullImageHeight = image.getBitmapHeight();
            fullImageWidth = image.getBitmapWidth();

            if (fullImageHeight / fullImageWidth != imageHeight / imageWidth) {
                if (fullImageHeight / fullImageWidth < imageHeight / imageWidth) {
                    fullImageWidth = fullImageWidth / fullImageHeight * imageHeight;
                    fullImageHeight = imageHeight;
                } else {
                    fullImageHeight = fullImageHeight / fullImageWidth * imageWidth;
                    fullImageWidth = imageWidth;
                }
            } else {
                fullImageHeight = imageHeight;
                fullImageWidth = imageWidth;
            }


            this.childImage = new ImageReceiver();
            this.childTextureViewContainer = textureViewContainer;
            this.childImage.onAttachedToWindow();
            Drawable drawable = image.getDrawable();
            this.childImage.setImageBitmap(drawable);

            this.childImage.setImageCoords(imageX, imageY, imageWidth, imageHeight);
            this.childImage.setAspectFit(image.isAspectFit());
            this.childImage.setRoundRadius(image.getRoundRadius(true));

            this.fullImage.setRoundRadius(image.getRoundRadius(true));
            this.fullImage.setAspectFit(image.isAspectFit());
        }

        if (callback != null) {
            callback.onZoomStarted(messageObject);
        }
        enterProgress = 0f;
    }

    private boolean updateViewsLocation() {
        float parentOffsetX = 0;
        float parentOffsetY = 0;
        View currentView = child;
        while (currentView != parentView) {
            if (currentView == null) {
                return false;
            }
            parentOffsetX += currentView.getLeft();
            parentOffsetY += currentView.getTop();
            if (currentView.getParent() instanceof View) {
                currentView = (View) currentView.getParent();
            } else {
                break;
            }
        }

        float fragmentOffsetX = 0;
        float fragmentOffsetY = 0;
        currentView = child;
        while (currentView != fragmentView) {
            if (currentView == null) {
                return false;
            }
            fragmentOffsetX += currentView.getLeft();
            fragmentOffsetY += currentView.getTop();
            currentView = (View) currentView.getParent();
        }

        this.fragmentOffsetX = fragmentOffsetX;
        this.fragmentOffsetY = fragmentOffsetY;

        this.parentOffsetX = parentOffsetX;
        this.parentOffsetY = parentOffsetY;
        return true;
    }

    public void finishZoom() {
        if (finishTransition != null || !inOverlayMode) {
            return;
        }
        if (!isSimple) {
            if (!updateViewsLocation()) {
                clear();
                return;
            }
        }
        finishTransition = ValueAnimator.ofFloat(1f, 0);
        finishTransition.addUpdateListener(valueAnimator -> {
            finishProgress = (float) valueAnimator.getAnimatedValue();
            invalidateViews();
        });
        finishTransition.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (finishTransition != null) {
                    finishTransition = null;
                    clear();
                }
            }
        });

        finishTransition.setDuration(220);
        finishTransition.setInterpolator(CubicBezierInterpolator.DEFAULT);
        finishTransition.start();
    }

    public void clear() {
        if (finishTransition != null) {
            ValueAnimator transition = finishTransition;
            finishTransition = null;
            transition.cancel();
        }
        isInPinchToZoomTouchMode = false;
        if (child != null && child.getParent() != null) {
            child.getParent().requestDisallowInterceptTouchEvent(false);
        }
        if (inOverlayMode) {
            if (callback != null) {
                callback.onZoomFinished(messageObject);
            }
            inOverlayMode = false;
        }
        if (overlayView != null && overlayView.getParent() != null) {
            parentView.removeView(overlayView);

        }
        if (child != null) {
            child.invalidate();
            child = null;
        }
        if (childImage != null) {
            this.childImage.onDetachedFromWindow();
            this.childImage.clearImage();
            this.childImage = null;
        }
        if (fullImage != null) {
            fullImage.onDetachedFromWindow();
            fullImage.clearImage();
            fullImage = null;
        }
        childTextureViewContainer = null;
        messageObject = null;
    }

    public boolean inOverlayMode() {
        return inOverlayMode;
    }

    public boolean isInOverlayMode() {
        return inOverlayMode;
    }

    public boolean isInOverlayModeFor(View child) {
        return inOverlayMode && child == this.child;
    }

    public boolean onTouchEvent(MotionEvent ev) {
        if (updateViewsLocation() && child != null) {
            ev.offsetLocation(-fragmentOffsetX, -fragmentOffsetY);
            return child.onTouchEvent(ev);
        }
        return false;
    }

    public ImageReceiver getPhotoImage() {
        return childImage;
    }

    protected boolean zoomEnabled(View child, ImageReceiver receiver) {
        if (isSimple) {
            return true;
        }
        return receiver.hasNotThumbOrOnlyStaticThumb();
    }


    private class ZoomOverlayView extends FrameLayout {

        public ZoomOverlayView(Context context) {
            super(context);

        }


        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (finishTransition == null && enterProgress != 1f) {
                enterProgress += 16f / 220;
                if (enterProgress > 1f) {
                    enterProgress = 1f;
                } else {
                    invalidateViews();
                }
            }

            float progress = finishProgress * CubicBezierInterpolator.DEFAULT.getInterpolation(enterProgress);
            float clipTop = 0;
            float clipBottom = getMeasuredHeight();
            if (progress != 1f && clipBoundsListener != null) {
                clipBoundsListener.getClipTopBottom(clipTopBottom);
                canvas.save();
                clipTop = clipTopBottom[0] * (1f - progress);
                clipBottom = clipTopBottom[1] * (1f - progress) + getMeasuredHeight() * progress;
                canvas.clipRect(0, clipTop, getMeasuredWidth(), clipBottom);
                drawImage(canvas);
                super.dispatchDraw(canvas);
                canvas.restore();
            } else {
                drawImage(canvas);
                super.dispatchDraw(canvas);
            }

            float parentOffsetX = PinchToZoomHelper.this.parentOffsetX - getLeft();
            float parentOffsetY = PinchToZoomHelper.this.parentOffsetY - getTop();

            drawOverlays(canvas, (1f - progress), parentOffsetX, parentOffsetY, clipTop, clipBottom);
        }

        private void drawImage(Canvas canvas) {
            if (!inOverlayMode || child == null || parentView == null) {
                return;
            }

            updateViewsLocation();

            float parentOffsetX = PinchToZoomHelper.this.parentOffsetX - getLeft();
            float parentOffsetY = PinchToZoomHelper.this.parentOffsetY - getTop();

            canvas.save();
            float s = pinchScale * finishProgress + 1f * 1f - finishProgress;
            canvas.scale(s, s, parentOffsetX + pinchCenterX, parentOffsetY + pinchCenterY);
            canvas.translate(parentOffsetX + pinchTranslationX * finishProgress, parentOffsetY + pinchTranslationY * finishProgress);
            if (fullImage != null && fullImage.hasNotThumb()) {
                if (progressToFullView != 1) {
                    progressToFullView += 16f / 150f;
                    if (progressToFullView > 1) {
                        progressToFullView = 1f;
                    } else {
                        invalidateViews();
                    }
                }
                fullImage.setAlpha(progressToFullView);
            }

            float x = imageX;
            float y = imageY;
            if (imageHeight != fullImageHeight || imageWidth != fullImageWidth) {
                float p;
                if (s < 1f) {
                    p = 0;
                } else if (s < 1.4f) {
                    p = (s - 1f) / 0.4f;
                } else  {
                    p = 1f;
                }
                float verticalPadding = (fullImageHeight - imageHeight) / 2f;
                float horizontalPadding = (fullImageWidth - imageWidth) / 2f;
                x = imageX - horizontalPadding * p;
                y = imageY - verticalPadding * p;
                if (childImage != null) {
                    childImage.setImageCoords(x, y, imageWidth + horizontalPadding * p * 2, imageHeight + verticalPadding * p * 2);
                }
            }

            if (childImage != null) {
                if (progressToFullView != 1f) {
                    if (childImage.getLottieAnimation() != null || childImage.getAnimation() != null || fullImage.getLottieAnimation() != null || fullImage.getAnimation() != null) {
                        invalidate();
                    }
                    childImage.draw(canvas);
                    fullImage.setImageCoords(childImage.getImageX(), childImage.getImageY(), childImage.getImageWidth(), childImage.getImageHeight());
                    fullImage.draw(canvas);
                } else {
                    fullImage.setImageCoords(childImage.getImageX(), childImage.getImageY(), childImage.getImageWidth(), childImage.getImageHeight());
                    fullImage.draw(canvas);
                    if (fullImage.getLottieAnimation() != null || fullImage.getAnimation() != null) {
                        invalidate();
                    }
                }
            }
            if (childImage != null
                    && childTextureViewContainer != null
                    && childTextureViewContainer.getMeasuredWidth() > 0
                    && childTextureViewContainer.getMeasuredHeight() > 0) {
                canvas.save();
                canvas.translate(childImage.getImageX(), childImage.getImageY());
                float scaleX = childImage.getImageWidth() / childTextureViewContainer.getMeasuredWidth();
                float scaleY = childImage.getImageHeight() / childTextureViewContainer.getMeasuredHeight();
                float maxScale = Math.max(scaleX, scaleY);
                if (childImage.isAspectFit()) {
                    canvas.scale(maxScale, maxScale, childTextureViewContainer.getMeasuredWidth() / 2f, 0);
                } else {
                    canvas.scale(maxScale, maxScale);
                }
                childTextureViewContainer.draw(canvas);
                canvas.restore();
            }

            canvas.restore();
        }
    }

    protected void drawOverlays(Canvas canvas, float alpha, float parentOffsetX, float parentOffsetY, float clipTop, float clipBottom) {

    }


    public void setClipBoundsListener(ClipBoundsListener clipBoundsListener) {
        this.clipBoundsListener = clipBoundsListener;
    }

    public interface Callback {
        default void onZoomStarted(MessageObject messageObject) {

        }

        default void onZoomFinished(MessageObject messageObject) {

        }
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public interface ClipBoundsListener {
        void getClipTopBottom(float[] topBottom);
    }

    public boolean checkPinchToZoom(MotionEvent ev, View child, ImageReceiver image, View textureViewContainer, View textureView, MessageObject messageObject) {
        return checkPinchToZoom(ev, child, image, textureViewContainer, textureView, messageObject, 0);
    }

    public boolean checkPinchToZoom(MotionEvent ev, View child, ImageReceiver image, View textureViewContainer, View textureView, MessageObject messageObject, int spoilerEffect2Index) {
        if (!zoomEnabled(child, image)) {
            return false;
        }
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN || ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            if (!isInPinchToZoomTouchMode && ev.getPointerCount() == 2) {
                pinchStartDistance = (float) Math.hypot(ev.getX(1) - ev.getX(0), ev.getY(1) - ev.getY(0));
                pinchStartCenterX = pinchCenterX = (ev.getX(0) + ev.getX(1)) / 2.0f;
                pinchStartCenterY = pinchCenterY = (ev.getY(0) + ev.getY(1)) / 2.0f;
                pinchScale = 1f;

                pointerId1 = ev.getPointerId(0);
                pointerId2 = ev.getPointerId(1);
                isInPinchToZoomTouchMode = true;
            }
        } else if (ev.getActionMasked() == MotionEvent.ACTION_MOVE && isInPinchToZoomTouchMode) {
            int index1 = -1;
            int index2 = -1;
            for (int i = 0; i < ev.getPointerCount(); i++) {
                if (pointerId1 == ev.getPointerId(i)) {
                    index1 = i;
                }
                if (pointerId2 == ev.getPointerId(i)) {
                    index2 = i;
                }
            }
            if (index1 == -1 || index2 == -1) {
                isInPinchToZoomTouchMode = false;
                child.getParent().requestDisallowInterceptTouchEvent(false);
                finishZoom();
                return false;
            }
            pinchScale = (float) Math.hypot(ev.getX(index2) - ev.getX(index1), ev.getY(index2) - ev.getY(index1)) / pinchStartDistance;
            if (pinchScale > 1.005f && !isInOverlayMode()) {
                pinchStartDistance = (float) Math.hypot(ev.getX(index2) - ev.getX(index1), ev.getY(index2) - ev.getY(index1));
                pinchStartCenterX = pinchCenterX = (ev.getX(index1) + ev.getX(index2)) / 2.0f;
                pinchStartCenterY = pinchCenterY = (ev.getY(index1) + ev.getY(index2)) / 2.0f;
                pinchScale = 1f;
                pinchTranslationX = 0f;
                pinchTranslationY = 0f;
                child.getParent().requestDisallowInterceptTouchEvent(true);
                startZoom(child, image, textureViewContainer, textureView, messageObject, spoilerEffect2Index);
            }

            float newPinchCenterX = (ev.getX(index1) + ev.getX(index2)) / 2.0f;
            float newPinchCenterY = (ev.getY(index1) + ev.getY(index2)) / 2.0f;

            float moveDx = pinchStartCenterX - newPinchCenterX;
            float moveDy = pinchStartCenterY - newPinchCenterY;
            pinchTranslationX = -moveDx / pinchScale;
            pinchTranslationY = -moveDy / pinchScale;
            invalidateViews();
        } else if ((ev.getActionMasked() == MotionEvent.ACTION_UP || (ev.getActionMasked() == MotionEvent.ACTION_POINTER_UP && checkPointerIds(ev)) || ev.getActionMasked() == MotionEvent.ACTION_CANCEL) && isInPinchToZoomTouchMode) {
            isInPinchToZoomTouchMode = false;
            if (child != null && child.getParent() != null) {
                child.getParent().requestDisallowInterceptTouchEvent(false);
            }
            finishZoom();
        }
        return isInOverlayModeFor(child);
    }

    private boolean checkPointerIds(MotionEvent ev) {
        if (ev.getPointerCount() < 2) {
            return false;
        }
        if (pointerId1 == ev.getPointerId(0) && pointerId2 == ev.getPointerId(1)) {
            return true;
        }
        if (pointerId1 == ev.getPointerId(1) && pointerId2 == ev.getPointerId(0)) {
            return true;
        }
        return false;
    }

    protected void invalidateViews() {
        if (isSimple && child != null) {
            child.invalidate();
        }
        if (overlayView != null) {
            overlayView.invalidate();
        }
    }

    public void applyTransform(Canvas canvas) {
        if (inOverlayMode) {
            canvas.save();
            float s = pinchScale * finishProgress + 1f * 1f - finishProgress;
            canvas.scale(s, s, parentOffsetX + pinchCenterX, parentOffsetY + pinchCenterY);
            canvas.translate(parentOffsetX + pinchTranslationX * finishProgress, parentOffsetY + pinchTranslationY * finishProgress);
        }
    }

    public View getChild() {
        return child;
    }
}
