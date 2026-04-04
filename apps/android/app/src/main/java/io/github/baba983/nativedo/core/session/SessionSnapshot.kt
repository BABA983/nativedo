package io.github.baba983.nativedo.core.session

import kotlinx.serialization.Serializable

@Serializable
data class SessionSnapshot(
    val username: String,
    val displayName: String,
    val avatarTemplate: String,
    val lastValidatedAt: Long,
) {
    val chromeTitle: String
        get() = displayName.ifBlank { username }
}
