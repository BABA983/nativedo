package io.github.baba983.nativedo.ui.auth

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import io.github.baba983.nativedo.NativeDoApp
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.session.AppSessionState
import org.telegram.ui.ActionBar.BackDrawable
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.coroutines.resume

private const val LOGIN_COOKIE_READY_MAX_ATTEMPTS = 20
private const val LOGIN_COOKIE_READY_DELAY_MILLIS = 500L
private const val CURRENT_USERNAME_SCRIPT = """
    (function() {
      var meta = document.querySelector('meta[name="current-username"]');
      if (meta && meta.content) {
        return meta.content;
      }
      var preloaded = document.querySelector('#data-preloaded');
      if (!preloaded) {
        return '';
      }
      try {
        var payload = JSON.parse(preloaded.getAttribute('data-preloaded') || '{}');
        var currentUser = payload.currentUser;
        if (typeof currentUser === 'string') {
          currentUser = JSON.parse(currentUser);
        }
        return currentUser && currentUser.username || '';
      } catch (error) {
        return '';
      }
    })();
"""

class LoginWebViewFragment : BaseFragment() {
    private val fragmentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var appContainer: AppContainer
    private var webView: WebView? = null
    private var restoreJob: Job? = null
    private var hasFinished = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun createView(context: Context): View {
        actionBar.setBackButtonDrawable(BackDrawable(false))
        actionBar.setTitle(context.getString(R.string.login_webview_title))

        appContainer = (context.applicationContext as NativeDoApp).appContainer
        appContainer.cookieSyncBridge.syncToWebView()

        val root = FrameLayout(context).apply {
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
        }
        val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            progress = 0
            visibility = View.GONE
        }
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
        }
        webView = loginWebView

        CookieManager.getInstance().setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(loginWebView, true)
        }

        loginWebView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }
        loginWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                maybeCompleteLogin(webView = view, currentUrl = url)
            }
        }

        root.addView(loginWebView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT))
        root.addView(progressBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 2))
        loginWebView.loadUrl(appContainer.baseUrl.trimEnd('/') + "/login")
        return root
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onPause() {
        webView?.onPause()
        super.onPause()
    }

    override fun onBackPressed(): Boolean {
        if (hasFinished) {
            return true
        }
        if (restoreJob?.isActive == true) {
            return false
        }
        val view = webView ?: return true
        if (!appContainer.cookieSyncBridge.hasAuthenticatedCookiesInWebView(appContainer.baseUrl)) {
            return true
        }
        startLoginCompletion(webView = view, currentUrl = view.url.orEmpty())
        return false
    }

    override fun onFragmentDestroy() {
        restoreJob?.cancel()
        restoreJob = null
        webView?.let { view ->
            runCatching {
                (view.parent as? ViewGroup)?.removeView(view)
                view.stopLoading()
                view.loadUrl("about:blank")
                view.destroy()
            }
        }
        webView = null
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

    private fun startLoginCompletion(webView: WebView, currentUrl: String) {
        restoreJob = fragmentScope.launch {
            val username = if (isSameOrigin(currentUrl, appContainer.baseUrl)) {
                webView.readCurrentUsername().takeIf { it.isNotBlank() }
            } else {
                null
            }
            val authenticated = awaitAuthenticatedSession(
                hasAuthenticatedCookies = {
                    appContainer.cookieSyncBridge.hasAuthenticatedCookiesInWebView(appContainer.baseUrl)
                },
                completeLogin = {
                    if (username != null) {
                        appContainer.sessionCoordinator.completeLoginFromWebViewWithKnownUsername(username)
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
    return isSameOrigin(currentUrl = currentUrl, baseUrl = baseUrl) &&
        current.encodedPath != "/login" &&
        !current.encodedPath.startsWith("/login/")
}

private fun isSameOrigin(currentUrl: String, baseUrl: String): Boolean {
    val current = currentUrl.toHttpUrlOrNull() ?: return false
    val base = baseUrl.toHttpUrlOrNull() ?: return false
    return current.scheme == base.scheme &&
        current.host == base.host &&
        current.port == base.port
}

internal suspend fun awaitAuthenticatedSession(
    hasAuthenticatedCookies: () -> Boolean,
    completeLogin: suspend () -> Boolean,
    maxAttempts: Int = LOGIN_COOKIE_READY_MAX_ATTEMPTS,
    delayMillis: Long = LOGIN_COOKIE_READY_DELAY_MILLIS,
): Boolean {
    repeat(maxAttempts) { attempt ->
        if (hasAuthenticatedCookies()) {
            return completeLogin()
        }
        if (attempt != maxAttempts - 1) {
            delay(delayMillis)
        }
    }
    return false
}

internal fun decodeJavascriptString(rawValue: String?): String {
    val normalized = rawValue.orEmpty()
    if (normalized.isBlank() || normalized == "null") {
        return ""
    }
    return runCatching {
        Json.decodeFromString<String>(normalized)
    }.getOrDefault("")
}

private suspend fun WebView.readCurrentUsername(): String = suspendCancellableCoroutine { continuation ->
    evaluateJavascript(CURRENT_USERNAME_SCRIPT) { rawValue ->
        if (continuation.isActive) {
            continuation.resume(decodeJavascriptString(rawValue).trim())
        }
    }
}
