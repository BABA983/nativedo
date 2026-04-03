package io.github.baba983.nativedo.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.baba983.nativedo.feature.home.domain.LoadHomeFeedUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val loadHomeFeed: LoadHomeFeedUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh(HomeFeedTab.Latest, initial = true)
    }

    fun onFeedSelected(tab: HomeFeedTab) {
        if (tab != _uiState.value.selectedTab) {
            refresh(tab, initial = false)
        }
    }

    fun onRefresh() {
        refresh(_uiState.value.selectedTab, initial = false)
    }

    fun loadNextPage() {
        val state = _uiState.value
        val nextPage = state.nextPage ?: return
        if (state.isLoading || state.isRefreshing || state.isLoadingMore || !state.hasMore) return

        viewModelScope.launch {
            _uiState.value = state.copy(
                isLoadingMore = true,
                loadMoreErrorMessage = null,
            )
            loadHomeFeed(state.selectedTab, nextPage).fold(
                onSuccess = { page ->
                    _uiState.value = _uiState.value.copy(
                        items = (_uiState.value.items + page.items).distinctBy { it.id },
                        isLoadingMore = false,
                        hasMore = page.hasMore,
                        nextPage = page.nextPage,
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        loadMoreErrorMessage = error.message,
                    )
                },
            )
        }
    }

    private fun refresh(tab: HomeFeedTab, initial: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                selectedTab = tab,
                isLoading = initial,
                isRefreshing = !initial,
                isLoadingMore = false,
                errorMessage = null,
                loadMoreErrorMessage = null,
            )
            loadHomeFeed(tab, page = 0).fold(
                onSuccess = { page ->
                    _uiState.value = _uiState.value.copy(
                        items = page.items,
                        isLoading = false,
                        isRefreshing = false,
                        hasMore = page.hasMore,
                        nextPage = page.nextPage,
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = error.message,
                    )
                },
            )
        }
    }
}
