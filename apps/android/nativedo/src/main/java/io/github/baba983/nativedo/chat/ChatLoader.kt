package io.github.baba983.nativedo.chat

import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.model.ChatChannel
import io.github.baba983.nativedo.core.model.ChatChannelPage
import io.github.baba983.nativedo.core.model.ChatMembership
import io.github.baba983.nativedo.core.model.ChatMessagePage
import io.github.baba983.nativedo.core.model.UploadedFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.telegram.messenger.FileLoader
import java.io.File
import java.security.MessageDigest

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
     * [uploadIds] are uploads this reader already sent to the site through [uploadVoice] (or any
     * `chat-composer` upload); the site enforces ownership and that the channel allows uploads, and
     * [message] may be blank when there are uploads.
     */
    @JvmOverloads
    fun send(
        container: AppContainer,
        channelId: Long,
        message: String,
        inReplyToId: Long?,
        onSent: (Long) -> Unit,
        onFailed: (Throwable) -> Unit,
        uploadIds: List<Long> = emptyList(),
    ) {
        scope.launch {
            container.chatRepository.send(
                channelId = channelId,
                message = message,
                inReplyToId = inReplyToId,
                uploadIds = uploadIds,
            ).onSuccess(onSent).onFailure(onFailed)
        }
    }

    /**
     * Uploads one file for a chat message (`upload_type=chat-composer`) and reports the upload the
     * site accepted. An upload alone sends nothing: its id is what [send] references through
     * `upload_ids`, and the site checks ownership again on the message write.
     */
    fun uploadVoice(
        container: AppContainer,
        file: File,
        mimeType: String?,
        onProgress: (Float) -> Unit,
        onUploaded: (UploadedFile) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            container.chatRepository.upload(file = file, mimeType = mimeType, onProgress = onProgress)
                .onSuccess(onUploaded)
                .onFailure(onFailed)
        }
    }

    /**
     * Fetches a received message's audio upload into the app's own cache, with the session-aware
     * client (linux.do serves some uploads to a signed-in reader only), and hands the local file to
     * [onReady]. The device's own player plays that file - never Telegram's account-backed media
     * controller, which has no datacenter here.
     */
    fun receivedAudio(
        container: AppContainer,
        url: String,
        onReady: (File) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            runCatching { downloadReceivedAudio(container, url) }
                .onSuccess(onReady)
                .onFailure(onFailed)
        }
    }

    private suspend fun downloadReceivedAudio(container: AppContainer, url: String): File =
        withContext(Dispatchers.IO) {
            val directory = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), AUDIO_DIR)
            directory.mkdirs()
            val target = File(directory, audioFileName(url))
            if (target.exists() && target.length() > 0L) {
                return@withContext target
            }
            container.imageOkHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                check(response.isSuccessful) { "GET $url answered ${response.code}" }
                val body = response.body ?: error("GET $url answered no body")
                val partial = File(directory, target.name + ".part")
                partial.outputStream().use { output -> body.byteStream().use { input -> input.copyTo(output) } }
                if (partial.length() <= 0L) {
                    partial.delete()
                    error("GET $url answered an empty body")
                }
                if (!partial.renameTo(target)) {
                    partial.delete()
                    error("cannot move ${target.name} into place")
                }
            }
            target
        }

    /** A stable cache name for a received upload: its own file name, disambiguated by its URL. */
    private fun audioFileName(url: String): String {
        val path = url.substringBefore('?').substringAfterLast('/')
        val name = path.replace(Regex("[^\\p{L}\\p{N}._-]"), "_").take(64).ifBlank { "audio" }
        val digest = MessageDigest.getInstance("MD5").digest(url.toByteArray())
        val suffix = digest.take(4).joinToString("") { "%02x".format(it) }
        return "$suffix-$name"
    }

    /** Advances the reader's last-read position; a failure is silent, the marker is not content. */
    fun markRead(container: AppContainer, channelId: Long, messageId: Long) {
        scope.launch {
            container.chatRepository.markRead(channelId = channelId, messageId = messageId)
        }
    }

    /** Where a received audio upload is cached while it exists; private app cache, not user storage. */
    private const val AUDIO_DIR = "chat-audio"
}
