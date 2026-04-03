package io.github.baba983.nativedo.feature.home.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.core.ui.UserAvatar
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras

@Composable
fun TopicListItem(
    topic: TopicSummary,
    baseUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    val colors = NativeDoThemeExtras.colors

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
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = topic.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = topicMetaLine(topic),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (topic.replyCount > 0) {
                Text(
                    text = topic.replyCount.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
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

private fun topicMetaLine(topic: TopicSummary): String = buildList {
    add("@${topic.authorUsername}")
    topic.lastPostedAt
        .takeIf { it.isNotBlank() }
        ?.substringBefore("T")
        ?.let(::add)
    if (topic.viewCount > 0) {
        add("${topic.viewCount} views")
    }
    topic.categoryId?.let { categoryId ->
        add("C$categoryId")
    }
}.joinToString(separator = " · ")
