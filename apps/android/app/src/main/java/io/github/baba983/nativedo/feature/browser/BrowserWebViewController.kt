package io.github.baba983.nativedo.feature.browser

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

internal interface BrowserWebViewController {
    fun attach(webView: WebView)
    fun detach(webView: WebView)
    fun loadUrl(url: String)
    fun reload()
    fun goBack()
    fun goForward()
    fun findAllAsync(query: String)
    fun findNext(forward: Boolean)
    fun clearMatches()
}

@Composable
internal fun rememberBrowserWebViewController(): BrowserWebViewController {
    return remember { DefaultBrowserWebViewController() }
}

private class DefaultBrowserWebViewController : BrowserWebViewController {
    private var webView: WebView? = null

    override fun attach(webView: WebView) {
        this.webView = webView
    }

    override fun detach(webView: WebView) {
        if (this.webView === webView) {
            this.webView = null
        }
    }

    override fun loadUrl(url: String) {
        webView?.loadUrl(url)
    }

    override fun reload() {
        webView?.reload()
    }

    override fun goBack() {
        webView?.goBack()
    }

    override fun goForward() {
        webView?.goForward()
    }

    override fun findAllAsync(query: String) {
        webView?.findAllAsync(query)
    }

    override fun findNext(forward: Boolean) {
        webView?.findNext(forward)
    }

    override fun clearMatches() {
        webView?.clearMatches()
    }
}
