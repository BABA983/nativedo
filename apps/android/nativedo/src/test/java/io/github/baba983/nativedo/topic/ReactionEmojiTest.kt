package io.github.baba983.nativedo.topic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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
 * is read back, so the mapped id has to show the site's own image and the two kinds of missing icon
 * have to show a drawn fallback, each one its own.</p>
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
        site.serveImage(mappedPath, solidPng(servedMagenta, 96))
        // failedPath is deliberately not served: the site answers it 404.
        // A 200 whose body is not a picture at all, and a picture the cache already holds bad
        // bytes for: both must end as the reaction's fallback, and the second must be replaced.
        site.serveImage(htmlPath, "<html>not a picture</html>".toByteArray(), contentType = "text/html")
        site.serveImage(corruptPath, solidPng(servedCyan, 96))
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
        // The fallback each missing id drew is read from the tile the app itself produced, so the
        // oracle is that id's own drawing and not "some coloured pixel".
        val failedFallback = dominantColour(DiscourseReactionEmoji.fallbackFile(failedId))
        val unmappedFallback = dominantColour(DiscourseReactionEmoji.fallbackFile(unmappedId))
        assertTrue("the failed download drew its fallback", colourCount(failedBitmap, failedFallback) > 0)
        assertTrue("the unmapped id drew its fallback", colourCount(unmappedBitmap, unmappedFallback) > 0)
        assertTrue(
            "the two fallbacks are different pictures",
            failedFallback != unmappedFallback,
        )
        assertTrue(
            "and neither is the site's own picture",
            failedFallback != servedMagenta && unmappedFallback != servedMagenta,
        )
        println(
            "reaction-emoji: mapped=${mappedFile.name} magentaPixels=${colourCount(mappedBitmap, servedMagenta)} " +
                "failedFallback=${Integer.toHexString(failedFallback)} pixels=${colourCount(failedBitmap, failedFallback)} " +
                "unmappedFallback=${Integer.toHexString(unmappedFallback)} pixels=${colourCount(unmappedBitmap, unmappedFallback)}",
        )
        // The three states in one frame: the mapped chip shows the site's picture, the 404'd and
        // icon-less chips each show their own drawn fallback.
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

        val servedCyanPng = solidPng(servedCyan, 96)
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
        val htmlFallback = dominantColour(DiscourseReactionEmoji.fallbackFile(htmlId))
        assertTrue(
            "the 200 that is not a picture drew its fallback",
            colourCount(htmlBitmap, htmlFallback) > 0,
        )
        assertTrue(
            "the real picture replaced the corrupt cache and is drawn",
            colourCount(corruptBitmap, servedCyan) > 0,
        )
        assertFalse(
            "and no fallback was used where the picture arrived",
            DiscourseReactionEmoji.fallbackFile(corruptId).exists(),
        )
        println(
            "reaction-emoji-cache: htmlFallback=${Integer.toHexString(htmlFallback)} " +
                "pixels=${colourCount(htmlBitmap, htmlFallback)} " +
                "corruptCacheReplaced=${corruptFile.length()} cyanPixels=${colourCount(corruptBitmap, servedCyan)}",
        )
        harness.renderApp("reactions-emoji-bad-cache")
        harness.assertNoTelegramSideEffects()
    }

    private fun firstCell(): ChatMessageCell =
        laidOutCells().firstOrNull { it.messageObject != null }
            ?: throw AssertionError("the topic drew no message cell")

    private fun chip(cell: ChatMessageCell, id: String): ReactionsLayoutInBubble.ReactionButton? =
        cell.reactionsLayoutInBubble.reactionButtons.firstOrNull { it.key == id }

    private fun solidPng(colour: Int, size: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(colour)
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        return bytes.toByteArray()
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

    /** The colour most of a drawn tile is: its background, which is the id's own. */
    private fun dominantColour(file: File): Int {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            ?: throw AssertionError("the fallback tile is not a picture: ${file.name}")
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.toList().groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key
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
