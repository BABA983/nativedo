package org.telegram.ui.web;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.LayoutHelper;

public class BotWebViewContainer extends FrameLayout {
    public static boolean firstWebView = true;

    public interface Delegate {
        default void onCloseRequested(Runnable callback) {}
        default void onCloseToTabs() {}
        default void onInstantClose() {}
        default void onWebAppSetupClosingBehavior(boolean needConfirmation) {}
        default void onWebAppSwipingBehavior(boolean allowSwipes) {}
        default void onWebAppSetActionBarColor(int colorKey, int color, boolean isOverrideColor) {}
        default void onWebAppSetBackgroundColor(int color) {}
        default void onWebAppBackgroundChanged(boolean actionBarColor, int color) {}
        default void onWebAppExpand() {}
        default void onWebAppSwitchInlineQuery(org.telegram.tgnet.TLRPC.User botUser, String query, java.util.List<String> chatTypes) {}
        default void onWebAppOpenInvoice(org.telegram.tgnet.TLRPC.InputInvoice inputInvoice, String slug, org.telegram.tgnet.TLObject response) {}
        default void onSetupMainButton(boolean isVisible, boolean isActive, String text, long emojiId, int color, int textColor, boolean isProgressVisible, boolean hasShineEffect) {}
        default void onSetupSecondaryButton(boolean isVisible, boolean isActive, String text, long emojiId, int color, int textColor, boolean isProgressVisible, boolean hasShineEffect, String position) {}
        default void onSetBackButtonVisible(boolean visible) {}
        default void onSetSettingsButtonVisible(boolean visible) {}
        default boolean onShowFileChooser(WebView webView, android.webkit.ValueCallback<android.net.Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) { return false; }
        default void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {}
    }

    public interface WebViewScrollListener {
        void onWebViewScrolled(WebView webView, int dx, int dy);
    }

    public static class WebViewProxy {
        public BotWebViewContainer container;
        public final MyWebView webView;

        public WebViewProxy(MyWebView webView, BotWebViewContainer container) {
            this.webView = webView;
            this.container = container;
        }

        public void setContainer(BotWebViewContainer container) {
            this.container = container;
        }

        @JavascriptInterface
        public void postEvent(String type, String data) {
        }
    }

    private MyWebView webView;
    private WebViewProxy webViewProxy;
    private ProgressListener progressListener;
    private WebViewScrollListener scrollListener;
    private Runnable closeRequestedListener;
    private Delegate delegate;
    private boolean preserving;
    private final boolean bot;

    public BotWebViewContainer(Context context, Object resourcesProvider, int backgroundColor, boolean allow) {
        super(context);
        this.bot = allow;
        setBackgroundColor(backgroundColor);
        setFocusable(false);
    }

    public void setOpener(MyWebView webView) {
        if (this.webView != null) {
            this.webView.opener = webView;
        }
    }
    public void setOnCloseRequestedListener(Runnable listener) { closeRequestedListener = listener; }
    public void setWebViewProgressListener(ProgressListener listener) { progressListener = listener; }
    public void setDelegate(Delegate delegate) { this.delegate = delegate; }
    public void setWebViewScrollListener(WebViewScrollListener listener) {
        scrollListener = listener;
        if (webView != null) {
            webView.setContainers(this, listener);
        }
    }
    public void setPageLoaded(String url, boolean animated) {}
    public void onWebViewCreated(MyWebView webView) {}
    protected void onURLChanged(String url, boolean first, boolean last) {}
    protected void onTitleChanged(String title) {}
    protected void onFaviconChanged(Bitmap favicon) {}
    protected void onErrorShown(boolean shown, int errorCode, String description) {}

