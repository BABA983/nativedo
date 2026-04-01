package io.github.baba983.nativedo.core.session

import io.github.baba983.nativedo.core.network.CsrfTokenProvider
import io.github.baba983.nativedo.core.network.ParsedBootstrap
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.core.storage.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface SessionBootstrapClient {
    suspend fun fetchBootstrap(): ParsedBootstrap
}

class SessionCoordinator(
    private val cookieStore: CookieStore,
    private val sessionStore: SessionStore,
    private val apiClient: SessionBootstrapClient,
    private val csrfTokenProvider: CsrfTokenProvider,
) {
    private val _sessionState = MutableStateFlow<AppSessionState>(AppSessionState.Anonymous)
    val sessionState: StateFlow<AppSessionState> = _sessionState.asStateFlow()

    suspend fun restoreSession() {
        if (!cookieStore.hasSessionCookies()) {
            _sessionState.value = AppSessionState.Anonymous
            return
        }
        _sessionState.value = AppSessionState.Authenticating

        runCatching {
            val bootstrap = apiClient.fetchBootstrap()
            val username = requireNotNull(bootstrap.currentUsername)
            val csrf = csrfTokenProvider.get(forceRefresh = true)
            sessionStore.save(username)
            AppSessionState.Authenticated(username, csrf)
        }.onSuccess {
            _sessionState.value = it
        }.onFailure {
            cookieStore.clear()
            sessionStore.clear()
            _sessionState.value = AppSessionState.Anonymous
        }
    }

    suspend fun onLogout() {
        cookieStore.clear()
        sessionStore.clear()
        _sessionState.value = AppSessionState.Anonymous
    }
}
