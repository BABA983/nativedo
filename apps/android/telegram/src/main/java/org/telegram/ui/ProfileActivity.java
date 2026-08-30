/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.util.Property;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ProfileGalleryBlurView;
import org.telegram.ui.Components.ProfileGalleryView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import java.util.Arrays;

public class ProfileActivity {

    public static class AvatarImageView extends BackupImageView implements SizeNotifierFrameLayout.IViewWithInvalidateCallback {

        private boolean isPulledDown;
        private float blurSizeFraction;
        private float avatarScale;
        public boolean isMetaballWorking = false;
        public int roundRadiusCollapse = 0;
        private int roundRadiusExpand = 0;
        private int actionsSize = 0;
        private boolean blurEnabled;

        public final Path clipPath = new Path();
        private final RectF rect = new RectF();
        private final Paint placeholderPaint;
        public boolean drawAvatar = true;
        public float bounceScale = 1f;

        private float crossfadeProgress;
        private ImageReceiver animateFromImageReceiver;

        public ImageReceiver foregroundImageReceiver;
        public float foregroundAlpha;
        private ImageReceiver.BitmapHolder drawableHolder;
        public boolean drawForeground = true;
        float progressToExpand;

        ProfileGalleryView avatarsViewPager;
        public boolean hasStories;
        private float progressToInsets = 1f;

        private Runnable invalidateCallback;

        public void setAvatarsViewPager(ProfileGalleryView avatarsViewPager) {
            this.avatarsViewPager = avatarsViewPager;
        }

        public void createBlurEffect(int actionsSize) {
            this.actionsSize = actionsSize;
            this.blurEnabled = true; // actionsSize > 0;
        }

        public AvatarImageView(@NonNull Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
            foregroundImageReceiver = new ImageReceiver(this);
            placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            placeholderPaint.setColor(Color.BLACK);
        }

        public void setAnimateFromImageReceiver(ImageReceiver imageReceiver) {
            this.animateFromImageReceiver = imageReceiver;
        }

        public void setCrossfadeProgress(float crossfadeProgress) {
            this.crossfadeProgress = crossfadeProgress;
            invalidate();
        }

        public static Property<AvatarImageView, Float> CROSSFADE_PROGRESS = new AnimationProperties.FloatProperty<AvatarImageView>("crossfadeProgress") {
            @Override
            public void setValue(AvatarImageView object, float value) {
                object.setCrossfadeProgress(value);
            }

            @Override
            public Float get(AvatarImageView object) {
                return object.crossfadeProgress;
            }
        };

        public void setForegroundImage(ImageLocation imageLocation, String imageFilter, Drawable thumb) {
            foregroundImageReceiver.setImage(imageLocation, imageFilter, thumb, 0, null, null, 0);
            if (drawableHolder != null) {
                drawableHolder.release();
                drawableHolder = null;
            }
        }

        public void setForegroundImageDrawable(ImageReceiver.BitmapHolder holder) {
            if (holder != null) {
                if (holder.drawable != null) {
                    foregroundImageReceiver.setImageBitmap(holder.drawable);
                } else if (holder.bitmap != null) {
                    foregroundImageReceiver.setImageBitmap(holder.bitmap);
                }
            }
            if (drawableHolder != null) {
                drawableHolder.release();
                drawableHolder = null;
            }
            drawableHolder = holder;
        }

        public float getForegroundAlpha() {
            return foregroundAlpha;
        }

        public void setForegroundAlpha(float value) {
            foregroundAlpha = value;
            invalidate();
        }

