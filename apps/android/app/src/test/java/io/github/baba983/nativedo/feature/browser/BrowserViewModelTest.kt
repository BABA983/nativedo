package io.github.baba983.nativedo.feature.browser

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.storage.AppBrowserSearchEngine
import org.junit.Test

class BrowserViewModelTest {

    @Test
    fun onAction_updateSearchQuery_updatesUiState() {
        val viewModel = BrowserViewModel(initialUrl = "https://linux.do")

        viewModel.onAction(BrowserAction.UpdateSearchQuery("compose"))

        assertThat(viewModel.uiState.value.search.query).isEqualTo("compose")
        assertThat(viewModel.uiState.value.search.isCounting).isTrue()
    }

    @Test
    fun onAction_openHistory_switchesPanelAndStopsAddressEditing() {
        val viewModel = BrowserViewModel(initialUrl = "https://linux.do")
        viewModel.startAddressEditing(
            currentUrl = "https://linux.do/t/123",
            recentEntries = emptyList(),
        )

        viewModel.onAction(BrowserAction.OpenHistory)

        assertThat(viewModel.uiState.value.panel).isEqualTo(BrowserPanel.History)
        assertThat(viewModel.uiState.value.address.isEditing).isFalse()
    }

    @Test
    fun startAddressEditing_setsEditingStateWithEmptyDraftAndClosesSearch() {
        val viewModel = BrowserViewModel(initialUrl = "https://linux.do")

        viewModel.onAction(BrowserAction.UpdateSearchQuery("compose"))
        viewModel.onAction(BrowserAction.StartSearch)
        viewModel.startAddressEditing(
            currentUrl = "https://linux.do/t/123",
            recentEntries = listOf(
                BrowserHistoryRecord(
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
    fun onAction_submitAddress_plainTextConvertsToSearchQueryAndStopsEditing() {
        val viewModel = BrowserViewModel(initialUrl = "https://linux.do")
        viewModel.startAddressEditing(
            currentUrl = "https://linux.do",
            recentEntries = emptyList(),
        )

        viewModel.onAction(BrowserAction.SubmitAddress("compose webview"))

        assertThat(viewModel.lastSubmittedAddress).contains("/search?q=compose%20webview")
        assertThat(viewModel.uiState.value.address.isEditing).isFalse()
    }

    @Test
    fun resolveAddressInput_withGoogleSearchEngine_usesGoogleSearchUrl() {
        val resolved = resolveBrowserAddressInput(
            value = "compose webview",
            baseUrl = "https://linux.do",
            searchEngine = AppBrowserSearchEngine.Google,
        )

        assertThat(resolved).isEqualTo("https://www.google.com/search?q=compose%20webview")
    }
}
