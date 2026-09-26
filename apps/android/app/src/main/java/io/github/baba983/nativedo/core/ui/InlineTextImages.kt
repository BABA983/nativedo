package io.github.baba983.nativedo.core.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ReplacementSpan
import io.github.baba983.nativedo.core.html.resolvePostImageUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ImageReceiver
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.TextSelectionHelper
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val twemojiInlineImageScale = 0.90f
private const val customEmojiInlineImageScale = 1.0f
private const val siteIconInlineImageScale = 0.92f
private const val inlineSvgIconScale = 0.92f

private val inlineIconSvgDocumentCache = InlineMemoryCache<String>(maxEntries = 128)

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
    inlineIcons: List<InlineIconSpec> = emptyList(),
    inlineHashtagChips: List<InlineHashtagChipSpec> = emptyList(),
    inlineCheckboxes: List<InlineCheckboxSpec> = emptyList(),
    baseUrl: String,
    onOpenLink: ((String) -> Unit)? = null,
    textSizePx: Float,
    accentColor: Int = 0,
    chipBackgroundArgb: Int = 0,
    svgSpriteMarkup: String = "",
): AppliedInlineImages {
    val builder = SpannableStringBuilder(text)
    val pendingImages = mutableListOf<PendingInlineImage>()
    val searchableText = text.toString()
    val placeholders = buildList {
        inlineImages.forEach { image ->
            val originalStart = searchableText.indexOf(image.token)
            if (originalStart != -1) {
                add(InlinePlaceholder.Image(originalStart, image))
            }
        }
        inlineIcons.forEach { icon ->
            val originalStart = searchableText.indexOf(icon.token)
            if (originalStart != -1) {
                add(InlinePlaceholder.Icon(originalStart, icon))
            }
        }
        inlineHashtagChips.forEach { chip ->
            val originalStart = searchableText.indexOf(chip.token)
            if (originalStart != -1) {
                add(InlinePlaceholder.HashtagChip(originalStart, chip))
            }
        }
        inlineCheckboxes.forEach { checkbox ->
            val originalStart = searchableText.indexOf(checkbox.token)
            if (originalStart != -1) {
                add(InlinePlaceholder.Checkbox(originalStart, checkbox))
            }
        }
    }.sortedBy { placeholder -> placeholder.originalStart }
    var replacedCharacterCount = 0

    placeholders.forEach { placeholder ->
        val start = builder.resolvePlaceholderStart(
            token = placeholder.token,
            expectedStart = placeholder.originalStart - replacedCharacterCount,
        )
        if (start < 0 || start >= builder.length) {
            return@forEach
        }
        when (placeholder) {
            is InlinePlaceholder.Image -> {
                val image = placeholder.spec
                val end = start + image.token.length
                if (end > builder.length) {
                    return@forEach
                }
                val drawable = DeferredInlineImageDrawable(
                    widthPx = image.displayWidthPx(textSizePx),
                    heightPx = image.displayHeightPx(textSizePx),
                )
                builder.replace(start, end, "\uFFFC")
                builder.setSpan(
                    CenteredInlineImageSpan(
                        drawable = drawable,
                        kind = image.kind,
                        emojiScale = emojiInlineImageScaleForUrl(image.rawUrl),
                    ),
                    start,
                    start + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                // Upstream's marker for "not part of the copied text"; the span itself extends
                // ReplacementSpan, so it cannot also extend the marker class (the pin's
                // IgnoreCopySpannable is carried as its own span on the same range).
                builder.setSpan(
                    TextSelectionHelper.IgnoreCopySpannable(),
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

            is InlinePlaceholder.Icon -> {
                val icon = placeholder.spec
                val end = start + icon.token.length
                if (end > builder.length) {
                    return@forEach
                }
                val drawable = renderInlineSvgIconDrawable(
                    spriteMarkup = svgSpriteMarkup,
                    symbolId = icon.symbolId,
                    rawTintColor = icon.rawTintColor,
                )
                if (drawable == null) {
                    builder.replace(start, end, icon.contentDescription)
                    replacedCharacterCount += icon.token.length - icon.contentDescription.length
                    return@forEach
                }
                builder.replace(start, end, "\uFFFC")
                val span = BaselineCenteredInlineSvgSpan(
                    drawable = drawable,
                    sizePx = max(1, (textSizePx * inlineSvgIconScale).roundToInt()),
                    tintColor = parseCssColor(icon.rawTintColor),
                )
                builder.setSpan(
                    span,
                    start,
                    start + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                builder.setSpan(
                    TextSelectionHelper.IgnoreCopySpannable(),
                    start,
                    start + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                replacedCharacterCount += icon.token.length - 1
            }

            is InlinePlaceholder.HashtagChip -> {
                val chip = placeholder.spec
                val end = start + chip.token.length
                if (end > builder.length) {
                    return@forEach
                }
                val drawable = renderInlineSvgIconDrawable(
                    spriteMarkup = svgSpriteMarkup,
                    symbolId = chip.symbolId,
                    rawTintColor = chip.rawIconColor.ifBlank { accentColor.toCssHexColor() },
                )
                val resolvedUrl = baseUrl.toHttpUrlOrNull()
                    ?.resolve(chip.href)?.toString() ?: chip.href
                builder.replace(start, end, chip.label)
                val chipEnd = start + chip.label.length
                builder.setSpan(
                    DiscourseTagChipSpan(
                        label = chip.label,
                        icon = drawable,
                        backgroundColor = chipBackgroundArgb,
                        textColor = accentColor,
                        textSizePx = textSizePx,
                    ),
                    start,
                    chipEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                if (onOpenLink != null) {
                    builder.setSpan(
                        PostBodyClickableSpan(
                            resolvedUrl = resolvedUrl,
                            clickAction = { onOpenLink(resolvedUrl) },
                            emphasizeBold = false,
                        ),
                        start,
                        chipEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
                replacedCharacterCount += chip.token.length - chip.label.length
            }

            is InlinePlaceholder.Checkbox -> {
                val checkbox = placeholder.spec
                val end = start + checkbox.token.length
                if (end > builder.length) {
                    return@forEach
                }
                builder.setSpan(
                    DiscourseCheckboxSpan(checked = checkbox.checked),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
    }

    return AppliedInlineImages(
        text = builder,
        pendingImages = pendingImages,
    )
}

private sealed class InlinePlaceholder(
    val originalStart: Int,
    val token: String,
) {
    class Image(originalStart: Int, val spec: InlineImageSpec) : InlinePlaceholder(originalStart, spec.token)
    class Icon(originalStart: Int, val spec: InlineIconSpec) : InlinePlaceholder(originalStart, spec.token)
    class HashtagChip(originalStart: Int, val spec: InlineHashtagChipSpec) : InlinePlaceholder(originalStart, spec.token)
    class Checkbox(originalStart: Int, val spec: InlineCheckboxSpec) : InlinePlaceholder(originalStart, spec.token)
}

private fun SpannableStringBuilder.resolvePlaceholderStart(token: String, expectedStart: Int): Int {
    if (expectedStart >= 0 && expectedStart + token.length <= length) {
        var matches = true
        for (index in token.indices) {
            if (this[expectedStart + index] != token[index]) {
                matches = false
                break
            }
        }
        if (matches) {
            return expectedStart
        }
    }
    val currentText = toString()
    return currentText.indexOf(token, expectedStart.coerceIn(0, currentText.length))
        .takeIf { it >= 0 }
        ?: currentText.indexOf(token)
}

private fun emojiInlineImageScaleForUrl(rawUrl: String): Float {
    return if (rawUrl.contains("/images/emoji/twemoji/")) {
        twemojiInlineImageScale
    } else {
        customEmojiInlineImageScale
    }
}

private fun calculateInlineImageTranslationY(
    y: Int,
    fontMetrics: Paint.FontMetrics,
    drawableHeight: Int,
): Float {
    val centerY = y + (fontMetrics.ascent + fontMetrics.descent) / 2f
    return centerY - drawableHeight / 2f
}

private fun calculateInlineImageSizePx(
    fontMetrics: Paint.FontMetricsInt,
    kind: InlineImageKind,
    emojiScale: Float = customEmojiInlineImageScale,
): Int {
    val fontHeight = max(1, abs(fontMetrics.ascent) + abs(fontMetrics.descent))
    val scale = when (kind) {
        InlineImageKind.Emoji -> emojiScale
        InlineImageKind.SiteIcon -> siteIconInlineImageScale
        InlineImageKind.Icon -> inlineSvgIconScale
    }
    return max(1, (fontHeight * scale).roundToInt())
}

/**
 * A cooked inline checkbox: the site cooks `[x]`/`[ ]` into an `<input type="checkbox">`, and this
 * is the token it becomes (the web UI draws the same element as a checkbox glyph).
 */
internal data class InlineCheckboxSpec(
    val token: String,
    val checked: Boolean,
)

/**
 * Telegram's task-list box as a drawable (`RichMessageLayout.RichBlock.setCheckbox`: a 20dp box with
 * 5dp corners, the accent fill, the `checkboxCheck` glyph, and the disabled-key border when open).
 * It exists so both inline paths can paint the box: the block path through [DiscourseCheckboxSpan]
 * and the rich path through the registry's emoji resolver. The colours are read while drawing, so a
 * theme switch does not leave a stale box behind.
 */
internal class DiscourseCheckboxDrawable(
    private val checked: Boolean,
) : Drawable() {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = AndroidUtilities.dp(1.2f).toFloat()
    }
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = AndroidUtilities.dp(1.9f).toFloat()
    }
    private val checkPath = Path()
    private val cornerRadius = AndroidUtilities.dp(5f).toFloat()

    override fun draw(canvas: Canvas) {
        val side = min(bounds.width(), bounds.height()).toFloat()
        if (side <= 0f) return
        val left = bounds.left + (bounds.width() - side) / 2f
        val top = bounds.top + (bounds.height() - side) / 2f
        val rect = RectF(left, top, left + side, top + side)
        if (checked) {
            fillPaint.color = Theme.getColor(Theme.key_telegram_color)
            checkPaint.color = Theme.getColor(Theme.key_checkboxCheck)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)
            val inset = side * 0.22f
            checkPath.reset()
            checkPath.moveTo(rect.left + inset, rect.centerY())
            checkPath.lineTo(rect.centerX() - side * 0.04f, rect.bottom - inset)
            checkPath.lineTo(rect.right - inset, rect.top + inset)
            canvas.drawPath(checkPath, checkPaint)
        } else {
            borderPaint.color = Theme.getColor(Theme.key_dialogCheckboxSquareDisabled)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
        }
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** The block path's inline checkbox: the drawable above, centred on the line like an inline image. */
internal class DiscourseCheckboxSpan(
    checked: Boolean,
) : ReplacementSpan() {
    private val drawable = DiscourseCheckboxDrawable(checked)

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        val side = max(1, paint.textSize.roundToInt())
        drawable.setBounds(0, 0, side, side)
        return side
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
        val metrics = paint.fontMetrics
        val centerY = y + (metrics.ascent + metrics.descent) / 2f
        val side = drawable.bounds.height()
        canvas.save()
        canvas.translate(x, centerY - side / 2f)
        drawable.draw(canvas)
        canvas.restore()
    }
}

internal fun renderInlineSvgIconDrawable(
    spriteMarkup: String,
    symbolId: String,
    rawTintColor: String,
): Drawable? {
    if (spriteMarkup.isBlank() || symbolId.isBlank()) {
        return null
    }
    val cacheKey = listOf(
        spriteMarkup.hashCode().toString(),
        symbolId,
        rawTintColor,
    ).joinToString(separator = "\n")
    val svgDocument = inlineIconSvgDocumentCache.get(cacheKey)
        ?: buildDiscourseFlairSvgDocument(
            spriteMarkup = spriteMarkup,
            rawFlairUrl = symbolId,
            rawTintColor = rawTintColor,
        )?.also { document ->
            inlineIconSvgDocumentCache.put(cacheKey, document)
        }
        ?: return null
    return buildDiscourseSvgDrawable(svgDocument)
}

private fun parseCssColor(rawColor: String): Int? {
    val trimmed = rawColor.trim()
    if (trimmed.isBlank()) {
        return null
    }
    val normalized = if (trimmed.startsWith("#")) {
        val hex = trimmed.removePrefix("#")
        when (hex.length) {
            3 -> "#" + hex.map { "$it$it" }.joinToString(separator = "")
            4 -> {
                val expanded = hex.map { "$it$it" }
                "#${expanded[3]}${expanded[0]}${expanded[1]}${expanded[2]}"
            }
            8 -> "#${hex.takeLast(2)}${hex.dropLast(2)}"
            else -> trimmed
        }
    } else {
        trimmed
    }
    return runCatching { Color.parseColor(normalized) }.getOrNull()
}

internal fun Int.toCssHexColor(): String {
    return "#${(this and 0xFFFFFF).toString(16).padStart(6, '0')}"
}

private class BaselineCenteredInlineSvgSpan(
    drawable: Drawable,
    sizePx: Int,
    private val tintColor: Int?,
) : ReplacementSpan() {
    private val centeredDrawable = drawable.mutate().apply {
        setBounds(0, 0, sizePx, sizePx)
        tintColor?.let { setColorFilter(android.graphics.PorterDuffColorFilter(it, android.graphics.PorterDuff.Mode.SRC_IN)) }
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        val paintMetrics = paint.fontMetricsInt
        if (fm != null) {
            fm.ascent = paintMetrics.ascent
            fm.descent = paintMetrics.descent
            fm.top = paintMetrics.top
            fm.bottom = paintMetrics.bottom
            fm.leading = paintMetrics.leading
        }
        return centeredDrawable.bounds.width()
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
        val metrics = paint.fontMetrics
        val centerY = y + (metrics.ascent + metrics.descent) / 2f
        canvas.save()
        canvas.translate(
            x,
            centerY - centeredDrawable.bounds.height() / 2f,
        )
        centeredDrawable.draw(canvas)
        canvas.restore()
    }

}

private class CenteredInlineImageSpan(
    drawable: Drawable,
    private val kind: InlineImageKind,
    private val emojiScale: Float = customEmojiInlineImageScale,
) : ReplacementSpan() {
    private val inlineDrawable = drawable

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
            emojiScale = emojiScale,
        )
        inlineDrawable.setBounds(0, 0, targetSizePx, targetSizePx)
        if (fm != null) {
            fm.ascent = paintMetrics.ascent
            fm.descent = paintMetrics.descent
            fm.top = paintMetrics.top
            fm.bottom = paintMetrics.bottom
            fm.leading = paintMetrics.leading
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
        canvas.save()
        canvas.translate(
            x,
            calculateInlineImageTranslationY(
                y = y,
                fontMetrics = paint.fontMetrics,
                drawableHeight = inlineDrawable.bounds.height(),
            ),
        )
        inlineDrawable.draw(canvas)
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
    private var imageReceiver: ImageReceiver? = null

    init {
        setBounds(0, 0, max(1, widthPx), max(1, heightPx))
    }

    fun setImageReceiver(receiver: ImageReceiver?) {
        imageReceiver = receiver
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        imageReceiver?.let { receiver ->
            receiver.setImageCoords(
                bounds.left.toFloat(),
                bounds.top.toFloat(),
                bounds.width().toFloat(),
                bounds.height().toFloat(),
            )
            receiver.draw(canvas)
            return
        }
        canvas.drawRoundRect(
            bounds.left.toFloat(),
            bounds.top.toFloat(),
            bounds.right.toFloat(),
            bounds.bottom.toFloat(),
            bounds.width() * 0.18f,
            bounds.height() * 0.18f,
            placeholderPaint,
        )
    }

    override fun setAlpha(alpha: Int) {
        placeholderPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        placeholderPaint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

private class InlineMemoryCache<T>(
    private val maxEntries: Int,
) {
    private val entries = object : LinkedHashMap<String, T>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, T>): Boolean {
            return size > maxEntries
        }
    }

    @Synchronized
    fun get(key: String): T? = entries[key]

    @Synchronized
    fun put(key: String, value: T) {
        entries[key] = value
    }
}
