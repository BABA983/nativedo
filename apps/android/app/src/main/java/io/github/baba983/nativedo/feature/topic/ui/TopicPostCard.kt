package io.github.baba983.nativedo.feature.topic.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import coil3.compose.AsyncImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import io.github.baba983.nativedo.core.model.PostReaction
import io.github.baba983.nativedo.core.model.defaultReactionFallback
import io.github.baba983.nativedo.core.model.toggledReaction
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.core.ui.HtmlPostBodyView
import io.github.baba983.nativedo.core.ui.NativeDoAnchoredPopup
import io.github.baba983.nativedo.core.ui.UserAvatarWithFlair
import io.github.baba983.nativedo.core.ui.resolvePostImageUrl
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
    replyTargetAuthorLabel: String = "",
    replyTargetSummary: String = "",
    replyCount: Int = 0,
    recentReplyParticipantLabels: List<String> = emptyList(),
    validReactionIds: List<String> = emptyList(),
    emojiCatalog: Map<String, String> = emptyMap(),
    currentTopicId: Long? = null,
    localPostsByPostNumber: Map<Int, String> = emptyMap(),
    onExpandQuote: (suspend (Long, Int) -> Result<String>)? = null,
    onRepliesClick: (() -> Unit)? = null,
    onReactionSelected: ((PostReaction) -> Unit)? = null,
    onMoreReactionsClick: ((List<PostReaction>, String?) -> Unit)? = null,
    onReactionPickerSelect: ((PostReaction) -> Unit)? = null,
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
            TopicPostHeader(
                post = post,
                postIndex = postIndex,
                role = role,
                baseUrl = baseUrl,
                svgSpriteMarkup = svgSpriteMarkup,
                onUserClick = onUserClick,
            )

            if (replyTargetFloorLabel != null && onReplyTargetClick != null) {
                TopicReplyPreview(
                    authorLabel = replyTargetAuthorLabel,
                    floorLabel = replyTargetFloorLabel,
                    summary = replyTargetSummary,
                    onClick = onReplyTargetClick,
                )
            }

            HtmlPostBodyView(
                html = post.cookedHtml,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                svgSpriteMarkup = svgSpriteMarkup,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                onOpenLink = onOpenLink,
                onExpandQuote = onExpandQuote,
                currentTopicId = currentTopicId,
                localPostsByPostNumber = localPostsByPostNumber,
                modifier = Modifier.fillMaxWidth(),
            )

            TopicPostFooter(
                replyCount = replyCount,
                recentReplyParticipantLabels = recentReplyParticipantLabels,
                reactions = if (post.reactions.isNotEmpty()) {
                    post.reactions
                } else {
                    post.defaultReactionFallback(emojiCatalog)
                },
                availableReactionIds = resolveTopicReactionPickerIds(
                    validReactionIds = validReactionIds,
                    emojiCatalog = emojiCatalog,
                ),
                emojiCatalog = emojiCatalog,
                baseUrl = baseUrl,
                onRepliesClick = onRepliesClick,
                onReactionSelected = onReactionSelected,
                onMoreReactionsClick = onMoreReactionsClick,
                onReactionPickerSelect = onReactionPickerSelect,
            )
        }
    }
}

