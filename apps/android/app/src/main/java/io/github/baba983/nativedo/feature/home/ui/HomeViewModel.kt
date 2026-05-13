package io.github.baba983.nativedo.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.baba983.nativedo.core.storage.CategoryStore
import io.github.baba983.nativedo.feature.home.data.HomeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: HomeRepository,
    private val categoryStore: CategoryStore? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
        refresh(HomeFeedTab.Latest, initial = true)
    }

    fun onFeedSelected(tab: HomeFeedTab) {
        if (tab != _uiState.value.selectedTab) {
            refresh(tab, initial = true)
        }
    }

    fun markTopicSeen(topicId: Long) {
        val state = _uiState.value
        _uiState.value = state.copy(
            items = state.items.map { topic ->
                if (topic.id == topicId && (topic.isUnseen || topic.unreadCount > 0)) {
                    topic.copy(isUnseen = false, unreadCount = 0)
                } else {
                    topic
                }
            },
        )
    }

    fun onRefresh() {
        refresh(_uiState.value.selectedTab, initial = false)
    }

    fun loadNextPage() {
        val state = _uiState.value
        val nextPage = state.nextPage ?: return
        if (state.isLoading || state.isRefreshing || state.isLoadingMore || !state.hasMore) {
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(
                isLoadingMore = true,
                loadMoreErrorMessage = null,
            )
            repository.load(state.selectedTab, nextPage).fold(
                onSuccess = { page ->
                    if (_uiState.value.selectedTab != state.selectedTab) {
                        return@fold
                    }
                    _uiState.value = _uiState.value.copy(
                        items = (_uiState.value.items + page.items).distinctBy { it.id },
                        isLoadingMore = false,
                        hasMore = page.hasMore,
                        nextPage = page.nextPage,
                    )
                },
                onFailure = { error ->
                    if (_uiState.value.selectedTab != state.selectedTab) {
                        return@fold
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        loadMoreErrorMessage = error.readableMessage(),
                    )
                },
            )
        }
    }

    private fun refresh(tab: HomeFeedTab, initial: Boolean) {
        viewModelScope.launch {
            val currentState = _uiState.value
            _uiState.value = currentState.copy(
                selectedTab = tab,
                items = if (initial) emptyList() else currentState.items,
                isLoading = initial,
                isRefreshing = !initial,
                isLoadingMore = false,
                hasMore = if (initial) false else currentState.hasMore,
                nextPage = if (initial) null else currentState.nextPage,
                errorMessage = null,
                loadMoreErrorMessage = null,
            )
            repository.load(tab, page = 0).fold(
                onSuccess = { page ->
                    if (_uiState.value.selectedTab != tab) {
                        return@fold
                    }
                    _uiState.value = _uiState.value.copy(
                        items = page.items,
                        isLoading = false,
                        isRefreshing = false,
                        hasMore = page.hasMore,
                        nextPage = page.nextPage,
                    )
                },
                onFailure = { error ->
                    if (_uiState.value.selectedTab != tab) {
                        return@fold
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = error.readableMessage(),
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

internal fun Throwable.readableMessage(): String =
    message?.takeIf(String::isNotBlank) ?: javaClass.simpleName
