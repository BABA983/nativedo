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
import org.telegram.messenger.CodeHighlighting
import org.telegram.tgnet.tl.TL_iv
import org.telegram.ui.Components.QuoteSpan
import java.util.Collections

/**
 * The two pieces of the iv document layer that carry logic rather than paint: [TableModel] (the
 * editable table model behind the table cell - spans, anchors, merge/unmerge, row and column edits)
 * and [RichMessageConvert] (the rich message ⇄ `CharSequence` bridge the editor's text view and the
 * renderer's copy path meet at). Both are the pin's files verbatim; these tests pin the contracts
 * their consumers would rely on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class IvDocumentModelContractTest {

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    private fun table(vararg rows: List<String>): TL_iv.pageBlockTable {
        val t = TL_iv.pageBlockTable()
        for (texts in rows) {
            val row = TL_iv.pageTableRow()
            for (text in texts) {
                val cell = TL_iv.pageTableCell()
                TableModel.applyPlainText(cell, text)
                row.cells.add(cell)
            }
            t.rows.add(row)
        }
        return t
    }

    private fun solid(model: TableModel) {
        assertThat(model.rowCount).isAtLeast(1)
        assertThat(model.colCount).isAtLeast(1)
        for (r in 0 until model.rowCount) {
            for (c in 0 until model.colCount) {
                assertThat(model.grid[r][c]).isNotNull()
                assertThat(model.anchorR[r][c]).isAtLeast(0)
                assertThat(model.anchorC[r][c]).isAtLeast(0)
            }
        }
    }

    // ---------- TableModel ----------

    @Test
    fun aRaggedRowIsPaddedAndWrittenBackToTheBlock() {
        val block = table(listOf("a", "b"), listOf("c"))
        val model = TableModel(block)

        assertThat(model.rowCount).isEqualTo(2)
        assertThat(model.colCount).isEqualTo(2)
        // The padding cell is an anchor of its own, and the model writes it into the block.
        assertThat(model.anchors()).hasSize(4)
        assertThat(model.isAnchor(1, 1)).isTrue()
        assertThat(TableModel.readPlainText(model.grid[1][1])).isEmpty()
        assertThat(block.rows[1].cells).hasSize(2)
        solid(model)
    }

    @Test
    fun mergingTwoCellsLeavesOneAnchorAndUnmergingRestoresThem() {
        val block = table(listOf("a", "b"), listOf("c", "d"))
        val model = TableModel(block)

        val merged = model.mergeCells(hashSetOf(model.anchorAt(0), model.anchorAt(1)))
        assertThat(merged).isTrue()
        assertThat(model.anchors()).hasSize(3)
        assertThat(model.isAnchor(0, 0)).isTrue()
        assertThat(model.isAnchor(0, 1)).isFalse()
        val topLeft = model.anchorAt(0)
        assertThat(TableModel.spanCol(topLeft)).isEqualTo(2)
        assertThat(TableModel.spanRow(topLeft)).isEqualTo(1)
        // The merged text is the covered cells' text, joined row-major.
        assertThat(TableModel.readPlainText(topLeft)).isEqualTo("a\nb")
        solid(model)

        assertThat(model.unmergeCell(topLeft)).isTrue()
        assertThat(model.anchors()).hasSize(4)
        assertThat(TableModel.spanCol(model.anchorAt(0))).isEqualTo(1)
        assertThat(model.isAnchor(0, 1)).isTrue()
        solid(model)
    }

    @Test
    fun mergingRefusesASelectionThatIsNotTheWholeRectangle() {
        val block = table(listOf("a", "b"), listOf("c", "d"))
        val model = TableModel(block)

        // (0,0) + (1,1) leaves (0,1) and (1,0) uncovered.
        assertThat(model.mergeCells(hashSetOf(model.anchorAt(0), model.anchorAt(3)))).isFalse()
        assertThat(model.anchors()).hasSize(4)
    }

    @Test
    fun rowAndColumnEditsKeepTheGridSolid() {
        val block = table(listOf("a", "b"), listOf("c", "d"), listOf("e", "f"))
        val model = TableModel(block)

        model.insertRowAt(1)
        assertThat(model.rowCount).isEqualTo(4)
        assertThat(model.colCount).isEqualTo(2)
        solid(model)

        model.insertColumnAt(1)
        assertThat(model.colCount).isEqualTo(3)
        solid(model)

        assertThat(model.deleteRows(HashSet(Collections.singletonList(0)))).isTrue()
        assertThat(model.rowCount).isEqualTo(3)
        solid(model)

        assertThat(model.deleteColumns(HashSet(Collections.singletonList(0)))).isTrue()
        assertThat(model.colCount).isEqualTo(2)
        solid(model)
    }

    @Test
    fun normalizeForSendFillsTheTextAndTheSpanFlags() {
        val block = table(listOf("a", "b"))
        val cell = block.rows[0].cells[0]
        cell.text = null
        cell.colspan = 2

        TableModel.normalizeForSend(block)

        assertThat(block.title).isNotNull()
        assertThat(cell.text).isNotNull()
        assertThat(TableModel.readPlainText(cell)).isEmpty()
        // colspan > 1 is mirrored in the flags the serializer reads.
        assertThat(cell.flags and (1 shl 1)).isNotEqualTo(0)
    }

    // ---------- RichMessageConvert ----------

    @Test
    fun oneParagraphPerLineRoundTrips() {
        val blocks = RichMessageConvert.blocksFromCharSequence("alpha\nbeta")

        assertThat(blocks).hasSize(2)
        assertThat(blocks[0]).isInstanceOf(TL_iv.pageBlockParagraph::class.java)
        assertThat(RichTextStyle.plainOf((blocks[0] as TL_iv.pageBlockParagraph).text)).isEqualTo("alpha")
        assertThat(RichMessageConvert.blocksToCharSequence(blocks).toString()).isEqualTo("alpha\nbeta")
    }

    @Test
    fun aCodeLineBecomesPreformattedAndComesBackAsCode() {
        val code = "fun main() {}"
        val sb = SpannableStringBuilder(code)
        sb.setSpan(CodeHighlighting.Span(true, 0, null, "kotlin", code), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val blocks = RichMessageConvert.blocksFromCharSequence(sb)

        assertThat(blocks).hasSize(1)
        val pre = blocks[0] as TL_iv.pageBlockPreformatted
        assertThat(pre.language).isEqualTo("kotlin")
        assertThat(RichTextStyle.plainOf(pre.text)).isEqualTo(code)

        val back = RichMessageConvert.blocksToCharSequence(blocks)
        val spans = (back as Spanned).getSpans(0, back.length, CodeHighlighting.Span::class.java)
        assertThat(spans).hasLength(1)
        assertThat(spans[0].lng).isEqualTo("kotlin")
        assertThat(back.toString()).isEqualTo(code)
    }

    @Test
    fun aQuoteLineBecomesABlockquoteAndComesBackQuoted() {
        val sb = SpannableStringBuilder("quoted")
        QuoteSpan.putQuote(sb, 0, sb.length, false)

        val blocks = RichMessageConvert.blocksFromCharSequence(sb)

        assertThat(blocks).hasSize(1)
        val quote = blocks[0] as TL_iv.pageBlockBlockquote
        assertThat(quote.collapsed).isFalse()
        assertThat(quote.text).isNotNull()

        val back = RichMessageConvert.blocksToCharSequence(blocks)
        assertThat((back as Spanned).getSpans(0, back.length, QuoteSpan::class.java)).isNotEmpty()
        assertThat(back.toString()).isEqualTo("quoted")
    }

    @Test
    fun onlyTheSimpleBlocksAreLossless() {
        val paragraph = BlockRow(TL_iv.pageBlockParagraph())
        val table = BlockRow(TL_iv.pageBlockTable())

        assertThat(RichMessageConvert.isLossy(listOf(paragraph))).isFalse()
        assertThat(RichMessageConvert.isLossy(listOf(table))).isTrue()
        // A details end row and anything nested inside a list cannot round-trip as plain text.
        val nested = BlockRow(TL_iv.pageBlockParagraph(), 1, 0)
        assertThat(RichMessageConvert.isLossy(listOf(nested))).isTrue()
    }
}
