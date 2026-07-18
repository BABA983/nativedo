package org.telegram.ui.web;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Environment;
import android.os.Message;
import android.text.TextUtils;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ArticleViewer;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;

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
    private final Theme.ResourcesProvider resourcesProvider;
    private String mUrl;
    private boolean isPageLoaded;
    private boolean keyboardFocusable;
    private boolean wasFocusable;

    public BotWebViewContainer(Context context, Object resourcesProvider, int backgroundColor, boolean allow) {
        super(context);
        this.bot = allow;
        this.resourcesProvider = resourcesProvider instanceof Theme.ResourcesProvider
                ? (Theme.ResourcesProvider) resourcesProvider
                : null;
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
    public void setPageLoaded(String url, boolean animated) {
        if (webView != null) {
            webView.pageLoaded = true;
            onURLChanged(
                    webView.getUrl() != null ? webView.getUrl() : url,
                    !webView.canGoBack(),
                    !webView.canGoForward());
            updateKeyboardFocusable();
        }
        mUrl = url;
        if (isPageLoaded) {
            return;
        }
        isPageLoaded = true;
        if (webView != null) {
            webView.setAlpha(1f);
        }
        updateKeyboardFocusable();
        if (delegate != null) {
            // Pin calls delegate.onWebAppReady(); keep no-op-safe for browser sheets.
        }
    }

    public boolean isPageLoaded() {
        return isPageLoaded;
    }

    public String getUrlLoaded() {
        return mUrl;
    }

    public void onWebViewCreated(MyWebView webView) {}
    protected void onURLChanged(String url, boolean first, boolean last) {}
    protected void onTitleChanged(String title) {}
    protected void onFaviconChanged(Bitmap favicon) {}
    protected void onErrorShown(boolean shown, int errorCode, String description) {}

    public void showLinkCopiedBulletin() {
        try {
            BulletinFactory.of(this, resourcesProvider).createCopyLinkBulletin().show(true);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    /** Pin-shaped back: consume when WebView can go back. */
    public boolean onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return false;
    }

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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && request != null) {
                    return shouldOverrideUrl(request.getUrl() != null ? request.getUrl().toString() : null);
                }
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return shouldOverrideUrl(url);
            }

            private boolean shouldOverrideUrl(String url) {
                if (TextUtils.isEmpty(url)) {
                    return false;
                }
                String scheme = null;
                try {
                    Uri uri = Uri.parse(url);
                    scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "";
                } catch (Exception e) {
                    FileLog.e(e);
                }
                // Keep http(s) inside the in-app WebView.
                if ("http".equals(scheme) || "https".equals(scheme) || TextUtils.isEmpty(scheme)) {
                    return false;
                }
                // tel:/mailto:/intent:/custom schemes → external handlers.
                try {
                    if (Browser.openInExternalApp(getContext(), url, true)
                            || Browser.openInExternalBrowser(getContext(), url, true)) {
                        return true;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (webView != null) {
                    webView.pageLoaded = false;
                    webView.injectedJS = false;
                    webView.currentUrl = url;
                    webView.lastUrl = url;
                }
                isPageLoaded = false;
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
                if (webView != null) {
                    webView.pageLoaded = true;
                    webView.currentUrl = url;
                    webView.lastUrl = url;
                    webView.saveHistory(bot);
                }
                notifyUrlChanged();
                injectWebViewScripts();
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
                if (webView != null) {
                    webView.lastTitleGot = title != null;
                    webView.setTitle(title);
                    webView.saveHistory(bot);
                }
                onTitleChanged(title);
            }

            @Override
            public void onReceivedIcon(WebView view, Bitmap icon) {
                if (webView != null) {
                    webView.lastFaviconGot = icon != null;
                    webView.setFavicon(icon);
                    webView.saveHistory(bot);
                }
                onFaviconChanged(icon);
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                if (resultMsg == null || !(resultMsg.obj instanceof WebView.WebViewTransport)) {
                    return false;
                }
                final String fromUrl = webView != null ? webView.getUrl() : null;
                if (MessagesController.getInstance(UserConfig.selectedAccount).isWebBrowserInAppEnabled()) {
                    BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                    if (lastFragment == null) {
                        return false;
                    }
                    try {
                        ArticleViewer articleViewer = lastFragment.createArticleViewer(true);
                        articleViewer.setOpener(webView);
                        articleViewer.open((String) null);
                        MyWebView newWebView = articleViewer.getLastWebView();
                        if (newWebView != null) {
                            if (!TextUtils.isEmpty(fromUrl) && TextUtils.isEmpty(newWebView.getOpenURL())) {
                                newWebView.setOpenURL(fromUrl);
                            }
                            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                            transport.setWebView(newWebView);
                            resultMsg.sendToTarget();
                            return true;
                        }
                        articleViewer.close(true, true);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    return false;
                }
                // In-app browser off: open popup targets via Browser policy (Custom Tabs / external).
                WebView transportView = new WebView(view.getContext());
                transportView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, String url) {
                        Browser.openUrl(getContext(), url);
                        try {
                            transportView.destroy();
                        } catch (Exception ignore) {
                        }
                        return true;
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(transportView);
                resultMsg.sendToTarget();
                return true;
            }

            @Override
            public void onCloseWindow(WebView window) {
                if (delegate != null) {
                    delegate.onCloseRequested(null);
                } else if (closeRequestedListener != null) {
                    closeRequestedListener.run();
                }
                super.onCloseWindow(window);
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
            } else {
                startDownload(url, userAgent, contentDisposition, mimetype, contentLength);
            }
        });
    }

    /**
     * Default download path when no ArticleViewer delegate is attached
     * (e.g. long-press image download). Uses system DownloadManager.
     */
    public void startDownload(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
        if (TextUtils.isEmpty(url) || url.startsWith("blob:")) {
            return;
        }
        try {
            final String filename = URLUtil.guessFileName(url, contentDisposition, mimetype);
            final Runnable download = () -> {
                try {
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    if (!TextUtils.isEmpty(mimetype)) {
                        request.setMimeType(mimetype);
                    }
                    if (!TextUtils.isEmpty(userAgent)) {
                        request.addRequestHeader("User-Agent", userAgent);
                    }
                    request.setDescription(LocaleController.getString(R.string.WebDownloading));
                    request.setTitle(filename);
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
                    DownloadManager downloadManager =
                            (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
                    if (downloadManager != null) {
                        downloadManager.enqueue(request);
                    }
                    try {
                        BulletinFactory.of(this, resourcesProvider)
                                .createSimpleBulletin(
                                        R.raw.chats_infotip,
                                        AndroidUtilities.replaceTags(
                                                LocaleController.formatString(
                                                        R.string.WebDownloadingFile, filename)))
                                .show(true);
                    } catch (Throwable t) {
                        FileLog.e(t);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            };
            if (contentLength > 2L * 1024L * 1024L) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), resourcesProvider);
                builder.setTitle(LocaleController.getString(R.string.WebDownloadAlertTitle));
                builder.setMessage(AndroidUtilities.replaceTags(
                        LocaleController.formatString(
                                R.string.WebDownloadAlertInfoWithSize,
                                filename,
                                AndroidUtilities.formatFileSize(contentLength))));
                builder.setPositiveButton(LocaleController.getString(R.string.WebDownloadAlertYes),
                        (di, w) -> download.run());
                builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                builder.show();
            } else {
                download.run();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
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
        isPageLoaded = false;
        mUrl = null;
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
        isPageLoaded = false;
        mUrl = url;
        view.loadUrl(url);
    }
    public void invalidateViewPortHeight() {}
    public void invalidateViewPortHeight(boolean animated) {}
    public void invalidateViewPortHeight(boolean isStable, boolean force) {}

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

        /** Host helper for popup windows that inherit opener URL fallback. */
        public void setOpenURL(String url) {
            openedByUrl = url;
        }
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
                if (getContext() == null || TextUtils.isEmpty(url)) {
                    return;
                }
                org.telegram.ui.ActionBar.AlertDialog.Builder builder =
                        new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext());
                builder.setTitle(url);
                builder.setItems(new CharSequence[]{
                        LocaleController.getString(R.string.Open),
                        LocaleController.getString(R.string.OpenInExternalApp),
                        LocaleController.getString(R.string.Copy)
                }, (dialog, which) -> {
                    if (which == 0) {
                        loadUrl(url);
                    } else if (which == 1) {
                        Browser.openInExternalBrowser(getContext(), url, true);
                    } else if (which == 2) {
                        AndroidUtilities.addToClipboard(url);
                        if (botWebViewContainer != null) {
                            botWebViewContainer.showLinkCopiedBulletin();
                        }
                    }
                });
                builder.show();
            });
        }

        private void showImageContextMenu(String imageUrl) {
            AndroidUtilities.runOnUIThread(() -> {
                if (getContext() == null || TextUtils.isEmpty(imageUrl)) {
                    return;
                }
                org.telegram.ui.ActionBar.AlertDialog.Builder builder =
                        new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext());
                builder.setTitle(imageUrl);
                builder.setItems(new CharSequence[]{
                        LocaleController.getString(R.string.OpenInExternalApp),
                        LocaleController.getString(R.string.AccActionDownload),
                        LocaleController.getString(R.string.CopyLink)
                }, (dialog, which) -> {
                    if (which == 0) {
                        Browser.openInExternalBrowser(getContext(), imageUrl, true);
                    } else if (which == 1) {
                        if (botWebViewContainer != null) {
                            botWebViewContainer.startDownload(
                                    imageUrl, null, null, "image/*", 0);
                        }
                    } else if (which == 2) {
                        AndroidUtilities.addToClipboard(imageUrl);
                        if (botWebViewContainer != null) {
                            botWebViewContainer.showLinkCopiedBulletin();
                        }
                    }
                });
                builder.show();
            });
        }
    }
}
