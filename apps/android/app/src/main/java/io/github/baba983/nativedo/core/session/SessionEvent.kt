package io.github.baba983.nativedo.core.session

sealed interface SessionEvent {
    data class LoginCompleted(
        val snapshot: SessionSnapshot,
    ) : SessionEvent

    data class SessionVerified(
        val snapshot: SessionSnapshot,
    ) : SessionEvent

    data object LogoutCompleted : SessionEvent

    data class SessionExpired(
        val reason: SessionExpiredReason,
    ) : SessionEvent
}
