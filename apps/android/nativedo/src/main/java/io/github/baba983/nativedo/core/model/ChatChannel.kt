package io.github.baba983.nativedo.core.model

/**
 * One public Discourse chat channel, from `GET /chat/api/channels.json` (see
 * `contracts/discourse/endpoints.md`). Public channels are category channels only; a direct-message
 * channel is a different resource and is never mapped here.
 *
 * <p>[status] is the site's own `open` / `read_only` / `closed` / `archived`. Only `open` accepts a
 * new message, and only a channel the site says the reader may join ([canJoin]) offers to join -
 * everything else is a state the screen says out loud rather than a message that silently fails.</p>
 */
data class ChatChannel(
    val id: Long,
    val title: String,
    val slug: String = "",
    val description: String = "",
    /** The site's own channel status: `open`, `read_only`, `closed` or `archived`. */
    val status: String = "open",
    val membershipsCount: Int = 0,
    val lastMessage: ChatLastMessage? = null,
    /** The reader's own membership, or null when they have not joined. */
    val currentUserMembership: ChatMembership? = null,
    /** `meta.can_join_chat_channel`: the site's answer to whether this reader may join. */
    val canJoin: Boolean = false,
    /** `meta.message_bus_last_ids.channel_message_bus_last_id`, the live-update seed. */
    val channelMessageBusLastId: Long? = null,
    /** `meta.message_bus_last_ids.new_messages`, the new-messages channel seed. */
    val newMessagesBusLastId: Long? = null,
) {
    /** Whether the reader is a member; the site's own membership is the only source of this. */
    val isJoined: Boolean get() = currentUserMembership != null

    /** Whether the channel accepts new messages at all. */
    val isOpen: Boolean get() = status == "open"

    /**
     * Whether a message may be sent now: the reader is a member and the channel is open. The site
     * re-checks both on the write, so this only decides whether the composer is offered.
     */
    val canPost: Boolean get() = isJoined && isOpen

    /** Whether the channel is visible but not writable, which the composer explains rather than hides. */
    val isReadOnly: Boolean get() = isJoined && !isOpen
}

/** The channel's last message, as its own preview serializer answers it. */
data class ChatLastMessage(
    val id: Long,
    val message: String = "",
    val cooked: String = "",
    val createdAt: String = "",
    val user: ChatUser? = null,
)

/** A chat participant, as the message and channel serializers carry one. */
data class ChatUser(
    val id: Long = 0L,
    val username: String,
    val name: String = "",
    val avatarTemplate: String = "",
)

/** The reader's own membership of one channel. */
data class ChatMembership(
    val following: Boolean = false,
    val lastReadMessageId: Long? = null,
)

/** One page of public channels plus the site's own `load_more_url` cursor. */
data class ChatChannelPage(
    val channels: List<ChatChannel>,
    val loadMoreUrl: String? = null,
)

/**
 * One chat message, from `GET /chat/api/channels/{id}/messages.json`. [cooked] is the site's own
 * rendered HTML - the client never cooks [message] itself - and [inReplyTo] carries the target's
 * excerpt and author when the message answers another one.
 */
data class ChatMessage(
    val id: Long,
    val message: String = "",
    val cooked: String = "",
    val createdAt: String = "",
    val channelId: Long = 0L,
    val user: ChatUser? = null,
    val inReplyTo: ChatReplyTarget? = null,
)

/** The message [ChatMessage.inReplyTo] answers, as its own preview serializer carries it. */
data class ChatReplyTarget(
    val id: Long,
    val cooked: String = "",
    val excerpt: String = "",
    val user: ChatUser? = null,
)

/**
 * One page of messages plus the site's own window metadata: whether an older or newer window
 * follows, and the message the window was anchored at (`target_message_id`).
 */
data class ChatMessagePage(
    val messages: List<ChatMessage>,
    val canLoadMorePast: Boolean = false,
    val canLoadMoreFuture: Boolean = false,
    val targetMessageId: Long? = null,
)
