// UPSTREAM-LEVEL: L1
// UPSTREAM-PATH:  org/telegram/ui/web/BotWebViewContainer.java; org/telegram/ui/WebviewActivity.java; res/raw/webview_ext.js
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// SUBSYSTEMS: webview-settings=L1, webview-lifecycle=L1, theme/resource-mapping=L2, color-bridge=L1, gesture-allow-scroll=L0, scroll-progress=L1
// HOST-WIRING:
//   - Telegram ApplicationLoader/FileLog/LocaleController/Browser helpers replaced with Android framework APIs.
//   - NativeDo strings and Theme keys are used for progress/download user feedback.
//   - Uses Telegram ArticleViewer browser background keys for the WebView host surface.
// ADAPTED:
//   - Keeps the non-bot WebView settings/client subset needed by NativeDo's first browser slice.
//   - Delegates loading progress drawing to WebActionBar so the browser has Telegram's single
//     action-bar progress line instead of a second WebView-container line.
//   - Keeps Telegram's setKeyboardFocusable(lastVisible) surface so sheet-to-tab collapse can
//     drop WebView focus before the overlay starts drawing the proxy sheet.
//   - Ports the upstream tg:theme-accent / theme-color / body backgroundColor JS bridge from
//     res/raw/webview_ext.js. NativeDo registers TelegramWebviewProxy for the same postEvent
//     color and allowScroll event shapes.
//     Color events are decoded the same way as upstream's onWebEventReceived (BotWebViewContainer
//     L1216-1240) and forwarded as Delegate.onWebBackgroundChanged.
//   - Mirrors upstream MyWebView.getScrollProgress / setScrollProgress (BotWebViewContainer
//     L4934-4944) from a small WebView subclass so computeVerticalScrollRange/Extent stay exact.
//   - Exposes MyWebView.onScrollChanged as Delegate.onWebViewScrolled so BrowserSheet can mirror
//     ArticleViewer.updatePages() and redraw action-bar reading progress.
// OMITTED:
//   - Bot bridge, Telegram WebViewProxy, TON proxying, payments, age verification, history serialization,
//     popup sheets, permissions UI, JS dialogs, and Telegram-specific dangerous URL handling.
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
import android.view.PixelCopy;
import android.view.Surface;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import io.github.baba983.nativedo.R;
import io.github.baba983.nativedo.core.AndroidUtilities;
import io.github.baba983.nativedo.ui.actionbar.Theme;
import io.github.baba983.nativedo.ui.components.LayoutHelper;

public class BrowserWebViewContainer extends FrameLayout {

    public interface PreviewCallback {
        void onPreviewReady(@Nullable Bitmap bitmap);
    }

    public interface Delegate {
        void onTitleChanged(String title);

        void onUrlChanged(String url, boolean canGoBack, boolean canGoForward);

        void onProgressChanged(int progress, boolean visible);

        default void onWebBackgroundChanged(boolean actionBarColor, int color) {}

        default void onWebViewScrolled(int dx, int dy) {}
    }

    private final Delegate delegate;
    private final BrowserWebViewProxy webViewProxy = new BrowserWebViewProxy();
    private BrowserWebView webView;
    private String currentUrl;
    private String browserExtensionScript;
    private Bitmap currentFavicon;
    private int currentProgress;
    private boolean keyboardFocusable;
    private boolean wasFocusable;

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
        webView = new BrowserWebView(getContext());
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
                notifyUrlChanged();
                notifyProgress(10);
                super.onPageStarted(view, url, favicon);
                setInjectedJS(false);
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
            }

            @Override
            public void onReceivedIcon(WebView view, Bitmap icon) {
                currentFavicon = icon;
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

        addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == webView && (AndroidUtilities.makingGlobalBlurBitmap || getLayerType() == LAYER_TYPE_HARDWARE && !canvas.isHardwareAccelerated())) {
            return true;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    public void loadUrl(String url) {
        currentUrl = url;
        webView.loadUrl(url);
        notifyUrlChanged();
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
    public String getCurrentUrl() {
        return currentUrl;
    }

    @Nullable
    public WebView getWebView() {
        return webView;
    }

    @Nullable
    public Bitmap getFavicon() {
        return currentFavicon;
    }

    public int getProgress() {
        return currentProgress;
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

    private void setInjectedJS(boolean injected) {
        if (getParent() instanceof WebViewSwipeContainer) {
            ((WebViewSwipeContainer) getParent()).injectedJS = injected;
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
                if (delegate != null) {
                    delegate.onWebBackgroundChanged(isActionBarColor, color);
                }
            } catch (Exception ignored) {
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
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme) || "about".equalsIgnoreCase(scheme)) {
            return false;
        }
        return openExternal(uri);
    }

    private boolean openExternal(Uri uri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.putExtra(android.provider.Browser.EXTRA_CREATE_NEW_TAB, true);
            intent.putExtra(android.provider.Browser.EXTRA_APPLICATION_ID, getContext().getPackageName());
            getContext().startActivity(intent);
            return true;
        } catch (Exception ignored) {
            return false;
        }
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

    private class BrowserWebView extends WebView {
        private int prevScrollX;
        private int prevScrollY;

        BrowserWebView(Context context) {
            super(context);
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
            if (delegate != null) {
                delegate.onWebViewScrolled(getScrollX() - prevScrollX, getScrollY() - prevScrollY);
            }
            prevScrollX = getScrollX();
            prevScrollY = getScrollY();
        }
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
