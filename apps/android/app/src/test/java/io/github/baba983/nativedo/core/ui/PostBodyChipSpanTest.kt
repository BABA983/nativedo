package io.github.baba983.nativedo.core.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ReplacementSpan
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.telegram.ui.Cells.TextSelectionHelper

@RunWith(RobolectricTestRunner::class)
class PostBodyChipSpanTest {
    @Test
    fun chipBackground_preservesParagraphFontMetrics() {
        val span = ChipBackgroundSpan(
            backgroundColor = 0x221677FF,
            textColor = 0xFF1677FF.toInt(),
            cornerRadiusPx = 6f,
            horizontalPaddingPx = 4f,
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f
        }
        val original = paint.fontMetricsInt
        val fm = Paint.FontMetricsInt()

        val width = span.getSize(paint, "@baba", 0, 5, fm)

        assertThat(width).isGreaterThan(0)
        assertThat(fm.ascent).isEqualTo(original.ascent)
        assertThat(fm.descent).isEqualTo(original.descent)
        assertThat(fm.top).isEqualTo(original.top)
        assertThat(fm.bottom).isEqualTo(original.bottom)
        assertThat(fm.leading).isEqualTo(original.leading)
    }

    @Test
    fun chipBackground_exposesPillHighlightBounds() {
        val span = ChipBackgroundSpan(
            backgroundColor = 0x221677FF,
            textColor = 0xFF1677FF.toInt(),
            cornerRadiusPx = 6f,
            horizontalPaddingPx = 4f,
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f
        }
        val width = span.getSize(paint, "@baba", 0, 5, null)

        val bounds = (span as PostBodyLinkHighlightSpan).linkHighlightBounds(
            paint = paint,
            text = "@baba",
            start = 0,
            end = 5,
            x = 12f,
            top = 0,
            y = 20,
            bottom = 28,
        )

        assertThat(bounds.left).isEqualTo(12f)
        assertThat(bounds.width()).isWithin(0.01f).of(width.toFloat())
        assertThat(bounds.height()).isGreaterThan(0f)
        assertThat((span as PostBodyLinkHighlightSpan).linkHighlightCornerRadius()).isEqualTo(6f)
    }

    @Test
    fun tagChip_preservesParagraphFontMetrics() {
        val span = DiscourseTagChipSpan(
            label = "快问快答",
            icon = null,
            backgroundColor = 0x221677FF,
            textColor = 0xFF1677FF.toInt(),
            textSizePx = 18f,
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f
        }
        val original = paint.fontMetricsInt
        val fm = Paint.FontMetricsInt()

        val width = span.getSize(paint, "快问快答", 0, 4, fm)

        assertThat(width).isGreaterThan(0)
        assertThat(fm.ascent).isEqualTo(original.ascent)
        assertThat(fm.descent).isEqualTo(original.descent)
        assertThat(fm.top).isEqualTo(original.top)
        assertThat(fm.bottom).isEqualTo(original.bottom)
        assertThat(fm.leading).isEqualTo(original.leading)
    }

    @Test
    fun tagChip_exposesPillHighlightBounds() {
        val span = DiscourseTagChipSpan(
            label = "快问快答",
            icon = null,
            backgroundColor = 0x221677FF,
            textColor = 0xFF1677FF.toInt(),
            textSizePx = 18f,
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f
        }
        val width = span.getSize(paint, "快问快答", 0, 4, null)

        val bounds = (span as PostBodyLinkHighlightSpan).linkHighlightBounds(
            paint = paint,
            text = "快问快答",
            start = 0,
            end = 4,
            x = 8f,
            top = 0,
            y = 20,
            bottom = 28,
        )

        assertThat(bounds.left).isEqualTo(8f)
        assertThat(bounds.width()).isWithin(0.01f).of(width.toFloat())
        assertThat(bounds.height()).isGreaterThan(0f)
        assertThat((span as PostBodyLinkHighlightSpan).linkHighlightCornerRadius()).isGreaterThan(0f)
        assertThat((span as PostBodyLinkHighlightSpan).linkHighlightBounds(
            paint,
            "快问快答",
            0,
            4,
            8f,
            0,
            20,
            28,
        ).width()).isWithin(0.01f).of(bounds.width())
    }

    @Test
    fun draw_forwardsNestedReplacementSpanInsideChip() {
        val text = SpannableString("\uFFFC tag")
        val iconSpan = RecordingReplacementSpan(width = 12)
        val chipSpan = ChipBackgroundSpan(
            backgroundColor = 0x221677FF,
            textColor = 0xFF1677FF.toInt(),
            cornerRadiusPx = 6f,
            horizontalPaddingPx = 4f,
        )
        text.setSpan(iconSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(chipSpan, 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 16f
        }
        val width = chipSpan.getSize(paint, text, 0, text.length, null)
        chipSpan.draw(
            canvas = Canvas(),
            text = text,
            start = 0,
            end = text.length,
            x = 0f,
            top = 0,
            y = 20,
            bottom = 28,
            paint = paint,
        )

        assertThat(width).isAtLeast(20)
        assertThat(iconSpan.drawCount).isEqualTo(1)
    }

    private class RecordingReplacementSpan(
        private val width: Int,
    ) : ReplacementSpan() {
        var drawCount = 0
            private set

        override fun getSize(
            paint: Paint,
            text: CharSequence,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?,
        ): Int = width

        override fun draw(
            canvas: Canvas,
            text: CharSequence,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint,
        ) {
            drawCount += 1
        }
    }
}
