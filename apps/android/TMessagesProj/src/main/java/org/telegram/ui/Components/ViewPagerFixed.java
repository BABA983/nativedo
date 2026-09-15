/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/Components/ViewPagerFixed.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/Components/ViewPagerFixed.java
 *
 * This is an L1 faithful slice. Kept gesture / animation / list-recycling
 * code is L0 and should stay aligned with upstream. See header tag above for
 * out-of-scope subsystems intentionally not present in this slice.
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
package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import java.util.ArrayList;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.ui.ActionBar.Theme;

public class ViewPagerFixed extends FrameLayout {

    private Theme.ResourcesProvider resourcesProvider;
    public int currentPosition;
    public float currentProgress = 1f;
    protected int nextPosition;
    protected View[] viewPages;
    private int[] viewTypes;
    private int[] pageIds;

    protected SparseArray<View> viewsByType = new SparseArray<>();

    private int startedTrackingPointerId;
    private int startedTrackingX;
    private int startedTrackingY;
    private VelocityTracker velocityTracker;

    private AnimatorSet tabsAnimation;
    private boolean tabsAnimationInProgress;
    private boolean animatingForward;
    private float additionalOffset;
    private boolean backAnimation;
    private int maximumVelocity;
    private boolean startedTracking;
    private boolean maybeStartTracking;
    private static final Interpolator interpolator = t -> {
        --t;
        return t * t * t * t * t + 1.0F;
    };

    AnimationNotificationsLocker notificationsLocker = new AnimationNotificationsLocker();

    private final float touchSlop;

    public Adapter adapter;
    TabsView tabsView;

