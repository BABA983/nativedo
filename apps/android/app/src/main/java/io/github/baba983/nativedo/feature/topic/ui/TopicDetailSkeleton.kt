package io.github.baba983.nativedo.feature.topic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.core.ui.shimmerLoading

private val SkeletonShape = RoundedCornerShape(4.dp)

internal data class TopicPostSkeletonTemplate(
    val bodyLines: List<Dp>,
    val reactionWidths: List<Dp>,
)

internal val TOPIC_DETAIL_SKELETON_POSTS: List<TopicPostSkeletonTemplate> = listOf(
    TopicPostSkeletonTemplate(
        bodyLines = listOf(280.dp, 240.dp, 300.dp, 180.dp),
        reactionWidths = listOf(36.dp, 36.dp, 36.dp),
    ),
    TopicPostSkeletonTemplate(
        bodyLines = listOf(260.dp, 200.dp),
        reactionWidths = listOf(36.dp),
    ),
    TopicPostSkeletonTemplate(
        bodyLines = listOf(290.dp, 270.dp, 220.dp),
        reactionWidths = emptyList(),
    ),
    TopicPostSkeletonTemplate(
        bodyLines = listOf(180.dp, 240.dp, 200.dp, 260.dp, 140.dp),
        reactionWidths = listOf(36.dp, 36.dp),
    ),
)

@Composable
internal fun TopicHeaderSkeleton(colors: TopicDetailColors) {
    val base = colors.divider
    val highlight = colors.secondary.copy(alpha = 0.18f)
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.rowBackground)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SkeletonBlock(width = 280.dp, height = 22.dp, base = base, highlight = highlight)
            SkeletonBlock(width = 220.dp, height = 22.dp, base = base, highlight = highlight)
            Spacer(modifier = Modifier.height(2.dp))
            SkeletonBlock(width = 180.dp, height = 12.dp, base = base, highlight = highlight)
            SkeletonBlock(width = 160.dp, height = 12.dp, base = base, highlight = highlight)
            SkeletonBlock(width = 120.dp, height = 12.dp, base = base, highlight = highlight)
        }
        HorizontalDivider(color = colors.divider)
    }
}

@Composable
internal fun TopicPostSkeleton(
    template: TopicPostSkeletonTemplate,
    colors: TopicDetailColors,
) {
    val base = colors.divider
    val highlight = colors.secondary.copy(alpha = 0.18f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.rowBackground)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonBlock(
                width = 40.dp,
                height = 40.dp,
                shape = CircleShape,
                base = base,
                highlight = highlight,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SkeletonBlock(width = 120.dp, height = 14.dp, base = base, highlight = highlight)
                SkeletonBlock(width = 90.dp, height = 10.dp, base = base, highlight = highlight)
            }
            SkeletonBlock(width = 36.dp, height = 12.dp, base = base, highlight = highlight)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            template.bodyLines.forEach { lineWidth ->
                SkeletonBlock(
                    width = lineWidth,
                    height = 13.dp,
                    base = base,
                    highlight = highlight,
                )
            }
        }
        if (template.reactionWidths.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                template.reactionWidths.forEach { reactionWidth ->
                    SkeletonBlock(
                        width = reactionWidth,
                        height = 22.dp,
                        shape = RoundedCornerShape(11.dp),
                        base = base,
                        highlight = highlight,
                    )
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = colors.divider,
        )
    }
}

@Composable
private fun SkeletonBlock(
    width: Dp,
    height: Dp,
    base: Color,
    highlight: Color,
    modifier: Modifier = Modifier,
    shape: Shape = SkeletonShape,
) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .shimmerLoading(baseColor = base, highlightColor = highlight, shape = shape),
    )
}
