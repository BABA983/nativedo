package io.github.baba983.nativedo.core.ui

import android.graphics.Paint
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InlineTextImagesTest {
    @Test
    fun emojiInlineImageScaleForUrl_distinguishesTwemojiAndCustomEmoji() {
        val twemojiScale = emojiInlineImageScaleForUrl(
            "https://cdn.linux.do/images/emoji/twemoji/tada.png?v=15",
        )
        val customEmojiScale = emojiInlineImageScaleForUrl(
            "https://cdn3.linux.do/original/3X/9/a/9ac368cc8eafad165bbcf61b0263803d3b2dc2a7.png?v=15",
        )

        assertThat(twemojiScale).isWithin(0.001f).of(0.90f)
        assertThat(customEmojiScale).isWithin(0.001f).of(1.0f)
    }

    @Test
    fun calculateInlineImageTranslationY_emojiAnchorsToTextBottomWhenLineBoxIsTaller() {
        val emojiTranslationY = calculateInlineImageTranslationY(
            top = 4,
            bottom = 32,
            baseline = 20,
            fontDescent = 4,
            drawableHeight = 16,
            kind = InlineImageKind.Emoji,
        )
        val siteIconTranslationY = calculateInlineImageTranslationY(
            top = 4,
            bottom = 32,
            baseline = 20,
            fontDescent = 4,
            drawableHeight = 16,
            kind = InlineImageKind.SiteIcon,
        )

        assertThat(emojiTranslationY).isWithin(0.001f).of(7.36f)
        assertThat(siteIconTranslationY).isWithin(0.001f).of(9.36f)
        assertThat(emojiTranslationY).isLessThan(siteIconTranslationY)
    }

    @Test
    fun calculateInlineImageSizePx_usesFontMetricsAndKindSpecificScale() {
        val fontMetrics = Paint.FontMetricsInt().apply {
            ascent = -12
            descent = 4
        }

        val twemojiSize = calculateInlineImageSizePx(
            fontMetrics = fontMetrics,
            kind = InlineImageKind.Emoji,
            emojiScale = 0.90f,
        )
        val customEmojiSize = calculateInlineImageSizePx(
            fontMetrics = fontMetrics,
            kind = InlineImageKind.Emoji,
        )
        val siteIconSize = calculateInlineImageSizePx(
            fontMetrics = fontMetrics,
            kind = InlineImageKind.SiteIcon,
        )

        assertThat(twemojiSize).isEqualTo(14)
        assertThat(customEmojiSize).isEqualTo(16)
        assertThat(siteIconSize).isEqualTo(15)
    }
}
