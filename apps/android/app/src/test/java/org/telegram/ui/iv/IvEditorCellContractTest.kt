package org.telegram.ui.iv

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.AppGlobalConfig
import org.telegram.messenger.ApplicationLoader
import org.telegram.tgnet.tl.TL_iv

/**
 * The editor cell family's logic pieces, both the pin's files verbatim: [RichEditorLimits] (the walk
 * over a block tree that counts length / blocks / depth / media / table columns and answers which
 * server limit the content exceeds) and [RichEditorHistory] (the undo stack that snapshots rows and
 * restores them). The history test stays on the row *shape* on purpose: the content round trip goes
 * through the TL serializers this tree's `TL_iv` types do not carry yet, so a restored block falls
 * back to an empty paragraph - see `docs/render-model-alignment.md`, "The ui/iv project".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class IvEditorCellContractTest {

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    private fun paragraph(text: String): TL_iv.pageBlockParagraph =
        TL_iv.pageBlockParagraph().also { it.text = RichTextStyle.fromSpannable(text) }

    @Test
    fun theLimitsWalkTheBlockTree() {
        val table = TL_iv.pageBlockTable()
        val row = TL_iv.pageTableRow()
        repeat(3) { row.cells.add(TL_iv.pageTableCell().also { TableModel.applyPlainText(it, "cell") }) }
        table.rows.add(row)
        val top = arrayListOf<TL_iv.PageBlock>(paragraph("hello"), table, TL_iv.pageBlockPhoto())

        val limits = RichEditorLimits.measure(top, 2)

        assertThat(limits.media).isEqualTo(2)
        // three top-level blocks plus the table's one row
        assertThat(limits.blocks).isEqualTo(4)
        assertThat(limits.tableCols).isEqualTo(3)
        assertThat(limits.length).isEqualTo("hello".length + 3 * "cell".length)
        // the paragraph sits at depth 1, the table cells one level below
        assertThat(limits.depth).isEqualTo(2)
        assertThat(limits.isWithin(AppGlobalConfig())).isTrue()
    }

    @Test
    fun nestedBlocksDeepenTheTree() {
        val details = TL_iv.pageBlockDetails()
        details.blocks.add(paragraph("x"))

        val limits = RichEditorLimits.measure(arrayListOf<TL_iv.PageBlock>(details), 0)

        assertThat(limits.blocks).isEqualTo(2)
        assertThat(limits.depth).isEqualTo(2)
        assertThat(limits.isWithin(AppGlobalConfig())).isTrue()
    }

    @Test
    fun theFirstExceededLimitIsReported() {
        val limits = RichEditorLimits.measure(arrayListOf<TL_iv.PageBlock>(paragraph("x")), 0)
        val config = AppGlobalConfig()
        assertThat(limits.findExceeded(config)).isEqualTo(RichEditorLimits.LIMIT_NONE)

        limits.length = config.richMessageLengthLimit.get() + 1
        assertThat(limits.findExceeded(config)).isEqualTo(RichEditorLimits.LIMIT_LENGTH)
        limits.length = 0

        limits.blocks = config.richMessageMaxBlocks.get() + 1
        assertThat(limits.findExceeded(config)).isEqualTo(RichEditorLimits.LIMIT_BLOCKS)
        limits.blocks = 0

        limits.depth = config.richMessageMaxDepth.get() + 1
        assertThat(limits.findExceeded(config)).isEqualTo(RichEditorLimits.LIMIT_DEPTH)
        limits.depth = 0

        limits.media = config.richMessageMaxMedia.get() + 1
        assertThat(limits.findExceeded(config)).isEqualTo(RichEditorLimits.LIMIT_MEDIA)
        limits.media = 0

        limits.tableCols = config.richMessageMaxTableCols.get() + 1
        assertThat(limits.findExceeded(config)).isEqualTo(RichEditorLimits.LIMIT_TABLE_COLS)
        limits.tableCols = 0

        assertThat(limits.isWithin(config)).isTrue()
    }

    @Test
    fun undoAndRedoWalkTheStacksAndRestoreTheRowShape() {
        val rows = ArrayList<BlockRow>()
        var restoreCalls = 0
        val delegate = object : RichEditorHistory.Delegate {
            override fun getRows(): ArrayList<BlockRow> = rows
            override fun restoreRows(restored: MutableList<BlockRow>, focus: RichEditorHistory.FocusState) {
                rows.clear()
                rows.addAll(restored)
                restoreCalls++
            }
            override fun onHistoryChanged() {}
            override fun captureFocus(): RichEditorHistory.FocusState = RichEditorHistory.FocusState.NONE
        }
        val history = RichEditorHistory(delegate)

        rows.add(BlockRow(paragraph("one"), 0, 0))
        history.record()
        assertThat(history.canUndo()).isTrue()

        rows.add(BlockRow(paragraph("two"), 1, 0))
        history.record()
        assertThat(rows).hasSize(2)
        assertThat(history.canRedo()).isFalse()

        history.undo()
        assertThat(restoreCalls).isEqualTo(1)
        assertThat(rows).hasSize(1)
        assertThat(rows[0].level).isEqualTo(0)
        assertThat(history.canRedo()).isTrue()

        history.redo()
        assertThat(restoreCalls).isEqualTo(2)
        assertThat(rows).hasSize(2)
        assertThat(rows[1].level).isEqualTo(1)
        assertThat(history.canRedo()).isFalse()
    }
}
