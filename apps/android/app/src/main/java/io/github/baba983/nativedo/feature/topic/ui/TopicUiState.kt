package io.github.baba983.nativedo.feature.topic.ui

import io.github.baba983.nativedo.core.model.Post

data class TopicUiState(
    val title: String = "",
    val totalPosts: Int = 0,
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val errorMessage: String? = null,
    val loadMoreErrorMessage: String? = null,
) {
    val canRetry: Boolean
        get() = errorMessage != null && posts.isEmpty()
}
