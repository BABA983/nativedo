package io.github.baba983.nativedo.core.session

sealed interface AppSessionState {
    data object Unknown : AppSessionState

    data class Restoring(
        val snapshot: SessionSnapshot?,
    ) : AppSessionState

    data class Authenticated(
        val snapshot: SessionSnapshot,
        val freshness: SessionFreshness,
    ) : AppSessionState {
        val username: String
            get() = snapshot.username

        val avatarTemplate: String
            get() = snapshot.avatarTemplate
    }

    data object Anonymous : AppSessionState

    data class Expired(
        val reason: SessionExpiredReason,
        val snapshot: SessionSnapshot? = null,
    ) : AppSessionState
}

enum class SessionFreshness {
    Cached,
    Verified,
}

enum class SessionExpiredReason {
    ServerLoggedOut,
    CookieMissing,
    ChallengeRequired,
    RecoveryFailed,
}
