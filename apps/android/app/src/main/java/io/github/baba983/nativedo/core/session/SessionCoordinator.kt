package io.github.baba983.nativedo.core.session

import io.github.baba983.nativedo.core.log.NativeDoLogTag
import io.github.baba983.nativedo.core.log.debugLog
import io.github.baba983.nativedo.core.log.warnLog
import io.github.baba983.nativedo.core.model.UserProfile
import io.github.baba983.nativedo.core.network.ParsedBootstrap
import io.github.baba983.nativedo.core.network.SessionRecoveryRequiredException
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.core.storage.SessionStore
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val cookieSyncBridge: CookieSyncBridge? = null,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _sessionState = MutableStateFlow<AppSessionState>(AppSessionState.Unknown)
    val sessionState: StateFlow<AppSessionState> = _sessionState.asStateFlow()

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    private val _authVersion = MutableStateFlow(0L)
    val authVersion: StateFlow<Long> = _authVersion.asStateFlow()

    private var sessionMutationToken: Long = 0L

    suspend fun start() {
        restore(trigger = RestoreTrigger.Startup)
    }

    suspend fun restoreSession() {
        start()
    }

    suspend fun completeLoginFromWebView() {
        restore(trigger = RestoreTrigger.LoginCompletion)
    }

    fun currentSnapshot(): SessionSnapshot? = when (val state = _sessionState.value) {
        AppSessionState.Anonymous,
        AppSessionState.Unknown -> sessionStore.snapshot()
        is AppSessionState.Authenticated -> state.snapshot
        is AppSessionState.Expired -> state.snapshot
        is AppSessionState.Restoring -> state.snapshot
    }

    suspend fun markExpired(reason: SessionExpiredReason) {
        val snapshot = currentSnapshot()
        clearCookies()
        sessionStore.clear()
        advanceBoundary()
        _sessionState.value = AppSessionState.Expired(reason = reason, snapshot = snapshot)
        _events.emit(SessionEvent.SessionExpired(reason))
    }

    suspend fun onLogout() {
        clearCookies()
        sessionStore.clear()
        advanceBoundary()
        _sessionState.value = AppSessionState.Anonymous
        _events.emit(SessionEvent.LogoutCompleted)
    }

    private suspend fun restore(trigger: RestoreTrigger) {
        val restoreToken = beginRestore()
        val syncedFromWebView = if (trigger.syncFromWebView) {
            cookieSyncBridge?.syncFromWebView()
        } else {
            null
        }
        val hasSessionCookies = cookieStore.hasAuthenticatedCookies()
        debugLog(
            TAG,
            "restore start trigger=${trigger.name} syncedFromWebView=$syncedFromWebView hasSessionCookies=$hasSessionCookies",
        )
        if (!hasSessionCookies) {
            debugLog(TAG, "restore abort: no session cookies")
            sessionStore.clear()
            advanceBoundary()
            _sessionState.value = AppSessionState.Anonymous
            return
        }
        val previousState = _sessionState.value
        val cachedSnapshot = sessionStore.snapshot()
        _sessionState.value = AppSessionState.Restoring(snapshot = cachedSnapshot)

        var cachedBootstrap: ParsedBootstrap? = null
        suspend fun bootstrap(): ParsedBootstrap {
            return cachedBootstrap ?: apiClient.fetchBootstrap().also { bootstrap ->
                cachedBootstrap = bootstrap
                debugLog(
                    TAG,
                    "restore bootstrap currentUsername=${bootstrap.currentUsername}",
                )
            }
        }

        try {
            val currentSessionUsername = runSuspendCatchingPreservingCancellation {
                apiClient.fetchCurrentUsername()
            }.onSuccess { username ->
                debugLog(TAG, "restore currentSession username=$username")
            }.onFailure { error ->
                warnLog(TAG, "restore currentSession lookup failed", error)
            }.getOrNull()
                ?.takeIf { it.isNotBlank() }
            val username = currentSessionUsername ?: requireNotNull(bootstrap().currentUsername)
            val matchingCachedSnapshot = cachedSnapshot?.takeIf { it.username == username }
            val profile = runSuspendCatchingPreservingCancellation {
                apiClient.fetchProfile(username)
            }.onFailure { error ->
                warnLog(TAG, "restore profile lookup failed for $username", error)
            }.getOrNull()
            val snapshot = SessionSnapshot(
                username = username,
                displayName = profile?.displayName
                    ?.takeIf { it.isNotBlank() }
                    ?: matchingCachedSnapshot?.displayName
                        ?.takeIf { it.isNotBlank() }
                    ?: username,
                avatarTemplate = profile?.avatarUrl
                    ?.takeIf { it.isNotBlank() }
                    ?: matchingCachedSnapshot?.avatarTemplate
                        .orEmpty(),
                lastValidatedAt = now(),
            )
            if (!isCurrentRestore(restoreToken)) {
                debugLog(TAG, "restore result ignored before persistence due to newer auth mutation")
                return
            }
            sessionStore.save(snapshot)
            val authenticatedState = AppSessionState.Authenticated(
                snapshot = snapshot,
                freshness = SessionFreshness.Verified,
            )
            debugLog(TAG, "restore authenticated username=${authenticatedState.username}")
            advanceBoundary()
            _sessionState.value = authenticatedState
            _events.emit(trigger.successEvent(snapshot))
        } catch (error: CancellationException) {
            debugLog(TAG, "restore cancelled; keeping previous session state")
            _sessionState.value = previousState
            throw error
        } catch (error: SessionRecoveryRequiredException) {
            warnLog(TAG, "restore needs session recovery", error)
            if (!isCurrentRestore(restoreToken)) {
                debugLog(TAG, "restore recovery-required result ignored due to newer auth mutation")
                return
            }
            if (cachedSnapshot != null) {
                _sessionState.value = AppSessionState.Authenticated(
                    snapshot = cachedSnapshot,
                    freshness = SessionFreshness.Cached,
                )
            } else {
                markExpired(SessionExpiredReason.ChallengeRequired)
            }
        } catch (error: IOException) {
            warnLog(TAG, "restore failed with io error", error)
            if (!isCurrentRestore(restoreToken)) {
                debugLog(TAG, "restore io failure ignored due to newer auth mutation")
                return
            }
            if (cachedSnapshot != null) {
                _sessionState.value = AppSessionState.Authenticated(
                    snapshot = cachedSnapshot,
                    freshness = SessionFreshness.Cached,
                )
            } else {
                advanceBoundary()
                _sessionState.value = AppSessionState.Expired(
                    reason = SessionExpiredReason.RecoveryFailed,
                )
            }
        } catch (error: Throwable) {
            warnLog(TAG, "restore failed with unrecoverable error", error)
            if (!isCurrentRestore(restoreToken)) {
                debugLog(TAG, "restore failure ignored due to newer auth mutation")
                return
            }
            advanceBoundary()
            _sessionState.value = AppSessionState.Expired(
                reason = SessionExpiredReason.RecoveryFailed,
                snapshot = cachedSnapshot,
            )
        }
    }

    private fun clearCookies() {
        cookieSyncBridge?.clear() ?: cookieStore.clear()
    }

    private fun beginRestore(): Long {
        sessionMutationToken += 1
        return sessionMutationToken
    }

    private fun isCurrentRestore(restoreToken: Long): Boolean = sessionMutationToken == restoreToken

    private fun advanceBoundary() {
        sessionMutationToken += 1
        _authVersion.value += 1
    }

    private companion object {
        val TAG: NativeDoLogTag = NativeDoLogTag.Session
    }
}

private enum class RestoreTrigger(
    val syncFromWebView: Boolean,
) {
    Startup(syncFromWebView = false) {
        override fun successEvent(snapshot: SessionSnapshot): SessionEvent =
            SessionEvent.SessionVerified(snapshot)
    },
    LoginCompletion(syncFromWebView = true) {
        override fun successEvent(snapshot: SessionSnapshot): SessionEvent =
            SessionEvent.LoginCompleted(snapshot)
    },
    ;

    abstract fun successEvent(snapshot: SessionSnapshot): SessionEvent
}

private suspend inline fun <T> runSuspendCatchingPreservingCancellation(
    crossinline block: suspend () -> T,
): Result<T> {
    return try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }
}
