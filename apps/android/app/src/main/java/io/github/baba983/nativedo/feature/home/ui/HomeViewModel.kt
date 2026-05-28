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
        val state = _uiState.value
        if (tab == state.selectedTab) {
            return
        }
        _uiState.value = state.copy(selectedTab = tab)
        val feed = state.feedState(tab)
        if (feed.items.isEmpty() && !feed.isLoading && feed.errorMessage == null) {
            refresh(tab, initial = true)
        }
    }

    fun markTopicSeen(topicId: Long) {
        val state = _uiState.value
        _uiState.value = state.copy(
            feeds = state.feeds.mapValues { (_, feed) ->
                feed.copy(
                    items = feed.items.map { topic ->
                        if (topic.id == topicId && (topic.isUnseen || topic.unreadCount > 0)) {
                            topic.copy(isUnseen = false, unreadCount = 0)
                        } else {
                            topic
                        }
                    },
                )
            },
        )
    }

    fun onRefresh() {
        refresh(_uiState.value.selectedTab, initial = false)
    }

    fun retrySelectedFeed() {
        val state = _uiState.value
        refresh(state.selectedTab, initial = state.currentFeed.items.isEmpty())
    }

    fun loadNextPage() {
        val state = _uiState.value
        val selectedTab = state.selectedTab
        val feed = state.feedState(selectedTab)
        val nextPage = feed.nextPage ?: return
        if (feed.isLoading || feed.isRefreshing || feed.isLoadingMore || !feed.hasMore) {
            return
        }

        viewModelScope.launch {
            _uiState.value = state.updateFeed(selectedTab) { currentFeed ->
                currentFeed.copy(
                    isLoadingMore = true,
                    loadMoreErrorMessage = null,
                )
            }
            repository.load(selectedTab, nextPage).fold(
                onSuccess = { page ->
                    _uiState.value = _uiState.value.updateFeed(selectedTab) { currentFeed ->
                        currentFeed.copy(
                            items = (currentFeed.items + page.items).distinctBy { it.id },
                            isLoadingMore = false,
                            hasMore = page.hasMore,
                            nextPage = page.nextPage,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.updateFeed(selectedTab) { currentFeed ->
                        currentFeed.copy(
                            isLoadingMore = false,
                            loadMoreErrorMessage = error.readableMessage(),
                        )
                    }
                },
            )
        }
    }

    private fun refresh(tab: HomeFeedTab, initial: Boolean) {
        viewModelScope.launch {
            val currentState = _uiState.value
            _uiState.value = currentState
                .copy(selectedTab = tab)
                .updateFeed(tab) { feed ->
                    feed.copy(
                        items = if (initial) emptyList() else feed.items,
                        isLoading = initial,
                        isRefreshing = !initial,
                        isLoadingMore = false,
                        hasMore = if (initial) false else feed.hasMore,
                        nextPage = if (initial) null else feed.nextPage,
                        errorMessage = null,
                        loadMoreErrorMessage = null,
                    )
                }
            repository.load(tab, page = 0).fold(
                onSuccess = { page ->
                    _uiState.value = _uiState.value.updateFeed(tab) { feed ->
                        feed.copy(
                            items = page.items,
                            isLoading = false,
                            isRefreshing = false,
                            hasMore = page.hasMore,
                            nextPage = page.nextPage,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.updateFeed(tab) { feed ->
                        feed.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = error.readableMessage(),
                        )
                    }
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

private fun HomeUiState.updateFeed(
    tab: HomeFeedTab,
    transform: (HomeFeedState) -> HomeFeedState,
): HomeUiState = copy(feeds = feeds + (tab to transform(feedState(tab))))

internal fun Throwable.readableMessage(): String =
    message?.takeIf(String::isNotBlank) ?: javaClass.simpleName
