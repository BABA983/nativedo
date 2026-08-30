package io.github.baba983.nativedo.ui.auth

import android.content.Intent
import android.net.Uri
import io.github.baba983.nativedo.core.web.isDiscourseSameOrigin
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

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
    // A session-restore job is running — let it finish and consume the back
    // press so the fragment stays until login completes or fails.
    isRestoringSession -> LoginWebBackAction.CONSUME_RESTORE
    // Authenticated cookies exist — complete the login session regardless of
    // WebView history. This takes priority over GO_WEB_HISTORY so that pressing
    // back after a successful OAuth round-trip (which leaves GitHub/Apple
    // intermediate pages in the history stack) finishes login instead of
    // rewinding to the OAuth provider.
    hasAuthenticatedCookies -> LoginWebBackAction.COMPLETE_SESSION
    canGoBack -> LoginWebBackAction.GO_WEB_HISTORY
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
    // Only attempt restore on same-origin pages — OAuth callbacks may briefly
    // sit on non-same-origin URLs (CF challenge, GitHub/Apple redirect) before
    // redirecting home; completion re-evaluates once the WebView settles.
    if (!isDiscourseSameOrigin(currentUrl = currentUrl, baseUrl = baseUrl)) {
        return false
    }
    // Exclude the login page itself and CF challenge intermediates.
    val isLoginPage = current.encodedPath == "/login" ||
        current.encodedPath.startsWith("/login/")
    if (isLoginPage) {
        return false
    }
    return true
}

