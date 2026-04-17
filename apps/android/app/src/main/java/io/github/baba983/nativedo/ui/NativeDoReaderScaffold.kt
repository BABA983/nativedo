package io.github.baba983.nativedo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras

@Composable
fun NativeDoReaderScaffold(
    modifier: Modifier = Modifier,
    topBar: (@Composable () -> Unit)? = null,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    NativeDoChromeScaffold(
        modifier = modifier,
        background = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NativeDoThemeExtras.colors.backgroundBase),
            )
        },
        topBar = topBar,
        overlay = overlay,
        content = content,
    )
}
