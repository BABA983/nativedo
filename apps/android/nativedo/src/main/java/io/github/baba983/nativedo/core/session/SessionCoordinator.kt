package io.github.baba983.nativedo.core.session

import io.github.baba983.nativedo.core.network.ParsedBootstrap
import io.github.baba983.nativedo.core.network.SessionRecoveryRequiredException
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.core.storage.SessionStore
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface SessionBootstrapClient {
    suspend fun fetchBootstrap(): ParsedBootstrap

    suspend fun fetchCurrentUser(): SessionUserSummary? =
        fetchCurrentUsername()?.let { username ->
            SessionUserSummary(username = username)
        }

    suspend fun fetchCurrentUsername(): String? = null
}

class SessionCoordinator(
    private val cookieStore: CookieStore,
    private val sessionStore: SessionStore,
    private val apiClient: SessionBootstrapClient,
    private val cookieSyncBridge: CookieSyncBridge? = null,
    private val now: () -> Long = System::currentTimeMillis,
    applicationScope: CoroutineScope? = null,
    private val persistenceDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) {
    private val coordinatorScope = applicationScope
        ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _sessionState = MutableStateFlow<AppSessionState>(AppSessionState.Unknown)
    val sessionState: StateFlow<AppSessionState> = _sessionState.asStateFlow()
    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()
    private val operationLock = Any()
    private val cookieOperationMutex = Mutex()
    private var operationGeneration = 0
    private var logoutJob: Job? = null

    suspend fun start() {
        var recoveredFromWebView = false
        val generation = cookieOperationMutex.withLock {
            val next = beginOperation()
            // No cached identity after explicit logout: never resurrect browser cookies.
            if (!cookieStore.hasAuthenticatedCookies() && sessionStore.snapshot() != null) {
                try {
                    cookieSyncBridge?.syncFromWebViewIfCurrent(cookieStore.revision())
                    recoveredFromWebView = cookieStore.hasAuthenticatedCookies()
                    SessionDiagnostics.record("startup_browser_recovery", cookieStore.revision(),
                        accepted = recoveredFromWebView)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    SessionDiagnostics.record("startup_browser_unavailable", cookieStore.revision(), accepted = false)
                }
            }
            next
        }
        restore(
            generation = generation,
            // Browser cookies can belong to a different account. Verify before using cached identity.
            allowCachedFallback = !recoveredFromWebView,
        )
    }

    suspend fun completeLoginFromWebView(cookieUrl: String? = null) {
        val generation = cookieOperationMutex.withLock {
            val nextGeneration = beginOperation()
            if (cookieUrl == null) {
                cookieSyncBridge?.syncFromWebView()
            } else {
                cookieSyncBridge?.syncFromWebView(cookieUrl)
            }
            nextGeneration
        }
        if (!cookieStore.hasAuthenticatedCookies()) {
            // The browser finished a login the native jar could not read: Chromium flushes its
            // cookie store asynchronously, and a conflicting `_t` pair is reported, not chosen.
            // A native verify here would answer "not logged in" and clear the session the browser
            // just created, so keep both jars and let the next attempt read them.
            SessionDiagnostics.record("webview_login_unreadable", cookieStore.revision(), accepted = false)
            keepSessionForRecovery(
                cachedSnapshot = sessionStore.snapshot(),
                allowCachedFallback = false,
                reason = SessionExpiredReason.RecoveryFailed,
            )
            return
        }
        restore(
            generation = generation,
            loginCompletion = true,
            allowCachedFallback = false,
        )
    }

    /**
     * Staged login completion — trusts the username just read from the
     * same-origin WebView DOM and syncs only the auth cookies
     * ([CookieSyncBridge.AUTH_COOKIE_NAMES]); the non-auth and runtime cookie
     * stages are orchestrated by [CompleteLoginUseCase].
     *
     * This is the preferred login-completion path because Cloudflare may
     * reject native OkHttp requests even when the WebView session is valid:
     * `cf_clearance` is bound to the WebView's TLS fingerprint and does not
     * always survive when replayed from OkHttp. The identity is stored with
     * [SessionFreshness.WebView] so normal startup verifies it natively later.
     */
    suspend fun completeLoginFromWebViewStaged(
        username: String,
        displayName: String? = null,
        avatarTemplate: String? = null,
        cookieUrl: String? = null,
    ) {
        val generation = cookieOperationMutex.withLock {
            val nextGeneration = beginOperation()
            if (cookieUrl == null) {
                cookieSyncBridge?.syncFromWebViewByName(
                    names = CookieSyncBridge.AUTH_COOKIE_NAMES,
                )
            } else {
                cookieSyncBridge?.syncFromWebViewByName(
                    url = cookieUrl,
                    names = CookieSyncBridge.AUTH_COOKIE_NAMES,
                )
            }
            nextGeneration
        }
        if (generation != currentOperationGeneration()) return
        val normalizedUsername = username.trim().takeIf(String::isNotBlank)
        if (normalizedUsername == null) {
            _sessionState.value = AppSessionState.Anonymous
            return
        }
        if (!cookieStore.hasAuthenticatedCookies()) {
            applyMissingAuthenticatedCookies()
            return
        }
        val snapshot = mergeCachedIdentity(
            username = normalizedUsername,
            displayName = displayName,
            avatarTemplate = avatarTemplate,
        )
        cookieOperationMutex.withLock {
            if (generation != currentOperationGeneration()) return
            sessionStore.save(snapshot)
            withContext(persistenceDispatcher) {
                cookieStore.flush()
                sessionStore.flush()
            }
            _sessionState.value = AppSessionState.Authenticated(
                snapshot = snapshot,
                freshness = SessionFreshness.WebView,
            )
            SessionDiagnostics.record("login_committed", cookieStore.revision())
        }
        _events.emit(SessionEvent.LoginCompleted(snapshot))
    }

    /**
     * Auth cookies vanished but a persisted identity may exist — keep it long
     * enough to render a useful re-login state; an explicit logout clears the
     * snapshot before reaching here.
     */
    private fun applyMissingAuthenticatedCookies() {
        val cachedSnapshot = sessionStore.snapshot()
        SessionDiagnostics.record("auth_cookie_missing", cookieStore.revision(), accepted = false)
        _sessionState.value = if (cachedSnapshot == null) {
            sessionStore.clear()
            AppSessionState.Anonymous
        } else {
            AppSessionState.Expired(
                reason = SessionExpiredReason.CookieMissing,
                snapshot = cachedSnapshot,
            )
        }
    }

    /** Fresh identity, enriched with a matching cached display name/avatar. */
    private fun mergeCachedIdentity(
        username: String,
        displayName: String?,
        avatarTemplate: String?,
    ): SessionSnapshot {
        val matchingCachedSnapshot = sessionStore.snapshot()?.takeIf { it.username == username }
        return SessionSnapshot(
            username = username,
            displayName = displayName?.takeIf(String::isNotBlank)
                ?: matchingCachedSnapshot?.displayName
                ?: username,
            avatarTemplate = avatarTemplate?.takeIf(String::isNotBlank)
                ?: matchingCachedSnapshot?.avatarTemplate.orEmpty(),
            lastValidatedAt = now(),
        )
    }

    suspend fun markExpired(reason: SessionExpiredReason) {
        markExpired(reason = reason, expectedGeneration = null)
    }

    suspend fun markExpiredIfCurrent(
        reason: SessionExpiredReason,
        expectedGeneration: Int,
    ) {
        markExpired(reason = reason, expectedGeneration = expectedGeneration)
    }

    fun currentRequestContext(): SessionRequestContext = synchronized(operationLock) {
        val auth = when (_sessionState.value) {
            is AppSessionState.Authenticated -> SessionRequestAuth.Authenticated
            is AppSessionState.Restoring -> SessionRequestAuth.Restoring
            AppSessionState.Anonymous,
            AppSessionState.Unknown,
            is AppSessionState.Expired,
            -> SessionRequestAuth.Anonymous
        }
        SessionRequestContext(generation = operationGeneration, auth = auth)
    }

    suspend fun requestLogin() {
        _events.emit(SessionEvent.LoginRequired)
    }

    private suspend fun markExpired(
        reason: SessionExpiredReason,
        expectedGeneration: Int?,
    ) {
        val transition = suspend {
            val shouldEmit = synchronized(operationLock) {
                if (expectedGeneration != null && expectedGeneration != operationGeneration) {
                    return@synchronized false
                }
                val currentState = _sessionState.value
                if (currentState is AppSessionState.Expired && currentState.reason == reason) {
                    return@synchronized false
                }
                // Requests capture their auth context before starting. Only
                // an authenticated/restoring request may invalidate a session;
                // anonymous, unknown, and already-expired requests are local
                // login-required failures.
                if (reason == SessionExpiredReason.ServerLoggedOut &&
                    expectedGeneration != null &&
                    currentState !is AppSessionState.Authenticated &&
                    currentState !is AppSessionState.Restoring
                ) {
                    return@synchronized false
                }
                val snapshot = currentSnapshot()
                operationGeneration += 1
                _sessionState.value = AppSessionState.Expired(reason = reason, snapshot = snapshot)
                SessionDiagnostics.record("expired_${reason.name}", cookieStore.revision(), accepted = false)
                true
            }
            if (shouldEmit && reason == SessionExpiredReason.ServerLoggedOut) {
                withContext(kotlinx.coroutines.NonCancellable) {
                    sessionStore.clear()
                    cookieStore.clear()
                    withContext(persistenceDispatcher) {
                        sessionStore.flush()
                        cookieStore.flush()
                    }
                    clearCookiesAsync()
                }
            }
            shouldEmit
        }
        val shouldEmit = if (reason == SessionExpiredReason.ServerLoggedOut) {
            cookieOperationMutex.withLock { transition() }
        } else {
            transition()
        }
        if (shouldEmit) {
            _events.emit(SessionEvent.SessionExpired(reason))
        }
    }

    fun onLogout(): Job {
        synchronized(operationLock) {
            logoutJob?.takeIf(Job::isActive)?.let { return it }
            val job = coordinatorScope.launch {
                withContext(kotlinx.coroutines.NonCancellable) {
                    cookieOperationMutex.withLock {
                        synchronized(operationLock) {
                            operationGeneration += 1
                            _sessionState.value = AppSessionState.Anonymous
                        }
                        sessionStore.clear()
                        cookieStore.clear()
                        withContext(persistenceDispatcher) {
                            sessionStore.flush()
                            cookieStore.flush()
                        }
                        clearCookiesAsync()
                    }
                }
                _events.emit(SessionEvent.LogoutCompleted)
            }
            logoutJob = job
            job.invokeOnCompletion {
                synchronized(operationLock) {
                    if (logoutJob === job) logoutJob = null
                }
            }
            return job
        }
    }

    private suspend fun restore(
        generation: Int,
        loginCompletion: Boolean = false,
        allowCachedFallback: Boolean,
        adoptedBrowserSession: Boolean = false,
    ) {
        if (generation != currentOperationGeneration()) return
        if (!cookieStore.hasAuthenticatedCookies()) {
            applyMissingAuthenticatedCookies()
            return
        }
        val cachedSnapshot = sessionStore.snapshot()
        _sessionState.value = AppSessionState.Restoring(snapshot = cachedSnapshot)
        try {
            // A temporary failure of /session/current.json should not prevent
            // the documented homepage bootstrap fallback. An explicit logout
            // signal is the one exception: it must terminate recovery now.
            var currentUserFailure: Throwable? = null
            val currentUser = try {
                apiClient.fetchCurrentUser()
            } catch (error: SessionLoggedOutException) {
                throw error
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                currentUserFailure = error
                null
            }
            val bootstrap = if (currentUser == null) apiClient.fetchBootstrap() else null
            val username = currentUser?.username ?: bootstrap?.currentUsername
            if (username.isNullOrBlank()) {
                // Do not turn a real network/decoder failure into a generic
                // Cloudflare state merely because the bootstrap fallback had
                // no identity either. The UI can then offer the right retry
                // action while keeping the cookies intact in both cases.
                throw currentUserFailure ?: SessionRecoveryRequiredException()
            }
            val matchingCachedSnapshot = cachedSnapshot?.takeIf { it.username == username }
            val snapshot = SessionSnapshot(
                username = username,
                displayName = currentUser?.displayName?.takeIf { it.isNotBlank() }
                    ?: matchingCachedSnapshot?.displayName
                    ?: username,
                avatarTemplate = currentUser?.avatarTemplate?.takeIf { it.isNotBlank() }
                    ?: matchingCachedSnapshot?.avatarTemplate.orEmpty(),
                lastValidatedAt = now(),
            )
            if (generation != currentOperationGeneration()) return
            cookieOperationMutex.withLock {
                if (generation != currentOperationGeneration()) return
                sessionStore.save(snapshot)
                withContext(persistenceDispatcher) {
                    cookieStore.flush()
                    sessionStore.flush()
                }
            }
            if (generation != currentOperationGeneration()) return
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
        } catch (error: CancellationException) {
            throw error
        } catch (error: SessionLoggedOutException) {
            // A rejected `_t` is not proof that the account is logged out: Discourse rotates `_t`
            // every ~10 minutes, and the browser jar can hold the rotation the native jar missed
            // (an in-app browser visit, a challenge page, a login handoff). Adopt what the browser
            // has and ask once more before anything is cleared.
            if (!adoptedBrowserSession && adoptBrowserSessionIfCurrent(generation)) {
                SessionDiagnostics.record("webview_session_adopted", cookieStore.revision())
                restore(
                    generation = generation,
                    loginCompletion = loginCompletion,
                    allowCachedFallback = allowCachedFallback,
                    adoptedBrowserSession = true,
                )
                return
            }
            markExpiredIfCurrent(
                reason = SessionExpiredReason.ServerLoggedOut,
                expectedGeneration = generation,
            )
        } catch (error: SessionRecoveryRequiredException) {
            if (generation != currentOperationGeneration()) return
            keepSessionForRecovery(
                cachedSnapshot = cachedSnapshot,
                allowCachedFallback = allowCachedFallback,
                reason = SessionExpiredReason.ChallengeRequired,
            )
        } catch (error: IOException) {
            if (generation != currentOperationGeneration()) return
            keepSessionForRecovery(
                cachedSnapshot = cachedSnapshot,
                allowCachedFallback = allowCachedFallback,
                reason = SessionExpiredReason.RecoveryFailed,
            )
        } catch (error: Throwable) {
            if (generation != currentOperationGeneration()) return
            keepSessionForRecovery(
                cachedSnapshot = cachedSnapshot,
                allowCachedFallback = allowCachedFallback,
                reason = SessionExpiredReason.RecoveryFailed,
            )
        }
    }

    private fun keepSessionForRecovery(
        cachedSnapshot: SessionSnapshot?,
        allowCachedFallback: Boolean,
        reason: SessionExpiredReason,
    ) {
        if (allowCachedFallback && cachedSnapshot != null) {
            _sessionState.value = AppSessionState.Authenticated(
                snapshot = cachedSnapshot,
                freshness = SessionFreshness.Cached,
            )
            return
        }
        // Keep both stores intact. The WebView can solve a challenge or a
        // transient outage on the next attempt, and the native jar must still
        // contain the cookies that made that attempt possible.
        _sessionState.value = AppSessionState.Expired(
            reason = reason,
            snapshot = cachedSnapshot,
        )
    }

    private suspend fun clearCookiesAsync() {
        cookieSyncBridge?.clearAsync() ?: cookieStore.clear()
    }

    /**
     * Adopt the browser's cookie values for the operation this caller started from. This is the
     * reconcile half of "the browser can hold a `_t` rotation the native jar missed"; the caller
     * re-verifies afterwards, so a browser jar that holds the same rejected token changes nothing.
     *
     * False when there is no bridge, the browser jar cannot be read (a `CookieManager` that is not
     * up yet, or a conflicting pair the import refuses to choose between), or a login/logout moved
     * the generation while this was running - none of which is a deletion signal.
     */
    internal suspend fun adoptBrowserSessionIfCurrent(generation: Int): Boolean =
        cookieOperationMutex.withLock {
            val bridge = cookieSyncBridge ?: return@withLock false
            if (generation != currentOperationGeneration()) return@withLock false
            try {
                bridge.syncFromWebViewIfCurrent(cookieStore.revision())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                SessionDiagnostics.record("webview_session_unavailable", cookieStore.revision(), accepted = false)
                false
            }
        }

    internal fun currentOperationGeneration(): Int = synchronized(operationLock) {
        operationGeneration
    }

    private fun beginOperation(): Int = synchronized(operationLock) {
        cookieStore.advanceRevision()
        operationGeneration += 1
        operationGeneration
    }

    private fun currentSnapshot(): SessionSnapshot? =
        when (val state = _sessionState.value) {
            is AppSessionState.Authenticated -> state.snapshot
            is AppSessionState.Restoring -> state.snapshot
            is AppSessionState.Expired -> state.snapshot
            AppSessionState.Anonymous,
            AppSessionState.Unknown,
            -> null
        } ?: sessionStore.snapshot()

}