    @SuppressLint("SetJavaScriptEnabled")
    public MyWebView getWebView() {
        if (webView == null) {
            webView = new MyWebView(getContext());
            webView.setContainers(this, scrollListener);
            webViewProxy = new WebViewProxy(webView, this);
            configureWebView(webView);
            addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            onWebViewCreated(webView);
            firstWebView = false;
        }
        return webView;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView(MyWebView view) {
        view.setOverScrollMode(View.OVER_SCROLL_NEVER);
        view.setVerticalScrollBarEnabled(false);
        view.setHorizontalScrollBarEnabled(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(view, true);
        }

        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setSupportMultipleWindows(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setSaveFormData(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            settings.setForceDark(WebSettings.FORCE_DARK_AUTO);
        }
        settings.setMediaPlaybackRequiresUserGesture(false);

        view.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                webView.pageLoaded = false;
                webView.injectedJS = false;
                webView.currentUrl = url;
                webView.lastUrl = url;
                onErrorShown(false, 0, null);
                notifyUrlChanged();
                if (progressListener != null) {
                    progressListener.onProgress(0f);
                }
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                injectWebViewScripts();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                webView.pageLoaded = true;
                webView.currentUrl = url;
                webView.lastUrl = url;
                notifyUrlChanged();
                injectWebViewScripts();
                webView.saveHistory(bot);
                setPageLoaded(url, true);
                if (progressListener != null) {
                    progressListener.onProgress(1f);
                }
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                if (webView != null) {
                    webView.doUpdateVisitedHistory(url, bot);
                    notifyUrlChanged();
                }
                super.doUpdateVisitedHistory(view, url, isReload);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && request != null && request.isForMainFrame()) {
                    onErrorShown(true, error.getErrorCode(), String.valueOf(error.getDescription()));
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                onErrorShown(true, errorCode, description);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                if (handler != null) {
                    handler.cancel();
                }
                onErrorShown(true, 0, error == null ? null : error.toString());
            }
        });

