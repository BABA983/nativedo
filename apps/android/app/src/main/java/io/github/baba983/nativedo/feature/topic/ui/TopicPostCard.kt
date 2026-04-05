package io.github.baba983.nativedo.feature.topic.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import io.github.baba983.nativedo.ui.theme.NativeDoCardShape
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
        colors.backgroundElevated.copy(alpha = 0.88f)
    }
    val borderColor = if (role == TopicPostRole.OriginalPoster) {
        colors.glassStroke
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = NativeDoCardShape,
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(width = 1.dp, color = borderColor),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                UserAvatar(
                    label = post.authorUsername,
                    avatarTemplate = post.authorAvatarTemplate,
                    baseUrl = baseUrl,
                    size = 32.dp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "@${post.authorUsername}",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textPrimary,
                    )
                    if (role == TopicPostRole.OriginalPoster) {
                        Surface(
                            shape = NativeDoPillShape,
                            color = colors.accentMuted,
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                        ) {
                            Text(
                                text = stringResource(R.string.topic_post_badge_original_poster),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.accent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                Text(
                    text = topicFloorLabel(postIndex),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
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
