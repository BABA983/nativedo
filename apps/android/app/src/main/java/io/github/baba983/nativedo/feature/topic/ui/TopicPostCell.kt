package io.github.baba983.nativedo.feature.topic.ui

import android.graphics.RectF
import android.graphics.Bitmap
import android.view.View
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.PostReaction
import io.github.baba983.nativedo.core.model.defaultReactionFallback
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.core.ui.GalleryImage
import io.github.baba983.nativedo.core.ui.HtmlPostBodyView
import io.github.baba983.nativedo.core.ui.UserAvatarWithFlair
import io.github.baba983.nativedo.core.ui.resolvePostImageUrl

@Composable
internal fun TopicPostCell(
    post: Post,
    index: Int,
    colors: TopicDetailColors,
    baseUrl: String,
    cookieStore: CookieStore,
    svgSpriteMarkup: String,
    validReactionIds: List<String>,
    emojiCatalog: Map<String, String>,
    currentTopicId: Long,
    localPostsByPostNumber: Map<Int, String>,
    replyTargetFloorLabel: String?,
    replyTargetAuthorLabel: String,
    replyTargetSummary: String,
    replyCount: Int,
    recentReplyParticipantLabels: List<String>,
    onExpandQuote: (suspend (Long, Int) -> Result<String>)?,
    onRepliesClick: (() -> Unit)?,
    onReplyTargetClick: (() -> Unit)?,
    onReactionSelected: ((PostReaction) -> Unit)?,
    onMoreReactionsClick: ((List<PostReaction>, String?) -> Unit)?,
    onReactionPickerSelect: ((PostReaction) -> Unit)?,
    onTopicClick: (Long) -> Unit,
    onOpenLink: (String) -> Unit,
    hiddenImageUrl: String?,
    onImageBoundsChanged: (String, RectF?) -> Unit,
    onImageViewChanged: (String, View?) -> Unit,
    onOpenImage: (String, List<GalleryImage>, RectF?, Bitmap?, View?) -> Unit,
    onOpenAuthor: () -> Unit,
    isJumpHighlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    val reactions = if (post.reactions.isNotEmpty()) {
        post.reactions
    } else {
        post.defaultReactionFallback(emojiCatalog)
    }
    val containerColor by animateColorAsState(
        targetValue = if (isJumpHighlighted) {
            lerp(colors.rowBackground, colors.accent, 0.10f)
        } else {
            colors.rowBackground
        },
        label = "topicPostCellContainer",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(containerColor)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TopicPostHeaderRow(
                post = post,
                index = index,
                colors = colors,
                baseUrl = baseUrl,
                svgSpriteMarkup = svgSpriteMarkup,
                onOpenAuthor = onOpenAuthor,
            )
            if (replyTargetFloorLabel != null) {
                TopicReplyPreview(
                    authorLabel = replyTargetAuthorLabel,
                    floorLabel = replyTargetFloorLabel,
                    summary = replyTargetSummary,
                    colors = colors,
                    onClick = onReplyTargetClick,
                )
            }
            HtmlPostBodyView(
                html = post.cookedHtml.ifBlank { "<p>（空内容）</p>" },
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                svgSpriteMarkup = svgSpriteMarkup,
                onTopicClick = onTopicClick,
                onUserClick = { username -> onOpenLink("$baseUrl/u/$username") },
                onOpenLink = onOpenLink,
                hiddenImageUrl = hiddenImageUrl,
                onImageBoundsChanged = onImageBoundsChanged,
                onImageViewChanged = onImageViewChanged,
                onOpenImage = onOpenImage,
                onExpandQuote = onExpandQuote,
                currentTopicId = currentTopicId,
                localPostsByPostNumber = localPostsByPostNumber,
                modifier = Modifier.fillMaxWidth(),
            )
            TopicPostFooter(
                replyCount = replyCount,
                recentReplyParticipantLabels = recentReplyParticipantLabels,
                reactions = reactions,
                availableReactionIds = resolveTopicReactionPickerIds(
                    validReactionIds = validReactionIds,
                    emojiCatalog = emojiCatalog,
                ),
                emojiCatalog = emojiCatalog,
                baseUrl = baseUrl,
                colors = colors,
                onRepliesClick = onRepliesClick,
                onReactionSelected = onReactionSelected,
                onMoreReactionsClick = onMoreReactionsClick,
                onReactionPickerSelect = onReactionPickerSelect,
            )
        }
        HorizontalDivider(color = colors.divider)
    }
}

