package io.github.baba983.nativedo.ui.topic.body

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.telegram.messenger.AndroidUtilities

@RunWith(RobolectricTestRunner::class)
class ImageBlockTest {
    @Test
    fun photoGeometry_matchesTelegramBlockPhotoCell() {
        assertThat(ImageBlock.minWidthPx()).isEqualTo(AndroidUtilities.dp(120f))
        assertThat(ImageBlock.photoTopGapPx(true)).isEqualTo(0)
        assertThat(ImageBlock.photoTopGapPx(false)).isEqualTo(AndroidUtilities.dp(8f))
        assertThat(ImageBlock.progressButtonSizePx()).isEqualTo(AndroidUtilities.dp(48f))
    }

    @Test
    fun captionGeometry_matchesArticleViewerPhotoCaption() {
        val parentWidth = AndroidUtilities.dp(320f)

        assertThat(ImageBlock.captionXPx()).isEqualTo(AndroidUtilities.dp(18f))
        assertThat(ImageBlock.captionWidthPx(parentWidth)).isEqualTo(parentWidth - AndroidUtilities.dp(36f))
        assertThat(ImageBlock.captionWidthPx(AndroidUtilities.dp(40f))).isEqualTo(AndroidUtilities.dp(80f))
        assertThat(ImageBlock.captionVerticalGapPx()).isEqualTo(AndroidUtilities.dp(8f))
        assertThat(ImageBlock.captionBottomPaddingPx()).isEqualTo(AndroidUtilities.dp(8f))
    }

    @Test
    fun externalLinkBadgeGeometry_matchesBlockPhotoCellInstantLinkBadge() {
        val parentWidth = AndroidUtilities.dp(320f)
        val imageTop = AndroidUtilities.dp(9f).toFloat()

        assertThat(ImageBlock.instantLinkSizePx()).isEqualTo(AndroidUtilities.dp(24f))
        assertThat(ImageBlock.instantLinkLeftPx(parentWidth)).isEqualTo(parentWidth - AndroidUtilities.dp(35f))
        assertThat(ImageBlock.instantLinkTopPx(imageTop)).isEqualTo(AndroidUtilities.dp(20f))
    }
}
