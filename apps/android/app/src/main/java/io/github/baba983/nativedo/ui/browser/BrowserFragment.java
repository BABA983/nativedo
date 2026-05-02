// PORT-MODE: Feature Workflow Port
// WORKFLOW-MAP: docs/telegram-port/browser.md
// UPSTREAM-SCOPE: org/telegram/messenger/browser/Browser.java; org/telegram/ui/ArticleViewer.java; org/telegram/ui/WebviewActivity.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// HOST-WIRING:
//   - NativeDo Profile dev URL entry presents this fragment through ActionBarLayout.
//   - Uses NativeDo WebActionBar and BrowserWebViewContainer instead of Telegram ArticleViewer pages.
// ADAPTED-PRODUCT:
//   - Temporary full-screen BaseFragment host uses Telegram ArticleViewer.Sheet open/dismiss durations
//     until the real attached-sheet/collapsed-tabs subsystem is ported.
// OMITTED:
//   - Telegram ArticleViewer pages stack, collapsed tabs, overlay previews, and Telegram account/chat integrations.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/messenger/browser/Browser.java, org/telegram/ui/ArticleViewer.java,
 * and org/telegram/ui/WebviewActivity.java.
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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.core.graphics.ColorUtils;

import io.github.baba983.nativedo.R;
import io.github.baba983.nativedo.core.SharedConfig;
import io.github.baba983.nativedo.ui.actionbar.ActionBarLayout;
import io.github.baba983.nativedo.ui.actionbar.BaseFragment;
import io.github.baba983.nativedo.ui.actionbar.Theme;
import io.github.baba983.nativedo.ui.components.LayoutHelper;

public class BrowserFragment extends BaseFragment {

    private final String initialUrl;
    private BrowserWebViewContainer webViewContainer;
    private WebActionBar webActionBar;
    private BrowserSearchPanel searchPanel;
    private BrowserAddressPanel addressPanel;
    private FrameLayout rootView;
    private boolean closing;
    private int webActionBarColor = Theme.getColor(Theme.key_iv_background);
    private int webBackgroundColor = Theme.getColor(Theme.key_iv_background);

    public BrowserFragment(String initialUrl) {
        this.initialUrl = initialUrl;
    }

