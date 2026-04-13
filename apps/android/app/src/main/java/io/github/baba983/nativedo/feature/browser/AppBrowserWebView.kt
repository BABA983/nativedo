package io.github.baba983.nativedo.feature.browser

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.ValueCallback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal fun AppBrowserWebView(
    initialUrl: String,
    userAgent: String,
    backgroundColor: Color,
    controller: AppBrowserWebViewController,
    modifier: Modifier = Modifier,
    onWebViewCreated: (AppBrowserWebViewView) -> Unit = {},
    onWebViewReleased: () -> Unit = {},
    onNavigationRequest: (String) -> Boolean,
    onPageStarted: (AppBrowserWebViewView?, String?, Bitmap?) -> Unit,
    onPageFinished: (AppBrowserWebViewView?, String?) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onTitleChanged: (String?) -> Unit,
    onIconChanged: (String, Bitmap?) -> Unit,
    onFindResultChanged: (Int, Int, Boolean) -> Unit,
    onScrollProgressChanged: (Float) -> Unit,
    onCanGoBackChanged: (Boolean) -> Unit,
    onCanGoForwardChanged: (Boolean) -> Unit,
    onShowFileChooser: (ValueCallback<Array<Uri>>?, WebChromeClient.FileChooserParams?) -> Unit =
        { callback, _ -> callback?.onReceiveValue(null) },
    onSslError: (SslErrorHandler?, SslError?) -> Unit = { handler, _ -> handler?.cancel() },
    onSyncCookiesToWebView: () -> Unit,
) {
    var attachedWebView by remember { mutableStateOf<AppBrowserWebViewView?>(null) }
    val latestAttachedWebView by rememberUpdatedState(attachedWebView)
    val latestOnWebViewReleased by rememberUpdatedState(onWebViewReleased)

    DisposableEffect(controller) {
        onDispose {
            latestAttachedWebView?.let(controller::detach)
            latestOnWebViewReleased()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            AppBrowserWebViewView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = userAgent
                // 单窗口模式下，window.open / target=_blank 会回落到当前会话导航。
                settings.setSupportMultipleWindows(false)
                settings.javaScriptCanOpenWindowsAutomatically = true
                setBackgroundColor(backgroundColor.toArgb())
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val requestUrl = request?.url?.toString() ?: return false
                        return onNavigationRequest(requestUrl)
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        onPageStarted(view as? AppBrowserWebViewView, url, favicon)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onPageFinished(view as? AppBrowserWebViewView, url)
                    }

                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?,
                    ) {
                        onSslError(handler, error)
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgressChanged(newProgress)
                    }

                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        super.onReceivedTitle(view, title)
                        onTitleChanged(title)
                    }

                    override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                        super.onReceivedIcon(view, icon)
                        onIconChanged(view?.url.orEmpty(), icon)
                    }

                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallback: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?,
                    ): Boolean {
                        onShowFileChooser(filePathCallback, fileChooserParams)
                        return true
                    }
                }

                setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
                    onFindResultChanged(activeMatchOrdinal, numberOfMatches, isDoneCounting)
                }

                setOnScrollChangeListener { _, _, _, _, _ ->
                    onScrollProgressChanged(calculateCurrentScrollProgress(this))
                }

                controller.attach(this)
                attachedWebView = this
                onWebViewCreated(this)
                onSyncCookiesToWebView()
                loadUrl(initialUrl)
            }
        },
        update = { view ->
            attachedWebView = view
            onCanGoBackChanged(view.canGoBack())
            onCanGoForwardChanged(view.canGoForward())
            onScrollProgressChanged(calculateCurrentScrollProgress(view))
        },
    )
}

internal class AppBrowserWebViewView(
    context: Context,
) : WebView(context) {
    val verticalScrollRange: Int
        get() = computeVerticalScrollRange()

    val verticalScrollExtent: Int
        get() = computeVerticalScrollExtent()
}

private fun calculateCurrentScrollProgress(view: AppBrowserWebViewView): Float {
    return buildAppBrowserScrollProgress(
        scrollY = view.scrollY,
        contentHeightPx = view.verticalScrollRange,
        viewportHeightPx = view.verticalScrollExtent,
    )
}
