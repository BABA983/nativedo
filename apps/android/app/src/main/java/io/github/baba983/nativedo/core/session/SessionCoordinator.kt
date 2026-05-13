package io.github.baba983.nativedo.core.session

import io.github.baba983.nativedo.core.network.ParsedBootstrap
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.core.storage.SessionStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

interface SessionBootstrapClient {
    suspend fun fetchBootstrap(): ParsedBootstrap

    suspend fun fetchCurrentUsername(): String? = null
}

class SessionCoordinator(
    private val cookieStore: CookieStore,
    private val sessionStore: SessionStore,
    private val apiClient: SessionBootstrapClient,
    private val cookieSyncBridge: CookieSyncBridge? = null,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _sessionState = MutableStateFlow<AppSessionState>(AppSessionState.Unknown)
    val sessionState: StateFlow<AppSessionState> = _sessionState.asStateFlow()

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    suspend fun start() {
        restore(
            allowCachedFallback = true,
            clearInvalidWebViewCookies = true,
        )
    }

    suspend fun completeLoginFromWebView(cookieUrl: String? = null) {
        if (cookieUrl == null) {
            cookieSyncBridge?.syncFromWebView()
        } else {
            cookieSyncBridge?.syncFromWebView(cookieUrl)
        }
        restore(
            loginCompletion = true,
            allowCachedFallback = false,
            clearInvalidWebViewCookies = false,
        )
    }

    /**
     * Skip the bootstrap/current-session API hop and trust the username we
     * just read directly from the WebView DOM (e.g. `meta[name=current-username]`).
     *
     * This is the preferred login-completion path because Cloudflare may
     * reject native OkHttp requests even when the WebView session is valid:
     * `cf_clearance` is bound to the WebView's TLS fingerprint and does not
     * always survive when replayed from OkHttp.
     */
    suspend fun completeLoginFromWebViewWithKnownUsername(
        username: String,
        cookieUrl: String? = null,
    ) {
        if (cookieUrl == null) {
            cookieSyncBridge?.syncFromWebView()
        } else {
            cookieSyncBridge?.syncFromWebView(cookieUrl)
        }
        if (!cookieStore.hasAuthenticatedCookies()) {
            sessionStore.clear()
            _sessionState.value = AppSessionState.Anonymous
            return
        }
        val cachedSnapshot = sessionStore.snapshot()
        val matchingCachedSnapshot = cachedSnapshot?.takeIf { it.username == username }
        val snapshot = SessionSnapshot(
            username = username,
            displayName = matchingCachedSnapshot?.displayName ?: username,
            avatarTemplate = matchingCachedSnapshot?.avatarTemplate.orEmpty(),
            lastValidatedAt = now(),
        )
        sessionStore.save(snapshot)
        _sessionState.value = AppSessionState.Authenticated(
            snapshot = snapshot,
            freshness = SessionFreshness.Verified,
        )
        _events.emit(SessionEvent.LoginCompleted(snapshot))
    }

    suspend fun markExpired(reason: SessionExpiredReason) {
        val snapshot = sessionStore.snapshot()
        clearCookies()
        sessionStore.clear()
        _sessionState.value = AppSessionState.Expired(reason = reason, snapshot = snapshot)
        _events.emit(SessionEvent.SessionExpired(reason))
    }

    suspend fun onLogout() {
        clearCookies()
        sessionStore.clear()
        _sessionState.value = AppSessionState.Anonymous
        _events.emit(SessionEvent.LogoutCompleted)
    }

    private suspend fun restore(
        loginCompletion: Boolean = false,
        allowCachedFallback: Boolean,
        clearInvalidWebViewCookies: Boolean,
    ) {
        if (!cookieStore.hasAuthenticatedCookies()) {
            sessionStore.clear()
            _sessionState.value = AppSessionState.Anonymous
            return
        }
        val cachedSnapshot = sessionStore.snapshot()
        _sessionState.value = AppSessionState.Restoring(snapshot = cachedSnapshot)
        runCatching {
            val currentUsername = apiClient.fetchCurrentUsername()
            val bootstrapUsername = currentUsername ?: apiClient.fetchBootstrap().currentUsername
            if (bootstrapUsername.isNullOrBlank()) {
                throw SessionNotAuthenticatedException
            }
            SessionSnapshot(
                username = bootstrapUsername,
                displayName = cachedSnapshot?.takeIf { it.username == bootstrapUsername }?.displayName ?: bootstrapUsername,
                avatarTemplate = cachedSnapshot?.takeIf { it.username == bootstrapUsername }?.avatarTemplate.orEmpty(),
                lastValidatedAt = now(),
            )
        }.fold(
            onSuccess = { snapshot ->
                sessionStore.save(snapshot)
                _sessionState.value = AppSessionState.Authenticated(
                    snapshot = snapshot,
                    freshness = SessionFreshness.Verified,
                )
                _events.emit(
                    if (loginCompletion) {
                        SessionEvent.LoginCompleted(snapshot)
                    } else {
                        SessionEvent.SessionVerified(snapshot)
                    },
                )
            },
            onFailure = { error ->
                _sessionState.value = cachedSnapshot?.takeIf { allowCachedFallback }?.let { snapshot ->
                        AppSessionState.Authenticated(
                            snapshot = snapshot,
                            freshness = SessionFreshness.Cached,
                        )
                    }?.takeUnless { error.isServerLoggedOut() } ?: run {
                    if (clearInvalidWebViewCookies || error.isServerLoggedOut()) {
                        clearCookies()
                    } else {
                        cookieStore.clear()
                    }
                    sessionStore.clear()
                    AppSessionState.Expired(
                        reason = if (error.isServerLoggedOut()) {
                            SessionExpiredReason.ServerLoggedOut
                        } else {
                            SessionExpiredReason.RecoveryFailed
                        },
                        snapshot = cachedSnapshot,
                    )
                }
            },
        )
    }

    private fun clearCookies() {
        cookieSyncBridge?.clear() ?: cookieStore.clear()
    }

    private data object SessionNotAuthenticatedException : Throwable()

    /**
     * Only treat the server's explicit "logged out" signal (via
     * [SessionLoggedOutException]) as confirmation that cookies are dead.
     *
     * A null username from bootstrap/current-session ([SessionNotAuthenticatedException])
     * may simply mean Cloudflare returned a challenge HTML or the page structure
     * changed — wiping WebView cookies in that case would destroy a legitimate
     * fresh login. See contracts/discourse/auth-flow.md rule 11.
     */
    private fun Throwable.isServerLoggedOut(): Boolean =
        this is SessionLoggedOutException
}
