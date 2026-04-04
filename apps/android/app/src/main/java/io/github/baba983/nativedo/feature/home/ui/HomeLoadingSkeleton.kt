package io.github.baba983.nativedo.feature.home.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.ui.theme.NativeDoExtendedColors
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras

internal const val HOME_SKELETON_ITEM_COUNT = 6

private const val HOME_SKELETON_HIGHLIGHT_TRAVEL = 900f
private const val HOME_SKELETON_ANIMATION_DURATION_MILLIS = 1400

internal data class HomeTopicSkeletonSpec(
    val titleWidthFraction: Float,
    val metaWidthFraction: Float,
    val showReplyBadge: Boolean,
)

internal data class HomeSkeletonPalette(
    val base: Color,
    val highlight: Color,
)

internal fun homeTopicSkeletonSpec(index: Int): HomeTopicSkeletonSpec = when (index % 6) {
    0 -> HomeTopicSkeletonSpec(
        titleWidthFraction = 0.82f,
        metaWidthFraction = 0.38f,
        showReplyBadge = true,
    )

    1 -> HomeTopicSkeletonSpec(
        titleWidthFraction = 0.68f,
        metaWidthFraction = 0.46f,
        showReplyBadge = false,
    )

    2 -> HomeTopicSkeletonSpec(
        titleWidthFraction = 0.9f,
        metaWidthFraction = 0.42f,
        showReplyBadge = true,
    )

    3 -> HomeTopicSkeletonSpec(
        titleWidthFraction = 0.74f,
        metaWidthFraction = 0.5f,
        showReplyBadge = false,
    )

    4 -> HomeTopicSkeletonSpec(
        titleWidthFraction = 0.62f,
        metaWidthFraction = 0.34f,
        showReplyBadge = false,
    )

    else -> HomeTopicSkeletonSpec(
        titleWidthFraction = 0.79f,
        metaWidthFraction = 0.44f,
        showReplyBadge = true,
    )
}

@Composable
internal fun HomeTopicSkeletonItem(
    index: Int,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    val spec = homeTopicSkeletonSpec(index)
    val shimmerBrush = rememberHomeSkeletonBrush()

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            HomeSkeletonBlock(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                brush = shimmerBrush,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HomeSkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(spec.titleWidthFraction)
                        .height(20.dp),
                    shape = RoundedCornerShape(10.dp),
                    brush = shimmerBrush,
                )
                HomeSkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(spec.metaWidthFraction)
                        .height(12.dp),
                    shape = RoundedCornerShape(8.dp),
                    brush = shimmerBrush,
                )
            }

            if (spec.showReplyBadge) {
                HomeSkeletonBlock(
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .width(28.dp)
                        .height(18.dp),
                    shape = RoundedCornerShape(9.dp),
                    brush = shimmerBrush,
                )
            }
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun rememberHomeSkeletonBrush(): Brush {
    val colors = NativeDoThemeExtras.colors
    val palette = homeSkeletonPalette(colors)
    val transition = rememberInfiniteTransition(label = "home_skeleton_transition")
    val highlightOffset by transition.animateFloat(
        initialValue = -HOME_SKELETON_HIGHLIGHT_TRAVEL,
        targetValue = HOME_SKELETON_HIGHLIGHT_TRAVEL,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = HOME_SKELETON_ANIMATION_DURATION_MILLIS,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "home_skeleton_highlight_offset",
    )

    return Brush.linearGradient(
        colors = listOf(
            palette.base,
            palette.highlight,
            palette.base,
        ),
        start = Offset(x = highlightOffset - 220f, y = 0f),
        end = Offset(x = highlightOffset + 220f, y = 220f),
    )
}

internal fun homeSkeletonPalette(colors: NativeDoExtendedColors): HomeSkeletonPalette {
    return HomeSkeletonPalette(
        base = lerp(
            start = colors.backgroundElevated,
            stop = colors.textSecondary,
            fraction = 0.08f,
        ).copy(alpha = 0.94f),
        highlight = lerp(
            start = colors.backgroundElevated,
            stop = colors.textSecondary,
            fraction = 0.18f,
        ).copy(alpha = 0.72f),
    )
}

@Composable
private fun HomeSkeletonBlock(
    modifier: Modifier,
    shape: Shape,
    brush: Brush,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush),
    )
}
