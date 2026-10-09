package io.github.baba983.nativedo.core.messagebus

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * The reader's unread notification counts as the site's own notification channel carries them:
 * `unread_notifications` (ordinary unread), `unread_high_priority_notifications` (the count the
 * site's own badge shows) and `all_unread_notifications_count` (both kinds, capped by the site).
 * All three default to zero, which is also what a signed-out client shows.
 */
data class NotificationUnreadCounts(
    val unread: Int = 0,
    val highPriority: Int = 0,
    val allUnread: Int = 0,
)

/**
 * The notifications tab's unread badge, fed from `/notification/{userId}`.
 *
 * The payload is the whole user-menu state, but only the three count fields matter here; a payload
 * that omits one of them (or a site that only sends some) leaves that count as it was instead of
 * zeroing it - a zeroed badge is indistinguishable from "everything read".
 *
 * The subscription starts at the site's own `notification_channel_position` when the caller has it
 * (see [MessageBusBootstrap]), otherwise at [MessageBusClient.INITIAL_LAST_ID]: only events from
 * now on. [initialCounts] is the caller's seed (e.g. from `/session/current.json`); a live message
 * then always wins over that seed because it arrives after it.
 */
class NotificationCountStateHolder(
    private val client: MessageBusClient,
    userId: Long,
    initialCounts: NotificationUnreadCounts = NotificationUnreadCounts(),
    initialLastId: Long = MessageBusClient.INITIAL_LAST_ID,
) : AutoCloseable {
    /** The channel this holder owns; also the key a bootstrap map names it by. */
    val channel: String = "/notification/$userId"

    private val _counts = MutableStateFlow(initialCounts)

    val counts: StateFlow<NotificationUnreadCounts> = _counts.asStateFlow()

    /**
     * Serialises the two writers of [_counts]: an arriving channel message and a server readback
     * ([applyReadback]). The readback carries the revision it saw when it started; a message that
     * landed in the meantime moved the revision, so the slower readback is dropped rather than
     * undoing a newer count.
     */
    private val lock = Any()

    /** Bumped on every count this holder adopts (a live message, or a server readback). */
    private var revision = 0L

    private val listener: MessageBusListener = { message -> onMessage(message) }

    init {
        client.subscribe(channel, initialLastId, listener)
    }

    override fun close() {
        client.unsubscribe(channel, listener)
    }

    /** The revision a caller captures before a server read; pass it back to [applyReadback]. */
    fun currentRevision(): Long = synchronized(lock) { revision }

    /**
     * Adopts counts the caller read back from the site (the bootstrap after a write). Dropped when
     * a newer live message arrived since [startedRevision] was captured, so a readback that was in
     * flight while the channel moved cannot roll the badge backwards. Returns whether it applied.
     */
    fun applyReadback(counts: NotificationUnreadCounts, startedRevision: Long): Boolean =
        synchronized(lock) {
            if (startedRevision != revision) {
                false
            } else {
                revision += 1
                _counts.value = counts
                true
            }
        }

    private fun onMessage(message: MessageBusMessage) {
        if (message.channel != channel) return
        val body = message.data as? JsonObject ?: return
        synchronized(lock) {
            val current = _counts.value
            revision += 1
            _counts.value = NotificationUnreadCounts(
                unread = (body["unread_notifications"] as? JsonPrimitive)?.intOrNull ?: current.unread,
                highPriority = (body["unread_high_priority_notifications"] as? JsonPrimitive)?.intOrNull
                    ?: current.highPriority,
                allUnread = (body["all_unread_notifications_count"] as? JsonPrimitive)?.intOrNull
                    ?: current.allUnread,
            )
        }
    }
}
