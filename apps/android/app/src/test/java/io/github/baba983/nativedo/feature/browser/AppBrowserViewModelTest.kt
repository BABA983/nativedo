package io.github.baba983.nativedo.feature.browser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppBrowserViewModelTest {

    @Test
    fun onAction_updateSearchQuery_updatesUiState() {
        val viewModel = AppBrowserViewModel(initialUrl = "https://linux.do")

        viewModel.onAction(AppBrowserAction.UpdateSearchQuery("compose"))

        assertThat(viewModel.uiState.value.search.query).isEqualTo("compose")
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
    fun onAction_setHistoryPopupVisible_updatesFlag() {
        val viewModel = AppBrowserViewModel(initialUrl = "https://linux.do")

        viewModel.onAction(AppBrowserAction.SetHistoryPopupVisible(true))

        assertThat(viewModel.uiState.value.isHistoryPopupVisible).isTrue()
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
}
