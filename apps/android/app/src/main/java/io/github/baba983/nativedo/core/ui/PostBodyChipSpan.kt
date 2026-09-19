// SCOPE: Discourse-specific inline chip rendering for @mentions and #hashtags.
//        Telegram instant view does not have a direct analogue (it would render
//        these as plain styled text via TextPaintUrlSpan); the chip pill is a
//        NativeDo product decision to mirror Discourse's web styling.
package io.github.baba983.nativedo.core.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Spanned
import android.text.style.ReplacementSpan
import androidx.annotation.ColorInt
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.Cells.TextSelectionHelper
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

interface PostBodyLinkHighlightSpan {
    fun linkHighlightBounds(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
    ): RectF

    fun linkHighlightCornerRadius(): Float
}

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
) : ReplacementSpan(), PostBodyLinkHighlightSpan {
    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        val textWidth = measureChipContent(paint, text, start, end)
        applyCenteredSpanMetrics(paint, fm, chipHeight(paint))
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
        val textWidth = measureChipContent(paint, text, start, end)
        val originalColor = paint.color
        val originalTypeface = paint.typeface
        val originalFlags = paint.flags
        val originalStyle = paint.style

        val fontMetrics = paint.fontMetrics
        val centerY = y + (fontMetrics.ascent + fontMetrics.descent) / 2f
        val chipHeight = fontMetrics.descent - fontMetrics.ascent + 2f
        val rect = RectF(
            x,
            centerY - chipHeight / 2f,
            x + textWidth + horizontalPaddingPx * 2f,
            centerY + chipHeight / 2f,
        )
        paint.color = backgroundColor
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint)

        paint.color = textColor
        paint.typeface = Typeface.create(originalTypeface, Typeface.BOLD)
        paint.isUnderlineText = false
        paint.style = Paint.Style.FILL
        drawChipContent(
            canvas = canvas,
            text = text,
            start = start,
            end = end,
            x = x + horizontalPaddingPx,
            top = top,
            y = y,
            bottom = bottom,
            paint = paint,
        )

        paint.color = originalColor
        paint.typeface = originalTypeface
        paint.flags = originalFlags
        paint.style = originalStyle
    }

    override fun linkHighlightBounds(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
    ): RectF {
        val textWidth = measureChipContent(paint, text, start, end)
        val fontMetrics = paint.fontMetrics
        val centerY = y + (fontMetrics.ascent + fontMetrics.descent) / 2f
        val chipHeight = fontMetrics.descent - fontMetrics.ascent + 2f
        return RectF(
            x,
            centerY - chipHeight / 2f,
            x + textWidth + horizontalPaddingPx * 2f,
            centerY + chipHeight / 2f,
        )
    }

    override fun linkHighlightCornerRadius(): Float = cornerRadiusPx

    private fun measureChipContent(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
    ): Float {
        var width = 0f
        var index = start
        while (index < end) {
            val replacement = innerReplacementAt(text, index, end)
            if (replacement != null) {
                width += replacement.span.getSize(paint, text, replacement.start, replacement.end, null)
                index = replacement.end
                continue
            }

            val nextReplacementStart = nextInnerReplacementStart(text, index, end)
            width += paint.measureText(text, index, nextReplacementStart)
            index = nextReplacementStart
        }
        return width
    }

    private fun chipHeight(paint: Paint): Float {
        val fontMetrics = paint.fontMetrics
        return fontMetrics.descent - fontMetrics.ascent + 2f
    }

    private fun drawChipContent(
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
        var currentX = x
        var index = start
        while (index < end) {
            val replacement = innerReplacementAt(text, index, end)
            if (replacement != null) {
                replacement.span.draw(
                    canvas,
                    text,
                    replacement.start,
                    replacement.end,
                    currentX,
                    top,
                    y,
                    bottom,
                    paint,
                )
                currentX += replacement.span.getSize(paint, text, replacement.start, replacement.end, null)
                index = replacement.end
                continue
            }

            val nextReplacementStart = nextInnerReplacementStart(text, index, end)
            canvas.drawText(text, index, nextReplacementStart, currentX, y.toFloat(), paint)
            currentX += paint.measureText(text, index, nextReplacementStart)
            index = nextReplacementStart
        }
    }

    private fun innerReplacementAt(
        text: CharSequence,
        index: Int,
        rangeEnd: Int,
    ): InnerReplacement? {
        val spanned = text as? Spanned ?: return null
        val span = spanned
            .getSpans(index, index + 1, ReplacementSpan::class.java)
            .firstOrNull { candidate ->
                candidate !== this &&
                    spanned.getSpanStart(candidate) == index &&
                    spanned.getSpanEnd(candidate) > index
            }
            ?: return null
        return InnerReplacement(
            span = span,
            start = index,
            end = spanned.getSpanEnd(span).coerceAtMost(rangeEnd),
        )
    }

    private fun nextInnerReplacementStart(
        text: CharSequence,
        index: Int,
        end: Int,
    ): Int {
        val spanned = text as? Spanned ?: return end
        return spanned
            .getSpans(index, end, ReplacementSpan::class.java)
            .asSequence()
            .filter { span -> span !== this }
            .map { span -> spanned.getSpanStart(span) }
            .filter { spanStart -> spanStart > index && spanStart < end }
            .minOrNull()
            ?: end
    }

    private data class InnerReplacement(
        val span: ReplacementSpan,
        val start: Int,
        val end: Int,
    )
}

