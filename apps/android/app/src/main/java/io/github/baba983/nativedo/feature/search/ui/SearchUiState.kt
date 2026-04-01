package io.github.baba983.nativedo.feature.search.ui

import io.github.baba983.nativedo.core.model.SearchUser
import io.github.baba983.nativedo.core.model.TopicSummary

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val topics: List<TopicSummary> = emptyList(),
    val users: List<SearchUser> = emptyList(),
    val recentQueries: List<String> = emptyList(),
    val errorMessage: String? = null,
)
