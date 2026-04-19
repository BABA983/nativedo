package io.github.baba983.nativedo.feature.browser

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.storage.AppBrowserSearchEngine
import org.junit.Test

class AppBrowserViewModelTest {

    @Test
    fun onAction_updateSearchQuery_updatesUiState() {
        val viewModel = AppBrowserViewModel(initialUrl = "https://linux.do")

        viewModel.onAction(AppBrowserAction.UpdateSearchQuery("compose"))

        assertThat(viewModel.uiState.value.search.query).isEqualTo("compose")
        assertThat(viewModel.uiState.value.search.isCounting).isTrue()
    }

    @Test
    fun onAction_updateSearchMatches_clearsCountingState() {
        val viewModel = AppBrowserViewModel(initialUrl = "https://linux.do")

        viewModel.onAction(AppBrowserAction.UpdateSearchQuery("compose"))
        viewModel.onAction(
            AppBrowserAction.UpdateSearchMatches(
                activeMatchOrdinal = 1,
                numberOfMatches = 4,
            ),
        )

        assertThat(viewModel.uiState.value.search.activeMatchOrdinal).isEqualTo(1)
        assertThat(viewModel.uiState.value.search.numberOfMatches).isEqualTo(4)
        assertThat(viewModel.uiState.value.search.isCounting).isFalse()
    }

    @Test
    fun onAction_openHistory_switchesSheetAndStopsAddressEditing() {
        val viewModel = AppBrowserViewModel(initialUrl = "https://linux.do")
        viewModel.startAddressEditing(
            currentUrl = "https://linux.do/t/123",
            recentEntries = emptyList(),
        )

        viewModel.onAction(AppBrowserAction.OpenHistory)

        assertThat(viewModel.uiState.value.sheet).isEqualTo(AppBrowserSheet.History)
        assertThat(viewModel.uiState.value.address.isEditing).isFalse()
    }

    @Test
    fun onAction_openBookmarks_switchesSheetAndStopsAddressEditing() {
        val viewModel = AppBrowserViewModel(initialUrl = "https://linux.do")
        viewModel.startAddressEditing(
            currentUrl = "https://linux.do/t/123",
            recentEntries = emptyList(),
        )

        viewModel.onAction(AppBrowserAction.OpenBookmarks)

        assertThat(viewModel.uiState.value.sheet).isEqualTo(AppBrowserSheet.Bookmarks)
        assertThat(viewModel.uiState.value.address.isEditing).isFalse()
    }

    @Test
    fun onAction_setHistoryPopupVisible_updatesFlag() {
        val viewModel = AppBrowserViewModel(initialUrl = "https://linux.do")

        viewModel.onAction(AppBrowserAction.SetHistoryPopupVisible(true))

        assertThat(viewModel.uiState.value.isHistoryPopupVisible).isTrue()
    }

    @Test
    fun onAction_setOverflowMenuVisible_updatesFlag() {
        val viewModel = AppBrowserViewModel(initialUrl = "https://linux.do")

        viewModel.onAction(AppBrowserAction.SetOverflowMenuVisible(true))

        assertThat(viewModel.uiState.value.isOverflowMenuVisible).isTrue()
    }

    @Test
    fun startAddressEditing_setsEditingStateWithEmptyDraftAndClosesSearch() {
        val viewModel = AppBrowserViewModel(initialUrl = "https://linux.do")

        viewModel.onAction(AppBrowserAction.UpdateSearchQuery("compose"))
        viewModel.onAction(AppBrowserAction.StartSearch)
        viewModel.startAddressEditing(
            currentUrl = "https://linux.do/t/123",
            recentEntries = listOf(
                AppBrowserHistoryRecord(
                    title = "Linux.do",
                    url = "https://linux.do",
                    host = "linux.do",
                    visitedAtMillis = 1L,
                ),
            ),
        )

        assertThat(viewModel.uiState.value.address.isEditing).isTrue()
        assertThat(viewModel.uiState.value.address.draft).isEmpty()
        assertThat(viewModel.uiState.value.address.recentEntries).hasSize(1)
        assertThat(viewModel.uiState.value.search.isActive).isFalse()
    }

