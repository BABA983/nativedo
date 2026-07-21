package io.github.baba983.nativedo.ui.auth

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.http.SslError
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebSettings
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.URLUtil
import android.widget.FrameLayout
import android.widget.Toast
import io.github.baba983.nativedo.NativeDoApp
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.network.NetworkJson
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.session.SessionUserSummary
import org.telegram.ui.ActionBar.BackDrawable
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ActionBar.ThemeDescription
import org.telegram.ui.ArticleViewer
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LineProgressView
import org.telegram.messenger.browser.Browser
import org.telegram.messenger.AndroidUtilities
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.baba983.nativedo.core.web.DISCOURSE_ASSETS_ICONS_SCRIPT
import io.github.baba983.nativedo.core.web.decodeJavascriptString
import io.github.baba983.nativedo.core.web.isDiscourseSameOrigin
import io.github.baba983.nativedo.core.web.saveDiscourseSvgSpriteMarkup
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.coroutines.resume
import java.util.Collections
import java.util.IdentityHashMap

private const val LOGIN_COOKIE_READY_MAX_ATTEMPTS = 20
private const val LOGIN_COOKIE_READY_DELAY_MILLIS = 500L

internal enum class LoginWebBackAction {
    CONSUME_RESTORE,
    GO_WEB_HISTORY,
    COMPLETE_SESSION,
    POP_FRAGMENT,
}

internal fun loginWebBackAction(
    isRestoringSession: Boolean,
    canGoBack: Boolean,
    hasAuthenticatedCookies: Boolean,
): LoginWebBackAction = when {
    isRestoringSession -> LoginWebBackAction.CONSUME_RESTORE
    canGoBack -> LoginWebBackAction.GO_WEB_HISTORY
    hasAuthenticatedCookies -> LoginWebBackAction.COMPLETE_SESSION
    else -> LoginWebBackAction.POP_FRAGMENT
}

internal sealed interface LoginWebNavigationDecision {
    data object LoadInWebView : LoginWebNavigationDecision
    data class LoadFallback(val url: String) : LoginWebNavigationDecision
    data object OpenExternal : LoginWebNavigationDecision
    data object Block : LoginWebNavigationDecision
}

internal fun loginWebNavigationDecision(url: String?): LoginWebNavigationDecision {
    val normalized = url?.trim().orEmpty()
    if (normalized.isEmpty()) return LoginWebNavigationDecision.Block
    val uri = runCatching { Uri.parse(normalized) }.getOrNull() ?: return LoginWebNavigationDecision.Block
    val scheme = uri.scheme?.lowercase().orEmpty()
    if (scheme == "http" || scheme == "https") return LoginWebNavigationDecision.LoadInWebView
    if (scheme == "intent") {
        val fallback = runCatching {
            Intent.parseUri(normalized, Intent.URI_INTENT_SCHEME)
                .getStringExtra("browser_fallback_url")
        }.getOrNull()
        if (fallback != null && loginWebNavigationDecision(fallback) == LoginWebNavigationDecision.LoadInWebView) {
            return LoginWebNavigationDecision.LoadFallback(fallback)
        }
        return LoginWebNavigationDecision.OpenExternal
    }
    if (scheme in setOf("javascript", "data", "file", "content", "about", "blob")) {
        return LoginWebNavigationDecision.Block
    }
    return LoginWebNavigationDecision.OpenExternal
}

internal fun loginWebShouldShowMainFrameError(
    isForMainFrame: Boolean,
    httpStatusCode: Int? = null,
): Boolean = isForMainFrame && (httpStatusCode == null || httpStatusCode >= 400)

internal fun loginWebCanCompleteNavigation(
    navigationGeneration: Int,
    failedNavigationGeneration: Int,
): Boolean = navigationGeneration != failedNavigationGeneration

internal enum class LoginWebDownloadDecision {
    AUTHENTICATED_SAME_ORIGIN,
    OPEN_EXTERNAL,
    BLOCK,
}

