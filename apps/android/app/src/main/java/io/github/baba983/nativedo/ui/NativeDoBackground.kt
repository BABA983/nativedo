package io.github.baba983.nativedo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras

@Composable
fun NativeDoBackground(modifier: Modifier = Modifier) {
    val colors = NativeDoThemeExtras.colors

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    colors.backgroundElevated,
                    colors.backgroundBase,
                ),
            ),
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colors.glowPrimary, Color.Transparent),
                center = Offset(x = size.width * 0.2f, y = size.height * 0.12f),
                radius = size.minDimension * 0.55f,
            ),
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colors.glowSecondary, Color.Transparent),
                center = Offset(x = size.width * 0.88f, y = size.height * 0.82f),
                radius = size.minDimension * 0.48f,
            ),
        )
    }
}