@Composable
private fun TopicPostHeaderRow(
    post: Post,
    index: Int,
    colors: TopicDetailColors,
    baseUrl: String,
    svgSpriteMarkup: String,
    onOpenAuthor: () -> Unit,
) {
    val displayName = topicPostDisplayName(post.authorDisplayName, post.authorUsername)
    Row(
        modifier = Modifier.fillMaxWidth(),
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
            avatarSize = 34.dp,
            flairBadgeSize = 14.dp,
            flairContentSize = 12.dp,
            modifier = Modifier.clickable(onClick = onOpenAuthor),
        )
        Spacer(modifier = Modifier.size(width = 10.dp, height = 0.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onOpenAuthor),
                )
                Text(
                    text = topicPostMetaLabel(index = index, createdAt = post.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondary,
                    maxLines = 1,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TopicPostSecondaryLine(
                    username = post.authorUsername,
                    title = post.authorTitle,
                    colors = colors,
                    modifier = Modifier.weight(1f),
                )
                if (index == 0) {
                    TopicPostBadge(label = stringResource(R.string.topic_post_badge_original_poster), colors = colors)
                }
                topicPostEditedMetaLabel(post.editCount)?.let { editLabel ->
                    TopicPostEditBadge(label = editLabel, colors = colors)
                }
            }
        }
    }
}

@Composable
private fun TopicPostSecondaryLine(
    username: String,
    title: String,
    colors: TopicDetailColors,
    modifier: Modifier = Modifier,
) {
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
            color = colors.secondary.copy(alpha = 0.86f),
            fontStyle = FontStyle.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (normalizedTitle.isNotBlank()) {
            Text(
                text = "·",
                style = MaterialTheme.typography.labelSmall,
                color = colors.secondary.copy(alpha = 0.34f),
            )
            TopicPostTitleText(
                title = normalizedTitle,
                colors = colors,
                modifier = Modifier.weight(weight = 1f, fill = false),
            )
        }
    }
}

