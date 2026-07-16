package io.github.baba983.nativedo.feature.topic.ui

import io.github.baba983.nativedo.core.model.Post
import java.time.ZoneId

internal data class TopicDateHeader(
    val epochDay: Long,
    val epochSeconds: Long,
) {
    val stableId: Long
        get() = TOPIC_DATE_STABLE_ID_BASE + epochDay
}

internal data class TopicPostDateEntry(
    val post: Post,
    val date: TopicDateHeader?,
    val header: TopicDateHeader?,
)

internal fun topicPostDateEntries(
    posts: List<Post>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<TopicPostDateEntry> {
    var previousEpochDay: Long? = null
    return posts.map { post ->
        val date = topicDateHeader(post.createdAt, zoneId)
        val header = date?.takeIf { it.epochDay != previousEpochDay }
        if (date != null) {
            previousEpochDay = date.epochDay
        }
        TopicPostDateEntry(post = post, date = date, header = header)
    }
}

internal fun topicDateHeader(
    rawValue: String,
    zoneId: ZoneId = ZoneId.systemDefault(),
): TopicDateHeader? {
    val instant = parseTopicPostInstant(rawValue) ?: return null
    val localDate = instant.atZone(zoneId).toLocalDate()
    val epochSeconds = localDate.atStartOfDay(zoneId).toEpochSecond()
    return TopicDateHeader(
        epochDay = localDate.toEpochDay(),
        epochSeconds = epochSeconds,
    )
}

private const val TOPIC_DATE_STABLE_ID_BASE = Long.MIN_VALUE + 1_000_000_000_000L
