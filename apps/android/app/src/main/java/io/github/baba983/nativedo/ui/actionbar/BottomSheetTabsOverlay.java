// UPSTREAM-LEVEL: L1
// UPSTREAM-PATH:  org/telegram/ui/ActionBar/BottomSheetTabsOverlay.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// SUBSYSTEMS: touch=L0, animation=L0, preview-drawing=L1
// HOST-WIRING:
//   - Hosted directly by NativeDo ActionBarLayout instead of Telegram LaunchActivity.
//   - Uses NativeDo BottomSheetTabs.WebTabData and TabDrawable.
//   - Manages visibility locally because NativeDo hosts the overlay inside ActionBarLayout.
//   - Consumes touch during sheet morph animations because NativeDo hosts the overlay as an
//     ActionBarLayout sibling instead of routing tabs through Telegram's sheet window.
//   - Clears the local sheet morph animator after completion so NativeDo's reused BrowserSheet is
//     not released again by a stale listener on the next collapse.
// ADAPTED:
//   - Blur snapshot uses NativeDo's AndroidUtilities.makeBlurBitmap() software helper because
//     NativeDo does not ship Telegram's native Utilities.stackBlurBitmap/global-view pipeline.
//   - WebView preview capture is delegated to NativeDo BrowserWebViewContainer.
//   - Close-all button keeps Paint/TextPaint rendering because NativeDo does not ship Telegram's
//     Text helper and selector round-rect drawable path.
// OMITTED:
//   - BottomSheetTabDialog, tablet dialog mode, RenderNode preview-node capture,
//     bot fields, and drag/reorder.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/ActionBar/BottomSheetTabsOverlay.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/BottomSheetTabsOverlay.java
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
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.OverScroller;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;

import io.github.baba983.nativedo.R;
import io.github.baba983.nativedo.core.AndroidUtilities;
import io.github.baba983.nativedo.ui.components.CubicBezierInterpolator;
import io.github.baba983.nativedo.ui.GradientClip;

public class BottomSheetTabsOverlay extends View {

    public interface Sheet {
        View getWindowView();

        BottomSheetTabs.WebTabData saveState();

        void capturePreviewBitmap(BottomSheetTabs.WebTabData tab, Runnable whenDone);

        void release();

        default void setLastVisible(boolean lastVisible) {
        }
    }

    public interface SheetView {
        void setDrawingFromOverlay(boolean value);

        RectF getRect();

        float drawInto(Canvas canvas, RectF finalRect, float progress, RectF clipRect, float alpha, boolean opening);
    }

    private BottomSheetTabs tabsView;
    private Sheet openingSheet;
    private Sheet dismissingSheet;
    private BottomSheetTabs.TabDrawable openingTab;
    private BottomSheetTabs.TabDrawable dismissingTab;
    private float openingTabScroll;
    private ValueAnimator sheetAnimator;
    private float openingProgress;
    private float dismissProgress;
    private final AnimatedFloat animatedCount = new AnimatedFloat(this);

    private final ArrayList<TabPreview> tabs = new ArrayList<>();
    private final OverScroller scroller;
    private final int maximumVelocity;
    private final int minimumVelocity;
    private int navigationBarInset;

    private final RectF tabsViewBounds = new RectF();
    private final RectF rect = new RectF();
    private final RectF rect2 = new RectF();
    private final RectF clipRect = new RectF();
    private final Path clipPath = new Path();

    private ValueAnimator openAnimator;
    public boolean isOpen;
    private float openProgress;
    private float offset;

    private ValueAnimator scrollAnimator;
    private TabPreview pressTab;
    private boolean pressTabClose;
    private float startY;
    private float startX;
    private long startTime;
    private boolean verticallyScrolling;
    private boolean horizontallySwiping;
    private VelocityTracker velocityTracker;
    private float lastY;
    private final float touchSlop;
    private boolean hitCloseAllButton;
    private final RectF closeAllButtonBounds = new RectF();
    private final Paint closeAllButtonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint closeAllTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap blurBitmap;
    private BitmapShader blurBitmapShader;
    private Paint blurBitmapPaint;
    private Matrix blurMatrix;
    private View actionBarLayout;
    private GradientClip gradientClip;
    private final int[] pos = new int[2];
    private final int[] pos2 = new int[2];

    public BottomSheetTabsOverlay(Context context) {
        super(context);

        setWillNotDraw(false);
        setVisibility(GONE);
        setClickable(true);

        scroller = new OverScroller(context);
        ViewConfiguration configuration = ViewConfiguration.get(context);
        maximumVelocity = configuration.getScaledMaximumFlingVelocity();
        minimumVelocity = configuration.getScaledMinimumFlingVelocity();
        touchSlop = configuration.getScaledTouchSlop();

        closeAllTextPaint.setColor(Color.WHITE);
        closeAllTextPaint.setTextSize(AndroidUtilities.dp(14));
        closeAllTextPaint.setTypeface(Typeface.DEFAULT_BOLD);

        ViewCompat.setOnApplyWindowInsetsListener(this, this::onApplyWindowInsets);
    }

    @NonNull
    private WindowInsetsCompat onApplyWindowInsets(@NonNull View ignoredView, @NonNull WindowInsetsCompat insets) {
        navigationBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
        invalidate();
        return WindowInsetsCompat.CONSUMED;
    }

