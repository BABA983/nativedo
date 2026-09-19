package org.telegram.ui.iv

import android.text.SpannableStringBuilder
import android.text.Spanned
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.tgnet.tl.TL_iv
import org.telegram.ui.Components.TextStyleSpan

/**
 * The pin's [RichHtml] - the document ⇄ HTML bridge the renderer's copy/export path calls
 * (`RichMessageLayout` uses `tableToHtml`/`inlineToHtml`/`preToHtml`) and the editor uses for its
 * whole document. These tests pin the wire format those callers depend on: the inline tag set, the
 * escaping, the `pre` language attribute, the table attributes, and a paragraph's round trip.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class IvRichHtmlContractTest {

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    private fun run(flags: Int): TextStyleSpan.TextStyleRun = TextStyleSpan.TextStyleRun().apply { this.flags = flags }

    @Test
    fun inlineHtmlWrapsStylesAndEscapesTheText() {
        val styled = SpannableStringBuilder("bold & <tag>")
        styled.setSpan(TextStyleSpan(run(RichTextStyle.BOLD)), 0, styled.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val html = RichHtml.inlineToHtml(styled)

        assertThat(html).isEqualTo("<b>bold &amp; &lt;tag&gt;</b>")
    }

    @Test
    fun aNewlineBecomesABreakAndSpoilersGetTheirTag() {
        val styled = SpannableStringBuilder("one\ntwo")
        styled.setSpan(TextStyleSpan(run(RichTextStyle.SPOILER)), 0, styled.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val html = RichHtml.inlineToHtml(styled)

        assertThat(html).isEqualTo("<spoiler>one<br>two</spoiler>")
    }

    @Test
    fun aLinkBecomesAnAnchorWithAnEscapedHref() {
        val styled = SpannableStringBuilder("linux.do")
        styled.setSpan(RichTextStyle.linkSpan("https://linux.do/t/x?a=1&b=2"), 0, styled.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val html = RichHtml.inlineToHtml(styled)

        assertThat(html).isEqualTo("<a href=\"https://linux.do/t/x?a=1&amp;b=2\">linux.do</a>")
    }

    @Test
    fun preformattedHtmlCarriesTheLanguage() {
        assertThat(RichHtml.preToHtml(SpannableStringBuilder("fun main()"), "kotlin"))
            .isEqualTo("<pre language=\"kotlin\">fun main()</pre>")
        assertThat(RichHtml.preToHtml(SpannableStringBuilder("plain"), null)).isEqualTo("<pre>plain</pre>")
        assertThat(RichHtml.preToHtml(SpannableStringBuilder(), "kotlin")).isEmpty()
    }

    @Test
    fun tableHtmlWritesHeaderCellsAndSpans() {
        val table = TL_iv.pageBlockTable()
        val row = TL_iv.pageTableRow()
        val header = TL_iv.pageTableCell()
        header.header = true
        header.colspan = 2
        TableModel.applyPlainText(header, "Head")
        row.cells.add(header)
        val cell = TL_iv.pageTableCell()
        TableModel.applyPlainText(cell, "body")
        row.cells.add(cell)
        table.rows.add(row)

        val html = RichHtml.tableToHtml(table)

        assertThat(html).contains("<th colspan=\"2\">Head</th>")
        assertThat(html).contains("<td>body</td>")
        assertThat(html).startsWith("<table>")
        assertThat(html).endsWith("</table>")
    }

    @Test
    fun aParagraphRoundTripsThroughHtml() {
        val paragraph = TL_iv.pageBlockParagraph()
        paragraph.text = RichTextStyle.fromSpannable("hello & <world>")
        val rows = listOf(BlockRow(paragraph))

        // The call ChatActivity makes for an unselected document.
        val html = RichHtml.serialize(rows, 0, rows.size - 1, 0, Int.MAX_VALUE)

        assertThat(html).isEqualTo("<p>hello &amp; &lt;world&gt;</p>")

        val parsed = RichHtml.parse(html)
        assertThat(parsed).hasSize(1)
        assertThat(RichTextStyle.plainOf(parsed[0].block.text)).isEqualTo("hello & <world>")
    }
}
