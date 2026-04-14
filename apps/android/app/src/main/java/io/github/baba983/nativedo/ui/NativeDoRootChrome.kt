package io.github.baba983.nativedo.ui

import androidx.compose.runtime.Immutable
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.session.SessionSnapshot
import io.github.baba983.nativedo.navigation.RootDestination

internal enum class NativeDoRootChromeScene {
    Normal,
    Search,
    ActionMode,
    ContextualOptions,
}

@Immutable
internal data class NativeDoRootChromeModel(
    val scene: NativeDoRootChromeScene = NativeDoRootChromeScene.Normal,
    val actionModeSelectionCount: Int = 0,
    val reserveHomeSearchSlot: Boolean = false,
    val showHomeSearchAction: Boolean = false,
    val showContextualOptionsAction: Boolean = false,
    val isContextualOptionsExpanded: Boolean = false,
)

internal fun buildNativeDoRootChromeModel(
    isRootRoute: Boolean,
    activeRoute: String?,
    homeHeaderSearchVisible: Boolean,
    isHomeSearchMode: Boolean,
    actionModeSelectionCount: Int,
    isContextualOptionsExpanded: Boolean,
): NativeDoRootChromeModel {
    val scene = resolveNativeDoRootChromeScene(
        isSearchMode = isHomeSearchMode,
        actionModeSelectionCount = actionModeSelectionCount,
        isContextualOptionsExpanded = isContextualOptionsExpanded,
    )
    val isHomeRootRoute = isRootRoute && activeRoute == RootDestination.Home.route
    return NativeDoRootChromeModel(
        scene = scene,
        actionModeSelectionCount = actionModeSelectionCount,
        reserveHomeSearchSlot = isHomeRootRoute && actionModeSelectionCount == 0,
        showHomeSearchAction = isHomeRootRoute &&
            homeHeaderSearchVisible &&
            scene == NativeDoRootChromeScene.Normal,
        showContextualOptionsAction = isRootRoute && scene != NativeDoRootChromeScene.Search,
        isContextualOptionsExpanded = isContextualOptionsExpanded,
    )
}

internal fun resolveNativeDoRootChromeScene(
    isSearchMode: Boolean,
    actionModeSelectionCount: Int,
    isContextualOptionsExpanded: Boolean,
): NativeDoRootChromeScene {
    return when {
        isContextualOptionsExpanded -> NativeDoRootChromeScene.ContextualOptions
        actionModeSelectionCount > 0 -> NativeDoRootChromeScene.ActionMode
        isSearchMode -> NativeDoRootChromeScene.Search
        else -> NativeDoRootChromeScene.Normal
    }
}

internal fun resolveNativeDoRootChromeTitle(
    defaultTitle: String,
    actionModeSelectionCount: Int,
): String {
    return if (actionModeSelectionCount > 0) {
        "$actionModeSelectionCount selected"
    } else {
        defaultTitle
    }
}

data class NativeDoChromeState(
    val title: String = "",
) {
    companion object {
        fun from(
            sessionState: AppSessionState,
            cachedSnapshot: SessionSnapshot? = null,
        ): NativeDoChromeState {
            val snapshot = sessionSnapshotForChrome(sessionState, cachedSnapshot)
            return if (snapshot == null) {
                NativeDoChromeState()
            } else {
                NativeDoChromeState(title = snapshot.chromeTitle)
            }
        }
    }
}
