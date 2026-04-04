package io.github.baba983.nativedo.feature.auth.ui

import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.baba983.nativedo.NativeDoApplication
import io.github.baba983.nativedo.core.log.debugLog
import io.github.baba983.nativedo.core.log.NativeDoLogTag
import io.github.baba983.nativedo.core.session.AppSessionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LOGIN_COOKIE_READY_MAX_ATTEMPTS = 20
private const val LOGIN_COOKIE_READY_DELAY_MILLIS = 500L

@Composable
fun LoginWebViewRoute(onLoginFinished: () -> Unit) {
    val context = LocalContext.current
    val appContainer = remember(context) {
        (context.applicationContext as NativeDoApplication).appContainer
    }
    val sessionState by appContainer.sessionCoordinator.sessionState.collectAsStateWithLifecycle()
    val hasFinished = remember { mutableStateOf(false) }
    val isRestoringSession = remember { mutableStateOf(false) }
    val loginRestoreScope = rememberCoroutineScope()

    LaunchedEffect(sessionState, hasFinished.value) {
        if (!hasFinished.value && sessionState is AppSessionState.Authenticated) {
            hasFinished.value = true
            onLoginFinished()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = appContainer.userAgent
                settings.loadWithOverviewMode = false
                settings.useWideViewPort = false
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                setBackgroundColor(Color.Transparent.toArgb())
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)

                        val currentUrl = url.orEmpty()
                        if (!shouldAttemptLoginSessionRestore(currentUrl, hasFinished.value, isRestoringSession.value)) {
                            return
                        }

                        isRestoringSession.value = true
                        loginRestoreScope.launch {
                            isRestoringSession.value = true
                            val authenticated = awaitAuthenticatedSession(
                                hasAuthenticatedCookies = {
                                    appContainer.cookieSyncBridge.syncFromWebView(currentUrl)
                                },
                                completeLoginFromWebView = {
                                    appContainer.sessionCoordinator.completeLoginFromWebView()
                                    appContainer.sessionCoordinator.sessionState.value
                                },
                            )
                            if (!authenticated) {
                                isRestoringSession.value = false
                            }
                        }
                    }
                }
                loadUrl("https://linux.do/login")
            }
        },
    )
}

internal fun shouldAttemptLoginSessionRestore(
    currentUrl: String,
    hasFinished: Boolean,
    isRestoringSession: Boolean,
): Boolean {
    return !hasFinished &&
        !isRestoringSession &&
        currentUrl.contains("linux.do") &&
        !currentUrl.contains("/login")
}

internal suspend fun awaitAuthenticatedSession(
    hasAuthenticatedCookies: () -> Boolean,
    completeLoginFromWebView: suspend () -> AppSessionState,
    maxAttempts: Int = LOGIN_COOKIE_READY_MAX_ATTEMPTS,
    delayMillis: Long = LOGIN_COOKIE_READY_DELAY_MILLIS,
): Boolean {
    repeat(maxAttempts) { attempt ->
        val cookiesReady = hasAuthenticatedCookies()
        debugLog(
            NativeDoLogTag.Session,
            "awaitAuthenticatedSession attempt=${attempt + 1}/$maxAttempts cookiesReady=$cookiesReady",
        )
        if (cookiesReady) {
            return completeLoginFromWebView() is AppSessionState.Authenticated
        }
        if (attempt != maxAttempts - 1) {
            delay(delayMillis)
        }
    }
    return false
}
