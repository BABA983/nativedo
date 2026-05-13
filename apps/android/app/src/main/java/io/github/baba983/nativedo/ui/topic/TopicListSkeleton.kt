package io.github.baba983.nativedo.ui.topic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.core.ui.shimmerLoading

private val SkeletonRowShape = RoundedCornerShape(4.dp)

@Composable
internal fun TopicListSkeletonRow(
    colors: TopicListColors,
    showDivider: Boolean,
    secondLineWidth: Dp,
    metaWidth: Dp,
    showSecondLine: Boolean,
) {
    val base = colors.divider
    val highlight = colors.secondary.copy(alpha = 0.18f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.rowBackground),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            SkeletonBlock(
                width = 36.dp,
                height = 36.dp,
                shape = CircleShape,
                base = base,
                highlight = highlight,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    SkeletonBlock(
                        width = null,
                        height = 14.dp,
                        base = base,
                        highlight = highlight,
                        modifier = Modifier.weight(1f),
                    )
                    SkeletonBlock(
                        width = 28.dp,
                        height = 10.dp,
                        base = base,
                        highlight = highlight,
                    )
                }
                if (showSecondLine) {
                    SkeletonBlock(
                        width = secondLineWidth,
                        height = 14.dp,
                        base = base,
                        highlight = highlight,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkeletonBlock(
                        width = 56.dp,
                        height = 16.dp,
                        shape = CircleShape,
                        base = base,
                        highlight = highlight,
                    )
                    SkeletonBlock(
                        width = metaWidth,
                        height = 10.dp,
                        base = base,
                        highlight = highlight,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkeletonBlock(
                        width = 90.dp,
                        height = 10.dp,
                        base = base,
                        highlight = highlight,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    SkeletonBlock(
                        width = 22.dp,
                        height = 10.dp,
                        base = base,
                        highlight = highlight,
                    )
                    SkeletonBlock(
                        width = 22.dp,
                        height = 10.dp,
                        base = base,
                        highlight = highlight,
                    )
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(color = colors.divider)
        }
    }
}

@Composable
private fun SkeletonBlock(
    width: Dp?,
    height: Dp,
    base: Color,
    highlight: Color,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = SkeletonRowShape,
) {
    val sized = if (width != null) modifier.then(Modifier.width(width)) else modifier
    Box(
        modifier = sized
            .height(height)
            .shimmerLoading(baseColor = base, highlightColor = highlight, shape = shape),
    )
}
