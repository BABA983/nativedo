// UPSTREAM-LEVEL: L2
// UPSTREAM-PATH:  org/telegram/ui/ArticleViewer.java; org/telegram/ui/ActionBar/BaseFragment.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// SUBSYSTEMS: attached-sheet=L2, bottom-up-animation=L0, swipe-back-gesture=L0, browser-lifecycle=L1
// HOST-WIRING:
//   - Attaches the browser window directly to NativeDo's ActionBarLayout ViewGroup.
//   - Uses NativeDo WebActionBar and BrowserWebViewContainer for page UI.
//   - WebView edge-detection replaces upstream pages[0].swipeContainer.allowingScroll() check.
// ADAPTED:
//   - Keeps Telegram ArticleViewer.Sheet open/dismiss timing and BaseFragment.AttachedSheet shape.
//   - Keeps Telegram's dismissingIntoTabs state: sheet-to-tab collapse is drawn by BottomSheetTabsOverlay
//     instead of translating the live sheet window down.
//   - Collapses into NativeDo BottomSheetTabs while preserving BrowserSheet-owned WebView state in memory.
//   - Implements BottomSheetTabsOverlay.SheetView for Telegram-style sheet-to-tab collapse drawing.
//   - Wraps NativeDo WebView + WebActionBar in ArticleViewer.Sheet.containerView so drawInto()
//     can use Telegram's sheet-to-tab content offset model.
//   - Maps ArticleViewer.PageLayout.getListTop() to NativeDo's WebActionBar height because
//     NativeDo has not ported Telegram's WebViewSwipeContainer vertical sheet offset yet.
//   - Uses Telegram ArticleViewer browser surface colors instead of NativeDo's parchment content
//     background so sheet-to-tab collapse fades over the same white/black shell.
//   - Keeps the WindowView fixed and applies open/dismiss/back motion while drawing, matching
//     ArticleViewer.Sheet.WindowView instead of translating the host view.
//   - Resets overlay drawing and gesture state when the same BrowserSheet instance is reopened
//     from NativeDo's in-memory tab list.
//   - Forwards BaseFragment/BottomSheetTabsOverlay setLastVisible() to BrowserWebViewContainer,
//     matching ArticleViewer.PageLayout keyboard focus ownership.
//   - Ports ArticleViewer$WindowView#handleTouchEvent with upstream L0 constants
//     (0.4cm slop, dx/3 > dy, velX>=3500 fast-flick, 0.3*width commit, velX<2500||velX<velY back,
//      duration max(420f/width*dist, 250), EASE_OUT_QUINT). The ported gesture only drives
//      sheet dismissal because NativeDo has no Telegram page stack.
// OMITTED:
//   - Telegram page stack movingPage branch, nested scroll dismissal,
//     BottomSheetTabDialog, RenderNode preview-node capture, nav/status color orchestration, and process save/restore.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's org/telegram/ui/ArticleViewer.java.
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
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.net.Uri;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.core.graphics.ColorUtils;

import io.github.baba983.nativedo.R;
import io.github.baba983.nativedo.core.AndroidUtilities;
import io.github.baba983.nativedo.ui.actionbar.ActionBarLayout;
import io.github.baba983.nativedo.ui.actionbar.BaseFragment;
import io.github.baba983.nativedo.ui.actionbar.BottomSheetTabs;
import io.github.baba983.nativedo.ui.actionbar.BottomSheetTabsOverlay;
import io.github.baba983.nativedo.ui.actionbar.Theme;
import io.github.baba983.nativedo.ui.components.AnimatedFloat;
import io.github.baba983.nativedo.ui.components.CubicBezierInterpolator;
import io.github.baba983.nativedo.ui.components.LayoutHelper;

public class BrowserSheet implements BaseFragment.AttachedSheet, BottomSheetTabsOverlay.Sheet {

    private BaseFragment fragment;
    private final WindowView windowView;
    private final FrameLayout containerView;
    private final WebActionBar webActionBar;
    private final BrowserWebViewContainer webViewContainer;
    private final BrowserSearchPanel searchPanel;
    private final BrowserAddressPanel addressPanel;
    private final String initialUrl;

