package io.github.baba983.nativedo.core.session

enum class SessionRequestAuth {
    Anonymous,
    Authenticated,
    Restoring,
}

data class SessionRequestContext(
    val generation: Int,
    val auth: SessionRequestAuth,
) {
    val canUseAuthenticatedSession: Boolean
        get() = auth == SessionRequestAuth.Authenticated || auth == SessionRequestAuth.Restoring
}
