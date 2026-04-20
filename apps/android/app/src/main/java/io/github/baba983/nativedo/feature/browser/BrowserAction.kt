package io.github.baba983.nativedo.feature.browser

internal sealed interface BrowserAction {
    data object NavigateBack : BrowserAction
    data object MinimizeBrowser : BrowserAction
    data object CloseBrowser : BrowserAction
    data object StartAddressEditing : BrowserAction
    data object CancelAddressEditing : BrowserAction
    data object OpenAddressSheet : BrowserAction
    data object OpenHistory : BrowserAction
    data object OpenBookmarks : BrowserAction
    data object OpenSettings : BrowserAction
    data object DismissPanel : BrowserAction
    data object ClosePanel : BrowserAction
    data class OpenHistoryEntry(val entry: BrowserHistoryEntry) : BrowserAction
    data class ApplyAddressSuggestion(val suggestion: BrowserAddressSuggestion) : BrowserAction
    data object StartSearch : BrowserAction
    data object CloseSearch : BrowserAction
    data class UpdateAddressDraft(val value: String) : BrowserAction
    data class SubmitAddress(val value: String) : BrowserAction
    data class UpdateSearchQuery(val query: String) : BrowserAction
    data class UpdateSearchMatches(
        val activeMatchOrdinal: Int,
        val numberOfMatches: Int,
    ) : BrowserAction
    data object GoForward : BrowserAction
    data object Refresh : BrowserAction
    data object Share : BrowserAction
    data object OpenInstantView : BrowserAction
    data object OpenInBrowser : BrowserAction
    data object CopyLink : BrowserAction
    data object ToggleBookmark : BrowserAction
    data class SetHistoryPopupVisible(val visible: Boolean) : BrowserAction
    data class SetOverflowMenuVisible(val visible: Boolean) : BrowserAction
}
