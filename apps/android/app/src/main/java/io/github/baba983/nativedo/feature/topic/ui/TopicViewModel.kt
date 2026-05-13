package io.github.baba983.nativedo.feature.topic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.baba983.nativedo.core.model.Post
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
        _uiState.value = _uiState.value.copy(
            posts = _uiState.value.posts.map { current ->
                if (current.id == post.id) post else current
            },
        )
    }
}