    ValueAnimator.AnimatorUpdateListener updateTabProgress = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
            if (tabsAnimationInProgress) {
                float scrollProgress = Math.abs(viewPages[0].getTranslationX()) / (float) viewPages[0].getMeasuredWidth();
                currentProgress = 1f - scrollProgress;
                if (tabsView != null) {
                    tabsView.selectTab(nextPosition, currentPosition, currentProgress);
                }
            }
            onTabAnimationUpdate(false);
        }
    };
    private Rect rect = new Rect();
    private boolean allowDisallowInterceptTouch = true;

    public void onTabAnimationUpdate(boolean manual) {

    }

    public float getPositionVisibility(int position) {
        if (getMeasuredWidth() == 0) {
            return clamp(1 - Math.abs(getCurrentPosition() - position), 0, 1);
        } else {
            return clamp(1f - Math.abs(getPositionAnimated() - position), 0, 1);
        }
    }

    public float getPositionAnimated() {
        float position = 0;
        if (viewPages[0] != null && viewPages[0].getVisibility() == View.VISIBLE) {
            final float t = clamp(1f - Math.abs(viewPages[0].getTranslationX() / getAvailableTranslationX()), 0, 1);
            position += currentPosition * t;
        }
        if (viewPages[1] != null && viewPages[1].getVisibility() == View.VISIBLE) {
            final float t = clamp(1f - Math.abs(viewPages[1].getTranslationX() / getAvailableTranslationX()), 0, 1);
            position += nextPosition * t;
        }
        return position;
    }

    public float getCurrentPositionAlpha() {
        if (viewPages[0] != null && viewPages[0].getVisibility() == View.VISIBLE) {
            return clamp(1f - Math.abs(viewPages[0].getTranslationX() / getAvailableTranslationX()), 0, 1);
        }
        return 0;
    }

    public int getNextPosition() {
        return nextPosition;
    }

    public float getNextPositionAlpha() {
        if (viewPages[1] != null && viewPages[1].getVisibility() == View.VISIBLE) {
            return clamp(1f - Math.abs(viewPages[1].getTranslationX() / getAvailableTranslationX()), 0, 1);
        }
        return 0;
    }

    protected float getAvailableTranslationX() {
        return getMeasuredWidth();
    }

    protected boolean canScroll(MotionEvent e) {
        return true;
    }

    protected boolean canScrollForward(MotionEvent e) {
        return canScroll(e);
    }

    protected boolean canScrollBackward(MotionEvent e) {
        return true;
    }

    protected void onScrollEnd() {
    }

    public ViewPagerFixed(@NonNull Context context) {
        this(context, null);
    }

    public ViewPagerFixed(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        touchSlop = AndroidUtilities.getPixelsInCM(0.3f, true);
        maximumVelocity = ViewConfiguration.get(context).getScaledMaximumFlingVelocity();

        viewTypes = new int[2];
        viewPages = new View[2];
        setClipChildren(true);
    }

    public void setAdapter(Adapter adapter) {
        this.adapter = adapter;
        viewTypes[0] = adapter.getItemViewType(currentPosition);
        viewPages[0] = adapter.createView(viewTypes[0]);
        if (viewPages[0] == null && currentPosition != 0) {
            currentPosition = 0;
            viewTypes[0] = adapter.getItemViewType(currentPosition);
            viewPages[0] = adapter.createView(viewTypes[0]);
        }
        adapter.bindView(viewPages[0], currentPosition, viewTypes[0]);
        addView(viewPages[0]);
        viewPages[0].setVisibility(View.VISIBLE);
    }

    protected void onTabPageSelected(int position, boolean forward) {
        onTabPageSelected(position);
    }

    protected void onTabPageSelected(int position) {

    }

    protected int tabMarginDp() {
        return 16;
    }

    public boolean isManualScrolling() {
        return manualScrolling != null && manualScrolling.isRunning();
    }

    private ValueAnimator manualScrolling;

    public boolean scrollToPosition(int page) {
        if (page == currentPosition || (manualScrolling != null && nextPosition == page)) {
            return false;
        }
        if (manualScrolling != null) {
            manualScrolling.cancel();
            manualScrolling = null;
        }

        boolean forward = currentPosition < page;
        animatingForward = forward;
        nextPosition = page;
        updateViewForIndex(1);

        onTabPageSelected(page, forward);
        final int tX = viewPages[0] != null ? viewPages[0].getMeasuredWidth() : 0;
        if (forward) {
            setTranslationX(viewPages[1], tX);
        } else {
            setTranslationX(viewPages[1], -tX);
        }

        manualScrolling = ValueAnimator.ofFloat(0, 1);
        manualScrolling.addUpdateListener(anm -> {
            float progress = (float) anm.getAnimatedValue();
            if (viewPages[1] == null) {
                return;
            }
            if (animatingForward) {
                setTranslationX(viewPages[1], viewPages[0].getMeasuredWidth() * (1f - progress));
                setTranslationX(viewPages[0], -viewPages[0].getMeasuredWidth() * progress);
            } else {
                setTranslationX(viewPages[1], -viewPages[0].getMeasuredWidth() * (1f - progress));
                setTranslationX(viewPages[0], viewPages[0].getMeasuredWidth() * progress);
            }
            currentProgress = progress;
            onTabAnimationUpdate(true);
            if (tabsView != null) {
                tabsView.listView.invalidate();
                tabsView.listView.invalidateViews();
                tabsView.invalidate();
            }
        });
        manualScrolling.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (viewPages[1] != null) {
                    swapViews();
                    viewsByType.put(viewTypes[1], viewPages[1]);
                    removeView(viewPages[1]);
                    setTranslationX(viewPages[0], 0);
                    viewPages[1] = null;
                }
                manualScrolling = null;
                onTabAnimationUpdate(true);
                if (tabsView != null) {
                    tabsView.listView.invalidate();
                    tabsView.listView.invalidateViews();
                    tabsView.invalidate();
                }
                onScrollEnd();
                notificationsLocker.unlock();
            }
        });
        manualScrolling.setDuration(getManualScrollDuration());
        manualScrolling.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        manualScrolling.start();
        return true;
    }

    protected long getManualScrollDuration() {
        return 540;
    }

    protected ValueAnimator translateAnimator(View view, float toTx) {
        ValueAnimator a = ValueAnimator.ofFloat(view.getTranslationX(), toTx);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(@NonNull ValueAnimator animation) {
                setTranslationX(view, (float) animation.getAnimatedValue());
            }
        });
        a.addListener(new AnimatorListenerAdapter() {
            boolean canceled;

            @Override
            public void onAnimationCancel(Animator animation) {
                super.onAnimationCancel(animation);
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!canceled) {
                    setTranslationX(view, toTx);
                }
            }
        });
        return a;
    }

    protected void setTranslationX(View view, float tx) {
        view.setTranslationX(tx);
    }

    protected void invalidateBlur() {

    }

    public boolean isTouch() {
        return startedTracking;
    }

    public void resetTouch() {
        if (!startedTracking) {
            return;
        }
        maybeStartTracking = true;
        startedTracking = false;
        setTranslationX(viewPages[0], 0);
        if (viewPages[1] != null) {
            setTranslationX(viewPages[1], animatingForward ? viewPages[0].getMeasuredWidth() : -viewPages[0].getMeasuredWidth());
        }
        nextPosition = 0;
        currentProgress = 1f;
        if (tabsView != null) {
            tabsView.selectTab(nextPosition, currentPosition, currentProgress);
        }
        onTabAnimationUpdate(false);
    }

    private void updateViewForIndex(int index) {
        int adapterPosition = index == 0 ? currentPosition : nextPosition;
        if (adapterPosition < 0 || adapterPosition >= adapter.getItemCount()) {
            return;
        }
        if (viewPages[index] == null) {
            viewTypes[index] = adapter.getItemViewType(adapterPosition);
            View v = viewsByType.get(viewTypes[index]);
            if (v == null) {
                v = adapter.createView(viewTypes[index]);
            } else {
                viewsByType.remove(viewTypes[index]);
            }
            if (v.getParent() != null) {
                ViewGroup parent = (ViewGroup) v.getParent();
                parent.removeView(v);
            }
            addView(v);
            v.setTranslationX(getMeasuredWidth());
            viewPages[index] = v;
            adapter.bindView(viewPages[index], adapterPosition, viewTypes[index]);
            viewPages[index].setVisibility(View.VISIBLE);
        } else {
            if (viewTypes[index] == adapter.getItemViewType(adapterPosition)) {
                adapter.bindView(viewPages[index], adapterPosition, viewTypes[index]);
                viewPages[index].setVisibility(View.VISIBLE);
            } else {
                viewsByType.put(viewTypes[index], viewPages[index]);
                viewPages[index].setVisibility(View.GONE);
                removeView(viewPages[index]);
                viewTypes[index] = adapter.getItemViewType(adapterPosition);
                View v = viewsByType.get(viewTypes[index]);
                if (v == null) {
                    v = adapter.createView(viewTypes[index]);
                } else {
                    viewsByType.remove(viewTypes[index]);
                }
                addView(v);
                viewPages[index] = v;
                viewPages[index].setVisibility(View.VISIBLE);
                adapter.bindView(viewPages[index], adapterPosition, adapter.getItemViewType(adapterPosition));
            }
        }
    }

    protected void onBack() {

    }

    private float backProgress;

    protected boolean onBackProgress(float progress) {
        return false;
    }

    private boolean prepareForMoving(MotionEvent ev, boolean forward) {
        if ((!forward && currentPosition == 0 && !onBackProgress(backProgress = 0)) || (forward && currentPosition == adapter.getItemCount() - 1) || manualScrolling != null) {
            return false;
        }
        if (!canScroll(ev)) {
            return false;
        }
        if (forward && !canScrollForward(ev)) {
            return false;
        }
        if (!forward && !canScrollBackward(ev)) {
            return false;
        }

        if (adapter != null && !adapter.canScrollTo(currentPosition + (forward ? +1 : -1))) {
            return false;
        }

        getParent().requestDisallowInterceptTouchEvent(true);
        maybeStartTracking = false;
        startedTracking = true;
        onStartTracking();
        startedTrackingX = (int) (ev.getX() + additionalOffset);
        if (tabsView != null) {
            tabsView.setEnabled(false);
        }

        notificationsLocker.lock();
        animatingForward = forward;
        nextPosition = currentPosition + (forward ? 1 : -1);
        updateViewForIndex(1);
        if (viewPages[1] != null) {
            if (forward) {
                setTranslationX(viewPages[1], viewPages[0].getMeasuredWidth());
            } else {
                setTranslationX(viewPages[1], -viewPages[0].getMeasuredWidth());
            }
        }
        onTabAnimationUpdate(false);
        return true;
    }

    public void onStartTracking() {

    }

    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (tabsView != null && tabsView.isAnimatingIndicator()) {
            return false;
        }
        if (checkTabsAnimationInProgress()) {
            return true;
        }
        onTouchEvent(ev);
        return startedTracking;
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (allowDisallowInterceptTouch && maybeStartTracking && !startedTracking) {
            onTouchEvent(null);
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return onTouchEventInternal(ev);
    }

    public boolean onTouchEventInternal(MotionEvent ev) {
        if (tabsView != null && tabsView.animatingIndicator) {
            return false;
        }
        if (ev != null) {
            if (velocityTracker == null) {
                velocityTracker = VelocityTracker.obtain();
            }
            velocityTracker.addMovement(ev);
        }
        if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN && checkTabsAnimationInProgress()) {
            startedTracking = true;
            onStartTracking();
            startedTrackingPointerId = ev.getPointerId(0);
            startedTrackingX = (int) ev.getX();
            if (animatingForward) {
                if (startedTrackingX < viewPages[0].getMeasuredWidth() + viewPages[0].getTranslationX()) {
                    additionalOffset = viewPages[0].getTranslationX();
                } else {
                    swapViews();
                    animatingForward = false;
                    additionalOffset = viewPages[0].getTranslationX();
                }
            } else if (viewPages[1] != null) {
                if (startedTrackingX < viewPages[1].getMeasuredWidth() + viewPages[1].getTranslationX()) {
                    swapViews();
                    animatingForward = true;
                    additionalOffset = viewPages[0].getTranslationX();
                } else {
                    additionalOffset = viewPages[0].getTranslationX();
                }
            }
            tabsAnimation.removeAllListeners();
            tabsAnimation.cancel();
            tabsAnimationInProgress = false;
        } else if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN) {
            additionalOffset = 0;
        }

        if (!startedTracking && ev != null) {
            View child = findScrollingChild(this, ev.getX(), ev.getY());
            if (child != null && (child.canScrollHorizontally(1) || child.canScrollHorizontally(-1))) {
                return false;
            }
        }
        if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN && !startedTracking && !maybeStartTracking) {
            startedTrackingPointerId = ev.getPointerId(0);
            maybeStartTracking = true;
            startedTrackingX = (int) ev.getX();
            startedTrackingY = (int) ev.getY();
        } else if (ev != null && ev.getAction() == MotionEvent.ACTION_MOVE && ev.getPointerId(0) == startedTrackingPointerId) {
            int dx = (int) (ev.getX() - startedTrackingX + additionalOffset);
            int dy = Math.abs((int) ev.getY() - startedTrackingY);
            if (startedTracking && (animatingForward && dx > 0 || !animatingForward && dx < 0)) {
                if (!prepareForMoving(ev, dx < 0)) {
                    maybeStartTracking = true;
                    startedTracking = false;
                    setTranslationX(viewPages[0], 0);
                    if (viewPages[1] != null) {
                        setTranslationX(viewPages[1], animatingForward ? viewPages[0].getMeasuredWidth() : -viewPages[0].getMeasuredWidth());
                    }
                    nextPosition = 0;
                    currentProgress = 1f;
                    if (tabsView != null) {
                        tabsView.selectTab(nextPosition, currentPosition, currentProgress);
                    }
                    onTabAnimationUpdate(false);
                }
            }
            if (maybeStartTracking && !startedTracking) {
                int dxLocal = (int) (ev.getX() - startedTrackingX);
                if (Math.abs(dxLocal) >= touchSlop && Math.abs(dxLocal) > dy) {
                    prepareForMoving(ev, dx < 0);
                }
            } else if (startedTracking) {
                float scrollProgress = Math.abs(dx) / (float) viewPages[0].getMeasuredWidth();
                if (nextPosition == -1) {
                    onBackProgress(backProgress = scrollProgress);
                } else {
                    setTranslationX(viewPages[0], dx);
                    if (viewPages[1] != null) {
                        if (animatingForward) {
                            setTranslationX(viewPages[1], viewPages[0].getMeasuredWidth() + dx);
                        } else {
                            setTranslationX(viewPages[1], dx - viewPages[0].getMeasuredWidth());
                        }
                    }
                }
                currentProgress = 1f - scrollProgress;
                if (tabsView != null) {
                    tabsView.selectTab(nextPosition, currentPosition, currentProgress);
                }
                onTabAnimationUpdate(false);
            }
        } else if (ev == null || ev.getPointerId(0) == startedTrackingPointerId && (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_POINTER_UP)) {
            if (velocityTracker != null) {
                velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
            }
            float velX;
            float velY;
            if (ev != null && ev.getAction() != MotionEvent.ACTION_CANCEL) {
                velX = velocityTracker.getXVelocity();
                velY = velocityTracker.getYVelocity();
                if (!startedTracking) {
                    if (Math.abs(velX) >= 3000 && Math.abs(velX) > Math.abs(velY)) {
                        prepareForMoving(ev, velX < 0);
                    }
                }
            } else {
                velX = 0;
                velY = 0;
            }
            if (startedTracking) {
                float x = viewPages[0].getX();
                tabsAnimation = new AnimatorSet();
                if (additionalOffset != 0) {
                    if (Math.abs(velX) > 1500) {
                        backAnimation = animatingForward ? velX > 0 : velX < 0;
                    } else {
                        if (animatingForward) {
                            if (viewPages[1] != null) {
                                backAnimation = (viewPages[1].getX() > (viewPages[0].getMeasuredWidth() >> 1));
                            } else {
                                backAnimation = false;
                            }
                        } else {
                            backAnimation = (viewPages[0].getX() < (viewPages[0].getMeasuredWidth() >> 1));
                        }
                    }
                } else {
                    backAnimation = Math.abs(x) < viewPages[0].getMeasuredWidth() / 3.0f && (Math.abs(velX) < 3500 || Math.abs(velX) < Math.abs(velY));
                }
                float distToMove;
                float dx = 0;
                if (backAnimation) {
                    dx = Math.abs(x);
                    if (animatingForward) {
                        tabsAnimation.playTogether(translateAnimator(viewPages[0], 0));
                        if (viewPages[1] != null) {
                            tabsAnimation.playTogether(translateAnimator(viewPages[1], viewPages[1].getMeasuredWidth()));
                        }
                    } else {
                        tabsAnimation.playTogether(translateAnimator(viewPages[0], 0));
                        if (viewPages[1] != null) {
                            tabsAnimation.playTogether(translateAnimator(viewPages[1], -viewPages[1].getMeasuredWidth()));
                        }
                    }
                } else if (nextPosition >= 0) {
                    dx = viewPages[0].getMeasuredWidth() - Math.abs(x);
                    if (animatingForward) {
                        tabsAnimation.playTogether(translateAnimator(viewPages[0], -viewPages[0].getMeasuredWidth()));
                        if (viewPages[1] != null) {
                            tabsAnimation.playTogether(translateAnimator(viewPages[1], 0));
                        }
                    } else {
                        tabsAnimation.playTogether(translateAnimator(viewPages[0], viewPages[0].getMeasuredWidth()));
                        if (viewPages[1] != null) {
                            tabsAnimation.playTogether(translateAnimator(viewPages[1], 0));
                        }
                    }
                }
                if (nextPosition < 0) {
                    ValueAnimator backAnimator = ValueAnimator.ofFloat(backProgress, backAnimation ? 0f : 1f);
                    backAnimator.addUpdateListener(anm -> {
                        onBackProgress(backProgress = (float) anm.getAnimatedValue());
                    });
                    tabsAnimation.playTogether(backAnimator);
                }
                ValueAnimator animator = ValueAnimator.ofFloat(0, 1f);
                animator.addUpdateListener(updateTabProgress);
                tabsAnimation.playTogether(animator);
                tabsAnimation.setInterpolator(interpolator);

                int width = getMeasuredWidth();
                int halfWidth = width / 2;
                float distanceRatio = Math.min(1.0f, 1.0f * dx / (float) width);
                float distance = (float) halfWidth + (float) halfWidth * distanceInfluenceForSnapDuration(distanceRatio);
                velX = Math.abs(velX);
                int duration;
                if (velX > 0) {
                    duration = 4 * Math.round(1000.0f * Math.abs(distance / velX));
                } else {
                    float pageDelta = dx / getMeasuredWidth();
                    duration = (int) ((pageDelta + 1.0f) * 100.0f);
                }
                duration = Math.max(150, Math.min(duration, 600));

                tabsAnimation.setDuration(duration);
                tabsAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animator) {
                        tabsAnimation = null;
                        if (nextPosition < 0) {
                            onBack();
                        }
                        if (viewPages[1] != null) {
                            if (!backAnimation) {
                                swapViews();
                            }

                            viewsByType.put(viewTypes[1], viewPages[1]);
                            removeView(viewPages[1]);
                            viewPages[1].setVisibility(View.GONE);
                            viewPages[1] = null;
                        }
                        tabsAnimationInProgress = false;
                        maybeStartTracking = false;
                        if (tabsView != null) {
                            tabsView.setEnabled(true);
                        }

                        onTabAnimationUpdate(false);
                        onScrollEnd();
                        notificationsLocker.unlock();
                    }
                });
                tabsAnimation.start();
                tabsAnimationInProgress = true;
                startedTracking = false;

                onTabAnimationUpdate(false);
            } else {
                maybeStartTracking = false;
                if (tabsView != null) {
                    tabsView.setEnabled(true);
                }
            }
            if (velocityTracker != null) {
                velocityTracker.recycle();
                velocityTracker = null;
            }
        }
        return startedTracking || maybeStartTracking;
    }

    protected void swapViews() {
        View page = viewPages[0];
        viewPages[0] = viewPages[1];
        viewPages[1] = page;
        int p = currentPosition;
        currentPosition = nextPosition;
        nextPosition = p;
        currentProgress = 1f - currentProgress;
        p = viewTypes[0];
        viewTypes[0] = viewTypes[1];
        viewTypes[1] = p;

        onItemSelected(viewPages[0], viewPages[1], currentPosition, nextPosition);
    }

    void updatePages() {

    }


    public boolean checkTabsAnimationInProgress() {
        if (tabsAnimationInProgress) {
            boolean cancel = false;
            if (backAnimation) {
                if (Math.abs(viewPages[0].getTranslationX()) < 1) {
                    setTranslationX(viewPages[0], 0);
                    if (viewPages[1] != null) {
                        setTranslationX(viewPages[1], viewPages[0].getMeasuredWidth() * (animatingForward ? 1 : -1));
                    }
                    cancel = true;
                }
            } else if (Math.abs(viewPages[1].getTranslationX()) < 1) {
                setTranslationX(viewPages[0], viewPages[0].getMeasuredWidth() * (animatingForward ? -1 : 1));
                if (viewPages[1] != null) {
                    setTranslationX(viewPages[1], 0);
                }
                cancel = true;
            }
            onTabAnimationUpdate(true);
            if (cancel) {
                if (tabsAnimation != null) {
                    tabsAnimation.cancel();
                    tabsAnimation = null;
                }
                tabsAnimationInProgress = false;
            }
            return tabsAnimationInProgress;
        }
        return false;
    }

    public static float distanceInfluenceForSnapDuration(float f) {
        f -= 0.5F;
        f *= 0.47123894F;
        return (float) Math.sin(f);
    }

    public void setPosition(int position) {
        if (adapter == null) {
            currentPosition = position;
            onTabAnimationUpdate(false);
        }
        if (tabsAnimation != null) {
            tabsAnimation.cancel();
        }
        if (viewPages[1] != null) {
            viewsByType.put(viewTypes[1], viewPages[1]);
            removeView(viewPages[1]);
            viewPages[1] = null;
        }
        if (currentPosition != position) {
            int oldPosition = currentPosition;
            currentPosition = position;
            nextPosition = 0;
            currentProgress = 1f;
            View oldView = viewPages[0];
            updateViewForIndex(0);
            onItemSelected(viewPages[0], oldView, currentPosition, oldPosition);
            setTranslationX(viewPages[0], 0);
            if (tabsView != null) {
                tabsView.selectTab(currentPosition, nextPosition, currentProgress);
            }
            onTabAnimationUpdate(true);
        }
    }

    public void updateCurrent() {
        if (viewTypes[0] != adapter.getItemViewType(currentPosition)) {
            updateViewForIndex(0);
            if (viewPages[1] != null) {
                viewsByType.put(viewTypes[1], viewPages[1]);
                removeView(viewPages[1]);
                viewPages[1] = null;
            }
            setTranslationX(viewPages[0], 0);
            onTabAnimationUpdate(true);
        }
    }

    protected void onItemSelected(View currentPage, View oldPage, int position, int oldPosition) {

    }

    protected void onTabScrollEnd(int position) {

    }

    public View[] getViewPages() {
        return viewPages;
    }

    public boolean isCurrentTabFirst() {
        return currentPosition == 0;
    }

    public abstract static class Adapter {
        public abstract int getItemCount();

        public abstract View createView(int viewType);

        public abstract void bindView(View view, int position, int viewType);

        public int getItemId(int position) {
            return position;
        }

        public CharSequence getItemTitle(int position) {
            return "";
        }

        public int getItemViewType(int position) {
            return 0;
        }

        public boolean hasStableId() {
            return false;
        }

        public boolean needsTab(int position) {
            return true;
        }

        public boolean canScrollTo(int position) {
            return true;
        }

        public boolean canReorder(int position) {
            return false;
        }

        public void applyReorder(ArrayList<Integer> itemIds) {

        }
    }

    @Override
    public boolean canScrollHorizontally(int direction) {
        if (direction == 0) {
            return false;
        }
        if (tabsAnimationInProgress || startedTracking) {
            return true;
        }
        boolean forward = direction > 0;
        if ((!forward && currentPosition == 0) || (forward && currentPosition == adapter.getItemCount() - 1)) {
            return false;
        }
        return true;
    }

    public View getCurrentView() {
        return viewPages[0];
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    private View findScrollingChild(ViewGroup parent, float x, float y) {
        int n = parent.getChildCount();
        for (int i = 0; i < n; i++) {
            View child = parent.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) {
                continue;
            }
            child.getHitRect(rect);
            if (rect.contains((int) x, (int) y)) {
                if (child.canScrollHorizontally(-1)) {
                    return child;
                } else if (child instanceof ViewGroup) {
                    View v = findScrollingChild((ViewGroup) child, x - rect.left, y - rect.top);
                    if (v != null) {
                        return v;
                    }
                }
            }
        }
        return null;
    }

    public void setAllowDisallowInterceptTouch(boolean allowDisallowInterceptTouch) {
        this.allowDisallowInterceptTouch = allowDisallowInterceptTouch;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Compile-only no-op shim for upstream's nested {@code TabsView} class
     * (a RecyclerView-based top-tabs strip with reorder/indicator/popup,
     * ~1200 lines in Telegram's source). NativeDo replaces it with
     * {@code MainTabsLayout} + {@code GlassTabView} at the host-activity
     * level. The {@link #tabsView} field is never assigned in this client;
     * upstream's {@code if (tabsView != null)} guards short-circuit at
     * runtime, so the bodies below are unreachable. They exist only so that
     * the L0 callsites compile without rewriting their structure. See
     * AGENTS.md Hard Rules: kept gesture / animation / list-recycling
     * subsystems in this file are L0.
     */
    static final class TabsView extends View {
        public boolean animatingIndicator;
        final ListViewStub listView;

        TabsView(Context context) {
            super(context);
            listView = new ListViewStub();
        }

        public boolean isAnimatingIndicator() {
            return animatingIndicator;
        }

        public void selectTab(int next, int current, float progress) {
        }

        static final class ListViewStub {
            void invalidate() {
            }

            void invalidateViews() {
            }
        }
    }
}
