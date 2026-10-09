package io.github.baba983.nativedo.core.messagebus

import io.github.baba983.nativedo.core.model.ChatMessage
import io.github.baba983.nativedo.core.network.decodeChatMessageBusEvent
import io.github.baba983.nativedo.core.network.decodeChatNewMessagesEvent
import kotlinx.serialization.json.JsonNull

/**
 * One live chat event, from either of a channel's two message-bus channels.
 *
 * <p>The site publishes messages on `/chat/{id}` with a `type` of `sent`, `edit`, `processed`,
 * `refresh`, `delete`, `restore`, `reaction`, `pin`… and the message itself under `chat_message`
 * (a delete carries only `deleted_id`). A new message is also published on
 * `/chat/{id}/new-messages` as `{type:"channel", message:{...}}`. This client only acts on the two
 * shapes it needs - an arriving or edited message, and a deletion - and drops the rest; explicit
 * refresh remains the fallback for everything it does not model.</p>
 */
data class ChatLiveEvent(
    /** The site's own event type; `sent`/`edit`/`processed`/`refresh` carry a message, `delete` an id. */
    val type: String,
    val message: ChatMessage? = null,
    val deletedId: Long? = null,
) {
    /** Whether this event carries a message to add or replace. */
    val isMessage: Boolean get() = message != null

    /** Whether this event says a message is gone. */
    val isDeletion: Boolean get() = deletedId != null
}

/**
 * A channel's live updates, over the app's own [MessageBusClient].
 *
 * <p>Subscribes to the channel's root channel and its `new-messages` channel, seeded from the
 * channel payload's own `channel_message_bus_last_id` and `new_messages` so a message published
 * between the read and the subscription is still replayed rather than lost. The listener runs on
 * the poller's dispatcher, so a screen that owns views marshals it onto the main thread itself.
 *
 * <p>This is foreground long-polling only: no OS-level push. Closing the handle unsubscribes both
 * channels. When no poller is running ([MessageBusSession.runningClient] is null) a screen simply
 * does not get live updates and falls back to explicit refresh.</p>
 */
class ChatChannelStateHolder(
    private val client: MessageBusClient,
    val channelId: Long,
    seedLastId: Long = MessageBusClient.INITIAL_LAST_ID,
    seedNewMessagesId: Long = MessageBusClient.INITIAL_LAST_ID,
    private val onEvent: (ChatLiveEvent) -> Unit,
) : AutoCloseable {

    private val channel: String = "/chat/$channelId"
    private val newMessagesChannel: String = "/chat/$channelId/new-messages"

    private val listener: MessageBusListener = { message -> onMessage(message) }

    init {
        client.subscribe(channel, seedLastId, listener)
        client.subscribe(newMessagesChannel, seedNewMessagesId, listener)
    }

    override fun close() {
        client.unsubscribe(channel, listener)
        client.unsubscribe(newMessagesChannel, listener)
    }

    private fun onMessage(message: MessageBusMessage) {
        if (message.data == JsonNull) return
        when (message.channel) {
            channel -> {
                val event = decodeChatMessageBusEvent(message.data) ?: return
                val type = event.type.ifBlank { "refresh" }
                onEvent(
                    ChatLiveEvent(
                        type = type,
                        message = event.message,
                        deletedId = event.deletedId,
                    ),
                )
            }
            newMessagesChannel -> {
                val event = decodeChatNewMessagesEvent(message.data) ?: return
                onEvent(ChatLiveEvent(type = "sent", message = event.message))
            }
        }
    }
}
