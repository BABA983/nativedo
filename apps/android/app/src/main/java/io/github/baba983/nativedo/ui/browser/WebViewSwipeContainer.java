// UPSTREAM-LEVEL: L0
// UPSTREAM-PATH:  org/telegram/ui/bots/ChatAttachAlertBotWebViewLayout.java (inner class WebViewSwipeContainer)
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// SUBSYSTEMS: gesture=L0, animation=L0, touch-slop=L0, spring-physics=L0, swipe-dismiss=L0
// HOST-WIRING:
//   - Package/imports rewritten to NativeDo.
//   - BotWebViewContainer.MyWebView replaced with android.webkit.WebView.
//   - injectedJS field added locally (default false) — standard browser WebViews don't inject JS.
//   - Bulletin.getVisibleBulletin() position update removed (no Bulletin in NativeDo).
//   - ActionBar.getCurrentActionBarHeight() default uses NativeDo ActionBar instance method.
// ADAPTED:
//   - Keeps all L0 constants: 650dp/s fling-down, 700dp/s fling-up, 1200dp/s fullsize threshold,
//     60dp cap, 64dp sticky range (portrait), 8dp sticky range (landscape), 1200 stiffness,
//     1400 stiffness, DAMPING_RATIO_NO_BOUNCY, 200dp/250ms distance/time thresholds.
// OMITTED:
//   - RenderNode capture for blur/preview (not needed for NativeDo's first browser slice).
//   - FileLog error logging.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/bots/ChatAttachAlertBotWebViewLayout.java (WebViewSwipeContainer).
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

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.math.MathUtils;
import androidx.core.view.GestureDetectorCompat;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import io.github.baba983.nativedo.core.AndroidUtilities;
import io.github.baba983.nativedo.core.GenericProvider;
import io.github.baba983.nativedo.ui.components.SimpleFloatPropertyCompat;

import static io.github.baba983.nativedo.core.AndroidUtilities.dp;

public class WebViewSwipeContainer extends FrameLayout {

    public static final SimpleFloatPropertyCompat<WebViewSwipeContainer> SWIPE_OFFSET_Y =
            new SimpleFloatPropertyCompat<>("swipeOffsetY", WebViewSwipeContainer::getSwipeOffsetY, WebViewSwipeContainer::setSwipeOffsetY);

    private final GestureDetectorCompat gestureDetector;
    public boolean isScrolling;
    private boolean isSwipeDisallowed;

    public float topActionBarOffsetY = AndroidUtilities.dp(56) + AndroidUtilities.statusBarHeight;
    public float offsetY = 0;
    private float pendingOffsetY = -1;
    private float pendingSwipeOffsetY = Integer.MIN_VALUE;
    private float swipeOffsetY;
    private boolean isSwipeOffsetAnimationDisallowed;

    private SpringAnimation offsetYAnimator;

    private boolean flingInProgress;

    private WebView webView;

    private Runnable scrollListener;
    private Runnable scrollEndListener;
    private Delegate delegate;

    private SpringAnimation scrollAnimator;

    private int swipeStickyRange;

    private GenericProvider<Void, Boolean> isKeyboardVisible = obj -> false;

    private boolean fullsize;
    public boolean opened;

    public void setFullSize(boolean fullsize) {
        if (this.fullsize != fullsize) {
            this.fullsize = fullsize;
            if (fullsize) {
                if (opened) {
                    stickTo(-getOffsetY() + getTopActionBarOffsetY());
                }
            } else {
                stickTo(0);
            }
        }
    }

    public boolean isFullSize() {
        return fullsize;
    }

    private boolean allowFullSizeSwipe;

    public void setAllowFullSizeSwipe(boolean value) {
        allowFullSizeSwipe = value;
    }

    private boolean allowSwipes = true;

    public void setAllowSwipes(boolean allowSwipes) {
        if (this.allowSwipes != allowSwipes) {
            this.allowSwipes = allowSwipes;
        }
    }

    public boolean isAllowedSwipes() {
        return allowSwipes;
    }

    public boolean shouldWaitWebViewScroll;
    public boolean allowedScrollX, allowedScrollY;

    public void setShouldWaitWebViewScroll(boolean value) {
        shouldWaitWebViewScroll = value;
    }

    public void allowThisScroll(boolean x, boolean y) {
        allowedScrollX = x;
        allowedScrollY = y;
    }

    /**
     * Upstream checks webView.injectedJS for bot WebViews.
     * Standard browser WebViews never inject JS, so injectedJS defaults to false
     * and allowingScroll always returns true when webView != null.
     */
    public boolean injectedJS;

