package io.github.baba983.nativedo.feature.topic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.baba983.nativedo.core.network.isSessionRecoveryRequired
import io.github.baba983.nativedo.feature.topic.domain.LoadTopicUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TOPIC_POST_PAGE_SIZE = 20

class TopicViewModel(
    private val loadTopic: LoadTopicUseCase,
    private val topicId: Long,
    private val currentSessionVersion: () -> Long = { 0L },
) : ViewModel() {
    private val _uiState = MutableStateFlow(TopicUiState())
    val uiState: StateFlow<TopicUiState> = _uiState.asStateFlow()

    private var remainingPostIds: List<Long> = emptyList()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val requestVersion = currentSessionVersion()
            val hasExistingPosts = _uiState.value.posts.isNotEmpty()
            _uiState.value = _uiState.value.copy(
                isLoading = !hasExistingPosts,
                isRefreshing = hasExistingPosts,
                isLoadingMore = false,
                errorMessage = null,
                loadMoreErrorMessage = null,
            )
            loadTopic(topicId).fold(
                onSuccess = { detail ->
                    if (requestVersion != currentSessionVersion()) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isRefreshing = false,
                        )
                        return@launch
                    }
                    val loadedIds = detail.posts.map { it.id }.toSet()
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
                        isAuthRecoveryRequired = false,
                    )
                },
                onFailure = { error ->
                    if (requestVersion != currentSessionVersion()) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isRefreshing = false,
                        )
                        return@launch
                    }
                    if (error.isSessionRecoveryRequired()) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = null,
                            loadMoreErrorMessage = null,
                            isAuthRecoveryRequired = true,
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = error.message,
                            isAuthRecoveryRequired = false,
                        )
                    }
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
            val requestVersion = currentSessionVersion()
            _uiState.value = state.copy(
                isLoadingMore = true,
                loadMoreErrorMessage = null,
            )
            loadTopic.loadPosts(topicId, nextChunk).fold(
                onSuccess = { posts ->
                    if (requestVersion != currentSessionVersion()) {
                        _uiState.value = _uiState.value.copy(isLoadingMore = false)
                        return@launch
                    }
                    remainingPostIds = remainingPostIds.drop(nextChunk.size)
                    _uiState.value = _uiState.value.copy(
                        posts = (_uiState.value.posts + posts).distinctBy { it.id },
                        isLoadingMore = false,
                        hasMore = remainingPostIds.isNotEmpty(),
                        isAuthRecoveryRequired = false,
                    )
                },
                onFailure = { error ->
                    if (requestVersion != currentSessionVersion()) {
                        _uiState.value = _uiState.value.copy(isLoadingMore = false)
                        return@launch
                    }
                    if (error.isSessionRecoveryRequired()) {
                        _uiState.value = _uiState.value.copy(
                            isLoadingMore = false,
                            loadMoreErrorMessage = null,
                            isAuthRecoveryRequired = true,
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoadingMore = false,
                            loadMoreErrorMessage = error.message,
                            isAuthRecoveryRequired = false,
                        )
                    }
                },
            )
        }
    }

    fun replacePost(post: io.github.baba983.nativedo.core.model.Post) {
        _uiState.value = _uiState.value.copy(
            posts = _uiState.value.posts.map { existing ->
                if (existing.id == post.id) post else existing
            },
        )
    }

    fun onAuthRecoveryHandled() {
        _uiState.value = _uiState.value.copy(isAuthRecoveryRequired = false)
    }
}
