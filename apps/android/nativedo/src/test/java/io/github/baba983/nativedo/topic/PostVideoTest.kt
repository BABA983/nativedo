package io.github.baba983.nativedo.topic

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.MessageObject
import org.telegram.tgnet.tl.TL_iv
import org.telegram.ui.Cells.ChatMessageCell
import java.io.File

/**
 * A post that carries a video the site hosts.
 *
 * <p>The failure this pins: a `<video>` is markup upstream's own importer already knows - it has a
 * block for one - but the block can only draw a video it holds, and this client holds files, not
 * Telegram media. A bridge that leaves the tag alone therefore drops the clip out of the post
 * entirely, and one that hands upstream the site's URL gets a block that never finds its file.</p>
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
internal class PostVideoTest {

    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
    }

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { context -> site.authenticatedContainer(context) }
        site.stop()
    }

    @Test
    fun aPostVideoBecomesUpstreamsVideoBlockWithItsCoverAndItsFile() {
        val clip = "uploads/clip.mp4"
        val poster = "uploads/clip-poster.png"
        site.serveImage(clip, ByteArray(2048) { 7 }, contentType = "video/mp4")
        site.serveImage(poster, onePixelPng())
        site.serveCooked(TOPIC_ID, videoPost(clip, poster))

        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "A clip"))
        harness.await("the topic to load") { rowCount() == 1 }

        val message = laidOutCells().first { it.messageObject != null }.messageObject!!
        val blocks = message.messageOwner.rich_message!!.blocks
        val videoBlock = blocks.filterIsInstance<TL_iv.pageBlockVideo>().firstOrNull()
            ?: throw AssertionError("the video did not arrive as a video block: " + blocks.map { it.javaClass.simpleName })

        val document = message.messageOwner.rich_message!!.documents
            .firstOrNull { it.id == videoBlock.video_id }
            ?: throw AssertionError("the block names a video the message does not carry")
        val attribute = document.attributes.filterIsInstance<org.telegram.tgnet.TLRPC.TL_documentAttributeVideo>()
            .firstOrNull() ?: throw AssertionError("the file is not a video: no video attribute")
        assertEquals("the frame the tag declared", 640, attribute.w)
        assertEquals("and its height", 360, attribute.h)
        assertEquals("the cover travels as the document's own thumbnail", 1, document.thumbs.size)

        // Both the clip and its cover were fetched by the session's client; a clip this client did
        // not fetch would leave the play button pointing at an empty file.
        harness.await("the clip to be fetched with the post") {
            site.recordedRequests.any { it.contains(clip) }
        }
        assertTrue(
            "the cover the block draws was fetched",
            site.recordedRequests.any { it.contains(poster) },
        )
        harness.await("the clip to land on disk") {
            DiscourseImageBridge.videoFileExists(videoBlock.video_id)
        }

        harness.drawApp()
        harness.renderApp("post-video")
        harness.assertNoTelegramSideEffects()
    }

    @Test
    fun aClipThisClientDidNotFetchIsPlayedElsewhereInsteadOfOpeningOnAnEmptyFile() {
        val clip = "uploads/never-arrives.mp4"
        site.serveCooked(TOPIC_ID, videoPost(clip, poster = null))

        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        val fragment = TopicFragment.forTopic(TOPIC_ID, "A clip")
        harness.present(fragment)
        harness.await("the topic to load") { rowCount() == 1 }

        val cell = laidOutCells().first { it.messageObject != null }
        val block = cell.messageObject!!.messageOwner.rich_message!!.blocks
            .filterIsInstance<TL_iv.pageBlockVideo>().first()

        val opened = ArticlePhotoViewer.open(fragment, cell.messageObject!!, block)
        val started = org.robolectric.Shadows.shadowOf(harness.activity).nextStartedActivity
        assertTrue("the tap was handled", opened)
        assertEquals(
            "a clip with no file is streamed from the site's own address",
            site.url(clip),
            started?.data.toString(),
        )
        harness.assertNoTelegramSideEffects()
    }

    /** A floor whose only content is one video the site hosts, exactly as its cook writes one. */
    private fun videoPost(clip: String, poster: String?): String {
        val posterAttribute = poster?.let { " poster=\"${site.url(it)}\"" }.orEmpty()
        return """
            <p>A clip.</p>
            <div class="video-container">
            <video width="640" height="360" preload="metadata" controls$posterAttribute>
            <source src="${site.url(clip)}" type="video/mp4">
            <a href="${site.url(clip)}">clip.mp4</a>
            </video>
            </div>
        """.trimIndent()
    }

    /** A 1x1 PNG: enough for the loader to have a cover to draw. */
    private fun onePixelPng(): ByteArray {
        val bitmap = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(200, 120, 60))
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    private fun rowCount(): Int = listView()?.adapter?.itemCount ?: 0

    private fun laidOutCells(): List<ChatMessageCell> {
        val list = listView() ?: return emptyList()
        return (0 until list.childCount)
            .map { list.getChildAt(it) }
            .filterIsInstance<ChatMessageCell>()
    }

    private fun listView(): RecyclerView? {
        val content = harness.activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        return findRecycler(content)
    }

    private fun findRecycler(view: android.view.View): RecyclerView? {
        if (view is RecyclerView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findRecycler(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private companion object {
        const val TOPIC_ID = 4001L
    }
}