    public boolean allowingScroll(boolean x) {
        return webView == null || !injectedJS || (x ? allowedScrollX : allowedScrollY);
    }

    public WebViewSwipeContainer(@NonNull Context context) {
        super(context);

        int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        gestureDetector = new GestureDetectorCompat(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (isSwipeDisallowed || !allowSwipes || fullsize && !allowFullSizeSwipe || (shouldWaitWebViewScroll && !allowingScroll(false))) {
                    return false;
                }
                final float distance = AndroidUtilities.distance(e1.getX(), e1.getY(), e2.getX(), e2.getY());
                final float time = e2.getEventTime() - e1.getEventTime();
                if (velocityY >= dp(650) && (distance > dp(200) || (time > 250)) && (webView == null || webView.getScrollY() == 0)) {
                    flingInProgress = true;

                    if (swipeOffsetY >= swipeStickyRange || fullsize) {
                        if (fullsize && allowFullSizeSwipe && (drawnSwipeOffsetY == -offsetY + topActionBarOffsetY || swipeOffsetY <= -swipeStickyRange && velocityY < dp(1200))) {
                            stickTo(-offsetY + topActionBarOffsetY);
                        } else if (delegate != null) {
                            delegate.onDismiss(false);
                        }
                    } else {
                        stickTo(0);
                    }
                    return true;
                } else if (velocityY <= -700 && swipeOffsetY > -offsetY + topActionBarOffsetY) {
                    flingInProgress = true;
                    stickTo(-offsetY + topActionBarOffsetY);
                    return true;
                }
                return false;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                distanceY = cap(distanceY);
                if (!isScrolling && !isSwipeDisallowed && allowSwipes && (!shouldWaitWebViewScroll || swipeOffsetY != -offsetY + topActionBarOffsetY || allowingScroll(false))) {
                    if (isKeyboardVisible.provide(null) && swipeOffsetY == -offsetY + topActionBarOffsetY) {
                        isSwipeDisallowed = true;
                    } else if (Math.abs(distanceY) >= touchSlop && Math.abs(distanceY) * 1.5f >= Math.abs(distanceX) && (swipeOffsetY != -offsetY + topActionBarOffsetY || webView == null || distanceY < 0 && webView.getScrollY() == 0)) {
                        isScrolling = true;

                        MotionEvent ev = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
                        for (int i = 0; i < getChildCount(); i++) {
                            getChildAt(i).dispatchTouchEvent(ev);
                        }
                        ev.recycle();

                        return true;
                    } else if (webView != null && webView.canScrollHorizontally(distanceX >= 0 ? 1 : -1) || Math.abs(distanceX) >= touchSlop && Math.abs(distanceX) * 1.5f >= Math.abs(distanceY)) {
                        isSwipeDisallowed = true;
                    }
                }
                if (isScrolling) {
                    if (distanceY < 0) {
                        if (swipeOffsetY > -offsetY + topActionBarOffsetY) {
                            swipeOffsetY -= distanceY;
                        } else if (webView != null) {
                            float newWebScrollY = webView.getScrollY() + distanceY;
                            webView.setScrollY((int) MathUtils.clamp(newWebScrollY, 0, Math.max(webView.getContentHeight(), webView.getHeight()) - topActionBarOffsetY));

                            if (newWebScrollY < 0) {
                                swipeOffsetY -= newWebScrollY;
                            }
                        } else {
                            swipeOffsetY -= distanceY;
                        }
                    } else if (distanceY > 0) {
                        swipeOffsetY -= distanceY;

                        if (webView != null && swipeOffsetY < -offsetY + topActionBarOffsetY) {
                            float newWebScrollY = webView.getScrollY() - (swipeOffsetY + offsetY - topActionBarOffsetY);
                            webView.setScrollY((int) MathUtils.clamp(newWebScrollY, 0, Math.max(webView.getContentHeight(), webView.getHeight()) - topActionBarOffsetY));
                        }
                    }

                    swipeOffsetY = MathUtils.clamp(swipeOffsetY, -offsetY + topActionBarOffsetY, getHeight() - offsetY + topActionBarOffsetY);
                    if (fullsize && !allowFullSizeSwipe) {
                        swipeOffsetY = Math.min(swipeOffsetY, -offsetY + topActionBarOffsetY);
                    }
                    invalidateTranslation();
                    return true;
                }

                return true;
            }
        });
        updateStickyRange();
    }

    private float drawnSwipeOffsetY;

    public void setIsKeyboardVisible(GenericProvider<Void, Boolean> isKeyboardVisible) {
        this.isKeyboardVisible = isKeyboardVisible;
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateStickyRange();
    }

    private void updateStickyRange() {
        swipeStickyRange = AndroidUtilities.dp(AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? 8 : 64);
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        super.requestDisallowInterceptTouchEvent(disallowIntercept);

        if (disallowIntercept) {
            isSwipeDisallowed = true;
            isScrolling = false;
        }
    }

    public void setSwipeOffsetAnimationDisallowed(boolean swipeOffsetAnimationDisallowed) {
        isSwipeOffsetAnimationDisallowed = swipeOffsetAnimationDisallowed;
    }

    public void setScrollListener(Runnable scrollListener) {
        this.scrollListener = scrollListener;
    }

    public void setScrollEndListener(Runnable scrollEndListener) {
        this.scrollEndListener = scrollEndListener;
    }

    public void setWebView(WebView webView) {
        this.webView = webView;
    }

    public void setTopActionBarOffsetY(float topActionBarOffsetY) {
        this.topActionBarOffsetY = topActionBarOffsetY;
        invalidateTranslation();
    }

    public void setSwipeOffsetY(float swipeOffsetY) {
        this.swipeOffsetY = swipeOffsetY;
        invalidateTranslation();
    }

    public void setForceOffsetY(float offsetY) {
        this.offsetY = offsetY;
        invalidateTranslation();
    }

    public void setOffsetY(float offsetY) {
        if (pendingSwipeOffsetY != Integer.MIN_VALUE) {
            pendingOffsetY = offsetY;
            return;
        }

        if (offsetYAnimator != null) {
            offsetYAnimator.cancel();
        }

        float wasOffsetY = this.offsetY;
        float deltaOffsetY = offsetY - wasOffsetY;
        boolean wasOnTop = Math.abs(swipeOffsetY + wasOffsetY - topActionBarOffsetY) <= dp(1);
        if (!isSwipeOffsetAnimationDisallowed) {
            if (offsetYAnimator != null) {
                offsetYAnimator.cancel();
            }
            offsetYAnimator = new SpringAnimation(new FloatValueHolder(wasOffsetY))
                    .setSpring(new SpringForce(offsetY)
                            .setStiffness(1400)
                            .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY))
                    .addUpdateListener((animation, value, velocity) -> {
                        this.offsetY = value;

                        float progress = deltaOffsetY == 0 ? 1f : (value - wasOffsetY) / deltaOffsetY;

                        if (wasOnTop) {
                            swipeOffsetY = MathUtils.clamp(
                                swipeOffsetY - progress * Math.max(0, deltaOffsetY),
                                -this.offsetY + topActionBarOffsetY,
                                getHeight() - this.offsetY + topActionBarOffsetY
                            );
                        }
                        if (scrollAnimator != null && scrollAnimator.getSpring().getFinalPosition() == -wasOffsetY + topActionBarOffsetY) {
                            scrollAnimator.getSpring().setFinalPosition(-offsetY + topActionBarOffsetY);
                        }
                        invalidateTranslation();
                    })
                    .addEndListener((animation, canceled, value, velocity) -> {
                        offsetYAnimator = null;

                        if (!canceled) {
                            WebViewSwipeContainer.this.offsetY = offsetY;
                            invalidateTranslation();
                        } else {
                            pendingOffsetY = offsetY;
                        }
                    });
            offsetYAnimator.start();
        } else {
            this.offsetY = offsetY;

            if (wasOnTop) {
                swipeOffsetY = MathUtils.clamp(
                    swipeOffsetY - Math.max(0, deltaOffsetY),
                    -this.offsetY + topActionBarOffsetY,
                    getHeight() - this.offsetY + topActionBarOffsetY
                );
            }
            invalidateTranslation();
        }
    }

    private void updateDrawn() {
        drawnSwipeOffsetY = swipeOffsetY;
    }

    public void invalidateTranslation() {
        setTranslationY(Math.max(topActionBarOffsetY, offsetY + swipeOffsetY));
        AndroidUtilities.cancelRunOnUIThread(this::updateDrawn);
        AndroidUtilities.runOnUIThread(this::updateDrawn);
        if (scrollListener != null) {
            scrollListener.run();
        }
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
    }

    public float getTopActionBarOffsetY() {
        return topActionBarOffsetY;
    }

    public float getOffsetY() {
        return offsetY;
    }

    public float getSwipeOffsetY() {
        return swipeOffsetY;
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    private float sy = 0;
    private boolean scrolledOut = false;
    private final float minscroll = dp(60);

    private float cap(float dy) {
        if (scrolledOut) {
            return dy;
        }
        sy += dy;
        if (Math.abs(sy) > minscroll) {
            scrolledOut = true;
            if (sy > 0) {
                dy = sy - minscroll;
            } else {
                dy = sy + minscroll;
            }
        } else {
            dy = 0;
        }
        return dy;
    }

    public boolean stickToEdges = true;

    private long pressDownTime;
    private float pressDownX, pressDownY;

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (isScrolling && ev.getActionIndex() != 0) {
            return false;
        }
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            pressDownTime = ev.getEventTime();
            pressDownX = ev.getX();
            pressDownY = ev.getY();
            scrolledOut = false;
            sy = 0;
            if (shouldWaitWebViewScroll) {
                allowedScrollX = false;
                allowedScrollY = false;
            }
        }

        MotionEvent rawEvent = MotionEvent.obtain(ev);
        int index = ev.getActionIndex();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            rawEvent.setLocation(ev.getRawX(index), ev.getRawY(index));
        } else {
            float offsetX = ev.getRawX() - ev.getX(), offsetY = ev.getRawY() - ev.getY();
            rawEvent.setLocation(ev.getX(index) + offsetX, ev.getY(index) + offsetY);
        }
        boolean detector = gestureDetector.onTouchEvent(rawEvent);
        rawEvent.recycle();

        if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
            final boolean wasScrolling = isScrolling;
            isSwipeDisallowed = false;
            isScrolling = false;

            if (fullsize && !allowFullSizeSwipe) {

            } else if (flingInProgress) {
                flingInProgress = false;
            } else if (allowSwipes && (!shouldWaitWebViewScroll || swipeOffsetY != -offsetY + topActionBarOffsetY && allowingScroll(false))) {
                if (swipeOffsetY <= -swipeStickyRange) {
                    if (stickToEdges) {
                        stickTo(-offsetY + topActionBarOffsetY);
                    }
                } else if (swipeOffsetY > -swipeStickyRange && swipeOffsetY <= swipeStickyRange) {
                    if (stickToEdges) {
                        stickTo(0);
                    }
                } else {
                    final float distance = AndroidUtilities.distance(ev.getX(), ev.getY(), pressDownX, pressDownY);
                    final long time = ev.getEventTime() - pressDownTime;
                    if (delegate != null && (time > 250 || distance > dp(200))) {
                        delegate.onDismiss(!wasScrolling);
                    } else if (stickToEdges) {
                        stickTo(-offsetY + topActionBarOffsetY);
                    }
                }
            }
        }

        boolean superTouch = super.dispatchTouchEvent(ev);
        if (!superTouch && !detector && ev.getAction() == MotionEvent.ACTION_DOWN) {
            return true;
        }

        return superTouch || detector;
    }

    public void stickTo(float offset) {
        stickTo(offset, null);
    }

    public void cancelStickTo() {
        if (offsetYAnimator != null) {
            offsetYAnimator.cancel();
        }
        if (scrollAnimator != null) {
            scrollAnimator.cancel();
        }
    }

    public void stickTo(float offset, Runnable callback) {
        stickTo(offset, false, callback);
    }

    public void stickTo(float offset, boolean force, Runnable callback) {
        if (fullsize && !force) {
            offset = -getOffsetY() + getTopActionBarOffsetY();
        }
        if (swipeOffsetY == offset || scrollAnimator != null && scrollAnimator.getSpring().getFinalPosition() == offset) {
            if (callback != null) {
                callback.run();
            }
            if (scrollEndListener != null) {
                scrollEndListener.run();
            }
            return;
        }
        pendingSwipeOffsetY = offset;

        if (offsetYAnimator != null) {
            offsetYAnimator.cancel();
        }
        if (scrollAnimator != null) {
            scrollAnimator.cancel();
        }
        scrollAnimator = new SpringAnimation(this, SWIPE_OFFSET_Y, offset)
                .setSpring(new SpringForce(offset)
                        .setStiffness(1200)
                        .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY))
                .addEndListener((animation, canceled, value, velocity) -> {
                    if (animation == scrollAnimator) {
                        scrollAnimator = null;

                        if (callback != null) {
                            callback.run();
                        }

                        if (scrollEndListener != null) {
                            scrollEndListener.run();
                        }

                        if (pendingOffsetY != -1) {
                            boolean wasDisallowed = isSwipeOffsetAnimationDisallowed;
                            isSwipeOffsetAnimationDisallowed = true;
                            setOffsetY(pendingOffsetY);
                            pendingOffsetY = -1;
                            isSwipeOffsetAnimationDisallowed = wasDisallowed;
                        }
                        pendingSwipeOffsetY = Integer.MIN_VALUE;
                    }
                });
        scrollAnimator.start();
    }

    public boolean isSwipeInProgress() {
        return isScrolling;
    }

    public interface Delegate {
        /**
         * Called to dismiss parent layout
         */
        void onDismiss(boolean byTap);
    }
}
