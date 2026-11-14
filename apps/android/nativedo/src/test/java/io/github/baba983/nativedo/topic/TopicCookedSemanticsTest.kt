package io.github.baba983.nativedo.topic

import android.os.SystemClock
import android.text.Spanned
import android.view.MotionEvent
import io.github.baba983.nativedo.app.NativeDo
import io.github.baba983.nativedo.core.di.AppContainer
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.RichMessageLayout
import org.telegram.tgnet.tl.TL_iv
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.TextStyleSpan
import org.telegram.ui.Components.URLSpanReplacement
import org.telegram.ui.iv.RichTextStyle

/**
 * The shapes a Discourse cook leaves that upstream's importer cannot read on its own.
 *
 * <p>Failure modes pinned here: a `div.spoiler` reaching the importer as an ordinary paragraph (the
 * secret is drawn openly and there is nothing to reveal); a block spoiler flattening the paragraph
 * it wraps; a fenced block losing the `lang-*` class the site wrote (so upstream highlights nothing
 * and never names the language) while its text must keep its exact whitespace; an unsupported
 * `audio`/`iframe`/`embed`/`object` importing empty; and a `javascript:`/`data:` source becoming a
 * clickable URL.</p>
 *
 * <p>Every body enters through the real topic API and is asserted on the production model and the
 * stock cell's own layout - the drawn spoiler cover and its reveal are the observable, not a mirror
 * of the normalizer's output.</p>
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = ApplicationLoader::class,
    instrumentedPackages = ["org.telegram"],
    qualifiers = "w411dp-h914dp-xxhdpi",
    shadows = [ShadowConnectionsManager::class, ShadowNativeByteBuffer::class,
        ShadowUtilities::class, ShadowMediaController::class, ShadowIntro::class,
        ShadowMrzRecognizer::class, ShadowRLottieNative::class, ShadowAnimatedFileNative::class,
        ShadowSQLiteDatabase::class, ShadowSQLiteCursor::class, ShadowSQLitePreparedStatement::class],
)
internal class TopicCookedSemanticsTest {
    private val harness = UpstreamUiHarness()
    private val site = FakeDiscourseSite()

    @After
    fun tearDown() {
        NativeDo.setContainerFactory { AppContainer(it) }
        site.stop()
    }

    /**
     * A block spoiler must keep the paragraph it wraps and reach the importer as spoiler text: the
     * drawn cell covers the secret with a spoiler effect until the reader taps it, and the tap
     * clears the cover without touching the text around it.
     */
    @Test
    fun blockSpoilerKeepsItsParagraphAndCoversTheSecretUntilTapped() {
        open("<p>Above</p><div class='spoiler'><p>launch code is 1234</p></div><p>Below</p>")

        val rich = cell().messageObject.messageOwner.rich_message
        assertEquals("the spoiler must not flatten the paragraphs around it", 3, rich.blocks.size)
        assertEquals("Above", RichMessageLayout.getString(rich.blocks[0].text).toString())
        assertEquals("Below", RichMessageLayout.getString(rich.blocks[2].text).toString())
        assertEquals("launch code is 1234", RichMessageLayout.getString(rich.blocks[1].text).toString())
        assertFalse("ordinary text must not become spoiler text", containsSpoiler(rich.blocks[0].text))
        assertTrue("the secret must reach the importer as spoiler text", containsSpoiler(rich.blocks[1].text))

        val message = cell().messageObject
        assertFalse("the secret must not start revealed", message.isSpoilersRevealed)
        assertTrue("the secret must be covered by a drawn spoiler effect, not shown openly",
            coveredTexts(message.richLayout).isNotEmpty())
        harness.renderApp("cooked-spoiler-hidden")

        val cell = cell()
        tapSpoiler(cell)

        assertTrue("tapping the spoiler must reveal it", message.isSpoilersRevealed)
        assertTrue(
            "the cover must be lifted after reveal",
            coveredTexts(message.richLayout).isEmpty(),
        )
        assertEquals("revealing must not change the secret text", "launch code is 1234",
            RichMessageLayout.getString(rich.blocks[1].text).toString())
        harness.renderApp("cooked-spoiler-revealed")
        harness.assertNoTelegramSideEffects()
    }

    /** An inline spoiler keeps its nested styling and inline emoji, and is still covered. */
    @Test
    fun inlineSpoilerKeepsNestedStylingAndEmoji() {
        open("<p>before <span class='spoiler'>hidden <b>bold</b> " +
            "<img class='emoji' alt=':innocent:' src='/images/emoji/innocent.png'> tail</span> after</p>" +
            "<div class='spoiler'><table><tr><td><img width='240' height='120' src='/secret-photo.png'></td></tr></table></div>" +
            "<div class='spoiler'><p><a href='/navigation'>nested secret<img width='240' height='120' src='/hidden.png'>nested tail</a></p>" +
            "<p><a class='attachment' href='/secret.pdf'>secret.pdf</a></p></div>" +
            "<div class='spoiler'>standalone secret one</div><div class='spoiler'>standalone secret two</div>")

        val blocks = cell().messageObject.messageOwner.rich_message.blocks
        val text = blocks.first().text
        // `getString` walks text nodes only: a custom emoji is artwork carried by its span, not
        // the shortcode it was written as, so the emoji is asserted on the span the cell draws.
        val plain = RichMessageLayout.getString(text).toString()
        assertTrue(
            "the inline run keeps the text on both sides of the emoji: $plain",
            plain.startsWith("before hidden bold") && plain.endsWith("tail after"),
        )
        val drawn = RichTextStyle.toSpannable(text) as Spanned
        assertEquals(
            "the inline emoji survives as a custom-emoji span",
            1,
            drawn.getSpans(0, drawn.length, org.telegram.ui.Components.AnimatedEmojiSpan::class.java).size,
        )
        assertTrue("the inline spoiler run must stay spoiler text", containsSpoiler(text))
        val table = blocks[1] as TL_iv.pageBlockTable
        assertTrue("a hidden table image's fallback link must stay hidden",
            containsSpoiler(table.rows.single().cells.single().text))
        val styled = blocks.mapNotNull { it.text?.let(RichTextStyle::toSpannable) }
        for (secret in listOf("standalone secret one", "standalone secret two")) {
            assertTrue("each block spoiler must retain its paragraph boundary: $secret",
                styled.any { it.toString() == secret })
        }
        for (secret in listOf("nested secret", "nested tail", "secret.pdf", "standalone secret one", "standalone secret two")) {
            val run = styled.firstOrNull { it.contains(secret) } as? Spanned
            assertTrue("the secret must survive as covered text: $secret", run != null)
            val start = run!!.toString().indexOf(secret)
            assertTrue("the entire nested secret must be hidden: $secret",
                run.getSpans(start, start + secret.length, TextStyleSpan::class.java).any {
                    it.isSpoiler && run.getSpanStart(it) <= start && run.getSpanEnd(it) >= start + secret.length
                })
        }
        val covered = cell().messageObject.richLayout.textBlocks.filterIsInstance<RichMessageLayout.Text>()
            .any { it.spoilers.isNotEmpty() }
        assertTrue("the inline spoiler must be covered before reveal", covered)
        harness.assertNoTelegramSideEffects()
    }

    /**
     * A fenced block keeps its language and its exact text: the language travels on the `<pre>`
     * attribute upstream reads, and `lang-auto` stays unlabelled so an auto-detected block is not
     * told the language is literally "auto".
     */
    @Test
    fun codeFenceKeepsItsLanguageAndVerbatimWhitespace() {
        open("<pre><code class='lang-kotlin'>val x = 1\n  indented\nline3\n</code></pre>" +
            "<pre><code class='lang-auto'>plain\n</code></pre>")

        val blocks = cell().messageObject.messageOwner.rich_message.blocks
        val kotlin = blocks[0] as TL_iv.pageBlockPreformatted
        assertEquals("the lang-* class must become the pre's language", "kotlin", kotlin.language)
        assertEquals("the code text and its inner whitespace must be verbatim",
            "val x = 1\n  indented\nline3", RichMessageLayout.getString(kotlin.text).toString())
        val auto = blocks[1] as TL_iv.pageBlockPreformatted
        assertTrue("an auto-detected block must not be labelled with a language",
            auto.language.isNullOrEmpty())
        assertEquals("plain", RichMessageLayout.getString(auto.text).toString())
        harness.renderApp("cooked-code-language")
        harness.assertNoTelegramSideEffects()
    }

    /**
     * An embed upstream cannot draw becomes a readable link: the absolute source and a site-relative
     * one both stay visible and openable, while a `javascript:` source is refused as a link and left
     * as plain, unclickable text.
     */
    @Test
    fun unsupportedEmbedsBecomeReadableLinksAndUnsafeSourcesStayPlainText() {
        open("<p>Audio</p><p><audio src='https://media.example.dev/song.mp3'></audio></p>" +
            "<p>Relative</p><p><iframe src='/uploads/clip.mp4'></iframe></p>" +
            "<p>Unsafe</p><p><iframe src='javascript:alert(1)'></iframe></p>" +
            "<p><embed src='data:text/html,unsafe'></p><p><object></object></p>")

        val rich = cell().messageObject.messageOwner.rich_message
        val urls = rich.blocks.flatMap { linkUrls(it.text) }
        assertTrue("the absolute media URL must become a link: $urls",
            urls.contains("https://media.example.dev/song.mp3"))
        assertTrue("a relative source must open against the configured site: $urls",
            urls.contains(site.url("uploads/clip.mp4")))
        assertFalse("unsafe embed sources must never become clickable: $urls",
            urls.any { it.trim().lowercase().startsWith("javascript:") || it.startsWith("data:") })

        val joined = cell().messageObject.richLayout.joinedText.toString()
        assertTrue("the absolute link must be visible", joined.contains("https://media.example.dev/song.mp3"))
        assertTrue("the relative link must be visible", joined.contains("/uploads/clip.mp4"))
        assertTrue("the refused source must still read as text", joined.contains("javascript:alert(1)"))
        assertTrue("a missing embed source must not silently disappear", joined.contains("没有可用链接"))
        harness.renderApp("cooked-embed-fallbacks")
        harness.assertNoTelegramSideEffects()
    }

    // ------------------------------------------------------------------------------- driving the flow

    /** A tap on the first drawn spoiler, at the point its own cover is anchored. */
    private fun tapSpoiler(cell: ChatMessageCell) {
        val layout = cell.messageObject.richLayout
        val index = layout.textBlocks.indexOfFirst {
            it is RichMessageLayout.Text && it.spoilers.isNotEmpty()
        }
        assertTrue("the covered spoiler must have a text block", index >= 0)
        val text = layout.textBlocks[index] as RichMessageLayout.Text
        val bounds = text.spoilers.first().bounds
        // The cell lays the rich layout out at (textX, textY). Inside it, the layout's own touch
        // path returns the point to the text's frame: it undoes the text's left inset on x and its
        // top inset on y, which is exactly `text.left` and `text.y`.
        val x = (cell.textX + bounds.right - 2 - text.left).toFloat()
        val y = (cell.textY + text.y + bounds.centerY()).toFloat()
        val now = SystemClock.uptimeMillis()
        for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(now, now, action, x, y, 0)
            val handled = cell.onTouchEvent(event)
            event.recycle()
            assertTrue("the production cell must handle the spoiler tap", handled)
        }
        // The reveal rides the spoiler's own ripple animation; step the clock until it lands.
        repeat(60) {
            harness.idleClock(50)
            if (cell.messageObject.isSpoilersRevealed) return
        }
        assertTrue("tapping the spoiler never revealed it", cell.messageObject.isSpoilersRevealed)
    }

    private fun containsSpoiler(text: TL_iv.RichText?): Boolean {
        if (text == null) return false
        val styled = RichTextStyle.toSpannable(text)
        if (styled !is Spanned) return false
        return styled.getSpans(0, styled.length, TextStyleSpan::class.java).any { it.isSpoiler() }
    }

    /** The text blocks of [layout] that a drawn spoiler effect currently covers. */
    private fun coveredTexts(layout: RichMessageLayout): List<RichMessageLayout.Text> =
        layout.textBlocks.filterIsInstance<RichMessageLayout.Text>().filter { it.spoilers.isNotEmpty() }

    private fun linkUrls(text: TL_iv.RichText?): List<String> {
        if (text == null) return emptyList()
        val styled = RichTextStyle.toSpannable(text)
        if (styled !is Spanned) return emptyList()
        return styled.getSpans(0, styled.length, URLSpanReplacement::class.java).map { it.getURL() }
    }

    private fun open(html: String) {
        site.start()
        site.serveCooked(TOPIC, html)
        NativeDo.setContainerFactory { site.authenticatedContainer(it) }
        harness.bootApp()
        harness.await("home rows") { contentListOf(harness.activity)?.adapter?.itemCount == 2 }
        harness.present(TopicFragment.forTopic(TOPIC, "Cooked semantics"))
        harness.await("the post") {
            harness.layoutApp()
            laidOutCells().isNotEmpty()
        }
        harness.drawApp()
    }

    private fun list(): RecyclerListView = contentListOf(harness.activity)!!
    private fun laidOutCells(): List<ChatMessageCell> = (0 until list().childCount)
        .map { list().getChildAt(it) }.filterIsInstance<ChatMessageCell>()
    private fun cell(): ChatMessageCell = laidOutCells().first()

    private companion object { const val TOPIC = 8409L }
}
