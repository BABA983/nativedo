package io.github.baba983.nativedo.feature.browser

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal data class BrowserUiState(
    val chrome: BrowserChromeModel = BrowserChromeModel(),
    val search: BrowserSearchUiState = BrowserSearchUiState(),
    val address: BrowserAddressUiState = BrowserAddressUiState(),
    val panel: BrowserPanel = BrowserPanel.None,
    val isHistoryPopupVisible: Boolean = false,
    val isOverflowMenuVisible: Boolean = false,
) {
    val chromeScene: BrowserChromeScene
        get() = resolveBrowserChromeScene(
            isSearching = search.isActive,
            isAddressEditing = address.isEditing,
            isHistoryPopupVisible = isHistoryPopupVisible,
            isOverflowMenuVisible = isOverflowMenuVisible,
            isInSubPanel = panel != BrowserPanel.None || chrome.isInSubPanel,
        )
}

internal fun resolveBrowserChromeScene(
    isSearching: Boolean,
    isAddressEditing: Boolean,
    isHistoryPopupVisible: Boolean,
    isOverflowMenuVisible: Boolean,
    isInSubPanel: Boolean,
): BrowserChromeScene {
    return when {
        isAddressEditing -> BrowserChromeScene.AddressEditing
        isSearching -> BrowserChromeScene.Search
        isInSubPanel -> BrowserChromeScene.Subpanel
        isHistoryPopupVisible -> BrowserChromeScene.HistoryPopup
        isOverflowMenuVisible -> BrowserChromeScene.OverflowMenu
        else -> BrowserChromeScene.Browse
    }
}

internal fun initialBrowserUiState(
    initialUrl: String,
): BrowserUiState {
    val httpUrl = initialUrl.toHttpUrlOrNull()
    return BrowserUiState(
        chrome = BrowserChromeModel(
            title = httpUrl?.host ?: initialUrl,
            subtitle = null,
            currentUrl = initialUrl,
            isHttps = httpUrl?.isHttps == true,
        ),
    )
}
