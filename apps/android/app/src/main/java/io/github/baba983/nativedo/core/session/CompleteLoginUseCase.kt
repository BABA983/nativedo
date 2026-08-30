package io.github.baba983.nativedo.core.session

import android.webkit.CookieManager
import android.webkit.WebView
import io.github.baba983.nativedo.core.web.isDiscourseSameOrigin
import kotlinx.coroutines.delay

/**
 * Orchestrates the login completion flow after the WebView detects a successful
 * Discourse login.
 *
 * Stages:
 * 1. Read DOM identity (CurrentUserExtractor)
 * 2. Sync non-auth cookies (cf_clearance etc.)
 * 3. Advance generation + sync auth cookies + state transition (SessionCoordinator)
 * 4. Sync runtime cookies (_rt)
 *
 * Stage 3 delegates to [SessionCoordinator.completeLoginFromWebViewStaged],
 * which advances the operation generation (cutting off stale requests) and
 * transitions the session to Authenticated.
 */
class CompleteLoginUseCase(
    private val sessionCoordinator: SessionCoordinator,
    private val cookieSyncBridge: CookieSyncBridge,
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

        // 1. Read DOM identity
        val currentUser = CurrentUserExtractor.readCurrentUser(webView)
        val liveUrl = webView.url.orEmpty().ifBlank { currentUrl }
        val sameOrigin = isDiscourseSameOrigin(liveUrl, baseUrl)
        val cookieUrl = liveUrl.takeIf { sameOrigin }

        return if (currentUser != null) {
            completeWithKnownUsername(
                username = currentUser.username,
                displayName = currentUser.displayName,
                avatarTemplate = currentUser.avatarTemplate,
                cookieUrl = cookieUrl,
            )
        } else {
            completeWithBootstrap(cookieUrl = cookieUrl)
        }
    }

    private suspend fun completeWithKnownUsername(
        username: String,
        displayName: String?,
        avatarTemplate: String?,
        cookieUrl: String?,
    ): Boolean {
        // Stage 2: sync non-auth cookies (cf_clearance etc.)
        runCatching {
            cookieSyncBridge.syncFromWebViewExcluding(
                url = cookieUrl ?: baseUrl,
                excludeNames = CookieSyncBridge.AUTH_COOKIE_NAMES,
            )
        }

        // Stage 3: advance generation + sync auth cookies + state transition
        sessionCoordinator.completeLoginFromWebViewStaged(
            username = username,
            displayName = displayName,
            avatarTemplate = avatarTemplate,
            cookieUrl = cookieUrl,
        )

        // Stage 4: sync runtime cookies (_rt)
        runCatching {
            cookieSyncBridge.syncFromWebViewByName(
                url = cookieUrl ?: baseUrl,
                names = CookieSyncBridge.RUNTIME_COOKIE_NAMES,
            )
        }

        return sessionCoordinator.sessionState.value is AppSessionState.Authenticated
    }

    private suspend fun completeWithBootstrap(cookieUrl: String?): Boolean =
        awaitAuthenticatedSession(
            hasAuthenticatedCookies = {
                cookieSyncBridge.hasAuthenticatedCookiesInWebView(baseUrl)
            },
            completeLogin = {
                sessionCoordinator.completeLoginFromWebView(cookieUrl)
                sessionCoordinator.sessionState.value is AppSessionState.Authenticated
            },
        )
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
        if (hasAuthenticatedCookies() && completeLogin()) {
            return true
        }
        if (attempt != maxAttempts - 1) {
            delay(delayMillis)
        }
    }
    return false
}
