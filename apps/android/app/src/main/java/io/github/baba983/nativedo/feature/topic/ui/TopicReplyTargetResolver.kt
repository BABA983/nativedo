package io.github.baba983.nativedo.feature.topic.ui

import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.ui.htmlToPreviewText
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val TOPIC_STATIC_ITEMS_BEFORE_POSTS = 1

internal enum class TopicPostRole {
    OriginalPoster,
    Reply,
}

internal data class TopicReplyTarget(
    val itemIndex: Int,
    val postId: Long,
    val postNumber: Int?,
    val authorUsername: String,
    val authorDisplayName: String,
    val authorAvatarTemplate: String,
    val authorAnimatedAvatarUrl: String,
    val createdAt: String,
    val cookedHtml: String,
)

internal data class TopicReplySheetItem(
    val postId: Long,
    val postNumber: Int?,
    val authorUsername: String,
    val authorDisplayName: String,
    val authorAvatarTemplate: String,
    val authorAnimatedAvatarUrl: String,
    val createdAt: String,
    val previewText: String,
)

private val topicPostTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val topicPostDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
private val topicPostUtcFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")

internal fun topicPostRole(index: Int): TopicPostRole =
    if (index == 0) TopicPostRole.OriginalPoster else TopicPostRole.Reply

internal fun topicFloorLabel(index: Int): String = "${index + 1}F"

internal fun topicFloorLabelForPostNumber(postNumber: Int): String = "${postNumber}F"

internal fun topicPostDisplayName(
    displayName: String,
    username: String,
): String = displayName.ifBlank { username }

internal fun topicPostUsernameLabel(username: String): String = "@$username"

internal fun topicPostUsesAnimatedTitle(title: String): Boolean = title.trim() in setOf("种子用户", "破界者")

internal object TopicReplyTargetResolver {
    fun replyTargetsByPostNumber(
        posts: List<Post>,
        leadingItemsCount: Int = TOPIC_STATIC_ITEMS_BEFORE_POSTS,
    ): Map<Int, TopicReplyTarget> = buildMap {
        posts.forEachIndexed { index, post ->
            post.postNumber?.let { postNumber ->
                put(
                    postNumber,
                    TopicReplyTarget(
                        itemIndex = index + leadingItemsCount,
                        postId = post.id,
                        postNumber = post.postNumber,
                        authorUsername = post.authorUsername,
                        authorDisplayName = post.authorDisplayName,
                        authorAvatarTemplate = post.authorAvatarTemplate,
                        authorAnimatedAvatarUrl = post.authorAnimatedAvatarUrl,
                        createdAt = post.createdAt,
                        cookedHtml = post.cookedHtml,
                    ),
                )
            }
        }
    }

    fun replySheetEntriesByPostNumber(
        posts: List<Post>,
    ): Map<Int, List<TopicReplySheetItem>> = posts
        .filter { it.replyToPostNumber != null }
        .groupBy { requireNotNull(it.replyToPostNumber) }
        .mapValues { (_, replies) ->
            replies.map { post ->
                TopicReplySheetItem(
                    postId = post.id,
                    postNumber = post.postNumber,
                    authorUsername = post.authorUsername,
                    authorDisplayName = post.authorDisplayName,
                    authorAvatarTemplate = post.authorAvatarTemplate,
                    authorAnimatedAvatarUrl = post.authorAnimatedAvatarUrl,
                    createdAt = post.createdAt,
                    previewText = post.cookedHtml.htmlToPreviewText(),
                )
            }
        }
}

internal fun topicReplyTargetsByPostNumber(
    posts: List<Post>,
    leadingItemsCount: Int = TOPIC_STATIC_ITEMS_BEFORE_POSTS,
): Map<Int, TopicReplyTarget> = TopicReplyTargetResolver.replyTargetsByPostNumber(
    posts = posts,
    leadingItemsCount = leadingItemsCount,
)

internal fun topicReplySheetEntriesByPostNumber(
    posts: List<Post>,
): Map<Int, List<TopicReplySheetItem>> = TopicReplyTargetResolver.replySheetEntriesByPostNumber(posts)

internal fun topicReplySheetItem(post: Post): TopicReplySheetItem = TopicReplySheetItem(
    postId = post.id,
    postNumber = post.postNumber,
    authorUsername = post.authorUsername,
    authorDisplayName = post.authorDisplayName,
    authorAvatarTemplate = post.authorAvatarTemplate,
    authorAnimatedAvatarUrl = post.authorAnimatedAvatarUrl,
    createdAt = post.createdAt,
    previewText = post.cookedHtml.htmlToPreviewText(),
)

internal fun topicPostMetaLabel(
    index: Int,
    createdAt: String,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    val floorLabel = topicFloorLabel(index)
    val timeLabel = topicPostTimeLabel(
        rawValue = createdAt,
        now = now,
        zoneId = zoneId,
    )
    return if (timeLabel == null) floorLabel else "$floorLabel · $timeLabel"
}

internal fun topicPostTimeLabel(
    rawValue: String,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): String? {
    if (rawValue.isBlank()) return null

    val postedInstant = parseTopicPostInstant(rawValue) ?: return null
    val duration = Duration.between(postedInstant, now)
    val postedLocalDateTime = postedInstant.atZone(zoneId)
    val nowLocalDate = now.atZone(zoneId).toLocalDate()

    if (duration.isNegative) {
        return postedLocalDateTime.format(topicPostDateTimeFormatter)
    }

    return when {
        duration < Duration.ofMinutes(1) -> "just now"
        duration < Duration.ofHours(1) -> "${duration.toMinutes()}m"
        postedLocalDateTime.toLocalDate() == nowLocalDate -> postedLocalDateTime.format(topicPostTimeFormatter)
        else -> postedLocalDateTime.format(topicPostDateTimeFormatter)
    }
}

internal fun parseTopicPostInstant(rawValue: String): Instant? {
    if (rawValue.isBlank()) return null
    return runCatching { OffsetDateTime.parse(rawValue).toInstant() }
        .recoverCatching {
            java.time.LocalDateTime.parse(rawValue, topicPostUtcFormatter)
                .atOffset(ZoneOffset.UTC)
                .toInstant()
        }
        .getOrNull()
}

internal fun topicPostEditedMetaLabel(
    editCount: Int,
): String? {
    return editCount
        .takeIf { it > 0 }
        ?.toString()
}