    private boolean dismissing;
    private boolean dismissingIntoTabs;
    private boolean released;
    private float openProgress;
    private float dismissProgress;
    private float backProgress;
    private boolean closeAnimationInProgress;
    private ValueAnimator openAnimator;
    private ValueAnimator dismissAnimator;
    private ValueAnimator backAnimator;
    private Runnable onDismissListener;
    private String lastHost;
    private boolean attachedToActionBar;
    private boolean fullyAttachedToActionBar;
    private boolean wasFullyVisible;

    public BrowserSheet(BaseFragment fragment, String initialUrl) {
        this.fragment = fragment;
        this.initialUrl = initialUrl;
        Context context = fragment.getContext();
        windowView = new WindowView(context);
        containerView = new FrameLayout(context);
        containerView.setClipChildren(false);
        containerView.setClipToPadding(false);
        windowView.addView(containerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.START));

        webActionBar = new WebActionBar(context, new WebActionBar.Delegate() {
            @Override
            public void onBackPressed() {
                onAttachedBackPressed();
            }

            @Override
            public void onAddressSubmitted(String value) {
                loadAddressValue(value);
            }

            @Override
            public void onMenuItem(int id) {
                handleMenuItem(id);
            }

            @Override
            public void onSearchUpdated(String text) {
                processSearch(text);
            }

            @Override
            public void onAddressingProgress(float progress) {
                if (addressPanel != null) {
                    addressPanel.setOpenProgress(progress);
                }
            }

            @Override
            public void onAddressColorsChanged(int backgroundColor, int textColor) {
                if (addressPanel != null) {
                    addressPanel.setColors(backgroundColor, textColor);
                }
            }

            @Override
            public void onTitleClicked() {
                if (webActionBar.isAddressing()) return;
                CharSequence titleCs = webActionBar.getTitle();
                String title = titleCs == null ? "" : titleCs.toString();
                String url = webViewContainer.getCurrentUrl();
                android.graphics.Bitmap favicon = webViewContainer.getFavicon();
                if (addressPanel != null) {
                    addressPanel.setCurrent(
                        title,
                        url == null ? "" : url,
                        favicon,
                        () -> {
                            webActionBar.showAddress(url == null ? "" : url, true);
                        },
                        v -> {
                            webActionBar.showAddress(false, true);
                            copyCurrentLink();
                        }
                    );
                }
                webActionBar.showAddress("", true);
            }
        });
        webActionBar.setBackgroundColor(getActionBarColor());
        webActionBar.setTitle(titleFromUrl(initialUrl));
        webActionBar.setCurrentUrl(initialUrl);
        lastHost = hostOf(initialUrl);

        webViewContainer = new BrowserWebViewContainer(context, new BrowserWebViewContainer.Delegate() {
            @Override
            public void onTitleChanged(String title) {
                if (!dismissing && title != null && title.trim().length() > 0) {
                    webActionBar.setTitle(title);
                }
            }

            @Override
            public void onUrlChanged(String url, boolean canGoBack, boolean canGoForward) {
                String currentHost = hostOf(url);
                if (lastHost != null && currentHost != null && !currentHost.equals(lastHost)) {
                    // Navigation to a different site: cross-slide the title slot.
                    webActionBar.setNextTitle(titleFromUrl(url), url == null ? "" : url);
                    webActionBar.swap(true);
                } else {
                    webActionBar.setCurrentUrl(url);
                    if (webActionBar.getTitle() == null || webActionBar.getTitle().length() == 0) {
                        webActionBar.setTitle(titleFromUrl(url));
                    }
                }
                lastHost = currentHost;
            }

            @Override
            public void onProgressChanged(int progress, boolean visible) {
                webActionBar.setProgress(progress, visible);
            }
        });

