/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/ui/web/BotWebViewContainer.java and org/telegram/ui/WebviewActivity.java.
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.baba983.nativedo.R;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.bots.ChatAttachAlertBotWebViewLayout.WebViewSwipeContainer;
import org.telegram.ui.web.BrowserHistory;
import org.telegram.ui.web.BotWebViewContainer;
import org.telegram.ui.web.MHTML;
import org.telegram.ui.web.RestrictedDomainsList;
import org.telegram.ui.web.WebMetadataCache;
import org.telegram.messenger.browser.Browser;
import io.github.baba983.nativedo.ui.browser.BrowserImageDecoder;
public class BrowserWebViewContainer extends FrameLayout {

    public interface PreviewCallback {
        void onPreviewReady(@Nullable Bitmap bitmap);
    }

    public interface Delegate {
        void onTitleChanged(String title);

        void onUrlChanged(String url, boolean canGoBack, boolean canGoForward);

        void onProgressChanged(int progress, boolean visible);

        default void onWebBackgroundChanged(boolean actionBarColor, int color) {}

        default void onWebSiteNameChanged(@Nullable String siteName) {}

        default void onWebViewScrolled(int dx, int dy) {}

        default void onInstantViewChanged(boolean available) {}

        default void onInstantViewLoadingChanged(boolean loading) {}

        default void onHistoryChanged(boolean available) {}
    }

    public interface InstantViewCallback {
        void onResult(@Nullable String json);
    }

    private final Delegate delegate;
    private final BrowserWebViewProxy webViewProxy = new BrowserWebViewProxy();
    private final ReaderInstantViewBridge readerInstantViewBridge = new ReaderInstantViewBridge();
    private BrowserWebView webView;
    private String currentUrl;
    private String browserExtensionScript;
    private String instantViewScript;
    private String openCollapsedScript;
    private Bitmap currentFavicon;
    private String currentSiteName;
    private int currentActionBarColor;
    private int currentBackgroundColor;
    private int currentProgress;
    private boolean keyboardFocusable;
    private boolean wasFocusable;
    private String currentInstantViewJson;
    private boolean instantViewLoading;
    private InstantViewCallback instantViewCallback;
    private String pendingInstantViewRequestUrl;
    private FrameLayout instantViewReaderContainer;
    private WebView instantViewReaderWebView;
    private File instantViewArchiveFile;
    private BrowserHistory.Entry currentHistoryEntry;
    private static final ExecutorService INSTANT_IMAGE_EXECUTOR = Executors.newSingleThreadExecutor();

    private int searchIndex;
    private int searchCount;
    private Runnable searchListener;

