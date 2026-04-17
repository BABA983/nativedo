package io.github.baba983.nativedo.feature.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.lerp
import io.github.baba983.nativedo.ui.NativeDoBrowserScaffold
import io.github.baba983.nativedo.ui.NativeDoBrowserTopBarOverlayHeight
import io.github.baba983.nativedo.ui.nativeDoBrowserTopBarOffset
import io.github.baba983.nativedo.ui.AppTopBarRowMinHeight

internal val AppBrowserTopBarOverlayHeight = NativeDoBrowserTopBarOverlayHeight
internal val AppBrowserCollapsedTopBarVerticalPadding = 1.dp  // 从 2dp 减小到 1dp
internal val AppBrowserCollapsedTopBarOverlayHeight =
    AppTopBarRowMinHeight + (AppBrowserCollapsedTopBarVerticalPadding * 2)

internal fun Modifier.appBrowserTopBarOffset(
    topInset: Dp = 0.dp,
): Modifier {
    return nativeDoBrowserTopBarOffset(topInset = topInset)
}

@Composable
internal fun AppBrowserScreen(
    uiState: AppBrowserUiState,
    onAction: (AppBrowserAction) -> Unit,
    modifier: Modifier = Modifier,
    sceneModifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    chrome: AppBrowserChromeModel = uiState.chrome,
    sheetContent: @Composable BoxScope.() -> Unit = {},
    overlayContent: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val topBarOverlayHeight = remember(chrome.headerCollapseProgress) {
        lerp(
            start = AppBrowserTopBarOverlayHeight,
            stop = AppBrowserCollapsedTopBarOverlayHeight,
            fraction = chrome.headerCollapseProgress.coerceIn(0f, 1f),
        )
    }
    NativeDoBrowserScaffold(
        modifier = modifier,
        sceneModifier = sceneModifier,
        topBarOverlayHeight = topBarOverlayHeight,
        topInset = topInset,
        pageContent = content,
        sheetContent = sheetContent,
        overlayContent = overlayContent,
        topBar = {
            AppBrowserTopBar(
                chrome = chrome,
                search = uiState.search,
                address = uiState.address,
                topInset = topInset,
                onAction = onAction,
            )
        },
    )
}
