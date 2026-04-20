package io.github.baba983.nativedo.feature.browser

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.storage.AppBrowserRecentSearchRecord
import org.junit.Test

class BrowserAddressSuggestionsTest {

    @Test
    fun buildAddressSuggestionSections_combinesQuickActionsRecentSearchesBookmarksAndLinks() {
        val sections = buildBrowserAddressSuggestionSections(
            draft = "compose",
            currentUrl = "https://linux.do/t/123",
            currentTitle = "Compose Thread",
            historyEntries = listOf(
                BrowserHistoryRecord(
                    title = "Compose history",
                    url = "https://linux.do/t/compose/1",
                    host = "linux.do",
                    visitedAtMillis = 10L,
                ),
            ),
            bookmarks = listOf(
                AppBrowserBookmarkRecord(
                    title = "Compose bookmark",
                    url = "https://linux.do/t/bookmark/2",
                    host = "linux.do",
                    savedAtMillis = 20L,
                ),
            ),
            recentSearches = listOf(
                AppBrowserRecentSearchRecord(
                    query = "compose animation",
                    searchedAtMillis = 30L,
                ),
            ),
        )

        assertThat(sections.map { it.kind }).containsExactly(
            BrowserAddressSuggestionSectionKind.QuickActions,
            BrowserAddressSuggestionSectionKind.RecentSearches,
            BrowserAddressSuggestionSectionKind.Bookmarks,
            BrowserAddressSuggestionSectionKind.RecentLinks,
        ).inOrder()
    }

    @Test
    fun buildAddressSuggestionSections_filtersEntriesUsingDraft() {
        val sections = buildBrowserAddressSuggestionSections(
            draft = "anim",
            currentUrl = "https://linux.do/t/123",
            currentTitle = "Compose Thread",
            historyEntries = listOf(
                BrowserHistoryRecord(
                    title = "Compose history",
                    url = "https://linux.do/t/compose/1",
                    host = "linux.do",
                    visitedAtMillis = 10L,
                ),
            ),
            bookmarks = listOf(
                AppBrowserBookmarkRecord(
                    title = "Animation bookmark",
                    url = "https://linux.do/t/animation/2",
                    host = "linux.do",
                    savedAtMillis = 20L,
                ),
            ),
            recentSearches = listOf(
                AppBrowserRecentSearchRecord(
                    query = "compose animation",
                    searchedAtMillis = 30L,
                ),
            ),
        )

        val bookmarkSection = sections.first { it.kind == BrowserAddressSuggestionSectionKind.Bookmarks }
        val linkSection = sections.firstOrNull { it.kind == BrowserAddressSuggestionSectionKind.RecentLinks }

        assertThat(bookmarkSection.items).hasSize(1)
        assertThat(linkSection).isNull()
    }

    @Test
    fun applyAddressSuggestion_forRecentSearch_usesSearchResolver() {
        val result = applyBrowserAddressSuggestion(
            suggestion = BrowserAddressSuggestion.RecentSearch(
                query = "compose animation",
                searchedAtMillis = 1L,
            ),
            currentDraft = "compose",
            baseUrl = "https://linux.do",
            searchEngine = io.github.baba983.nativedo.core.storage.AppBrowserSearchEngine.Google,
        )

        assertThat(result).isEqualTo(
            BrowserAddressSuggestionResult.SubmitAddress(
                "https://www.google.com/search?q=compose%20animation",
            ),
        )
    }

    @Test
    fun applyAddressSuggestion_forQuickAction_returnsActionResult() {
        val result = applyBrowserAddressSuggestion(
            suggestion = BrowserAddressSuggestion.QuickAction(
                action = BrowserAddressQuickAction.CopyCurrentLink,
            ),
            currentDraft = "",
            baseUrl = "https://linux.do",
            searchEngine = io.github.baba983.nativedo.core.storage.AppBrowserSearchEngine.LinuxDo,
        )

        assertThat(result).isEqualTo(
            BrowserAddressSuggestionResult.QuickAction(BrowserAddressQuickAction.CopyCurrentLink),
        )
    }
}
