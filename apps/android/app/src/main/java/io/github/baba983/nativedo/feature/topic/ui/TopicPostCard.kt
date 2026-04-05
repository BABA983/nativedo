package io.github.baba983.nativedo.feature.topic.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
    replyTargetFloorLabel: String? = null,
    replyTargetAvatarLabel: String = "",
    replyTargetAvatarTemplate: String = "",
    onReplyTargetClick: (() -> Unit)? = null,
    isJumpHighlighted: Boolean = false,
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
    val resolvedContainerColor by animateColorAsState(
        targetValue = if (isJumpHighlighted) {
            lerp(
                start = containerColor,
                stop = MaterialTheme.colorScheme.scrim,
                fraction = 0.14f,
            )
        } else {
            containerColor
        },
        label = "topicPostCardContainer",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = NativeDoInnerCardShape,
        color = resolvedContainerColor,
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
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = topicPostDisplayName(
                                displayName = post.authorDisplayName,
                                username = post.authorUsername,
                            ),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
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
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            if (replyTargetFloorLabel != null && onReplyTargetClick != null) {
                                Surface(
                                    onClick = onReplyTargetClick,
                                    shape = NativeDoPillShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
                                    tonalElevation = 0.dp,
                                    shadowElevation = 0.dp,
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f),
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.Reply,
                                            contentDescription = null,
                                            tint = colors.textSecondary,
                                            modifier = Modifier.size(10.dp),
                                        )
                                        if (replyTargetAvatarLabel.isNotBlank() || replyTargetAvatarTemplate.isNotBlank()) {
                                            UserAvatar(
                                                label = replyTargetAvatarLabel,
                                                avatarTemplate = replyTargetAvatarTemplate,
                                                baseUrl = baseUrl,
                                                size = 14.dp,
                                            )
                                        } else {
                                            Text(
                                                text = replyTargetFloorLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.textSecondary,
                                            )
                                        }
                                    }
                                }
                            }
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
                    Text(
                        text = topicPostUsernameLabel(post.authorUsername),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary.copy(alpha = 0.96f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
