package io.github.baba983.nativedo.core.model

data class NotificationPage(
    val items: List<NotificationItem>,
    /** How many notifications the reader has in total, before the page's offset was applied. */
    val totalRows: Int,
)

/**
 * One in-app notification, as the site sent it.
 *
 * [actorUsername] is the reader who caused it - the site's `data.display_username` and its
 * fallbacks, never `user_id`, which is the recipient. [count] is how many people the notification
 * stands for when the site consolidated several into one row, and [title] is the topic it is about,
 * empty for the types that have no topic (a badge, consolidated likes).
 */
data class NotificationItem(
    val id: Long,
    val type: Int,
    val isRead: Boolean,
    val createdAt: String,
    val topicId: Long = 0,
    val postNumber: Int? = null,
    val title: String = "",
    val actorUsername: String = "",
    val actorName: String = "",
    val actorAvatarTemplate: String = "",
    val secondUsername: String = "",
    val count: Int = 0,
    val badgeName: String = "",
    val consolidatedCount: Int = 0,
)
