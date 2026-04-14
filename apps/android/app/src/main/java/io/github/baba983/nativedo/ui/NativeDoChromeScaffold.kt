package io.github.baba983.nativedo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun NativeDoChromeScaffold(
    modifier: Modifier = Modifier,
    background: @Composable BoxScope.() -> Unit = {
        NativeDoBackground(modifier = Modifier.fillMaxSize())
    },
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable BoxScope.() -> Unit)? = null,
    bulletinHost: (@Composable BoxScope.() -> Unit)? = null,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        background()

        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                topBar?.invoke()
            },
        ) { paddingValues ->
            content(paddingValues)
        }

        bottomBar?.invoke(this)
        bulletinHost?.invoke(this)
        overlay?.invoke(this)
    }
}
