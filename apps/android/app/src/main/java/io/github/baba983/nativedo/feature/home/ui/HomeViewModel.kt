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

    private fun refresh(tab: HomeFeedTab, initial: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                selectedTab = tab,
                isLoading = initial,
                isRefreshing = !initial,
                errorMessage = null,
            )
            loadHomeFeed(tab).fold(
                onSuccess = { topics ->
                    _uiState.value = _uiState.value.copy(
                        items = topics,
                        isLoading = false,
                        isRefreshing = false,
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
