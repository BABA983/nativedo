package io.github.baba983.nativedo.topic

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
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
import org.telegram.messenger.MessageObject
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Components.RecyclerListView

/**
 * A post that links to a page the site rendered a onebox for: the card the site drew becomes
 * upstream's own link preview, instead of its parts flowing into the post's text.
 *
 * <p>The failure this pins: upstream's rich body has no preview block at all, so a client that left
 * the `aside.onebox` in the markup shows the site name, the title and the description as plain
 * paragraphs - and the bare link line the card stands for as a link nobody needs, next to a
 * thumbnail that is either missing or drawn full width.</p>
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
internal class OneboxTest {

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
    fun theSitesOneboxBecomesUpstreamsLinkPreviewAndTheBareLinkLeavesTheText() {
        val link = site.url("docs/start")
        val thumbnail = "uploads/onebox-thumb.png"
        site.serveImage(thumbnail, onePixelPng())
        val cooked = """
            <p>Service notes.</p>
            <p><a href="$link" class="onebox" target="_blank">$link</a></p>
            <aside class="onebox allowlistedgeneric" data-onebox-src="$link">
              <header class="source">
                <img src="${site.url("uploads/site-icon.png")}" class="site-icon" width="48" height="48">
                <a href="$link" target="_blank">docs.example</a>
              </header>
              <article class="onebox-body">
                <div class="aspect-image" style="--aspect-ratio:690/362;">
                  <img src="${site.url(thumbnail)}" class="thumbnail" width="690" height="362">
                </div>
                <h3><a href="$link" target="_blank">Getting started</a></h3>
                <p>How the service is set up.</p>
              </article>
              <div class="onebox-metadata"></div>
              <div style="clear: both"></div>
            </aside>
            <p>End of the post.</p>
        """.trimIndent()

        // The split, on its own: the card is a segment of its own, and the bare link line the card
        // stands for is not left behind in the text next to it.
        val segments = DiscoursePostBody.segments(cooked)
        assertEquals("text, card, text", 3, segments.size)
        assertFalse("the bare link line is not kept", segments[0].html.contains("class=\"onebox\""))
        assertTrue("the note is kept", segments[0].html.contains("Service notes."))
        assertTrue("the card is a segment of its own", segments[1].isOnebox)

        site.serveCooked(TOPIC_ID, cooked)
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Onebox"))
        harness.await("the topic to load") { rowCount() == 3 }

        val cells = laidOutCells()
        assertEquals("text, card, text", 3, cells.size)
        val card = MessageObject.getMedia(cells[1].messageObject!!.messageOwner)
        assertTrue("the card is upstream's link preview", card is TLRPC.TL_messageMediaWebPage)
        val webpage = (card as TLRPC.TL_messageMediaWebPage).webpage
        assertTrue(webpage is TLRPC.TL_webPage)
        val page = webpage as TLRPC.TL_webPage
        assertEquals("the site's own name", "docs.example", page.site_name)
        assertEquals("the linked page's title", "Getting started", page.title)
        assertEquals("and the description the site found", "How the service is set up.", page.description)
        assertEquals("pointing at the link the card stands for", link, page.url)
        assertEquals("drawn as an article, so the thumbnail is the small card image", "article", page.type)
        assertNotNull("with the thumbnail the site rendered", page.photo)
        assertTrue(
            "the card stands on its own: the bare link is not repeated as the bubble's text",
            cells[1].messageObject!!.messageText.isNullOrEmpty(),
        )
        assertFalse(
            "and no bubble offers to hand a post on",
            cells.any { it.messageObject!!.needDrawShareButton() },
        )

        harness.await("the thumbnail to be downloaded") {
            site.recordedRequests.any { it.contains(thumbnail) }
        }
        println("onebox: requests = " + site.recordedRequests)
        harness.drawApp()
        harness.renderApp("post-onebox")
        harness.assertNoTelegramSideEffects()
    }

    /** A 1x1 PNG: enough for the loader to have a file to draw. */
    private fun onePixelPng(): ByteArray {
        val bitmap = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(90, 130, 200))
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

    private fun listView(): RecyclerListView? =
        contentListOf(harness.activity)

    private companion object {
        const val TOPIC_ID = 3001L
    }
}
