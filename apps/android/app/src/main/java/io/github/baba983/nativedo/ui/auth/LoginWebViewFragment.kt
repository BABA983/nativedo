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
import io.github.baba983.nativedo.core.network.NetworkJson
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.session.SessionUserSummary
import org.telegram.ui.ActionBar.BackDrawable
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper
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

private const val LOGIN_COOKIE_READY_MAX_ATTEMPTS = 20
private const val LOGIN_COOKIE_READY_DELAY_MILLIS = 500L
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
                captureDiscourseAssetsIcons(webView = view, currentUrl = url)
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
            val currentUser = if (isDiscourseSameOrigin(currentUrl, appContainer.baseUrl)) {
                webView.readCurrentUser()
            } else {
                null
            }
            val authenticated = awaitAuthenticatedSession(
                hasAuthenticatedCookies = {
                    appContainer.cookieSyncBridge.hasAuthenticatedCookiesInWebView(appContainer.baseUrl)
                },
                completeLogin = {
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
        if (hasAuthenticatedCookies()) {
            return completeLogin()
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
