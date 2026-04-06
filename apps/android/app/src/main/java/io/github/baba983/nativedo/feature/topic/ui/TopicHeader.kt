package io.github.baba983.nativedo.feature.topic.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.core.ui.compactCount
import io.github.baba983.nativedo.ui.theme.NativeDoInnerCardShape
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val topicHeaderDateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

@Composable
internal fun TopicHeader(
    title: String,
    createdAt: String,
    lastPostedAt: String,
    viewCount: Int,
    tags: List<String>,
    totalPosts: Int,
    loadedPosts: Int,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val tagLine = topicTagLine(tags)
    val dateLine = topicHeaderDateLine(
        createdAt = createdAt,
        lastPostedAt = lastPostedAt,
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = NativeDoInnerCardShape,
        color = colors.backgroundElevated.copy(alpha = 0.9f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (tagLine.isNotBlank()) {
                Text(
                    text = tagLine,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (dateLine.isNotBlank()) {
                Text(
                    text = dateLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TopicHeaderStatsRow(
                viewCount = viewCount,
                totalPosts = totalPosts,
                loadedPosts = loadedPosts,
            )
        }
    }
}

@Composable
private fun TopicHeaderStatsRow(
    viewCount: Int,
    totalPosts: Int,
    loadedPosts: Int,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (viewCount > 0) {
            TopicHeaderStat(
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = colors.textSecondary,
                    )
                },
                text = topicHeaderCompactCount(viewCount),
                color = colors.textSecondary,
            )
        }
        TopicHeaderStat(
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Reply,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            text = topicHeaderCompactCount(if (totalPosts > 0) totalPosts else loadedPosts),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun TopicHeaderStat(
    icon: @Composable () -> Unit,
    text: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
        )
    }
}

internal fun topicTagLine(tags: List<String>): String = tags
    .filter(String::isNotBlank)
    .joinToString(separator = "  ") { "#$it" }

internal fun topicHeaderDateLine(
    createdAt: String,
    lastPostedAt: String,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String = buildList {
    topicHeaderDateLabel(
        label = "Started",
        rawValue = createdAt,
        zoneId = zoneId,
    )?.let(::add)
    topicHeaderDateLabel(
        label = "Active",
        rawValue = lastPostedAt,
        zoneId = zoneId,
    )?.let(::add)
}.joinToString(separator = " · ")

internal fun topicHeaderStatsLine(
    viewCount: Int,
    totalPosts: Int,
    loadedPosts: Int,
): String = buildList {
    if (viewCount > 0) {
        add("${topicHeaderCompactCount(viewCount)} views")
    }
    add("${topicHeaderCompactCount(if (totalPosts > 0) totalPosts else loadedPosts)} posts")
}.joinToString(separator = " · ")

internal fun topicHeaderCompactCount(count: Int): String = compactCount(count)

private fun topicHeaderDateLabel(
    label: String,
    rawValue: String,
    zoneId: ZoneId,
): String? {
    if (rawValue.isBlank()) return null

    val dateLabel = runCatching {
        OffsetDateTime.parse(rawValue)
            .atZoneSameInstant(zoneId)
            .format(topicHeaderDateFormatter)
    }.getOrNull() ?: return null

    return "$label $dateLabel"
}
