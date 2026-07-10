package io.github.baba983.nativedo.ui.topic.body

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader

@RunWith(RobolectricTestRunner::class)
class QuoteBlockTest {
    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun collapsedTitleUsesSingleLineEllipsisWhenExpandable() {
        assertThat(QuoteBlock.TITLE_CATEGORY_CHIP_V_GAP_DP).isEqualTo(4f)
    }

    @Test
    fun expandChevronGeometry_matchesDetailsBlockSmallArrow() {
        assertThat(QuoteBlock.titleChevronSizePx()).isEqualTo(AndroidUtilities.dp(13f))
        assertThat(QuoteBlock.titleChevronHitPx()).isEqualTo(AndroidUtilities.dp(36f))
    }

    @Test
    fun titleAvatarGeometry_matchesDiscourse24dpAvatarColumn() {
        assertThat(QuoteBlock.titleAvatarSizePx()).isEqualTo(AndroidUtilities.dp(24f))
        assertThat(QuoteBlock.titleAvatarTextGapPx()).isEqualTo(AndroidUtilities.dp(10f))
    }

    @Test
    fun blockquoteConstants_matchTelegramArticleViewerGeometry() {
        assertThat(QuoteBlock.textTopPx()).isEqualTo(AndroidUtilities.dp(8f))
        assertThat(QuoteBlock.lineLeftPx()).isEqualTo(AndroidUtilities.dp(18f))
        assertThat(QuoteBlock.lineRightPx()).isEqualTo(AndroidUtilities.dp(20f))
        assertThat(QuoteBlock.textInsetAfterLinePx()).isEqualTo(AndroidUtilities.dp(14f))
        assertThat(QuoteBlock.sidePaddingPx()).isEqualTo(AndroidUtilities.dp(18f))
        assertThat(QuoteBlock.lineVerticalInsetPx()).isEqualTo(AndroidUtilities.dp(6f))
    }

    @Test
    fun derivedBlockquoteGeometry_matchesTelegramArticleViewer() {
        assertThat(QuoteBlock.textLeftPx()).isEqualTo(AndroidUtilities.dp(18f) + AndroidUtilities.dp(14f))
        assertThat(QuoteBlock.lineRightPx() - QuoteBlock.lineLeftPx()).isEqualTo(AndroidUtilities.dp(2f))
        assertThat(QuoteBlock.titleBodyGapPx()).isEqualTo(AndroidUtilities.dp(8f))
        assertThat(QuoteBlock.bodyBottomPaddingPx()).isEqualTo(AndroidUtilities.dp(8f))
        assertThat(QuoteBlock.bodyBlockSpacingPx()).isEqualTo(0)
    }

    @Test
    fun nestedBlockquoteGeometry_matchesTelegramLevelOffsetsInRecursiveCoordinates() {
        assertThat(QuoteBlock.lineLeftPx(1)).isEqualTo(0)
        assertThat(QuoteBlock.lineRightPx(1)).isEqualTo(AndroidUtilities.dp(2f))
        assertThat(QuoteBlock.parentLineLeftPx(1)).isEqualTo(-AndroidUtilities.dp(14f))
        assertThat(QuoteBlock.parentLineRightPx(1)).isEqualTo(-AndroidUtilities.dp(14f) + AndroidUtilities.dp(2f))
        assertThat(QuoteBlock.textLeftPx(1)).isEqualTo(AndroidUtilities.dp(14f))
        assertThat(QuoteBlock.sidePaddingPx(1)).isEqualTo(0)

        val parentTextLeft = QuoteBlock.textLeftPx(0)
        assertThat(parentTextLeft + QuoteBlock.parentLineLeftPx(1))
            .isEqualTo(AndroidUtilities.dp(18f))
        assertThat(parentTextLeft + QuoteBlock.lineLeftPx(1))
            .isEqualTo(AndroidUtilities.dp(18f + 14f))
        assertThat(parentTextLeft + QuoteBlock.textLeftPx(1))
            .isEqualTo(AndroidUtilities.dp(18f + 14f + 14f))

        val deepParentTextLeft = parentTextLeft + QuoteBlock.textLeftPx(1)
        assertThat(deepParentTextLeft + QuoteBlock.parentLineLeftPx(2))
            .isEqualTo(AndroidUtilities.dp(18f))
        assertThat(deepParentTextLeft + QuoteBlock.lineLeftPx(2))
            .isEqualTo(AndroidUtilities.dp(18f + 14f * 2f))

        val width = AndroidUtilities.dp(320f)
        val parentBodyWidth = width - QuoteBlock.textLeftPx(0) - QuoteBlock.sidePaddingPx(0)
        val nestedBodyWidth = parentBodyWidth - QuoteBlock.textLeftPx(1) - QuoteBlock.sidePaddingPx(1)
        assertThat(nestedBodyWidth)
            .isEqualTo(width - AndroidUtilities.dp(36f + 14f) - AndroidUtilities.dp(14f))
    }
}
