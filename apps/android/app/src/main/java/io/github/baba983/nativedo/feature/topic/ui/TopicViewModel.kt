package io.github.baba983.nativedo.feature.topic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.PostReaction
import io.github.baba983.nativedo.core.model.toggledReaction
import io.github.baba983.nativedo.feature.home.ui.readableMessage
import io.github.baba983.nativedo.feature.topic.data.TopicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TOPIC_POST_PAGE_SIZE = 20

class TopicViewModel(
    private val repository: TopicRepository,
    private val topicId: Long,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TopicUiState())
    val uiState: StateFlow<TopicUiState> = _uiState.asStateFlow()

    private var remainingPostIds: List<Long> = emptyList()
    private var jumpNonce: Int = 0

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val hasExistingPosts = _uiState.value.posts.isNotEmpty()
            _uiState.value = _uiState.value.copy(
                isLoading = !hasExistingPosts,
                isRefreshing = hasExistingPosts,
                isLoadingMore = false,
                errorMessage = null,
                loadMoreErrorMessage = null,
            )
            repository.load(topicId).fold(
                onSuccess = { detail ->
                    val loadedIds = detail.posts.map(Post::id).toSet()
                    remainingPostIds = detail.streamPostIds.filterNot(loadedIds::contains)
                    _uiState.value = TopicUiState(
                        title = detail.title,
                        createdAt = detail.createdAt,
                        lastPostedAt = detail.lastPostedAt,
                        viewCount = detail.viewCount,
                        tags = detail.tags,
                        validReactions = detail.validReactions,
                        emojiCatalog = detail.emojiCatalog,
                        totalPosts = detail.totalPosts,
                        posts = detail.posts,
                        isLoading = false,
                        isRefreshing = false,
                        hasMore = remainingPostIds.isNotEmpty(),
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = error.readableMessage(),
                    )
                },
            )
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isRefreshing || state.isLoadingMore || remainingPostIds.isEmpty()) {
            return
        }

        val nextChunk = remainingPostIds.take(TOPIC_POST_PAGE_SIZE)
        viewModelScope.launch {
            _uiState.value = state.copy(
                isLoadingMore = true,
                loadMoreErrorMessage = null,
            )
            repository.loadPosts(topicId, nextChunk).fold(
                onSuccess = { posts ->
                    remainingPostIds = remainingPostIds.drop(nextChunk.size)
                    _uiState.value = _uiState.value.copy(
                        posts = (_uiState.value.posts + posts).distinctBy(Post::id),
                        isLoadingMore = false,
                        hasMore = remainingPostIds.isNotEmpty(),
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        loadMoreErrorMessage = error.readableMessage(),
                    )
                },
            )
        }
    }

    fun replacePost(post: Post) {
        replacePostById(post)
    }

    fun onReactionSelected(postId: Long, reactionId: String) {
        val currentPost = _uiState.value.posts.firstOrNull { post -> post.id == postId } ?: return
        val optimisticPost = currentPost.toggledReaction(
            reactionId = reactionId,
            emojiCatalog = _uiState.value.emojiCatalog,
        )
        replacePostById(optimisticPost)

        viewModelScope.launch {
            repository.toggleReaction(postId = postId, reactionId = reactionId).onFailure {
                replacePostById(currentPost)
            }
        }
    }

    fun openReactionSheet(
        postId: Long,
        reactions: List<PostReaction>,
        initialReactionId: String?,
    ) {
        val selectedReactionId = initialReactionId
            ?: reactions.firstOrNull { reaction -> reaction.reacted }?.id
            ?: reactions.firstOrNull()?.id
        _uiState.value = _uiState.value.copy(
            reactionSheet = TopicReactionSheetUiState(
                postId = postId,
                reactions = reactions,
                initialReactionId = initialReactionId,
            ),
            reactionUsers = TopicReactionUsersUiState(
                selectedReactionId = selectedReactionId,
                isLoading = true,
                hasLoaded = false,
            ),
        )

        viewModelScope.launch {
            repository.loadReactionUsers(postId = postId).fold(
                onSuccess = { groups ->
                    val currentSelected = _uiState.value.reactionUsers.selectedReactionId
                    _uiState.value = _uiState.value.copy(
                        reactionUsers = TopicReactionUsersUiState(
                            groups = groups,
                            selectedReactionId = currentSelected ?: groups.firstOrNull()?.id,
                            isLoading = false,
                            hasLoaded = true,
                        ),
                    )
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(
                        reactionUsers = _uiState.value.reactionUsers.copy(
                            groups = emptyList(),
                            isLoading = false,
                            hasLoaded = true,
                        ),
                    )
                },
            )
        }
    }

    fun selectReactionInSheet(reactionId: String) {
        _uiState.value = _uiState.value.copy(
            reactionUsers = _uiState.value.reactionUsers.copy(selectedReactionId = reactionId),
        )
    }

    fun dismissReactionSheet() {
        _uiState.value = _uiState.value.copy(
            reactionSheet = null,
            reactionUsers = TopicReactionUsersUiState(),
        )
    }

    suspend fun expandQuote(sourceTopicId: Long, sourcePostNumber: Int): Result<String> {
        return repository.loadPostByNumber(topicId = sourceTopicId, postNumber = sourcePostNumber)
            .map { post -> post.cookedHtml }
    }

    fun onPollOptionSelected(postId: Long, postNumber: Int?, pollName: String, optionId: String) {
        viewModelScope.launch {
            repository.votePoll(postId = postId, pollName = pollName, optionId = optionId).onSuccess {
                if (postNumber != null) {
                    repository.loadPostByNumber(topicId = topicId, postNumber = postNumber).onSuccess(::replacePostById)
                }
            }
        }
    }

    fun requestJumpToReplyTarget(postNumber: Int) {
        val target = TopicReplyTargetResolver.replyTargetsByPostNumber(_uiState.value.posts)[postNumber] ?: return
        jumpNonce += 1
        _uiState.value = _uiState.value.copy(
            jumpEvent = TopicJumpEvent(
                itemIndex = target.itemIndex,
                postId = target.postId,
                nonce = jumpNonce,
            ),
            jumpHighlight = TopicJumpHighlight(
                postId = target.postId,
                nonce = jumpNonce,
            ),
        )
    }

    fun consumeJumpEvent(nonce: Int) {
        if (_uiState.value.jumpEvent?.nonce != nonce) return
        _uiState.value = _uiState.value.copy(jumpEvent = null)
    }

    fun clearJumpHighlight(nonce: Int) {
        if (_uiState.value.jumpHighlight?.nonce != nonce) return
        _uiState.value = _uiState.value.copy(jumpHighlight = null)
    }

    private fun replacePostById(post: Post) {
        _uiState.value = _uiState.value.copy(
            posts = _uiState.value.posts.map { current ->
                if (current.id == post.id) post else current
            },
        )
    }
}
