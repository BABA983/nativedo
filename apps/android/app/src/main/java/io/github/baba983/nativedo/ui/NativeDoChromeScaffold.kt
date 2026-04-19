package io.github.baba983.nativedo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.ui.theme.NativeDoEasing

@Composable
fun NativeDoChromeScaffold(
    modifier: Modifier = Modifier,
    background: @Composable BoxScope.() -> Unit = {
        NativeDoBackground(modifier = Modifier.fillMaxSize())
    },
    topBar: (@Composable () -> Unit)? = null,
    bottomInsetHeight: Dp = 0.dp,
    bottomBar: (@Composable BoxScope.() -> Unit)? = null,
    bottomDock: (@Composable BoxScope.() -> Unit)? = null,
    bulletinHost: (@Composable BoxScope.() -> Unit)? = null,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val animatedBottomInsetHeight = animateDpAsState(
        targetValue = bottomInsetHeight,
        animationSpec = tween(
            durationMillis = 220,
            easing = NativeDoEasing.EaseOutQuint,
        ),
        label = "chromeBottomInsetHeight",
    )

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
            content(paddingValues.withAdditionalBottom(animatedBottomInsetHeight.value))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = animatedBottomInsetHeight.value),
        ) {
            bottomBar?.invoke(this)
        }
        bottomDock?.invoke(this)
        overlay?.invoke(this)
        bulletinHost?.invoke(this)
    }
}

private fun PaddingValues.withAdditionalBottom(
    additionalBottom: Dp,
): PaddingValues {
    if (additionalBottom == 0.dp) {
        return this
    }
    val original = this
    return object : PaddingValues {
        override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
            original.calculateLeftPadding(layoutDirection)

        override fun calculateTopPadding(): Dp = original.calculateTopPadding()

        override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
            original.calculateRightPadding(layoutDirection)

        override fun calculateBottomPadding(): Dp =
            original.calculateBottomPadding() + additionalBottom
    }
}
