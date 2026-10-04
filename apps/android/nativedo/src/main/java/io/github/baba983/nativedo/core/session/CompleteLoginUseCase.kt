package io.github.baba983.nativedo.core.session

import android.webkit.CookieManager
import android.webkit.WebView
import io.github.baba983.nativedo.core.web.isDiscourseSameOrigin
import kotlinx.coroutines.delay

/**
 * Orchestrates the login completion flow after the WebView detects a successful
 * Discourse login.
 *
 * <p>The WebView writes its cookies straight into the shared [SharedCookieJar]; there is nothing
 * to import. This use case only reads the DOM identity, waits out Chromium's asynchronous cookie
 * flush, and hands the identity to [SessionCoordinator.completeLoginFromWebViewStaged], which
 * stores it with [SessionFreshness.WebView] so normal startup verifies it natively later.</p>
 */
class CompleteLoginUseCase(
    private val sessionCoordinator: SessionCoordinator,
    private val cookieJar: SharedCookieJar,
    private val baseUrl: String,
) {
    /**
     * Complete login from a WebView that has finished loading a page after
     * authentication.
     *
     * @return true if the session entered [AppSessionState.Authenticated].
     */
    suspend operator fun invoke(webView: WebView, currentUrl: String): Boolean {
        CookieManager.getInstance().flush()

        return awaitAuthenticatedSession(
            hasAuthenticatedCookies = { cookieJar.hasAuthenticatedCookies() },
            completeLogin = {
                val liveUrl = webView.url.orEmpty().ifBlank { currentUrl }
                if (!isDiscourseSameOrigin(liveUrl, baseUrl)) {
                    false
                } else {
                    val currentUser = CurrentUserExtractor.readCurrentUser(webView)
                    // Navigation can change while evaluateJavascript is pending.
                    if (!isDiscourseSameOrigin(webView.url.orEmpty().ifBlank { currentUrl }, baseUrl)) {
                        false
                    } else if (currentUser != null) {
                        completeWithKnownUsername(currentUser.username, currentUser.displayName,
                            currentUser.avatarTemplate)
                    } else {
                        sessionCoordinator.completeLoginFromWebView()
                        sessionCoordinator.sessionState.value is AppSessionState.Authenticated
                    }
                }
            },
        )
    }

    private suspend fun completeWithKnownUsername(
        username: String,
        displayName: String?,
        avatarTemplate: String?,
    ): Boolean {
        sessionCoordinator.completeLoginFromWebViewStaged(
            username = username,
            displayName = displayName,
            avatarTemplate = avatarTemplate,
        )
        return sessionCoordinator.sessionState.value is AppSessionState.Authenticated
    }
}

private const val LOGIN_COOKIE_READY_MAX_ATTEMPTS = 20
private const val LOGIN_COOKIE_READY_DELAY_MILLIS = 500L

/**
 * Polls until the WebView exposes authenticated cookies and [completeLogin]
 * reports success. WebView cookie writes may lag behind onPageFinished by
 * several frames, so completion retries briefly instead of failing fast.
 */
internal suspend fun awaitAuthenticatedSession(
    hasAuthenticatedCookies: () -> Boolean,
    completeLogin: suspend () -> Boolean,
    maxAttempts: Int = LOGIN_COOKIE_READY_MAX_ATTEMPTS,
    delayMillis: Long = LOGIN_COOKIE_READY_DELAY_MILLIS,
): Boolean {
    repeat(maxAttempts) { attempt ->
        try {
            if (hasAuthenticatedCookies() && completeLogin()) return true
        } catch (error: io.github.baba983.nativedo.core.network.SessionRecoveryRequiredException) {
            // An ambiguous browser cookie read may settle on a later callback.
        }
        if (attempt != maxAttempts - 1) {
            delay(delayMillis)
        }
    }
    return false
}
