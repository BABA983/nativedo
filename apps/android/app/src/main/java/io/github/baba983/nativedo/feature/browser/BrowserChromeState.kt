package io.github.baba983.nativedo.feature.browser

import androidx.compose.ui.graphics.Color
import io.github.baba983.nativedo.core.storage.AppBrowserRecentSearchRecord

internal enum class BrowserChromeScene {
    Browse,
    Search,
    AddressEditing,
    HistoryPopup,
    OverflowMenu,
    Subpanel,
}

internal enum class BrowserPanel {
    None,
    History,
    Bookmarks,
    Settings,
}

internal enum class BrowserInstantViewActionAvailability {
    Hidden,
    Disabled,
    Enabled,
}

internal enum class BrowserInstantViewPhase {
    Hidden,
    Probing,
    Ready,
    Unsupported,
}

internal data class BrowserInstantViewUiState(
    val phase: BrowserInstantViewPhase = BrowserInstantViewPhase.Hidden,
    val actionAvailability: BrowserInstantViewActionAvailability = BrowserInstantViewActionAvailability.Hidden,
    val isPendingOpen: Boolean = false,
)

internal data class BrowserSearchUiState(
    val isActive: Boolean = false,
    val query: String = "",
    val activeMatchOrdinal: Int = 0,
    val numberOfMatches: Int = 0,
    val isCounting: Boolean = false,
)

internal data class BrowserAddressUiState(
    val isEditing: Boolean = false,
    val draft: String = "",
    val recentEntries: List<BrowserHistoryRecord> = emptyList(),
    val recentSearches: List<AppBrowserRecentSearchRecord> = emptyList(),
    val suggestionSections: List<BrowserAddressSuggestionSection> = emptyList(),
)

internal data class BrowserChromeModel(
    val scene: BrowserChromeScene = BrowserChromeScene.Browse,
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
    val instantView: BrowserInstantViewUiState = BrowserInstantViewUiState(),
    val chromeColor: Color = Color.Unspecified,
    val isBookmarked: Boolean = false,
    val backHistoryEntries: List<BrowserHistoryEntry> = emptyList(),
    val browserHistoryEntries: List<BrowserHistoryEntry> = emptyList(),
    val isInSubPanel: Boolean = false,
    val alpha: Float = 0f,
    val translationX: Float = 0f,
    val headerCollapseProgress: Float = 0f,
    val isHistoryPopupVisible: Boolean = false,
    val isOverflowMenuVisible: Boolean = false,
)

internal const val BrowserChromeDarkThreshold: Float = 0.721f

internal fun computePerceivedBrightness(color: Color): Float {
    return (color.red * 0.2126f + color.green * 0.7152f + color.blue * 0.0722f)
}

internal fun isBrowserChromeDark(color: Color): Boolean {
    return computePerceivedBrightness(color) <= BrowserChromeDarkThreshold
}
