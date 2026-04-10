package io.github.baba983.nativedo.feature.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import io.github.baba983.nativedo.ui.AppTopBarRowMinHeight
import io.github.baba983.nativedo.ui.AppTopBarVerticalPadding

private val AppBrowserTopBarOverlayHeight = AppTopBarRowMinHeight + (AppTopBarVerticalPadding * 2) + 4.dp

@Composable
internal fun AppBrowserScreen(
    uiState: AppBrowserUiState,
    onAction: (AppBrowserAction) -> Unit,
    modifier: Modifier = Modifier,
    sheetContent: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = AppBrowserTopBarOverlayHeight),
        ) {
            content()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = AppBrowserTopBarOverlayHeight),
        ) {
            sheetContent()
        }
        AppBrowserTopBar(
            chrome = uiState.chrome,
            search = uiState.search,
            onAction = onAction,
        )
        AppBrowserAddressSheet(
            state = uiState.address,
            onAction = onAction,
        )
    }
}
