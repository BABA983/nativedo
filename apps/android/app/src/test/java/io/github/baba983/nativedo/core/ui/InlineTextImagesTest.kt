package io.github.baba983.nativedo.core.ui

import android.graphics.Paint
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InlineTextImagesTest {
    @Test
    fun calculateInlineImageTranslationY_emojiMovesUpMoreThanSiteIcon() {
        val emojiTranslationY = calculateInlineImageTranslationY(
            top = 4,
            bottom = 28,
            drawableHeight = 16,
            kind = InlineImageKind.Emoji,
        )
        val siteIconTranslationY = calculateInlineImageTranslationY(
            top = 4,
            bottom = 28,
            drawableHeight = 16,
            kind = InlineImageKind.SiteIcon,
        )

        assertThat(emojiTranslationY).isWithin(0.001f).of(6.08f)
        assertThat(siteIconTranslationY).isWithin(0.001f).of(7.36f)
        assertThat(emojiTranslationY).isLessThan(siteIconTranslationY)
    }

    @Test
    fun calculateInlineImageSizePx_usesFontMetricsAndKindSpecificScale() {
        val fontMetrics = Paint.FontMetricsInt().apply {
            ascent = -12
            descent = 4
        }

        val emojiSize = calculateInlineImageSizePx(
            fontMetrics = fontMetrics,
            kind = InlineImageKind.Emoji,
        )
        val siteIconSize = calculateInlineImageSizePx(
            fontMetrics = fontMetrics,
            kind = InlineImageKind.SiteIcon,
        )

        assertThat(emojiSize).isEqualTo(16)
        assertThat(siteIconSize).isEqualTo(15)
    }
}
