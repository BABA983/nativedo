package io.github.baba983.nativedo.core.session

import io.github.baba983.nativedo.core.model.UserProfile
import io.github.baba983.nativedo.core.network.CsrfTokenProvider
import io.github.baba983.nativedo.core.network.ParsedBootstrap
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.core.storage.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface SessionBootstrapClient {
    suspend fun fetchBootstrap(): ParsedBootstrap

    suspend fun fetchCurrentUsername(): String? = null

    suspend fun fetchProfile(username: String): UserProfile? = null
}

class SessionCoordinator(
    private val cookieStore: CookieStore,
    private val sessionStore: SessionStore,
    private val apiClient: SessionBootstrapClient,
    private val csrfTokenProvider: CsrfTokenProvider,
    private val cookieSyncBridge: CookieSyncBridge? = null,
) {
    private val _sessionState = MutableStateFlow<AppSessionState>(AppSessionState.Anonymous)
    val sessionState: StateFlow<AppSessionState> = _sessionState.asStateFlow()

    suspend fun restoreSession() {
        cookieSyncBridge?.syncFromWebView()
        if (!cookieStore.hasSessionCookies()) {
            _sessionState.value = AppSessionState.Anonymous
            return
        }
        _sessionState.value = AppSessionState.Authenticating

        var cachedBootstrap: ParsedBootstrap? = null
        suspend fun bootstrap(): ParsedBootstrap {
            return cachedBootstrap ?: apiClient.fetchBootstrap().also { cachedBootstrap = it }
        }

        runCatching {
            val username = runCatching {
                apiClient.fetchCurrentUsername()
            }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: requireNotNull(bootstrap().currentUsername)
            val avatarTemplate = runCatching {
                apiClient.fetchProfile(username)
            }.getOrNull()
                ?.avatarUrl
                .orEmpty()
            val csrf = runCatching {
                csrfTokenProvider.get(forceRefresh = true)
            }.getOrElse { error ->
                bootstrap().csrfToken
                    .takeIf { it.isNotBlank() }
                    ?: throw error
            }
            sessionStore.save(username)
            AppSessionState.Authenticated(
                username = username,
                csrfToken = csrf,
                avatarTemplate = avatarTemplate,
            )
        }.onSuccess {
            _sessionState.value = it
        }.onFailure {
            sessionStore.clear()
            _sessionState.value = AppSessionState.Expired
        }
    }

    suspend fun onLogout() {
        cookieSyncBridge?.clear() ?: cookieStore.clear()
        sessionStore.clear()
        _sessionState.value = AppSessionState.Anonymous
    }
}
