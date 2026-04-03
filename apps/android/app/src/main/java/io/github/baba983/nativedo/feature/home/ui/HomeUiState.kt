package io.github.baba983.nativedo.feature.home.ui

import io.github.baba983.nativedo.core.model.TopicSummary

enum class HomeFeedTab {
    Latest,
    New,
    Top,
}

data class HomeUiState(
    val selectedTab: HomeFeedTab = HomeFeedTab.Latest,
    val items: List<TopicSummary> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val nextPage: Int? = null,
    val errorMessage: String? = null,
    val loadMoreErrorMessage: String? = null,
) {
    val isEmpty: Boolean
        get() = !isLoading && items.isEmpty() && errorMessage == null
}
