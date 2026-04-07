package io.github.baba983.nativedo.feature.auth.ui

import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume

private const val LOGIN_COOKIE_READY_MAX_ATTEMPTS = 20
private const val LOGIN_COOKIE_READY_DELAY_MILLIS = 500L
private const val SVG_SPRITE_CAPTURE_MAX_ATTEMPTS = 20
private const val SVG_SPRITE_CAPTURE_DELAY_MILLIS = 300L
private const val SVG_SPRITE_QUERY_SCRIPT = """
    (function() {
      var stateKey = '__nativeDoSvgSpriteState';
      var settleDelayMillis = 250;

      function readMarkup() {
        var root = document.querySelector('discourse-assets-icons #svg-sprites');
        if (!root) {
          return '';
        }
        return (root.innerHTML || '').trim();
      }

      var state = window[stateKey];
      if (!state) {
        state = {
          latestMarkup: '',
          stableMarkup: '',
          settleTimer: null,
          observer: null
        };
        window[stateKey] = state;
      }

      function refreshMarkup() {
        var markup = readMarkup();
        if (markup === state.latestMarkup) {
          return;
        }
        state.latestMarkup = markup;
        state.stableMarkup = '';
        if (state.settleTimer) {
          clearTimeout(state.settleTimer);
          state.settleTimer = null;
        }
        if (markup.indexOf('<symbol') === -1) {
          return;
        }
        state.settleTimer = setTimeout(function() {
          var settledMarkup = readMarkup();
          if (settledMarkup === state.latestMarkup && settledMarkup.indexOf('<symbol') !== -1) {
            state.stableMarkup = settledMarkup;
          }
        }, settleDelayMillis);
      }

      if (!state.observer) {
        state.observer = new MutationObserver(function() {
          refreshMarkup();
        });
        state.observer.observe(document.documentElement || document, {
          childList: true,
          subtree: true
        });
      }

      refreshMarkup();
      return state.stableMarkup || '';
    })();
"""

@Composable
fun LoginWebViewRoute(onLoginFinished: () -> Unit) {
    val context = LocalContext.current
    val appContainer = remember(context) {
        (context.applicationContext as NativeDoApplication).appContainer
    }
    val sessionState by appContainer.sessionCoordinator.sessionState.collectAsStateWithLifecycle()
    val hasFinished = remember { mutableStateOf(false) }
    val isRestoringSession = remember { mutableStateOf(false) }
    val svgSpriteCaptureJob = remember { mutableStateOf<Job?>(null) }
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
                        if (
                            view != null &&
                            currentUrl.contains("linux.do")
                        ) {
                            svgSpriteCaptureJob.value?.cancel()
                            svgSpriteCaptureJob.value = loginRestoreScope.launch {
                                val markup = awaitSvgSpriteMarkup(
                                    readSvgSpriteMarkup = {
                                        view.readSvgSpriteMarkup()
                                    },
                                )
                                markup?.let {
                                    val shouldLogCacheUpdate = appContainer.svgSpriteStore.sprite.value != it
                                    appContainer.svgSpriteStore.save(it)
                                    if (shouldLogCacheUpdate) {
                                        debugLog(
                                            NativeDoLogTag.Auth,
                                            "svg sprite cached length=${it.length}",
                                        )
                                    }
                                } ?: debugLog(
                                    NativeDoLogTag.Auth,
                                    "svg sprite capture timed out before markup became stable",
                                )
                            }
                        }
                        if (!shouldAttemptLoginSessionRestore(currentUrl, hasFinished.value, isRestoringSession.value)) {
                            return
                        }

                        isRestoringSession.value = true
                        loginRestoreScope.launch {
                            val authenticated = awaitAuthenticatedSession(
                                hasAuthenticatedCookies = {
                                    appContainer.cookieSyncBridge.syncFromWebView(currentUrl)
                                },
                                completeLoginFromWebView = {
                                    appContainer.sessionCoordinator.completeLoginFromWebView()
                                    appContainer.sessionCoordinator.sessionState.value
                                }
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

internal suspend fun awaitSvgSpriteMarkup(
    readSvgSpriteMarkup: suspend () -> String,
    maxAttempts: Int = SVG_SPRITE_CAPTURE_MAX_ATTEMPTS,
    delayMillis: Long = SVG_SPRITE_CAPTURE_DELAY_MILLIS,
): String? {
    repeat(maxAttempts) { attempt ->
        val markup = readSvgSpriteMarkup().trim()
        if (markup.isNotBlank()) {
            return markup
        }
        if (attempt != maxAttempts - 1) {
            delay(delayMillis)
        }
    }
    return null
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

private suspend fun WebView.readSvgSpriteMarkup(): String = suspendCancellableCoroutine { continuation ->
    evaluateJavascript(SVG_SPRITE_QUERY_SCRIPT) { rawValue ->
        if (continuation.isActive) {
            continuation.resume(decodeJavascriptString(rawValue))
        }
    }
}
