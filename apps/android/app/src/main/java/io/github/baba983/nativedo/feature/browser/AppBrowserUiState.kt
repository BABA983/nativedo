package io.github.baba983.nativedo.feature.browser

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal enum class AppBrowserSheet {
    None,
    History,
    Settings,
}

internal enum class AppBrowserChromeScene {
    Browse,
    Search,
    AddressEditing,
    HistoryPopup,
    OverflowMenu,
    Subpanel,
}

internal data class AppBrowserSearchUiState(
    val isActive: Boolean = false,
    val query: String = "",
    val activeMatchOrdinal: Int = 0,
    val numberOfMatches: Int = 0,
)

internal data class AppBrowserAddressUiState(
    val isEditing: Boolean = false,
    val draft: String = "",
    val recentEntries: List<AppBrowserHistoryRecord> = emptyList(),
)

internal data class AppBrowserChromeModel(
    val scene: AppBrowserChromeScene = AppBrowserChromeScene.Browse,
    val title: String = "",
    val subtitle: String? = null,
    val currentUrl: String = "",
    val isHttps: Boolean = false,
    val favicon: android.graphics.Bitmap? = null,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val progress: Float = 0f,
    val scrollProgress: Float = 0f,
    val isLoadingProgressVisible: Boolean = false,
    val backHistoryEntries: List<AppBrowserHistoryEntry> = emptyList(),
    val browserHistoryEntries: List<AppBrowserHistoryEntry> = emptyList(),
    val isInSubPanel: Boolean = false,
    val alpha: Float = 0f,
    val translationX: Float = 0f,
    val isHistoryPopupVisible: Boolean = false,
    val isOverflowMenuVisible: Boolean = false,
)

internal data class AppBrowserUiState(
    val chrome: AppBrowserChromeModel = AppBrowserChromeModel(),
    val search: AppBrowserSearchUiState = AppBrowserSearchUiState(),
    val address: AppBrowserAddressUiState = AppBrowserAddressUiState(),
    val sheet: AppBrowserSheet = AppBrowserSheet.None,
    val isHistoryPopupVisible: Boolean = false,
    val isOverflowMenuVisible: Boolean = false,
) {
    val chromeScene: AppBrowserChromeScene
        get() = resolveAppBrowserChromeScene(
            isSearching = search.isActive,
            isAddressEditing = address.isEditing,
            isHistoryPopupVisible = isHistoryPopupVisible,
            isOverflowMenuVisible = isOverflowMenuVisible,
            isInSubPanel = sheet != AppBrowserSheet.None || chrome.isInSubPanel,
        )
}

internal fun resolveAppBrowserChromeScene(
    isSearching: Boolean,
    isAddressEditing: Boolean,
    isHistoryPopupVisible: Boolean,
    isOverflowMenuVisible: Boolean,
    isInSubPanel: Boolean,
): AppBrowserChromeScene {
    return when {
        isAddressEditing -> AppBrowserChromeScene.AddressEditing
        isSearching -> AppBrowserChromeScene.Search
        isInSubPanel -> AppBrowserChromeScene.Subpanel
        isHistoryPopupVisible -> AppBrowserChromeScene.HistoryPopup
        isOverflowMenuVisible -> AppBrowserChromeScene.OverflowMenu
        else -> AppBrowserChromeScene.Browse
    }
}

internal fun initialAppBrowserUiState(
    initialUrl: String,
): AppBrowserUiState {
    val httpUrl = initialUrl.toHttpUrlOrNull()
    return AppBrowserUiState(
        chrome = AppBrowserChromeModel(
            title = httpUrl?.host ?: initialUrl,
            subtitle = null,
            currentUrl = initialUrl,
            isHttps = httpUrl?.isHttps == true,
        ),
    )
}
