package io.github.baba983.nativedo.topic

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.telegram.messenger.FileLoader
import org.telegram.messenger.MediaDataController
import org.telegram.tgnet.TLRPC

/**
 * A reaction's picture, for the chip upstream draws at the bottom of a bubble.
 *
 * Upstream draws a reaction chip from its table of the reactions Telegram offers: the chip looks the
 * emoticon up in `MediaDataController.getReactionsMap()` and asks the image loader for that entry's
 * `center_icon`. Nothing fills that table here - `messages.getAvailableReactions` does, and that
 * needs tgnet - so a chip had only its count to draw, and every reaction on the site read as a bare
 * number in a blue pill.
 *
 * The site already answers with a picture for each reaction it knows (`/emojis.json` names an image
 * per reaction, and `PostReaction.iconUrl` carries it this far). Those bytes are fetched with the
 * session like every other picture here, and what upstream is handed is an entry it can load: a
 * document whose `localPath` is the fetched file - the same shape the attachment and video blocks
 * use. The table is filled directly rather than through `processLoadedReactions`, because that
 * method writes its answer into Telegram's own storage, which this client never opens.
 */
object DiscourseReactionEmoji {

    /**
     * The datacenter id the fetched pictures are filed under. Upstream will not key a document
     * image at all unless its dc_id is non-zero, and nothing here ever talks to a datacenter: the
     * number only names the file.
     */
    private const val DC_ID = 1

    /**
     * Where a reaction's picture is kept: upstream's cache directory, named by the picture's URL,
     * so two reactions never share a file and one reaction's picture is fetched once.
     */
    fun file(url: String): File {
        // Upstream keys a document image by dc_id + "_" + id and reads it from the cache directory
        // under that name, so that is what the file is called.
        val name = DC_ID.toString() + "_" + idOf(url) + ".png"
        return File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), name)
    }

    /** Fetches the picture when it is not on disk yet; null when the site answered nothing usable. */
    suspend fun cache(client: OkHttpClient, url: String): File? = withContext(Dispatchers.IO) {
        val file = file(url)
        if (file.exists() && file.length() > 0) {
            return@withContext file
        }
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                check(response.isSuccessful) { "GET $url answered ${response.code}" }
                val bytes = response.body?.bytes() ?: error("GET $url answered no body")
                file.parentFile?.mkdirs()
                val partial = File(file.parentFile, file.name + "." + Thread.currentThread().id + ".part")
                partial.writeBytes(bytes)
                check(partial.renameTo(file)) { "cannot move ${partial.name} into place" }
            }
            file
        }.getOrElse { error ->
            error.printStackTrace()
            null
        }
    }

    /**
     * Points the emoticon's entry in upstream's live reaction table at [file], so the chip that
     * looks the emoticon up draws this picture instead of nothing.
     */
    fun register(emoticon: String, file: File) {
        if (emoticon.isEmpty() || !file.exists() || file.length() <= 0) {
            return
        }
        val document = document(file)
        val reaction = TLRPC.TL_availableReaction()
        reaction.reaction = emoticon
        reaction.title = emoticon
        reaction.static_icon = document
        reaction.center_icon = document
        MediaDataController.getInstance(0).getReactionsMap()[emoticon] = reaction
    }

    /** The document upstream keys the picture by: `id` and `dc_id` are exactly the file's name. */
    private fun document(file: File): TLRPC.TL_document = TLRPC.TL_document().apply {
        // The same pair the file is named after, so the loader's key finds it.
        id = file.nameWithoutExtension.substringAfter('_').toLong()
        dc_id = DC_ID
        access_hash = 0
        file_name = file.name
        mime_type = "image/png"
        size = file.length()
        thumbs = ArrayList()
        attributes = arrayListOf(TLRPC.TL_documentAttributeImageSize().apply {
            w = 24
            h = 24
        })
    }

    /** A stable positive id for a URL, so the same picture always names the same file. */
    private fun idOf(url: String): Long {
        val digest = MessageDigest.getInstance("MD5").digest(url.toByteArray(Charsets.UTF_8))
        var value = 0L
        for (index in 0 until 8) {
            value = (value shl 8) or (digest[index].toLong() and 0xff)
        }
        return (value and 0x7fffffffffffffffL).coerceAtLeast(1L)
    }
}
