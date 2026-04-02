package io.github.baba983.nativedo.core.ui

import android.text.Spanned
import android.text.style.ImageSpan
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InlineTextImagesTest {
    @Test
    fun applyInlineImagePlaceholders_usesBaselineAlignmentForInlineIcons() {
        val applied = applyInlineImagePlaceholders(
            text = "Before inline_image_0 after",
            inlineImages = listOf(
                InlineImageSpec(
                    token = "inline_image_0",
                    rawUrl = "https://github.com/favicon.ico",
                    contentDescription = "GitHub",
                    kind = InlineImageKind.SiteIcon,
                ),
            ),
            baseUrl = "https://linux.do/",
            textSizePx = 16f,
        )

        val imageSpan = (applied.text as Spanned)
            .getSpans(0, applied.text.length, ImageSpan::class.java)
            .single()

        assertThat(imageSpan.verticalAlignment).isEqualTo(ImageSpan.ALIGN_BASELINE)
    }
}
