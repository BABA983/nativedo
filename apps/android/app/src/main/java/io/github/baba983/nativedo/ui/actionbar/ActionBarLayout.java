// UPSTREAM-LEVEL: L1
// UPSTREAM-PATH:  org/telegram/ui/ActionBar/ActionBarLayout.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// SUBSYSTEMS: navigation-stack=L1, gesture=L0, animation=L0, lifecycle-orchestration=L1
// HOST-WIRING:
//   - Telegram BaseFragment/Theme/LayoutHelper/AndroidUtilities imports rewritten to NativeDo.
//   - NativeDo MainActivity forwards Android predictive-back callbacks into this layout.
// ADAPTED:
//   - Keeps a single-stack container model for NativeDo fragments only.
//   - Uses direct Activity finish fallback instead of Telegram LaunchActivity/parentActivity orchestration.
// OMITTED:
//   - Drawer, sheets, previews, dialogs, story layers, multi-window branches, and Telegram delegate surfaces.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/ActionBar/ActionBarLayout.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBarLayout.java
 *
 * NativeDo milestone slice: keeps Telegram's single-stack container choreography,
 * NavigationParams-backed push/pop frame animation, edge swipe-back, and predictive-back
 * wiring; omits drawer, sheets, previews, dialogs, and multi-window branches.
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
package io.github.baba983.nativedo.ui.actionbar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.RoundedCorner;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

import io.github.baba983.nativedo.core.AndroidUtilities;
import io.github.baba983.nativedo.ui.components.CubicBezierInterpolator;
import io.github.baba983.nativedo.ui.components.LayoutHelper;

public class ActionBarLayout extends FrameLayout implements INavigationLayout {

    public class LayoutContainer extends FrameLayout {

        public LayoutContainer(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        public void setTranslationX(float translationX) {
            boolean invalidate = getTranslationX() != translationX;
            super.setTranslationX(translationX);
            if (invalidate) {
                ActionBarLayout.this.invalidate();
            }
        }

        @Override
        public void setAlpha(float alpha) {
            boolean invalidate = getAlpha() != alpha;
            super.setAlpha(alpha);
            if (invalidate) {
                ActionBarLayout.this.invalidate();
            }
        }
    }

    private LayoutContainer containerViewBack;
    private LayoutContainer containerView;
    private final ArrayList<BaseFragment> fragmentsStack = new ArrayList<>();
    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect rect = new Rect();
    private final RectF roundedClipRect = new RectF();
    private final Path clipPath = new Path();
    private final float[] radii = new float[8];

    public float innerTranslationX;
    private boolean maybeStartTracking;
    private boolean startedTracking;
    private int startedTrackingX;
    private int startedTrackingY;
    private int startedTrackingPointerId = -1;
    private VelocityTracker velocityTracker;
    private View layoutToIgnore;
    private boolean beginTrackingSent;

    private boolean animationInProgress;
    private boolean predictiveInput;
    private boolean predictiveBackInProgress;
    private boolean predictiveBackHasProgress;
    private boolean predictiveBackLeft = true;
    private float predictiveBackY;
    private AnimatorSet backAnimator;
    private Runnable animationRunnable;
    private float animationProgress;
    private long lastFrameTime;
    private BaseFragment newFragment;
    private BaseFragment oldFragment;
    private final DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator(1.5f);

    public ActionBarLayout(Context context) {
        super(context);
        AndroidUtilities.init(context);
        setClipChildren(false);
        setClipToPadding(false);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        containerViewBack = new LayoutContainer(context);
        containerViewBack.setVisibility(INVISIBLE);
        addView(containerViewBack, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.START));

