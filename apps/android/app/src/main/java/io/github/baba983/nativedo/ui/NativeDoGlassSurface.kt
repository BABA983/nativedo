package io.github.baba983.nativedo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.ui.theme.NativeDoCardShape
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras

@Composable
fun NativeDoGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = NativeDoCardShape,
    strong: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = NativeDoThemeExtras.colors

    Surface(
        modifier = modifier,
        shape = shape,
        color = if (strong) colors.glassBackgroundStrong else colors.glassBackground,
        border = BorderStroke(
            width = 1.dp,
            color = if (strong) colors.glassStrokeStrong else colors.glassStroke,
        ),
        shadowElevation = if (strong) 10.dp else 4.dp,
        tonalElevation = 0.dp,
        content = content,
    )
}
