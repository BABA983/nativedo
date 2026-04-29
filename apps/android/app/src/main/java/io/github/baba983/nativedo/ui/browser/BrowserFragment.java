// UPSTREAM-LEVEL: L2
// UPSTREAM-PATH:  org/telegram/messenger/browser/Browser.java; org/telegram/ui/ArticleViewer.java; org/telegram/ui/WebviewActivity.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// SUBSYSTEMS: browser-entry=L2, bottom-up-animation=L0, webview-lifecycle=L1
// HOST-WIRING:
//   - NativeDo Profile dev URL entry presents this fragment through ActionBarLayout.
//   - Uses NativeDo WebActionBar and BrowserWebViewContainer instead of Telegram ArticleViewer pages.
// ADAPTED:
//   - Temporary full-screen BaseFragment host uses Telegram ArticleViewer.Sheet open/dismiss durations
//     until the real attached-sheet/collapsed-tabs subsystem is ported.
// OMITTED:
//   - Telegram ArticleViewer pages stack, collapsed tabs, overlay previews, bookmarks, history, search,
//     Instant View, and Telegram account/chat integrations.
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
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import io.github.baba983.nativedo.R;
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
    private boolean closing;

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
        });
        actionBar = webActionBar;
        webActionBar.setTitle(titleFromUrl(initialUrl));
        webActionBar.setCurrentUrl(initialUrl);

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
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
                if (webActionBar.getTitle() == null || webActionBar.getTitle().length() == 0) {
                    webActionBar.setTitle(titleFromUrl(url));
                }
            }

            @Override
            public void onProgressChanged(int progress, boolean visible) {
                webActionBar.setProgress(progress, visible);
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

        webViewContainer.loadUrl(initialUrl);
        return root;
    }

    @Override
    public boolean onBackPressed() {
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
        } else if (id == WebActionBar.MENU_OPEN_EXTERNAL) {
            webViewContainer.openCurrentUrlExternal();
        } else if (id == WebActionBar.MENU_COPY_LINK) {
            copyCurrentLink();
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
}
