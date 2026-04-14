package io.github.baba983.nativedo.core.ui.mediaviewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntRect
import java.util.IdentityHashMap

data class MediaViewerChromeState(
    val isVisible: Boolean = true,
    val isMenuExpanded: Boolean = false,
    val isAutoHideSuspended: Boolean = false,
)

data class MediaViewerLaunchSource(
    val boundsOnScreen: IntRect,
    val fullBoundsOnScreen: IntRect = boundsOnScreen,
    val cornerRadiusPx: Float = 0f,
)

class MediaViewerState {
    var activeRequest by mutableStateOf<MediaViewerRequest?>(null)
        private set
    var chromeState by mutableStateOf(MediaViewerChromeState())
        private set
    val isChromeVisible: Boolean
        get() = chromeState.isVisible

    fun open(
        items: List<MediaItem>,
        initialIndex: Int = 0,
        launchSource: MediaViewerLaunchSource? = null,
        itemLaunchSources: Map<MediaItem, MediaViewerLaunchSource> = emptyMap(),
    ) {
        chromeState = MediaViewerChromeState()
        activeRequest = MediaViewerRequest(
            items = items,
            initialIndex = initialIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0)),
            launchSource = launchSource,
            itemLaunchSources = itemLaunchSources,
        )
    }

    fun dismiss() {
        activeRequest = null
        chromeState = MediaViewerChromeState()
    }

    fun showChrome() {
        chromeState = chromeState.copy(isVisible = true)
    }

    fun hideChrome() {
        if (chromeState.isMenuExpanded) {
            return
        }
        chromeState = chromeState.copy(isVisible = false)
    }

    fun toggleChrome() {
        if (chromeState.isMenuExpanded) {
            setMenuExpanded(false)
            return
        }
        chromeState = chromeState.copy(isVisible = !chromeState.isVisible)
    }

    fun setMenuExpanded(expanded: Boolean) {
        chromeState = if (expanded) {
            chromeState.copy(
                isVisible = true,
                isMenuExpanded = true,
                isAutoHideSuspended = true,
            )
        } else {
            chromeState.copy(
                isMenuExpanded = false,
                isAutoHideSuspended = false,
            )
        }
    }

    fun setAutoHideSuspended(suspended: Boolean) {
        chromeState = chromeState.copy(
            isAutoHideSuspended = suspended || chromeState.isMenuExpanded,
        )
    }
}

data class MediaViewerRequest(
    val items: List<MediaItem>,
    val initialIndex: Int,
    val launchSource: MediaViewerLaunchSource? = null,
    val itemLaunchSources: Map<MediaItem, MediaViewerLaunchSource> = emptyMap(),
)

@Composable
fun rememberMediaViewerState(): MediaViewerState = remember { MediaViewerState() }
