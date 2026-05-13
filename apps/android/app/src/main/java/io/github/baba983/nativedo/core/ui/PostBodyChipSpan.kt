// PORT-MODE: Native Feature
// SCOPE: Discourse-specific inline chip rendering for @mentions and #hashtags.
//        Telegram instant view does not have a direct analogue (it would render
//        these as plain styled text via TextPaintUrlSpan); the chip pill is a
//        NativeDo product decision to mirror Discourse's web styling.
package io.github.baba983.nativedo.core.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.style.ReplacementSpan
import androidx.annotation.ColorInt

/**
 * Inline pill-shaped chip span. Replaces the natural rendering of the wrapped
 * range with a rounded background plus tinted bold text. Coexists with a
 * ClickableSpan on the same range, so taps still fire the link handler.
 */
internal class ChipBackgroundSpan(
    @ColorInt private val backgroundColor: Int,
    @ColorInt private val textColor: Int,
    private val cornerRadiusPx: Float,
    private val horizontalPaddingPx: Float,
) : ReplacementSpan() {

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        val textWidth = paint.measureText(text, start, end)
        if (fm != null) {
            val originalFm = paint.fontMetricsInt
            fm.ascent = originalFm.ascent
            fm.descent = originalFm.descent
            fm.top = originalFm.top
            fm.bottom = originalFm.bottom
            fm.leading = originalFm.leading
        }
        return (textWidth + horizontalPaddingPx * 2f).toInt()
    }

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
        val textWidth = paint.measureText(text, start, end)
        val originalColor = paint.color
        val originalTypeface = paint.typeface
        val originalFlags = paint.flags

        val rect = RectF(
            x,
            top.toFloat() + 1f,
            x + textWidth + horizontalPaddingPx * 2f,
            bottom.toFloat() - 1f,
        )
        paint.color = backgroundColor
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint)

        paint.color = textColor
        paint.typeface = Typeface.create(originalTypeface, Typeface.BOLD)
        paint.flags = originalFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
        canvas.drawText(text, start, end, x + horizontalPaddingPx, y.toFloat(), paint)

        paint.color = originalColor
        paint.typeface = originalTypeface
        paint.flags = originalFlags
    }
}