    public boolean isOpened() {
        return openProgress > .1f;
    }

    public void setTabsView(BottomSheetTabs tabsView) {
        this.tabsView = tabsView;
    }

    public boolean isAnimatingSheet() {
        return openingSheet != null || dismissingSheet != null || openingTab != null || dismissingTab != null;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (isAnimatingSheet()) {
            return true;
        }
        boolean handled = false;
        if (openProgress > 0) {
            if (velocityTracker == null) {
                velocityTracker = VelocityTracker.obtain();
            }
            velocityTracker.addMovement(event);
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                startTime = System.currentTimeMillis();
                startX = event.getX();
                startY = event.getY();
                pressTab = getTabAt(event.getX(), event.getY());
                hitCloseAllButton = closeAllButtonBounds.contains(event.getX(), event.getY());
                if (hitCloseAllButton) {
                    pressTab = null;
                }
                verticallyScrolling = false;
                horizontallySwiping = false;
                pressTabClose = false;
                if (pressTab != null) {
                    pressTab.cancelDismissAnimator();
                    pressTabClose = pressTab.tabDrawable.closeRipple.getBounds().contains(
                            (int) (event.getX() - pressTab.clickBounds.left),
                            (int) (event.getY() - pressTab.clickBounds.top - AndroidUtilities.dp(24))
                    );
                    pressTab.setPressed(!pressTabClose);
                    pressTab.tabDrawable.closeRipple.setState(pressTabClose ? new int[]{android.R.attr.state_pressed, android.R.attr.state_enabled} : new int[]{});
                }
                lastY = event.getY();
                if (!scroller.isFinished()) {
                    scroller.abortAnimation();
                }
                if (scrollAnimator != null) {
                    scrollAnimator.cancel();
                    scrollAnimator = null;
                }
            } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                if (pressTab != null) {
                    if (pressTab.isPressed()) {
                        if (!horizontallySwiping && !verticallyScrolling && Math.abs(event.getX() - startX) > touchSlop) {
                            horizontallySwiping = true;
                        }
                        if (!verticallyScrolling && !horizontallySwiping && Math.abs(event.getY() - startY) > touchSlop) {
                            if (!scroller.isFinished()) {
                                scroller.abortAnimation();
                            }
                            if (scrollAnimator != null) {
                                scrollAnimator.cancel();
                                scrollAnimator = null;
                            }
                            verticallyScrolling = true;
                        }
                        if (tabsView != null && (verticallyScrolling || horizontallySwiping)) {
                            pressTab.setPressed(false);
                            pressTab.cancelDismissAnimator();
                        }
                    } else {
                        if (!pressTabClose && !horizontallySwiping && !verticallyScrolling && Math.abs(event.getX() - startX) > touchSlop) {
                            horizontallySwiping = true;
                        }
                        if (!pressTabClose && !verticallyScrolling && !horizontallySwiping && Math.abs(event.getY() - startY) > touchSlop) {
                            if (!scroller.isFinished()) {
                                scroller.abortAnimation();
                            }
                            if (scrollAnimator != null) {
                                scrollAnimator.cancel();
                                scrollAnimator = null;
                            }
                            verticallyScrolling = true;
                        }
                        if (pressTabClose) {
                            pressTabClose = pressTab.tabDrawable.closeRipple.getBounds().contains(
                                    (int) (event.getX() - pressTab.clickBounds.left),
                                    (int) (event.getY() - pressTab.clickBounds.top - AndroidUtilities.dp(24))
                            );
                            if (!pressTabClose) {
                                pressTab.tabDrawable.closeRipple.setState(new int[]{});
                            }
                        }
                    }
                    if (!pressTab.isPressed()) {
                        if (horizontallySwiping) {
                            pressTab.dismissProgress = (event.getX() - startX) / AndroidUtilities.dp(300);
                        } else if (verticallyScrolling) {
                            float deltaY = event.getY() - lastY;
                            if (offset < getScrollMin()) {
                                deltaY *= (1f - .5f * clamp((getScrollMin() - offset) / getScrollStep(), 1f, 0f));
                            }
                            setScrollOffset(clamp((getScrollOffset() * getScrollStep() - deltaY) / getScrollStep(), getScrollMax(), getScrollMin() - 1.4f * getScrollStep()));
                            invalidate();
                        }
                    }
                    invalidate();
                }
                if (hitCloseAllButton) {
                    hitCloseAllButton = closeAllButtonBounds.contains(event.getX(), event.getY());
                    invalidate();
                }
                lastY = event.getY();
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (pressTab != null) {
                    if (tabsView != null && Math.abs(pressTab.dismissProgress) > .4f) {
                        TabPreview tab = pressTab;
                        tabsView.removeTab(pressTab.tabData, true);
                        tab.animateDismiss(tab.dismissProgress < 0 ? -1f : 1f, () -> {
                            tabs.remove(tab);
                            scrollTo(clamp(offset, getScrollMax(false), getScrollMin(false)));
                            if (tabsView.getTabs().isEmpty()) {
                                closeTabsView();
                            }
                        });
                    } else {
                        pressTab.animateDismiss(0, null);
                        if (tabsView != null && pressTab.isPressed()) {
                            BottomSheetTabs.WebTabData tab = pressTab.tabData;
                            closeTabsView();
                            tabsView.openTab(tab);
                        } else if (verticallyScrolling) {
                            if (offset < getScrollMin() - getScrollWindow() * .15f) {
                                closeTabsView();
                            } else if (offset < getScrollMin()) {
                                scrollTo(getScrollMin());
                            } else {
                                velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
                                float velocityY = velocityTracker.getYVelocity();
                                if (Math.abs(velocityY) > minimumVelocity) {
                                    scroller.fling(0, (int) (getScrollOffset() * getScrollStep()), 0, (int) -velocityY, 0, 0, (int) (getScrollMin() * getScrollStep()), (int) (getScrollMax() * getScrollStep()), 0, (int) (.1f * getScrollStep()));
                                } else {
                                    scroller.startScroll(0, (int) (getScrollOffset() * getScrollStep()), 0, 0, 0);
                                }
                            }
                            velocityTracker.recycle();
                            velocityTracker = null;
                            postInvalidateOnAnimation();
                        }
                    }
                    pressTab.setPressed(false);
                    if (pressTabClose) {
                        pressTabClose = pressTab.tabDrawable.closeRipple.getBounds().contains(
                                (int) (event.getX() - pressTab.clickBounds.left),
                                (int) (event.getY() - pressTab.clickBounds.top - AndroidUtilities.dp(24))
                        );
                    }
                    if (pressTabClose) {
                        TabPreview tab = pressTab;
                        tabsView.removeTab(pressTab.tabData, true);
                        tab.animateDismiss(1f, () -> {
                            tabs.remove(tab);
                            scrollTo(clamp(offset, getScrollMax(false), getScrollMin(false)));
                            if (tabsView.getTabs().isEmpty()) {
                                closeTabsView();
                            }
                        });
                    }
                    pressTab.tabDrawable.closeRipple.setState(new int[]{});
                } else if (hitCloseAllButton && tabsView != null) {
                    tabsView.removeAll();
                    closeTabsView();
                } else if (distance(startX, startY, event.getX(), event.getY()) <= touchSlop
                        && !verticallyScrolling && !horizontallySwiping
                        && System.currentTimeMillis() - startTime <= ViewConfiguration.getTapTimeout() * 1.2f) {
                    closeTabsView();
                }
                pressTab = null;
                pressTabClose = false;
                hitCloseAllButton = false;
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                if (pressTab != null) {
                    pressTab.animateDismiss(0, null);
                    pressTab.setPressed(false);
                    pressTab.tabDrawable.closeRipple.setState(new int[]{});
                }
                pressTab = null;
                pressTabClose = false;
                hitCloseAllButton = false;
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
            }
            handled = true;
        }
        return handled;
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            setScrollOffset(scroller.getCurrY() / getScrollStep());
            postInvalidateOnAnimation();
        }
    }

    public float getScrollOffset() {
        return offset;
    }

    public void setScrollOffset(float offset) {
        this.offset = offset;
    }

    private float getScrollStep() {
        return AndroidUtilities.dp(200);
    }

    public float getScrollRange() {
        return getScrollRange(true);
    }

    public float getScrollRange(boolean animated) {
        float tabCount = 0;
        for (int i = 0; i < tabs.size(); ++i) {
            final TabPreview tab = tabs.get(i);
            tabCount += tab.tabDrawable.index >= 0 ? 1 : 0;
        }
        return animated ? animatedCount.set(tabCount) : tabCount;
    }

    public float getScrollWindow() {
        return Math.min(3, getScrollRange());
    }

    public float getScrollWindow(boolean animated) {
        return Math.min(3, getScrollRange(animated));
    }

    public float getScrollMin() {
        return getScrollMin(true);
    }

    public float getScrollMin(boolean animated) {
        return -getScrollWindow() / 3f * clamp(getScrollRange(animated), 1f, 0f);
    }

    public float getScrollMax() {
        return getScrollMax(true);
    }

    public float getScrollMax(boolean animated) {
        return getScrollRange(animated) - getScrollWindow(animated) - getScrollWindow(animated) / 3f * clamp(4f - getScrollRange(animated), .5f, 0f);
    }

    private TabPreview getTabAt(float x, float y) {
        if (openProgress < 1) {
            return null;
        }
        for (int i = tabs.size() - 1; i >= 0; --i) {
            TabPreview tab = tabs.get(i);
            if (Math.abs(tab.dismissProgress) < .4f && tab.clickBounds.contains(x, y)) {
                return tab;
            }
        }
        return null;
    }

    public void openTabsView() {
        if (tabsView == null || tabsView.getParent() == null) {
            return;
        }
        bringToFront();
        stopAnimations();
        actionBarLayout = (View) tabsView.getParent();
        if (actionBarLayout != null) {
            actionBarLayout.getLocationOnScreen(pos);
        } else {
            pos[0] = pos[1] = 0;
        }
        getLocationOnScreen(pos2);
        tabsViewBounds.set(
                pos[0] - pos2[0],
                pos[1] - pos2[1],
                pos[0] - pos2[0] + (actionBarLayout == null ? 0 : actionBarLayout.getWidth()),
                pos[1] - pos2[1] + (actionBarLayout == null ? 0 : actionBarLayout.getHeight())
        );
        prepareBlur(actionBarLayout);
        clearTabs();
        prepareTabs();
        if (tabs.isEmpty()) {
            return;
        }
        setVisibility(VISIBLE);
        animateOpen(true);
    }

    private void clearTabs() {
        tabs.clear();
    }

    private void prepareTabs() {
        ArrayList<BottomSheetTabs.WebTabData> tabData = tabsView.getTabs();
        ArrayList<BottomSheetTabs.TabDrawable> tabDrawables = tabsView.getTabDrawables();

        for (int i = tabData.size() - 1; i >= 0; --i) {
            BottomSheetTabs.WebTabData data = tabData.get(i);
            BottomSheetTabs.TabDrawable tabDrawable = null;
            for (int j = 0; j < tabDrawables.size(); ++j) {
                BottomSheetTabs.TabDrawable drawable = tabDrawables.get(j);
                if (drawable.tab == data) {
                    tabDrawable = drawable;
                    break;
                }
            }
            if (tabDrawable != null) {
                tabs.add(new TabPreview(this, data, tabDrawable));
            }
        }
        animatedCount.set(tabs.size(), true);
        setScrollOffset(getScrollMax());
    }

    public void closeTabsView() {
        animateOpen(false);
    }

    private void scrollTo(float offset) {
        if (scrollAnimator != null) {
            scrollAnimator.cancel();
            scrollAnimator = null;
        }
        scrollAnimator = ValueAnimator.ofFloat(this.offset, offset);
        scrollAnimator.addUpdateListener(animation -> {
            this.offset = (float) animation.getAnimatedValue();
            invalidate();
        });
        scrollAnimator.setDuration(250);
        scrollAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        scrollAnimator.start();
    }

    public boolean openSheet(Sheet sheet, BottomSheetTabs.WebTabData tab, Runnable whenOpened) {
        if (sheet == null || tabsView == null || !(sheet.getWindowView() instanceof SheetView)) {
            return false;
        }
        if (dismissingSheet != null || openingSheet != null) {
            if (sheetAnimator != null) {
                sheetAnimator.end();
                sheetAnimator = null;
            }
        }

        openingSheet = sheet;
        ((SheetView) sheet.getWindowView()).setDrawingFromOverlay(true);
        invalidate();

        if (sheetAnimator != null) {
            sheetAnimator.cancel();
            sheetAnimator = null;
        }

        openingTab = tabsView.findTabDrawable(tab);
        float scrollWindow = Math.max(.001f, getScrollWindow());
        openingTabScroll = openingTab != null
                ? ((getScrollRange() - 1 - openingTab.getPosition()) - Math.max(getScrollMin(), getScrollOffset())) / scrollWindow
                : 0f;
        openingProgress = 0f;

        sheetAnimator = ValueAnimator.ofFloat(0f, 1f);
        sheetAnimator.addUpdateListener(animation -> {
            openingProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        sheetAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (sheetAnimator == animation) {
                    sheetAnimator = null;
                }
                ((SheetView) sheet.getWindowView()).setDrawingFromOverlay(false);
                sheet.getWindowView().post(() -> {
                    openingSheet = null;
                    openingTab = null;
                    openingProgress = 0f;
                    if (!isOpen) {
                        clearTabs();
                        setVisibility(GONE);
                    }
                    invalidate();
                });

                if (whenOpened != null) {
                    whenOpened.run();
                }
            }
        });
        AndroidUtilities.applySpring(sheetAnimator, 260, 30, 1, 0);
        sheetAnimator.start();
        invalidate();
        return true;
    }

    public boolean dismissSheet(Sheet sheet) {
        if (sheet == null || tabsView == null || !(sheet.getWindowView() instanceof SheetView)) {
            return false;
        }
        if (dismissingSheet != null || openingSheet != null) {
            if (sheetAnimator != null) {
                sheetAnimator.end();
                sheetAnimator = null;
            }
        }

        dismissingSheet = sheet;
        sheet.setLastVisible(false);
        BottomSheetTabs.WebTabData tab = sheet.saveState();
        dismissingTab = tabsView.pushTab(tab);
        tabsView.drawTabs = true;
        tabsView.invalidate();
        setVisibility(VISIBLE);
        bringToFront();
        post(() -> {
            if (sheet.getWindowView() instanceof SheetView) {
                ((SheetView) sheet.getWindowView()).setDrawingFromOverlay(true);
            }
        });
        dismissProgress = 0f;
        invalidate();

        sheetAnimator = ValueAnimator.ofFloat(0f, 1f);
        sheetAnimator.addUpdateListener(animation -> {
            dismissProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        sheetAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (sheetAnimator == animation) {
                    sheetAnimator = null;
                }
                sheet.capturePreviewBitmap(tab, () -> {
                    ((SheetView) sheet.getWindowView()).setDrawingFromOverlay(false);
                    sheet.release();
                });
                dismissingSheet = null;
                dismissingTab = null;
                if (!isOpen && openingSheet == null) {
                    setVisibility(GONE);
                }
                invalidate();
            }
        });
        AndroidUtilities.applySpring(sheetAnimator, 220, 30, 1, 0);
        sheetAnimator.setDuration((long) (sheetAnimator.getDuration() * 1.1f));
        sheetAnimator.start();
        return true;
    }

    private void animateOpen(boolean open) {
        if (isOpen == open) {
            return;
        }
        if (openAnimator != null) {
            openAnimator.cancel();
        }

        isOpen = open;
        if (tabsView != null) {
            tabsView.drawTabs = false;
            tabsView.invalidate();
        }
        invalidate();
        openAnimator = ValueAnimator.ofFloat(openProgress, open ? 1f : 0f);
        openAnimator.addUpdateListener(animation -> {
            openProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        openAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (tabsView != null) {
                    tabsView.drawTabs = true;
                    tabsView.invalidate();
                }
                openProgress = isOpen ? 1f : 0f;
                if (!isOpen && openingSheet == null) {
                    clearTabs();
                    setVisibility(GONE);
                }
                invalidate();
            }
        });
        openAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        openAnimator.setDuration(320);
        openAnimator.start();
    }

    public boolean onBackPressed() {
        if (isOpen) {
            closeTabsView();
            return true;
        }
        return false;
    }

    public void stopAnimations() {
        if (sheetAnimator != null) {
            sheetAnimator.cancel();
            sheetAnimator = null;
        }
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        drawDismissingTab(canvas);
        drawTabsPreview(canvas);
    }

    private void drawDismissingTab(Canvas canvas) {
        if (dismissingSheet == null || tabsView == null || !(dismissingSheet.getWindowView() instanceof SheetView)) {
            return;
        }

        getLocationOnScreen(pos2);
        tabsView.getLocationOnScreen(pos);
        tabsView.getTabBounds(rect, 0);
        rect.offset(pos[0] - pos2[0], pos[1] - pos2[1]);

        canvas.save();
        canvas.clipRect(0, 0, getMeasuredWidth(), getMeasuredHeight() - navigationBarInset);
        float radius = ((SheetView) dismissingSheet.getWindowView()).drawInto(canvas, rect, dismissProgress, clipRect, dismissProgress, false);

        if (dismissingTab != null) {
            clipPath.rewind();
            clipPath.addRoundRect(clipRect, radius, radius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clipPath);
            float y = clipRect.top - AndroidUtilities.dp(50) * (1f - dismissProgress);
            rect.set(clipRect.left, y, clipRect.right, y + AndroidUtilities.dp(50));
            tabsView.setupTab(dismissingTab);
            dismissingTab.draw(canvas, rect, radius, dismissProgress, 1f);
            canvas.restore();
        }
        canvas.restore();
    }

    private void drawTabsPreview(Canvas canvas) {
        if (openProgress <= 0 && openingProgress <= 0) {
            return;
        }

        canvas.save();
        if (actionBarLayout != null) {
            actionBarLayout.getLocationOnScreen(pos);
            getLocationOnScreen(pos2);
            tabsViewBounds.set(
                    pos[0] - pos2[0],
                    pos[1] - pos2[1],
                    pos[0] - pos2[0] + actionBarLayout.getWidth(),
                    pos[1] - pos2[1] + actionBarLayout.getHeight()
            );
        } else {
            pos[0] = pos[1] = 0;
            tabsViewBounds.set(0, 0, 0, 0);
        }
        canvas.clipRect(tabsViewBounds);
        canvas.translate(tabsViewBounds.left, tabsViewBounds.top);

        final float thisWidth = tabsViewBounds.width();
        final float thisHeight = tabsViewBounds.height();

        if (blurBitmap != null) {
            blurMatrix.reset();
            float scale = thisWidth / blurBitmap.getWidth();
            blurMatrix.postScale(scale, scale);
            blurBitmapShader.setLocalMatrix(blurMatrix);

            blurBitmapPaint.setAlpha((int) (0xFF * openProgress));
            canvas.drawRect(0, 0, thisWidth, thisHeight, blurBitmapPaint);
        }

        canvas.saveLayerAlpha(0, 0, thisWidth, thisHeight, 0xFF, Canvas.ALL_SAVE_FLAG);

        final float paddingTop = AndroidUtilities.statusBarHeight + AndroidUtilities.dp(40) + AndroidUtilities.dp(55);
        final float paddingBottom = AndroidUtilities.dp(68);

        final int width = (int) Math.min(AndroidUtilities.dp(340), thisWidth * .95f);
        final int height = (int) (thisHeight * .75f);
        final float cx = thisWidth / 2f;
        final float count = getScrollRange(true);
        final float scrollWindow = Math.max(.001f, getScrollWindow(true));
        final float open = lerp(0, 1f - clamp((getScrollMin(true) - getScrollOffset()) / (scrollWindow * .15f) * .2f, 1f, 0f), openProgress);

        int openingTabIndex = -1;
        for (int i = 0; i < tabs.size() + 1; i++) {
            final TabPreview tab;
            if (i == tabs.size()) {
                if (openingTabIndex >= 0 && openingProgress > .5f) {
                    tab = tabs.get(openingTabIndex);
                } else {
                    continue;
                }
            } else {
                tab = tabs.get(i);
            }
            if (i < tabs.size() && tab.tabDrawable == openingTab && openingProgress > .5f) {
                openingTabIndex = i;
                continue;
            }

            final float tabOpen = tab.tabDrawable == openingTab ? 1f : open;
            final float opening = tab.tabDrawable == openingTab ? openingProgress : 0f;
            final float position = count - 1 - tab.tabDrawable.getPosition();
            final float scroll = tab.tabDrawable == openingTab ? openingTabScroll : (position - Math.max(getScrollMin(true), getScrollOffset())) / scrollWindow;

            float top = paddingTop + AndroidUtilities.dp(6) * Math.min(5, position);
            float bottom = thisHeight - paddingBottom - height * .26f;
            float y = top + (bottom - top) * scroll;

            rect2.set(cx - width / 2f, y, cx + width / 2f, y + height);
            boolean drawSimple = tab.tabDrawable != openingTab && (rect2.top > thisHeight || rect2.bottom < 0 || open < .1f) && position < count - 3;

            if (openingSheet != null && tab.tabDrawable == openingTab && openingSheet.getWindowView() instanceof SheetView) {
                rect.set(((SheetView) openingSheet.getWindowView()).getRect());
                lerpCentered(rect2, rect, opening, rect2);
            } else {
                tabsView.getTabBounds(rect, clamp(tab.tabDrawable.getPosition(), 1f, 0f));
                rect.offset(tabsView.getX(), tabsView.getY());
                lerpCentered(rect, rect2, open, rect2);
            }

            tabsView.setupTab(tab.tabDrawable);

            if (tab.tabDrawable != openingTab && (rect2.top > thisHeight || rect2.bottom < 0)) {
                continue;
            }

            canvas.save();
            tab.clickBounds.set(rect2);

            tab.matrix.reset();

            final float sourceHeight = 1f;
            tab.src[0] = rect2.left;
            tab.src[1] = rect2.top;
            tab.src[2] = rect2.right;
            tab.src[3] = rect2.top;
            tab.src[4] = rect2.right;
            tab.src[5] = rect2.top + rect2.height() * sourceHeight;
            tab.src[6] = rect2.left;
            tab.src[7] = rect2.top + rect2.height() * sourceHeight;

            tab.dst[0] = rect2.left;
            tab.dst[1] = rect2.top;
            tab.dst[2] = rect2.right;
            tab.dst[3] = rect2.top;
            final float scale1 = .83f;
            final float scale2 = .6f;
            tab.dst[4] = rect2.centerX() + rect2.width() / 2f * lerp(1f, scale1, tabOpen * (1f - opening));
            tab.dst[5] = rect2.top + rect2.height() * sourceHeight * lerp(1f, scale2, tabOpen * (1f - opening));
            tab.dst[6] = rect2.centerX() - rect2.width() / 2f * lerp(1f, scale1, tabOpen * (1f - opening));
            tab.dst[7] = rect2.top + rect2.height() * sourceHeight * lerp(1f, scale2, tabOpen * (1f - opening));

            tab.matrix.setPolyToPoly(tab.src, 0, tab.dst, 0, 4);
            canvas.concat(tab.matrix);

            tab.draw(
                    canvas,
                    rect2,
                    drawSimple,
                    tab.tabDrawable == openingTab ? 1f : lerp(tab.tabDrawable.getAlpha(), 1f, openProgress),
                    tab.tabDrawable == openingTab ? 1f : tabOpen * (1f - opening),
                    opening,
                    lerp(clamp(position - count + 2, 1f, 0f), 1f, clamp((tabOpen - .1f) / .8f, 1f, 0f))
            );

            if (openingSheet != null && tab.tabDrawable == openingTab && openingSheet.getWindowView() instanceof SheetView) {
                ((SheetView) openingSheet.getWindowView()).drawInto(canvas, rect2, 1f, clipRect, opening, true);
            }

            canvas.restore();
        }
        canvas.save();
        if (gradientClip == null) {
            gradientClip = new GradientClip();
        }
        rect.set(0, 0, thisWidth, paddingTop);
        gradientClip.draw(canvas, rect, true, openProgress);
        canvas.restore();
        canvas.restore();
        canvas.restore();
        drawCloseAllButton(canvas, tabsViewBounds.left, tabsViewBounds.top, thisWidth, paddingTop);
    }

    private void drawCloseAllButton(Canvas canvas, float left, float top, float width, float paddingTop) {
        String text = getContext().getString(R.string.app_browser_close_all_tabs);
        float buttonWidth = closeAllTextPaint.measureText(text) + AndroidUtilities.dp(24);
        float centerY = top + paddingTop - AndroidUtilities.dp(75 + 20) / 2f;
        closeAllButtonBounds.set(
                left + (width - buttonWidth) / 2f,
                centerY - AndroidUtilities.dp(14),
                left + (width + buttonWidth) / 2f,
                centerY + AndroidUtilities.dp(14)
        );
        closeAllButtonPaint.setColor(hitCloseAllButton
                ? (Theme.isCurrentThemeDark() ? 0x33ffffff : 0x44000000)
                : (Theme.isCurrentThemeDark() ? 0x20ffffff : 0x2e000000));
        closeAllButtonPaint.setAlpha((int) (Color.alpha(closeAllButtonPaint.getColor()) * openProgress));
        canvas.drawRoundRect(closeAllButtonBounds, AndroidUtilities.dp(64), AndroidUtilities.dp(64), closeAllButtonPaint);

        closeAllTextPaint.setAlpha((int) (0xFF * openProgress));
        Paint.FontMetrics fontMetrics = closeAllTextPaint.getFontMetrics();
        float baseline = closeAllButtonBounds.centerY() - (fontMetrics.ascent + fontMetrics.descent) / 2f;
        canvas.drawText(text, closeAllButtonBounds.left + AndroidUtilities.dp(12), baseline, closeAllTextPaint);
    }

    private void prepareBlur(View view) {
        AndroidUtilities.makingGlobalBlurBitmap = true;
        blurBitmap = AndroidUtilities.makeBlurBitmap(view, 14, 14);
        AndroidUtilities.makingGlobalBlurBitmap = false;
        if (blurBitmap == null) {
            blurBitmapShader = null;
            blurBitmapPaint = null;
            blurMatrix = null;
            return;
        }

        blurBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blurBitmapPaint.setShader(blurBitmapShader = new BitmapShader(blurBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        ColorMatrix colorMatrix = new ColorMatrix();
        AndroidUtilities.adjustSaturationColorMatrix(colorMatrix, Theme.isCurrentThemeDark() ? .08f : .25f);
        blurBitmapPaint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
        blurMatrix = new Matrix();
    }

    private static class TabPreview {

        public final RectF clickBounds = new RectF();
        public final View parentView;
        public final BottomSheetTabs.WebTabData tabData;
        public final BottomSheetTabs.TabDrawable tabDrawable;

        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix matrix = new Matrix();
        private final float[] src = new float[8];
        private final float[] dst = new float[8];

        public float dismissProgress = 0f;
        private ValueAnimator dismissAnimator;
        private final ButtonBounce bounce;

        private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final RectF tabBounds = new RectF();
        private final Path clipPath = new Path();

        private final RadialGradient gradient = new RadialGradient(0, 0, 255, new int[]{0x00000000, 0x30000000}, new float[]{.5f, 1}, Shader.TileMode.CLAMP);
        private final Matrix gradientMatrix = new Matrix();
        private final Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public TabPreview(View parentView, BottomSheetTabs.WebTabData tabData, BottomSheetTabs.TabDrawable tabDrawable) {
            this.parentView = parentView;
            this.tabData = tabData;
            this.tabDrawable = tabDrawable;
            this.bounce = new ButtonBounce(parentView);

            backgroundPaint.setColor(tabData.backgroundColor);
        }

        public void cancelDismissAnimator() {
            if (dismissAnimator != null) {
                dismissAnimator.cancel();
            }
        }

        public void animateDismiss(float dismissTo, Runnable onEnd) {
            cancelDismissAnimator();
            dismissAnimator = ValueAnimator.ofFloat(dismissProgress, dismissTo);
            dismissAnimator.addUpdateListener(animation -> {
                dismissProgress = (float) animation.getAnimatedValue();
                parentView.invalidate();
            });
            dismissAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    dismissProgress = dismissTo;
                    if (parentView != null) {
                        parentView.invalidate();
                    }
                    if (onEnd != null) {
                        onEnd.run();
                    }
                }
            });
            if (Math.abs(dismissTo) < .1f) {
                AndroidUtilities.applySpring(dismissAnimator, 285, 20);
            } else {
                dismissAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            }
            dismissAnimator.start();
        }

        public boolean isPressed() {
            return bounce.isPressed();
        }

        public void setPressed(boolean pressed) {
            bounce.setPressed(pressed);
        }

        public void draw(Canvas canvas, RectF bounds, boolean simple, float alpha, float expandProgress, float openingProgress, float contentAlpha) {
            alpha *= clamp(1f - ((Math.abs(dismissProgress) - .3f) / .7f), 1f, 0f);
            if (alpha <= 0) {
                return;
            }

            float tabScaleY = lerp(1f, 1.3f, expandProgress * (1f - openingProgress));
            final float tabTranslateY = openingProgress * (AndroidUtilities.statusBarHeight + AndroidUtilities.dp(56) - AndroidUtilities.dp(50));
            canvas.save();
            canvas.rotate(dismissProgress * 20, bounds.centerX() + AndroidUtilities.dp(50) * dismissProgress, bounds.bottom + AndroidUtilities.dp(350));
            final float scale = bounce.getScale(.01f);
            canvas.scale(scale, scale, bounds.centerX(), bounds.centerY());

            final float radius = lerp(AndroidUtilities.dp(10), AndroidUtilities.dp(6), expandProgress);
            if (simple) {
                shadowPaint.setColor(Color.TRANSPARENT);
                shadowPaint.setShadowLayer(AndroidUtilities.dp(30), 0, AndroidUtilities.dp(10), multAlpha(0x20000000, alpha * expandProgress * (1f - openingProgress)));
                canvas.drawRoundRect(bounds, radius, radius, shadowPaint);
                backgroundPaint.setAlpha((int) (0xFF * alpha));
                canvas.drawRoundRect(bounds, radius, radius, backgroundPaint);
                canvas.restore();
                return;
            }

            clipPath.rewind();
            clipPath.addRoundRect(bounds, radius, radius, Path.Direction.CW);
            canvas.save();
            shadowPaint.setColor(Color.TRANSPARENT);
            shadowPaint.setShadowLayer(AndroidUtilities.dp(30), 0, AndroidUtilities.dp(10), multAlpha(0x20000000, alpha * expandProgress * (1f - openingProgress)));
            canvas.drawPath(clipPath, shadowPaint);
            canvas.clipPath(clipPath);

            backgroundPaint.setAlpha((int) (0xFF * alpha * expandProgress));
            canvas.drawRoundRect(bounds, radius, radius, backgroundPaint);

            canvas.save();
            canvas.translate(bounds.left, bounds.top + AndroidUtilities.dp(50) * tabScaleY + tabTranslateY);
            canvas.scale(1f, lerp(1f, 1.25f, expandProgress * (1f - openingProgress)));
            if (tabData.previewBitmap != null) {
                final float bitmapScale = bounds.width() / tabData.previewBitmap.getWidth();
                canvas.scale(bitmapScale, bitmapScale);
                bitmapPaint.setAlpha((int) (0xFF * alpha * expandProgress));
                canvas.drawBitmap(tabData.previewBitmap, 0, 0, bitmapPaint);
            }
            canvas.restore();

            canvas.save();
            gradientPaint.setAlpha((int) (0xFF * alpha * expandProgress * (1f - openingProgress)));
            gradientMatrix.reset();
            final float gradientScale = bounds.height() / 255f;
            gradientMatrix.postScale(gradientScale, gradientScale);
            gradientMatrix.postTranslate(bounds.centerX(), bounds.top);
            gradient.setLocalMatrix(gradientMatrix);
            gradientPaint.setShader(gradient);
            canvas.drawRect(bounds, gradientPaint);
            canvas.restore();

            tabBounds.set(bounds);
            tabBounds.bottom = tabBounds.top + Math.min(bounds.height(), AndroidUtilities.dp(50));
            tabBounds.offset(0, tabTranslateY);
            tabDrawable.setExpandProgress(expandProgress);
            canvas.scale(1f, tabScaleY, tabBounds.centerX(), tabBounds.top);
            tabDrawable.draw(canvas, tabBounds, radius, alpha * alpha, contentAlpha);

            canvas.restore();
            canvas.restore();
        }
    }

    private static class ButtonBounce {
        private final View view;
        private ValueAnimator animator;
        private boolean pressed;
        private float pressedT;

        ButtonBounce(View view) {
            this.view = view;
        }

        void setPressed(boolean pressed) {
            if (this.pressed == pressed) {
                return;
            }
            this.pressed = pressed;
            if (animator != null) {
                animator.cancel();
            }
            animator = ValueAnimator.ofFloat(pressedT, pressed ? 1f : 0f);
            animator.addUpdateListener(animation -> {
                pressedT = (float) animation.getAnimatedValue();
                view.invalidate();
            });
            animator.setInterpolator(pressed ? CubicBezierInterpolator.DEFAULT : CubicBezierInterpolator.EASE_OUT_QUINT);
            animator.setDuration(pressed ? 60 : 350);
            animator.start();
        }

        boolean isPressed() {
            return pressed;
        }

        float getScale(float diff) {
            return (1f - diff) + diff * (1f - pressedT);
        }
    }

    private static class AnimatedFloat {
        private final View view;
        private ValueAnimator animator;
        private boolean initialized;
        private float value;
        private float targetValue;

        AnimatedFloat(View view) {
            this.view = view;
        }

        float set(float target) {
            return set(target, false);
        }

        float set(float target, boolean immediate) {
            if (!initialized || immediate) {
                initialized = true;
                if (animator != null) {
                    animator.cancel();
                    animator = null;
                }
                value = target;
                targetValue = target;
                return value;
            }
            if (Math.abs(value - target) < .001f) {
                targetValue = target;
                return value;
            }
            if (animator == null || Math.abs(targetValue - target) >= .001f) {
                if (animator != null) {
                    animator.cancel();
                }
                targetValue = target;
                animator = ValueAnimator.ofFloat(value, target);
                animator.addUpdateListener(animation -> {
                    value = (float) animation.getAnimatedValue();
                    view.invalidate();
                });
                animator.setDuration(350);
                animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                animator.start();
            }
            return value;
        }
    }

    private static float distance(float startX, float startY, float endX, float endY) {
        return (float) Math.hypot(endX - startX, endY - startY);
    }

    private static float lerp(float from, float to, float progress) {
        return from + (to - from) * progress;
    }

    private static void lerpCentered(RectF from, RectF to, float progress, RectF out) {
        final float cx = lerp(from.centerX(), to.centerX(), progress);
        final float cy = lerp(from.centerY(), to.centerY(), progress);
        final float halfWidth = lerp(from.width(), to.width(), Math.min(1, progress)) / 2f;
        final float halfHeight = lerp(from.height(), to.height(), Math.min(1, progress)) / 2f;
        out.set(cx - halfWidth, cy - halfHeight, cx + halfWidth, cy + halfHeight);
    }

    private static float clamp(float value, float max, float min) {
        if (Float.isNaN(value)) {
            return min;
        }
        if (Float.isInfinite(value)) {
            return max;
        }
        return Math.max(Math.min(value, max), min);
    }

    private static int multAlpha(int color, float multiply) {
        if (multiply == 1f) {
            return color;
        }
        return ColorUtils.setAlphaComponent(color, Math.max(0, Math.min(0xFF, (int) (Color.alpha(color) * multiply))));
    }
}
