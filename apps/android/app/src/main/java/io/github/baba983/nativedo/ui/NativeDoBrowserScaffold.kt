package io.github.baba983.nativedo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val NativeDoBrowserTopBarOverlayHeight = AppTopBarRowMinHeight + (AppTopBarVerticalPadding * 2) + 4.dp

fun Modifier.nativeDoBrowserTopBarOffset(): Modifier {
    return statusBarsPadding().padding(top = NativeDoBrowserTopBarOverlayHeight)
}

@Composable
fun NativeDoBrowserScaffold(
    modifier: Modifier = Modifier,
    pageContent: @Composable BoxScope.() -> Unit,
    sheetContent: @Composable BoxScope.() -> Unit = {},
    overlayContent: @Composable BoxScope.() -> Unit = {},
    topBar: @Composable BoxScope.() -> Unit,
    popupAnchor: (@Composable BoxScope.() -> Unit)? = null,
) {
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nativeDoBrowserTopBarOffset(),
        ) {
            pageContent()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nativeDoBrowserTopBarOffset(),
        ) {
            sheetContent()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nativeDoBrowserTopBarOffset(),
        ) {
            overlayContent()
        }
        topBar()
        popupAnchor?.invoke(this)
    }
}
