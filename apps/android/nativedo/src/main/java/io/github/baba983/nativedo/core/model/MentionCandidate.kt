package io.github.baba983.nativedo.core.model

data class MentionCandidate(
    val username: String,
    val displayName: String = "",
    val avatarTemplate: String = "",
    val animatedAvatarUrl: String = "",
    val isGroup: Boolean = false,
    val priorityGroup: Int? = null,
    val flairUrl: String = "",
    val userCount: Int? = null,
    val postCount: Int? = null,
)

data class MentionSearchResult(
    val candidates: List<MentionCandidate> = emptyList(),
)
