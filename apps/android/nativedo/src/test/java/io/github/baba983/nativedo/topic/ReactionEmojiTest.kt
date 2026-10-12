package io.github.baba983.nativedo.topic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
import io.github.baba983.nativedo.harness.contentListOf
import io.github.baba983.nativedo.harness.shadows.ShadowAnimatedFileNative
import io.github.baba983.nativedo.harness.shadows.ShadowConnectionsManager
import io.github.baba983.nativedo.harness.shadows.ShadowIntro
import io.github.baba983.nativedo.harness.shadows.ShadowMediaController
import io.github.baba983.nativedo.harness.shadows.ShadowMrzRecognizer
import io.github.baba983.nativedo.harness.shadows.ShadowNativeByteBuffer
import io.github.baba983.nativedo.harness.shadows.ShadowRLottieNative
import io.github.baba983.nativedo.harness.shadows.ShadowSQLiteCursor
import io.github.baba983.nativedo.harness.shadows.ShadowSQLiteDatabase
import io.github.baba983.nativedo.harness.shadows.ShadowSQLitePreparedStatement
import io.github.baba983.nativedo.harness.shadows.ShadowUtilities
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.MediaDataController
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble
import org.telegram.ui.Components.RecyclerListView
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * A floor's reactions, drawn from the pictures the site names.
 *
 * <p>The failures this pins: the chip looks its emoticon up in upstream's reaction table and draws
 * whatever document that entry carries, so an id the site gave no icon for, or one whose icon does
 * not arrive, used to draw a bare count - indistinguishable from the other. Each chip's own picture
 * is read back, so the mapped id has to show the site's own (transparent) image and the two kinds of
 * missing icon have to show a transparent glyph/label, never an invented colour block standing in for
 * a picture the site never sent.</p>
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = ApplicationLoader::class,
    instrumentedPackages = ["org.telegram"],
    qualifiers = "w411dp-h914dp-xxhdpi",
    shadows = [
        ShadowConnectionsManager::class,
        ShadowNativeByteBuffer::class,
        ShadowUtilities::class,
        ShadowMediaController::class,
        ShadowIntro::class,
        ShadowMrzRecognizer::class,
        ShadowRLottieNative::class,
        ShadowAnimatedFileNative::class,
        ShadowSQLiteDatabase::class,
        ShadowSQLiteCursor::class,
        ShadowSQLitePreparedStatement::class,
    ],
)
internal class ReactionEmojiTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    /**
     * Ids of this run's own, short so the drawn fallback label is legible in the chip. Upstream's
     * reaction table and the file cache outlive a test class, so unknown ids (which become their own
     * emoticon) keep a fallback one test wrote from leaking into another's chip.
     */
    private val runToken = System.nanoTime().toString().takeLast(3)
    private val mappedId = "m$runToken"
    private val failedId = "f$runToken"
    private val unmappedId = "u$runToken"

    private val mappedPath = "emoji/mapped$runToken.png"
    private val failedPath = "emoji/failed$runToken.png"
    private val htmlId = "h$runToken"
    private val corruptId = "c$runToken"
    private val htmlPath = "emoji/notapicture$runToken.html"
    private val corruptPath = "emoji/corrupt$runToken.png"

    /** The colour only the site's own picture has, so a fallback can never be mistaken for it. */
    private val servedMagenta = Color.rgb(255, 0, 255)

    /** The colour of the picture that replaces a corrupt cache entry. */
    private val servedCyan = Color.rgb(0, 255, 255)

    @Before
    fun setUp() {
        site.start()
        // The site's own icon is a picture with a transparent surround, the shape a real emoji icon
        // has - not an opaque block that would be indistinguishable from an invented fallback tile.
        site.serveImage(mappedPath, emojiPng(servedMagenta, 96))
        // failedPath is deliberately not served: the site answers it 404.
        // A 200 whose body is not a picture at all, and a picture the cache already holds bad
        // bytes for: both must end as the reaction's fallback, and the second must be replaced.
        site.serveImage(htmlPath, "<html>not a picture</html>".toByteArray(), contentType = "text/html")
        site.serveImage(corruptPath, emojiPng(servedCyan, 96))
        site.serveTopic(TOPIC_ID) { post ->
            post.put("cooked", "<p>反应如下。</p>")
            post.put("reaction_users_count", 6)
            post.put("reactions", JSONArray().apply {
                put(JSONObject().put("id", mappedId).put("count", 3).put("icon", site.url(mappedPath)))
                put(JSONObject().put("id", failedId).put("count", 2).put("icon", site.url(failedPath)))
                put(JSONObject().put("id", unmappedId).put("count", 1))
            })
        }
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun aMappedReactionDrawsTheSitesPictureAndTheMissingOnesDrawTheirOwnFallback() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Reactions"))
        harness.await("the topic to load") { rowCount() == 1 }

        val mappedFile = DiscourseReactionEmoji.file(site.url(mappedPath))
        harness.await("the site's picture to land on disk") { mappedFile.exists() && mappedFile.length() > 0 }
        // The registration is what the chip draws from, and it happens after the bytes are written
        // (and, for the fallbacks, drawn) on another thread, so the wait is for the table entries,
        // not only the files.
        harness.await("the ids to be handed to upstream's table") {
            val table = MediaDataController.getInstance(0).getReactionsMap()
            table[mappedId] != null && table[failedId] != null && table[unmappedId] != null
        }

        val table = MediaDataController.getInstance(0).getReactionsMap()
        assertNotNull("the mapped id was handed to upstream's table", table[mappedId])
        assertNotNull("the failed download was handed a fallback", table[failedId])
        assertNotNull("the id the site named no icon for was handed a fallback", table[unmappedId])
        assertEquals(
            "the fallback entry carries the reaction's readable label as its description",
            io.github.baba983.nativedo.core.model.PostReactionRendering.fallbackLabel(failedId),
            table[failedId]!!.title,
        )
        assertFalse(
            "the mapped id keeps the site's picture and is not downgraded to a fallback",
            DiscourseReactionEmoji.fallbackFile(mappedId).exists(),
        )

        // The two missing icons fall back to a transparent tile, not an opaque invented block.
        val failedFallbackFile = DiscourseReactionEmoji.fallbackFile(failedId)
        val unmappedFallbackFile = DiscourseReactionEmoji.fallbackFile(unmappedId)
        harness.await("the missing icons to draw a fallback") {
            failedFallbackFile.exists() && unmappedFallbackFile.exists()
        }
        val failedFallback = BitmapFactory.decodeFile(failedFallbackFile.absolutePath)!!
        val unmappedFallback = BitmapFactory.decodeFile(unmappedFallbackFile.absolutePath)!!
        assertTrue("the failed download's fallback is transparent, not an opaque tile",
            hasTransparency(failedFallback))
        assertTrue("the unmapped id's fallback is transparent, not an opaque tile",
            hasTransparency(unmappedFallback))
        assertTrue("the failed download's fallback still draws something readable",
            opaqueCount(failedFallback) > 0)
        assertTrue("the unmapped id's fallback still draws something readable",
            opaqueCount(unmappedFallback) > 0)

        // Each chip decodes its own document; the wait is for the loader, not a fixed number of drains.
        harness.await("the chips to draw their pictures") {
            harness.drawApp()
            val cell = firstCell()
            chip(cell, mappedId)?.imageReceiver?.bitmap != null &&
                chip(cell, failedId)?.imageReceiver?.bitmap != null &&
                chip(cell, unmappedId)?.imageReceiver?.bitmap != null
        }

        val cell = firstCell()
        val mappedBitmap = chip(cell, mappedId)!!.imageReceiver!!.bitmap!!
        val failedBitmap = chip(cell, failedId)!!.imageReceiver!!.bitmap!!
        val unmappedBitmap = chip(cell, unmappedId)!!.imageReceiver!!.bitmap!!

        assertTrue(
            "the mapped reaction drew the site's own picture",
            colourCount(mappedBitmap, servedMagenta) > 0,
        )
        assertTrue(
            "the site's picture is the transparent icon, not a filled block",
            hasTransparency(mappedBitmap),
        )
        println(
            "reaction-emoji: mapped=${mappedFile.name} magentaPixels=${colourCount(mappedBitmap, servedMagenta)} " +
                "failedFallback=${opaqueCount(failedFallback)}px unmappedFallback=${opaqueCount(unmappedFallback)}px",
        )
        // The three states in one frame: the mapped chip shows the site's picture, the 404'd and
        // icon-less chips each show their own transparent glyph/label.
        harness.renderApp("reactions-emoji-states")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * Two ways a reaction's icon can look fetched but not be drawable: the site answers 200 with
     * something that is not a picture, or the cache already holds bytes that are not one. Neither
     * may be published - the chip would ask the loader for it, the decode would fail, and the
     * reaction would read as a bare count with no fallback. The 200 case falls back; the corrupt
     * cache is replaced by the picture the site really serves and that picture is drawn.
     */
    @Test
    fun a200ThatIsNotAPictureFallsBackAndACorruptCacheIsReplacedByTheRealPicture() {
        site.serveTopic(BAD_PICTURE_TOPIC_ID) { post ->
            post.put("cooked", "<p>坏图。</p>")
            post.put("reaction_users_count", 2)
            post.put("reactions", JSONArray().apply {
                put(JSONObject().put("id", htmlId).put("count", 1).put("icon", site.url(htmlPath)))
                put(JSONObject().put("id", corruptId).put("count", 1).put("icon", site.url(corruptPath)))
            })
        }

        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        // Seed the corrupt cache once the app's cache directories exist, before the topic asks for
        // the picture, so the loader finds bytes that are not a picture where it looks.
        val corruptFile = DiscourseReactionEmoji.file(site.url(corruptPath))
        corruptFile.parentFile?.mkdirs()
        corruptFile.writeBytes("not a picture either".toByteArray())
        harness.present(TopicFragment.forTopic(BAD_PICTURE_TOPIC_ID, "Bad pictures"))
        harness.await("the topic to load") { rowCount() == 1 }

        val servedCyanPng = emojiPng(servedCyan, 96)
        harness.await("the corrupt cache to be replaced by the site's picture") {
            corruptFile.readBytes().contentEquals(servedCyanPng)
        }
        harness.await("the chips to draw their pictures") {
            harness.drawApp()
            val cell = firstCell()
            chip(cell, htmlId)?.imageReceiver?.bitmap != null &&
                chip(cell, corruptId)?.imageReceiver?.bitmap != null
        }

        val cell = firstCell()
        val htmlBitmap = chip(cell, htmlId)!!.imageReceiver!!.bitmap!!
        val corruptBitmap = chip(cell, corruptId)!!.imageReceiver!!.bitmap!!
        val htmlFallbackFile = DiscourseReactionEmoji.fallbackFile(htmlId)
        assertTrue("the 200 that is not a picture fell back to a transparent tile",
            htmlFallbackFile.exists() && hasTransparency(BitmapFactory.decodeFile(htmlFallbackFile.absolutePath)!!))
        assertTrue(
            "the real picture replaced the corrupt cache and is drawn",
            colourCount(corruptBitmap, servedCyan) > 0,
        )
        assertTrue(
            "the site's transparent picture is what the corrupt chip drew",
            hasTransparency(corruptBitmap),
        )
        assertFalse(
            "and no fallback was used where the picture arrived",
            DiscourseReactionEmoji.fallbackFile(corruptId).exists(),
        )
        println(
            "reaction-emoji-cache: htmlFallback=${htmlFallbackFile.length()} " +
                "corruptCacheReplaced=${corruptFile.length()} cyanPixels=${colourCount(corruptBitmap, servedCyan)}",
        )
        harness.renderApp("reactions-emoji-bad-cache")
        harness.assertNoTelegramSideEffects()
    }

    /** The replies endpoint omits icon URLs; the same catalog artwork must still reach its chips. */
    @Test
    fun replyChipsUseTheSameSiteArtworkAsTopicChips() {
        site.serveJson("emojis.json", JSONObject().put("custom", JSONArray().put(
            JSONObject().put("name", mappedId).put("url", site.url(mappedPath)),
        )).toString())
        val reply = JSONObject(harness.fixture("topic-detail.json"))
            .getJSONObject("post_stream").getJSONArray("posts").getJSONObject(0)
            .put("id", 1002).put("post_number", 2).put("reply_to_post_number", 1)
            .put("cooked", "<p>回复里的回应图标</p>")
            .put("reactions", JSONArray().put(JSONObject().put("id", mappedId).put("count", 2)))
        site.serveJson("posts/1001/replies", JSONArray().put(reply).toString())
        harness.bootApp()
        harness.await("the topic list") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Reaction artwork"))
        harness.await("the topic chip") {
            harness.drawApp()
            laidOutCells().firstOrNull()?.let { chip(it, mappedId)?.imageReceiver?.bitmap }?.let {
                colourCount(it, servedMagenta) > 0
            } == true
        }
        val topic = harness.visibleFragment() as TopicFragment
        val footerCell = firstCell()
        footerCell.delegate.didPressCommentButton(footerCell)
        harness.await("the reply page") { harness.visibleFragment() !== topic }
        harness.await("the reply rows") { harness.layoutApp(); rowCount() == 2 }
        harness.await("the reply chip's decoded artwork") {
            harness.drawApp()
            laidOutCells().firstOrNull { it.messageObject.id == 1002 }
                ?.let { chip(it, mappedId)?.imageReceiver?.bitmap } != null
        }
        val bitmap = chip(laidOutCells().first { it.messageObject.id == 1002 }, mappedId)!!.imageReceiver!!.bitmap!!
        harness.renderApp("comments-site-reaction-artwork")
        assertTrue("the reply draws the same site picture, not a system-emoji fallback",
            colourCount(bitmap, servedMagenta) > 0)
        harness.assertNoTelegramSideEffects()
    }

    private fun firstCell(): ChatMessageCell =
        laidOutCells().firstOrNull { it.messageObject != null }
            ?: throw AssertionError("the topic drew no message cell")

    private fun chip(cell: ChatMessageCell, id: String): ReactionsLayoutInBubble.ReactionButton? =
        cell.reactionsLayoutInBubble.reactionButtons.firstOrNull { it.key == id }

    /** A picture with a transparent surround, the shape a real emoji icon has. */
    private fun emojiPng(colour: Int, size: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = colour }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f * 0.4f, paint)
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        return bytes.toByteArray()
    }

    /** Whether the picture has any fully transparent pixel - a plain block would not. */
    private fun hasTransparency(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.any { pixel -> (pixel ushr 24) == 0 }
    }

    /** How many pixels of the picture are not fully transparent. */
    private fun opaqueCount(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { pixel -> (pixel ushr 24) != 0 }
    }

    /** How many pixels of [bitmap] are within a small distance of [colour]. */
    private fun colourCount(bitmap: Bitmap, colour: Int): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { pixel ->
            kotlin.math.abs(Color.red(pixel) - Color.red(colour)) <= 24 &&
                kotlin.math.abs(Color.green(pixel) - Color.green(colour)) <= 24 &&
                kotlin.math.abs(Color.blue(pixel) - Color.blue(colour)) <= 24
        }
    }

    private fun rowCount(): Int = listView()?.adapter?.itemCount ?: 0

    private fun laidOutCells(): List<ChatMessageCell> {
        val list = listView() ?: return emptyList()
        return (0 until list.childCount)
            .map { list.getChildAt(it) }
            .filterIsInstance<ChatMessageCell>()
    }

    private fun listView(): RecyclerListView? = contentListOf(harness.activity)

    private companion object {
        const val TOPIC_ID = 707L
        const val BAD_PICTURE_TOPIC_ID = 708L
    }
}
