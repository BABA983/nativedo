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
}
