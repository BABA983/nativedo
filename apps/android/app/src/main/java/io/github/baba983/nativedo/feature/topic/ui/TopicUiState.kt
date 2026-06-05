package io.github.baba983.nativedo.feature.topic.ui

import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.PostReaction
import io.github.baba983.nativedo.core.model.PostReactionUsersGroup

data class TopicUiState(
    val title: String = "",
    val createdAt: String = "",
    val lastPostedAt: String = "",
    val viewCount: Int = 0,
    val tags: List<String> = emptyList(),
    val validReactions: List<String> = emptyList(),
    val emojiCatalog: Map<String, String> = emptyMap(),
    val totalPosts: Int = 0,
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val errorMessage: String? = null,
    val loadMoreErrorMessage: String? = null,
    val reactionSheet: TopicReactionSheetUiState? = null,
    val reactionUsers: TopicReactionUsersUiState = TopicReactionUsersUiState(),
    val jumpEvent: TopicJumpEvent? = null,
    val jumpHighlight: TopicJumpHighlight? = null,
) {
    val canRetry: Boolean
        get() = errorMessage != null && posts.isEmpty()
}

data class TopicReactionSheetUiState(
    val postId: Long,
    val reactions: List<PostReaction>,
    val initialReactionId: String? = null,
)

data class TopicReactionUsersUiState(
    val groups: List<PostReactionUsersGroup> = emptyList(),
    val selectedReactionId: String? = null,
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
)

data class TopicJumpEvent(
    val itemIndex: Int,
    val postId: Long,
    val nonce: Int,
)

data class TopicJumpHighlight(
    val postId: Long,
    val nonce: Int,
)
