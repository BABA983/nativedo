package io.github.baba983.nativedo.feature.topic.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.ui.theme.NativeDoInnerCardShape
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

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
    val statsLine = topicHeaderStatsLine(
        viewCount = viewCount,
        totalPosts = totalPosts,
        loadedPosts = loadedPosts,
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
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
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (dateLine.isNotBlank()) {
                    Text(
                        text = dateLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = statsLine,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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

internal fun topicHeaderCompactCount(count: Int): String = when {
    count < 1_000 -> count.toString()
    count < 10_000 -> topicHeaderCompactLabel(
        value = count / 1_000.0,
        suffix = "K",
        fractionDigits = 1,
    )
    count < 1_000_000 -> topicHeaderCompactLabel(
        value = count / 1_000.0,
        suffix = "K",
        fractionDigits = 0,
    )
    count < 10_000_000 -> topicHeaderCompactLabel(
        value = count / 1_000_000.0,
        suffix = "M",
        fractionDigits = 1,
    )
    else -> topicHeaderCompactLabel(
        value = count / 1_000_000.0,
        suffix = "M",
        fractionDigits = 0,
    )
}

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

private fun topicHeaderCompactLabel(
    value: Double,
    suffix: String,
    fractionDigits: Int,
): String {
    val roundedValue = when (fractionDigits) {
        0 -> value.roundToInt().toString()
        else -> {
            val rounded = (value * 10).roundToInt() / 10.0
            if (rounded % 1.0 == 0.0) {
                rounded.roundToInt().toString()
            } else {
                String.format(Locale.US, "%.1f", rounded)
            }
        }
    }
    return "$roundedValue$suffix"
}