    @Test
    fun startAddressEditing_closesAnyOpenSubPanel() {
        val viewModel = AppBrowserViewModel(initialUrl = "https://linux.do")

        viewModel.onAction(AppBrowserAction.OpenSettings)
        viewModel.startAddressEditing(
            currentUrl = "https://linux.do/t/123",
            recentEntries = emptyList(),
        )

        assertThat(viewModel.uiState.value.address.isEditing).isTrue()
        assertThat(viewModel.uiState.value.sheet).isEqualTo(AppBrowserSheet.None)
    }

    @Test
    fun onAction_cancelAddressEditing_keepsDraftAndStopsEditing() {
        val viewModel = AppBrowserViewModel(initialUrl = "https://linux.do")
        viewModel.startAddressEditing(
            currentUrl = "https://linux.do/t/123",
            recentEntries = emptyList(),
        )
        viewModel.onAction(AppBrowserAction.UpdateAddressDraft("https://linux.do/new"))

        viewModel.onAction(AppBrowserAction.CancelAddressEditing)

        assertThat(viewModel.uiState.value.address.isEditing).isFalse()
        assertThat(viewModel.uiState.value.address.draft).isEqualTo("https://linux.do/new")
    }

    @Test
    fun onAction_startSearch_closesAnyOpenSubPanel() {
        val viewModel = AppBrowserViewModel(initialUrl = "https://linux.do")

        viewModel.onAction(AppBrowserAction.OpenHistory)
        viewModel.onAction(AppBrowserAction.StartSearch)

        assertThat(viewModel.uiState.value.search.isActive).isTrue()
        assertThat(viewModel.uiState.value.sheet).isEqualTo(AppBrowserSheet.None)
    }

    @Test
    fun onAction_startSearch_closesOverflowMenu() {
        val viewModel = AppBrowserViewModel(initialUrl = "https://linux.do")

        viewModel.onAction(AppBrowserAction.SetOverflowMenuVisible(true))
        viewModel.onAction(AppBrowserAction.StartSearch)

        assertThat(viewModel.uiState.value.search.isActive).isTrue()
        assertThat(viewModel.uiState.value.isOverflowMenuVisible).isFalse()
    }

    @Test
    fun onAction_startSearch_closesHistoryPopup() {
        val viewModel = AppBrowserViewModel(initialUrl = "https://linux.do")

        viewModel.onAction(AppBrowserAction.SetHistoryPopupVisible(true))
        viewModel.onAction(AppBrowserAction.StartSearch)

        assertThat(viewModel.uiState.value.search.isActive).isTrue()
        assertThat(viewModel.uiState.value.isHistoryPopupVisible).isFalse()
    }

    @Test
    fun startAddressEditing_closesOverflowMenu() {
        val viewModel = AppBrowserViewModel(initialUrl = "https://linux.do")

        viewModel.onAction(AppBrowserAction.SetOverflowMenuVisible(true))
        viewModel.startAddressEditing(
            currentUrl = "https://linux.do/t/123",
            recentEntries = emptyList(),
        )

        assertThat(viewModel.uiState.value.address.isEditing).isTrue()
        assertThat(viewModel.uiState.value.isOverflowMenuVisible).isFalse()
    }

    @Test
    fun startAddressEditing_closesHistoryPopup() {
        val viewModel = AppBrowserViewModel(initialUrl = "https://linux.do")

        viewModel.onAction(AppBrowserAction.SetHistoryPopupVisible(true))
        viewModel.startAddressEditing(
            currentUrl = "https://linux.do/t/123",
            recentEntries = emptyList(),
        )

        assertThat(viewModel.uiState.value.address.isEditing).isTrue()
        assertThat(viewModel.uiState.value.isHistoryPopupVisible).isFalse()
    }