        public void clearForeground() {
            foregroundImageReceiver.clearImage();
            if (drawableHolder != null) {
                drawableHolder.release();
                drawableHolder = null;
            }
            foregroundAlpha = 0f;
            invalidate();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            foregroundImageReceiver.onDetachedFromWindow();
            if (drawableHolder != null) {
                drawableHolder.release();
                drawableHolder = null;
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            foregroundImageReceiver.onAttachedToWindow();
        }

        public int getRoundRadiusForExpand() {
            if (!blurEnabled) {
                return getRoundRadius()[0];
            } else {
                return roundRadiusExpand;
            }
        }

        public void setRoundRadiusForExpand(int value) {
            if (!blurEnabled) {
                setRoundRadius(value);
            } else {
                roundRadiusExpand = value;
                setRoundRadius(value);
            }
        }

        public void setBlurRadiusProgressForExpand(float value, float avatarScale, boolean isPulledDown) {
            blurSizeFraction = value;
            this.avatarScale = avatarScale;
            this.isPulledDown = isPulledDown;
        }

        public void setRoundRadiusCollapse(int roundRadiusCollapse) {
            this.isMetaballWorking = true;
            int old = this.roundRadiusCollapse;
            this.roundRadiusCollapse = roundRadiusCollapse;
            if (old != this.roundRadiusCollapse) {
                super.invalidate();
            }
        }

        @Override
        public void setRoundRadius(int value) {
            super.setRoundRadius(value);
            foregroundImageReceiver.setRoundRadius(value);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int thisWidth = getMeasuredWidth();
            int thisHeight = getMeasuredHeight();
            boolean shouldDrawBlur = avatarsViewPager != null &&
                    avatarsViewPager.getVisibility() == View.VISIBLE &&
                    blurEnabled && blurSizeFraction > 0f;

            ProfileGalleryBlurView blurView = null;
            if (shouldDrawBlur) {
                blurView = avatarsViewPager.getBlurDrawer();
                shouldDrawBlur = blurView != null;
            }

            float inset = hasStories ? (int) AndroidUtilities.dpf2(3.5f) : 0;
            inset *= (1f - progressToExpand);
            inset *= progressToInsets * (1f - foregroundAlpha);

            ImageReceiver imageReceiver = animatedEmojiDrawable != null ? animatedEmojiDrawable.getImageReceiver() : this.imageReceiver;

            int r = /*isMetaballWorking && !hasStories ? roundRadiusCollapse : */roundRadiusExpand;
            if (r > 0) {
                clipPath.rewind();
                AndroidUtilities.rectTmp.set(inset, inset, thisWidth - inset, thisHeight - inset);
                clipPath.addRoundRect(AndroidUtilities.rectTmp, r, r, Path.Direction.CW);
                canvas.clipPath(clipPath);
            }

            canvas.save();
            canvas.scale(bounceScale, bounceScale, thisWidth / 2f, thisHeight / 2f);

            if (shouldDrawBlur) {
                if (isPulledDown) {
                    thisHeight = Math.min(thisHeight, thisWidth);
                } else {
                    thisHeight = (int) (thisHeight - AndroidUtilities.lerp(0f, actionsSize / avatarScale, blurSizeFraction));
                }
            }

            float alpha = 1.0f;
            if (animateFromImageReceiver != null) {
                alpha *= 1.0f - crossfadeProgress;
                if (crossfadeProgress > 0.0f) {
                    final float fromAlpha = crossfadeProgress;
                    final float wasImageX = animateFromImageReceiver.getImageX();
                    final float wasImageY = animateFromImageReceiver.getImageY();
                    final float wasImageW = animateFromImageReceiver.getImageWidth();
                    final float wasImageH = animateFromImageReceiver.getImageHeight();
                    final float wasAlpha = animateFromImageReceiver.getAlpha();
                    animateFromImageReceiver.setImageCoords(inset, inset, thisWidth - inset * 2f, thisHeight - inset * 2f);
                    animateFromImageReceiver.setAlpha(fromAlpha);
                    animateFromImageReceiver.draw(canvas);
                    animateFromImageReceiver.setImageCoords(wasImageX, wasImageY, wasImageW, wasImageH);
                    animateFromImageReceiver.setAlpha(wasAlpha);
                }
            }
            if (imageReceiver != null && alpha > 0 && (foregroundAlpha < 1f || !drawForeground)) {
                imageReceiver.setImageCoords(inset, inset, thisWidth - inset * 2f, thisHeight - inset * 2f);
                final float wasAlpha = imageReceiver.getAlpha();
                imageReceiver.setAlpha(wasAlpha * alpha);
                if (drawAvatar) {
                    int wasRadius = imageReceiver.getRoundRadius()[0];
                    if (shouldDrawBlur) {
                        imageReceiver.setRoundRadius(0);
                    }
                    imageReceiver.draw(canvas);
                    if (shouldDrawBlur) {
                        imageReceiver.setRoundRadius(wasRadius);
                    }
                }
                imageReceiver.setAlpha(wasAlpha);
            }
            if (foregroundAlpha > 0f && drawForeground && alpha > 0) {
                if (foregroundImageReceiver.getDrawable() != null) {
                    foregroundImageReceiver.setImageCoords(inset, inset, thisWidth - inset * 2f, thisHeight - inset * 2f);
                    foregroundImageReceiver.setAlpha(alpha * foregroundAlpha);
                    foregroundImageReceiver.draw(canvas);
                } else {
                    rect.set(0f, 0f, thisWidth, thisHeight);
                    placeholderPaint.setAlpha((int) (alpha * foregroundAlpha * 255f));
                    final int radius = foregroundImageReceiver.getRoundRadius()[0];
                    canvas.drawRoundRect(rect, radius, radius, placeholderPaint);
                }
            }

            if (shouldDrawBlur) {
                canvas.translate(inset, inset + thisHeight);
                float fraction = !isPulledDown && !blurView.isUsingRenderNode() && avatarsViewPager.getRealPosition() != 0 ? 1f : (1f - blurSizeFraction);
                blurView.draw(canvas, this, thisWidth - inset * 2f, thisHeight - inset * 2f, true, fraction, alpha);
            }

            canvas.restore();
        }

        public void setProgressToStoriesInsets(float progressToInsets) {
            if (progressToInsets == this.progressToInsets) {
                return;
            }
            this.progressToInsets = progressToInsets;
            //if (hasStories) {
            invalidate();
            //}
        }

        public void drawForeground(boolean drawForeground) {
            this.drawForeground = drawForeground;
        }

        public void setHasStories(boolean hasStories) {
            if (this.hasStories == hasStories) {
                return;
            }
            this.hasStories = hasStories;
            invalidate();
        }

        public void setProgressToExpand(float animatedFracture) {
            if (progressToExpand == animatedFracture) {
                return;
            }
            progressToExpand = animatedFracture;
            invalidate();
        }

        @Override
        public void listenInvalidate(Runnable callback) {
            invalidateCallback = callback;
        }

        @Override
        public void invalidate() {
            super.invalidate();
            if (avatarsViewPager != null) {
                avatarsViewPager.invalidate();
            }
            if (invalidateCallback != null) {
                invalidateCallback.run();
            }
        }

        @Override
        public void invalidate(Rect dirty) {
            super.invalidate(dirty);
            if (invalidateCallback != null) {
                invalidateCallback.run();
            }
        }

        @Override
        public void invalidate(int l, int t, int r, int b) {
            super.invalidate(l, t, r, b);
            if (invalidateCallback != null) {
                invalidateCallback.run();
            }
        }
    }

