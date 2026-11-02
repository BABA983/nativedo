package io.github.baba983.nativedo.topic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.text.Spanned
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import io.github.baba983.nativedo.harness.UpstreamUiHarness
import io.github.baba983.nativedo.harness.contentListOf
import io.github.baba983.nativedo.harness.contentRowCount
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.MessageObject
import org.telegram.messenger.RichMessageLayout
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tl.TL_iv
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.URLSpanReplacement
import org.telegram.ui.iv.RichTextStyle
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * A post that links to a page the site rendered a onebox for: the link's own preview stays in the
 * post, in one rich bubble with the words around it, where the reader wrote the link.
 *
 * <p>The failure this pins: the site's onebox used to be lifted out of the body and drawn as a
 * second message - a link card of its own - so a post read as two bubbles and the link left the
 * paragraph it was written in. The preview's parts (the linked page's title, the description the
 * site found, the thumbnail) must survive in the same bubble, in the reader's order and still
 * clickable, while an ordinary inline link stays ordinary. Only a poll, which upstream cannot draw
 * as rich text, is a cut of its own.</p>
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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

    /**
     * One link with a full onebox: the post is one rich bubble, the text before and after the link
     * stays in it, and the preview - the site's name, the thumbnail, the page's title and its
     * description - sits between them in that order, with the bare link line the card stood for gone.
     */
    @Test
    fun aOneboxStaysInThePostAsOneBubbleWithItsPreviewAroundTheText() {
        val link = site.url("docs/start")
        val thumbnail = "uploads/onebox-thumb.png"
        site.serveImage(thumbnail, solidPng(690, 362, Color.CYAN))
        val cooked = "<p>Service notes.</p>" +
            onebox(link, site.url(thumbnail), "Getting started", "How the service is set up.") +
            "<p>End of the post.</p>"
        site.serveCooked(TOPIC_ID, cooked)

        // The split, on its own: the body is one run - the preview is in it, not a segment beside it -
        // and the bare link line the card stood for is gone.
        val segments = DiscoursePostBody.segments(cooked)
        assertEquals("the post is one run, not a run and a card beside it", 1, segments.size)
        assertFalse("the bare link line is not left behind", segments[0].html.contains("class=\"onebox\""))
        assertTrue("the note before the link is kept", segments[0].html.contains("Service notes."))
        assertTrue("the text after the link is kept", segments[0].html.contains("End of the post."))

        open()
        val message = onlyCell().messageObject
        assertTrue("the post is one rich bubble", message.type == MessageObject.TYPE_ARTICLE)
        assertTrue("carrying a rich body", message.messageOwner.rich_message != null)
        assertFalse(
            "the link is not a message of its own",
            MessageObject.getMedia(message.messageOwner) is TLRPC.TL_messageMediaWebPage,
        )
        assertEquals(
            "the bubble holds the text and the preview's parts, in the reader's order",
            listOf(
                "Service notes.",
                "docs.example",
                "<photo>",
                "Getting started",
                "How the service is set up.",
                "End of the post.",
            ),
            blockShape(message),
        )
        assertTrue(
            "the linked page's title stays a clickable link: ${linkUrls(message)}",
            linkUrls(message).contains(link),
        )
        assertEquals(
            "the preview's thumbnail is carried by the same message",
            1,
            message.messageOwner.rich_message.photos.size,
        )
        assertFalse("and the bubble offers to hand the post on", message.needDrawShareButton())

        harness.await("the thumbnail to be downloaded") {
            site.recordedRequests.any { it.contains(thumbnail) }
        }
        harness.await("the thumbnail to be drawn in the post", iterations = 8_000) {
            cyanPixels(harness.renderApp("post-onebox")) > 1000
        }
        harness.assertNoTelegramSideEffects()
    }

    /** Two oneboxes in one post: both previews stay in the one bubble, in order, and both are live. */
    @Test
    fun twoOneboxesInOnePostBothPreviewInTheSameBubble() {
        val first = site.url("docs/start")
        val second = site.url("blog/release")
        val unpreviewed = site.url("docs/other")
        site.serveImage("uploads/first.png", solidPng(400, 200, Color.CYAN))
        site.serveImage("uploads/second.png", solidPng(400, 200, Color.MAGENTA))
        val cooked = "<p>Two links: <a class=\"onebox\" href=\"$unpreviewed\">other docs</a></p>" +
            onebox(first, site.url("uploads/first.png"), "First page", "First description.") +
            "<p>and</p>" +
            onebox(second, site.url("uploads/second.png"), "Second page", "Second description.") +
            "<p>Done.</p>"
        site.serveCooked(TOPIC_ID, cooked)

        assertEquals("both previews stay in one run", 1, DiscoursePostBody.segments(cooked).size)

        open()
        val message = onlyCell().messageObject
        assertEquals(
            "both previews and their surrounding text retain their authored order",
            listOf("Two links: other docs", "docs.example", "<photo>", "First page", "First description.",
                "and", "docs.example", "<photo>", "Second page", "Second description.", "Done."),
            blockShape(message),
        )
        assertEquals("both thumbnails are carried", 2, message.messageOwner.rich_message.photos.size)
        assertEquals("only each preview's own placeholder is removed", setOf(unpreviewed, first, second), linkUrls(message).toSet())

        harness.await("both thumbnails to be requested") {
            site.recordedRequests.any { it.contains("uploads/first.png") } &&
                site.recordedRequests.any { it.contains("uploads/second.png") }
        }
        harness.assertNoTelegramSideEffects()
    }

    /**
     * An ordinary inline link is not a preview: it stays in its sentence as a clickable link, draws
     * no picture, and opens no card - the distinction the split used to erase.
     */
    @Test
    fun anOrdinaryInlineLinkStaysPlainTextWithNoPreview() {
        val link = site.url("docs/start")
        val cooked = "<p>See <a href=\"$link\">the docs</a> for the details.</p>"
        site.serveCooked(TOPIC_ID, cooked)

        assertEquals("an ordinary link is not a split point", 1, DiscoursePostBody.segments(cooked).size)

        open()
        val message = onlyCell().messageObject
        assertEquals(
            "the link stays inside its sentence",
            listOf("See the docs for the details."),
            blockShape(message),
        )
        assertTrue("and is still clickable", linkUrls(message).contains(link))
        assertTrue("but draws no preview picture", message.messageOwner.rich_message.photos.isEmpty())
        assertFalse(
            "and opens no link card",
            MessageObject.getMedia(message.messageOwner) is TLRPC.TL_messageMediaWebPage,
        )
        harness.assertNoTelegramSideEffects()
    }

    // -------------------------------------------------------------------------------- driving the flow

    /** The site's own markup for a link it oneboxed: the bare link line, then the card. */
    private fun onebox(link: String, thumbnailUrl: String, title: String, description: String): String = """
        <p><a href="$link" class="onebox" target="_blank">$link</a></p>
        <aside class="onebox allowlistedgeneric" data-onebox-src="$link">
          <header class="source">
            <img src="${site.url("uploads/site-icon.png")}" class="site-icon" width="48" height="48">
            <a href="$link" target="_blank">docs.example</a>
          </header>
          <article class="onebox-body">
            <div class="aspect-image" style="--aspect-ratio:690/362;">
              <img src="$thumbnailUrl" class="thumbnail" width="690" height="362">
            </div>
            <h3><a href="$link" target="_blank">$title</a></h3>
            <p>$description</p>
          </article>
          <div class="onebox-metadata"></div>
          <div style="clear: both"></div>
        </aside>
    """.trimIndent()

    private fun open() {
        harness.bootApp()
        harness.await("the topic list to load") { rowCount() == 2 }
        harness.present(TopicFragment.forTopic(TOPIC_ID, "Onebox"))
        harness.await("the topic to load") {
            harness.layoutApp()
            rowCount() == 1 && laidOutCells().isNotEmpty()
        }
    }

    /** The one bubble the post draws; a second bubble is the failure this test exists for. */
    private fun onlyCell(): ChatMessageCell {
        harness.layoutApp()
        val cells = laidOutCells()
        assertEquals("the post must be one bubble", 1, cells.size)
        return cells.first()
    }

    /** Each block of the rich body as text, with a photo block named so its place is visible. */
    private fun blockShape(message: MessageObject): List<String> =
        message.messageOwner.rich_message.blocks.map { block ->
            when (block) {
                is TL_iv.pageBlockPhoto -> "<photo>"
                else -> RichMessageLayout.getString(block.text)
            }
        }

    /** The clickable URLs the bubble's text carries, the way upstream's own spans read them. */
    private fun linkUrls(message: MessageObject): List<String> =
        message.messageOwner.rich_message.blocks.flatMap { linkUrls(it.text) }

    private fun linkUrls(text: TL_iv.RichText?): List<String> {
        if (text == null) return emptyList()
        val styled = RichTextStyle.toSpannable(text)
        if (styled !is Spanned) return emptyList()
        return styled.getSpans(0, styled.length, URLSpanReplacement::class.java).map { it.getURL() }
    }

    private fun solidPng(width: Int, height: Int, color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        return bytes.toByteArray()
    }

    private fun cyanPixels(file: File): Int {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return 0
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { Color.red(it) < 100 && Color.blue(it) > 200 && Color.green(it) > 200 }
    }

    private fun rowCount(): Int = contentRowCount(harness)

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
