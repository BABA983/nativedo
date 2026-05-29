package org.telegram.ui.web;

import android.content.Context;
import android.graphics.Bitmap;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import java.util.Map;

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
    }

    public interface WebViewScrollListener {
        void onWebViewScrolled(WebView webView, int dx, int dy);
    }

    public static class WebViewProxy {
    }

    private MyWebView webView;

    public BotWebViewContainer(Context context, Object resourcesProvider, int backgroundColor, boolean allow) {
        super(context);
    }

    public void setOpener(MyWebView webView) {}
    public void setOnCloseRequestedListener(Runnable listener) {}
    public void setWebViewProgressListener(ProgressListener listener) {}
    public void setDelegate(Delegate delegate) {}
    public void setWebViewScrollListener(WebViewScrollListener listener) {}
    public void setPageLoaded(String url, boolean animated) {}
    public void onWebViewCreated(MyWebView webView) {}
    protected void onURLChanged(String url, boolean first, boolean last) {}
    protected void onTitleChanged(String title) {}
    protected void onFaviconChanged(Bitmap favicon) {}
    protected void onErrorShown(boolean shown, int errorCode, String description) {}
    public MyWebView getWebView() {
        if (webView == null) {
            webView = new MyWebView(getContext());
        }
        return webView;
    }
    public void checkCreateWebView() { getWebView(); }
    public void resetWebView() {}
    public void destroyWebView() {}
    public void preserveWebView() {}
    public void replaceWebView(int account, MyWebView webView, Object proxy) { this.webView = webView; }
    public WebViewProxy getProxy() { return null; }
    public void loadUrl(int account, String url) { getWebView().loadUrl(url); }
    public void invalidateViewPortHeight() {}
    public void invalidateViewPortHeight(boolean animated) {}
    public void setKeyboardFocusable(boolean focusable) {}
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
        public boolean injectedJS;

        public MyWebView(Context context) {
            super(context);
        }

        public boolean isPageLoaded() {
            return true;
        }

        public boolean isUrlDangerous() {
            return false;
        }

        public String getOpenURL() { return getUrl(); }
        public int getSearchIndex() { return 0; }
        public int getSearchCount() { return 0; }
        public void search(String text, Runnable listener) { if (listener != null) listener.run(); }
        public float getScrollProgress() { return 0f; }
        public void setScrollProgress(float progress) {}
        public Bitmap getFavicon(String url) { return getFavicon(); }
        public void loadUrl(String url, WebMetadataCache.WebMetadata meta) { loadUrl(url); }
    }
}
