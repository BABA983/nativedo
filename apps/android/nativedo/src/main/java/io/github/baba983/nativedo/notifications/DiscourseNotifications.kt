package io.github.baba983.nativedo.notifications

import io.github.baba983.nativedo.core.model.NotificationItem
import java.time.Instant
import org.telegram.ui.Cells.DialogCell

/**
 * Maps the site's in-app notification onto the row upstream's list draws, the way
 * [io.github.baba983.nativedo.topic.DiscourseRows] maps a topic onto one: a list row is a title,
 * a line under it and a date, and once the site's fields are in those three places the row is
 * upstream's own cell and nothing of ours.
 *
 * <p>The site says who did it in the payload's `data`, not in `user_id` (which is the reader the
 * notification was addressed to) - the reader's own notification list is one page of "someone did
 * something in this topic", newest first.</p>
 */
object DiscourseNotifications {

    /** Whether the notification points at a post the reader can open. */
    fun hasDestination(item: NotificationItem): Boolean = item.topicId > 0

    /**
     * One notification as a row: the topic it is about as the title, who did what under it, when the
     * site sent it, and the row's own unread badge while it is unread.
     */
    fun row(item: NotificationItem): DialogCell.CustomDialog {
        val what = action(item)
        val who = actor(item)
        val row = DialogCell.CustomDialog()
        // A row without a topic has no topic id to draw a picture for, and two rows still have to
        // differ: the notification's own id is what is left.
        row.id = ((if (item.topicId > 0) item.topicId else item.id) and 0x7fffffff).toInt()
        row.name = item.title.ifEmpty { what }
        row.message = when {
            item.title.isEmpty() -> ""
            who.isEmpty() -> what
            else -> "$who · $what"
        }
        row.date = epochSeconds(item.createdAt)
        row.unread_count = if (item.isRead) 0 else 1
        return row
    }

    /** Who the notification is about, with the count when the site folded several people into one. */
    private fun actor(item: NotificationItem): String {
        val who = item.actorName.ifEmpty { item.actorUsername }
        return if (who.isNotEmpty() && item.count > 1) "$who 等 ${item.count} 人" else who
    }

    /**
     * What happened, in the reader's own words. The site's notification types are its own numbers
     * (see contracts/discourse/endpoints.md); a type this client does not know - a plugin's - is
     * something happened rather than nothing.
     */
    private fun action(item: NotificationItem): String = when (item.type) {
        1, 15 -> "提到了你"
        2, 6, 9 -> if (item.consolidatedCount > 1) {
            "有 ${item.consolidatedCount} 条新回复"
        } else {
            "回复了你"
        }
        3 -> "引用了你的帖子"
        4 -> "编辑了你的帖子"
        5 -> "赞了你的帖子"
        7 -> "邀请你加入私信"
        8 -> "接受了邀请"
        10 -> "移动了你的帖子"
        11 -> "链接了你的帖子"
        12 -> if (item.badgeName.isEmpty()) "获得了徽章" else "获得了徽章「${item.badgeName}」"
        13 -> "邀请你加入话题"
        14 -> "有新动态"
        16 -> "有新的组消息"
        17 -> "在你关注的话题里发了帖子"
        18, 24 -> "话题提醒"
        19 -> if (item.count > 1) "${item.count} 人赞了你的帖子" else "赞了你的帖子"
        20 -> "批准了你的帖子"
        21 -> "批准了你的提交"
        22 -> "接受了你的加入申请"
        23 -> "有人申请加入"
        25 -> "回应了你的帖子"
        26 -> "投票结果已发布"
        27 -> "日程提醒"
        28 -> "邀请你参加日程"
        29, 32 -> "在聊天里提到了你"
        30 -> "给你发了聊天消息"
        31 -> "邀请你加入频道"
        33 -> "在聊天里引用了你"
        34 -> "指派给你"
        35 -> "在你回答的问题下评论了"
        36 -> "在你关注的分类或标签里发了话题"
        37 -> "站点有新功能"
        39 -> if (item.count > 1) "${item.count} 条链接提到了你的帖子" else "链接了你的帖子"
        else -> "有新动态"
    }

    /** The site dates a notification with an ISO-8601 instant; an unreadable one is dated now. */
    private fun epochSeconds(timestamp: String): Int =
        runCatching { Instant.parse(timestamp).epochSecond.toInt() }
            .getOrDefault((System.currentTimeMillis() / 1000L).toInt())
}
