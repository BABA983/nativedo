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
    fun onAction_openHistory_switchesSheet() {
        val viewModel = AppBrowserViewModel(initialUrl = "https://linux.do")

        viewModel.onAction(AppBrowserAction.OpenHistory)

        assertThat(viewModel.uiState.value.sheet).isEqualTo(AppBrowserSheet.History)
    }

    @Test
    fun onAction_setHistoryPopupVisible_updatesFlag() {
        val viewModel = AppBrowserViewModel(initialUrl = "https://linux.do")

        viewModel.onAction(AppBrowserAction.SetHistoryPopupVisible(true))

        assertThat(viewModel.uiState.value.isHistoryPopupVisible).isTrue()
    }

    @Test
    fun setChrome_updatesChromeModel() {
        val viewModel = AppBrowserViewModel(initialUrl = "https://linux.do")

        viewModel.setChrome(
            AppBrowserChromeModel(
                title = "History",
                subtitle = "linux.do",
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
    fun onAction_submitAddress_plainTextConvertsToSearchQuery() {
        val viewModel = AppBrowserViewModel(initialUrl = "https://linux.do")

        viewModel.onAction(AppBrowserAction.SubmitAddress("compose webview"))

        assertThat(viewModel.lastSubmittedAddress).contains("/search?q=compose%20webview")
    }
}