internal fun loginWebDownloadDecision(url: String?, baseUrl: String): LoginWebDownloadDecision {
    val normalized = url?.trim().orEmpty()
    if (normalized.isEmpty() || normalized.startsWith("blob:", ignoreCase = true)) {
        return LoginWebDownloadDecision.BLOCK
    }
    return when (loginWebNavigationDecision(normalized)) {
        LoginWebNavigationDecision.LoadInWebView -> {
            if (isDiscourseSameOrigin(normalized, baseUrl)) {
                LoginWebDownloadDecision.AUTHENTICATED_SAME_ORIGIN
            } else {
                LoginWebDownloadDecision.OPEN_EXTERNAL
            }
        }
        LoginWebNavigationDecision.OpenExternal,
        is LoginWebNavigationDecision.LoadFallback,
        -> LoginWebDownloadDecision.OPEN_EXTERNAL
        LoginWebNavigationDecision.Block -> LoginWebDownloadDecision.BLOCK
    }
}
private const val CURRENT_USER_SCRIPT = """
    (function() {
      function normalizeUser(currentUser) {
        if (!currentUser) {
          return null;
        }
        if (typeof currentUser === 'string') {
          try {
            currentUser = JSON.parse(currentUser);
          } catch (error) {
            return null;
          }
        }
        var username = currentUser.username || '';
        if (!username) {
          return null;
        }
        return {
          username: username,
          name: currentUser.name || '',
          avatar_template: currentUser.avatar_template || ''
        };
      }
      var meta = document.querySelector('meta[name="current-username"]');
      var metaUsername = meta && meta.content || '';
      var preloaded = document.querySelector('#data-preloaded');
      var user = null;
      try {
        var payload = JSON.parse(preloaded.getAttribute('data-preloaded') || '{}');
        user = normalizeUser(payload.currentUser);
      } catch (error) {
      }
      if (!user && metaUsername) {
        user = {
          username: metaUsername,
          name: '',
          avatar_template: ''
        };
      }
      if (!user) {
        return '';
      }
      return JSON.stringify(user);
    })();
"""

@Serializable
private data class LoginCurrentUserPayload(
    val username: String = "",
    val name: String = "",
    @SerialName("avatar_template")
    val avatarTemplate: String = "",
) {
    fun toSessionUserSummary(): SessionUserSummary? {
        val normalizedUsername = username.takeIf { it.isNotBlank() } ?: return null
        return SessionUserSummary(
            username = normalizedUsername,
            displayName = name.takeIf { it.isNotBlank() } ?: normalizedUsername,
            avatarTemplate = avatarTemplate,
        )
    }
}

internal fun decodeCurrentUserPayload(rawValue: String?): SessionUserSummary? {
    val payload = decodeJavascriptString(rawValue).trim()
    if (payload.isBlank()) {
        return null
    }
    return runCatching {
        NetworkJson.instance.decodeFromString<LoginCurrentUserPayload>(payload)
            .toSessionUserSummary()
    }.getOrNull()
}

class LoginWebViewFragment : BaseFragment() {
    private val fragmentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var appContainer: AppContainer
    private var webView: WebView? = null
    private var webRoot: FrameLayout? = null
    private var progressView: LineProgressView? = null
    private var errorContainer: ArticleViewer.ErrorContainer? = null
    private var restoreJob: Job? = null
    private var progressJob: Job? = null
    private var initialLoadJob: Job? = null
    private var hasFinished = false
    private var rendererGone = false
    private var requiresCookieResync = false
    private var failedUrl: String? = null
    private var navigationGeneration = 0
    private var failedNavigationGeneration = -1
    private var hasWebHistory = false
    private val popupWebViews = Collections.newSetFromMap(IdentityHashMap<WebView, Boolean>())

