package io.github.baba983.nativedo.chat

import io.github.baba983.nativedo.core.model.ChatChannel
import io.github.baba983.nativedo.core.model.ChatMessage
import io.github.baba983.nativedo.core.model.ChatReplyTarget
import io.github.baba983.nativedo.core.model.Post
import org.telegram.ui.Cells.DialogCell
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Maps chat models onto the Telegram shapes the stock cells consume, exactly as
 * [io.github.baba983.nativedo.topic.DiscourseRows] maps a topic's. A channel is a
 * [DialogCell.CustomDialog] row and a message is a [Post] the shared post->bubble bridge
 * ([io.github.baba983.nativedo.topic.DiscourseRows.postMessage]) turns into a `ChatMessageCell`.
 *
 * <p>Nothing here decides what the UI does; both sides are plain data holders, so this is a
 * translation and not a seam.</p>
 */
object ChatRows {

    /**
     * One public channel as a chat-list row: its title, its last message's own text and when that
     * message arrived. A channel the reader has not joined says so in the second line rather than
     * pretending to be an ongoing conversation.
     */
    @JvmStatic
    fun channelRow(channel: ChatChannel): DialogCell.CustomDialog {
        val row = DialogCell.CustomDialog()
        row.id = (channel.id and 0x7fffffffL).toInt()
        row.name = channel.title
        row.message = lastMessageLine(channel)
        row.date = epochSeconds(channel.lastMessage?.createdAt)
        row.unread_count = 0
        row.pinned = false
        row.muted = false
        return row
    }

    /** Whether a channel row draws the joined marker the reader's own membership decides. */
    @JvmStatic
    fun isJoined(channel: ChatChannel): Boolean = channel.isJoined

    private fun lastMessageLine(channel: ChatChannel): String {
        val last = channel.lastMessage
        if (last != null) {
            val who = last.user?.username.orEmpty()
            val text = last.message.ifBlank { plainText(last.cooked) }
            return when {
                who.isNotEmpty() && text.isNotEmpty() -> "$who: $text"
                text.isNotEmpty() -> text
                else -> joinedLine(channel)
            }
        }
        return joinedLine(channel)
    }

    private fun joinedLine(channel: ChatChannel): String = when {
        channel.isJoined -> "已加入"
        channel.canJoin -> "未加入，点一下加入"
        else -> channel.status.ifBlank { "频道" }
    }

    private fun plainText(cooked: String): String =
        cooked.replace(Regex("<[^>]+>"), "").replace("&nbsp;", " ").trim()

    /**
     * One message as the [Post] the shared bubble bridge reads: its own cooked body, its author and
     * when the site says it arrived. Nothing is derived from the raw markdown.
     */
    @JvmStatic
    fun post(message: ChatMessage): Post = Post(
        id = message.id,
        authorUsername = message.user?.username.orEmpty(),
        authorDisplayName = message.user?.name.orEmpty(),
        authorAvatarTemplate = message.user?.avatarTemplate.orEmpty(),
        createdAt = message.createdAt,
        cookedHtml = message.cooked,
    )

    /** The message [message] answers, as the snapshot the reply preview draws. */
    @JvmStatic
    fun replyTarget(target: ChatReplyTarget): Post = Post(
        id = target.id,
        authorUsername = target.user?.username.orEmpty(),
        authorDisplayName = target.user?.name.orEmpty(),
        authorAvatarTemplate = target.user?.avatarTemplate.orEmpty(),
        cookedHtml = target.cooked,
    )

    /** Discourse sends ISO-8601; upstream's cells want whole seconds. */
    @JvmStatic
    fun epochSeconds(isoDate: String?): Int {
        if (isoDate.isNullOrEmpty()) {
            return (System.currentTimeMillis() / 1000L).toInt()
        }
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        val parsed: Date? = format.parse(isoDate, ParsePosition(0))
        return if (parsed == null) {
            (System.currentTimeMillis() / 1000L).toInt()
        } else {
            (parsed.time / 1000L).toInt()
        }
    }
}