    @Override
    public View createView(Context context) {
        webActionBar = new WebActionBar(context, new WebActionBar.Delegate() {
            @Override
            public void onBackPressed() {
                handleBrowserBack();
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
                        () -> webActionBar.showAddress(url == null ? "" : url, true),
                        v -> {
                            webActionBar.showAddress(false, true);
                            copyCurrentLink();
                        }
                    );
                }
                webActionBar.showAddress("", true);
            }

            @Override
            public void onScrolledProgress(float delta) {
                if (webViewContainer != null) {
                    webViewContainer.setScrollProgress(clamp01(webViewContainer.getScrollProgress() + delta));
                    updatePageProgress();
                }
            }
        });
        actionBar = webActionBar;
        webActionBar.setBackgroundColor(getBrowserActionBarColor());
        webActionBar.setTitle(titleFromUrl(initialUrl));
        webActionBar.setCurrentUrl(initialUrl);
        webActionBar.setCanMinimize(false);
        updateBrowserMenuState(initialUrl);

        FrameLayout root = rootView = new FrameLayout(context);
        root.setBackgroundColor(getBrowserBackgroundColor());
        webViewContainer = new BrowserWebViewContainer(context, new BrowserWebViewContainer.Delegate() {
            @Override
            public void onTitleChanged(String title) {
                if (!closing && title != null && title.trim().length() > 0) {
                    webActionBar.setTitle(title);
                }
            }

            @Override
            public void onUrlChanged(String url, boolean canGoBack, boolean canGoForward) {
                webActionBar.setCurrentUrl(url);
                webActionBar.setForwardAvailable(canGoForward);
                if (webActionBar.getTitle() == null || webActionBar.getTitle().length() == 0) {
                    webActionBar.setTitle(titleFromUrl(url));
                }
                updateBrowserMenuState(url);
            }

            @Override
            public void onProgressChanged(int progress, boolean visible) {
                webActionBar.setLoadingProgress(progress, visible);
            }

            @Override
            public void onWebViewScrolled(int dx, int dy) {
                updatePageProgress();
            }

            @Override
            public void onWebBackgroundChanged(boolean actionBarColor, int color) {
                setWebBackgroundColor(actionBarColor, color);
            }

            @Override
            public void onInstantViewChanged(boolean available) {
                updateInstantViewMenuState();
            }

            @Override
            public void onInstantViewLoadingChanged(boolean loading) {
                updateInstantViewMenuState();
            }

            @Override
            public void onHistoryChanged(boolean available) {
                webActionBar.setHistoryAvailable(available);
            }
        });
        root.addView(webViewContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

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
        root.addView(searchPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 51, Gravity.BOTTOM | Gravity.START));
        searchPanel.attachKeyboardTracking(root);

        addressPanel = new BrowserAddressPanel(context);
        root.addView(addressPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.START));

        if (!webViewContainer.loadUrl(initialUrl)) {
            root.post(() -> {
                if (closing) {
                    return;
                }
                closing = true;
                finishFragment();
            });
        }
        return root;
    }

    @Override
    public boolean onBackPressed() {
        if (!super.onBackPressed()) {
            return false;
        }
        handleBrowserBack();
        return false;
    }

    @Override
    public boolean canBeginSlide() {
        return false;
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return false;
    }

    @Override
    public void onResume() {
        if (webViewContainer != null) {
            webViewContainer.onResume();
        }
    }

    @Override
    public void onPause() {
        if (webViewContainer != null) {
            if (closing) {
                if (searchPanel != null) {
                    searchPanel.show(false, false);
                    searchPanel.detachKeyboardTracking(webViewContainer);
                }
                if (webActionBar != null && webActionBar.isAddressing()) {
                    webActionBar.showAddress(false, false);
                }
                webViewContainer.destroyWebView();
            } else {
                webViewContainer.onPause();
            }
        }
    }

    private void handleBrowserBack() {
        if (closing) {
            return;
        }
        if (webActionBar != null && webActionBar.isSearching()) {
            webActionBar.showSearch(false, true);
            return;
        }
        if (webActionBar != null && webActionBar.isAddressing()) {
            webActionBar.closeAddress(true);
            return;
        }
        if (webViewContainer != null && webViewContainer.canGoBack()) {
            webViewContainer.goBack();
            return;
        }
        closing = true;
        if (parentLayout instanceof ActionBarLayout && ((ActionBarLayout) parentLayout).closeLastFragmentFromBottom()) {
            return;
        }
        finishFragment();
    }

    private void handleMenuItem(int id) {
        if (webViewContainer == null) {
            return;
        }
        if (id == WebActionBar.MENU_REFRESH) {
            webViewContainer.reload();
        } else if (id == WebActionBar.MENU_FORWARD) {
            webViewContainer.goForward();
        } else if (id == WebActionBar.MENU_OPEN_EXTERNAL) {
            webViewContainer.openCurrentUrlExternal();
        } else if (id == WebActionBar.MENU_COPY_LINK) {
            copyCurrentLink();
        } else if (id == WebActionBar.MENU_SEARCH) {
            webActionBar.showSearch(true, true);
        } else if (id == WebActionBar.MENU_BOOKMARK) {
            toggleBookmark();
        } else if (id == WebActionBar.MENU_HISTORY) {
            BrowserFeatureSheet.showHistory(this, BrowserListFragment.Mode.HISTORY, this::loadBrowserUrl);
        } else if (id == WebActionBar.MENU_BOOKMARKS) {
            BrowserFeatureSheet.showHistory(this, BrowserListFragment.Mode.BOOKMARKS, this::loadBrowserUrl);
        } else if (id == WebActionBar.MENU_SETTINGS) {
            BrowserFeatureSheet.showSettings(this, this::loadBrowserUrl, () -> updateBrowserMenuState(
                    webViewContainer == null ? null : webViewContainer.getCurrentUrl()));
        } else if (id == WebActionBar.MENU_INSTANT_VIEW) {
            openInstantView();
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

    private void copyCurrentLink() {
        String url = webViewContainer == null ? null : webViewContainer.getCurrentUrl();
        if (url == null || url.trim().isEmpty()) {
            return;
        }
        Context context = webViewContainer.getContext();
        ClipboardManager clipboardManager = context.getSystemService(ClipboardManager.class);
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("link", url));
            Toast.makeText(context, R.string.app_browser_link_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void loadAddressValue(String value) {
        String url = normalizeAddressValue(value);
        if (url == null || webViewContainer == null) {
            return;
        }
        loadBrowserUrl(url);
    }

    private void loadBrowserUrl(String url) {
        if (TextUtils.isEmpty(url) || webViewContainer == null) {
            return;
        }
        if (webViewContainer.loadUrl(url)) {
            webActionBar.setCurrentUrl(url);
            webActionBar.setTitle(titleFromUrl(url));
            updateBrowserMenuState(url);
        }
    }

    private void updatePageProgress() {
        if (webActionBar != null && webViewContainer != null) {
            webActionBar.setProgress(webViewContainer.getScrollProgress());
        }
    }

    private int getBrowserBackgroundColor() {
        return SharedConfig.adaptableColorInBrowser ? webBackgroundColor : Theme.getColor(Theme.key_iv_background);
    }

    private int getBrowserActionBarColor() {
        return SharedConfig.adaptableColorInBrowser ? webActionBarColor : Theme.getColor(Theme.key_iv_background);
    }

    private void setWebBackgroundColor(boolean actionBarColor, int color) {
        if (actionBarColor) {
            webActionBarColor = blendOver(Theme.getColor(Theme.key_iv_background), color);
            if (SharedConfig.adaptableColorInBrowser && webActionBar != null) {
                webActionBar.setColors(webActionBarColor, true);
            }
        } else {
            webBackgroundColor = blendOver(0xFFFFFFFF, color);
            if (SharedConfig.adaptableColorInBrowser) {
                if (rootView != null) {
                    rootView.setBackgroundColor(webBackgroundColor);
                }
                if (webViewContainer != null) {
                    webViewContainer.setBrowserBackgroundColor(webBackgroundColor);
                }
            }
        }
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
        if (httpsUri.getHost() != null && !httpsUri.getHost().trim().isEmpty()) {
            return httpsUri.toString();
        }
        return BrowserStorage.buildSearchUrl(getContext(), trimmed);
    }

    private String titleFromUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return "";
        }
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        return host == null || host.trim().isEmpty() ? url : host;
    }

    private static int blendOver(int background, int foreground) {
        return ColorUtils.compositeColors(foreground, background);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private void toggleBookmark() {
        if (webViewContainer == null) {
            return;
        }
        String url = webViewContainer.getCurrentUrl();
        if (TextUtils.isEmpty(url)) {
            return;
        }
        boolean saved = BrowserStorage.toggleBookmark(getContext(), url, webViewContainer.getTitle(), webViewContainer.getFavicon());
        webActionBar.setBookmarkSaved(saved);
        Toast.makeText(getContext(),
                saved ? R.string.app_browser_bookmark_saved_message : R.string.app_browser_bookmark_removed_message,
                Toast.LENGTH_SHORT).show();
    }

    private void openInstantView() {
        if (webViewContainer == null) {
            return;
        }
        String json = webViewContainer.getInstantViewJson();
        if (TextUtils.isEmpty(json)) {
            Toast.makeText(getContext(), R.string.app_browser_instant_view_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        presentFragment(new BrowserInstantViewFragment(webViewContainer.getCurrentUrl(), json));
    }

    private void updateBrowserMenuState(String url) {
        webActionBar.setBookmarkSaved(BrowserStorage.isBookmarked(getContext(), url));
        webActionBar.setHistoryAvailable(!BrowserStorage.getHistory(getContext()).isEmpty());
        webActionBar.setForwardAvailable(webViewContainer != null && webViewContainer.canGoForward());
        updateInstantViewMenuState();
    }

    private void updateInstantViewMenuState() {
        boolean available = webViewContainer != null && !TextUtils.isEmpty(webViewContainer.getInstantViewJson());
        boolean loading = webViewContainer != null && webViewContainer.isInstantViewLoading();
        webActionBar.setInstantViewAvailable(available || loading, available);
    }
}
