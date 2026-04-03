package io.github.baba983.nativedo.core.session

sealed interface AppSessionState {
    data object Anonymous : AppSessionState

    data object Authenticating : AppSessionState

    data class Authenticated(
        val username: String,
        val csrfToken: String,
        val avatarTemplate: String = "",
    ) : AppSessionState

    data object Expired : AppSessionState
}
