package io.github.baba983.nativedo.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.draw.clip

private const val SHIMMER_PERIOD_MS = 1200

private val ShimmerStops: List<Float> = listOf(0f, 0.5f, 1f)

/**
 * Paints the receiver with a horizontally-traveling shimmer brush, intended for skeleton
 * placeholders. The base/highlight colors should come from the active theme so dark mode
 * keeps working without code changes.
 */
fun Modifier.shimmerLoading(
    baseColor: Color,
    highlightColor: Color,
    shape: Shape = RectangleShape,
): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SHIMMER_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerTranslate",
    )

    val width = size.width.toFloat().coerceAtLeast(1f)
    val sweepWidth = width * 1.5f
    val startX = -sweepWidth + translate * (width + sweepWidth * 2f)

    val brush = Brush.linearGradient(
        colorStops = arrayOf(
            ShimmerStops[0] to baseColor,
            ShimmerStops[1] to highlightColor,
            ShimmerStops[2] to baseColor,
        ),
        start = Offset(startX, 0f),
        end = Offset(startX + sweepWidth, 0f),
    )

    this
        .clip(shape)
        .onGloballyPositioned { coordinates ->
            if (coordinates.size != size) {
                size = coordinates.size
            }
        }
        .background(brush = brush, shape = shape)
}

/**
 * Default base/highlight pair for skeletons in this project. Both colors come from the
 * Telegram-derived theme so the placeholder follows light/dark switches automatically.
 */
data class SkeletonPalette(
    val base: Color,
    val highlight: Color,
)
