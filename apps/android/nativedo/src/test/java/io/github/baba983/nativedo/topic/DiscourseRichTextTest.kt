package io.github.baba983.nativedo.topic

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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.tgnet.tl.TL_iv
import org.telegram.ui.iv.RichHtml

/**
 * The editor's content and Discourse's wire format.
 *
 * <p>Each test names a way the round trip can lose or corrupt what the reader typed, and checks it
 * against the site's own semantics (upstream [RichHtml] / `MarkdownParser`, not a copy of the
 * converter): literal punctuation must stay literal, a code span must keep its backticks and
 * asterisks, a link must keep its query ampersands, nested lists and quotes must keep their depth,
 * and an uploaded file's `upload://` markup must survive a reopen. Content Discourse can store is
 * stored; only a construct with no Discourse form (a Telegram media id, an inline button) is
 * refused, and never silently dropped.</p>
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    application = ApplicationLoader::class,
    instrumentedPackages = ["org.telegram"],
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
internal class DiscourseRichTextTest {

    private val harness = UpstreamUiHarness()

    @Before
    fun setUp() {
        harness.bootViews()
    }

    /** The formatting a reopened post must not lose. */
    private fun reopen(raw: String): String = DiscourseRichText.blocksToRaw(DiscourseRichText.rawToBlocks(raw))

    @Test
    fun escapedAsterisksStayLiteralAndDoNotBecomeEmphasis() {
        val raw = "a \\*literal\\* star"
        val html = DiscourseRichText.rawToHtml(raw)
        println("rich: escaped html = $html")
        assertFalse("an escaped asterisk is not emphasis", html.contains("<i>"))
        assertEquals("the escape survives the round trip", raw, reopen(raw))
    }

    @Test
    fun aCodeSpanKeepsItsLiteralAsterisksAndBackticks() {
        val simple = "`a*b*c` is code"
        val withBacktick = "``a`b`` is code"
        val html = DiscourseRichText.rawToHtml(simple)
        println("rich: code html = $html")
        assertTrue("the code text is monospace", html.contains("<code>a*b*c</code>"))
        assertFalse("asterisks inside code are not emphasis", html.contains("<i>"))
        assertEquals("literal asterisks survive", simple, reopen(simple))
        assertEquals("a literal backtick inside code survives", withBacktick, reopen(withBacktick))
    }

    @Test
    fun aLinkKeepsItsQueryAmpersandsExactlyOnce() {
        val raw = "[q](https://linux.do/x?a=1&b=2)"
        val raw2 = reopen(raw)
        println("rich: link = $raw2")
        assertEquals("the query string survives the round trip", raw, raw2)
        assertFalse("the ampersand is not double-escaped", raw2.contains("&amp;"))
    }

    @Test
    fun aNestedListKeepsItsDepth() {
        val raw = "- one\n  - nested\n    - deep"
        val back = reopen(raw)
        println("rich: nested list = " + back.replace("\n", "\\n"))
        assertEquals("nested items keep their indentation", raw, back)
    }

    @Test
    fun aNestedQuoteWithInlineCodeKeepsItsDepthAndItsCode() {
        val raw = "> outer\n>\n> > inner `x*y`"
        val html = DiscourseRichText.rawToHtml(raw)
        println("rich: quote html = $html")
        assertTrue("a quote inside a quote stays nested", html.contains("<blockquote><p>outer</p><blockquote>"))
        assertTrue("the inline code is untouched", html.contains("<code>x*y</code>"))
        assertEquals("the quote survives the round trip", raw, reopen(raw))
    }

    @Test
    fun anUploadedImageMarkupSurvivesReopen() {
        val raw = "![screenshot.png|1080x720](upload://9d5a2f6f0a1b.png)"
        val back = reopen(raw)
        println("rich: upload reopen = $back")
        assertEquals("the upload handle and its label survive", raw, back)
    }

    @Test
    fun anAttachmentMarkupSurvivesReopen() {
        val raw = "[session-notes.zip|attachment](upload://1a2b3c4d.zip) (1.2 MB)"
        val back = reopen(raw)
        println("rich: attachment reopen = $back")
        assertEquals("an attachment link keeps its marker and its handle", raw, back)
    }

    @Test
    fun aTableIsStoredNotRefused() {
        val raw = "| a | b |\n| --- | --- |\n| c | d |"
        val back = reopen(raw)
        println("rich: table = " + back.replace("\n", "\\n"))
        assertEquals("Discourse stores tables, so the table survives", raw, back)
    }

    @Test
    fun anExpandableSectionRoundTrips() {
        val raw = "[details=More]\n\nbody text\n\n[/details]"
        val back = reopen(raw)
        println("rich: details = " + back.replace("\n", "\\n"))
        assertEquals("a details section keeps its title and body", raw, back)
    }

    @Test
    fun aSpoilerRoundTrips() {
        val raw = "a [spoiler]secret[/spoiler] b"
        val html = DiscourseRichText.rawToHtml(raw)
        println("rich: spoiler html = $html")
        assertTrue("the spoiler is the editor's own tag", html.contains("<spoiler>secret</spoiler>"))
        assertEquals("the spoiler survives the round trip", raw, reopen(raw))
    }

    @Test
    fun headingsRulesAndUnderlineRoundTrip() {
        for (raw in listOf("# Title", "## Sub", "---", "x <u>under</u> y")) {
            val back = reopen(raw)
            println("rich: block " + raw.replace("\n", "\\n") + " -> " + back.replace("\n", "\\n"))
            assertEquals("the round trip of \"$raw\" changed it", raw, back)
        }
    }

    @Test
    fun rawToRichMessageKeepsAnUploadsHandle() {
        val message = DiscourseRichText.rawToRichMessage("![screenshot.png|1080x720](upload://9d5a2f6f0a1b.png)")
        val urls = urlsOf(firstText(message))
        println("rich: richMessage upload urls = $urls")
        assertTrue("the upload handle is not dropped", urls.contains("upload://9d5a2f6f0a1b.png"))
    }

    @Test
    fun theSupportedFormattingSurvivesTheWireRoundTrip() {
        val cases = listOf(
            "a **bold** and *italic* word",
            "[a link](https://linux.do/t/123)",
            "> a quoted line",
            "```kotlin\nval x = 1\n```",
            "- first\n- second",
            "1. first\n2. second",
        )
        for (raw in cases) {
            val back = reopen(raw)
            println("rich: round trip " + raw.replace("\n", "\\n") + "  ->  " + back.replace("\n", "\\n"))
            assertEquals("the round trip of \"$raw\" changed it", raw, back)
        }
    }

    @Test
    fun theEditorsOwnHtmlIsWhatDiscourseGets() {
        // The document the editor builds, through its own export, becomes the markdown Discourse
        // stores - not plain text, and not its HTML.
        val html = "<p><b>bold</b> then <i>italic</i> and <a href=\"https://linux.do\">a link</a></p>" +
            "<ul><li>one</li><li>two</li></ul>"
        val raw = DiscourseRichText.blocksToRaw(RichHtml.parse(html))
        println("rich: export = " + raw.replace("\n", "\\n"))
        assertTrue("bold is markdown bold", raw.contains("**bold**"))
        assertTrue("italic is markdown italic", raw.contains("*italic*"))
        assertTrue("a link keeps its target", raw.contains("[a link](https://linux.do)"))
        assertTrue("the list is markdown bullets", raw.contains("- one") && raw.contains("- two"))
        assertTrue("no HTML tag leaked into the wire format", !raw.contains("<"))
    }

    @Test
    fun rawToRichMessageUsesUpstreamParsingAndKeepsEscapes() {
        // The parent uses RichMessage directly; it must come from upstream's CommonMark parser, so
        // an escaped asterisk is literal text and an unescaped one is emphasis.
        val literal = DiscourseRichText.rawToRichMessage("a \\*literal\\* star")
        val italic = DiscourseRichText.rawToRichMessage("a *slanted* word")
        val bold = DiscourseRichText.rawToRichMessage("a **heavy** word")
        val literalStyles = stylesOf(firstText(literal))
        val italicStyles = stylesOf(firstText(italic))
        val boldStyles = stylesOf(firstText(bold))
        println("rich: richMessage styles literal=$literalStyles italic=$italicStyles bold=$boldStyles")
        assertFalse("an escaped asterisk is not emphasis", literalStyles.contains("italic"))
        assertTrue("an asterisk pair is emphasis", italicStyles.contains("italic"))
        assertTrue("a double pair is bold", boldStyles.contains("bold"))
    }

    @Test
    fun aTelegramMediaIdHasNoDiscourseFormAndIsRefusedNotDropped() {
        // The editor carries an inserted photo as a numeric media id, which no `upload://` URL can
        // name; refusing tells the reader instead of dropping it from their post.
        val rows = RichHtml.parse("<p>before</p><img src=\"123456\" />")
        val thrown = runCatching { DiscourseRichText.blocksToRaw(rows) }.exceptionOrNull()
        println("rich: media export threw $thrown")
        assertTrue(
            "an embedded Telegram photo is reported, not silently dropped",
            thrown is DiscourseRichText.UnsupportedContentException,
        )
    }

    private fun firstText(message: TL_iv.RichMessage): TL_iv.RichText? =
        (message.blocks.firstOrNull() as? TL_iv.pageBlockParagraph)?.text

    private fun stylesOf(text: TL_iv.RichText?): Set<String> = when (text) {
        null -> emptySet()
        is TL_iv.textBold -> stylesOf(text.text) + "bold"
        is TL_iv.textItalic -> stylesOf(text.text) + "italic"
        is TL_iv.textStrike -> stylesOf(text.text) + "strike"
        is TL_iv.textFixed -> stylesOf(text.text) + "code"
        is TL_iv.textConcat -> text.texts.flatMap { stylesOf(it) }.toSet()
        else -> stylesOf(text.text)
    }

    private fun urlsOf(text: TL_iv.RichText?): Set<String> = when (text) {
        null -> emptySet()
        is TL_iv.textUrl -> urlsOf(text.text) + text.url
        is TL_iv.textConcat -> text.texts.flatMap { urlsOf(it) }.toSet()
        else -> urlsOf(text.text)
    }
}
