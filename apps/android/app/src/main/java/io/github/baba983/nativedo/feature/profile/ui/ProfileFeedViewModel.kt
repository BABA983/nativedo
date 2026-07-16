package io.github.baba983.nativedo.feature.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.core.storage.CategoryStore
import io.github.baba983.nativedo.feature.home.ui.readableMessage
import io.github.baba983.nativedo.feature.profile.data.ProfileFeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ProfileFeedViewModel(
    private val username: String,
    private val feedType: ProfileFeedType,
    private val repository: ProfileFeedRepository,
    private val categoryStore: CategoryStore? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileFeedUiState())
    val uiState: StateFlow<ProfileFeedUiState> = _uiState.asStateFlow()
    private var requestGeneration = 0
    private var initialLoadJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        loadCategories()
        loadInitial()
    }

    fun retry() {
        if (_uiState.value.isLoading) return
        loadInitial()
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (
            state.isLoading ||
            state.isLoadingMore ||
            !state.hasMore ||
            state.loadMoreErrorMessage != null
        ) {
            return
        }
        loadMore(state.nextOffset)
    }

    fun retryLoadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        loadMore(state.nextOffset)
    }

    private fun loadInitial() {
        val generation = ++requestGeneration
        initialLoadJob?.cancel()
        loadMoreJob?.cancel()
        initialLoadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                items = emptyList(),
                isLoading = true,
                isLoadingMore = false,
                hasMore = false,
                nextOffset = 0,
                errorMessage = null,
                loadMoreErrorMessage = null,
            )
            repository.load(
                username = username,
                filter = feedType.filter,
                offset = 0,
            ).fold(
                onSuccess = { page ->
                    if (generation != requestGeneration) return@fold
                    _uiState.value = _uiState.value.copy(
                        items = page.items.distinctActions(),
                        isLoading = false,
                        hasMore = page.rawCount == ProfileFeedRepository.PAGE_SIZE,
                        nextOffset = ProfileFeedRepository.PAGE_SIZE,
                    )
                },
                onFailure = { error ->
                    if (generation != requestGeneration) return@fold
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.readableMessage(),
                    )
                },
            )
        }
    }

    private fun loadMore(offset: Int) {
        val generation = requestGeneration
        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingMore = true,
                loadMoreErrorMessage = null,
            )
            repository.load(
                username = username,
                filter = feedType.filter,
                offset = offset,
            ).fold(
                onSuccess = { page ->
                    if (generation != requestGeneration || offset != _uiState.value.nextOffset) return@fold
                    _uiState.value = _uiState.value.copy(
                        items = (_uiState.value.items + page.items).distinctActions(),
                        isLoadingMore = false,
                        hasMore = page.rawCount == ProfileFeedRepository.PAGE_SIZE,
                        nextOffset = offset + ProfileFeedRepository.PAGE_SIZE,
                    )
                },
                onFailure = { error ->
                    if (generation != requestGeneration || offset != _uiState.value.nextOffset) return@fold
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        loadMoreErrorMessage = error.readableMessage(),
                    )
                },
            )
        }
    }

    private fun loadCategories() {
        val store = categoryStore ?: return
        viewModelScope.launch {
            store.loadIfNeeded().onSuccess { categories ->
                _uiState.value = _uiState.value.copy(categories = categories)
            }
        }
    }
}

private fun List<TopicSummary>.distinctActions(): List<TopicSummary> =
    distinctBy { topic -> topic.rowIdentity ?: "${topic.id}:${topic.targetPostNumber ?: 0}" }
