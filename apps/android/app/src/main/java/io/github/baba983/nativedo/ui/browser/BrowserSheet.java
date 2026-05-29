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
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebBackForwardList;
import android.webkit.WebHistoryItem;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;

import java.util.ArrayList;

import io.github.baba983.nativedo.R;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheetTabs;
import org.telegram.ui.ActionBar.BottomSheetTabsOverlay;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ArticleViewer;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.bots.ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer;
import org.telegram.ui.web.AddressBarList;
import org.telegram.ui.web.HistoryFragment;
import org.telegram.ui.web.WebInstantView;

public class BrowserSheet extends org.telegram.ui.ActionBar.BrowserSheet implements BottomSheetTabsOverlay.Sheet {

    private BaseFragment fragment;
    private final WindowView windowView;
    private final FrameLayout containerView;
    private final org.telegram.ui.web.WebActionBar webActionBar;
    private final PageLayout[] pages = new PageLayout[2];
    private final ArrayList<CachedPage> pagesStack = new ArrayList<>();
    private final BrowserSearchPanel searchPanel;
    private final AddressBarList addressPanel;
    private final String initialUrl;
    private final boolean initialLoadHandledInApp;

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
    private AnimatorSet pageSwitchAnimation;
    private ItemOptions historyOptions;
    private Runnable onDismissListener;
    private boolean attachedToActionBar;
    private boolean fullyAttachedToActionBar;
    private boolean wasFullyVisible;
    private int keyboardHeight;
    private int currentHeaderHeight = AndroidUtilities.dp(56);
    public BrowserSheet(BaseFragment fragment, String initialUrl) {
        this.fragment = fragment;
        this.initialUrl = initialUrl;
        Context context = fragment.getContext();
        windowView = new WindowView(context);
        containerView = new FrameLayout(context);
        containerView.setClipChildren(false);
        containerView.setClipToPadding(false);
        windowView.addView(containerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.START));