    public static class TopView extends FrameLayout {

        private int currentColor;
        private Paint paint = new Paint();

        public ActionBar actionBar;
        public float extraHeight;
        public float mediaHeaderAnimationProgress;

        public TopView(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        public void setBackgroundColor(int color) {
            if (color != currentColor) {
                currentColor = color;
                paint.setColor(color);
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final int height = ActionBar.getCurrentActionBarHeight() + (actionBar != null && actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0);
            final float v = height + extraHeight;

            int y1 = (int) (v * (1.0f - mediaHeaderAnimationProgress));

            if (y1 != 0) {
                paint.setColor(currentColor);
                canvas.drawRect(0, 0, getMeasuredWidth(), y1, paint);
            }
            // Upstream fills the strip below y1 with windowBackgroundWhite as the
            // media header opens (blur-backed there; NativeDo has no blur
            // contentView, so paint it directly).
            if (y1 != v) {
                paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                canvas.drawRect(0, y1, getMeasuredWidth(), v, paint);
            }
        }
    }

    public static class OverlaysView extends View implements ProfileGalleryView.Callback {

        public ActionBar actionBar;
        public ProfileGalleryView avatarsViewPager;
        public int overlayCountVisible;
        public int actionsExtraHeight;

        private final Rect topOverlayRect = new Rect();
        private final Rect bottomOverlayRect = new Rect();
        private final RectF rect = new RectF();

        private final GradientDrawable topOverlayGradient;
        private final GradientDrawable bottomOverlayGradient;
        private final ValueAnimator animator;
        private final float[] animatorValues = new float[]{0f, 1f};
        private final Paint backgroundPaint;
        private final Paint barPaint;
        private final Paint selectedBarPaint;

        private final GradientDrawable[] pressedOverlayGradient = new GradientDrawable[2];
        private final boolean[] pressedOverlayVisible = new boolean[2];
        private final float[] pressedOverlayAlpha = new float[2];

        private boolean isOverlaysVisible;
        private float currentAnimationValue;
        private float alpha = 0f;
        private float[] alphas = null;
        private long lastTime;
        private float previousSelectedProgress;
        private int previousSelectedPotision = -1;
        private float currentProgress;
        private int selectedPosition;

        private float currentLoadingAnimationProgress;
        private int currentLoadingAnimationDirection = 1;

        public OverlaysView(Context context) {
            super(context);
            setVisibility(GONE);

            barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            barPaint.setColor(0x55ffffff);
            selectedBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            selectedBarPaint.setColor(0xffffffff);

            topOverlayGradient = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x42000000, 0});
            topOverlayGradient.setShape(GradientDrawable.RECTANGLE);

            bottomOverlayGradient = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0x42000000, 0});
            bottomOverlayGradient.setShape(GradientDrawable.RECTANGLE);

