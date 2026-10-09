package io.github.baba983.nativedo.core.session

import io.github.baba983.nativedo.core.network.ParsedBootstrap
import io.github.baba983.nativedo.core.network.SessionRecoveryRequiredException
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

/**
 * Owns the app's session state over the shared cookie jar.
 *
 * <p>There is no separate native cookie copy any more: the WebView jar, the in-app browser's jar
 * and the OkHttp client's jar are one [SharedCookieJar]. This coordinator therefore no longer
 * imports cookies from anywhere - it only asks whether the shared jar carries `_t` and verifies
 * the identity behind it. A `_t` the owner created in the in-app browser while the app was closed
 * is a valid session, so a cold start with an empty [SessionStore] verifies and adopts it; that is
 * the normal path, not a recovery special case.</p>
 */
class SessionCoordinator(
    private val sessionStore: SessionStore,
    private val cookieJar: SharedCookieJar,
    private val apiClient: SessionBootstrapClient,
    private val now: () -> Long = System::currentTimeMillis,
    applicationScope: CoroutineScope? = null,
    private val persistenceDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    /**
     * Invoked synchronously whenever the coordinator itself drops the session (an explicit logout or
     * a server-proven logout), so derived per-reader stores - the offline read cache - are cleared
     * with it instead of being cleared behind the coordinator's back.
     */
    private val onSessionCleared: () -> Unit = {},
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
        // The shared jar is the truth: whatever `_t` it holds (possibly written by the WebView
        // while the app was not running) is verified natively before the app calls itself signed
        // in. No cached identity is required for this to work.
        restore(
            generation = beginOperation(),
            allowCachedFallback = true,
        )
    }

    suspend fun completeLoginFromWebView() {
        val generation = beginOperation()
        if (!cookieJar.hasAuthenticatedCookies()) {
            // The browser finished a login the shared jar could not read yet: Chromium flushes
            // its cookie store asynchronously. A native verify here would answer "not logged in"
            // and clear the session the browser just created, so keep the identity for recovery
            // and let the next attempt read the jar.
            cookieOperationMutex.withLock {
                if (generation == currentOperationGeneration()) {
                    SessionDiagnostics.record(
                        "webview_login_unreadable",
                        cookieJar.revision(),
                        accepted = false,
                    )
                    keepSessionForRecovery(
                        cachedSnapshot = sessionStore.snapshot(),
                        allowCachedFallback = false,
                        reason = SessionExpiredReason.RecoveryFailed,
                    )
                }
            }
            return
        }
        restore(
            generation = generation,
            loginCompletion = true,
            allowCachedFallback = false,
        )
    }

    /**
     * Staged login completion - trusts the username just read from the same-origin WebView DOM and
     * stores the identity with [SessionFreshness.WebView] so normal startup verifies it natively
     * later.
     *
     * This is the preferred login-completion path because Cloudflare may reject native OkHttp
     * requests even when the WebView session is valid: `cf_clearance` is bound to the WebView's
     * TLS fingerprint and does not always survive when replayed from OkHttp. The cookies need no
     * import at all: the WebView wrote them into the shared jar, where the native client reads
     * them.
     */
    suspend fun completeLoginFromWebViewStaged(
        username: String,
        displayName: String? = null,
        avatarTemplate: String? = null,
    ) {
        val generation = beginOperation()
        if (generation != currentOperationGeneration()) return
        val normalizedUsername = username.trim().takeIf(String::isNotBlank)
        if (normalizedUsername == null) {
            _sessionState.value = AppSessionState.Anonymous
            return
        }
        if (!cookieJar.hasAuthenticatedCookies()) {
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
                sessionStore.flush()
            }
            _sessionState.value = AppSessionState.Authenticated(
                snapshot = snapshot,
                freshness = SessionFreshness.WebView,
            )
            SessionDiagnostics.record("login_committed", cookieJar.revision())
        }
        _events.emit(SessionEvent.LoginCompleted(snapshot))
    }

    /**
     * Auth cookies vanished but a persisted identity may exist - keep it long
     * enough to render a useful re-login state; an explicit logout clears the
     * snapshot before reaching here.
     */
    private fun applyMissingAuthenticatedCookies() {
        val cachedSnapshot = sessionStore.snapshot()
        SessionDiagnostics.record("auth_cookie_missing", cookieJar.revision(), accepted = false)
        _sessionState.value = if (cachedSnapshot == null) {
            sessionStore.clear()
            // No cookies and no stored reader: the same drop the coordinator reports as a logout.
            onSessionCleared()
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
                SessionDiagnostics.record(
                    "expired_${reason.name}",
                    cookieJar.revision(),
                    accepted = false,
                )
                true
            }
            if (shouldEmit && reason == SessionExpiredReason.ServerLoggedOut) {
                // The server has told this client the session is gone; the shared jar holds the
                // dead `_t`, and the next participant would send it again, so the site's own
                // cookies are deleted here. Other hosts the in-app browser visited keep their
                // own cookies.
                withContext(kotlinx.coroutines.NonCancellable) {
                    sessionStore.clear()
                    withContext(persistenceDispatcher) { sessionStore.flush() }
                    cookieJar.clearSessionCookies()
                }
                // The reader is gone: the reads cached for them go with the session.
                onSessionCleared()
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
                        withContext(persistenceDispatcher) {
                            sessionStore.flush()
                        }
                        cookieJar.clearSessionCookies()
                        // The reader is gone: the reads cached for them go with the session.
                        onSessionCleared()
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
    ) {
        if (generation != currentOperationGeneration()) return
        if (!cookieJar.hasAuthenticatedCookies()) {
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
            // The shared jar's own token was rejected with explicit logout evidence: there is no
            // second jar holding a newer rotation, so this is a real logout.
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
        // Keep the shared jar and the stored identity intact. The WebView can solve a challenge
        // or a transient outage on the next attempt, and the jar must still contain the cookies
        // that made that attempt possible.
        _sessionState.value = AppSessionState.Expired(
            reason = reason,
            snapshot = cachedSnapshot,
        )
    }

    internal fun currentOperationGeneration(): Int = synchronized(operationLock) {
        operationGeneration
    }

    private fun beginOperation(): Int = synchronized(operationLock) {
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
