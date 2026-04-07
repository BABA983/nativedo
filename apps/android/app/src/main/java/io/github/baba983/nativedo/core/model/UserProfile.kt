package io.github.baba983.nativedo.core.model

data class UserProfile(
    val username: String,
    val displayName: String,
    val bio: String,
    val avatarUrl: String = "",
    val animatedAvatarUrl: String = "",
    val flairName: String = "",
    val flairUrl: String = "",
    val flairBgColor: String = "",
    val flairColor: String = "",
)
