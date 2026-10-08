package io.github.baba983.nativedo.topic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
        // A cache file that is not a decodable picture is not this reaction's icon: an interstitial
        // the site answered with a 200, or a transfer that was cut short, must not be published as
        // one - the loader would fail to decode it and the chip would read as a bare count with no
        // fallback. So the bytes are checked, not the response code or the content type, and a cache
        // file that fails is replaced rather than trusted.
        if (isPicture(file)) {
            return@withContext file
        }
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                check(response.isSuccessful) { "GET $url answered ${response.code}" }
                val bytes = response.body?.bytes() ?: error("GET $url answered no body")
                check(isPicture(bytes)) { "GET $url answered something that is not a picture" }
                check(AtomicFileWrite.bytes(file, bytes)) { "cannot move ${file.name} into place" }
            }
            file
        }.getOrElse { error ->
            error.printStackTrace()
            null
        }
    }

    /** Whether the file the loader would open really is a picture it can decode. */
    private fun isPicture(file: File): Boolean {
        if (!file.exists() || file.length() <= 0) {
            return false
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return false
        bitmap.recycle()
        return true
    }

    /** Whether the bytes the site answered really are a picture, before they are published. */
    private fun isPicture(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) {
            return false
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
        bitmap.recycle()
        return true
    }

    /**
     * Where a reaction's fallback picture is kept: the same cache and the same name shape as a
     * fetched icon, so the loader finds it the same way.
     */
    fun fallbackFile(emoticon: String): File {
        val name = DC_ID.toString() + "_" + idOf("fallback:v2:" + emoticon) + ".png"
        return File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), name)
    }

    /**
     * Points the emoticon at a picture drawn here when the site named no icon or the icon could not
     * be fetched. The tile is transparent and carries the reaction itself - a canonical emoji as its
     * glyph, anything else as its readable label - so the chip draws the reaction rather than a bare
     * count, and an invented colour block never stands in for a picture the site did not send. It
     * flows through the same table and the same loader as a fetched icon, so the chip needs no
     * separate path to draw it.
     */
    suspend fun registerFallback(emoticon: String, label: String, id: String = emoticon) {
        if (emoticon.isEmpty()) {
            return
        }
        val file = withContext(Dispatchers.IO) { fallbackTile(emoticon, label, id) } ?: return
        // The entry's title is the reaction's own label, which is the readable description upstream
        // carries for it; the chip itself has no per-chip accessibility node of its own.
        register(emoticon, file, title = label)
    }

    /**
     * The same fallback, drawn and registered on the calling thread. A menu has to have its rail
     * populated before it is measured and shown - upstream builds the picker synchronously from the
     * chat's catalog and only then measures it - so the catalogue the picker filters through must be
     * complete on the first frame, not one fetch later. The tile is small (96px) and cached, so the
     * draw is a few milliseconds; the fetched site icon replaces it later without changing the list.
     *
     * <p>The registration happens only when the table does not already carry an icon for the
     * emoticon. The menu opens again and again over the same catalogue, and a warm open already
     * holds the loaded site picture: replacing it here - before the picker is measured - would draw
     * a synthetic tile on the first frame the reopened menu shows, until the fetch callback found
     * the picture on disk and put it back. So a present entry is left alone.</p>
     */
    fun registerFallbackBlocking(emoticon: String, label: String, id: String = emoticon) {
        if (emoticon.isEmpty() || hasRegisteredIcon(emoticon)) {
            return
        }
        val file = fallbackTile(emoticon, label, id) ?: return
        register(emoticon, file, title = label)
    }

    /**
     * Whether upstream's table already carries an icon this app registered for [emoticon]: a
     * document this app put there, whose entry carries a `localPath`. The check is in memory - it
     * reads the table and that path, and touches no file - so a warm menu open pays nothing for it,
     * while the expensive part it skips (drawing and writing a fallback tile) is exactly what would
     * regress the chip to a placeholder.
     */
    private fun hasRegisteredIcon(emoticon: String): Boolean {
        if (emoticon.isEmpty()) {
            return false
        }
        val entry = MediaDataController.getInstance(0).reactionsMap[emoticon] ?: return false
        val document = entry.center_icon ?: return false
        return !document.localPath.isNullOrEmpty()
    }

    /**
     * Draws the fallback once and keeps it; the same emoticon always names the same tile. A glyph
     * the device can render is drawn as itself; when no font draws it, the reaction's own text is
     * drawn instead so the chip stays readable rather than blank.
     */
    private fun fallbackTile(emoticon: String, label: String, id: String): File? {
        val file = fallbackFile(emoticon)
        if (file.exists() && file.length() > 0) {
            return file
        }
        return runCatching {
            val bitmap = Bitmap.createBitmap(TILE_PX, TILE_PX, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textAlign = Paint.Align.CENTER
            }
            val glyph = emoticon.takeIf { isCanonicalGlyph(it) }
            drawCentered(canvas, paint, glyph ?: label)
            if (glyph != null && !hasOpaquePixels(bitmap)) {
                // No emoji font on this device drew the glyph. The reaction still has to read as
                // itself, so its own id - plain text - is drawn instead of an empty tile.
                canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
                drawCentered(canvas, paint, id)
            }
            val bytes = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
            bitmap.recycle()
            if (AtomicFileWrite.bytes(file, bytes.toByteArray())) file else null
        }.getOrElse { error ->
            error.printStackTrace()
            null
        }
    }

    /** Draws [text] centred in the tile, shrunk to fit its width. */
    private fun drawCentered(canvas: Canvas, paint: Paint, text: String) {
        if (text.isEmpty()) {
            return
        }
        paint.textSize = TILE_PX * 0.72f
        val maxWidth = TILE_PX * 0.9f
        val measured = paint.measureText(text)
        if (measured > maxWidth && measured > 0f) {
            paint.textSize = paint.textSize * (maxWidth / measured)
        }
        val baseline = TILE_PX / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, TILE_PX / 2f, baseline, paint)
    }

    /** Whether the text carries a character outside the BMP's plain text range - an emoji glyph. */
    private fun isCanonicalGlyph(text: String): Boolean =
        text.codePoints().anyMatch { codePoint -> codePoint > 0x2000 }

    /** Whether anything in the tile was actually painted; a glyph with no font draws nothing. */
    private fun hasOpaquePixels(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.any { pixel -> (pixel ushr 24) != 0 }
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
        reaction.activate_animation = document
        reaction.appear_animation = document
        reaction.select_animation = document
        reaction.around_animation = document
        val controller = MediaDataController.getInstance(0)
        controller.reactionsMap[emoticon] = reaction
        // ReactionsContainerLayout filters a finite chat list through this in-memory catalog.
        // Do not call processLoadedReactions: that writes Telegram's database.
        controller.enabledReactionsList.removeAll { it.reaction == emoticon }
        controller.enabledReactionsList.add(reaction)
    }

    /** The document upstream keys the picture by: `id` and `dc_id` are exactly the file's name. */
    private fun document(file: File): TLRPC.TL_document = TLRPC.TL_document().apply {
        // The same pair the file is named after, so the loader's key finds it.
        id = file.nameWithoutExtension.substringAfter('_').toLong()
        dc_id = DC_ID
        access_hash = 0
        file_name = file.name
        localPath = file.absolutePath
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
