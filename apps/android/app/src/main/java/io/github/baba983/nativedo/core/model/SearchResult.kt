package io.github.baba983.nativedo.core.model

data class SearchUser(
    val username: String,
    val displayName: String,
    val avatarTemplate: String = "",
)

data class SearchResult(
    val topics: List<TopicSummary>,
    val users: List<SearchUser>,
)
