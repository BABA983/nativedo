package io.github.baba983.nativedo.feature.browser

import io.github.baba983.nativedo.core.storage.AppBrowserRecentSearchRecord
import io.github.baba983.nativedo.core.storage.AppBrowserSearchEngine

internal enum class AppBrowserAddressSuggestionSectionKind {
    QuickActions,
    RecentSearches,
    Bookmarks,
    RecentLinks,
}

internal enum class AppBrowserAddressQuickAction {
    CopyCurrentLink,
    ToggleBookmark,
}

internal sealed interface AppBrowserAddressSuggestion {
    data class RecentLink(
        val title: String,
        val url: String,
        val host: String,
        val visitedAtMillis: Long,
    ) : AppBrowserAddressSuggestion

    data class Bookmark(
        val title: String,
        val url: String,
        val host: String,
        val savedAtMillis: Long,
    ) : AppBrowserAddressSuggestion

    data class RecentSearch(
        val query: String,
        val searchedAtMillis: Long,
    ) : AppBrowserAddressSuggestion

    data class QuickAction(
        val action: AppBrowserAddressQuickAction,
    ) : AppBrowserAddressSuggestion
}

internal data class AppBrowserAddressSuggestionSection(
    val kind: AppBrowserAddressSuggestionSectionKind,
    val items: List<AppBrowserAddressSuggestion>,
)

internal sealed interface AppBrowserAddressSuggestionResult {
    data class SubmitAddress(val value: String) : AppBrowserAddressSuggestionResult
    data class QuickAction(val action: AppBrowserAddressQuickAction) : AppBrowserAddressSuggestionResult
}

internal fun buildAppBrowserAddressSuggestionSections(
    draft: String,
    currentUrl: String,
    currentTitle: String?,
    historyEntries: List<AppBrowserHistoryRecord>,
    bookmarks: List<AppBrowserBookmarkRecord>,
    recentSearches: List<AppBrowserRecentSearchRecord>,
): List<AppBrowserAddressSuggestionSection> {
    val query = draft.trim().lowercase()
    val matches: (String) -> Boolean = { value ->
        query.isBlank() || value.lowercase().contains(query)
    }

    val quickActions = buildList {
        if (currentUrl.isNotBlank()) {
            add(AppBrowserAddressSuggestion.QuickAction(AppBrowserAddressQuickAction.CopyCurrentLink))
        }
        if (!currentUrl.isNullOrBlank()) {
            add(AppBrowserAddressSuggestion.QuickAction(AppBrowserAddressQuickAction.ToggleBookmark))
        }
    }

    val searchItems = recentSearches
        .filter { record -> matches(record.query) }
        .map { record ->
            AppBrowserAddressSuggestion.RecentSearch(
                query = record.query,
                searchedAtMillis = record.searchedAtMillis,
            )
        }

    val bookmarkItems = bookmarks
        .filter { bookmark ->
            matches(bookmark.title) || matches(bookmark.url) || matches(bookmark.host)
        }
        .map { bookmark ->
            AppBrowserAddressSuggestion.Bookmark(
                title = bookmark.title,
                url = bookmark.url,
                host = bookmark.host,
                savedAtMillis = bookmark.savedAtMillis,
            )
        }

    val historyItems = historyEntries
        .filter { history ->
            matches(history.title) || matches(history.url) || matches(history.host)
        }
        .map { history ->
            AppBrowserAddressSuggestion.RecentLink(
                title = history.title,
                url = history.url,
                host = history.host,
                visitedAtMillis = history.visitedAtMillis,
            )
        }

    return buildList {
        if (quickActions.isNotEmpty()) {
            add(
                AppBrowserAddressSuggestionSection(
                    kind = AppBrowserAddressSuggestionSectionKind.QuickActions,
                    items = quickActions,
                ),
            )
        }
        if (searchItems.isNotEmpty()) {
            add(
                AppBrowserAddressSuggestionSection(
                    kind = AppBrowserAddressSuggestionSectionKind.RecentSearches,
                    items = searchItems,
                ),
            )
        }
        if (bookmarkItems.isNotEmpty()) {
            add(
                AppBrowserAddressSuggestionSection(
                    kind = AppBrowserAddressSuggestionSectionKind.Bookmarks,
                    items = bookmarkItems,
                ),
            )
        }
        if (historyItems.isNotEmpty()) {
            add(
                AppBrowserAddressSuggestionSection(
                    kind = AppBrowserAddressSuggestionSectionKind.RecentLinks,
                    items = historyItems,
                ),
            )
        }
    }
}

internal fun applyAppBrowserAddressSuggestion(
    suggestion: AppBrowserAddressSuggestion,
    currentDraft: String,
    baseUrl: String,
    searchEngine: AppBrowserSearchEngine,
): AppBrowserAddressSuggestionResult {
    return when (suggestion) {
        is AppBrowserAddressSuggestion.RecentLink -> AppBrowserAddressSuggestionResult.SubmitAddress(suggestion.url)
        is AppBrowserAddressSuggestion.Bookmark -> AppBrowserAddressSuggestionResult.SubmitAddress(suggestion.url)
        is AppBrowserAddressSuggestion.RecentSearch -> AppBrowserAddressSuggestionResult.SubmitAddress(
            resolveAppBrowserAddressInput(
                value = suggestion.query,
                baseUrl = baseUrl,
                searchEngine = searchEngine,
            ).orEmpty(),
        )
        is AppBrowserAddressSuggestion.QuickAction -> AppBrowserAddressSuggestionResult.QuickAction(suggestion.action)
    }
}