    public BrowserWebViewContainer(Context context, Delegate delegate) {
        super(context);
        this.delegate = delegate;
        setBackgroundColor(Theme.getColor(Theme.key_iv_background));

        setupWebView();
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupWebView() {
        webView = new BrowserWebView(getContext(), this);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setBackgroundColor(Theme.getColor(Theme.key_iv_background));
        webView.setVerticalScrollBarEnabled(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);
        cookieManager.flush();

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setSupportMultipleWindows(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setSaveFormData(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSafeBrowsingEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setTextSize(WebSettings.TextSize.NORMAL);

        bindWebViewCallbacks();

        addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    @SuppressLint("AddJavascriptInterface")
    private void bindWebViewCallbacks() {
        if (webView == null) {
            return;
        }
        webView.setOwner(this);
        webView.removeJavascriptInterface("TelegramWebviewProxy");
        webView.addJavascriptInterface(webViewProxy, "TelegramWebviewProxy");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return request != null && shouldOpenOutside(request.getUrl(), request.isForMainFrame());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return shouldOpenOutside(Uri.parse(url), true);
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                currentUrl = url;
                currentHistoryEntry = null;
                notifyUrlChanged();
                notifyProgress(10);
                super.onPageStarted(view, url, favicon);
                setInjectedJS(false);
                cleanupInstantViewReader();
                currentInstantViewJson = null;
                currentSiteName = null;
                currentFavicon = favicon;
                currentActionBarColor = 0;
                currentBackgroundColor = 0;
                pendingInstantViewRequestUrl = null;
                if (delegate != null) {
                    delegate.onInstantViewChanged(false);
                }
                setInstantViewLoading(true);
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                injectBrowserExtension();
                super.onPageCommitVisible(view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                currentUrl = url;
                notifyUrlChanged();
                notifyProgress(100);
                injectBrowserExtension();
                super.onPageFinished(view, url);
                saveCurrentPageToHistory();
                if (delegate != null) {
                    delegate.onHistoryChanged(!BrowserHistory.getHistory().isEmpty());
                }
                generateInstantView(null);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                notifyProgress(newProgress);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (delegate != null && title != null && title.trim().length() > 0) {
                    delegate.onTitleChanged(title);
                }
                saveCurrentMetadata();
                saveCurrentPageToHistory();
            }

            @Override
            public void onReceivedIcon(WebView view, Bitmap icon) {
                currentFavicon = icon;
                saveCurrentMetadata();
                saveCurrentPageToHistory();
            }
        });

        webView.setFindListener((activeMatchOrdinal, numberOfMatches, isDoneCounting) -> {
            searchIndex = activeMatchOrdinal;
            searchCount = numberOfMatches;
            if (searchListener != null) {
                searchListener.run();
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (openExternal(Uri.parse(url))) {
                Toast.makeText(getContext(), R.string.app_browser_download_opened_in_browser, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), R.string.app_browser_download_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == webView && (AndroidUtilities.makingGlobalBlurBitmap || getLayerType() == LAYER_TYPE_HARDWARE && !canvas.isHardwareAccelerated())) {
            return true;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    public boolean loadUrl(String url) {
        if (webView == null) {
            setupWebView();
        }
        if (shouldOpenHttpOutside(url)) {
            openExternal(Uri.parse(url));
            return false;
        }
        currentUrl = url;
        webView.loadUrl(url);
        notifyUrlChanged();
        return true;
    }

    public void reload() {
        if (webView != null) {
            webView.reload();
        }
    }

    public boolean canGoBack() {
        return webView != null && webView.canGoBack();
    }

    public void goBack() {
        if (webView != null) {
            webView.goBack();
        }
    }

    public boolean canGoForward() {
        return webView != null && webView.canGoForward();
    }

    /** Mirrors upstream WebViewSwipeContainer allowingScroll / PageLayout.isAtTop(). */
    public boolean isAtTop() {
        return webView == null || webView.getScrollY() <= 0;
    }

    public void goForward() {
        if (webView != null) {
            webView.goForward();
        }
    }

    public void search(String text, Runnable onUpdated) {
        if (webView == null) {
            return;
        }
        searchListener = onUpdated;
        if (text == null || text.isEmpty()) {
            searchIndex = 0;
            searchCount = 0;
            webView.clearMatches();
            if (searchListener != null) {
                searchListener.run();
            }
        } else {
            webView.findAllAsync(text);
        }
    }

    public void findNext(boolean forward) {
        if (webView != null) {
            webView.findNext(forward);
        }
    }

    public int getSearchIndex() {
        return searchIndex;
    }

    public int getSearchCount() {
        return searchCount;
    }

    public void clearSearch() {
        if (webView != null) {
            webView.clearMatches();
        }
        searchIndex = 0;
        searchCount = 0;
        searchListener = null;
    }

    public boolean openCurrentUrlExternal() {
        if (currentUrl == null) {
            return false;
        }
        return openExternal(Uri.parse(currentUrl));
    }

    public void onResume() {
        if (webView != null) {
            webView.onResume();
        }
    }

    public void onPause() {
        if (webView != null) {
            webView.onPause();
        }
    }

    public void setKeyboardFocusable(boolean focusable) {
        keyboardFocusable = focusable;
        updateKeyboardFocusable();
    }

    public void destroyWebView() {
        cleanupInstantViewReader();
        if (webView == null) {
            return;
        }
        removeView(webView);
        webView.stopLoading();
        webView.loadUrl("about:blank");
        webView.destroy();
        webView = null;
    }

    @Nullable
    public WebView preserveWebView() {
        cleanupInstantViewReader();
        if (webView == null) {
            return null;
        }
        BrowserWebView preserved = webView;
        removeView(preserved);
        webView = null;
        preserved.setOwner(null);
        preserved.removeJavascriptInterface("TelegramWebviewProxy");
        preserved.setWebViewClient(new WebViewClient());
        preserved.setWebChromeClient(null);
        preserved.setDownloadListener(null);
        return preserved;
    }

    public void replaceWebView(@Nullable WebView preservedWebView) {
        if (!(preservedWebView instanceof BrowserWebView)) {
            return;
        }
        if (webView != null && webView != preservedWebView) {
            destroyWebView();
        }
        if (preservedWebView.getParent() instanceof FrameLayout) {
            ((FrameLayout) preservedWebView.getParent()).removeView(preservedWebView);
        }
        webView = (BrowserWebView) preservedWebView;
        bindWebViewCallbacks();
        addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        currentUrl = webView.getUrl();
        currentProgress = webView.getProgress();
        currentFavicon = webView.getFavicon();
        currentSiteName = null;
        notifyUrlChanged();
        notifyProgress(currentProgress);
    }

    @Nullable
    public String getCurrentUrl() {
        return currentUrl;
    }

    @Nullable
    public String getTitle() {
        return webView == null ? null : webView.getTitle();
    }

    @Nullable
    public WebView getWebView() {
        return webView;
    }

    @Nullable
    public Bitmap getFavicon() {
        return currentFavicon;
    }

    @Nullable
    public WebBackForwardList copyBackForwardList() {
        return webView == null ? null : webView.copyBackForwardList();
    }

    public void goBackToHistoryIndex(int index) {
        if (webView == null) {
            return;
        }
        WebBackForwardList history = webView.copyBackForwardList();
        int currentIndex = history == null ? -1 : history.getCurrentIndex();
        if (index < 0 || currentIndex <= index) {
            return;
        }
        for (int i = 0; i < currentIndex - index; i++) {
            webView.goBack();
        }
    }

    public int getProgress() {
        return currentProgress;
    }

    @Nullable
    public String getInstantViewJson() {
        return currentInstantViewJson;
    }

    public boolean isInstantViewLoading() {
        return instantViewLoading;
    }

    public float getScrollProgress() {
        return webView == null ? 0f : webView.getScrollProgress();
    }

    public void setScrollProgress(float progress) {
        if (webView != null) {
            webView.setScrollProgress(progress);
        }
    }

    public void setBrowserBackgroundColor(int color) {
        setBackgroundColor(color);
        if (webView != null) {
            webView.setBackgroundColor(color);
        }
    }

    public int getWebViewScrollY() {
        return webView == null ? 0 : webView.getScrollY();
    }

    public int getWebViewWidth() {
        return webView == null ? 0 : webView.getWidth();
    }

    public int getWebViewHeight() {
        return webView == null ? 0 : webView.getHeight();
    }

    public void capturePreviewBitmap(PreviewCallback callback) {
        if (webView == null || webView.getWidth() <= 0 || webView.getHeight() <= 0) {
            if (callback != null) {
                callback.onPreviewReady(null);
            }
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            renderHardwareViewToBitmap(webView, -webView.getScrollY(), callback);
            return;
        }
        if (callback != null) {
            callback.onPreviewReady(capturePreviewBitmapSync());
        }
    }

    @Nullable
    private Bitmap capturePreviewBitmapSync() {
        if (webView == null || webView.getWidth() <= 0 || webView.getHeight() <= 0) {
            return null;
        }
        Bitmap bitmap = Bitmap.createBitmap(webView.getWidth(), webView.getHeight(), Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);
        canvas.translate(0, -webView.getScrollY());
        webView.draw(canvas);
        return bitmap;
    }

    private void updateKeyboardFocusable() {
        boolean focusable = keyboardFocusable;
        if (wasFocusable == focusable) {
            return;
        }
        if (!focusable) {
            setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
            setFocusable(false);
            if (webView != null) {
                webView.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
                webView.clearFocus();
            }
            AndroidUtilities.hideKeyboard(this);
        } else {
            setDescendantFocusability(FOCUS_BEFORE_DESCENDANTS);
            setFocusable(true);
            if (webView != null) {
                webView.setDescendantFocusability(FOCUS_BEFORE_DESCENDANTS);
            }
        }
        wasFocusable = focusable;
    }

    private void injectBrowserExtension() {
        if (webView == null) {
            return;
        }
        setInjectedJS(true);
        webView.evaluateJS(readBrowserExtensionScript());
    }

    private String readBrowserExtensionScript() {
        if (browserExtensionScript != null) {
            return browserExtensionScript;
        }
        StringBuilder builder = new StringBuilder();
        try (InputStream stream = getResources().openRawResource(R.raw.app_browser_webview_ext);
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        } catch (Exception ignored) {
        }
        browserExtensionScript = builder.toString().replace("$DEBUG$", "false");
        return browserExtensionScript;
    }

    public void generateInstantView(@Nullable InstantViewCallback callback) {
        instantViewCallback = callback;
        cleanupInstantViewReader();
        if (webView == null || TextUtils.isEmpty(currentUrl)) {
            handleInstantViewResult("");
            return;
        }
        pendingInstantViewRequestUrl = currentUrl;
        setInstantViewLoading(true);
        captureInstantViewArchive(webView, currentUrl);
    }

    private String readInstantViewScript() {
        if (instantViewScript != null) {
            return instantViewScript;
        }
        StringBuilder builder = new StringBuilder();
        builder.append(readRawScript(R.raw.app_browser_instant_view_dompurify)).append('\n');
        builder.append(readRawScript(R.raw.app_browser_instant_view_readability)).append('\n');
        builder.append(readRawScript(R.raw.app_browser_instant_view_readerable)).append('\n');
        builder.append(readRawScript(R.raw.app_browser_instant_view_runtime)).append('\n');
        instantViewScript = builder.toString().replace("$DEBUG$", "false");
        return instantViewScript;
    }

    private String readOpenCollapsedScript(boolean open) {
        if (openCollapsedScript == null) {
            openCollapsedScript = readRawScript(R.raw.app_browser_open_collapsed);
        }
        return openCollapsedScript.replace("$OPEN$", open ? "true" : "false");
    }

    private String readRawScript(int resId) {
        StringBuilder builder = new StringBuilder();
        try (InputStream stream = getResources().openRawResource(resId);
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        } catch (Exception ignored) {
        }
        return builder.toString();
    }

    private void setInjectedJS(boolean injected) {
        if (getParent() instanceof WebViewSwipeContainer) {
            WebViewSwipeContainer swipe = (WebViewSwipeContainer) getParent();
            if (swipe.getWebView() instanceof BotWebViewContainer.MyWebView) {
                ((BotWebViewContainer.MyWebView) swipe.getWebView()).injectedJS = injected;
            }
        }
    }

    private void onWebEventReceived(String type, String data) {
        if ("actionBarColor".equals(type) || "navigationBarColor".equals(type)) {
            try {
                JSONArray jsonArray = new JSONArray(data);
                boolean isActionBarColor = "actionBarColor".equals(type);
                int color = Color.argb(
                        (int) Math.round(jsonArray.optDouble(3, 1) * 255),
                        (int) Math.round(jsonArray.optDouble(0)),
                        (int) Math.round(jsonArray.optDouble(1)),
                        (int) Math.round(jsonArray.optDouble(2))
                );
                if (isActionBarColor) {
                    currentActionBarColor = color;
                } else {
                    currentBackgroundColor = color;
                }
                saveCurrentMetadata();
                if (delegate != null) {
                    delegate.onWebBackgroundChanged(isActionBarColor, color);
                }
            } catch (Exception ignored) {
            }
        } else if ("siteName".equals(type)) {
            currentSiteName = TextUtils.isEmpty(data) ? null : data;
            saveCurrentMetadata();
            if (delegate != null) {
                delegate.onWebSiteNameChanged(currentSiteName);
            }
        } else if ("siteNameEmpty".equals(type)) {
            currentSiteName = "";
            saveCurrentMetadata();
            if (delegate != null) {
                delegate.onWebSiteNameChanged(null);
            }
        } else if ("allowScroll".equals(type)) {
            boolean x = true;
            boolean y = true;
            try {
                JSONArray jsonArray = new JSONArray(data);
                x = jsonArray.optBoolean(0, true);
                y = jsonArray.optBoolean(1, true);
            } catch (Exception ignored) {
            }
            if (getParent() instanceof WebViewSwipeContainer) {
                ((WebViewSwipeContainer) getParent()).allowThisScroll(x, y);
            }
        }
    }

    private void renderHardwareViewToBitmap(View view, float offsetY, PreviewCallback callback) {
        SurfaceTexture surfaceTexture = new SurfaceTexture(false);
        surfaceTexture.setDefaultBufferSize(view.getWidth(), view.getHeight());
        Surface surface = new Surface(surfaceTexture);
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas hardwareCanvas = surface.lockHardwareCanvas();
        hardwareCanvas.translate(0, offsetY);
        view.draw(hardwareCanvas);
        surface.unlockCanvasAndPost(hardwareCanvas);

        PixelCopy.request(surface, bitmap, copyResult -> {
            Bitmap result = copyResult == PixelCopy.SUCCESS ? bitmap : null;
            if (result == null) {
                bitmap.recycle();
            }
            surface.release();
            surfaceTexture.release();
            if (callback != null) {
                callback.onPreviewReady(result);
            }
        }, new Handler(Looper.getMainLooper()));
    }

    private boolean shouldOpenOutside(Uri uri, boolean mainFrame) {
        if (uri == null || !mainFrame) {
            return false;
        }
        String scheme = uri.getScheme();
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            if (shouldOpenHttpOutside(uri.toString())) {
                return openExternal(uri);
            }
            return false;
        }
        if ("about".equalsIgnoreCase(scheme)) {
            return false;
        }
        return openExternal(uri);
    }

    private boolean shouldOpenHttpOutside(@Nullable String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        return !BrowserStorage.openExternalLinksInApp(getContext())
                || RestrictedDomainsList.getInstance().isRestrictedUrl(url);
    }

    private boolean openExternal(Uri uri) {
        return Browser.openInExternalBrowser(getContext(), uri.toString(), true);
    }

    private void notifyProgress(int progress) {
        int clamped = Math.max(0, Math.min(100, progress));
        currentProgress = clamped;
        boolean visible = clamped < 100;
        if (delegate != null) {
            delegate.onProgressChanged(clamped, visible);
        }
    }

    private void notifyUrlChanged() {
        if (delegate != null) {
            delegate.onUrlChanged(currentUrl, webView != null && webView.canGoBack(), webView != null && webView.canGoForward());
        }
    }

    private void saveCurrentPageToHistory() {
        if (TextUtils.isEmpty(currentUrl) || "about:blank".equals(currentUrl)) {
            return;
        }
        BrowserStorage.pushHistory(getContext(), currentUrl, getTitle(), currentFavicon);
        WebMetadataCache.WebMetadata meta = buildCurrentMetadata();
        if (meta == null) {
            return;
        }
        WebMetadataCache.getInstance().save(meta);
        if (currentHistoryEntry == null || !TextUtils.equals(currentHistoryEntry.url, currentUrl)) {
            currentHistoryEntry = new BrowserHistory.Entry();
            currentHistoryEntry.id = Utilities.fastRandom.nextLong();
            currentHistoryEntry.time = System.currentTimeMillis();
            currentHistoryEntry.url = currentUrl;
        }
        currentHistoryEntry.meta = meta;
        BrowserHistory.pushHistory(currentHistoryEntry);
    }

    private void saveCurrentMetadata() {
        if (TextUtils.isEmpty(currentUrl) || "about:blank".equals(currentUrl)) {
            return;
        }
        BrowserMetadataCache.save(
                getContext(),
                currentUrl,
                getTitle(),
                currentSiteName,
                currentFavicon,
                currentActionBarColor,
                currentBackgroundColor
        );
        WebMetadataCache.WebMetadata meta = buildCurrentMetadata();
        if (meta != null) {
            WebMetadataCache.getInstance().save(meta);
        }
    }

    @Nullable
    private WebMetadataCache.WebMetadata buildCurrentMetadata() {
        WebMetadataCache.WebMetadata metadata = new WebMetadataCache.WebMetadata();
        metadata.domain = AndroidUtilities.getHostAuthority(currentUrl, true);
        if (TextUtils.isEmpty(metadata.domain)) {
            return null;
        }
        metadata.title = getTitle();
        metadata.sitename = currentSiteName;
        metadata.favicon = currentFavicon;
        metadata.actionBarColor = currentActionBarColor;
        metadata.backgroundColor = currentBackgroundColor;
        return metadata;
    }

    private void captureInstantViewArchive(BrowserWebView sourceWebView, String requestUrl) {
        if (sourceWebView == null) {
            handleInstantViewResult("");
            return;
        }
        final File archiveFile;
        try {
            archiveFile = File.createTempFile("browser_instant_view_", ".mht", getContext().getCacheDir());
        } catch (Exception ignored) {
            handleInstantViewResult("");
            return;
        }
        sourceWebView.evaluateJavascript(readOpenCollapsedScript(true), value ->
                sourceWebView.saveWebArchive(archiveFile.getAbsolutePath(), false, filename -> {
                    sourceWebView.evaluateJS(readOpenCollapsedScript(false));
                    MHTML archive = null;
                    try {
                        archive = new MHTML(archiveFile);
                    } catch (Exception ignored) {
                        archive = null;
                    }
                    if (archive == null || archive.entries.isEmpty()) {
                        archiveFile.delete();
                        handleInstantViewResult("");
                        return;
                    }
                    startInstantViewReader(requestUrl, archive, archiveFile);
                })
        );
    }

    private void startInstantViewReader(String requestUrl, MHTML archive, File archiveFile) {
        cleanupInstantViewReader();
        instantViewArchiveFile = archiveFile;
        instantViewReaderContainer = new FrameLayout(getContext()) {
            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                return false;
            }

            @Override
            public boolean onTouchEvent(android.view.MotionEvent event) {
                return false;
            }

            @Override
            public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
                return false;
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(
                        MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(500), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(500), MeasureSpec.EXACTLY)
                );
            }
        };
        addView(instantViewReaderContainer, LayoutHelper.createFrame(1, 1));

        WebView readerWebView = new WebView(getContext());
        instantViewReaderWebView = readerWebView;
        WebSettings settings = readerWebView.getSettings();
        settings.setAllowContentAccess(false);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setJavaScriptEnabled(true);
        settings.setSaveFormData(false);
        settings.setGeolocationEnabled(false);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        readerWebView.removeJavascriptInterface("Instant");
        readerWebView.addJavascriptInterface(readerInstantViewBridge, "Instant");
        readerWebView.setWebViewClient(new WebViewClient() {
            private boolean firstLoad = true;

            @Nullable
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return interceptInstantViewRequest(archive, request == null || request.getUrl() == null ? null : request.getUrl().toString());
            }

            @SuppressWarnings("deprecation")
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                return interceptInstantViewRequest(archive, url);
            }

            @Nullable
            private WebResourceResponse interceptInstantViewRequest(MHTML instantArchive, @Nullable String url) {
                if (firstLoad) {
                    firstLoad = false;
                    String html = "<script>\n" + readInstantViewScript() + "\n</script>";
                    return new WebResourceResponse("text/html", "UTF-8",
                            new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
                }
                MHTML.Entry entry = null;
                String mime = "text/html";
                if (url != null && url.endsWith("/index.html")) {
                    entry = instantArchive.entries.get(0);
                } else if (url != null) {
                    entry = instantArchive.entriesByLocation.get(url);
                    if (entry != null) {
                        mime = entry.getType();
                    }
                }
                if (entry == null) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                        return new WebResourceResponse(mime, "UTF-8", null);
                    }
                    return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, null);
                }
                if (!"text/html".equalsIgnoreCase(mime) && !"text/css".equalsIgnoreCase(mime)) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                        return new WebResourceResponse(mime, "UTF-8", null);
                    }
                    return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, null);
                }
                try {
                    return new WebResourceResponse(mime, null, entry.getInputStream());
                } catch (Exception ignored) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                        return new WebResourceResponse(mime, "UTF-8", null);
                    }
                    return new WebResourceResponse("text/plain", "utf-8", 503, "Server Error", null, null);
                }
            }
        });
        instantViewReaderContainer.addView(readerWebView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        readerWebView.loadUrl(requestUrl);
    }

    private void cleanupInstantViewReader() {
        if (instantViewReaderWebView != null) {
            instantViewReaderWebView.stopLoading();
            instantViewReaderWebView.removeJavascriptInterface("Instant");
            instantViewReaderWebView.destroy();
            instantViewReaderWebView = null;
        }
        if (instantViewReaderContainer != null) {
            removeView(instantViewReaderContainer);
            instantViewReaderContainer = null;
        }
        if (instantViewArchiveFile != null) {
            instantViewArchiveFile.delete();
            instantViewArchiveFile = null;
        }
    }

    private void handleInstantViewResult(@Nullable String json) {
        File archiveFile = instantViewArchiveFile;
        instantViewArchiveFile = null;
        cleanupInstantViewReaderShell();
        String requestUrl = pendingInstantViewRequestUrl;
        boolean urlMatches = TextUtils.equals(currentUrl, requestUrl);
        pendingInstantViewRequestUrl = null;
        if (!urlMatches && instantViewCallback == null) {
            setInstantViewLoading(false);
            if (archiveFile != null) {
                archiveFile.delete();
            }
            return;
        }
        String normalized = "null".equals(json) ? null : json;
        if (archiveFile != null && !TextUtils.isEmpty(normalized)) {
            cacheInstantImagesAsync(archiveFile, () -> finishInstantViewResult(requestUrl, normalized));
        } else {
            if (archiveFile != null) {
                archiveFile.delete();
            }
            finishInstantViewResult(requestUrl, normalized);
        }
    }

    private void finishInstantViewResult(@Nullable String url, @Nullable String json) {
        if (!TextUtils.equals(currentUrl, url) && instantViewCallback == null) {
            setInstantViewLoading(false);
            return;
        }
        currentInstantViewJson = TextUtils.isEmpty(json) ? null : json;
        if (currentInstantViewJson != null) {
            // Make this URL→json mapping discoverable for in-document Instant View link
            // navigation (BrowserInstantViewFragment.openLinkOrInstantView).
            BrowserInstantViewFragment.cacheInstantViewJson(url, currentInstantViewJson);
        }
        setInstantViewLoading(false);
        if (delegate != null) {
            delegate.onInstantViewChanged(!TextUtils.isEmpty(currentInstantViewJson));
        }
        if (instantViewCallback != null) {
            InstantViewCallback callback = instantViewCallback;
            instantViewCallback = null;
            callback.onResult(currentInstantViewJson);
        }
    }

    private void setInstantViewLoading(boolean loading) {
        if (instantViewLoading == loading) {
            return;
        }
        instantViewLoading = loading;
        if (delegate != null) {
            delegate.onInstantViewLoadingChanged(loading);
        }
    }

    private void cleanupInstantViewReaderShell() {
        if (instantViewReaderWebView != null) {
            instantViewReaderWebView.stopLoading();
            instantViewReaderWebView.removeJavascriptInterface("Instant");
            instantViewReaderWebView.destroy();
            instantViewReaderWebView = null;
        }
        if (instantViewReaderContainer != null) {
            removeView(instantViewReaderContainer);
            instantViewReaderContainer = null;
        }
    }

    private void cacheInstantImagesAsync(@NonNull File archiveFile, @Nullable Runnable callback) {
        INSTANT_IMAGE_EXECUTOR.execute(() -> {
            cacheInstantImages(archiveFile);
            if (callback != null) {
                AndroidUtilities.runOnUIThread(callback);
            }
        });
    }

    @WorkerThread
    private void cacheInstantImages(@NonNull File archiveFile) {
        try {
            MHTML archive = new MHTML(archiveFile);
            for (int i = 0; i < archive.entries.size(); i++) {
                MHTML.Entry entry = archive.entries.get(i);
                String location = entry.getLocation();
                String type = entry.getType();
                if (TextUtils.isEmpty(location) || TextUtils.isEmpty(type) || !type.toLowerCase().startsWith("image/")) {
                    continue;
                }
                try (InputStream stream = entry.getInputStream()) {
                    Bitmap bitmap = BrowserImageDecoder.decode(stream, type);
                    BrowserInstantViewFragment.cacheInstantImage(location, bitmap);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        } finally {
            archiveFile.delete();
        }
    }

    private final class BrowserWebViewProxy {
        @JavascriptInterface
        public void postEvent(String type, String data) {
            AndroidUtilities.runOnUIThread(() -> onWebEventReceived(type, data));
        }

        @JavascriptInterface
        public void postEvent(String type) {
            postEvent(type, "");
        }
    }

    private final class ReaderInstantViewBridge {
        @JavascriptInterface
        public void done(String result) {
            AndroidUtilities.runOnUIThread(() -> handleInstantViewResult(result));
        }
    }

    private static class BrowserWebView extends WebView {
        private BrowserWebViewContainer owner;
        private int prevScrollX;
        private int prevScrollY;

        BrowserWebView(Context context, BrowserWebViewContainer owner) {
            super(context);
            this.owner = owner;
        }

        void setOwner(BrowserWebViewContainer owner) {
            this.owner = owner;
        }

        public void evaluateJS(String script) {
            if (script == null || script.isEmpty()) {
                return;
            }
            evaluateJavascript(script, value -> {});
        }

        public float getScrollProgress() {
            final float scrollHeight = Math.max(1, computeVerticalScrollRange() - computeVerticalScrollExtent());
            if (scrollHeight <= getHeight()) {
                return 0f;
            }
            return clamp01((float) getScrollY() / scrollHeight);
        }

        public void setScrollProgress(float progress) {
            setScrollY((int) (clamp01(progress) * Math.max(1, computeVerticalScrollRange() - computeVerticalScrollExtent())));
        }

        @Override
        protected void onScrollChanged(int l, int t, int oldl, int oldt) {
            super.onScrollChanged(l, t, oldl, oldt);
            if (owner != null && owner.delegate != null) {
                owner.delegate.onWebViewScrolled(getScrollX() - prevScrollX, getScrollY() - prevScrollY);
            }
            prevScrollX = getScrollX();
            prevScrollY = getScrollY();
        }
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
