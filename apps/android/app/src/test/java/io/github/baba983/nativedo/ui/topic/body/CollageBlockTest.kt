package io.github.baba983.nativedo.ui.topic.body

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.telegram.messenger.AndroidUtilities

@RunWith(RobolectricTestRunner::class)
class CollageBlockTest {
    @Test
    fun collageOuterGeometry_matchesTelegramBlockCollageCell() {
        assertThat(CollageBlock.maxTilesPerRow()).isEqualTo(3)
        assertThat(CollageBlock.maxSizeWidth()).isEqualTo(1000f)
        assertThat(CollageBlock.maxSizeHeight()).isEqualTo(814f)
        assertThat(CollageBlock.tileSpacingPx()).isEqualTo(AndroidUtilities.dp(2f))
        assertThat(CollageBlock.listTopPaddingPx()).isEqualTo(AndroidUtilities.dp(8f))
        assertThat(CollageBlock.bottomPaddingPx()).isEqualTo(AndroidUtilities.dp(16f))
    }

    @Test
    fun collageCaptionGeometry_matchesArticleViewerMediaCaption() {
        val parentWidth = AndroidUtilities.dp(320f)

        assertThat(CollageBlock.captionXPx()).isEqualTo(AndroidUtilities.dp(18f))
        assertThat(CollageBlock.captionWidthPx(parentWidth)).isEqualTo(parentWidth - AndroidUtilities.dp(36f))
        assertThat(CollageBlock.captionWidthPx(AndroidUtilities.dp(40f))).isEqualTo(AndroidUtilities.dp(80f))
    }

    @Test
    fun collageRowGeometry_matchesGroupedMessagesLayoutGuardrails() {
        assertThat(CollageBlock.singleMinHeightPx()).isEqualTo(AndroidUtilities.dp(140f))
        assertThat(CollageBlock.singleMaxHeightPx()).isEqualTo(AndroidUtilities.dp(420f))
        assertThat(CollageBlock.rowTargetHeightPx()).isEqualTo(AndroidUtilities.dp(180f))
        assertThat(CollageBlock.rowMinHeightPx()).isEqualTo(AndroidUtilities.dp(120f))
        assertThat(CollageBlock.rowMaxHeightPx()).isEqualTo(AndroidUtilities.dp(260f))
        assertThat(CollageBlock.attemptMinLineHeightPx()).isEqualTo(AndroidUtilities.dp(100f))
        assertThat(CollageBlock.tileMinWidthPx()).isEqualTo(AndroidUtilities.dp(64f))
        assertThat(CollageBlock.progressButtonSizePx()).isEqualTo(AndroidUtilities.dp(48f))
    }
}
