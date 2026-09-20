package io.github.baba983.nativedo.ui.topic

import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.CodeHighlighting
import org.telegram.ui.Components.QuoteSpan
import org.telegram.ui.Components.TextStyleSpan
import org.telegram.ui.Components.URLSpanReplacement

@RunWith(RobolectricTestRunner::class)
class TopicReplyComposerMarkdownTest {

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
        AndroidUtilities.init(RuntimeEnvironment.getApplication())
    }

    private fun editable(text: String) = SpannableStringBuilder(text)

    private fun style(editable: Editable, start: Int, end: Int, flag: Int) {
        val run = TextStyleSpan.TextStyleRun()
        run.flags = flag
        editable.setSpan(TextStyleSpan(run), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun quote(editable: Editable, start: Int, end: Int) {
        val styleSpan = QuoteSpan.QuoteStyleSpan()
        val quoteSpan = QuoteSpan(true, false, styleSpan)
        styleSpan.span = quoteSpan
        editable.setSpan(quoteSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        editable.setSpan(styleSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    /** The flags every character carries, the way the serializer reads them. */
    private fun flagsOf(text: CharSequence): IntArray {
        val flags = IntArray(text.length)
        if (text is Spanned) {
            for (span in text.getSpans(0, text.length, TextStyleSpan::class.java)) {
                val spanFlags = span.textStyleRun?.flags ?: 0
                for (i in text.getSpanStart(span).coerceAtLeast(0) until text.getSpanEnd(span).coerceAtMost(text.length)) {
                    flags[i] = flags[i] or spanFlags
                }
            }
        }
        return flags
    }

    private fun serialize(text: CharSequence) = TopicReplyComposerMarkdown.serialize(
        if (text is Editable) text else SpannableStringBuilder(text),
    )

    @Test
    fun serializeLeavesUnstyledTextAlone() {
        assertThat(serialize(editable("plain text"))).isEqualTo("plain text")
    }

    @Test
    fun serializeWrapsEverySupportedStyleRun() {
        val cases = listOf(
            TextStyleSpan.FLAG_STYLE_BOLD to "**x**",
            TextStyleSpan.FLAG_STYLE_ITALIC to "_x_",
            TextStyleSpan.FLAG_STYLE_STRIKE to "~~x~~",
            TextStyleSpan.FLAG_STYLE_MONO to "`x`",
            TextStyleSpan.FLAG_STYLE_UNDERLINE to "[u]x[/u]",
            TextStyleSpan.FLAG_STYLE_SPOILER to "[spoiler]x[/spoiler]",
        )
        for ((flag, expected) in cases) {
            val text = editable("x")
            style(text, 0, 1, flag)
            assertThat(serialize(text)).isEqualTo(expected)
        }
    }

    @Test
    fun serializeDropsStylesTheWireCannotCarry() {
        val text = editable("x")
        style(text, 0, 1, TextStyleSpan.FLAG_STYLE_ACCENT or TextStyleSpan.FLAG_STYLE_MARKED)
        assertThat(serialize(text)).isEqualTo("x")
    }

    @Test
    fun serializeNestsAnInnerRunInsideTheOuterOne() {
        val text = editable("a b c")
        style(text, 0, 5, TextStyleSpan.FLAG_STYLE_BOLD)
        style(text, 2, 3, TextStyleSpan.FLAG_STYLE_ITALIC)
        assertThat(serialize(text)).isEqualTo("**a _b_ c**")
    }

    @Test
    fun serializeReopensTheOuterStyleAroundAPartialRun() {
        val text = editable("abcd")
        style(text, 0, 2, TextStyleSpan.FLAG_STYLE_BOLD)
        style(text, 2, 4, TextStyleSpan.FLAG_STYLE_ITALIC)
        assertThat(serialize(text)).isEqualTo("**ab**_cd_")
    }

    @Test
    fun serializeWritesLinksAsMarkdown() {
        val text = editable("see docs more")
        text.setSpan(URLSpanReplacement("https://linux.do"), 4, 8, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        assertThat(serialize(text)).isEqualTo("see [docs](https://linux.do) more")
    }

    @Test
    fun serializePrefixesQuotedLines() {
        val text = editable("one\ntwo")
        quote(text, 0, 7)
        assertThat(serialize(text)).isEqualTo("> one\n> two")
    }

    @Test
    fun serializeTreatsMonospaceHighlightingAsCode() {
        val text = editable("x")
        text.setSpan(CodeHighlighting.Span(false, 0, null, null, "code"), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        assertThat(serialize(text)).isEqualTo("`x`")
    }

    @Test
    fun parseTurnsMarkersIntoSpanRuns() {
        val parsed = TopicReplyComposerMarkdown.parse("a **b** c")
        assertThat(parsed.toString()).isEqualTo("a b c")
        val flags = flagsOf(parsed)
        assertThat(flags.sliceArray(0..1).toList()).containsExactly(0, 0)
        assertThat(flags[2]).isEqualTo(TextStyleSpan.FLAG_STYLE_BOLD)
        assertThat(flags.sliceArray(3..4).toList()).containsExactly(0, 0)
    }

    @Test
    fun parseKeepsNestedStylesNested() {
        val parsed = TopicReplyComposerMarkdown.parse("**a _b_ c**")
        assertThat(parsed.toString()).isEqualTo("a b c")
        val flags = flagsOf(parsed)
        assertThat(flags[0]).isEqualTo(TextStyleSpan.FLAG_STYLE_BOLD)
        assertThat(flags[2]).isEqualTo(TextStyleSpan.FLAG_STYLE_BOLD or TextStyleSpan.FLAG_STYLE_ITALIC)
        assertThat(flags[4]).isEqualTo(TextStyleSpan.FLAG_STYLE_BOLD)
    }

    @Test
    fun parseReadsEverySupportedMarker() {
        val cases = listOf(
            "**x**" to TextStyleSpan.FLAG_STYLE_BOLD,
            "_x_" to TextStyleSpan.FLAG_STYLE_ITALIC,
            "*x*" to TextStyleSpan.FLAG_STYLE_ITALIC,
            "~~x~~" to TextStyleSpan.FLAG_STYLE_STRIKE,
            "`x`" to TextStyleSpan.FLAG_STYLE_MONO,
            "[u]x[/u]" to TextStyleSpan.FLAG_STYLE_UNDERLINE,
            "[spoiler]x[/spoiler]" to TextStyleSpan.FLAG_STYLE_SPOILER,
        )
        for ((markdown, flag) in cases) {
            val parsed = TopicReplyComposerMarkdown.parse(markdown)
            assertThat(parsed.toString()).isEqualTo("x")
            assertThat(flagsOf(parsed)[0]).isEqualTo(flag)
        }
    }

    @Test
    fun parseStripsQuotePrefixesAndAddsAQuoteSpan() {
        val parsed = TopicReplyComposerMarkdown.parse("intro\n> one\n> two\noutro")
        assertThat(parsed.toString()).isEqualTo("intro\none\ntwo\noutro")
        val spans = (parsed as Spanned).getSpans(0, parsed.length, QuoteSpan::class.java)
        assertThat(spans).hasLength(1)
        assertThat(parsed.getSpanStart(spans[0])).isEqualTo(6)
        assertThat(parsed.getSpanEnd(spans[0])).isEqualTo(13)
    }

    /**
     * The parse side has to hand the composer the same span pair upstream's `putQuote`/`putQuoteToEditable`
     * build: the metric-affecting helper reads its quote back-reference on the first measure and draw.
     */
    @Test
    fun parsedQuoteSpansMeasureAndDraw() {
        val parsed = TopicReplyComposerMarkdown.parse("> one\n> two")
        val paint = TextPaint()
        val spans = (parsed as Spanned).getSpans(0, parsed.length, QuoteSpan.QuoteStyleSpan::class.java)
        assertThat(spans).hasLength(1)
        spans[0].updateMeasureState(paint)
        spans[0].updateDrawState(paint)
    }

    @Test
    fun parseWritesLinksAsUrlSpans() {
        val parsed = TopicReplyComposerMarkdown.parse("see [docs](https://linux.do) more")
        assertThat(parsed.toString()).isEqualTo("see docs more")
        val spans = (parsed as Spanned).getSpans(0, parsed.length, URLSpanReplacement::class.java)
        assertThat(spans).hasLength(1)
        assertThat(spans[0].url).isEqualTo("https://linux.do")
        assertThat(parsed.getSpanStart(spans[0])).isEqualTo(4)
        assertThat(parsed.getSpanEnd(spans[0])).isEqualTo(8)
    }

    @Test
    fun parseLeavesUnknownMarkersAsText() {
        val markdown = "[quote=\"daud, post:1, topic:2\"]body[/quote] and **unclosed"
        val parsed = TopicReplyComposerMarkdown.parse(markdown)
        assertThat(parsed.toString()).isEqualTo(markdown)
        assertThat(flagsOf(parsed).toList()).containsExactlyElementsIn(List(markdown.length) { 0 })
    }

    @Test
    fun roundTripIsStable() {
        val samples = listOf(
            "plain text",
            "a **b** c",
            "**a _b_ c**",
            "**ab**_cd_",
            "~~gone~~ and `code`",
            "[u]under[/u] [spoiler]hidden[/spoiler]",
            "see [docs](https://linux.do) more",
            "intro\n> one\n> two",
            "**bold**\n> quoted _line_",
            "a_b_c",
        )
        for (markdown in samples) {
            assertThat(serialize(TopicReplyComposerMarkdown.parse(markdown))).isEqualTo(markdown)
        }
    }

    @Test
    fun roundTripKeepsStyleRuns() {
        val text = editable("a b c")
        style(text, 0, 5, TextStyleSpan.FLAG_STYLE_BOLD)
        style(text, 2, 3, TextStyleSpan.FLAG_STYLE_ITALIC)
        val reparsed = TopicReplyComposerMarkdown.parse(serialize(text))
        assertThat(reparsed.toString()).isEqualTo("a b c")
        assertThat(flagsOf(reparsed).toList()).containsExactly(
            TextStyleSpan.FLAG_STYLE_BOLD,
            TextStyleSpan.FLAG_STYLE_BOLD,
            TextStyleSpan.FLAG_STYLE_BOLD or TextStyleSpan.FLAG_STYLE_ITALIC,
            TextStyleSpan.FLAG_STYLE_BOLD,
            TextStyleSpan.FLAG_STYLE_BOLD,
        )
    }
}
