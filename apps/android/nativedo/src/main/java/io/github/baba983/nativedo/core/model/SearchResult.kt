package io.github.baba983.nativedo.core.model

data class SearchResult(
    val topics: List<TopicSummary> = emptyList(),
    val posts: List<TopicSummary> = emptyList(),
    val users: List<SearchUserSummary> = emptyList(),
    val hasMore: Boolean = false,
)

data class SearchUserSummary(
    val username: String,
    val name: String = "",
    val avatarTemplate: String = "",
    val animatedAvatarUrl: String = "",
)