        webActionBar = new org.telegram.ui.web.WebActionBar(context, null) {
            @Override
            protected void onSearchUpdated(String text) {
                processSearch(text);
            }

            @Override
            protected void onAddressingProgress(float progress) {
                super.onAddressingProgress(progress);
                if (addressPanel != null) {
                    addressPanel.setOpenProgress(progress);
                }
            }

            @Override
            protected void onAddressColorsChanged(int backgroundColor, int textColor) {
                if (addressPanel != null) {
                    addressPanel.setColors(backgroundColor, textColor);
                }
            }

            @Override
            public void showAddress(boolean show, boolean animated) {
                super.showAddress(show, animated);
                if (addressPanel != null) {
                    addressPanel.setOpened(show);
                }
            }

            @Override
            protected void onScrolledProgress(float delta) {
                if (pages[0] != null) {
                    pages[0].addProgress(delta);
                }
                windowView.invalidate();
            }

            @Override
            protected WebInstantView.Loader getInstantViewLoader() {
                return null;
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                if (addressPanel != null) {
                    ViewGroup.LayoutParams lp = addressPanel.getLayoutParams();
                    if (lp instanceof ViewGroup.MarginLayoutParams) {
                        ((ViewGroup.MarginLayoutParams) lp).topMargin = getMeasuredHeight();
                    }
                }
            }
        };
        webActionBar.occupyStatusBar(true);
        webActionBar.setOnClickListener(v -> {
            if (webActionBar.longClicked) {
                return;
            }
            showAddressPanel();
        });
        webActionBar.addressEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (addressPanel != null && webActionBar.isAddressing()) {
                    addressPanel.setInput(s == null ? null : s.toString());
                }
            }
        });
        webActionBar.backButton.setOnClickListener(v -> onAttachedBackPressed());
        webActionBar.backButton.setOnLongClickListener(this::showBackHistoryMenu);
        webActionBar.forwardButton.setOnClickListener(v -> dismiss(true));
        webActionBar.setMenuListener(this::handleMenuItem);
        webActionBar.setColors(getActionBarColor(), false);
        webActionBar.setMenuColors(getBackgroundColor());
        webActionBar.setIsTonsite(false);
        webActionBar.setTitle(titleFromUrl(initialUrl), false);
        webActionBar.setSubtitle(initialUrl, false);
        webActionBar.setMenuType(ArticleViewer.PageLayout.TYPE_WEB);

        pages[0] = new PageLayout(context);
        pages[1] = new PageLayout(context);
        pages[1].setVisibility(View.GONE);
        pagesStack.add(new CachedWeb(initialUrl));
        initialLoadHandledInApp = pages[0].setPage(pagesStack.get(0));

        int actionBarHeight = getActionBarHeight();
        FrameLayout.LayoutParams contentLayoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.START);
        containerView.addView(pages[1], contentLayoutParams);
        containerView.addView(pages[0], new FrameLayout.LayoutParams(contentLayoutParams));
        containerView.addView(webActionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.START));

        addressPanel = new AddressBarList(context);
        FrameLayout.LayoutParams addressPanelLp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.START);
        addressPanelLp.topMargin = actionBarHeight;
        containerView.addView(addressPanel, addressPanelLp);

        searchPanel = new BrowserSearchPanel(context);
        searchPanel.setCallback(new BrowserSearchPanel.Callback() {
            @Override
            public void onFindPrevious() {
                if (pages[0] != null && pages[0].isWeb()) {
                    pages[0].webViewContainer.findNext(false);
                }
            }

            @Override
            public void onFindNext() {
                if (pages[0] != null && pages[0].isWeb()) {
                    pages[0].webViewContainer.findNext(true);
                }
            }
        });
        containerView.addView(searchPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 51, Gravity.BOTTOM | Gravity.START));
        searchPanel.attachKeyboardTracking(windowView);

    }

    public void show() {
        if (dismissing || released) {
            return;
        }
        if (!initialLoadHandledInApp) {
            release();
            destroy();
            return;
        }
        attachInternal();
        animateOpen(true, true, null);
    }

    @Override
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
        if (pages[0] != null) {
            pages[0].onResume();
        }
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
        if (historyOptions != null) {
            historyOptions.dismiss(false);
            historyOptions = null;
        }
        searchPanel.show(false, false);
        searchPanel.detachKeyboardTracking(windowView);
        for (PageLayout page : pages) {
            if (page != null) {
                page.onPause();
            }
        }
        fragment.removeSheet(this);
        removeFromParent(windowView);
        if (onDismissListener != null) {
            onDismissListener.run();
            onDismissListener = null;
        }
    }

    private void destroy() {
        for (PageLayout page : pages) {
            if (page != null) {
                page.cleanup();
            }
        }
        for (CachedPage page : pagesStack) {
            page.destroy();
        }
        pagesStack.clear();
    }

    @Override
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
            webActionBar.showAddress(false, true);
            return true;
        }
        if (pagesStack.size() <= 1 && pages[0] != null && pages[0].hasBackButton()) {
            pages[0].back();
            return true;
        }
        if (pagesStack.size() > 1) {
            goBack();
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
        this.keyboardHeight = keyboardHeight;
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
        if (pages[0] != null) {
            pages[0].setLastVisible(lastVisible);
        }
        if (pages[1] != null) {
            pages[1].setLastVisible(false);
        }
    }

    @Override
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
        if (historyOptions != null) {
            historyOptions.dismiss(false);
            historyOptions = null;
        }
        searchPanel.show(false, false);
        searchPanel.attachKeyboardTracking(windowView);
        if (pages[0] != null) {
            pages[0].resetSwipeOffset();
        }
        updateSwipeContainerOffset();
        windowView.resetDrawingState();
        checkFullyVisible();
        updateTranslation();
        windowView.invalidate();
        windowView.requestLayout();
    }

    @Override
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
        fragment.notifyParentSystemBarsChanged();
        windowView.invalidate();
    }

    private int getListTop() {
        return pages[0] == null ? 0 : (int) pages[0].getListTop();
    }

    private int getListPaddingTop() {
        return AndroidUtilities.dp(20);
    }

    private int getEmptyPadding() {
        int height = containerView.getHeight() <= 0 ? AndroidUtilities.displaySize.y : containerView.getHeight();
        return AndroidUtilities.dp(16) + height - (getListTop() - getListPaddingTop());
    }

    private int getActionBarHeight() {
        return webActionBar.getMeasuredHeight() > 0 ? webActionBar.getMeasuredHeight() : webActionBar.topPadding() + AndroidUtilities.dp(56);
    }

    private void showAddressPanel() {
        if (webActionBar.isAddressing()) {
            return;
        }
        PageLayout page = pages[0];
        if (page == null || !page.isWeb()) {
            return;
        }
        String title = webActionBar.getTitle();
        String url = page.getSubtitle();
        Bitmap favicon = page.getFavicon();
        if (addressPanel != null) {
            addressPanel.setCurrent(
                    favicon,
                    TextUtils.isEmpty(title) ? titleFromUrl(url) : title,
                    TextUtils.isEmpty(url) ? "about:blank" : url,
                    () -> setAddressText(TextUtils.isEmpty(url) ? "about:blank" : url),
                    query -> {
                        webActionBar.showAddress(false, true);
                        loadAddressValue(query);
                    },
                    this::setAddressText,
                    urlToOpen -> {
                        webActionBar.showAddress(false, true);
                        loadBrowserUrl(urlToOpen);
                    },
                    v -> {
                        webActionBar.showAddress(false, true);
                        copyCurrentLink();
                    }
            );
        }
        webActionBar.showAddress("", this::loadAddressValue);
    }

    private void setAddressText(String value) {
        webActionBar.addressEditText.setText(value == null ? "" : value);
        webActionBar.addressEditText.setSelection(webActionBar.addressEditText.getText().length());
        AndroidUtilities.showKeyboard(webActionBar.addressEditText);
    }

    private void setLoadingProgress(int progress, boolean visible) {
        int clamped = Math.max(0, Math.min(100, progress));
        webActionBar.lineProgressView.setProgress(visible ? clamped / 100f : 1f, true);
    }

    private boolean hideActionBarKeyboardIfInputFocused() {
        if (webActionBar.searchEditText.hasFocus() || webActionBar.addressEditText.hasFocus()) {
            webActionBar.hideKeyboard();
            return true;
        }
        return false;
    }

    private void updateSwipeContainerOffset() {
        if (windowView.getMeasuredHeight() <= 0) {
            return;
        }
        float targetOffsetY = windowView.getMeasuredHeight() * 0.4f;
        for (PageLayout page : pages) {
            if (page != null) {
                page.setSwipeOffset(targetOffsetY);
            }
        }
    }

    private int getBackgroundColor() {
        return pages[0] != null ? pages[0].getBackgroundColor() : Theme.getColor(Theme.key_iv_background);
    }

    private int getActionBarColor() {
        return pages[0] != null ? pages[0].getActionBarColor() : Theme.getColor(Theme.key_iv_background);
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
        PageLayout page = pages[0];
        if (page == null) {
            return;
        }
        if (id == org.telegram.ui.web.WebActionBar.reload_item && page.isWeb()) {
            page.webViewContainer.reload();
        } else if (id == org.telegram.ui.web.WebActionBar.forward_item && page.isWeb()) {
            page.webViewContainer.goForward();
        } else if (id == org.telegram.ui.web.WebActionBar.open_item) {
            openCurrentPageExternal(page);
        } else if (id == org.telegram.ui.web.WebActionBar.share_item) {
            shareCurrentPage(page);
        } else if (id == org.telegram.ui.web.WebActionBar.search_item) {
            webActionBar.showSearch(true, true);
        } else if (id == org.telegram.ui.web.WebActionBar.bookmark_item) {
            toggleBookmark(page);
        } else if (id == org.telegram.ui.web.WebActionBar.history_item) {
            BaseFragment.BottomSheetParams params = new BaseFragment.BottomSheetParams();
            params.transitionFromLeft = true;
            fragment.showAsSheet(new HistoryFragment(null, entry -> {
                if (entry != null) {
                    loadBrowserUrl(entry.url);
                }
            }), params);
        } else if (id == org.telegram.ui.web.WebActionBar.bookmarks_item) {
            BrowserFeatureSheet.showHistory(fragment, BrowserListFragment.Mode.BOOKMARKS, this::loadBrowserUrl);
        } else if (id == org.telegram.ui.web.WebActionBar.settings_item) {
            if (page.isArticle()) {
                page.openArticleFontSizeDialog();
            } else {
                BrowserFeatureSheet.showSettings(fragment, this::loadBrowserUrl, () -> updateBrowserMenuState(
                        pages[0] == null ? null : pages[0].webViewContainer.getCurrentUrl()));
            }
        } else if (id == org.telegram.ui.web.WebActionBar.instant_item && page.isWeb()) {
            openInstantView(page);
        }
    }

    private void processSearch(String text) {
        PageLayout page = pages[0];
        if (page == null) {
            return;
        }
        if (page.isArticle()) {
            return;
        }
        if (text == null || text.trim().isEmpty()) {
            page.webViewContainer.clearSearch();
            searchPanel.show(false, true);
            return;
        }
        searchPanel.show(true, true);
        page.webViewContainer.search(text, () -> {
            int count = page.webViewContainer.getSearchCount();
            int index = page.webViewContainer.getSearchIndex();
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
        PageLayout page = pages[0];
        PageLayout webPage = getCurrentWebPage();
        tab.browserSheet = this;
        tab.title = page == null ? "" : page.getTitle();
        tab.startUrl = initialUrl;
        tab.currentUrl = page == null ? null : page.getSubtitle();
        tab.favicon = page == null ? null : page.getFavicon();
        tab.articleProgress = page == null ? 0f : page.getProgress();
        tab.viewWidth = webPage == null ? 0 : webPage.webViewContainer.getWebViewWidth();
        tab.viewHeight = webPage == null ? 0 : webPage.webViewContainer.getWebViewHeight();
        tab.viewScroll = webPage == null ? 0 : webPage.webViewContainer.getWebViewScrollY();
        tab.actionBarColor = getActionBarColor();
        tab.backgroundColor = getBackgroundColor();
        tab.themeIsDark = Theme.isCurrentThemeDark();
        return tab;
    }

    private void updatePageProgress() {
        if (pages[0] == null || pages[1] == null) {
            return;
        }
        float page0Alpha = pages[0].getVisibility() != View.VISIBLE || pages[0].getWidth() <= 0 ? 1f : 1f - pages[0].getTranslationX() / pages[0].getWidth();
        page0Alpha = clamp01(page0Alpha);
        float page1Alpha = 1f - page0Alpha;
        webActionBar.setProgress(0, pages[0].getProgress());
        webActionBar.setProgress(1, pages[1].getProgress());
        if (windowView.movingPage || windowView.openingPage) {
            webActionBar.setTransitionProgress(page1Alpha);
            if (!webActionBar.isAddressing() && !webActionBar.isSearching()) {
                float backButton = AndroidUtilities.lerp(pagesStack.size() > 1 ? 1f : 0f, pagesStack.size() > 2 ? 1f : 0f, page1Alpha);
                webActionBar.backButtonDrawable.setRotation(1f - backButton, false);
                webActionBar.setBackButtonCached(backButton > .5f);
            }
            webActionBar.setColors(ColorUtils.blendARGB(pages[0].getActionBarColor(), pages[1].getActionBarColor(), page1Alpha), false);
            webActionBar.setMenuColors(ColorUtils.blendARGB(pages[0].getBackgroundColor(), pages[1].getBackgroundColor(), page1Alpha));
        }
        windowView.invalidate();
    }

    @Override
    public void capturePreviewBitmap(BottomSheetTabs.WebTabData tab, Runnable whenDone) {
        if (pages[0] == null) {
            if (whenDone != null) {
                whenDone.run();
            }
            return;
        }
        if (pages[0].isArticle()) {
            tab.previewBitmap = captureViewBitmap(pages[0].articleContainer);
            if (whenDone != null) {
                whenDone.run();
            }
            return;
        }
        PageLayout page = getCurrentWebPage();
        if (page == null) {
            if (whenDone != null) {
                whenDone.run();
            }
            return;
        }
        page.webViewContainer.capturePreviewBitmap(bitmap -> {
            tab.previewBitmap = bitmap;
            if (whenDone != null) {
                whenDone.run();
            }
        });
    }

    @Nullable
    private Bitmap captureViewBitmap(View view) {
        if (view == null || view.getWidth() <= 0 || view.getHeight() <= 0) {
            return null;
        }
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);
        return bitmap;
    }

    private void copyCurrentLink() {
        String url = pages[0] == null ? null : pages[0].getSubtitle();
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
        PageLayout page = pages[0];
        if (url == null || page == null) {
            return;
        }
        loadBrowserUrl(url);
    }

    private void loadBrowserUrl(String url) {
        PageLayout page = pages[0];
        if (page == null || TextUtils.isEmpty(url)) {
            return;
        }
        if (page.isArticle()) {
            addPageToStack(url, 1);
            return;
        }
        if (page.webViewContainer.loadUrl(url)) {
            webActionBar.setSubtitle(url, true);
            webActionBar.setTitle(titleFromUrl(url), true);
            updateBrowserMenuState(url);
        }
    }

    private boolean addPageToStack(String url, int order) {
        if (pages[0] != null) {
            pages[0].updatePage();
        }
        CachedWeb web = new CachedWeb(url);
        pagesStack.add(web);
        webActionBar.showSearch(false, true);
        updateInterfaceForCurrentPage(web, false, order);
        return false;
    }

    private void addInstantPageToStack(String url, String json, int order) {
        if (pages[0] != null) {
            pages[0].updatePage();
        }
        CachedArticle article = new CachedArticle(url, json);
        pagesStack.add(article);
        webActionBar.showSearch(false, true);
        updateInterfaceForCurrentPage(article, false, order);
    }

    private void updateInterfaceForCurrentPage(CachedPage cachedPage, boolean previous, int order) {
        if (cachedPage == null) {
            return;
        }
        if (!previous && order != 0) {
            PageLayout pageToUpdate = pages[1];
            pages[1] = pages[0];
            pages[0] = pageToUpdate;
            webActionBar.swap();
            pageToUpdate.setPage(cachedPage);
            pageToUpdate.setVisibility(View.VISIBLE);
            pageToUpdate.setAlpha(1f);
            pageToUpdate.setTranslationX(order == 1 ? windowView.getWidth() : 0);
            bringPageToFront(pageToUpdate);
            pageSwitchAnimation = new AnimatorSet();
            if (order == 1) {
                pageSwitchAnimation.playTogether(ObjectAnimator.ofFloat(pageToUpdate, View.TRANSLATION_X, windowView.getWidth(), 0));
            } else if (order == -1) {
                pages[1].setTranslationX(0);
                pageSwitchAnimation.playTogether(ObjectAnimator.ofFloat(pages[1], View.TRANSLATION_X, 0, windowView.getWidth()));
            }
            pageSwitchAnimation.setDuration(320);
            pageSwitchAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            pageSwitchAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    pages[1].cleanup();
                    pages[1].setVisibility(View.GONE);
                    pageSwitchAnimation = null;
                    windowView.openingPage = false;
                    updateActivePage();
                }
            });
            windowView.openingPage = true;
            updateTitleSlots();
            pageSwitchAnimation.start();
            updateActivePage();
            return;
        }
        int index = previous ? 1 : 0;
        pages[index].setPage(cachedPage);
        pages[index].setVisibility(View.VISIBLE);
        pages[index].setAlpha(1f);
        pages[index].setTranslationX(0);
        if (!previous) {
            updateActivePage();
        }
    }

    private void goBack() {
        goBack(pagesStack.size() - 2);
    }

    private void goBack(int intoIndex) {
        if (pagesStack.size() <= 1 || intoIndex < 0 || intoIndex >= pagesStack.size() - 1) {
            dismiss(false);
            return;
        }
        if (pageSwitchAnimation != null) {
            pageSwitchAnimation.cancel();
        }
        windowView.openingPage = true;
        windowView.movingPage = true;
        windowView.startMovingHeaderHeight = currentHeaderHeight;
        PageLayout previousPage = pages[1];
        CachedPage previousCachedPage = pagesStack.get(intoIndex);
        previousPage.setPage(previousCachedPage);
        previousPage.setVisibility(View.VISIBLE);
        previousPage.setAlpha(1f);
        previousPage.setTranslationX(0f);
        sendPageToBack(previousPage);

        PageLayout currentPage = pages[0];
        pageSwitchAnimation = new AnimatorSet();
        pageSwitchAnimation.playTogether(ObjectAnimator.ofFloat(currentPage, View.TRANSLATION_X, currentPage.getMeasuredWidth()));
        pageSwitchAnimation.setDuration(420);
        pageSwitchAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        pageSwitchAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                finishPageBack(intoIndex, currentPage, previousPage);
            }
        });
        updateTitleSlots();
        pageSwitchAnimation.start();
        closeAnimationInProgress = true;
        updateTranslation();
    }

    private void finishPageBack(int intoIndex, PageLayout currentPage, PageLayout previousPage) {
        ArrayList<CachedPage> removed = new ArrayList<>();
        currentPage.cleanup();
        currentPage.setVisibility(View.GONE);
        currentPage.setTranslationX(0f);
        for (int i = pagesStack.size() - 1; i > intoIndex; --i) {
            removed.add(pagesStack.remove(i));
        }
        for (CachedPage cachedPage : removed) {
            cachedPage.destroy();
        }
        pages[0] = previousPage;
        pages[1] = currentPage;
        webActionBar.swap();
        webActionBar.setTransitionProgress(0f);
        pageSwitchAnimation = null;
        windowView.openingPage = false;
        windowView.movingPage = false;
        windowView.startedTracking = false;
        closeAnimationInProgress = false;
        updateActivePage();
    }

    private void updateActivePage() {
        PageLayout page = pages[0];
        if (page == null) {
            return;
        }
        page.setLastVisible(true);
        if (pages[1] != null) {
            pages[1].setLastVisible(false);
        }
        webActionBar.setTitle(page.getTitle(), true);
        webActionBar.setSubtitle(page.getSubtitleForDisplay(), true);
        webActionBar.setColors(page.getActionBarColor(), true);
        webActionBar.setMenuColors(page.getBackgroundColor());
        webActionBar.setIsTonsite(false);
        webActionBar.setBackButton(pagesStack.size() > 1);
        setLoadingProgress(page.getLoadingProgress(), page.isWeb() && page.getLoadingProgress() < 100);
        updateBrowserMenuState(page.getSubtitle());
        updateInstantViewMenuState(page);
        if (!page.isArticle()) {
            currentHeaderHeight = AndroidUtilities.dp(56);
        }
        webActionBar.setHeight(page.isArticle() ? currentHeaderHeight : AndroidUtilities.dp(56));
        webActionBar.setMenuType(page.isArticle() ? ArticleViewer.PageLayout.TYPE_ARTICLE : ArticleViewer.PageLayout.TYPE_WEB);
        updatePageProgress();
    }

    private void updateTitleSlots() {
        if (pages[0] == null || pages[1] == null) {
            return;
        }
        webActionBar.setTitle(0, pages[0].getTitle(), false);
        webActionBar.setSubtitle(0, pages[0].getSubtitleForDisplay(), false);
        webActionBar.setTitle(1, pages[1].getTitle(), false);
        webActionBar.setSubtitle(1, pages[1].getSubtitleForDisplay(), false);
    }

    private void bringPageToFront(PageLayout page) {
        containerView.removeView(page);
        int chromeIndex = containerView.indexOfChild(webActionBar);
        containerView.addView(page, Math.max(0, chromeIndex), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.START));
    }

    private void sendPageToBack(PageLayout page) {
        containerView.removeView(page);
        containerView.addView(page, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.START));
    }

    private boolean showBackHistoryMenu(View anchor) {
        PageLayout page = pages[0];
        if (page == null) {
            return false;
        }
        ItemOptions options = ItemOptions.makeOptions(windowView, anchor);
        int backgroundColor = SharedConfig.adaptableColorInBrowser ? Theme.getColor(Theme.key_iv_background) : page.getBackgroundColor();
        int textColor = SharedConfig.adaptableColorInBrowser
                ? Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)
                : AndroidUtilities.computePerceivedBrightness(page.getBackgroundColor()) >= .721f ? Color.BLACK : Color.WHITE;
        int subtextColor = multAlpha(textColor, .65f);

        WebBackForwardList history = page.isWeb() ? page.webViewContainer.copyBackForwardList() : null;
        int currentIndex = history == null ? -1 : history.getCurrentIndex();
        if (page.isWeb() && currentIndex > 0) {
            for (int i = 0; i < currentIndex; ++i) {
                WebHistoryItem item = history.getItemAtIndex(i);
                final int index = i;
                addHistoryItem(options, item == null ? null : item.getTitle(), item == null ? null : item.getUrl(), item == null ? null : item.getFavicon(), textColor, subtextColor, () -> page.webViewContainer.goBackToHistoryIndex(index));
            }
        }

        for (int i = pagesStack.size() - 2; i >= 0; --i) {
            CachedPage web = pagesStack.get(i);
            final int index = i;
            addHistoryItem(options, web.getTitle(), web.lastUrl, web.favicon, textColor, subtextColor, () -> goBack(index));
        }

        if (options.getItemsCount() <= 0) {
            return false;
        }
        if (historyOptions != null && historyOptions.isShown()) {
            historyOptions.dismiss(false);
        }
        float wasRotation = webActionBar.backButtonDrawable.getRotation();
        webActionBar.backButtonDrawable.setRotation(0f, true);
        options.setScrimViewBackground(Theme.createCircleDrawable(AndroidUtilities.dp(40), webActionBar.getBackgroundColor()));
        options.setBackgroundColor(backgroundColor);
        options.updateColors();
        options.setOnDismiss(() -> {
            webActionBar.backButtonDrawable.setRotation(wasRotation, true);
            historyOptions = null;
        });
        historyOptions = options;
        options.show();
        return true;
    }

    private void addHistoryItem(ItemOptions options, String title, String url, Bitmap favicon, int textColor, int subtextColor, Runnable onClick) {
        options.add(TextUtils.isEmpty(title) ? titleFromUrl(url) : title, onClick);
        ActionBarMenuSubItem item = options.getLast();
        if (item == null) {
            return;
        }
        item.setSubtext(TextUtils.isEmpty(url) ? "about:blank" : url);
        item.setTextColor(textColor);
        item.setSubtextColor(subtextColor);
        item.setColors(textColor, textColor);
        item.setIconDrawable(createFaviconDrawable(favicon, textColor));
        item.setMinimumWidth(AndroidUtilities.dp(240));
    }

    private Drawable createFaviconDrawable(Bitmap favicon, int fallbackColor) {
        if (favicon == null) {
            Drawable fallback = windowView.getContext().getDrawable(R.drawable.msg_language);
            if (fallback != null) {
                fallback = fallback.mutate();
                fallback.setColorFilter(new PorterDuffColorFilter(fallbackColor, PorterDuff.Mode.SRC_IN));
            }
            return fallback;
        }
        return new Drawable() {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

            @Override
            public void draw(Canvas canvas) {
                canvas.save();
                canvas.translate(getBounds().left, getBounds().top);
                canvas.scale(getBounds().width() / (float) favicon.getWidth(), getBounds().height() / (float) favicon.getHeight());
                canvas.drawBitmap(favicon, 0, 0, paint);
                canvas.restore();
            }

            @Override
            public void setAlpha(int alpha) {
                paint.setAlpha(alpha);
            }

            @Override
            public void setColorFilter(android.graphics.ColorFilter colorFilter) {
                paint.setColorFilter(colorFilter);
            }

            @Override
            public int getOpacity() {
                return android.graphics.PixelFormat.TRANSPARENT;
            }

            @Override
            public int getIntrinsicWidth() {
                return AndroidUtilities.dp(24);
            }

            @Override
            public int getIntrinsicHeight() {
                return AndroidUtilities.dp(24);
            }
        };
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
        if ("javascript".equals(scheme)) {
            return null;
        }
        if (scheme != null) {
            return uri.toString();
        }
        if (Patterns.WEB_URL.matcher(trimmed).matches()) {
            return "https://" + trimmed;
        }
        AddressBarList.pushRecentSearch(windowView.getContext(), trimmed);
        return BrowserStorage.buildSearchUrl(windowView.getContext(), trimmed);
    }

    private String titleFromUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return "";
        }
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        return host == null || host.trim().isEmpty() ? url : host;
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

    private class PageLayout extends FrameLayout {
        private final BrowserWebViewContainer webViewContainer;
        private final WebViewSwipeContainer swipeContainer;
        private final FrameLayout articleContainer;
        private CachedPage page;
        private CachedWeb web;
        private BrowserInstantViewFragment articleFragment;
        private boolean backButton;
        private int webActionBarColor = Theme.getColor(Theme.key_iv_background);
        private int webBackgroundColor = Theme.getColor(Theme.key_iv_background);

        PageLayout(Context context) {
            super(context);
            setClipChildren(false);
            setClipToPadding(false);

            webViewContainer = new BrowserWebViewContainer(context, new BrowserWebViewContainer.Delegate() {
                @Override
                public void onTitleChanged(String title) {
                    updatePage();
                    if (isWeb() && PageLayout.this == pages[0] && !dismissing && title != null && title.trim().length() > 0) {
                        webActionBar.setTitle(title, true);
                    }
                }

                @Override
                public void onUrlChanged(String url, boolean canGoBack, boolean canGoForward) {
                    backButton = canGoBack;
                    updatePage();
                    if (isWeb() && PageLayout.this == pages[0] && !webActionBar.isAddressing() && !webActionBar.isSearching() && !windowView.movingPage && !windowView.openingPage) {
                        updateBrowserMenuState(url);
                        webActionBar.setSubtitle(url, true);
                        if (webActionBar.getTitle() == null || webActionBar.getTitle().length() == 0) {
                            webActionBar.setTitle(titleFromUrl(url), true);
                        }
                        if (pagesStack.size() > 1) {
                            webActionBar.setBackButton(true);
                        } else {
                            webActionBar.setBackButtonCached(false);
                        }
                    }
                }

                @Override
                public void onProgressChanged(int progress, boolean visible) {
                    if (PageLayout.this == pages[0]) {
                        setLoadingProgress(progress, visible);
                    }
                }

                @Override
                public void onWebViewScrolled(int dx, int dy) {
                    if (PageLayout.this == pages[0]) {
                        updatePageProgress();
                    }
                }

                @Override
                public void onWebBackgroundChanged(boolean actionBarColor, int color) {
                    setWebBgColor(actionBarColor, color);
                }

                @Override
                public void onInstantViewChanged(boolean available) {
                    if (PageLayout.this == pages[0]) {
                        updateInstantViewMenuState(PageLayout.this);
                    }
                    updatePage();
                }

                @Override
                public void onInstantViewLoadingChanged(boolean loading) {
                    if (PageLayout.this == pages[0]) {
                        updateInstantViewMenuState(PageLayout.this);
                    }
                    updatePage();
                }

                @Override
                public void onHistoryChanged(boolean available) {
                }
            });

            swipeContainer = new WebViewSwipeContainer(context);
            swipeContainer.setShouldWaitWebViewScroll(true);
            swipeContainer.setFullSize(true);
            swipeContainer.setAllowFullSizeSwipe(true);
            swipeContainer.setTopActionBarOffsetY(getActionBarHeight());
            swipeContainer.setSwipeOffsetY(-swipeContainer.getOffsetY() + swipeContainer.getTopActionBarOffsetY());
            swipeContainer.setIsKeyboardVisible(obj -> keyboardHeight >= AndroidUtilities.dp(20));
            swipeContainer.setDelegate(byTap -> {
                if (!dismissing) {
                    dismiss(true);
                }
            });
            swipeContainer.setScrollListener(() -> {
                if (!dismissingIntoTabs) {
                    updateTranslation();
                }
                windowView.invalidate();
            });
            swipeContainer.setWebView(webViewContainer.getWebView());
            swipeContainer.addView(webViewContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            addView(swipeContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            articleContainer = new FrameLayout(context);
            articleContainer.setVisibility(GONE);
            addView(articleContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }

        void setSwipeOffset(float offsetY) {
            swipeContainer.setOffsetY(offsetY);
        }

        void resetSwipeOffset() {
            swipeContainer.cancelStickTo();
            swipeContainer.setSwipeOffsetY(-swipeContainer.getOffsetY() + swipeContainer.getTopActionBarOffsetY());
        }

        float getListTop() {
            if (isArticle() && articleFragment != null) {
                return articleFragment.getListTop();
            }
            return swipeContainer.getTranslationY();
        }

        int getBackgroundColor() {
            return isWeb() && SharedConfig.adaptableColorInBrowser ? webBackgroundColor : Theme.getColor(Theme.key_iv_background);
        }

        int getActionBarColor() {
            return isWeb() && SharedConfig.adaptableColorInBrowser ? webActionBarColor : Theme.getColor(Theme.key_iv_background);
        }

        String getTitle() {
            if (isArticle() && articleFragment != null) {
                return articleFragment.getDisplayTitle();
            }
            String title = webViewContainer.getTitle();
            if (!TextUtils.isEmpty(title)) {
                return title;
            }
            return web == null ? titleFromUrl(webViewContainer.getCurrentUrl()) : web.getTitle();
        }

        String getSubtitle() {
            if (isArticle() && articleFragment != null) {
                return articleFragment.getArticleUrl();
            }
            String url = webViewContainer.getCurrentUrl();
            if (!TextUtils.isEmpty(url)) {
                return url;
            }
            return web == null ? "" : web.lastUrl;
        }

        String getSubtitleForDisplay() {
            return isArticle() ? "" : getSubtitle();
        }

        Bitmap getFavicon() {
            return webViewContainer.getFavicon();
        }

        boolean hasBackButton() {
            return isWeb() && backButton;
        }

        void back() {
            if (isWeb()) {
                webViewContainer.goBack();
            }
        }

        float getProgress() {
            if (isArticle() && articleFragment != null) {
                return articleFragment.getReadingProgress();
            }
            return webViewContainer.getScrollProgress();
        }

        void addProgress(float delta) {
            if (isArticle()) {
                return;
            }
            webViewContainer.setScrollProgress(clamp01(getProgress() + delta));
            updatePageProgress();
        }

        void setLastVisible(boolean lastVisible) {
            webViewContainer.setKeyboardFocusable(lastVisible);
        }

        void onResume() {
            if (isWeb()) {
                webViewContainer.onResume();
            }
        }

        void onPause() {
            if (isWeb()) {
                webViewContainer.onPause();
            }
        }

        boolean isWeb() {
            return page instanceof CachedWeb;
        }

        boolean isArticle() {
            return page instanceof CachedArticle;
        }

        boolean isAtTop() {
            if (isArticle() && articleFragment != null) {
                return articleFragment.isAtTop();
            }
            return webViewContainer.isAtTop();
        }

        int getLoadingProgress() {
            return isWeb() ? webViewContainer.getProgress() : 100;
        }

        void openArticleFontSizeDialog() {
            if (articleFragment != null) {
                articleFragment.openFontSizeDialog();
            }
        }

        void setWebBgColor(boolean actionBarColor, int color) {
            if (actionBarColor) {
                webActionBarColor = blendOver(Theme.getColor(Theme.key_iv_background), color);
                if (PageLayout.this == pages[0] && SharedConfig.adaptableColorInBrowser) {
                    webActionBar.setColors(webActionBarColor, true);
                }
            } else {
                webBackgroundColor = blendOver(Color.WHITE, color);
                if (SharedConfig.adaptableColorInBrowser) {
                    webViewContainer.setBrowserBackgroundColor(webBackgroundColor);
                    if (PageLayout.this == pages[0]) {
                        webActionBar.setMenuColors(webBackgroundColor);
                    }
                }
            }
            updatePage();
            updateTranslation();
            checkFullyVisible();
        }

        boolean setPage(CachedPage page) {
            if (this.page == page) {
                return true;
            }
            if (this.page != null) {
                this.page.detach(this);
            }
            if (this.web != null) {
                this.web = null;
            }
            articleFragment = null;
            articleContainer.removeAllViews();
            articleContainer.setVisibility(GONE);
            swipeContainer.setVisibility(GONE);
            webViewContainer.destroyWebView();
            this.page = page;
            boolean handledInApp = true;
            if (this.page != null) {
                handledInApp = this.page.attach(this);
            }
            swipeContainer.setWebView(webViewContainer.getWebView());
            return handledInApp;
        }

        void updatePage() {
            if (page != null) {
                page.enrich(this);
            }
        }

        void cleanup() {
            backButton = false;
            setPage(null);
            webViewContainer.destroyWebView();
            webActionBarColor = Theme.getColor(Theme.key_iv_background);
            webBackgroundColor = Theme.getColor(Theme.key_iv_background);
        }

        @Override
        public void setTranslationX(float translationX) {
            super.setTranslationX(translationX);
            updatePageProgress();
            if (windowView.openingPage || windowView.movingPage) {
                containerView.invalidate();
            }
            if (windowView.movingPage) {
                int width = getMeasuredWidth();
                if (width > 0) {
                    float progress = clamp01(translationX / width);
                    currentHeaderHeight = (int) (windowView.startMovingHeaderHeight + (AndroidUtilities.dp(56) - windowView.startMovingHeaderHeight) * progress);
                    webActionBar.setHeight(currentHeaderHeight);
                }
            }
            updateTranslation();
        }
    }

    private abstract class CachedPage {
        protected final String startUrl;
        protected String lastUrl;
        protected String title;
        protected Bitmap favicon;

        CachedPage(String url, String title) {
            startUrl = url;
            lastUrl = url;
            this.title = TextUtils.isEmpty(title) ? titleFromUrl(url) : title;
        }

        abstract boolean attach(PageLayout pageLayout);

        abstract void detach(PageLayout pageLayout);

        abstract void enrich(PageLayout pageLayout);

        abstract void destroy();

        String getTitle() {
            if (!TextUtils.isEmpty(title)) {
                return title;
            }
            return titleFromUrl(lastUrl == null ? startUrl : lastUrl);
        }

        String getSubtitle() {
            return TextUtils.isEmpty(lastUrl) ? startUrl : lastUrl;
        }
    }

    private class CachedWeb extends CachedPage {
        private WebView webView;
        private int actionBarColor = Theme.getColor(Theme.key_iv_background);
        private int backgroundColor = Theme.getColor(Theme.key_iv_background);

        CachedWeb(String url) {
            super(url, titleFromUrl(url));
        }

        @Override
        boolean attach(PageLayout pageLayout) {
            pageLayout.web = this;
            pageLayout.swipeContainer.setVisibility(View.VISIBLE);
            pageLayout.articleContainer.setVisibility(View.GONE);
            if (webView != null) {
                webView.onResume();
                pageLayout.webViewContainer.replaceWebView(webView);
                webView = null;
                pageLayout.setWebBgColor(true, actionBarColor);
                pageLayout.setWebBgColor(false, backgroundColor);
                return true;
            }
            return lastUrl == null || pageLayout.webViewContainer.loadUrl(lastUrl);
        }

        @Override
        void enrich(PageLayout pageLayout) {
            String currentTitle = pageLayout.webViewContainer.getTitle();
            if (!TextUtils.isEmpty(currentTitle)) {
                title = currentTitle;
            }
            Bitmap currentFavicon = pageLayout.webViewContainer.getFavicon();
            if (currentFavicon != null) {
                favicon = currentFavicon;
            }
            String currentUrl = pageLayout.webViewContainer.getCurrentUrl();
            if (!TextUtils.isEmpty(currentUrl)) {
                lastUrl = currentUrl;
            }
            actionBarColor = pageLayout.webActionBarColor;
            backgroundColor = pageLayout.webBackgroundColor;
        }

        @Override
        void detach(PageLayout pageLayout) {
            enrich(pageLayout);
            WebView preserved = pageLayout.webViewContainer.preserveWebView();
            if (preserved != null) {
                preserved.onPause();
                webView = preserved;
            }
        }

        @Override
        void destroy() {
            if (webView != null) {
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.destroy();
                webView = null;
            }
        }
    }

    private class CachedArticle extends CachedPage {
        private final String json;
        private BrowserInstantViewFragment fragment;
        private int headerHeight = AndroidUtilities.dp(56);
        private float progress;

        CachedArticle(String url, String json) {
            super(url, null);
            this.json = json;
            BrowserInstantViewFragment.cacheInstantViewJson(url, json);
            BrowserInstantViewFragment parsed = new BrowserInstantViewFragment(url, json);
            BrowserPage page = parsed.getPage();
            if (page != null) {
                title = !TextUtils.isEmpty(page.sourceName) ? page.sourceName : page.title;
                lastUrl = !TextUtils.isEmpty(page.url) ? page.url : url;
            }
        }

        @Override
        boolean attach(PageLayout pageLayout) {
            fragment = new BrowserInstantViewFragment(lastUrl, json);
            pageLayout.articleFragment = fragment;
            fragment.setDelegate(new BrowserInstantViewFragment.Delegate() {
                @Override
                public void onBackPressed() {
                    BrowserSheet.this.onAttachedBackPressed();
                }

                @Override
                public void onHeaderHeightChanged(int height) {
                    headerHeight = height;
                    currentHeaderHeight = height;
                    if (pageLayout == pages[0]) {
                        webActionBar.setHeight(height);
                        windowView.invalidate();
                    }
                }

                @Override
                public void onReadingProgressChanged(float value) {
                    progress = value;
                    if (pageLayout == pages[0]) {
                        webActionBar.setProgress(value);
                    }
                }

                @Override
                public void onArticleScrolled() {
                    if (pageLayout == pages[0]) {
                        updatePageProgress();
                        windowView.invalidate();
                    }
                }

                @Override
                public void onOpenExternal(String url) {
                    openExternalUrl(url);
                }

                @Override
                public void onCopyLink() {
                    copyCurrentLink();
                }

                @Override
                public void onFontSizeRequested() {
                    fragment.openFontSizeDialog();
                }

                @Override
                public void onOpenInstantLink(String url, String json) {
                    addInstantPageToStack(url, json, 1);
                }

                @Override
                public boolean shouldCollapseHeader() {
                    return attachedToActionBar;
                }
            });
            currentHeaderHeight = headerHeight;
            webActionBar.setHeight(headerHeight);
            View view = fragment.createEmbeddedView(pageLayout.getContext(), AndroidUtilities.dp(56));
            pageLayout.articleContainer.removeAllViews();
            pageLayout.articleContainer.addView(view, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            pageLayout.articleContainer.setVisibility(View.VISIBLE);
            pageLayout.swipeContainer.setVisibility(View.GONE);
            fragment.scrollToInitialSheetPosition();
            BrowserPage parsedPage = fragment.getPage();
            if (parsedPage != null) {
                title = fragment.getDisplayTitle();
                lastUrl = fragment.getArticleUrl();
            }
            return true;
        }

        @Override
        void detach(PageLayout pageLayout) {
            enrich(pageLayout);
            if (pageLayout.articleFragment == fragment) {
                pageLayout.articleFragment = null;
            }
            pageLayout.articleContainer.removeAllViews();
            pageLayout.articleContainer.setVisibility(View.GONE);
            fragment = null;
        }

        @Override
        void enrich(PageLayout pageLayout) {
            if (fragment != null) {
                title = fragment.getDisplayTitle();
                lastUrl = fragment.getArticleUrl();
                progress = fragment.getReadingProgress();
                headerHeight = currentHeaderHeight;
            }
        }

        @Override
        void destroy() {
            fragment = null;
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
        private final Paint headerBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final AnimatedFloat attachedActionBar = new AnimatedFloat(this, 0, 420, CubicBezierInterpolator.EASE_OUT_QUINT);
        private boolean drawingFromOverlay;
        private boolean movingPage;
        private boolean openingPage;
        private int startMovingHeaderHeight;

        private int startedTrackingPointerId;
        private boolean maybeStartTracking;
        private boolean startedTracking;
        private int startedTrackingX;
        private int startedTrackingY;
        private VelocityTracker velocityTracker;
        private boolean lastWebViewAllowedScroll;
        private boolean stoppedAtFling;
        private boolean nestedVerticalScroll;

        WindowView(Context context) {
            super(context);
            setClipChildren(false);
            setClipToPadding(false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            updateSwipeContainerOffset();
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
            movingPage = false;
            openingPage = false;
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

        private void prepareForMoving(MotionEvent ev) {
            maybeStartTracking = false;
            startedTracking = true;
            startedTrackingX = (int) ev.getX();
            if (pagesStack.size() > 1 && !webActionBar.isSearching() && !webActionBar.isAddressing()) {
                movingPage = true;
                startMovingHeaderHeight = currentHeaderHeight;
                PageLayout previousPage = pages[1];
                CachedPage previousWeb = pagesStack.get(pagesStack.size() - 2);
                previousPage.setPage(previousWeb);
                previousPage.setVisibility(View.VISIBLE);
                previousPage.setAlpha(1f);
                previousPage.setTranslationX(0f);
                sendPageToBack(previousPage);
                webActionBar.setTitle(1, previousPage.getTitle(), false);
                webActionBar.setSubtitle(1, previousPage.getSubtitleForDisplay(), false);
                webActionBar.setTransitionProgress(0f);
            } else {
                movingPage = false;
            }
        }

        boolean handleTouchEvent(MotionEvent event) {
            if (pageSwitchAnimation != null || closeAnimationInProgress || dismissing || released) {
                return false;
            }
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
                // UPSTREAM: ArticleViewer.WindowView L1018
                // lastWebviewAllowedScroll = pages[0] == null || !pages[0].isWeb() || pages[0].swipeContainer.allowingScroll(true) && !pages[0].swipeContainer.isScrolling;
                lastWebViewAllowedScroll = pages[0] == null || pages[0].isArticle() || pages[0].swipeContainer.allowingScroll(true) && !pages[0].swipeContainer.isScrolling;

                if (!nestedVerticalScroll
                        && maybeStartTracking && !startedTracking
                        && dx >= AndroidUtilities.getPixelsInCM(0.4f, true)
                        && Math.abs(dx) / 3 > dy
                        && lastWebViewAllowedScroll) {
                    prepareForMoving(event);
                } else if (startedTracking) {
                    int width = getWidth();
                    if (movingPage && pages[0] != null) {
                        pages[0].setTranslationX(dx);
                        webActionBar.setTransitionProgress(width <= 0 ? 0f : clamp01(dx / (float) width));
                        updatePageProgress();
                    } else {
                        setBackProgress(width <= 0 ? 0f : dx / (float) width);
                    }
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

                if (!nestedVerticalScroll && !startedTracking && velX >= 3500 && velX > Math.abs(velY)) {
                    prepareForMoving(event);
                }
                if (startedTracking) {
                    int width = getWidth();
                    float x = movingPage && pages[0] != null ? pages[0].getX() : backProgress * width;
                    final boolean backAnimation = (x < width * 0.3f && (velX < 2500 || velX < velY)) || !lastWebViewAllowedScroll;
                    float distToMove = backAnimation ? x : (width - x);
                    if (movingPage) {
                        finishMovingPageGesture(backAnimation, distToMove, width);
                    } else {
                        finishBackGesture(backAnimation, distToMove, width);
                    }
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

        private void finishMovingPageGesture(boolean backAnimation, float distToMove, int width) {
            if (pageSwitchAnimation != null) {
                pageSwitchAnimation.cancel();
            }
            PageLayout currentPage = pages[0];
            PageLayout previousPage = pages[1];
            if (currentPage == null || previousPage == null) {
                return;
            }
            pageSwitchAnimation = new AnimatorSet();
            pageSwitchAnimation.playTogether(ObjectAnimator.ofFloat(currentPage, View.TRANSLATION_X, backAnimation ? 0 : width));
            int duration = width <= 0 ? 250 : Math.max((int) (420.0f / width * distToMove), 250);
            pageSwitchAnimation.setDuration(duration);
            pageSwitchAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            pageSwitchAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!backAnimation) {
                        finishPageBack(pagesStack.size() - 2, currentPage, previousPage);
                    } else {
                        previousPage.cleanup();
                        previousPage.setVisibility(View.GONE);
                        currentPage.setTranslationX(0f);
                        webActionBar.setTransitionProgress(0f);
                        movingPage = false;
                        startedTracking = false;
                        closeAnimationInProgress = false;
                        pageSwitchAnimation = null;
                        updatePageProgress();
                    }
                }
            });
            closeAnimationInProgress = true;
            pageSwitchAnimation.start();
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

            // UPSTREAM: ArticleViewer.WindowView.dispatchDraw L14264-14271
            // Draw actionBar background explicitly so title/subtitle text
            // doesn't bleed below the handle during dismiss.
            if (attachedActionBar > 0) {
                canvas.save();
                float t = AndroidUtilities.lerp(top + getListPaddingTop() + 1, 0, attachedActionBar);
                canvas.translate(0, t);
                headerBackgroundPaint.setColor(getActionBarColor());
                float barH = (top + getListPaddingTop() + 1) - t;
                canvas.drawRect(0, 0, getWidth(), barH, headerBackgroundPaint);
                canvas.restore();
            }

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
        public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
            stoppedAtFling = false;
            return nestedScrollAxes == ViewCompat.SCROLL_AXIS_VERTICAL && pages[0] != null && pages[0].isArticle();
        }

        @Override
        public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
            if (!nestedVerticalScroll) {
                nestedVerticalScroll = dy != 0;
            }
            if (pages[0] == null || !pages[0].isArticle() || !pages[0].isAtTop()) {
                return;
            }
            int emptyPadding = getEmptyPadding();
            if (emptyPadding <= 0) {
                return;
            }
            consumed[1] = Math.min((int) (emptyPadding * dismissProgress), dy);
            dismissProgress = Math.max(0f, Math.min(1f, dismissProgress - dy / (float) emptyPadding));
            updateTranslation();
            checkFullyVisible();
        }

        @Override
        public void onStopNestedScroll(View child) {
            nestedVerticalScroll = false;
            if (!stoppedAtFling && pages[0] != null && pages[0].isArticle()) {
                if (dismissProgress > .25f) {
                    dismiss(true);
                } else {
                    animateDismiss(false, true, null);
                }
            }
            super.onStopNestedScroll(child);
        }

        @Override
        public boolean onNestedPreFling(View target, float velocityX, float velocityY) {
            boolean handled = super.onNestedPreFling(target, velocityX, velocityY);
            if (pages[0] != null && pages[0].isArticle()) {
                if (pages[0].isAtTop() && velocityY < -1000) {
                    dismiss(true);
                } else {
                    animateDismiss(false, true, null);
                }
                stoppedAtFling = true;
            }
            return handled;
        }

        @Override
        public boolean dispatchKeyEventPreIme(KeyEvent event) {
            if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP && hideActionBarKeyboardIfInputFocused()) {
                return true;
            }
            return super.dispatchKeyEventPreIme(event);
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

    private static int blendOver(int background, int foreground) {
        return ColorUtils.compositeColors(foreground, background);
    }

    private void toggleBookmark(PageLayout page) {
        if (page == null) {
            return;
        }
        String url = page.getSubtitle();
        if (TextUtils.isEmpty(url)) {
            return;
        }
        boolean saved = BrowserStorage.toggleBookmark(windowView.getContext(), url, page.getTitle(), page.getFavicon());
        Toast.makeText(windowView.getContext(),
                saved ? R.string.app_browser_bookmark_saved_message : R.string.app_browser_bookmark_removed_message,
                Toast.LENGTH_SHORT).show();
    }

    private void openInstantView(PageLayout page) {
        if (page == null || !page.isWeb()) {
            return;
        }
        String json = page.webViewContainer.getInstantViewJson();
        if (TextUtils.isEmpty(json)) {
            Toast.makeText(windowView.getContext(), R.string.app_browser_instant_view_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        addInstantPageToStack(page.webViewContainer.getCurrentUrl(), json, 1);
    }

    private void updateBrowserMenuState(String url) {
        PageLayout page = pages[0];
        webActionBar.setMenuColors(page != null ? page.getBackgroundColor() : Theme.getColor(Theme.key_iv_background));
        webActionBar.setIsTonsite(false);
        webActionBar.setHasForward(page != null && page.isWeb() && page.webViewContainer.canGoForward());
        webActionBar.setIsLoaded(page == null || !page.isWeb() || page.webViewContainer.getProgress() >= 100);
        webActionBar.forwardButtonDrawable.setState(false);
    }

    private void updateInstantViewMenuState(PageLayout page) {
    }

    private void openCurrentPageExternal(PageLayout page) {
        if (page == null) {
            return;
        }
        if (page.isWeb()) {
            page.webViewContainer.openCurrentUrlExternal();
        } else {
            openExternalUrl(page.getSubtitle());
        }
    }

    private void openExternalUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        try {
            windowView.getContext().startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
    }

    private void shareCurrentPage(PageLayout page) {
        if (page == null) {
            return;
        }
        String url = page.isWeb() ? page.webViewContainer.getCurrentUrl() : page.getSubtitle();
        if (TextUtils.isEmpty(url)) {
            return;
        }
        Context context = windowView.getContext();
        try {
            android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(android.content.Intent.EXTRA_TEXT, url);
            CharSequence titleCs = page.isWeb() ? webActionBar.getTitle() : null;
            if (!TextUtils.isEmpty(titleCs)) {
                send.putExtra(android.content.Intent.EXTRA_SUBJECT, titleCs.toString());
            }
            android.content.Intent chooser = android.content.Intent.createChooser(
                    send, context.getString(R.string.app_browser_share));
            chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
        } catch (Exception ignored) {
        }
    }

    @Nullable
    private PageLayout getCurrentWebPage() {
        for (PageLayout page : pages) {
            if (page != null && page.isWeb()) {
                return page;
            }
        }
        return null;
    }
}
