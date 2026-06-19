package io.github.baba983.nativedo.ui.topic.body

import android.text.Layout
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
    fun postSelectionLayoutBlock_exposesOptionalPrefixLayoutGeometry() {
        val textLayout = staticLayout("item")
        val prefixLayout = staticLayout("•")

        val block = PostSelectionLayoutBlock(
            textLayout,
            x = 40,
            y = 50,
            row = 0,
            prefix = "•",
            prefixLayout = prefixLayout,
            prefixX = 12,
            prefixY = 48,
        )

        assertThat(block.layout).isSameInstanceAs(textLayout)
        assertThat(block.prefix).isEqualTo("•")
        assertThat(block.prefixLayout).isSameInstanceAs(prefixLayout)
        assertThat(block.prefixX).isEqualTo(12)
        assertThat(block.prefixY).isEqualTo(48)
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
