package io.github.baba983.nativedo.feature.topic.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.core.ui.HtmlPostBodyView
import io.github.baba983.nativedo.core.ui.UserAvatar
import io.github.baba983.nativedo.core.ui.UserAvatarWithFlair
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
    svgSpriteMarkup: String,
    onTopicClick: (Long) -> Unit,
    onUserClick: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    replyTargetFloorLabel: String? = null,
    replyTargetAvatarLabel: String = "",
    replyTargetAvatarTemplate: String = "",
    replyTargetAnimatedAvatarUrl: String = "",
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
            // Header layout: avatar + two-line text block.
            // Line 1: [displayName ...  OP  reply  edit  time]  — meta pills share the first row
            // Line 2: [@username · title]                       — full width, no meta competition
            // Keep meta pills inside the first-line Row so the second line can use the entire width.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onUserClick(post.authorUsername) },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatarWithFlair(
                    label = post.authorUsername,
                    avatarTemplate = post.authorAvatarTemplate,
                    animatedAvatarUrl = post.authorAnimatedAvatarUrl,
                    flairName = post.authorFlairName,
                    flairUrl = post.authorFlairUrl,
                    flairBgColor = post.authorFlairBgColor,
                    flairColor = post.authorFlairColor,
                    svgSpriteMarkup = svgSpriteMarkup,
                    baseUrl = baseUrl,
                    avatarSize = 28.dp,
                    flairBadgeSize = 13.dp,
                    flairContentSize = 11.dp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 20.dp),
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
                                    modifier = Modifier
                                        .clickable(onClick = onReplyTargetClick)
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.Reply,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(10.dp),
                                    )
                                    if (
                                        replyTargetAvatarLabel.isNotBlank() ||
                                        replyTargetAvatarTemplate.isNotBlank() ||
                                        replyTargetAnimatedAvatarUrl.isNotBlank()
                                    ) {
                                        UserAvatar(
                                            label = replyTargetAvatarLabel,
                                            avatarTemplate = replyTargetAvatarTemplate,
                                            animatedAvatarUrl = replyTargetAnimatedAvatarUrl,
                                            baseUrl = baseUrl,
                                            size = 14.dp,
                                        )
                                    }
                                    Text(
                                        text = replyTargetFloorLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textSecondary,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        topicPostEditedMetaLabel(
                            editCount = post.editCount,
                        )?.let { editLabel ->
                            TopicPostEditBadge(
                                label = editLabel,
                            )
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
                    TopicPostSecondaryLine(
                        username = post.authorUsername,
                        title = post.authorTitle,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            HtmlPostBodyView(
                html = post.cookedHtml,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                svgSpriteMarkup = svgSpriteMarkup,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                onOpenLink = onOpenLink,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TopicPostSecondaryLine(
    username: String,
    title: String,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val normalizedTitle = title.trim()

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = topicPostUsernameLabel(username),
            modifier = if (normalizedTitle.isNotBlank()) {
                Modifier.weight(weight = 1f, fill = false)
            } else {
                Modifier
            },
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary.copy(alpha = 0.86f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (normalizedTitle.isNotBlank()) {
            Text(
                text = "·",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary.copy(alpha = 0.34f),
            )
            TopicPostTitleText(
                title = normalizedTitle,
                modifier = Modifier.weight(weight = 1f, fill = false),
            )
        }
    }
}

@Composable
private fun TopicPostTitleText(
    title: String,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val titleStyle = MaterialTheme.typography.labelSmall

    if (topicPostUsesAnimatedTitle(title)) {
        val brush = rememberTopicPostAnimatedTitleBrush()
        Text(
            text = title,
            modifier = modifier,
            style = titleStyle.copy(brush = brush),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        Text(
            text = title,
            modifier = modifier,
            style = titleStyle,
            color = colors.textSecondary.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun rememberTopicPostAnimatedTitleBrush(
): Brush {
    val transition = rememberInfiniteTransition(label = "topic_post_title_transition")
    val loopOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = TOPIC_POST_TITLE_GRADIENT_PERIOD,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = TOPIC_POST_TITLE_ANIMATION_DURATION_MILLIS,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "topic_post_title_loop_offset",
    )

    return Brush.linearGradient(
        colors = listOf(
            TopicPostAnimatedTitleMagenta,
            TopicPostAnimatedTitleCyan,
            TopicPostAnimatedTitleYellow,
            TopicPostAnimatedTitleMagenta,
            TopicPostAnimatedTitleCyan,
        ),
        start = Offset(x = loopOffset, y = loopOffset),
        end = Offset(
            x = loopOffset + TOPIC_POST_TITLE_GRADIENT_PERIOD,
            y = loopOffset + TOPIC_POST_TITLE_GRADIENT_PERIOD,
        ),
        tileMode = TileMode.Repeated,
    )
}
private const val TOPIC_POST_TITLE_ANIMATION_DURATION_MILLIS = 15000
private const val TOPIC_POST_TITLE_GRADIENT_PERIOD = 320f
private val TopicPostAnimatedTitleMagenta = Color(0xFFFF00FF)
private val TopicPostAnimatedTitleCyan = Color(0xFF00FFFF)
private val TopicPostAnimatedTitleYellow = Color(0xFFFFFF00)

@Composable
private fun TopicPostEditBadge(
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors

    Surface(
        modifier = modifier,
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
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(10.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}
