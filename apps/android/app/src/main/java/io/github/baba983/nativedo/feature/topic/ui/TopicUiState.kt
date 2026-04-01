package io.github.baba983.nativedo.feature.topic.ui

import io.github.baba983.nativedo.core.model.Post

data class TopicUiState(
    val title: String = "",
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
) {
    val canRetry: Boolean
        get() = errorMessage != null && posts.isEmpty()
}