    @Test
    fun onAction_setHistoryPopupVisible_closesOverflowMenu() {
        val viewModel = AppBrowserViewModel(initialUrl = "https://linux.do")

        viewModel.onAction(AppBrowserAction.SetOverflowMenuVisible(true))
        viewModel.onAction(AppBrowserAction.SetHistoryPopupVisible(true))

        assertThat(viewModel.uiState.value.isHistoryPopupVisible).isTrue()
        assertThat(viewModel.uiState.value.isOverflowMenuVisible).isFalse()
    }

    @Test
    fun setChrome_updatesChromeModel() {
        val viewModel = AppBrowserViewModel(initialUrl = "https://linux.do")

        viewModel.setChrome(
            AppBrowserChromeModel(
                title = "History",
                subtitle = "linux.do",
                currentUrl = "https://linux.do/history",
                isHttps = true,
                progress = 0.5f,
                isInSubPanel = true,
            ),
        )

        assertThat(viewModel.uiState.value.chrome.title).isEqualTo("History")
        assertThat(viewModel.uiState.value.chrome.progress).isEqualTo(0.5f)
        assertThat(viewModel.uiState.value.chrome.isInSubPanel).isTrue()
    }

    @Test
    fun onAction_minimizeBrowser_keepsCurrentUiStateStable() {
        val viewModel = AppBrowserViewModel(initialUrl = "https://linux.do")
        viewModel.onAction(AppBrowserAction.OpenHistory)

        viewModel.onAction(AppBrowserAction.MinimizeBrowser)

        assertThat(viewModel.uiState.value.sheet).isEqualTo(AppBrowserSheet.History)
    }

    @Test
    fun onAction_submitAddress_plainTextConvertsToSearchQueryAndStopsEditing() {
        val viewModel = AppBrowserViewModel(initialUrl = "https://linux.do")
        viewModel.startAddressEditing(
            currentUrl = "https://linux.do",
            recentEntries = emptyList(),
        )

        viewModel.onAction(AppBrowserAction.SubmitAddress("compose webview"))

        assertThat(viewModel.lastSubmittedAddress).contains("/search?q=compose%20webview")
        assertThat(viewModel.uiState.value.address.isEditing).isFalse()
    }

    @Test
    fun resolveAddressInput_withGoogleSearchEngine_usesGoogleSearchUrl() {
        val resolved = resolveAppBrowserAddressInput(
            value = "compose webview",
            baseUrl = "https://linux.do",
            searchEngine = AppBrowserSearchEngine.Google,
        )

        assertThat(resolved).isEqualTo("https://www.google.com/search?q=compose%20webview")
    }

    @Test
    fun updateAddressSuggestions_updatesRecentSearchesAndSuggestionSections() {
        val viewModel = AppBrowserViewModel(initialUrl = "https://linux.do")
        viewModel.startAddressEditing(
            currentUrl = "https://linux.do",
            recentEntries = emptyList(),
        )

        val suggestions = listOf(
            AppBrowserAddressSuggestionSection(
                kind = AppBrowserAddressSuggestionSectionKind.RecentSearches,
                items = listOf(
                    AppBrowserAddressSuggestion.RecentSearch(
                        query = "compose animation",
                        searchedAtMillis = 1L,
                    ),
                ),
            ),
        )
        val recentSearches = listOf(
            io.github.baba983.nativedo.core.storage.AppBrowserRecentSearchRecord(
                query = "compose animation",
                searchedAtMillis = 1L,
            ),
        )

        viewModel.updateAddressSuggestions(
            recentSearches = recentSearches,
            suggestionSections = suggestions,
        )

        assertThat(viewModel.uiState.value.address.recentSearches).isEqualTo(recentSearches)
        assertThat(viewModel.uiState.value.address.suggestionSections).isEqualTo(suggestions)
    }
}