        view.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (progressListener != null) {
                    progressListener.onProgress(newProgress / 100f);
                }
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                webView.lastTitleGot = title != null;
                webView.setTitle(title);
                webView.saveHistory(bot);
                onTitleChanged(title);
            }

            @Override
            public void onReceivedIcon(WebView view, Bitmap icon) {
                webView.lastFaviconGot = icon != null;
                webView.setFavicon(icon);
                webView.saveHistory(bot);
                onFaviconChanged(icon);
            }

            @Override
            public boolean onShowFileChooser(WebView webView, android.webkit.ValueCallback<android.net.Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (delegate != null) {
                    return delegate.onShowFileChooser(webView, filePathCallback, fileChooserParams);
                }
                return false;
            }
        });

        view.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            if (delegate != null) {
                delegate.onDownloadStart(url, userAgent, contentDisposition, mimetype, contentLength);
            }
        });
    }

    private void injectWebViewScripts() {
        if (webView == null || webView.injectedJS) {
            return;
        }
        if (bot) {
            webView.evaluateJS(AndroidUtilities.readRes(R.raw.webview_app_ext).replace("$DEBUG$", "" + BuildVars.DEBUG_VERSION));
        } else {
            webView.evaluateJS(AndroidUtilities.readRes(R.raw.webview_ext).replace("$DEBUG$", "" + BuildVars.DEBUG_VERSION));
            webView.evaluateJS(AndroidUtilities.readRes(R.raw.webview_share));
        }
        webView.injectedJS = true;
    }

    private void notifyUrlChanged() {
        if (webView == null) {
            return;
        }
        WebBackForwardList list = webView.copyBackForwardList();
        boolean first = list == null || list.getCurrentIndex() <= 0;
        boolean last = list == null || list.getCurrentIndex() >= list.getSize() - 1;
        onURLChanged(webView.getUrl(), first, last);
    }

    public void checkCreateWebView() { getWebView(); }
    public void resetWebView() {
        preserving = false;
        if (webView != null) {
            AndroidUtilities.removeFromParent(webView);
            addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            onWebViewCreated(webView);
            notifyUrlChanged();
        }
    }
    public void destroyWebView() {
        if (webView == null) {
            return;
        }
        AndroidUtilities.removeFromParent(webView);
        if (preserving) {
            return;
        }
        if (webViewProxy != null) {
            webViewProxy.setContainer(null);
        }
        webView.destroyed = true;
        webView.clearSearchState();
        webView.clearSearchListener();
        webView.stopLoading();
        webView.setWebChromeClient(null);
        webView.setWebViewClient(null);
        webView.destroy();
        webView = null;
        webViewProxy = null;
    }
    public void preserveWebView() {
        preserving = true;
        if (webViewProxy != null) {
            webViewProxy.setContainer(null);
        }
        if (webView != null) {
            webView.setContainers(null, null);
            AndroidUtilities.removeFromParent(webView);
        }
    }
    public void replaceWebView(int account, MyWebView webView, Object proxy) {
        preserving = false;
        if (this.webView == webView) {
            this.webViewProxy = webView == null ? null : proxy instanceof WebViewProxy ? (WebViewProxy) proxy : this.webViewProxy != null ? this.webViewProxy : new WebViewProxy(webView, this);
            if (this.webViewProxy != null) {
                this.webViewProxy.setContainer(this);
            }
            if (webView != null) {
                webView.setContainers(this, scrollListener);
                AndroidUtilities.removeFromParent(webView);
                addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            }
            onWebViewCreated(webView);
            notifyUrlChanged();
            return;
        }
        if (this.webView != null) {
            AndroidUtilities.removeFromParent(this.webView);
        }
        this.webView = webView;
        this.webViewProxy = webView == null ? null : proxy instanceof WebViewProxy ? (WebViewProxy) proxy : new WebViewProxy(webView, this);
        if (this.webViewProxy != null) {
            this.webViewProxy.setContainer(this);
        }
        if (webView != null) {
            webView.setContainers(this, scrollListener);
            AndroidUtilities.removeFromParent(webView);
            addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            onWebViewCreated(webView);
        }
        notifyUrlChanged();
    }
    public WebViewProxy getProxy() { return webViewProxy; }
    public void loadUrl(int account, String url) {
        MyWebView view = getWebView();
        if (view.destroyed) {
            webView = null;
            view = getWebView();
        }
        view.loadUrl(url);
    }
    public void invalidateViewPortHeight() {}
    public void invalidateViewPortHeight(boolean animated) {}
    private boolean keyboardFocusable;
    private boolean wasFocusable;
    private boolean isPageLoaded;
    private void updateKeyboardFocusable() {
        final boolean focusable = keyboardFocusable && isPageLoaded && false;
        if (wasFocusable != focusable) {
            if (!focusable) {
                setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
                BotWebViewContainer.this.setFocusable(false);
                if (webView != null) {
                    webView.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
                    webView.clearFocus();
                }
                AndroidUtilities.hideKeyboard(this);
            } else {
                setDescendantFocusability(FOCUS_BEFORE_DESCENDANTS);
                BotWebViewContainer.this.setFocusable(true);
                if (webView != null) {
                    webView.setDescendantFocusability(FOCUS_BEFORE_DESCENDANTS);
                }
            }
        }
        wasFocusable = focusable;
    }

    public void setKeyboardFocusable(boolean focusable) {
        keyboardFocusable = focusable;
        updateKeyboardFocusable();
    }
    public static String magic2tonsite(String url) { return url; }
    public static boolean isTonsite(String url) { return false; }

    public interface ProgressListener {
        void onProgress(float progress);
    }

    public static class MyWebView extends WebView {
        public boolean lastTitleGot;
        public String lastTitle;
        public String lastSiteName;
        public boolean lastActionBarColorGot;
        public int lastActionBarColor;
        public boolean lastBackgroundColorGot;
        public int lastBackgroundColor;
        public boolean lastFaviconGot;
        public Bitmap lastFavicon;
        private String openedByUrl;
        private String currentUrl;
        private String lastUrl;
        private BrowserHistory.Entry currentHistoryEntry;
        private final HashMap<String, Bitmap> lastFavicons = new HashMap<>();
        private int searchIndex;
        private int searchCount;
        private boolean searchLoading;
        private Runnable searchListener;
        public boolean injectedJS;
        public MyWebView opener;
        private BotWebViewContainer botWebViewContainer;
        private WebViewScrollListener webViewScrollListener;
        private boolean pageLoaded;
        private boolean destroyed;

        public MyWebView(Context context) {
            super(context);
            setFindListener((activeMatchOrdinal, numberOfMatches, isDoneCounting) -> {
                if (destroyed) {
                    return;
                }
                searchIndex = activeMatchOrdinal;
                searchCount = numberOfMatches;
                searchLoading = !isDoneCounting;
                Runnable listener = searchListener;
                if (listener != null) {
                    listener.run();
                }
            });

            setOnLongClickListener(v -> {
                HitTestResult result = getHitTestResult();
                if (result == null) return false;

                if (result.getType() == HitTestResult.SRC_ANCHOR_TYPE) {
                    final String url = result.getExtra();
                    if (url != null) {
                        showLinkContextMenu(url);
                        return true;
                    }
                } else if (result.getType() == HitTestResult.IMAGE_TYPE) {
                    final String imageUrl = result.getExtra();
                    if (imageUrl != null) {
                        showImageContextMenu(imageUrl);
                        return true;
                    }
                }
                return false;
            });
        }

        public void setContainers(BotWebViewContainer botWebViewContainer, WebViewScrollListener webViewScrollListener) {
            this.botWebViewContainer = botWebViewContainer;
            this.webViewScrollListener = webViewScrollListener;
        }

        public void evaluateJS(String script) {
            if (script == null || script.length() == 0) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                evaluateJavascript(script, value -> {});
            } else {
                loadUrl("javascript:" + script);
            }
        }

        public boolean isPageLoaded() {
            return pageLoaded;
        }

        public boolean isUrlDangerous() {
            return false;
        }

        public String getOpenURL() { return openedByUrl; }
        public int getSearchIndex() { return destroyed ? 0 : searchIndex; }
        public int getSearchCount() { return destroyed ? 0 : searchCount; }
        public void search(String text, Runnable listener) {
            searchLoading = true;
            this.searchListener = listener;
            if (destroyed) {
                searchIndex = 0;
                searchCount = 0;
                searchLoading = false;
                if (listener != null) {
                    listener.run();
                }
                return;
            }
            try {
                findAllAsync(text == null ? "" : text);
            } catch (Throwable t) {
                FileLog.e(t);
                searchIndex = 0;
                searchCount = 0;
                searchLoading = false;
                if (listener != null) {
                    listener.run();
                }
            }
        }

        @Override
        public void findNext(boolean forward) {
            if (destroyed) {
                return;
            }
            try {
                super.findNext(forward);
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }

        private void clearSearchState() {
            searchIndex = 0;
            searchCount = 0;
            searchLoading = false;
        }

        private void clearSearchListener() {
            searchListener = null;
        }
        public float getScrollProgress() {
            int range = computeVerticalScrollRange() - computeVerticalScrollExtent();
            return range <= 0 ? 0f : computeVerticalScrollOffset() / (float) range;
        }
        public void setScrollProgress(float progress) {
            int range = computeVerticalScrollRange() - computeVerticalScrollExtent();
            scrollTo(getScrollX(), range <= 0 ? 0 : (int) (range * progress));
        }
        public Bitmap getFavicon(String url) { return lastFavicons.get(url); }

        public Bitmap getFavicon() {
            return lastFavicon;
        }

        public String getTitle() {
            return lastTitle;
        }

        public void setTitle(String title) {
            lastTitle = title;
        }

        public void setFavicon(Bitmap favicon) {
            lastFavicon = favicon;
            String url = getUrl();
            if (url != null && favicon != null) {
                lastFavicons.put(url, favicon);
            }
        }

        public void loadUrl(String url, WebMetadataCache.WebMetadata meta) {
            applyCachedMeta(meta);
            openedByUrl = url;
            currentUrl = url;
            lastUrl = url;
            super.loadUrl(url);
            notifyContainerUrlChanged();
        }

        public void checkCachedMetaProperties(String url) {
            String domain = AndroidUtilities.getHostAuthority(url, true);
            WebMetadataCache.WebMetadata meta = WebMetadataCache.getInstance().get(domain);
            applyCachedMeta(meta);
        }

        public boolean applyCachedMeta(WebMetadataCache.WebMetadata meta) {
            if (meta == null) {
                return false;
            }
            if (meta.actionBarColor != 0) {
                lastActionBarColor = meta.actionBarColor;
                lastActionBarColorGot = true;
                if (botWebViewContainer != null && botWebViewContainer.delegate != null) {
                    botWebViewContainer.delegate.onWebAppBackgroundChanged(true, meta.actionBarColor);
                }
            }
            if (meta.backgroundColor != 0) {
                lastBackgroundColor = meta.backgroundColor;
                lastBackgroundColorGot = true;
                if (botWebViewContainer != null && botWebViewContainer.delegate != null) {
                    botWebViewContainer.delegate.onWebAppBackgroundChanged(false, meta.backgroundColor);
                }
                if (SharedConfig.adaptableColorInBrowser) {
                    setBackgroundColor(meta.backgroundColor);
                }
            }
            if (meta.favicon != null) {
                setFavicon(meta.favicon);
                lastFaviconGot = true;
                if (botWebViewContainer != null) {
                    botWebViewContainer.onFaviconChanged(lastFavicon);
                }
            }
            if (!TextUtils.isEmpty(meta.sitename)) {
                lastSiteName = meta.sitename;
                setTitle(meta.sitename);
                lastTitleGot = true;
                if (botWebViewContainer != null) {
                    botWebViewContainer.onTitleChanged(lastTitle);
                }
            } else if (!TextUtils.isEmpty(meta.title)) {
                setTitle(meta.title);
                lastTitleGot = true;
                if (botWebViewContainer != null) {
                    botWebViewContainer.onTitleChanged(lastTitle);
                }
            }
            return true;
        }

        @Override
        public void loadUrl(@NonNull String url) {
            checkCachedMetaProperties(url);
            openedByUrl = url;
            currentUrl = url;
            lastUrl = url;
            super.loadUrl(url);
            notifyContainerUrlChanged();
        }

        @Override
        public void loadUrl(@NonNull String url, @NonNull Map<String, String> additionalHttpHeaders) {
            checkCachedMetaProperties(url);
            openedByUrl = url;
            currentUrl = url;
            lastUrl = url;
            super.loadUrl(url, additionalHttpHeaders);
            notifyContainerUrlChanged();
        }

        @Override
        public String getUrl() {
            String url = super.getUrl();
            if (url != null) {
                lastUrl = url;
            }
            return url != null ? url : lastUrl;
        }

        private void doUpdateVisitedHistory(String url, boolean bot) {
            currentUrl = url;
            lastUrl = url;
            if (bot || TextUtils.isEmpty(url)) {
                return;
            }
            if (currentHistoryEntry == null || !TextUtils.equals(currentHistoryEntry.url, url)) {
                currentHistoryEntry = new BrowserHistory.Entry();
                currentHistoryEntry.id = Utilities.fastRandom.nextLong();
                currentHistoryEntry.time = System.currentTimeMillis();
                currentHistoryEntry.url = magic2tonsite(getUrl());
                currentHistoryEntry.meta = WebMetadataCache.WebMetadata.from(this);
                BrowserHistory.pushHistory(currentHistoryEntry);
            }
        }

        private void saveHistory(boolean bot) {
            if (bot) {
                return;
            }
            WebMetadataCache.WebMetadata meta = WebMetadataCache.WebMetadata.from(this);
            WebMetadataCache.getInstance().save(meta);
            if (currentHistoryEntry != null && meta != null) {
                currentHistoryEntry.meta = meta;
                BrowserHistory.pushHistory(currentHistoryEntry);
            }
        }

        private void notifyContainerUrlChanged() {
            if (botWebViewContainer != null) {
                botWebViewContainer.onURLChanged(getUrl(), !canGoBack(), !canGoForward());
            }
        }

        @Override
        protected void onScrollChanged(int l, int t, int oldl, int oldt) {
            super.onScrollChanged(l, t, oldl, oldt);
            if (webViewScrollListener != null) {
                webViewScrollListener.onWebViewScrolled(this, l - oldl, t - oldt);
            }
        }

        private void showLinkContextMenu(String url) {
            AndroidUtilities.runOnUIThread(() -> {
                if (getContext() == null) return;

                org.telegram.ui.ActionBar.AlertDialog.Builder builder = new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext());
                builder.setTitle(url);
                builder.setItems(new CharSequence[]{
                        org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.Open),
                        org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.Copy)
                }, (dialog, which) -> {
                    if (which == 0) {
                        loadUrl(url);
                    } else if (which == 1) {
                        AndroidUtilities.addToClipboard(url);
                    }
                });
                builder.show();
            });
        }

        private void showImageContextMenu(String imageUrl) {
            AndroidUtilities.runOnUIThread(() -> {
                if (getContext() == null) return;

                org.telegram.ui.ActionBar.AlertDialog.Builder builder = new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext());
                builder.setTitle(imageUrl);
                builder.setItems(new CharSequence[]{
                        org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.AccActionDownload),
                        org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.CopyLink)
                }, (dialog, which) -> {
                    if (which == 0) {
                        if (botWebViewContainer != null && botWebViewContainer.delegate != null) {
                            botWebViewContainer.delegate.onDownloadStart(imageUrl, null, null, "image/*", 0);
                        }
                    } else if (which == 1) {
                        AndroidUtilities.addToClipboard(imageUrl);
                    }
                });
                builder.show();
            });
        }
    }
}
