package io.github.baba983.nativedo.ui.topic.body

import android.text.Layout
import android.graphics.Rect
import android.text.StaticLayout
import android.text.TextPaint
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader

@RunWith(RobolectricTestRunner::class)
class ListBlockTest {
    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun markerTopForFirstTextLine_matchesArticleViewerUnorderedOffset() {
        val top = ListBlock.markerTopForFirstTextLine(
            10,
            3,
            4,
            -11,
            -15,
            false,
        )

        assertThat(top).isEqualTo(10 + 3 + 4 - 11 + 15 + AndroidUtilities.dp(2.5f) - AndroidUtilities.dp(1f))
    }

    @Test
    fun markerTopForFirstTextLine_matchesArticleViewerOrderedOffset() {
        val top = ListBlock.markerTopForFirstTextLine(
            10,
            3,
            4,
            -11,
            -15,
            true,
        )

        assertThat(top).isEqualTo((10 + 3 + 4 - 11 + 15).toFloat())
    }

    @Test
    fun selectionPrefixForItem_matchesRenderedUnorderedMarker() {
        assertThat(ListBlock.selectionPrefixForItem(0, false)).isEqualTo("•")
        assertThat(ListBlock.selectionPrefixForItem(1, false)).isEqualTo("•")
    }

    @Test
    fun selectionPrefixForItem_matchesRenderedOrderedMarker() {
        assertThat(ListBlock.selectionPrefixForItem(0, true)).isEqualTo("1.")
        assertThat(ListBlock.selectionPrefixForItem(1, true)).isEqualTo("2.")
    }

    @Test
    fun firstItemTopPadding_matchesArticleViewerTopLevelOnlyRule() {
        assertThat(ListBlock.firstItemTopPaddingPx(0)).isEqualTo(AndroidUtilities.dp(10f))
        assertThat(ListBlock.firstItemTopPaddingPx(1)).isEqualTo(0)
        assertThat(ListBlock.firstItemTopPaddingPx(2)).isEqualTo(0)
    }

    @Test
    fun markerHorizontalGeometry_matchesArticleViewerFormula() {
        val markerWidth = AndroidUtilities.dp(11f)

        assertThat(ListBlock.markerTextGapPx()).isEqualTo(AndroidUtilities.dp(6f))
        assertThat(ListBlock.markerTextGapPx(true)).isEqualTo(AndroidUtilities.dp(6f))
        assertThat(ListBlock.markerTextGapPx(false)).isEqualTo(AndroidUtilities.dp(9f))
        assertThat(ListBlock.trailingTextInsetPx()).isEqualTo(AndroidUtilities.dp(18f))
        assertThat(ListBlock.markerTextXPx(markerWidth, false, 0))
            .isEqualTo(AndroidUtilities.dp(24f) + markerWidth)
        assertThat(ListBlock.markerTextXPx(markerWidth, false, 2))
            .isEqualTo(AndroidUtilities.dp(24f) + markerWidth + AndroidUtilities.dp(24f))
        assertThat(ListBlock.markerTextXPx(markerWidth, true, 2))
            .isEqualTo(AndroidUtilities.dp(24f) + markerWidth + AndroidUtilities.dp(40f))
        assertThat(ListBlock.markerRightPx(ListBlock.markerTextXPx(markerWidth, false, 2), false))
            .isEqualTo((AndroidUtilities.dp(15f) + markerWidth + AndroidUtilities.dp(24f)).toFloat())
        assertThat(ListBlock.markerRightPx(ListBlock.markerTextXPx(markerWidth, true, 2), true))
            .isEqualTo((AndroidUtilities.dp(18f) + markerWidth + AndroidUtilities.dp(40f)).toFloat())
    }

    @Test
    fun postSelectionLayoutBlock_exposesPrefixAndSelectionBounds() {
        val textLayout = staticLayout("item")
        val selectionBounds = Rect(12, 48, 60, 80)

        val block = PostSelectionLayoutBlock(
            textLayout,
            x = 40,
            y = 50,
            row = 0,
            prefix = "•",
            selectionBounds = selectionBounds,
        )

        assertThat(block.layout).isSameInstanceAs(textLayout)
        assertThat(block.prefix).isEqualTo("•")
        assertThat(block.selectionBounds).isSameInstanceAs(selectionBounds)
    }

    @Suppress("DEPRECATION")
    private fun staticLayout(text: String): StaticLayout {
        return StaticLayout(
            text,
            TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
                textSize = 16f
            },
            100,
            Layout.Alignment.ALIGN_NORMAL,
            1f,
            0f,
            false,
        )
    }
}
