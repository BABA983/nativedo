package io.github.baba983.nativedo.feature.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.baba983.nativedo.ui.NativeDoBrowserScaffold
import io.github.baba983.nativedo.ui.NativeDoBrowserTopBarOverlayHeight
import io.github.baba983.nativedo.ui.nativeDoBrowserTopBarOffset

internal val AppBrowserTopBarOverlayHeight = NativeDoBrowserTopBarOverlayHeight

internal fun Modifier.appBrowserTopBarOffset(): Modifier {
    return nativeDoBrowserTopBarOffset()
}

@Composable
internal fun AppBrowserScreen(
    uiState: AppBrowserUiState,
    onAction: (AppBrowserAction) -> Unit,
    modifier: Modifier = Modifier,
    sheetContent: @Composable BoxScope.() -> Unit = {},
    overlayContent: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    NativeDoBrowserScaffold(
        modifier = modifier,
        pageContent = content,
        sheetContent = sheetContent,
        overlayContent = overlayContent,
        topBar = {
            AppBrowserTopBar(
                chrome = uiState.chrome,
                search = uiState.search,
                address = uiState.address,
                onAction = onAction,
            )
        },
    )
}
