package io.github.baba983.nativedo.feature.search.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.baba983.nativedo.core.storage.RecentSearchStore
import io.github.baba983.nativedo.feature.search.domain.SearchTopicsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchViewModel(
    private val searchTopics: SearchTopicsUseCase,
    private val recentSearchStore: RecentSearchStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            recentSearchStore.recentQueries.collect { queries ->
                _uiState.update { state ->
                    state.copy(recentQueries = queries)
                }
            }
        }
    }

    fun onQueryChanged(query: String) {
        _uiState.update { state -> state.copy(query = query) }
    }

    fun onQuerySubmitted(query: String) {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    query = query,
                    isSearching = true,
                    errorMessage = null,
                )
            }
            recentSearchStore.record(query)
            searchTopics(query).fold(
                onSuccess = { result ->
                    _uiState.update { state ->
                        state.copy(
                            isSearching = false,
                            topics = result.topics,
                            users = result.users,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { state ->
                        state.copy(
                            isSearching = false,
                            errorMessage = error.message,
                        )
                    }
                },
            )
        }
    }
}
