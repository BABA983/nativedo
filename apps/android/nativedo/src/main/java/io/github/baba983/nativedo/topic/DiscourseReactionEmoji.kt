package io.github.baba983.nativedo.topic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.io.ByteArrayOutputStream
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

    /** The side of a drawn fallback tile, in pixels: a 24 dp chip at 4x. */
    private const val TILE_PX = 96

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
                check(AtomicFileWrite.bytes(file, bytes)) { "cannot move ${file.name} into place" }
            }
            file
        }.getOrElse { error ->
            error.printStackTrace()
            null
        }
    }

    /**
     * Where a reaction's fallback picture is kept: the same cache and the same name shape as a
     * fetched icon, so the loader finds it the same way.
     */
    fun fallbackFile(emoticon: String): File {
        val name = DC_ID.toString() + "_" + idOf("fallback:" + emoticon) + ".png"
        return File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), name)
    }

    /**
     * Points the emoticon at a picture drawn here when the site named no icon or the icon could not
     * be fetched: a tile carrying the reaction's own label on a colour of its own, so the chip draws
     * the reaction rather than a bare count. It flows through the same table and the same loader as a
     * fetched icon, so the chip needs no separate path to draw it.
     */
    suspend fun registerFallback(emoticon: String, label: String) {
        if (emoticon.isEmpty()) {
            return
        }
        val file = withContext(Dispatchers.IO) { fallbackTile(emoticon, label) } ?: return
        // The entry's title is the reaction's own label, which is the readable description upstream
        // carries for it; the chip itself has no per-chip accessibility node of its own.
        register(emoticon, file, title = label)
    }

    /** Draws the fallback once and keeps it; the same emoticon always names the same tile. */
    private fun fallbackTile(emoticon: String, label: String): File? {
        val file = fallbackFile(emoticon)
        if (file.exists() && file.length() > 0) {
            return file
        }
        return runCatching {
            val bitmap = Bitmap.createBitmap(TILE_PX, TILE_PX, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(colorOf(emoticon))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = TILE_PX * 0.5f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            // The label is the reaction's own; the chip draws the tile small, so the text is scaled
            // to fit the tile rather than clipped by it.
            val maxWidth = TILE_PX * 0.84f
            val measured = paint.measureText(label)
            if (measured > maxWidth && measured > 0f) {
                paint.textSize = paint.textSize * (maxWidth / measured)
            }
            val baseline = TILE_PX / 2f - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(label, TILE_PX / 2f, baseline, paint)
            val bytes = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
            bitmap.recycle()
            if (AtomicFileWrite.bytes(file, bytes.toByteArray())) file else null
        }.getOrElse { error ->
            error.printStackTrace()
            null
        }
    }

    /** A colour of the emoticon's own, so two fallbacks never look alike and a reader can tell them apart. */
    private fun colorOf(emoticon: String): Int {
        val hue = Math.floorMod(emoticon.hashCode(), 360).toFloat()
        return Color.HSVToColor(floatArrayOf(hue, 0.62f, 0.78f))
    }

    /**
     * Points the emoticon's entry in upstream's live reaction table at [file], so the chip that
     * looks the emoticon up draws this picture instead of nothing.
     */
    fun register(emoticon: String, file: File, title: String = emoticon) {
        if (emoticon.isEmpty() || !file.exists() || file.length() <= 0) {
            return
        }
        val document = document(file)
        val reaction = TLRPC.TL_availableReaction()
        reaction.reaction = emoticon
        reaction.title = title
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
