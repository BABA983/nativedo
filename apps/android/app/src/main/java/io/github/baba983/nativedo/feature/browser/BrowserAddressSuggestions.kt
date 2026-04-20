package io.github.baba983.nativedo.feature.browser

import io.github.baba983.nativedo.core.storage.AppBrowserRecentSearchRecord
import io.github.baba983.nativedo.core.storage.AppBrowserSearchEngine

internal enum class BrowserAddressSuggestionSectionKind {
    QuickActions,
    RecentSearches,
    Bookmarks,
    RecentLinks,
}

internal enum class BrowserAddressQuickAction {
    CopyCurrentLink,
    ToggleBookmark,
}

internal sealed interface BrowserAddressSuggestion {
    data class RecentLink(
        val title: String,
        val url: String,
        val host: String,
        val visitedAtMillis: Long,
    ) : BrowserAddressSuggestion

    data class Bookmark(
        val title: String,
        val url: String,
        val host: String,
        val savedAtMillis: Long,
    ) : BrowserAddressSuggestion

    data class RecentSearch(
        val query: String,
        val searchedAtMillis: Long,
    ) : BrowserAddressSuggestion

    data class QuickAction(
        val action: BrowserAddressQuickAction,
    ) : BrowserAddressSuggestion
}

internal data class BrowserAddressSuggestionSection(
    val kind: BrowserAddressSuggestionSectionKind,
    val items: List<BrowserAddressSuggestion>,
)

internal sealed interface BrowserAddressSuggestionResult {
    data class SubmitAddress(val value: String) : BrowserAddressSuggestionResult
    data class QuickAction(val action: BrowserAddressQuickAction) : BrowserAddressSuggestionResult
}

internal fun buildBrowserAddressSuggestionSections(
    draft: String,
    currentUrl: String,
    currentTitle: String?,
    historyEntries: List<BrowserHistoryRecord>,
    bookmarks: List<AppBrowserBookmarkRecord>,
    recentSearches: List<AppBrowserRecentSearchRecord>,
): List<BrowserAddressSuggestionSection> {
    val query = draft.trim().lowercase()
    val matches: (String) -> Boolean = { value ->
        query.isBlank() || value.lowercase().contains(query)
    }

    val quickActions = buildList {
        if (currentUrl.isNotBlank()) {
            add(BrowserAddressSuggestion.QuickAction(BrowserAddressQuickAction.CopyCurrentLink))
        }
        if (!currentUrl.isNullOrBlank()) {
            add(BrowserAddressSuggestion.QuickAction(BrowserAddressQuickAction.ToggleBookmark))
        }
    }

    val searchItems = recentSearches
        .filter { record -> matches(record.query) }
        .map { record ->
            BrowserAddressSuggestion.RecentSearch(
                query = record.query,
                searchedAtMillis = record.searchedAtMillis,
            )
        }

    val bookmarkItems = bookmarks
        .filter { bookmark ->
            matches(bookmark.title) || matches(bookmark.url) || matches(bookmark.host)
        }
        .map { bookmark ->
            BrowserAddressSuggestion.Bookmark(
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
            BrowserAddressSuggestion.RecentLink(
                title = history.title,
                url = history.url,
                host = history.host,
                visitedAtMillis = history.visitedAtMillis,
            )
        }

    return buildList {
        if (quickActions.isNotEmpty()) {
            add(
                BrowserAddressSuggestionSection(
                    kind = BrowserAddressSuggestionSectionKind.QuickActions,
                    items = quickActions,
                ),
            )
        }
        if (searchItems.isNotEmpty()) {
            add(
                BrowserAddressSuggestionSection(
                    kind = BrowserAddressSuggestionSectionKind.RecentSearches,
                    items = searchItems,
                ),
            )
        }
        if (bookmarkItems.isNotEmpty()) {
            add(
                BrowserAddressSuggestionSection(
                    kind = BrowserAddressSuggestionSectionKind.Bookmarks,
                    items = bookmarkItems,
                ),
            )
        }
        if (historyItems.isNotEmpty()) {
            add(
                BrowserAddressSuggestionSection(
                    kind = BrowserAddressSuggestionSectionKind.RecentLinks,
                    items = historyItems,
                ),
            )
        }
    }
}

internal fun applyBrowserAddressSuggestion(
    suggestion: BrowserAddressSuggestion,
    currentDraft: String,
    baseUrl: String,
    searchEngine: AppBrowserSearchEngine,
): BrowserAddressSuggestionResult {
    return when (suggestion) {
        is BrowserAddressSuggestion.RecentLink -> BrowserAddressSuggestionResult.SubmitAddress(suggestion.url)
        is BrowserAddressSuggestion.Bookmark -> BrowserAddressSuggestionResult.SubmitAddress(suggestion.url)
        is BrowserAddressSuggestion.RecentSearch -> BrowserAddressSuggestionResult.SubmitAddress(
            resolveBrowserAddressInput(
                value = suggestion.query,
                baseUrl = baseUrl,
                searchEngine = searchEngine,
            ).orEmpty(),
        )
        is BrowserAddressSuggestion.QuickAction -> BrowserAddressSuggestionResult.QuickAction(suggestion.action)
    }
}