        int actionBarHeight = webActionBar.getCurrentActionBarHeight();
        FrameLayout.LayoutParams contentLayoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.START);
        contentLayoutParams.topMargin = actionBarHeight;
        containerView.addView(webViewContainer, contentLayoutParams);
        containerView.addView(webActionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, actionBarHeight, Gravity.TOP | Gravity.START));

        addressPanel = new BrowserAddressPanel(context);
        FrameLayout.LayoutParams addressPanelLp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.START);
        addressPanelLp.topMargin = actionBarHeight;
        containerView.addView(addressPanel, addressPanelLp);

        searchPanel = new BrowserSearchPanel(context);
        searchPanel.setCallback(new BrowserSearchPanel.Callback() {
            @Override
            public void onFindPrevious() {
                webViewContainer.findNext(false);
            }

            @Override
            public void onFindNext() {
                webViewContainer.findNext(true);
            }
        });
        containerView.addView(searchPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 51, Gravity.BOTTOM | Gravity.START));
        searchPanel.attachKeyboardTracking(windowView);

        webViewContainer.loadUrl(initialUrl);
    }

    public void show() {
        if (dismissing || released) {
            return;
        }
        attachInternal();
        animateOpen(true, true, null);
    }

    public void attachInternal(BaseFragment fragment) {
        if (fragment == null) {
            return;
        }
        this.fragment = fragment;
        attachInternal();
    }

    private void attachInternal() {
        ViewGroup parent = getParentView();
        if (parent == null) {
            return;
        }
        released = false;
        backProgress = 0f;
        dismissingIntoTabs = false;
        closeAnimationInProgress = false;
        removeFromParent(windowView);
        parent.addView(windowView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.START));
        parent.bringChildToFront(windowView);
        if (parent instanceof ActionBarLayout) {
            BottomSheetTabsOverlay overlay = ((ActionBarLayout) parent).getBottomSheetTabsOverlay();
            if (overlay != null && overlay.getVisibility() == View.VISIBLE) {
                overlay.bringToFront();
            }
        }
        webViewContainer.onResume();
    }

    @Override
    public View getWindowView() {
        return windowView;
    }

    @Override
    public boolean isShown() {
        return !dismissing && !released && openProgress > 0.5f && windowView.isAttachedToWindow();
    }

    @Override
    public void dismiss() {
        dismiss(false);
    }

    @Override
    public void dismiss(boolean tabs) {
        if (dismissing) {
            return;
        }
        dismissing = true;
        dismissingIntoTabs = tabs;
        checkFullyVisible();
        if (tabs && fragment.getParentLayout() instanceof ActionBarLayout) {
            BottomSheetTabsOverlay overlay = ((ActionBarLayout) fragment.getParentLayout()).getBottomSheetTabsOverlay();
            if (overlay != null && overlay.dismissSheet(this)) {
                return;
            }
        }
        boolean collapseToTabs = tabs && pushTab();
        animateDismiss(true, true, () -> {
            release();
            if (!collapseToTabs) {
                destroy();
            }
        });
    }

    @Override
    public void release() {
        if (released) {
            return;
        }
        released = true;
        if (webActionBar.isSearching()) {
            webActionBar.showSearch(false, false);
        }
        if (webActionBar.isAddressing()) {
            webActionBar.showAddress(false, false);
        }
        searchPanel.show(false, false);
        searchPanel.detachKeyboardTracking(windowView);
        webViewContainer.onPause();
        fragment.removeSheet(this);
        removeFromParent(windowView);
        if (onDismissListener != null) {
            onDismissListener.run();
            onDismissListener = null;
        }
    }

    private void destroy() {
        webViewContainer.destroyWebView();
    }

    public void destroyFromTabs() {
        release();
        destroy();
    }

    @Override
    public boolean isFullyVisible() {
        return fullyAttachedToActionBar && !dismissing && !dismissingIntoTabs && !released && openProgress >= 1f && dismissProgress <= 0f && backProgress <= 0f;
    }

    @Override
    public boolean attachedToParent() {
        return windowView.isAttachedToWindow();
    }

    @Override
    public boolean onAttachedBackPressed() {
        if (webActionBar.isSearching()) {
            webActionBar.showSearch(false, true);
            return true;
        }
        if (webActionBar.isAddressing()) {
            webActionBar.closeAddress(true);
            return true;
        }
        if (webViewContainer.canGoBack()) {
            webViewContainer.goBack();
            return true;
        }
        dismiss(false);
        return true;
    }

    @Override
    public boolean showDialog(Dialog dialog) {
        return false;
    }

    @Override
    public void setKeyboardHeightFromParent(int keyboardHeight) {
        searchPanel.setKeyboardHeight(keyboardHeight);
    }

    @Override
    public boolean isAttachedLightStatusBar() {
        return !Theme.isCurrentThemeDark();
    }

    @Override
    public int getNavigationBarColor(int color) {
        final float open = dismissingIntoTabs ? 0f : Math.min(openProgress, 1f - dismissProgress) * (1f - backProgress);
        return ColorUtils.blendARGB(color, getBackgroundColor(), open);
    }

    @Override
    public void setOnDismissListener(Runnable onDismiss) {
        onDismissListener = onDismiss;
    }

    @Override
    public void setLastVisible(boolean lastVisible) {
        webViewContainer.setKeyboardFocusable(lastVisible);
    }

    public void reset() {
        dismissing = false;
        dismissingIntoTabs = false;
        closeAnimationInProgress = false;
        if (openAnimator != null) {
            openAnimator.cancel();
            openAnimator = null;
        }
        if (dismissAnimator != null) {
            dismissAnimator.cancel();
            dismissAnimator = null;
        }
        if (backAnimator != null) {
            backAnimator.cancel();
            backAnimator = null;
        }
        dismissProgress = 0f;
        openProgress = 0f;
        backProgress = 0f;
        attachedToActionBar = false;
        fullyAttachedToActionBar = false;
        if (webActionBar.isSearching()) {
            webActionBar.showSearch(false, false);
        }
        if (webActionBar.isAddressing()) {
            webActionBar.showAddress(false, false);
        }
        searchPanel.show(false, false);
        searchPanel.attachKeyboardTracking(windowView);
        windowView.resetDrawingState();
        checkFullyVisible();
        updateTranslation();
        windowView.invalidate();
        windowView.requestLayout();
    }

    public void animateOpen(boolean open, boolean animated, Runnable callback) {
        if (openAnimator != null) {
            openAnimator.cancel();
        }
        if (!animated) {
            openProgress = open ? 1f : 0f;
            updateTranslation();
            checkFullyVisible();
            if (callback != null) {
                callback.run();
            }
            return;
        }
        openAnimator = ValueAnimator.ofFloat(openProgress, open ? 1f : 0f);
        openAnimator.addUpdateListener(animation -> {
            openProgress = (float) animation.getAnimatedValue();
            updateTranslation();
        });
        openAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                openProgress = open ? 1f : 0f;
                updateTranslation();
                checkFullyVisible();
                if (callback != null) {
                    callback.run();
                }
            }
        });
        openAnimator.setInterpolator(open ? CubicBezierInterpolator.EASE_OUT_QUINT : CubicBezierInterpolator.EASE_OUT);
        openAnimator.setDuration(open ? 320 : 180);
        openAnimator.start();
    }

    public void animateDismiss(boolean dismiss, boolean animated, Runnable callback) {
        if (dismissAnimator != null) {
            dismissAnimator.cancel();
        }
        if (!animated) {
            dismissProgress = dismiss ? 1f : 0f;
            updateTranslation();
            checkFullyVisible();
            if (callback != null) {
                callback.run();
            }
            return;
        }
        dismissAnimator = ValueAnimator.ofFloat(dismissProgress, dismiss ? 1f : 0f);
        dismissAnimator.addUpdateListener(animation -> {
            dismissProgress = (float) animation.getAnimatedValue();
            if (!dismissingIntoTabs) {
                updateTranslation();
            }
            checkFullyVisible();
        });
        dismissAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                dismissProgress = dismiss ? 1f : 0f;
                if (!dismissingIntoTabs) {
                    updateTranslation();
                }
                checkFullyVisible();
                if (callback != null) {
                    callback.run();
                }
            }
        });
        dismissAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        dismissAnimator.setDuration(250);
        dismissAnimator.start();
    }

    private void updateTranslation() {
        containerView.setTranslationY(getEmptyPadding() * Math.max(1f - openProgress, dismissingIntoTabs ? 0f : dismissProgress));
        windowView.setTranslationX(0f);
        windowView.setTranslationY(0f);
        windowView.invalidate();
    }

    private int getListTop() {
        return getActionBarHeight();
    }

    private int getListPaddingTop() {
        return AndroidUtilities.dp(20);
    }

    private int getEmptyPadding() {
        int height = containerView.getHeight() <= 0 ? AndroidUtilities.displaySize.y : containerView.getHeight();
        return AndroidUtilities.dp(16) + height - (getListTop() - getListPaddingTop());
    }

    private int getActionBarHeight() {
        return webActionBar.getMeasuredHeight() > 0 ? webActionBar.getMeasuredHeight() : webActionBar.getCurrentActionBarHeight();
    }

    private int getBackgroundColor() {
        return Theme.getColor(Theme.key_iv_background);
    }

    private int getActionBarColor() {
        return Theme.getColor(Theme.key_iv_background);
    }

    private void checkFullyVisible() {
        boolean fullyVisible = isFullyVisible();
        if (wasFullyVisible == fullyVisible) {
            return;
        }
        wasFullyVisible = fullyVisible;
        if (fragment != null) {
            fragment.updateSheetsVisibility();
        }
        if (windowView.getParent() instanceof View) {
            ((View) windowView.getParent()).invalidate();
        }
    }

    public float getBackProgress() {
        return backProgress;
    }

    public void setBackProgress(float progress) {
        backProgress = progress;
        updateTranslation();
        checkFullyVisible();
    }

    public ValueAnimator animateBackProgressTo(float target) {
        ValueAnimator animator = ValueAnimator.ofFloat(backProgress, target);
        animator.addUpdateListener(animation -> setBackProgress((float) animation.getAnimatedValue()));
        return animator;
    }

    private void handleMenuItem(int id) {
        if (id == WebActionBar.MENU_REFRESH) {
            webViewContainer.reload();
        } else if (id == WebActionBar.MENU_OPEN_EXTERNAL) {
            webViewContainer.openCurrentUrlExternal();
        } else if (id == WebActionBar.MENU_COPY_LINK) {
            copyCurrentLink();
        } else if (id == WebActionBar.MENU_MINIMIZE) {
            dismiss(true);
        } else if (id == WebActionBar.MENU_SEARCH) {
            webActionBar.showSearch(true, true);
        }
    }

    private void processSearch(String text) {
        if (text == null || text.trim().isEmpty()) {
            webViewContainer.clearSearch();
            searchPanel.show(false, true);
            return;
        }
        searchPanel.show(true, true);
        webViewContainer.search(text, () -> {
            int count = webViewContainer.getSearchCount();
            int index = webViewContainer.getSearchIndex();
            searchPanel.updateResults(index, count);
        });
    }

    private boolean pushTab() {
        if (!(fragment.getParentLayout() instanceof ActionBarLayout)) {
            return false;
        }
        ActionBarLayout layout = (ActionBarLayout) fragment.getParentLayout();
        BottomSheetTabs bottomSheetTabs = layout.getBottomSheetTabs();
        if (bottomSheetTabs == null) {
            return false;
        }
        bottomSheetTabs.pushTab(saveState());
        return true;
    }

    @Override
    public BottomSheetTabs.WebTabData saveState() {
        BottomSheetTabs.WebTabData tab = new BottomSheetTabs.WebTabData();
        tab.browserSheet = this;
        tab.title = webActionBar.getTitle() == null ? titleFromUrl(webViewContainer.getCurrentUrl()) : webActionBar.getTitle().toString();
        tab.startUrl = initialUrl;
        tab.currentUrl = webViewContainer.getCurrentUrl();
        tab.favicon = webViewContainer.getFavicon();
        tab.articleProgress = webViewContainer.getProgress() / 100f;
        tab.viewWidth = webViewContainer.getWebViewWidth();
        tab.viewHeight = webViewContainer.getWebViewHeight();
        tab.viewScroll = webViewContainer.getWebViewScrollY();
        tab.actionBarColor = getActionBarColor();
        tab.backgroundColor = getBackgroundColor();
        tab.themeIsDark = Theme.isCurrentThemeDark();
        return tab;
    }

    @Override
    public void capturePreviewBitmap(BottomSheetTabs.WebTabData tab, Runnable whenDone) {
        webViewContainer.capturePreviewBitmap(bitmap -> {
            tab.previewBitmap = bitmap;
            if (whenDone != null) {
                whenDone.run();
            }
        });
    }

    private void copyCurrentLink() {
        String url = webViewContainer.getCurrentUrl();
        if (url == null || url.trim().isEmpty()) {
            return;
        }
        ClipboardManager clipboardManager = windowView.getContext().getSystemService(ClipboardManager.class);
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("link", url));
            Toast.makeText(windowView.getContext(), R.string.app_browser_link_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void loadAddressValue(String value) {
        String url = normalizeAddressValue(value);
        if (url == null) {
            return;
        }
        webActionBar.setCurrentUrl(url);
        webActionBar.setTitle(titleFromUrl(url));
        webViewContainer.loadUrl(url);
    }

    private String normalizeAddressValue(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        Uri uri = Uri.parse(trimmed);
        String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase();
        if (("http".equals(scheme) || "https".equals(scheme)) && uri.getHost() != null && !uri.getHost().trim().isEmpty()) {
            return uri.toString();
        }
        Uri httpsUri = Uri.parse("https://" + trimmed);
        return httpsUri.getHost() == null || httpsUri.getHost().trim().isEmpty() ? null : httpsUri.toString();
    }

    private String titleFromUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return "";
        }
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        return host == null || host.trim().isEmpty() ? url : host;
    }

    private String hostOf(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        String host = Uri.parse(url).getHost();
        return host == null || host.trim().isEmpty() ? null : host;
    }

    private ViewGroup getParentView() {
        if (fragment.getParentLayout() instanceof ViewGroup) {
            return (ViewGroup) fragment.getParentLayout();
        }
        return fragment.getFragmentView() == null || !(fragment.getFragmentView().getParent() instanceof ViewGroup)
                ? null
                : (ViewGroup) fragment.getFragmentView().getParent();
    }

    private void removeFromParent(View view) {
        if (view != null && view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }

    private class WindowView extends FrameLayout implements BaseFragment.AttachedSheetWindow, BottomSheetTabsOverlay.SheetView {
        private final RectF rect = new RectF();
        private final RectF clipRect = new RectF();
        private final Path clipPath = new Path();
        private final Path clipPath2 = new Path();
        private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final AnimatedFloat attachedActionBar = new AnimatedFloat(this, 0, 420, CubicBezierInterpolator.EASE_OUT_QUINT);
        private boolean drawingFromOverlay;

        private int startedTrackingPointerId;
        private boolean maybeStartTracking;
        private boolean startedTracking;
        private int startedTrackingX;
        private int startedTrackingY;
        private VelocityTracker velocityTracker;
        private boolean lastWebViewAllowedScroll;

        WindowView(Context context) {
            super(context);
            setClipChildren(false);
            setClipToPadding(false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            updateTranslation();
        }

        public boolean isVisible() {
            final int top = getListTop() - getListPaddingTop();
            final int drawnTop = (int) AndroidUtilities.lerp(top, 0, clamp01(attachedActionBar.get()));
            return drawnTop < getHeight();
        }

        private void resetDrawingState() {
            drawingFromOverlay = false;
            attachedActionBar.force(0f);
            maybeStartTracking = false;
            startedTracking = false;
            startedTrackingPointerId = -1;
            lastWebViewAllowedScroll = false;
            if (velocityTracker != null) {
                velocityTracker.recycle();
                velocityTracker = null;
            }
            invalidate();
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            return handleTouchEvent(ev) || super.onInterceptTouchEvent(ev);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return handleTouchEvent(event) || super.onTouchEvent(event);
        }

        @Override
        public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            handleTouchEvent(null);
            super.requestDisallowInterceptTouchEvent(disallowIntercept);
        }

        boolean handleTouchEvent(MotionEvent event) {
            if (closeAnimationInProgress || dismissing || released) {
                return false;
            }
            // Match upstream pages[0].swipeContainer.allowingScroll(true) gating with a simpler edge check.
            lastWebViewAllowedScroll = !webViewContainer.canWebViewScrollHorizontallyBack();

            if (event != null && event.getAction() == MotionEvent.ACTION_DOWN && !startedTracking && !maybeStartTracking) {
                startedTrackingPointerId = event.getPointerId(0);
                maybeStartTracking = true;
                startedTrackingX = (int) event.getX();
                startedTrackingY = (int) event.getY();
                if (velocityTracker != null) {
                    velocityTracker.clear();
                }
                return false;
            }

            if (event != null && event.getAction() == MotionEvent.ACTION_MOVE && event.getPointerId(0) == startedTrackingPointerId) {
                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain();
                }
                int dx = Math.max(0, (int) (event.getX() - startedTrackingX));
                int dy = Math.abs((int) event.getY() - startedTrackingY);
                velocityTracker.addMovement(event);
                if (maybeStartTracking && !startedTracking
                        && dx >= AndroidUtilities.getPixelsInCM(0.4f, true)
                        && Math.abs(dx) / 3 > dy
                        && lastWebViewAllowedScroll) {
                    maybeStartTracking = false;
                    startedTracking = true;
                    startedTrackingX = (int) event.getX();
                } else if (startedTracking) {
                    int width = getWidth();
                    setBackProgress(width <= 0 ? 0f : dx / (float) width);
                }
                return startedTracking;
            }

            boolean isUpLike = event != null && event.getPointerId(0) == startedTrackingPointerId
                    && (event.getAction() == MotionEvent.ACTION_CANCEL
                    || event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_POINTER_UP);

            if (isUpLike) {
                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain();
                }
                velocityTracker.computeCurrentVelocity(1000);
                float velX = velocityTracker.getXVelocity();
                float velY = velocityTracker.getYVelocity();
                if (!startedTracking && velX >= 3500 && velX > Math.abs(velY)) {
                    maybeStartTracking = false;
                    startedTracking = true;
                    startedTrackingX = (int) event.getX();
                }
                if (startedTracking) {
                    int width = getWidth();
                    float x = backProgress * width;
                    final boolean backAnimation = (x < width * 0.3f && (velX < 2500 || velX < velY)) || !lastWebViewAllowedScroll;
                    float distToMove = backAnimation ? x : (width - x);
                    finishBackGesture(backAnimation, distToMove, width);
                } else {
                    maybeStartTracking = false;
                    startedTracking = false;
                }
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
                return false;
            }

            if (event == null) {
                maybeStartTracking = false;
                startedTracking = false;
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
                return false;
            }

            return startedTracking && lastWebViewAllowedScroll;
        }

        private void finishBackGesture(boolean backAnimation, float distToMove, int width) {
            if (backAnimator != null) {
                backAnimator.cancel();
            }
            backAnimator = animateBackProgressTo(backAnimation ? 0f : 1f);
            int duration = width <= 0 ? 250 : Math.max((int) (420.0f / width * distToMove), 250);
            backAnimator.setDuration(duration);
            backAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            backAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    closeAnimationInProgress = false;
                    startedTracking = false;
                    if (!backAnimation && !dismissing && !released) {
                        dismissing = true;
                        release();
                        destroy();
                    }
                }
            });
            closeAnimationInProgress = true;
            backAnimator.start();
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (drawingFromOverlay) {
                return;
            }
            final float open = Math.min(openProgress, 1f - dismissProgress);
            scrimPaint.setColor(Color.BLACK);
            scrimPaint.setAlpha((int) (0x60 * open * (1f - backProgress)));
            canvas.drawRect(0, 0, getWidth(), getHeight(), scrimPaint);

            final int top = getListTop() - getListPaddingTop();
            final boolean nextAttachedToActionBar = top < (AndroidUtilities.statusBarHeight + AndroidUtilities.dp(56)) && open > .95f;
            if (BrowserSheet.this.attachedToActionBar != nextAttachedToActionBar) {
                BrowserSheet.this.attachedToActionBar = nextAttachedToActionBar;
            }
            final float attachedActionBar = this.attachedActionBar.set(nextAttachedToActionBar);
            if (BrowserSheet.this.fullyAttachedToActionBar != (attachedActionBar >= .999f)) {
                BrowserSheet.this.fullyAttachedToActionBar = attachedActionBar >= .999f;
                checkFullyVisible();
            }
            final int drawnTop = (int) AndroidUtilities.lerp(top, 0, clamp01(attachedActionBar));
            final float y = getEmptyPadding() * Math.max(1f - openProgress, dismissProgress);

            canvas.save();
            canvas.translate(getWidth() * backProgress, y);
            rect.set(0, drawnTop, getWidth(), getHeight() + AndroidUtilities.dp(16));
            final float radius = AndroidUtilities.dp(16) * (1f - attachedActionBar);
            if (attachedActionBar < 1f) {
                shadowPaint.setColor(Color.TRANSPARENT);
                shadowPaint.setShadowLayer(AndroidUtilities.dp(18), 0, -AndroidUtilities.dp(3), multAlpha(Color.BLACK, .26f * open));
                canvas.drawRoundRect(rect, radius, radius, shadowPaint);
            }

            if (radius <= 0) {
                canvas.clipRect(rect);
            } else {
                clipPath.rewind();
                clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW);
                canvas.clipPath(clipPath);
            }

            backgroundPaint.setColor(getBackgroundColor());
            backgroundPaint.setAlpha(0xFF);
            canvas.drawRect(rect, backgroundPaint);

            canvas.translate(0, -y);
            super.dispatchDraw(canvas);
            canvas.translate(0, y);

            if (attachedActionBar < 1f) {
                final float handleAlpha = 1f - attachedActionBar;
                final boolean dark = computePerceivedBrightness(getBackgroundColor()) < .721f;
                handlePaint.setColor(ColorUtils.blendARGB(multAlpha(dark ? Color.WHITE : Color.BLACK, .15f), Color.BLACK, attachedActionBar));
                handlePaint.setAlpha((int) (handlePaint.getAlpha() * handleAlpha));
                final float cx = getWidth() / 2f;
                final float cy = drawnTop + getListPaddingTop() / 2f - attachedActionBar * AndroidUtilities.dp(8);
                final float w = AndroidUtilities.lerp(AndroidUtilities.dp(32), AndroidUtilities.dp(48), attachedActionBar);
                rect.set(cx - w / 2f, cy - AndroidUtilities.dp(2), cx + w / 2f, cy + AndroidUtilities.dp(2));
                canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, handlePaint);
            }

            canvas.restore();
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getY() < (attachedToActionBar ? 0 : getListTop())) {
                dismiss(true);
                return true;
            }
            return super.dispatchTouchEvent(ev);
        }

        @Override
        public void setDrawingFromOverlay(boolean value) {
            if (drawingFromOverlay != value) {
                drawingFromOverlay = value;
                invalidate();
            }
        }

        @Override
        public RectF getRect() {
            clipRect.set(
                    0,
                    (attachedToActionBar ? 0 : getListTop() - getListPaddingTop()) + getEmptyPadding() * Math.max(1f - openProgress, dismissProgress),
                    getWidth(),
                    getHeight()
            );
            return clipRect;
        }

        @Override
        public float drawInto(Canvas canvas, RectF finalRect, float progress, RectF clipRect, float alpha, boolean opening) {
            clipRect.set(getRect());
            lerpRect(clipRect, finalRect, progress, clipRect);

            float open = Math.min(openProgress, 1f - dismissProgress) * (1f - progress);
            scrimPaint.setColor(Color.BLACK);
            scrimPaint.setAlpha((int) (0x60 * open * (1f - backProgress)));
            canvas.drawRect(0, 0, getWidth(), getHeight(), scrimPaint);

            float radius = AndroidUtilities.lerp(AndroidUtilities.dp(16), AndroidUtilities.dp(10), progress);
            clipPath2.rewind();
            clipPath2.addRoundRect(clipRect, radius, radius, Path.Direction.CW);

            backgroundPaint.setColor(getBackgroundColor());
            backgroundPaint.setAlpha(0xFF);
            canvas.drawPath(clipPath2, backgroundPaint);

            if (getWidth() <= 0 || getHeight() <= 0) {
                return radius;
            }

            if (attachedToActionBar) {
                canvas.save();
                canvas.clipPath(clipPath2);
                canvas.translate(0, clipRect.top);
                webActionBar.draw(canvas);
                canvas.restore();
            }

            canvas.save();
            float scale = opening ? 1f : AndroidUtilities.lerp(1f, .99f, progress);
            if (Math.abs(scale - 1f) > .01f) {
                canvas.scale(scale, scale, clipRect.centerX(), clipRect.centerY());
            }
            canvas.clipPath(clipPath2);
            if (Math.abs(scale - 1f) > .01f) {
                canvas.scale(1f / scale, 1f / scale, clipRect.centerX(), clipRect.centerY());
            }
            canvas.translate(0, -getListTop() + clipRect.top + (attachedToActionBar ? getActionBarHeight() : 0) * (1f - progress));
            if (getChildCount() > 0) {
                getChildAt(0).draw(canvas);
            }
            canvas.restore();
            return radius;
        }
    }

    private static void lerpRect(RectF from, RectF to, float progress, RectF out) {
        out.set(
                AndroidUtilities.lerp(from.left, to.left, progress),
                AndroidUtilities.lerp(from.top, to.top, progress),
                AndroidUtilities.lerp(from.right, to.right, progress),
                AndroidUtilities.lerp(from.bottom, to.bottom, progress)
        );
    }

    private static float computePerceivedBrightness(int color) {
        return (Color.red(color) * 0.2126f + Color.green(color) * 0.7152f + Color.blue(color) * 0.0722f) / 255f;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static int multAlpha(int color, float multiply) {
        if (multiply == 1f) {
            return color;
        }
        return ColorUtils.setAlphaComponent(color, Math.max(0, Math.min(0xFF, (int) (Color.alpha(color) * multiply))));
    }
}