@Composable
private fun TopicPostTitleText(
    title: String,
    colors: TopicDetailColors,
    modifier: Modifier = Modifier,
) {
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
            color = colors.secondary.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun rememberTopicPostAnimatedTitleBrush(): Brush {
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
private val TopicPostAnimatedTitleMagenta = androidx.compose.ui.graphics.Color(0xFFFF00FF)
private val TopicPostAnimatedTitleCyan = androidx.compose.ui.graphics.Color(0xFF00FFFF)
private val TopicPostAnimatedTitleYellow = androidx.compose.ui.graphics.Color(0xFFFFFF00)

@Composable
private fun TopicReplyPreview(
    authorLabel: String,
    floorLabel: String,
    summary: String,
    colors: TopicDetailColors,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val resolvedSummary = summary.ifBlank { "Tap to jump to the replied post." }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = colors.background,
                shape = RoundedCornerShape(12.dp),
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .heightIn(min = 28.dp)
                .background(colors.accent, RoundedCornerShape(999.dp)),
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
                color = colors.secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TopicPostBadge(label: String, colors: TopicDetailColors) {
    Box(
        modifier = Modifier
            .background(colors.accent.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.accent,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun TopicPostEditBadge(
    label: String,
    colors: TopicDetailColors,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = colors.background.copy(alpha = 0.58f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(width = 1.dp, color = colors.divider.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = null,
                tint = colors.secondary,
                modifier = Modifier.size(10.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.secondary,
                maxLines = 1,
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
    colors: TopicDetailColors,
    onRepliesClick: (() -> Unit)?,
    onReactionSelected: ((PostReaction) -> Unit)?,
    onMoreReactionsClick: ((List<PostReaction>, String?) -> Unit)?,
    onReactionPickerSelect: ((PostReaction) -> Unit)?,
) {
    val showRepliesEntry = replyCount > 0 || onRepliesClick != null
    val visibleReactions = reactions.filter { reaction -> reaction.count > 0 }
    var pickerExpanded by remember { mutableStateOf(false) }
    if (!showRepliesEntry && visibleReactions.isEmpty() && availableReactionIds.isEmpty()) {
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showRepliesEntry) {
                TopicRepliesEntry(
                    replyCount = replyCount,
                    participantLabels = recentReplyParticipantLabels,
                    colors = colors,
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
                    colors = colors,
                    onReactionClick = onReactionSelected,
                    onMoreClick = onMoreReactionsClick,
                )
                if (availableReactionIds.isNotEmpty() && reactions.none { reaction -> reaction.reacted }) {
                    TopicAddReactionChip(
                        colors = colors,
                        onClick = { pickerExpanded = !pickerExpanded },
                    )
                }
            }
        }
        if (pickerExpanded) {
            TopicReactionPickerRow(
                availableReactionIds = availableReactionIds,
                emojiCatalog = emojiCatalog,
                baseUrl = baseUrl,
                colors = colors,
                onReactionSelected = { reaction ->
                    pickerExpanded = false
                    onReactionPickerSelect?.invoke(reaction)
                },
            )
        }
    }
}

@Composable
private fun TopicRepliesEntry(
    replyCount: Int,
    participantLabels: List<String>,
    colors: TopicDetailColors,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy((-4).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            participantLabels.take(3).ifEmpty { listOf("reply") }.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(
                            color = if (index == 0) {
                                colors.accent.copy(alpha = 0.28f)
                            } else {
                                colors.secondary.copy(alpha = 0.14f)
                            },
                            shape = CircleShape,
                        ),
                )
            }
        }
        Text(
            text = topicReplyCountLabel(replyCount),
            style = MaterialTheme.typography.labelMedium,
            color = colors.secondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun TopicReactionRow(
    reactions: List<PostReaction>,
    baseUrl: String,
    colors: TopicDetailColors,
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
                colors = colors,
                modifier = Modifier.clickable {
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
                colors = colors,
                onClick = { onMoreClick(sortedReactions, null) },
            )
        }
    }
}

@Composable
internal fun TopicReactionChip(
    reaction: PostReaction,
    baseUrl: String,
    colors: TopicDetailColors,
    selected: Boolean = false,
    compact: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val isHeartFallback = reaction.id == "heart" && reaction.iconUrl.isNullOrBlank()
    val containerColor = when {
        reaction.reacted -> colors.accent.copy(alpha = 0.18f)
        selected -> colors.background.copy(alpha = 0.82f)
        else -> colors.accent.copy(alpha = 0.08f)
    }
    val borderColor = when {
        reaction.reacted -> colors.accent.copy(alpha = 0.28f)
        selected -> colors.secondary.copy(alpha = 0.28f)
        else -> colors.divider.copy(alpha = 0.42f)
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(width = 1.dp, color = borderColor),
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
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(if (compact) 14.dp else 16.dp),
                    )
                }
                isHeartFallback -> {
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = reaction.id,
                        tint = colors.secondary,
                        modifier = Modifier.size(if (compact) 12.dp else 14.dp),
                    )
                }
                else -> {
                    Text(
                        text = reaction.id,
                        style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                        color = colors.secondary,
                        maxLines = 1,
                    )
                }
            }
            if (reaction.count > 0) {
                Text(
                    text = reaction.count.toString(),
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                    color = if (reaction.reacted) colors.accent else colors.secondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TopicMoreReactionsChip(
    hiddenCount: Int,
    colors: TopicDetailColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(colors.accent.copy(alpha = 0.08f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = "+$hiddenCount",
            style = MaterialTheme.typography.labelSmall,
            color = colors.secondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun TopicAddReactionChip(
    colors: TopicDetailColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(colors.accent.copy(alpha = 0.08f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 4.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            tint = colors.secondary,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun TopicReactionPickerRow(
    availableReactionIds: List<String>,
    emojiCatalog: Map<String, String>,
    baseUrl: String,
    colors: TopicDetailColors,
    onReactionSelected: (PostReaction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        availableReactionIds.take(10).forEach { reactionId ->
            val reaction = PostReaction(
                id = reactionId,
                count = 0,
                iconUrl = emojiCatalog[reactionId],
                reacted = false,
            )
            TopicReactionChip(
                reaction = reaction,
                baseUrl = baseUrl,
                colors = colors,
                modifier = Modifier.clickable { onReactionSelected(reaction) },
            )
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