internal class DiscourseTagChipSpan(
    private val label: String,
    icon: Drawable?,
    @ColorInt private val backgroundColor: Int,
    @ColorInt private val textColor: Int,
    textSizePx: Float,
) : ReplacementSpan(), PostBodyLinkHighlightSpan {
    private val horizontalPaddingPx = AndroidUtilities.dp(6f).toFloat()
    private val verticalPaddingPx = AndroidUtilities.dp(2f).toFloat()
    private val iconTextGapPx = AndroidUtilities.dp(4f).toFloat()
    private val cornerRadiusPx = AndroidUtilities.dp(5f).toFloat()
    private val iconSizePx = max(1, (textSizePx * 0.92f).roundToInt())
    private val iconDrawable = icon?.mutate()?.apply {
        setBounds(0, 0, iconSizePx, iconSizePx)
    }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        val textWidth = measureBoldLabel(paint)
        applyCenteredSpanMetrics(paint, fm, chipHeight(paint))

        return ceil(chipWidth(textWidth)).toInt()
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
        val originalColor = paint.color
        val originalTypeface = paint.typeface
        val originalFlags = paint.flags
        val originalStyle = paint.style

        val textWidth = measureBoldLabel(paint)
        val rect = chipBounds(paint, x, y, textWidth)
        val centerY = rect.centerY()

        backgroundPaint.color = backgroundColor
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, backgroundPaint)

        var contentX = x + horizontalPaddingPx
        iconDrawable?.let { drawable ->
            canvas.save()
            canvas.translate(contentX, iconTop(centerY))
            drawable.draw(canvas)
            canvas.restore()
            contentX += iconSizePx + iconTextGapPx
        }

        paint.typeface = Typeface.create(originalTypeface, Typeface.BOLD)
        paint.isUnderlineText = false
        paint.color = textColor
        paint.style = Paint.Style.FILL
        canvas.drawText(label, contentX, y.toFloat(), paint)

        paint.color = originalColor
        paint.typeface = originalTypeface
        paint.flags = originalFlags
        paint.style = originalStyle
    }

    override fun linkHighlightBounds(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
    ): RectF {
        val textWidth = measureBoldLabel(paint)
        return chipBounds(paint, x, y, textWidth)
    }

    override fun linkHighlightCornerRadius(): Float = cornerRadiusPx

    private fun measureBoldLabel(paint: Paint): Float {
        val originalTypeface = paint.typeface
        paint.typeface = Typeface.create(originalTypeface, Typeface.BOLD)
        val textWidth = paint.measureText(label)
        paint.typeface = originalTypeface
        return textWidth
    }

    private fun chipHeight(paint: Paint): Float {
        val fontMetrics = paint.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent
        return max(iconSizePx + verticalPaddingPx * 2f, textHeight + verticalPaddingPx * 2f)
    }

    private fun chipWidth(textWidth: Float): Float {
        val iconWidth = if (iconDrawable != null) iconSizePx + iconTextGapPx else 0f
        return horizontalPaddingPx * 2f + iconWidth + textWidth
    }

    private fun chipBounds(
        paint: Paint,
        x: Float,
        baselineY: Int,
        textWidth: Float,
    ): RectF {
        val fontMetrics = paint.fontMetricsInt
        val centerY = baselineY + (fontMetrics.ascent + fontMetrics.descent) / 2f
        val height = chipHeight(paint)
        return RectF(
            x,
            centerY - height / 2f,
            x + chipWidth(textWidth),
            centerY + height / 2f,
        )
    }

    private fun iconTop(centerY: Float): Float {
        return (centerY - iconSizePx / 2f).roundToInt().toFloat()
    }
}

private fun applyCenteredSpanMetrics(
    paint: Paint,
    fm: Paint.FontMetricsInt?,
    spanHeightPx: Float,
) {
    if (fm == null) {
        return
    }
    val originalFm = paint.fontMetricsInt
    val center = (originalFm.ascent + originalFm.descent) / 2f
    val spanAscent = floor(center - spanHeightPx / 2f).toInt()
    val spanDescent = ceil(center + spanHeightPx / 2f).toInt()
    fm.ascent = min(originalFm.ascent, spanAscent)
    fm.descent = max(originalFm.descent, spanDescent)
    fm.top = min(originalFm.top, fm.ascent)
    fm.bottom = max(originalFm.bottom, fm.descent)
    fm.leading = originalFm.leading
}
