package io.github.baba983.nativedo.feature.topic.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.core.ui.HtmlPostBodyView
import io.github.baba983.nativedo.core.ui.UserAvatar
import io.github.baba983.nativedo.ui.theme.NativeDoInnerCardShape
import io.github.baba983.nativedo.ui.theme.NativeDoPillShape
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras

@Composable
internal fun TopicPostCard(
    post: Post,
    postIndex: Int,
    role: TopicPostRole,
    cookieStore: CookieStore,
    baseUrl: String,
    onTopicClick: (Long) -> Unit,
    onUserClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val containerColor = if (role == TopicPostRole.OriginalPoster) {
        colors.glassBackgroundStrong
    } else {
        colors.glassBackground
    }
    val borderColor = if (role == TopicPostRole.OriginalPoster) {
        colors.accent.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.26f)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = NativeDoInnerCardShape,
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(width = 1.dp, color = borderColor),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(
                    label = post.authorUsername,
                    avatarTemplate = post.authorAvatarTemplate,
                    baseUrl = baseUrl,
                    size = 28.dp,
                )
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "@${post.authorUsername}",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textPrimary,
                    )
                    if (role == TopicPostRole.OriginalPoster) {
                        Surface(
                            shape = NativeDoPillShape,
                            color = colors.accentMuted.copy(alpha = 0.9f),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                        ) {
                            Text(
                                text = stringResource(R.string.topic_post_badge_original_poster),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.accent,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = topicPostMetaLabel(
                            index = postIndex,
                            createdAt = post.createdAt,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                    )
                }
            }

            HtmlPostBodyView(
                html = post.cookedHtml,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
