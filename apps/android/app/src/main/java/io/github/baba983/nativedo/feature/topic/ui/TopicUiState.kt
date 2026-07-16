package io.github.baba983.nativedo.feature.topic.ui

import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.PostReaction
import io.github.baba983.nativedo.core.model.PostReactionUsersGroup
import io.github.baba983.nativedo.core.model.DiscourseEmojiCategory
import io.github.baba983.nativedo.core.model.MentionCandidate
import io.github.baba983.nativedo.feature.topic.data.TopicReplyDraft

internal const val TOPIC_REACTION_ALL_FILTER_ID = "__nativedo_all_reactions__"

data class TopicUiState(
    val title: String = "",
    val createdAt: String = "",
    val lastPostedAt: String = "",
    val viewCount: Int = 0,
    val tags: List<String> = emptyList(),
    val validReactions: List<String> = emptyList(),
    val emojiCatalog: Map<String, String> = emptyMap(),
    val emojiCategories: List<DiscourseEmojiCategory> = emptyList(),
    val totalPosts: Int = 0,
    val posts: List<Post> = emptyList(),
    val participants: List<MentionCandidate> = emptyList(),
    val streamPostIds: List<Long> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isSubmittingReply: Boolean = false,
    val isJumpingToLatest: Boolean = false,
    val replyDraft: TopicReplyDraft = TopicReplyDraft(),
    val hasMore: Boolean = false,
    val errorMessage: String? = null,
    val loadMoreErrorMessage: String? = null,
    val replySubmitErrorMessage: String? = null,
    val jumpToLatestErrorMessage: String? = null,
    val reactionSheet: TopicReactionSheetUiState? = null,
    val reactionUsers: TopicReactionUsersUiState = TopicReactionUsersUiState(),
    val pollVotersSheet: TopicPollVotersSheetUiState? = null,
    val pollVoters: TopicPollVotersUiState = TopicPollVotersUiState(),
    val postRepliesSheet: TopicPostRepliesSheetUiState? = null,
    val postReplies: TopicPostRepliesUiState = TopicPostRepliesUiState(),
    val pollActionFailure: PollActionFailure? = null,
    val jumpEvent: TopicJumpEvent? = null,
    val jumpHighlight: TopicJumpHighlight? = null,
) {
    val canRetry: Boolean
        get() = errorMessage != null && posts.isEmpty()
}

data class TopicReactionSheetUiState(
    val postId: Long,
    val reactions: List<PostReaction>,
    val reactionUsersCount: Int = 0,
    val initialReactionId: String? = null,
)

data class TopicReactionUsersUiState(
    val groups: List<PostReactionUsersGroup> = emptyList(),
    val selectedReactionId: String? = null,
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val errorMessage: String? = null,
)

data class TopicPollVotersSheetUiState(
    val postId: Long,
    val pollName: String,
    val pollTitle: String? = null,
    val options: List<PollOptionRef> = emptyList(),
)

data class TopicPostRepliesSheetUiState(
    val sourcePost: Post,
    val totalCount: Int,
)

data class TopicPostRepliesUiState(
    val posts: List<Post> = emptyList(),
    val nextAfterPostNumber: Int = 1,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasLoaded: Boolean = false,
    val endReached: Boolean = false,
    val errorMessage: String? = null,
) {
    val canLoadMore: Boolean
        get() = hasLoaded && !isLoading && !isLoadingMore && !endReached
}

data class PollActionFailure(
    val postId: Long,
    val nonce: Int,
)

data class TopicPollVoterOptionPageUiState(
    val nextPage: Int = 1,
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val endReached: Boolean = false,
    val errorMessage: String? = null,
)

data class TopicPollVotersUiState(
    val groups: List<PostReactionUsersGroup> = emptyList(),
    val selectedOptionId: String? = null,
    val optionPages: Map<String, TopicPollVoterOptionPageUiState> = emptyMap(),
) {
    val selectedPage: TopicPollVoterOptionPageUiState?
        get() = selectedOptionId?.let(optionPages::get)

    val isLoading: Boolean
        get() = selectedPage?.let { page -> page.isLoading && !page.hasLoaded } == true

    val isLoadingMore: Boolean
        get() = selectedPage?.let { page -> page.isLoading && page.hasLoaded } == true

    val hasLoaded: Boolean
        get() = selectedPage?.hasLoaded == true

    val errorMessage: String?
        get() = selectedPage?.errorMessage

    val canLoadMore: Boolean
        get() = selectedPage?.let { page -> page.hasLoaded && !page.isLoading && !page.endReached } == true
}

data class TopicJumpEvent(
    val itemIndex: Int,
    val postId: Long,
    val nonce: Int,
)

data class TopicJumpHighlight(
    val postId: Long,
    val nonce: Int,
)
