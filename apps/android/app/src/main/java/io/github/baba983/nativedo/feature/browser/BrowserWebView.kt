package io.github.baba983.nativedo.feature.browser

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebView.WebViewTransport
import android.webkit.WebViewClient
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
internal fun BrowserWebView(
    initialUrl: String,
    userAgent: String,
    backgroundColor: Color,
    controller: BrowserWebViewController,
    modifier: Modifier = Modifier,
    onWebViewCreated: (BrowserWebViewView) -> Unit = {},
    onWebViewReleased: () -> Unit = {},
    onNavigationRequest: (String) -> Boolean,
    onPageStarted: (BrowserWebViewView?, String?, Bitmap?) -> Unit,
    onPageFinished: (BrowserWebViewView?, String?) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onTitleChanged: (String?) -> Unit,
    onIconChanged: (String, Bitmap?) -> Unit,
    onFindResultChanged: (Int, Int, Boolean) -> Unit,
    onScrollSnapshotChanged: (BrowserScrollSnapshot) -> Unit,
    onCanGoBackChanged: (Boolean) -> Unit,
    onCanGoForwardChanged: (Boolean) -> Unit,
    onShowFileChooser: (ValueCallback<Array<Uri>>?, WebChromeClient.FileChooserParams?) -> Unit = { callback, _ ->
        callback?.onReceiveValue(null)
    },
    onPopupRequest: (BrowserPopupRequest) -> Unit = {},
    onDownloadRequest: (BrowserDownloadRequest) -> Unit = {},
    onWebPermissionRequest: (PermissionRequest?) -> Unit = { it?.deny() },
    onPageLoadError: (BrowserPageLoadError) -> Unit = {},
    onSslError: (SslErrorHandler?, SslError?) -> Unit = { handler, _ -> handler?.cancel() },
    onSyncCookiesToWebView: () -> Unit,
) {
    var attachedWebView by remember { mutableStateOf<BrowserWebViewView?>(null) }
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
            BrowserWebViewView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    setSupportMultipleWindows(true)
                    javaScriptCanOpenWindowsAutomatically = true
                    allowFileAccess = false
                    allowContentAccess = false
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    userAgentString = sanitizeBrowserUserAgent(userAgent)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        safeBrowsingEnabled = true
                    }
                }
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
                        onPageStarted(view as? BrowserWebViewView, url, favicon)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onPageFinished(view as? BrowserWebViewView, url)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        super.onReceivedError(view, request, error)
                        buildBrowserPageLoadError(
                            failingUrl = request?.url?.toString().orEmpty(),
                            isForMainFrame = request?.isForMainFrame == true,
                            description = error?.description?.toString(),
                        )?.let(onPageLoadError)
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?,
                    ) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        buildBrowserPageLoadError(
                            failingUrl = request?.url?.toString().orEmpty(),
                            isForMainFrame = request?.isForMainFrame == true,
                            description = errorResponse?.reasonPhrase,
                            statusCode = errorResponse?.statusCode,
                            reasonPhrase = errorResponse?.reasonPhrase,
                        )?.let(onPageLoadError)
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

                    override fun onCreateWindow(
                        view: WebView?,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: Message?,
                    ): Boolean {
                        val transport = resultMsg?.obj as? WebViewTransport ?: return false
                        val popupWebView = BrowserWebViewView(context)
                        var dispatched = false
                        val dispatchPopup: (String?) -> Unit = dispatch@{ requestedUrl ->
                            if (dispatched) {
                                return@dispatch
                            }
                            dispatched = true
                            onPopupRequest(
                                BrowserPopupRequest(
                                    requestedUrl = requestedUrl,
                                    fallbackUrl = view?.hitTestResult?.extra,
                                    isDialog = isDialog,
                                    isUserGesture = isUserGesture,
                                ),
                            )
                            popupWebView.stopLoading()
                            popupWebView.destroy()
                        }
                        popupWebView.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                dispatchPopup(request?.url?.toString())
                                return true
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                dispatchPopup(url)
                            }
                        }
                        transport.webView = popupWebView
                        resultMsg.sendToTarget()
                        return true
                    }

                    override fun onPermissionRequest(request: PermissionRequest?) {
                        onWebPermissionRequest(request)
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

                setDownloadListener(
                    DownloadListener { url, requestUserAgent, contentDisposition, mimeType, contentLength ->
                        url
                            ?.takeIf(String::isNotBlank)
                            ?.let { resolvedUrl ->
                                onDownloadRequest(
                                    BrowserDownloadRequest(
                                        url = resolvedUrl,
                                        userAgent = requestUserAgent,
                                        contentDisposition = contentDisposition,
                                        mimeType = mimeType,
                                        contentLength = contentLength,
                                    ),
                                )
                            }
                    },
                )

                setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
                    onFindResultChanged(activeMatchOrdinal, numberOfMatches, isDoneCounting)
                }

                setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                    onScrollSnapshotChanged(
                        buildBrowserScrollSnapshot(
                            scrollY = scrollY,
                            previousScrollY = oldScrollY,
                            contentHeightPx = verticalScrollRange,
                            viewportHeightPx = verticalScrollExtent,
                        ),
                    )
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
            onScrollSnapshotChanged(
                buildBrowserScrollSnapshot(
                    scrollY = view.scrollY,
                    previousScrollY = view.scrollY,
                    contentHeightPx = view.verticalScrollRange,
                    viewportHeightPx = view.verticalScrollExtent,
                ),
            )
        },
    )
}

private fun sanitizeBrowserUserAgent(userAgent: String): String {
    return userAgent
        .replace("; wv)", ")")
        .replace(Regex("Version/\\S+\\s*"), "")
}

