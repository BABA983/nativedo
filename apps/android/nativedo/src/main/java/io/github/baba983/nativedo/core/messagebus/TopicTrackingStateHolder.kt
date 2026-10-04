package io.github.baba983.nativedo.core.messagebus

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Topics that arrived after the list last refreshed: one entry per distinct topic id, carrying the
 * topic's category when the site sent one. A null category means the message did not name a
 * category (the reference client then treats the topic as unfiltered).
 */
data class IncomingTopics(
    val byTopicId: Map<Long, Int?> = emptyMap(),
) {
    val count: Int get() = byTopicId.size

    val isEmpty: Boolean get() = byTopicId.isEmpty()

    /** How many incoming topics are in [categoryId]; null means every category. */
    fun countForCategory(categoryId: Int?): Int =
        if (categoryId == null) byTopicId.size else byTopicId.values.count { it == categoryId }

    /** The incoming topic ids of [categoryId]; null means every category. */
    fun topicIdsForCategory(categoryId: Int?): List<Long> =
        if (categoryId == null) {
            byTopicId.keys.toList()
        } else {
            byTopicId.filterValues { it == categoryId }.keys.toList()
        }
}

/**
 * The topic list's "new topics" indicator, fed from the site's own tracking channels.
 *
 * Discourse publishes `new_topic` on `/new` when a topic is created and `latest` on `/latest` when
 * a topic is bumped; its own topic-tracking state counts both as one incoming topic per id (the
 * `latest` message type is additionally published to muted/unmuted users only, but a client that
 * filters by category can drop those itself). This holder listens to both channels, takes
 * `topic_id` and `payload.category_id`, and counts each topic once until [clear] is called - which
 * is what the list does after it refreshes, matching the web client's `resetTracking`.
 *
 * Type-change messages on the same channels (`muted`, `unmuted`) and user-targeted messages
 * (`read`, `unread`, `dismiss_new`) do not grow the count; only `new_topic` and `latest` do.
 */
class TopicTrackingStateHolder(
    private val client: MessageBusClient,
    initialLastIds: Map<String, Long> = emptyMap(),
    private val latestChannel: String = "/latest",
    private val newChannel: String = "/new",
) : AutoCloseable {
    private val _incoming = MutableStateFlow(IncomingTopics())

    val incoming: StateFlow<IncomingTopics> = _incoming.asStateFlow()

    private val listener: MessageBusListener = { message -> onMessage(message) }

    init {
        client.subscribe(latestChannel, initialLastIds[latestChannel] ?: MessageBusClient.INITIAL_LAST_ID, listener)
        client.subscribe(newChannel, initialLastIds[newChannel] ?: MessageBusClient.INITIAL_LAST_ID, listener)
    }

    /** Drops everything collected so far; the list calls this after it has refreshed. */
    fun clear() {
        _incoming.value = IncomingTopics()
    }

    override fun close() {
        client.unsubscribe(latestChannel, listener)
        client.unsubscribe(newChannel, listener)
    }

    private fun onMessage(message: MessageBusMessage) {
        if (message.channel != latestChannel && message.channel != newChannel) return
        val body = message.data as? JsonObject ?: return
        val messageType = (body["message_type"] as? JsonPrimitive)?.contentOrNull
        if (messageType != "new_topic" && messageType != "latest") return
        val topicId = (body["topic_id"] as? JsonPrimitive)?.longOrNull ?: return
        val categoryId = ((body["payload"] as? JsonObject)?.get("category_id") as? JsonPrimitive)?.intOrNull
        _incoming.update { current ->
            if (current.byTopicId.containsKey(topicId)) current
            else current.copy(byTopicId = current.byTopicId + (topicId to categoryId))
        }
    }
}
