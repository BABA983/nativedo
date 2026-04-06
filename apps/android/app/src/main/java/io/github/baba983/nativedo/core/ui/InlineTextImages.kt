package io.github.baba983.nativedo.core.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val emojiInlineImageScale = 1.0f
private const val siteIconInlineImageScale = 0.92f
private const val emojiInlineImageUpwardBiasRatio = 0.04f
private const val siteIconInlineImageUpwardBiasRatio = 0.04f

internal data class AppliedInlineImages(
    val text: CharSequence,
    val pendingImages: List<PendingInlineImage>,
)

internal data class PendingInlineImage(
    val resolvedUrl: String,
    val drawable: DeferredInlineImageDrawable,
)

internal fun applyInlineImagePlaceholders(
    text: CharSequence,
    inlineImages: List<InlineImageSpec>,
    baseUrl: String,
    textSizePx: Float,
): AppliedInlineImages {
    val builder = SpannableStringBuilder(text)
    val pendingImages = mutableListOf<PendingInlineImage>()
    val searchableText = text.toString()
    var replacedCharacterCount = 0

    inlineImages.forEach { image ->
        val originalStart = searchableText.indexOf(image.token)
        if (originalStart == -1) {
            return@forEach
        }

        val start = originalStart - replacedCharacterCount
        val end = start + image.token.length
        val drawable = DeferredInlineImageDrawable(
            widthPx = image.displayWidthPx(textSizePx),
            heightPx = image.displayHeightPx(textSizePx),
        )
        builder.replace(start, end, "\uFFFC")
        builder.setSpan(
            CenteredInlineImageSpan(
                drawable = drawable,
                kind = image.kind,
            ),
            start,
            start + 1,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        pendingImages += PendingInlineImage(
            resolvedUrl = resolvePostImageUrl(
                baseUrl = baseUrl,
                rawUrl = image.rawUrl,
            ),
            drawable = drawable,
        )
        replacedCharacterCount += image.token.length - 1
    }

    return AppliedInlineImages(
        text = builder,
        pendingImages = pendingImages,
    )
}

internal fun calculateInlineImageTranslationY(
    top: Int,
    bottom: Int,
    drawableHeight: Int,
    kind: InlineImageKind,
): Float {
    val upwardBiasRatio = when (kind) {
        InlineImageKind.Emoji -> emojiInlineImageUpwardBiasRatio
        InlineImageKind.SiteIcon -> siteIconInlineImageUpwardBiasRatio
    }
    return top + ((bottom - top - drawableHeight) / 2f) - (drawableHeight * upwardBiasRatio)
}

internal fun calculateInlineImageSizePx(
    fontMetrics: Paint.FontMetricsInt,
    kind: InlineImageKind,
): Int {
    val fontHeight = max(1, abs(fontMetrics.ascent) + abs(fontMetrics.descent))
    val scale = when (kind) {
        InlineImageKind.Emoji -> emojiInlineImageScale
        InlineImageKind.SiteIcon -> siteIconInlineImageScale
    }
    return max(1, (fontHeight * scale).roundToInt())
}

internal class CenteredInlineImageSpan(
    drawable: Drawable,
    private val kind: InlineImageKind,
) : ImageSpan(drawable, ALIGN_BASELINE) {
    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        val paintMetrics = paint.fontMetricsInt
        val targetSizePx = calculateInlineImageSizePx(
            fontMetrics = paintMetrics,
            kind = kind,
        )
        drawable.setBounds(0, 0, targetSizePx, targetSizePx)
        if (fm != null) {
            fm.ascent = paintMetrics.ascent
            fm.descent = paintMetrics.descent
            fm.top = paintMetrics.top
            fm.bottom = paintMetrics.bottom
        }
        return targetSizePx
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
        val drawable = drawable
        canvas.save()
        canvas.translate(
            x,
            calculateInlineImageTranslationY(
                top = top,
                bottom = bottom,
                drawableHeight = drawable.bounds.height(),
                kind = kind,
            ),
        )
        drawable.draw(canvas)
        canvas.restore()
    }
}

@Suppress("OVERRIDE_DEPRECATION")
internal class DeferredInlineImageDrawable(
    widthPx: Int,
    heightPx: Int,
) : Drawable() {
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x335D6A7A
    }
    private var bitmap: Bitmap? = null

    init {
        setBounds(0, 0, max(1, widthPx), max(1, heightPx))
    }

    fun setBitmap(bitmap: Bitmap) {
        this.bitmap = bitmap
        val targetBounds = bounds
        val maxWidth = max(1, targetBounds.width())
        val maxHeight = max(1, targetBounds.height())
        val scale = minOf(
            maxWidth.toFloat() / bitmap.width.toFloat(),
            maxHeight.toFloat() / bitmap.height.toFloat(),
        )
        val scaledWidth = max(1, (bitmap.width * scale).roundToInt())
        val scaledHeight = max(1, (bitmap.height * scale).roundToInt())
        setBounds(0, 0, scaledWidth, scaledHeight)
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val bitmap = bitmap
        if (bitmap == null) {
            canvas.drawRoundRect(
                bounds.left.toFloat(),
                bounds.top.toFloat(),
                bounds.right.toFloat(),
                bounds.bottom.toFloat(),
                bounds.width() * 0.18f,
                bounds.height() * 0.18f,
                placeholderPaint,
            )
            return
        }

        canvas.drawBitmap(bitmap, null, bounds, null)
    }

    override fun setAlpha(alpha: Int) {
        placeholderPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        placeholderPaint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