            for (int i = 0; i < 2; i++) {
                final GradientDrawable.Orientation orientation = i == 0 ? GradientDrawable.Orientation.LEFT_RIGHT : GradientDrawable.Orientation.RIGHT_LEFT;
                pressedOverlayGradient[i] = new GradientDrawable(orientation, new int[]{0x32000000, 0});
                pressedOverlayGradient[i].setShape(GradientDrawable.RECTANGLE);
            }

            backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            backgroundPaint.setColor(Color.BLACK);
            backgroundPaint.setAlpha(66);
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(250);
            animator.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
            animator.addUpdateListener(anim -> {
                float value = AndroidUtilities.lerp(animatorValues, currentAnimationValue = anim.getAnimatedFraction());
                setAlphaValue(value, true);
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!isOverlaysVisible) {
                        setVisibility(GONE);
                    }
                }

                @Override
                public void onAnimationStart(Animator animation) {
                    setVisibility(VISIBLE);
                }
            });
        }

        public void saveCurrentPageProgress() {
            previousSelectedProgress = currentProgress;
            previousSelectedPotision = selectedPosition;
            currentLoadingAnimationProgress = 0.0f;
            currentLoadingAnimationDirection = 1;
        }

        public void setAlphaValue(float value, boolean self) {
            int alpha = (int) (255 * value);
            topOverlayGradient.setAlpha(alpha);
            bottomOverlayGradient.setAlpha(alpha);
            backgroundPaint.setAlpha((int) (66 * value));
            barPaint.setAlpha((int) (0x55 * value));
            selectedBarPaint.setAlpha(alpha);
            this.alpha = value;
            if (!self) {
                currentAnimationValue = value;
            }
            invalidate();
        }

        public boolean isOverlaysVisible() {
            return isOverlaysVisible;
        }

        public void setOverlaysVisible() {
            isOverlaysVisible = true;
            setVisibility(VISIBLE);
        }

        public void setOverlaysVisible(boolean overlaysVisible, float durationFactor) {
            if (overlaysVisible != isOverlaysVisible) {
                isOverlaysVisible = overlaysVisible;
                animator.cancel();
                final float value = AndroidUtilities.lerp(animatorValues, currentAnimationValue);
                if (overlaysVisible) {
                    animator.setDuration((long) ((1f - value) * 250f / durationFactor));
                } else {
                    animator.setDuration((long) (value * 250f / durationFactor));
                }
                animatorValues[0] = value;
                animatorValues[1] = overlaysVisible ? 1f : 0f;
                animator.start();
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            final int actionBarHeight = (actionBar != null && actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight();
            final float k = 0.5f;
            topOverlayRect.set(0, 0, w, (int) (actionBarHeight * k));
            bottomOverlayRect.set(0, (int) (h - AndroidUtilities.dp(72f) * k), w, h);
            topOverlayGradient.setBounds(0, topOverlayRect.bottom, w, actionBarHeight + AndroidUtilities.dp(16f));
            bottomOverlayGradient.setBounds(0, h - actionsExtraHeight - AndroidUtilities.dp(72f) - AndroidUtilities.dp(24f), w, bottomOverlayRect.top);
            pressedOverlayGradient[0].setBounds(0, 0, w / 5, h);
            pressedOverlayGradient[1].setBounds(w - (w / 5), 0, w, h);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            for (int i = 0; i < 2; i++) {
                if (pressedOverlayAlpha[i] > 0f) {
                    pressedOverlayGradient[i].setAlpha((int) (pressedOverlayAlpha[i] * 255));
                    pressedOverlayGradient[i].draw(canvas);
                }
            }

            topOverlayGradient.draw(canvas);
            bottomOverlayGradient.draw(canvas);
            canvas.drawRect(topOverlayRect, backgroundPaint);
            canvas.drawRect(bottomOverlayRect, backgroundPaint);

            int count = avatarsViewPager.getRealCount();
            selectedPosition = avatarsViewPager.getRealPosition();

            if (alphas == null || alphas.length != count) {
                alphas = new float[count];
                Arrays.fill(alphas, 0.0f);
            }

            boolean invalidate = false;

            long newTime = SystemClock.elapsedRealtime();
            long dt = (newTime - lastTime);
            if (dt < 0 || dt > 20) {
                dt = 17;
            }
            lastTime = newTime;

            if (count > 1 && count <= 20) {
                if (overlayCountVisible == 0) {
                    alpha = 0.0f;
                    overlayCountVisible = 3;
                } else if (overlayCountVisible == 1) {
                    alpha = 0.0f;
                    overlayCountVisible = 2;
                }
                if (overlayCountVisible == 2) {
                    barPaint.setAlpha((int) (0x55 * alpha));
                    selectedBarPaint.setAlpha((int) (0xff * alpha));
                }
                int width = (getMeasuredWidth() - AndroidUtilities.dp(5 * 2) - AndroidUtilities.dp(2 * (count - 1))) / count;
                int y = AndroidUtilities.dp(4) + AndroidUtilities.statusBarHeight;
                for (int a = 0; a < count; a++) {
                    int x = AndroidUtilities.dp(5 + a * 2) + width * a;
                    float progress;
                    int baseAlpha = 0x55;
                    if (a == previousSelectedPotision && Math.abs(previousSelectedProgress - 1.0f) > 0.0001f) {
                        progress = previousSelectedProgress;
                        canvas.save();
                        canvas.clipRect(x + width * progress, y, x + width, y + AndroidUtilities.dp(2));
                        rect.set(x, y, x + width, y + AndroidUtilities.dp(2));
                        barPaint.setAlpha((int) (0x55 * alpha));
                        canvas.drawRoundRect(rect, AndroidUtilities.dp(1), AndroidUtilities.dp(1), barPaint);
                        baseAlpha = 0x50;
                        canvas.restore();
                        invalidate = true;
                    } else if (a == selectedPosition) {
                        if (avatarsViewPager.isCurrentItemVideo()) {
                            progress = currentProgress = avatarsViewPager.getCurrentItemProgress();
                            if (progress <= 0 && avatarsViewPager.isLoadingCurrentVideo() || currentLoadingAnimationProgress > 0.0f) {
                                currentLoadingAnimationProgress += currentLoadingAnimationDirection * dt / 500.0f;
                                if (currentLoadingAnimationProgress > 1.0f) {
                                    currentLoadingAnimationProgress = 1.0f;
                                    currentLoadingAnimationDirection *= -1;
                                } else if (currentLoadingAnimationProgress <= 0) {
                                    currentLoadingAnimationProgress = 0.0f;
                                    currentLoadingAnimationDirection *= -1;
                                }
                            }
                            rect.set(x, y, x + width, y + AndroidUtilities.dp(2));
                            barPaint.setAlpha((int) ((0x55 + 0x30 * currentLoadingAnimationProgress) * alpha));
                            canvas.drawRoundRect(rect, AndroidUtilities.dp(1), AndroidUtilities.dp(1), barPaint);
                            invalidate = true;
                            baseAlpha = 0x50;
                        } else {
                            progress = currentProgress = 1.0f;
                        }
                    } else {
                        progress = 1.0f;
                    }
                    rect.set(x, y, x + width * progress, y + AndroidUtilities.dp(2));

                    if (a != selectedPosition) {
                        if (overlayCountVisible == 3) {
                            barPaint.setAlpha((int) (AndroidUtilities.lerp(baseAlpha, 0xff, CubicBezierInterpolator.EASE_BOTH.getInterpolation(alphas[a])) * alpha));
                        }
                    } else {
                        alphas[a] = 0.75f;
                    }

                    canvas.drawRoundRect(rect, AndroidUtilities.dp(1), AndroidUtilities.dp(1), a == selectedPosition ? selectedBarPaint : barPaint);
                }

                if (overlayCountVisible == 2) {
                    if (alpha < 1.0f) {
                        alpha += dt / 180.0f;
                        if (alpha > 1.0f) {
                            alpha = 1.0f;
                        }
                        invalidate = true;
                    } else {
                        overlayCountVisible = 3;
                    }
                } else if (overlayCountVisible == 3) {
                    for (int i = 0; i < alphas.length; i++) {
                        if (i != selectedPosition && alphas[i] > 0.0f) {
                            alphas[i] -= dt / 500.0f;
                            if (alphas[i] <= 0.0f) {
                                alphas[i] = 0.0f;
                                if (i == previousSelectedPotision) {
                                    previousSelectedPotision = -1;
                                }
                            }
                            invalidate = true;
                        } else if (i == previousSelectedPotision) {
                            previousSelectedPotision = -1;
                        }
                    }
                }
            }

            for (int i = 0; i < 2; i++) {
                if (pressedOverlayVisible[i]) {
                    if (pressedOverlayAlpha[i] < 1f) {
                        pressedOverlayAlpha[i] += dt / 180.0f;
                        if (pressedOverlayAlpha[i] > 1f) {
                            pressedOverlayAlpha[i] = 1f;
                        }
                        invalidate = true;
                    }
                } else {
                    if (pressedOverlayAlpha[i] > 0f) {
                        pressedOverlayAlpha[i] -= dt / 180.0f;
                        if (pressedOverlayAlpha[i] < 0f) {
                            pressedOverlayAlpha[i] = 0f;
                        }
                        invalidate = true;
                    }
                }
            }

            if (invalidate) {
                postInvalidateOnAnimation();
            }
        }

        @Override
        public void onDown(boolean left) {
            pressedOverlayVisible[left ? 0 : 1] = true;
            postInvalidateOnAnimation();
        }

        @Override
        public void onRelease() {
            Arrays.fill(pressedOverlayVisible, false);
            postInvalidateOnAnimation();
        }

        @Override
        public void onPhotosLoaded() {
        }

        @Override
        public void onVideoSet() {
            invalidate();
        }
    }
}
