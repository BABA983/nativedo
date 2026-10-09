package io.github.baba983.nativedo.chat

import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.model.ChatChannel
import io.github.baba983.nativedo.core.model.ChatChannelPage
import io.github.baba983.nativedo.core.model.ChatMembership
import io.github.baba983.nativedo.core.model.ChatMessagePage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Bridges the chat data layer to the Java screens, the way
 * [io.github.baba983.nativedo.messages.PrivateMessagesLoader] does: a suspend call made from Java
 * needs a continuation by hand, so the launch lives here and reports back through callbacks.
 */
object ChatLoader {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** One page of public channels from [offset]. */
    fun channels(
        container: AppContainer,
        offset: Int,
        onLoaded: (ChatChannelPage) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            container.chatRepository.channels(offset = offset)
                .onSuccess(onLoaded)
                .onFailure(onFailed)
        }
    }

    /** One channel, with the reader's membership and the live-update seeds. */
    fun channel(
        container: AppContainer,
        channelId: Long,
        onLoaded: (ChatChannel) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            container.chatRepository.channel(channelId)
                .onSuccess(onLoaded)
                .onFailure(onFailed)
        }
    }

    /** One window of a channel's messages. */
    fun messages(
        container: AppContainer,
        channelId: Long,
        targetMessageId: Long?,
        direction: String?,
        fetchFromLastRead: Boolean?,
        onLoaded: (ChatMessagePage) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            container.chatRepository.messages(
                channelId = channelId,
                targetMessageId = targetMessageId,
                direction = direction,
                fetchFromLastRead = fetchFromLastRead,
            ).onSuccess(onLoaded).onFailure(onFailed)
        }
    }

    /** Joins the channel; the site decides whether this reader may. */
    fun join(
        container: AppContainer,
        channelId: Long,
        onJoined: (ChatMembership) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            container.chatRepository.join(channelId)
                .onSuccess(onJoined)
                .onFailure(onFailed)
        }
    }

    /**
     * Sends one message and reports the id the site accepted. The ack is not the cooked message, so
     * the screen reads the accepted id back through [messages]; a readback that fails keeps the id.
     */
    fun send(
        container: AppContainer,
        channelId: Long,
        message: String,
        inReplyToId: Long?,
        onSent: (Long) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            container.chatRepository.send(
                channelId = channelId,
                message = message,
                inReplyToId = inReplyToId,
            ).onSuccess(onSent).onFailure(onFailed)
        }
    }

    /** Advances the reader's last-read position; a failure is silent, the marker is not content. */
    fun markRead(container: AppContainer, channelId: Long, messageId: Long) {
        scope.launch {
            container.chatRepository.markRead(channelId = channelId, messageId = messageId)
        }
    }
}
