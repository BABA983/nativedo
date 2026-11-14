package io.github.baba983.nativedo.auth

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * Creates a pre-configured WebView for the login flow.
 *
 * Responsibilities: WebSettings configuration, CookieManager setup.
 * Does NOT set WebViewClient/WebChromeClient — those are set by [LoginWebViewFragment].
 */
internal object LoginWebViewFactory {

    @SuppressLint("SetJavaScriptEnabled")
    fun create(context: Context, userAgent: String): WebView = WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        setBackgroundColor(Color.TRANSPARENT)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.userAgentString = userAgent
        settings.loadWithOverviewMode = false
        settings.useWideViewPort = false
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
        settings.mediaPlaybackRequiresUserGesture = true
        settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.safeBrowsingEnabled = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Leave form filling to Android's Autofill framework and the reader's password
            // manager: the app no longer captures, stores or injects login credentials.
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
        }
    }

    fun configureCookies(webView: WebView) {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
    }
}
