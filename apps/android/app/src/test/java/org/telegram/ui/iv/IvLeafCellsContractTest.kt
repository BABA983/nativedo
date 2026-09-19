package org.telegram.ui.iv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.TextSelectionHelper

/**
 * The iv leaf cells that landed with the selection interface: `RichBlockSelection` plus the three
 * cells that build their selection geometry with it (`RichDividerCell`, `RichDetailsEndCell`,
 * `RichMathCell`). They are upstream's files verbatim, so what a test can pin is the contract — the
 * measured height of each cell, the one selection block each contributes to the article helper, and
 * the geometry that block reports (the handles snap to those bounds).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class IvLeafCellsContractTest {

    private companion object {
        const val DIVIDER_COLOR = 0xff123456.toInt()
        const val DETAILS_LINE_COLOR = 0xff654321.toInt()
        const val SELECTION_COLOR = 0x553390ec
        const val FALLBACK_COLOR = 0xff737373.toInt()
        const val WIDTH = 400
    }

    private val provider = object : Theme.ResourcesProvider {
        private fun color(key: Int): Int = when (key) {
            Theme.key_chat_inDivider -> DIVIDER_COLOR
            Theme.key_chat_inArticleDetailsLine -> DETAILS_LINE_COLOR
            Theme.key_chat_inTextSelectionHighlight -> SELECTION_COLOR
            else -> FALLBACK_COLOR
        }

        override fun getColor(key: Int): Int = color(key)
        override fun getColorOrNull(key: Int): Int = color(key)
    }

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    private fun View.measureAndLayout() {
        measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        layout(0, 0, measuredWidth, measuredHeight)
    }

    private fun TextSelectionHelper.ArticleSelectableView.selectionBlocks():
        List<TextSelectionHelper.TextLayoutBlock> {
        val out = ArrayList<TextSelectionHelper.TextLayoutBlock>()
        fillTextLayoutBlocks(out)
        return out
    }

    @Test
    fun dividerCellMeasuresTwelveDpAndReportsItsInsetLine() {
        val cell = RichDividerCell(RuntimeEnvironment.getApplication(), provider)
        cell.measureAndLayout()
        assertThat(cell.measuredHeight).isEqualTo(AndroidUtilities.dp(12f))

        val blocks = cell.selectionBlocks()
        assertThat(blocks).hasSize(1)
        val block = blocks.single()
        // The line spans the middle half of the cell; the selection block bleeds dp(12) past it.
        val lineLeft = WIDTH / 4
        val lineRight = WIDTH - WIDTH / 4
        assertThat(block.selectionBounds)
            .isEqualTo(Rect(lineLeft - AndroidUtilities.dp(12f), 0, lineRight + AndroidUtilities.dp(12f), AndroidUtilities.dp(12f)))
        // RichBlockSelection's placeholder: a 1-char layout at the origin, bounds carry the geometry.
        assertThat(block.layout.text.toString()).isEqualTo(" ")
        assertThat(block.x).isEqualTo(0)
        assertThat(block.y).isEqualTo(0)
        assertThat(block.row).isEqualTo(0)
    }

    @Test
    fun detailsEndCellMeasuresItsSixDpLineAndDrawsIt() {
        val cell = RichDetailsEndCell(RuntimeEnvironment.getApplication(), provider)
        cell.measureAndLayout()
        assertThat(cell.measuredHeight).isEqualTo(AndroidUtilities.dp(6f) + 1)

        val bitmap = Bitmap.createBitmap(WIDTH, cell.measuredHeight, Bitmap.Config.ARGB_8888)
        cell.draw(Canvas(bitmap))

        assertThat(bitmap.getPixel(WIDTH / 2, AndroidUtilities.dp(6f))).isEqualTo(DETAILS_LINE_COLOR)
        assertThat(bitmap.getPixel(WIDTH / 2, 0)).isEqualTo(0)
    }

    @Test
    fun mathCellReportsASelectionBlockOverItsOwnBounds() {
        val cell = RichMathCell(RuntimeEnvironment.getApplication(), provider)
        cell.measureAndLayout()

        val blocks = cell.selectionBlocks()
        assertThat(blocks).hasSize(1)
        val bounds = blocks.single().selectionBounds
        assertThat(bounds).isNotNull()
        // A bitmap-less math cell centres its selection over half the cell, inflated by dp(4).
        assertThat(bounds!!.left).isEqualTo(WIDTH / 2 - WIDTH / 4 - AndroidUtilities.dp(4f))
        assertThat(bounds.right).isEqualTo(WIDTH / 2 + WIDTH / 4 + AndroidUtilities.dp(4f))
        assertThat(bounds.top).isAtMost(bounds.bottom)
    }

    @Test
    fun everySelectionBlockSharesTheCachedPlaceholderLayout() {
        val divider = RichDividerCell(RuntimeEnvironment.getApplication(), provider)
        val math = RichMathCell(RuntimeEnvironment.getApplication(), provider)
        divider.measureAndLayout()
        math.measureAndLayout()

        val dividerBlock = divider.selectionBlocks().single()
        val mathBlock = math.selectionBlocks().single()

        // `RichBlockSelection` caches one placeholder layout and hands out fresh bounds per call.
        assertThat(dividerBlock.layout).isSameInstanceAs(mathBlock.layout)
        assertThat(dividerBlock.layout.text.length).isEqualTo(1)
        assertThat(dividerBlock.selectionBounds).isNotSameInstanceAs(mathBlock.selectionBounds)
    }
}