    @SuppressLint("SetJavaScriptEnabled")
    override fun createView(context: Context): View {
        actionBar.setBackButtonDrawable(BackDrawable(false))
        actionBar.setTitle(context.getString(R.string.login_webview_title))
        actionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                if (id == -1 && onBackPressed()) {
                    finishFragment()
                }
            }
        })

        appContainer = (context.applicationContext as NativeDoApp).appContainer

        val root = FrameLayout(context).apply {
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
        }
        webRoot = root
        val lineProgressView = LineProgressView(context).apply {
            setProgressColor(Theme.getColor(Theme.key_featuredStickers_addButton))
            setProgress(0f, false)
        }
        progressView = lineProgressView
        val telegramErrorContainer = ArticleViewer.ErrorContainer(context).apply {
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
            setDark(false, false)
            buttonView.setOnClickListener { retryFailedPage() }
        }
        errorContainer = telegramErrorContainer
        root.addView(telegramErrorContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))
        root.addView(lineProgressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 2))

        val loginWebView = createLoginWebView(context, root)
        loadAfterCookieSync(loginWebView, loginUrl())
        return root
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createLoginWebView(context: Context, root: FrameLayout): WebView {
        val loginWebView = WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = appContainer.userAgent
            settings.loadWithOverviewMode = false
            settings.useWideViewPort = false
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(true)
            settings.mediaPlaybackRequiresUserGesture = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                settings.safeBrowsingEnabled = true
            }
        }
        webView = loginWebView
        rendererGone = false
        hasWebHistory = false

        CookieManager.getInstance().setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(loginWebView, true)
        }

        loginWebView.webChromeClient = createLoginWebChromeClient(loginWebView)
        loginWebView.webViewClient = createLoginWebViewClient()
        loginWebView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            handleLoginDownload(
                context = context,
                url = url,
                userAgent = userAgent,
                contentDisposition = contentDisposition,
                mimeType = mimeType,
            )
        }
        root.addView(
            loginWebView,
            0,
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT),
        )
        errorContainer?.bringToFront()
        progressView?.bringToFront()
        return loginWebView
    }

    private fun createLoginWebChromeClient(owner: WebView): WebChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            if (view !== webView) return
            val lineProgressView = progressView ?: return
            val progress = (newProgress / 100f).coerceIn(0f, 1f)
            if (progress < lineProgressView.currentProgress) {
                lineProgressView.setProgress(0f, false)
            }
            lineProgressView.setProgress(progress, true)
            if (newProgress >= 100) progressJob?.cancel()
        }

        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message,
        ): Boolean {
            if (view !== owner || view !== webView || !isUserGesture) return false
            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
            val popup = WebView(view.context)
            popupWebViews += popup
            popup.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(popupView: WebView, request: WebResourceRequest): Boolean {
                    consumePopupNavigation(popupView, request.url.toString())
                    return true
                }

                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(popupView: WebView, url: String): Boolean {
                    consumePopupNavigation(popupView, url)
                    return true
                }

                override fun onPageStarted(popupView: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    if (!url.equals("about:blank", ignoreCase = true)) {
                        consumePopupNavigation(popupView, url)
                    }
                }

                override fun onRenderProcessGone(popupView: WebView, detail: RenderProcessGoneDetail?): Boolean {
                    destroyPopupWebView(popupView)
                    return true
                }
            }
            transport.webView = popup
            resultMsg.sendToTarget()
            return true
        }

        override fun onCloseWindow(window: WebView) {
            destroyPopupWebView(window)
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: android.webkit.ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean {
            filePathCallback.onReceiveValue(null)
            return true
        }
    }

    private fun createLoginWebViewClient(): WebViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            return handleLoginNavigation(view, request.url.toString())
        }

        @Suppress("DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            return handleLoginNavigation(view, url)
        }

        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            super.onPageStarted(view, url, favicon)
            if (view !== webView) return
            navigationGeneration += 1
            failedNavigationGeneration = -1
            failedUrl = null
            hideLoadError()
            progressJob?.cancel()
            progressView?.setProgress(0f, false)
            progressView?.setProgress(0.3f, true)
            progressJob = fragmentScope.launch {
                var progress = 0.3f
                while (progress < 0.7f) {
                    delay(100L)
                    progress += if (0.7f - progress < 0.25f) 0.01f else 0.02f
                    progressView?.setProgress(progress.coerceAtMost(0.7f), true)
                }
            }
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            if (view === webView) {
                hasWebHistory = view.canGoBack()
            }
            super.doUpdateVisitedHistory(view, url, isReload)
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            if (view !== webView) return
            hasWebHistory = view.canGoBack()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().flush()
            }
            if (!loginWebCanCompleteNavigation(navigationGeneration, failedNavigationGeneration)) {
                return
            }
            captureDiscourseAssetsIcons(webView = view, currentUrl = url)
            maybeCompleteLogin(webView = view, currentUrl = url)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                loginWebShouldShowMainFrameError(request.isForMainFrame)
            ) {
                showLoadError(
                    view = view,
                    url = request.url?.toString().orEmpty(),
                    code = error.errorCode,
                    description = error.description?.toString(),
                )
            }
            super.onReceivedError(view, request, error)
        }

        @Suppress("DEPRECATION")
        override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                showLoadError(view, failingUrl, errorCode, description)
            }
            super.onReceivedError(view, errorCode, description, failingUrl)
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            if (loginWebShouldShowMainFrameError(request.isForMainFrame, errorResponse.statusCode)) {
                showLoadError(
                    view = view,
                    url = request.url?.toString().orEmpty(),
                    code = errorResponse.statusCode,
                    description = errorResponse.reasonPhrase,
                )
            }
            super.onReceivedHttpError(view, request, errorResponse)
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
            showLoadError(view, error.url.orEmpty(), error.primaryError, error.toString())
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail?): Boolean {
            handleRenderProcessGone(view, detail)
            return true
        }

        private fun handleLoginNavigation(view: WebView, url: String): Boolean {
            if (view !== webView) return true
            return when (val decision = loginWebNavigationDecision(url)) {
                LoginWebNavigationDecision.LoadInWebView -> false
                is LoginWebNavigationDecision.LoadFallback -> {
                    view.loadUrl(decision.url)
                    true
                }
                LoginWebNavigationDecision.OpenExternal -> {
                    Browser.openUrl(view.context, url)
                    true
                }
                LoginWebNavigationDecision.Block -> true
            }
        }
    }

    private fun loginUrl(): String = appContainer.baseUrl.trimEnd('/') + "/login"

    private fun loadAfterCookieSync(view: WebView, url: String) {
        initialLoadJob?.cancel()
        initialLoadJob = fragmentScope.launch {
            val synced = appContainer.cookieSyncBridge.syncToWebViewAsync()
            if (webView !== view || hasFinished) return@launch
            if (synced) {
                requiresCookieResync = false
                view.loadUrl(url)
            } else {
                requiresCookieResync = true
                showLoadError(view, url, 0, "WebView cookies could not be restored")
            }
        }
    }

    private fun showLoadError(
        view: WebView,
        url: String,
        code: Int,
        description: String?,
    ) {
        if (view !== webView) return
        failedNavigationGeneration = navigationGeneration
        failedUrl = url.ifBlank { view.url.orEmpty().ifBlank(::loginUrl) }
        progressJob?.cancel()
        progressView?.setProgress(0f, false)
        showErrorContainer(failedUrl, code, description)
    }

    private fun showErrorContainer(url: String?, code: Int, description: String?) {
        val container = errorContainer ?: return
        container.set(url, code, description.orEmpty())
        container.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
        container.setDark(false, false)
        AndroidUtilities.updateViewVisibilityAnimated(container, true, 1f, false)
        container.bringToFront()
        progressView?.bringToFront()
    }

    private fun hideLoadError() {
        errorContainer?.let { container ->
            AndroidUtilities.updateViewVisibilityAnimated(container, false, 1f, false)
        }
    }

    private fun retryFailedPage() {
        if (hasFinished) return
        val targetUrl = failedUrl.orEmpty().ifBlank(::loginUrl)
        failedNavigationGeneration = -1
        hideLoadError()
        val current = webView
        if (rendererGone || current == null) {
            val root = webRoot ?: return
            val recreated = createLoginWebView(root.context, root)
            loadAfterCookieSync(recreated, targetUrl)
        } else if (requiresCookieResync) {
            loadAfterCookieSync(current, targetUrl)
        } else {
            current.loadUrl(targetUrl)
        }
    }

    private fun handleRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail?) {
        if (view !== webView) {
            destroyPopupWebView(view)
            return
        }
        val lastUrl = view.url.orEmpty().ifBlank(::loginUrl)
        rendererGone = true
        failedUrl = lastUrl
        failedNavigationGeneration = navigationGeneration
        hasWebHistory = false
        restoreJob?.cancel()
        restoreJob = null
        progressJob?.cancel()
        progressJob = null
        initialLoadJob?.cancel()
        initialLoadJob = null
        webView = null
        (view.parent as? ViewGroup)?.removeView(view)
        runCatching { view.destroy() }
        val description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && detail?.didCrash() == true) {
            "Android System WebView renderer crashed"
        } else {
            "Android System WebView renderer stopped"
        }
        showErrorContainer(lastUrl, 0, description)
    }

    private fun consumePopupNavigation(popup: WebView, url: String) {
        if (url.equals("about:blank", ignoreCase = true)) return
        val owner = webView
        when (val decision = loginWebNavigationDecision(url)) {
            LoginWebNavigationDecision.LoadInWebView -> owner?.loadUrl(url)
            is LoginWebNavigationDecision.LoadFallback -> owner?.loadUrl(decision.url)
            LoginWebNavigationDecision.OpenExternal -> Browser.openUrl(popup.context, url)
            LoginWebNavigationDecision.Block -> Unit
        }
        destroyPopupWebView(popup)
    }

    private fun destroyPopupWebView(popup: WebView) {
        if (!popupWebViews.remove(popup)) return
        (popup.parent as? ViewGroup)?.removeView(popup)
        runCatching {
            popup.stopLoading()
            popup.webChromeClient = null
            popup.webViewClient = WebViewClient()
            popup.destroy()
        }
    }

    private fun handleLoginDownload(
        context: Context,
        url: String?,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
    ) {
        when (loginWebDownloadDecision(url, appContainer.baseUrl)) {
            LoginWebDownloadDecision.BLOCK -> return
            LoginWebDownloadDecision.OPEN_EXTERNAL -> {
                url?.let { Browser.openUrl(context, it) }
            }
            LoginWebDownloadDecision.AUTHENTICATED_SAME_ORIGIN -> {
                val downloadUrl = url ?: return
                runCatching {
                    val filename = URLUtil.guessFileName(downloadUrl, contentDisposition, mimeType)
                    val request = DownloadManager.Request(Uri.parse(downloadUrl))
                        .setTitle(filename)
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, filename)
                    mimeType?.takeIf { it.isNotBlank() }?.let(request::setMimeType)
                    userAgent?.takeIf { it.isNotBlank() }?.let { request.addRequestHeader("User-Agent", it) }
                    CookieManager.getInstance().getCookie(downloadUrl)
                        ?.takeIf { it.isNotBlank() }
                        ?.let { request.addRequestHeader("Cookie", it) }
                    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    manager.enqueue(request)
                }.onSuccess {
                    Toast.makeText(context, R.string.app_browser_download_started, Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, R.string.app_browser_download_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onPause() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().flush()
        }
        webView?.onPause()
        super.onPause()
    }

    override fun getThemeDescriptions(): java.util.ArrayList<ThemeDescription> {
        val descriptions = java.util.ArrayList<ThemeDescription>()
        actionBar?.let { bar ->
            descriptions.add(ThemeDescription(bar, ThemeDescription.FLAG_BACKGROUND, null, null as android.graphics.Paint?, null, null, Theme.key_actionBarDefault))
            descriptions.add(ThemeDescription(bar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null as android.graphics.Paint?, null, null, Theme.key_actionBarDefaultIcon))
            descriptions.add(ThemeDescription(bar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null as android.graphics.Paint?, null, null, Theme.key_actionBarDefaultTitle))
            descriptions.add(ThemeDescription(bar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null as android.graphics.Paint?, null, null, Theme.key_actionBarDefaultSelector))
        }
        webRoot?.let { root ->
            descriptions.add(ThemeDescription(root, ThemeDescription.FLAG_BACKGROUND, null, null as android.graphics.Paint?, null, null, Theme.key_windowBackgroundWhite))
        }
        progressView?.let { progress ->
            descriptions.add(ThemeDescription(progress, ThemeDescription.FLAG_PROGRESSBAR, null, null as android.graphics.Paint?, null, null, Theme.key_featuredStickers_addButton))
        }
        val errorDelegate = ThemeDescription.ThemeDescriptionDelegate {
            val background = Theme.getColor(Theme.key_windowBackgroundWhite)
            errorContainer?.setBackgroundColor(background)
            errorContainer?.setDark(AndroidUtilities.computePerceivedBrightness(background) <= 0.721f, false)
        }
        descriptions.add(ThemeDescription(null, 0, null, null as android.graphics.Paint?, null, errorDelegate, Theme.key_windowBackgroundWhite))
        return descriptions
    }

    override fun onBackPressed(): Boolean {
        if (hasFinished) {
            return true
        }
        val view = webView ?: return true
        return when (
            loginWebBackAction(
                isRestoringSession = restoreJob?.isActive == true,
                canGoBack = view.canGoBack(),
                hasAuthenticatedCookies = appContainer.cookieSyncBridge
                    .hasAuthenticatedCookiesInWebView(appContainer.baseUrl),
            )
        ) {
            LoginWebBackAction.CONSUME_RESTORE -> false
            LoginWebBackAction.GO_WEB_HISTORY -> {
                view.goBack()
                false
            }
            LoginWebBackAction.COMPLETE_SESSION -> {
                startLoginCompletion(webView = view, currentUrl = view.url.orEmpty())
                false
            }
            LoginWebBackAction.POP_FRAGMENT -> true
        }
    }

    override fun canBeginSlide(): Boolean {
        return restoreJob?.isActive != true && webView?.canGoBack() != true && !hasWebHistory
    }

    override fun isSwipeBackEnabled(event: android.view.MotionEvent): Boolean {
        return canBeginSlide()
    }

    override fun onFragmentDestroy() {
        restoreJob?.cancel()
        restoreJob = null
        progressJob?.cancel()
        progressJob = null
        initialLoadJob?.cancel()
        initialLoadJob = null
        popupWebViews.toList().forEach(::destroyPopupWebView)
        webView?.let { view ->
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    CookieManager.getInstance().flush()
                }
                AndroidUtilities.hideKeyboard(view)
                view.clearFocus()
                view.webChromeClient = null
                view.webViewClient = WebViewClient()
                view.setDownloadListener(null)
                (view.parent as? ViewGroup)?.removeView(view)
                view.stopLoading()
                view.loadUrl("about:blank")
                view.destroy()
            }
        }
        webView = null
        webRoot = null
        progressView = null
        errorContainer = null
        hasWebHistory = false
        fragmentScope.cancel()
        super.onFragmentDestroy()
    }

    private fun maybeCompleteLogin(
        webView: WebView,
        currentUrl: String,
    ) {
        if (!shouldAttemptLoginSessionRestore(
                currentUrl = currentUrl,
                baseUrl = appContainer.baseUrl,
                hasFinished = hasFinished,
                isRestoringSession = restoreJob?.isActive == true,
            )
        ) {
            return
        }
        startLoginCompletion(webView = webView, currentUrl = currentUrl)
    }

    private fun captureDiscourseAssetsIcons(
        webView: WebView,
        currentUrl: String,
    ) {
        if (!isDiscourseSameOrigin(currentUrl = currentUrl, baseUrl = appContainer.baseUrl)) {
            return
        }
        webView.evaluateJavascript(DISCOURSE_ASSETS_ICONS_SCRIPT) { rawValue ->
            saveDiscourseSvgSpriteMarkup(
                appContainer.svgSpriteStore,
                decodeJavascriptString(rawValue),
            )
        }
    }

    private fun startLoginCompletion(webView: WebView, currentUrl: String) {
        restoreJob = fragmentScope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().flush()
            }
            val authenticated = awaitAuthenticatedSession(
                hasAuthenticatedCookies = {
                    appContainer.cookieSyncBridge.hasAuthenticatedCookiesInWebView(appContainer.baseUrl)
                },
                completeLogin = {
                    val liveUrl = webView.url.orEmpty().ifBlank { currentUrl }
                    val currentUser = if (isDiscourseSameOrigin(liveUrl, appContainer.baseUrl)) {
                        webView.readCurrentUser()
                    } else {
                        null
                    }
                    if (currentUser != null) {
                        appContainer.sessionCoordinator.completeLoginFromWebViewWithKnownUsername(
                            username = currentUser.username,
                            displayName = currentUser.displayName,
                            avatarTemplate = currentUser.avatarTemplate,
                        )
                    } else {
                        appContainer.sessionCoordinator.completeLoginFromWebView()
                    }
                    appContainer.sessionCoordinator.sessionState.value is AppSessionState.Authenticated
                },
            )
            if (authenticated) {
                hasFinished = true
                finishFragment()
            }
        }
    }
}

