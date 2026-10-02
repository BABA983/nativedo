package io.github.baba983.nativedo.auth

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
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
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.URLUtil
import android.widget.FrameLayout
import android.widget.Toast
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.di.AppContainer
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import io.github.baba983.nativedo.core.web.DISCOURSE_ASSETS_ICONS_SCRIPT
import io.github.baba983.nativedo.core.web.decodeJavascriptString
import io.github.baba983.nativedo.core.web.isDiscourseSameOrigin
import io.github.baba983.nativedo.core.web.saveDiscourseSvgSpriteMarkup
import java.util.IdentityHashMap
import java.util.Collections

class LoginWebViewFragment @JvmOverloads constructor(
    /** What to do once the session is usable; the gate uses it to move on to the topic list. */
    private val onAuthenticated: Runnable? = null,
    /** Recovery is an explicit user completion, never inferred from existing login cookies. */
    private val recoveryMode: Boolean = false,
) : BaseFragment() {
    private val fragmentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var autofillManager: LoginAutofillManager
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
    private var completedNavigationGeneration = -1
    private var hasWebHistory = false
    private val popupWebViews = Collections.newSetFromMap(IdentityHashMap<WebView, Boolean>())

    @SuppressLint("SetJavaScriptEnabled")
    override fun createView(context: Context): View {
        actionBar.setBackButtonDrawable(BackDrawable(false))
        actionBar.setTitle(context.getString(if (recoveryMode) R.string.verification_webview_title else R.string.login_webview_title))
        if (recoveryMode) {
            actionBar.setSubtitle(context.getString(R.string.verification_webview_hint))
            actionBar.createMenu().addItem(1, context.getString(R.string.verification_complete))
        }
        actionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                if (id == -1 && onBackPressed(false)) {
                    finishFragment()
                } else if (id == 1 && recoveryMode) {
                    completeVerification()
                }
            }
        })

        appContainer = NativeDo.container(context)
        autofillManager = LoginAutofillManager(appContainer.credentialStore)

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
        root.addView(telegramErrorContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0))
        root.addView(lineProgressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 2f))

        val loginWebView = createLoginWebView(context, root)
        loadAfterCookieSync(loginWebView, loginUrl())
        return root
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createLoginWebView(context: Context, root: FrameLayout): WebView {
        val loginWebView = LoginWebViewFactory.create(context, appContainer.userAgent)
        LoginWebViewFactory.configureCookies(loginWebView)
        if (!recoveryMode) autofillManager.attach(loginWebView)

        webView = loginWebView
        rendererGone = false
        hasWebHistory = false

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
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0),
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
            completedNavigationGeneration = -1
            if (recoveryMode) {
                restoreJob?.cancel()
                restoreJob = null
            }
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
            completedNavigationGeneration = navigationGeneration
            if (!recoveryMode) autofillManager.onPageFinished(view, url)
            captureDiscourseAssetsIcons(webView = view, currentUrl = url)
            if (!recoveryMode) maybeCompleteLogin(webView = view, currentUrl = url)
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
            // Cloudflare's 403 document contains the interactive verification. Do not cover it
            // with a native error panel, but it is still not a completed navigation.
            if (recoveryMode && view === webView && request.isForMainFrame && errorResponse.statusCode == 403) {
                failedNavigationGeneration = navigationGeneration
                completedNavigationGeneration = -1
                progressJob?.cancel()
                progressView?.setProgress(0f, false)
                return
            }
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

    override fun onBackPressed(invoked: Boolean): Boolean {
        if (hasFinished) {
            return true
        }
        if (recoveryMode) {
            hasFinished = true
            restoreJob?.cancel()
            restoreJob = null
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
        if (recoveryMode) return true
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
        val generation = navigationGeneration
        restoreJob = fragmentScope.launch {
            val authenticated = appContainer.completeLoginUseCase(webView, currentUrl)
            if (hasFinished || (recoveryMode && generation != navigationGeneration)) return@launch
            if (authenticated) {
                hasFinished = true
                if (recoveryMode) {
                    Toast.makeText(webView.context, R.string.verification_session_synced, Toast.LENGTH_LONG).show()
                }
                val next = onAuthenticated
                if (next != null) {
                    next.run()
                } else {
                    finishFragment()
                }
            } else if (recoveryMode) {
                Toast.makeText(webView.context, R.string.verification_not_completed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun completeVerification() {
        if (hasFinished || restoreJob?.isActive == true) return
        val view = webView ?: return
        val url = view.url.orEmpty()
        if (completedNavigationGeneration != navigationGeneration ||
            !loginWebCanCompleteNavigation(navigationGeneration, failedNavigationGeneration) ||
            !shouldAttemptLoginSessionRestore(url, appContainer.baseUrl, hasFinished, false)
        ) {
            Toast.makeText(view.context, R.string.verification_not_completed, Toast.LENGTH_LONG).show()
            return
        }
        startLoginCompletion(view, url)
    }
}