@Composable
private fun TopicPostHeader(
    post: Post,
    postIndex: Int,
    role: TopicPostRole,
    baseUrl: String,
    svgSpriteMarkup: String,
    onUserClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors

    Row(
        modifier = modifier
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
                Text(
                    text = topicPostMetaLabel(
                        index = postIndex,
                        createdAt = post.createdAt,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopicPostSecondaryLine(
                    username = post.authorUsername,
                    title = post.authorTitle,
                    modifier = Modifier.weight(1f),
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
                topicPostEditedMetaLabel(
                    editCount = post.editCount,
                )?.let { editLabel ->
                    TopicPostEditBadge(
                        label = editLabel,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopicReplyPreview(
    authorLabel: String,
    floorLabel: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val interactionSource = remember { MutableInteractionSource() }
    val resolvedSummary = summary.ifBlank { "Tap to jump to the replied post." }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = colors.backgroundElevated.copy(alpha = 0.72f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .heightIn(min = 28.dp)
                .background(colors.accent.copy(alpha = 0.9f), RoundedCornerShape(999.dp)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = listOf(authorLabel.ifBlank { "Unknown" }, floorLabel)
                    .filter { it.isNotBlank() }
                    .joinToString(separator = " · "),
                style = MaterialTheme.typography.labelMedium,
                color = colors.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = resolvedSummary,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary.copy(alpha = 0.86f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TopicPostFooter(
    replyCount: Int,
    recentReplyParticipantLabels: List<String>,
    reactions: List<PostReaction>,
    availableReactionIds: List<String>,
    emojiCatalog: Map<String, String>,
    baseUrl: String,
    onRepliesClick: (() -> Unit)?,
    onReactionSelected: ((PostReaction) -> Unit)?,
    onMoreReactionsClick: ((List<PostReaction>, String?) -> Unit)?,
    onReactionPickerSelect: ((PostReaction) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val showRepliesEntry = replyCount > 0 || onRepliesClick != null
    val visibleReactions = reactions.filter { reaction -> reaction.count > 0 }
    var pickerExpanded by remember { mutableStateOf(false) }
    if (!showRepliesEntry && visibleReactions.isEmpty() && availableReactionIds.isEmpty()) {
        return
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showRepliesEntry) {
            TopicRepliesEntry(
                replyCount = replyCount,
                participantLabels = recentReplyParticipantLabels,
                onClick = onRepliesClick,
            )
        } else {
            Box(modifier = Modifier.weight(1f))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopicReactionRow(
                reactions = visibleReactions,
                baseUrl = baseUrl,
                onReactionClick = onReactionSelected,
                onMoreClick = onMoreReactionsClick,
            )
            if (availableReactionIds.isNotEmpty() && reactions.none { reaction -> reaction.reacted }) {
                Box {
                    TopicAddReactionChip(
                        onClick = { pickerExpanded = !pickerExpanded },
                    )
                    TopicReactionPickerPopup(
                        expanded = pickerExpanded,
                        availableReactionIds = availableReactionIds,
                        emojiCatalog = emojiCatalog,
                        baseUrl = baseUrl,
                        onDismissRequest = {
                            pickerExpanded = false
                        },
                        onReactionSelected = { reaction ->
                            pickerExpanded = false
                            onReactionPickerSelect?.invoke(reaction)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TopicRepliesEntry(
    replyCount: Int,
    participantLabels: List<String>,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy((-4).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            participantLabels.take(3).ifEmpty { listOf("reply") }.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(
                            color = if (index == 0) {
                                colors.accent.copy(alpha = 0.28f)
                            } else {
                                colors.textSecondary.copy(alpha = 0.14f)
                            },
                            shape = CircleShape,
                        ),
                )
            }
        }
        Text(
            text = topicReplyCountLabel(replyCount),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun TopicReactionRow(
    reactions: List<PostReaction>,
    baseUrl: String,
    onReactionClick: ((PostReaction) -> Unit)?,
    onMoreClick: ((List<PostReaction>, String?) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (reactions.isEmpty()) {
        return
    }
    val sortedReactions = reactions.sortedWith(
        compareByDescending<PostReaction> { it.reacted }
            .thenByDescending { it.count }
            .thenBy { it.id },
    )
    val visibleReactions = sortedReactions.take(3)
    val hiddenCount = (sortedReactions.size - visibleReactions.size).coerceAtLeast(0)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        visibleReactions.forEach { reaction ->
            TopicReactionChip(
                reaction = reaction,
                baseUrl = baseUrl,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (reaction.reacted) {
                        onReactionClick?.invoke(reaction)
                    } else {
                        onMoreClick?.invoke(sortedReactions, reaction.id)
                    }
                },
            )
        }
        if (hiddenCount > 0 && onMoreClick != null) {
            TopicMoreReactionsChip(
                hiddenCount = hiddenCount,
                onClick = { onMoreClick(sortedReactions, null) },
            )
        }
    }
}

@Composable
internal fun TopicReactionChip(
    reaction: PostReaction,
    baseUrl: String,
    selected: Boolean = false,
    compact: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val isHeartFallback = reaction.id == "heart" && reaction.iconUrl.isNullOrBlank()
    val containerColor = when {
        reaction.reacted -> colors.accentMuted.copy(alpha = 0.28f)
        selected -> colors.backgroundElevated.copy(alpha = 0.82f)
        else -> colors.accentMuted.copy(alpha = 0.18f)
    }
    val borderColor = when {
        reaction.reacted -> colors.accent.copy(alpha = 0.28f)
        selected -> colors.textSecondary.copy(alpha = 0.28f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
    }
    Surface(
        modifier = modifier,
        shape = NativeDoPillShape,
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = borderColor,
        ),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = if (compact) 4.dp else 6.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                !reaction.iconUrl.isNullOrBlank() -> {
                    AsyncImage(
                        model = resolvePostImageUrl(
                            baseUrl = baseUrl,
                            rawUrl = reaction.iconUrl,
                        ),
                        contentDescription = reaction.id,
                        modifier = Modifier.size(if (compact) 14.dp else 16.dp),
                    )
                }
                isHeartFallback -> {
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = reaction.id,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(if (compact) 12.dp else 14.dp),
                    )
                }
                else -> {
                    Text(
                        text = reaction.id,
                        style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                        maxLines = 1,
                    )
                }
            }
            Text(
                text = reaction.count.toString(),
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TopicMoreReactionsChip(
    hiddenCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        ),
        shape = NativeDoPillShape,
        color = colors.accentMuted.copy(alpha = 0.14f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f),
        ),
    ) {
        Text(
            text = "+$hiddenCount",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun TopicAddReactionChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        ),
        shape = NativeDoPillShape,
        color = colors.accentMuted.copy(alpha = 0.14f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f),
        ),
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier
                .size(14.dp)
                .padding(0.dp),
        )
    }
}

@Composable
private fun TopicReactionPickerPopup(
    expanded: Boolean,
    availableReactionIds: List<String>,
    emojiCatalog: Map<String, String>,
    baseUrl: String,
    onDismissRequest: () -> Unit,
    onReactionSelected: (PostReaction) -> Unit,
) {
    if (availableReactionIds.isEmpty()) {
        return
    }
    NativeDoAnchoredPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        width = 244.dp,
        itemCount = availableReactionIds.size.coerceAtMost(10),
        spacing = 4.dp,
        closeTranslationY = 0.dp,
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
    ) { popupProgress ->
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            availableReactionIds.chunked(5).forEachIndexed { rowIndex, rowIds ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    rowIds.forEachIndexed { itemIndex, reactionId ->
                        val reaction = PostReaction(
                            id = reactionId,
                            count = 0,
                            iconUrl = emojiCatalog[reactionId],
                            reacted = false,
                        )
                        val revealProgress = io.github.baba983.nativedo.core.ui.nativeDoPopupCascadeProgress(
                            fullAnimationT = popupProgress,
                            position = (rowIndex * 5 + itemIndex).toFloat(),
                            count = availableReactionIds.size.toFloat().coerceAtLeast(1f),
                            waveLength = 4f,
                        )
                        TopicReactionPickerButton(
                            reaction = reaction,
                            baseUrl = baseUrl,
                            revealProgress = revealProgress,
                            onClick = { onReactionSelected(reaction) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicReactionPickerButton(
    reaction: PostReaction,
    baseUrl: String,
    revealProgress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .size(40.dp)
            .graphicsLayer {
                alpha = revealProgress
                scaleX = 0.92f + (0.08f * revealProgress)
                scaleY = 0.92f + (0.08f * revealProgress)
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(14.dp),
        color = colors.backgroundElevated.copy(alpha = 0.92f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f),
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (!reaction.iconUrl.isNullOrBlank()) {
                AsyncImage(
                    model = resolvePostImageUrl(
                        baseUrl = baseUrl,
                        rawUrl = reaction.iconUrl,
                    ),
                    contentDescription = reaction.id,
                    modifier = Modifier.size(20.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.FavoriteBorder,
                    contentDescription = reaction.id,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private val preferredTopicReactionPickerOrder = listOf(
    "heart",
    "+1",
    "laughing",
    "open_mouth",
    "clap",
    "confetti_ball",
    "hugs",
    "distorted_face",
    "tieba_087",
    "bili_057",
)

private fun resolveTopicReactionPickerIds(
    validReactionIds: List<String>,
    emojiCatalog: Map<String, String>,
): List<String> {
    val availableIds = buildSet {
        addAll(validReactionIds)
        preferredTopicReactionPickerOrder.forEach { reactionId ->
            if (emojiCatalog.containsKey(reactionId)) {
                add(reactionId)
            }
        }
    }
    val ordered = preferredTopicReactionPickerOrder.filter { reactionId -> reactionId in availableIds }
    val remaining = validReactionIds.filterNot { reactionId -> reactionId in ordered }
    return (ordered + remaining).take(10)
}

internal fun topicReplyCountLabel(replyCount: Int): String {
    return if (replyCount > 0) "$replyCount replies" else "Replies"
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
