package io.github.baba983.nativedo.core.ui.mediaviewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntRect
import java.util.IdentityHashMap

data class MediaViewerLaunchSource(
    val boundsOnScreen: IntRect,
    val fullBoundsOnScreen: IntRect = boundsOnScreen,
    val cornerRadiusPx: Float = 0f,
)

class MediaViewerState {
    var activeRequest by mutableStateOf<MediaViewerRequest?>(null)
        private set
    var isChromeVisible by mutableStateOf(true)
        private set

    fun open(
        items: List<MediaItem>,
        initialIndex: Int = 0,
        launchSource: MediaViewerLaunchSource? = null,
        itemLaunchSources: Map<MediaItem, MediaViewerLaunchSource> = emptyMap(),
    ) {
        isChromeVisible = true
        activeRequest = MediaViewerRequest(
            items = items,
            initialIndex = initialIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0)),
            launchSource = launchSource,
            itemLaunchSources = itemLaunchSources,
        )
    }

    fun dismiss() {
        activeRequest = null
        isChromeVisible = true
    }

    fun showChrome() {
        isChromeVisible = true
    }

    fun hideChrome() {
        isChromeVisible = false
    }

    fun toggleChrome() {
        isChromeVisible = !isChromeVisible
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
