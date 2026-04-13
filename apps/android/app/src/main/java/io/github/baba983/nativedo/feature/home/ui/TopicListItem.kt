package io.github.baba983.nativedo.feature.home.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.core.model.CategorySummary
import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.core.ui.compactCount
import io.github.baba983.nativedo.core.ui.UserAvatar
import io.github.baba983.nativedo.ui.theme.NativeDoPillShape
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val TOPIC_LIST_MAX_TAGS = 2
private val topicListDateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

@Composable
fun TopicListItem(
    topic: TopicSummary,
    category: CategorySummary? = null,
    baseUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    val colors = NativeDoThemeExtras.colors
    val tagLine = topicTagLine(topic)
    val activityLine = topicActivityLine(topic)
    val timeLabel = topicRelativeTime(topic.lastPostedAt).orEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            UserAvatar(
                label = topic.authorUsername,
                avatarTemplate = topic.authorAvatarTemplate,
                baseUrl = baseUrl,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        if (topic.isPinned) {
                            Icon(
                                imageVector = Icons.Outlined.PushPin,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(14.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                            )
                        }
                        Text(
                            text = topic.title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (timeLabel.isNotBlank()) {
                        Text(
                            text = timeLabel,
                            modifier = Modifier.padding(top = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                            maxLines = 1,
                        )
                    }
                }
                if (category != null || tagLine.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        category?.let {
                            TopicCategoryBadge(category = it)
                        }
                        if (tagLine.isNotBlank()) {
                            Text(
                                text = tagLine,
                                modifier = Modifier.weight(1f, fill = false),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (activityLine.isNotBlank()) {
                        Text(
                            text = activityLine,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TopicStatsRow(topic = topic)
                    TopicUnreadBadge(topic = topic)
                }
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
private fun TopicCategoryBadge(
    category: CategorySummary,
    modifier: Modifier = Modifier,
) {
    val badgeColor = discourseCategoryColor(
        rawHex = category.colorHex,
        fallback = MaterialTheme.colorScheme.primary,
    )
    val labelColor = Color(0xFF161616)

    Surface(
        modifier = modifier,
        shape = NativeDoPillShape,
        color = badgeColor.copy(alpha = 0.18f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            text = category.name,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TopicStatsRow(
    topic: TopicSummary,
    modifier: Modifier = Modifier,
) {
    if (topic.replyCount <= 0 && topic.viewCount <= 0) {
        return
    }

    val colors = NativeDoThemeExtras.colors

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (topic.replyCount > 0) {
            TopicStat(
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Reply,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                text = compactCount(topic.replyCount),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (topic.viewCount > 0) {
            TopicStat(
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = colors.textSecondary,
                    )
                },
                text = compactCount(topic.viewCount),
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun TopicStat(
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

internal fun topicTagLine(
    topic: TopicSummary,
    maxTags: Int = TOPIC_LIST_MAX_TAGS,
): String = topic.tags
    .asSequence()
    .filter(String::isNotBlank)
    .take(maxTags)
    .joinToString(separator = " · ") { tag -> "#$tag" }

internal fun topicActivityLine(
    topic: TopicSummary,
): String = buildList {
    topic.authorUsername
        .takeIf { it.isNotBlank() }
        ?.let { username -> add("@$username") }
    topic.lastPosterUsername
        .takeIf { it.isNotBlank() && it != topic.authorUsername }
        ?.let { username -> add("@$username") }
}.joinToString(separator = " · ")

internal fun topicRelativeTime(
    rawValue: String,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): String? {
    if (rawValue.isBlank()) {
        return null
    }

    val postedAt = runCatching { OffsetDateTime.parse(rawValue) }.getOrNull() ?: return null
    val duration = Duration.between(postedAt.toInstant(), now)

    if (duration.isNegative) {
        return postedAt.atZoneSameInstant(zoneId).format(topicListDateFormatter)
    }

    return when {
        duration < Duration.ofMinutes(1) -> "just now"
        duration < Duration.ofHours(1) -> "${duration.toMinutes()}m"
        duration < Duration.ofDays(1) -> "${duration.toHours()}h"
        duration < Duration.ofDays(7) -> "${duration.toDays()}d"
        else -> postedAt.atZoneSameInstant(zoneId).format(topicListDateFormatter)
    }
}

@Composable
private fun TopicUnreadBadge(
    topic: TopicSummary,
    modifier: Modifier = Modifier,
) {
    when {
        topic.unreadCount > 0 -> {
            val bgColor = if (topic.isMuted) {
                NativeDoThemeExtras.colors.textSecondary.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.primary
            }
            Surface(
                modifier = modifier,
                shape = NativeDoPillShape,
                color = bgColor,
            ) {
                Text(
                    text = if (topic.unreadCount > 99) "99+" else topic.unreadCount.toString(),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                )
            }
        }
        topic.isUnseen -> {
            Box(
                modifier = modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

private fun discourseCategoryColor(
    rawHex: String,
    fallback: Color,
): Color {
    val normalizedHex = rawHex.trim().removePrefix("#")
    if (normalizedHex.isBlank()) {
        return fallback
    }
    return runCatching {
        Color(AndroidColor.parseColor("#$normalizedHex"))
    }.getOrDefault(fallback)
}
