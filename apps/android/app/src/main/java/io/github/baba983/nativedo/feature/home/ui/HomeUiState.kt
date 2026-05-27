package io.github.baba983.nativedo.feature.home.ui

import io.github.baba983.nativedo.core.model.CategorySummary
import io.github.baba983.nativedo.core.model.TopicSummary

enum class HomeFeedTab {
    Latest,
    New,
    Top,
}

data class HomeFeedState(
    val items: List<TopicSummary> = emptyList(),
    val isLoading: Boolean = false,
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

data class HomeUiState(
    val selectedTab: HomeFeedTab = HomeFeedTab.Latest,
    val feeds: Map<HomeFeedTab, HomeFeedState> = HomeFeedTab.entries.associateWith { tab ->
        HomeFeedState(isLoading = tab == HomeFeedTab.Latest)
    },
    val categories: Map<Int, CategorySummary> = emptyMap(),
) {
    fun feedState(tab: HomeFeedTab): HomeFeedState = feeds[tab] ?: HomeFeedState()

    val currentFeed: HomeFeedState
        get() = feedState(selectedTab)

    val items: List<TopicSummary>
        get() = currentFeed.items

    val isLoading: Boolean
        get() = currentFeed.isLoading

    val isRefreshing: Boolean
        get() = currentFeed.isRefreshing

    val isLoadingMore: Boolean
        get() = currentFeed.isLoadingMore

    val hasMore: Boolean
        get() = currentFeed.hasMore

    val nextPage: Int?
        get() = currentFeed.nextPage

    val errorMessage: String?
        get() = currentFeed.errorMessage

    val loadMoreErrorMessage: String?
        get() = currentFeed.loadMoreErrorMessage

    val isEmpty: Boolean
        get() = currentFeed.isEmpty
}
