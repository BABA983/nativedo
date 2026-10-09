package io.github.baba983.nativedo.feature.chat.data

import io.github.baba983.nativedo.core.model.ChatChannel
import io.github.baba983.nativedo.core.model.ChatChannelPage
import io.github.baba983.nativedo.core.model.ChatMembership
import io.github.baba983.nativedo.core.model.ChatMessagePage
import io.github.baba983.nativedo.core.network.DiscourseApiClient

/**
 * The reader's public chat channels and their messages, over the chat API routes (see
 * `contracts/discourse/endpoints.md`). Direct-message channels are a different resource and are not
 * read here.
 *
 * <p>Every call carries the session - there is nothing public about a chat channel to a signed-out
 * app - and every failure is kept as the site's own distinction (a challenge, a refusal, an outage)
 * so a screen can say which one it was rather than showing a blank list.</p>
 */
class ChatRepository(
    private val client: DiscourseApiClient,
) {
    /** One page of public channels from [offset]; the answer's `loadMoreUrl` says another follows. */
    suspend fun channels(offset: Int, limit: Int = 25): Result<ChatChannelPage> =
        runCatching { client.fetchChatChannels(offset = offset, limit = limit) }

    /** One channel, with the reader's membership and the live-update seeds. */
    suspend fun channel(channelId: Long): Result<ChatChannel> =
        runCatching { client.fetchChatChannel(channelId) }

    /**
     * One window of messages. With no [targetMessageId] the site answers the newest window;
     * a target without direction is included, whereas `past`/`future` exclude the target.
     */
    suspend fun messages(
        channelId: Long,
        pageSize: Int = 50,
        targetMessageId: Long? = null,
        direction: String? = null,
        fetchFromLastRead: Boolean? = null,
    ): Result<ChatMessagePage> = runCatching {
        client.fetchChatMessages(
            channelId = channelId,
            pageSize = pageSize,
            targetMessageId = targetMessageId,
            direction = direction,
            fetchFromLastRead = fetchFromLastRead,
        )
    }

    /** Joins the channel; the site decides whether this reader may. */
    suspend fun join(channelId: Long): Result<ChatMembership> =
        runCatching { client.joinChatChannel(channelId) }

    /**
     * Sends one message and answers the id the site accepted. The write is never replayed by this
     * repository: the caller reads the accepted id back through [messages].
     */
    suspend fun send(
        channelId: Long,
        message: String,
        inReplyToId: Long? = null,
        stagedId: String? = null,
    ): Result<Long> = runCatching {
        client.createChatMessage(
            channelId = channelId,
            message = message,
            inReplyToId = inReplyToId,
            stagedId = stagedId,
        )
    }

    /** Advances the reader's last-read position in the channel to [messageId]. */
    suspend fun markRead(channelId: Long, messageId: Long): Result<Unit> =
        runCatching { client.markChatChannelRead(channelId, messageId) }
}
