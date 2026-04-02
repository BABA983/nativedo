package io.github.baba983.nativedo.core.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import kotlin.math.max
import kotlin.math.roundToInt

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

    inlineImages.forEach { image ->
        val start = builder.toString().indexOf(image.token)
        if (start == -1) {
            return@forEach
        }

        val end = start + image.token.length
        val drawable = DeferredInlineImageDrawable(
            widthPx = image.displayWidthPx(textSizePx),
            heightPx = image.displayHeightPx(textSizePx),
        )
        builder.replace(start, end, "\uFFFC")
        builder.setSpan(
            ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM),
            start,
            start + 1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        pendingImages += PendingInlineImage(
            resolvedUrl = resolvePostImageUrl(
                baseUrl = baseUrl,
                rawUrl = image.rawUrl,
            ),
            drawable = drawable,
        )
    }

    return AppliedInlineImages(
        text = builder,
        pendingImages = pendingImages,
    )
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
