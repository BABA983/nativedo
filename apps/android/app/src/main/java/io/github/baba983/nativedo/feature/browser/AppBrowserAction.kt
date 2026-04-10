package io.github.baba983.nativedo.feature.browser

internal sealed interface AppBrowserAction {
    data object NavigateBack : AppBrowserAction
    data object CloseBrowser : AppBrowserAction
    data object OpenAddressSheet : AppBrowserAction
    data object OpenHistory : AppBrowserAction
    data object OpenSettings : AppBrowserAction
    data object DismissSheet : AppBrowserAction
    data object CloseSheet : AppBrowserAction
    data class OpenHistoryEntry(val entry: AppBrowserHistoryEntry) : AppBrowserAction
    data object StartSearch : AppBrowserAction
    data object CloseSearch : AppBrowserAction
    data class UpdateAddressDraft(val value: String) : AppBrowserAction
    data class SubmitAddress(val value: String) : AppBrowserAction
    data class UpdateSearchQuery(val query: String) : AppBrowserAction
    data class UpdateSearchMatches(
        val activeMatchOrdinal: Int,
        val numberOfMatches: Int,
    ) : AppBrowserAction
    data object GoForward : AppBrowserAction
    data object Refresh : AppBrowserAction
    data object Share : AppBrowserAction
    data object OpenInBrowser : AppBrowserAction
    data object CopyLink : AppBrowserAction
    data class SetHistoryPopupVisible(val visible: Boolean) : AppBrowserAction
}