        containerView = new LayoutContainer(context);
        addView(containerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.START));
    }

    @Override
    public boolean addFragmentToStack(BaseFragment fragment, int position) {
        if (fragment == null) {
            return false;
        }
        fragment.setParentLayout(this);
        if (position == -1 || position >= fragmentsStack.size()) {
            fragmentsStack.add(fragment);
        } else {
            fragmentsStack.add(position, fragment);
        }
        if (fragmentsStack.get(fragmentsStack.size() - 1) == fragment) {
            containerView.removeAllViews();
            attachView(fragment);
            fragment.onResume();
        }
        return true;
    }

    public boolean canHandleBack() {
        return fragmentsStack.size() > 1;
    }

    public boolean handleBackPressed() {
        if (animationInProgress) {
            return true;
        }
        if (fragmentsStack.size() <= 1) {
            return false;
        }
        BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
        if (!currentFragment.onBackPressed()) {
            return true;
        }
        closeLastFragment(true, false);
        return true;
    }

    @Override
    public boolean presentFragment(NavigationParams params) {
        BaseFragment fragment = params.fragment;
        boolean forceWithoutAnimation = params.noAnimation;
        boolean removeLast = params.removeLast;
        if (fragment == null || animationInProgress) {
            return false;
        }
        if (fragmentsStack.isEmpty()) {
            return addFragmentToStack(fragment);
        }
        BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
        currentFragment.onPause();
        fragment.setParentLayout(this);
        fragmentsStack.add(fragment);
        if (removeLast) {
            fragmentsStack.remove(currentFragment);
        }

        if (forceWithoutAnimation || getWidth() == 0) {
            containerView.removeAllViews();
            attachView(fragment);
            fragment.onResume();
            return true;
        }

        animationInProgress = true;
        containerViewBack.removeAllViews();
        attachViewTo(fragment, containerViewBack);
        containerViewBack.setVisibility(VISIBLE);

        LayoutContainer temp = containerView;
        containerView = containerViewBack;
        containerViewBack = temp;
        containerView.setAlpha(0.0f);
        containerView.setTranslationX(48.0f);
        bringChildToFront(containerView);

        oldFragment = currentFragment;
        newFragment = fragment;
        startLayoutAnimation(true, true);
        return true;
    }

    @Override
    public void closeLastFragment(boolean animated, boolean forceNoAnimation) {
        if (fragmentsStack.size() <= 1 || animationInProgress) {
            return;
        }
        if (forceNoAnimation || !animated || getWidth() == 0) {
            if (!prepareForMoving()) {
                return;
            }
            onSlideAnimationEnd(false);
            return;
        }
        startLayoutCloseAnimation();
    }

    @Override
    public List<BaseFragment> getFragmentStack() {
        return fragmentsStack;
    }

    public void onBackStarted(float touchX, float touchY) {
        if (animationInProgress) {
            if (backAnimator != null) {
                backAnimator.end();
                backAnimator = null;
            } else {
                return;
            }
            if (animationInProgress) {
                return;
            }
        }
        if (predictiveBackInProgress || predictiveInput || startedTracking || fragmentsStack.size() <= 1) {
            return;
        }
        BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
        if (!currentFragment.onBackPressed() || !currentFragment.canBeginSlide()) {
            return;
        }
        predictiveBackHasProgress = false;
        predictiveBackInProgress = true;
        predictiveInput = true;
        predictiveBackLeft = touchX < getWidth() / 2f;
        predictiveBackY = touchY;
        if (!prepareForMoving()) {
            predictiveBackInProgress = false;
            predictiveInput = false;
            return;
        }
        sendBeginSlideIfNeeded();
    }

    public void onBackProgress(float progress) {
        if (!predictiveInput) {
            return;
        }
        float dx = AndroidUtilities.dp(56) * progress;
        predictiveBackHasProgress = progress > 0;
        containerView.setTranslationX(dx);
        setInnerTranslationX(dx);
    }

    public void onBackCancelled() {
        if (!predictiveInput) {
            return;
        }
        predictiveInput = false;
        animateBackEndAnimation(true);
    }

    public void onBackInvoked() {
        if (!predictiveInput) {
            if (!handleBackPressed()) {
                ((Activity) getContext()).finish();
            }
            return;
        }
        predictiveInput = false;
        animateBackEndAnimation(false);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return animationInProgress || onTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!animationInProgress && !predictiveBackInProgress && fragmentsStack.size() > 1) {
            if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN) {
                BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
                if (ev.getX() > AndroidUtilities.dp(24) || !currentFragment.isSwipeBackEnabled(ev)) {
                    maybeStartTracking = false;
                    startedTracking = false;
                    startedTrackingPointerId = -1;
                    beginTrackingSent = false;
                    if (velocityTracker != null) {
                        velocityTracker.recycle();
                        velocityTracker = null;
                    }
                    containerView.setLayerType(LAYER_TYPE_NONE, null);
                    return false;
                }
                startedTrackingPointerId = ev.getPointerId(0);
                maybeStartTracking = true;
                startedTrackingX = (int) ev.getX();
                startedTrackingY = (int) ev.getY();
                if (velocityTracker != null) {
                    velocityTracker.clear();
                }
            } else if (ev != null && ev.getAction() == MotionEvent.ACTION_MOVE && ev.findPointerIndex(startedTrackingPointerId) >= 0) {
                int pointerIndex = ev.findPointerIndex(startedTrackingPointerId);
                float x = ev.getX(pointerIndex);
                float y = ev.getY(pointerIndex);
                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain();
                }
                int dx = Math.max(0, (int) (x - startedTrackingX));
                int dy = Math.abs((int) y - startedTrackingY);
                velocityTracker.addMovement(ev);
                if (maybeStartTracking && !startedTracking && dx >= AndroidUtilities.getPixelsInCM(0.4f, true) && Math.abs(dx) / 3 > dy) {
                    BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
                    if (currentFragment.canBeginSlide() && findScrollingChild(this, x, y) == null) {
                        startedTrackingX = (int) x;
                        prepareForMoving();
                    } else {
                        maybeStartTracking = false;
                    }
                } else if (startedTracking) {
                    sendBeginSlideIfNeeded();
                    containerView.setTranslationX(dx);
                    setInnerTranslationX(dx);
                }
            } else if (ev != null && ev.findPointerIndex(startedTrackingPointerId) >= 0 && (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_POINTER_UP)) {
                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain();
                }
                velocityTracker.addMovement(ev);
                velocityTracker.computeCurrentVelocity(1000);
                BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
                if (!startedTracking && ev.getX() <= AndroidUtilities.dp(24) && currentFragment.isSwipeBackEnabled(ev)) {
                    float velX = velocityTracker.getXVelocity();
                    float velY = velocityTracker.getYVelocity();
                    if (velX >= 3500 && velX > Math.abs(velY) && currentFragment.canBeginSlide()) {
                        startedTrackingX = (int) ev.getX();
                        prepareForMoving();
                        sendBeginSlideIfNeeded();
                    }
                }
                if (startedTracking) {
                    float x = containerView.getX();
                    float velX = velocityTracker.getXVelocity();
                    float velY = velocityTracker.getYVelocity();
                    boolean backAnimation = x < containerView.getMeasuredWidth() / 3.0f && (velX < 3500 || Math.abs(velX) < Math.abs(velY));
                    animateBackEndAnimation(backAnimation);
                } else {
                    maybeStartTracking = false;
                    startedTracking = false;
                    layoutToIgnore = null;
                    containerView.setLayerType(LAYER_TYPE_NONE, null);
                }
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
            } else if (ev == null) {
                maybeStartTracking = false;
                startedTracking = false;
                startedTrackingPointerId = -1;
                beginTrackingSent = false;
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
                layoutToIgnore = null;
                containerView.setLayerType(LAYER_TYPE_NONE, null);
            }
            return startedTracking;
        }
        return false;
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        onTouchEvent(null);
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        int width = getWidth();
        int translationX = (int) innerTranslationX;
        int clipLeft = 0;
        int clipRight = width;

        if (child == containerViewBack) {
            clipRight = translationX + AndroidUtilities.dp(1);
        } else if (child == containerView) {
            clipLeft = translationX;
        }

        int restoreCount = canvas.save();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && translationX != 0) {
            if (child == containerView) {
                WindowInsets insets = getRootWindowInsets();
                if (insets != null) {
                    roundedClipRect.set(translationX, 0, translationX + child.getWidth(), getHeight());
                    if (newBackTransitions()) {
                        float progress = clamp01(translationX / AndroidUtilities.dpf2(56));
                        float scale = lerp(1.0f, 0.90f, progress);
                        float dx = translationX > AndroidUtilities.dp(56) && !animationInProgress && predictiveBackInProgress ? translationX : clamp(translationX, 0, AndroidUtilities.dp(56));
                        canvas.translate(-dx, 0);
                        clipRight += dx;
                        float pivotX = predictiveBackLeft ? roundedClipRect.right - AndroidUtilities.dp(82) : roundedClipRect.left + AndroidUtilities.dp(82);
                        canvas.scale(scale, scale, pivotX, predictiveBackY);
                    }

                    RoundedCorner topLeft = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT);
                    RoundedCorner topRight = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT);
                    RoundedCorner bottomRight = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT);
                    RoundedCorner bottomLeft = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT);
                    radii[0] = radii[1] = topLeft == null ? 0 : topLeft.getRadius();
                    radii[2] = radii[3] = topRight == null ? 0 : topRight.getRadius();
                    radii[4] = radii[5] = bottomRight == null ? 0 : bottomRight.getRadius();
                    radii[6] = radii[7] = bottomLeft == null ? 0 : bottomLeft.getRadius();

                    clipPath.rewind();
                    clipPath.addRoundRect(roundedClipRect, radii, Path.Direction.CW);
                    canvas.clipPath(clipPath);
                }
            } else if (child == containerViewBack) {
                WindowInsets insets = getRootWindowInsets();
                if (insets != null) {
                    RoundedCorner topLeft = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT);
                    RoundedCorner bottomLeft = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT);
                    clipRight += Math.max(topLeft == null ? 0 : topLeft.getRadius(), bottomLeft == null ? 0 : bottomLeft.getRadius());
                    if (newBackTransitions()) {
                        clipRight = width;
                    }
                }
            }
        }

        int childRestoreCount = canvas.save();
        if (!animationInProgress) {
            canvas.clipRect(clipLeft, 0, clipRight, getHeight());
        }
        boolean result = super.drawChild(canvas, child, drawingTime);
        canvas.restoreToCount(childRestoreCount);

        if (translationX != 0 && child == containerViewBack) {
            int widthOffset = width - translationX;
            float opacity = clamp(widthOffset / (float) Math.max(1, width), 0f, 0.8f);
            scrimPaint.setColor(Color.argb((int) (120 * opacity), 0x00, 0x00, 0x00));
            canvas.drawRect(clipLeft, 0, clipRight, getHeight() * 1.5f, scrimPaint);
        }
        canvas.restoreToCount(restoreCount);
        return result;
    }

    public void setInnerTranslationX(float value) {
        innerTranslationX = value;
        invalidate();
    }

    public float getInnerTranslationX() {
        return innerTranslationX;
    }

    private boolean prepareForMoving() {
        if (fragmentsStack.size() <= 1) {
            return false;
        }
        maybeStartTracking = false;
        startedTracking = true;
        layoutToIgnore = containerViewBack;
        containerViewBack.setVisibility(VISIBLE);
        beginTrackingSent = false;

        BaseFragment previousFragment = fragmentsStack.get(fragmentsStack.size() - 2);
        previousFragment.setParentLayout(this);
        containerViewBack.removeAllViews();
        attachViewTo(previousFragment, containerViewBack);
        containerViewBack.setAlpha(1.0f);
        containerViewBack.setTranslationX(0);
        previousFragment.onResume();

        containerView.setLayerType(LAYER_TYPE_HARDWARE, null);
        View focus = findFocus();
        if (focus != null) {
            AndroidUtilities.hideKeyboard(focus);
        }
        return true;
    }

    private void sendBeginSlideIfNeeded() {
        if (beginTrackingSent || fragmentsStack.isEmpty()) {
            return;
        }
        View focus = findFocus();
        if (focus != null) {
            AndroidUtilities.hideKeyboard(focus);
        }
        BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
        currentFragment.onBeginSlide();
        beginTrackingSent = true;
    }

    private void animateBackEndAnimation(boolean backAnimation) {
        BaseFragment currentFragment = fragmentsStack.isEmpty() ? null : fragmentsStack.get(fragmentsStack.size() - 1);
        if (currentFragment == null) {
            return;
        }

        if (backAnimator != null) {
            backAnimator.cancel();
        }

        float x = containerView.getX();
        float measuredWidth = Math.max(1, containerView.getMeasuredWidth());
        AnimatorSet animatorSet = new AnimatorSet();
        float distToMove;
        if (!backAnimation) {
            distToMove = Math.abs(measuredWidth - x);
            int duration = Math.max((int) (200.0f / measuredWidth * distToMove), newBackTransitions() ? 380 : 50);
            animatorSet.playTogether(
                ObjectAnimator.ofFloat(containerView, View.TRANSLATION_X, measuredWidth + (predictiveBackInProgress ? AndroidUtilities.dp(56) : 0)).setDuration(duration),
                ObjectAnimator.ofFloat(this, "innerTranslationX", measuredWidth).setDuration(duration)
            );
        } else {
            distToMove = x;
            int duration = Math.max((int) (320.0f / measuredWidth * distToMove), newBackTransitions() ? 320 : 120);
            animatorSet.playTogether(
                ObjectAnimator.ofFloat(containerView, View.TRANSLATION_X, 0).setDuration(duration),
                ObjectAnimator.ofFloat(this, "innerTranslationX", 0.0f).setDuration(duration)
            );
        }
        if (newBackTransitions()) {
            animatorSet.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        }
        animatorSet.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
                predictiveBackInProgress = false;
                containerView.setAlpha(1.0f);
                onSlideAnimationEnd(true);
                backAnimator = null;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (cancelled) {
                    return;
                }
                predictiveBackInProgress = false;
                containerView.setAlpha(1.0f);
                onSlideAnimationEnd(backAnimation);
                backAnimator = null;
            }
        });
        backAnimator = animatorSet;
        animationInProgress = true;
        animatorSet.start();
    }

    private void startLayoutCloseAnimation() {
        if (!prepareForMoving()) {
            return;
        }
        BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);

        LayoutContainer temp = containerView;
        containerView = containerViewBack;
        containerViewBack = temp;

        animationInProgress = true;
        containerViewBack.setAlpha(1.0f);
        containerViewBack.setTranslationX(0.0f);
        oldFragment = currentFragment;
        newFragment = fragmentsStack.get(fragmentsStack.size() - 2);
        startLayoutAnimation(false, true);
    }

    private void startLayoutAnimation(final boolean open, final boolean first) {
        if (first) {
            animationProgress = 0.0f;
            lastFrameTime = System.nanoTime() / 1000000;
        }
        AndroidUtilities.runOnUIThread(animationRunnable = new Runnable() {
            @Override
            public void run() {
                if (animationRunnable != this) {
                    return;
                }
                animationRunnable = null;
                long newTime = System.nanoTime() / 1000000;
                long dt = newTime - lastFrameTime;
                if (dt > 40 && first) {
                    dt = 0;
                } else if (dt > 18) {
                    dt = 18;
                }
                lastFrameTime = newTime;
                animationProgress += dt / 150.0f;
                if (animationProgress > 1.0f) {
                    animationProgress = 1.0f;
                }
                float interpolated = decelerateInterpolator.getInterpolation(animationProgress);
                if (open) {
                    float clampedInterpolated = clamp01(interpolated);
                    containerView.setAlpha(clampedInterpolated);
                    containerView.setTranslationX(AndroidUtilities.dp(48) * (1.0f - interpolated));
                } else {
                    float clampedReverseInterpolated = clamp01(1.0f - interpolated);
                    containerViewBack.setAlpha(clampedReverseInterpolated);
                    containerViewBack.setTranslationX(AndroidUtilities.dp(48) * interpolated);
                }
                if (animationProgress < 1.0f) {
                    startLayoutAnimation(open, false);
                } else {
                    onAnimationEndCheck(open);
                }
            }
        });
    }

    private void onAnimationEndCheck(boolean open) {
        AndroidUtilities.cancelRunOnUIThread(animationRunnable);
        animationRunnable = null;
        if (open) {
            containerView.setAlpha(1.0f);
            containerView.setTranslationX(0.0f);
            containerViewBack.removeAllViews();
            containerViewBack.setVisibility(INVISIBLE);
            if (newFragment != null) {
                newFragment.onResume();
            }
        } else {
            if (oldFragment != null && fragmentsStack.size() > 1) {
                fragmentsStack.remove(fragmentsStack.size() - 1);
                oldFragment.onPause();
            }
            containerViewBack.removeAllViews();
            containerViewBack.setAlpha(1.0f);
            containerViewBack.setTranslationX(0.0f);
            containerViewBack.setVisibility(INVISIBLE);
            containerViewBack.setLayerType(LAYER_TYPE_NONE, null);
            startedTracking = false;
            maybeStartTracking = false;
            beginTrackingSent = false;
            layoutToIgnore = null;
            setInnerTranslationX(0);
        }
        oldFragment = null;
        newFragment = null;
        animationInProgress = false;
    }

    private void onSlideAnimationEnd(boolean backAnimation) {
        if (!backAnimation) {
            if (fragmentsStack.size() >= 2) {
                BaseFragment currentFragment = fragmentsStack.remove(fragmentsStack.size() - 1);
                BaseFragment previousFragment = fragmentsStack.get(fragmentsStack.size() - 1);
                currentFragment.onPause();

                LayoutContainer temp = containerView;
                containerView = containerViewBack;
                containerViewBack = temp;
                bringChildToFront(containerView);
                previousFragment.onResume();
            }
        } else if (fragmentsStack.size() >= 2) {
            BaseFragment previousFragment = fragmentsStack.get(fragmentsStack.size() - 2);
            previousFragment.onPause();
            BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
            currentFragment.onResume();
        }
        containerViewBack.removeAllViews();
        containerViewBack.setVisibility(INVISIBLE);
        startedTracking = false;
        animationInProgress = false;
        containerView.setTranslationX(0);
        containerViewBack.setTranslationX(0);
        containerView.setLayerType(LAYER_TYPE_NONE, null);
        setInnerTranslationX(0);
        predictiveInput = false;
        predictiveBackInProgress = false;
        predictiveBackHasProgress = false;
        layoutToIgnore = null;
    }

    private void attachView(BaseFragment fragment) {
        attachViewTo(fragment, containerView);
    }

    private void attachViewTo(BaseFragment fragment, FrameLayout container) {
        fragment.setParentLayout(this);
        View contentView = fragment.getFragmentView();
        if (contentView == null) {
            contentView = fragment.createView(getContext());
            fragment.setFragmentView(contentView);
        }
        removeFromParent(contentView);

        int actionBarHeight = 0;
        ActionBar actionBar = fragment.getActionBar();
        if (actionBar != null && actionBar.shouldAddToContainer()) {
            removeFromParent(actionBar);
            actionBarHeight = actionBar.getCurrentActionBarHeight();
        }

        FrameLayout.LayoutParams contentLayoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.START);
        contentLayoutParams.topMargin = actionBarHeight;
        container.addView(contentView, contentLayoutParams);
        if (actionBar != null && actionBar.shouldAddToContainer()) {
            container.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, actionBarHeight, Gravity.TOP | Gravity.START));
        }
    }

    private void removeFromParent(View view) {
        if (view == null) {
            return;
        }
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        maybeStartTracking = false;
        startedTracking = false;
        startedTrackingPointerId = -1;
        beginTrackingSent = false;
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    private boolean newBackTransitions() {
        return predictiveBackInProgress && predictiveBackHasProgress;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float clamp01(float value) {
        return clamp(value, 0f, 1f);
    }

    private float lerp(float from, float to, float progress) {
        return from + (to - from) * progress;
    }

    private View findScrollingChild(ViewGroup parent, float x, float y) {
        int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = parent.getChildAt(i);
            if (child == layoutToIgnore || child.getVisibility() != View.VISIBLE) {
                continue;
            }
            child.getHitRect(rect);
            if (rect.contains((int) x, (int) y)) {
                if (child.canScrollHorizontally(-1)) {
                    return child;
                } else if (child instanceof ViewGroup) {
                    View scrollingChild = findScrollingChild((ViewGroup) child, x - rect.left, y - rect.top);
                    if (scrollingChild != null) {
                        return scrollingChild;
                    }
                }
            }
        }
        return null;
    }
}
