package io.github.baba983.nativedo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val NativeDoBrowserTopBarOverlayHeight = AppTopBarRowMinHeight + (AppTopBarVerticalPadding * 2)

fun Modifier.nativeDoBrowserTopBarOffset(
    topBarOverlayHeight: Dp = NativeDoBrowserTopBarOverlayHeight,
    topInset: Dp = 0.dp,
): Modifier {
    return padding(top = topInset + topBarOverlayHeight)
}

@Composable
fun NativeDoBrowserScaffold(
    modifier: Modifier = Modifier,
    sceneModifier: Modifier = Modifier,
    topBarOverlayHeight: Dp = NativeDoBrowserTopBarOverlayHeight,
    topInset: Dp = 0.dp,
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
                .nativeDoBrowserTopBarOffset(
                    topBarOverlayHeight = topBarOverlayHeight,
                    topInset = topInset,
                )
                .then(sceneModifier),
        ) {
            pageContent()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nativeDoBrowserTopBarOffset(
                    topBarOverlayHeight = topBarOverlayHeight,
                    topInset = topInset,
                )
                .then(sceneModifier),
        ) {
            sheetContent()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nativeDoBrowserTopBarOffset(
                    topBarOverlayHeight = topBarOverlayHeight,
                    topInset = topInset,
                )
                .then(sceneModifier),
        ) {
            overlayContent()
        }
        topBar()
        popupAnchor?.invoke(this)
    }
}
