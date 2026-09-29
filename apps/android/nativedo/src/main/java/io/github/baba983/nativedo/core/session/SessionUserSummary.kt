package io.github.baba983.nativedo.core.session

data class SessionUserSummary(
    val username: String,
    val displayName: String = username,
    val avatarTemplate: String = "",
)