internal fun shouldAttemptLoginSessionRestore(
    currentUrl: String,
    baseUrl: String,
    hasFinished: Boolean,
    isRestoringSession: Boolean,
): Boolean {
    if (hasFinished || isRestoringSession) {
        return false
    }
    val current = currentUrl.toHttpUrlOrNull() ?: return false
    return isDiscourseSameOrigin(currentUrl = currentUrl, baseUrl = baseUrl) &&
        current.encodedPath != "/login" &&
        !current.encodedPath.startsWith("/login/")
}

internal suspend fun awaitAuthenticatedSession(
    hasAuthenticatedCookies: () -> Boolean,
    completeLogin: suspend () -> Boolean,
    maxAttempts: Int = LOGIN_COOKIE_READY_MAX_ATTEMPTS,
    delayMillis: Long = LOGIN_COOKIE_READY_DELAY_MILLIS,
): Boolean {
    repeat(maxAttempts) { attempt ->
        if (hasAuthenticatedCookies() && completeLogin()) {
            return true
        }
        if (attempt != maxAttempts - 1) {
            delay(delayMillis)
        }
    }
    return false
}

private suspend fun WebView.readCurrentUser(): SessionUserSummary? = suspendCancellableCoroutine { continuation ->
    evaluateJavascript(CURRENT_USER_SCRIPT) { rawValue ->
        if (continuation.isActive) {
            continuation.resume(decodeCurrentUserPayload(rawValue))
        }
    }
}
