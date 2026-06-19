package io.github.baba983.nativedo.core.ui

import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ClickableSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import android.text.style.URLSpan
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.text.HtmlCompat
import java.util.TreeSet
import kotlin.math.roundToInt
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.telegram.ui.Components.TextStyleSpan

internal sealed interface PostBodyInternalDestination {
    data class Topic(
        val topicId: Long,
        val postNumber: Int?,
    ) : PostBodyInternalDestination

    data class User(
        val username: String,
    ) : PostBodyInternalDestination

    data class Hashtag(
        val slug: String,
        val type: HashtagType,
    ) : PostBodyInternalDestination
}

internal enum class HashtagType {
    Tag, Category, Other,
}

private enum class ChipStyle { None, Mention, Hashtag }

private data class PendingTextStyleRange(
    val start: Int,
    val end: Int,
    val flags: Int = 0,
    val sizeMultiplier: Float = 1f,
    val absoluteSizePx: Float? = null,
)

private class InlineTextStyleMarker(
    val flags: Int,
    val sizeMultiplier: Float,
)

internal fun buildPostBodyText(
    html: String,
    baseUrl: String,
    inlineSpoilers: List<InlineSpoilerSpec> = emptyList(),
    inlineTextStyles: List<InlineTextStyleSpec> = emptyList(),
    textSizePx: Float = 0f,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    onOpenLink: ((String) -> Unit)? = null,
    @ColorInt accentColor: Int = 0,
    @ColorInt chipBackgroundColor: Int = 0,
): CharSequence {
    val spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
    val builder = SpannableStringBuilder(spanned)
    applyTelegramTextStyleSpans(
        builder = builder,
        inlineSpoilers = inlineSpoilers,
        inlineTextStyles = inlineTextStyles,
        textSizePx = textSizePx,
    )
    builder.getSpans(0, builder.length, URLSpan::class.java).forEach { span ->
        val destination = parseInternalLinkDestination(
            baseUrl = baseUrl,
            rawUrl = span.url,
        )

        val onClick = when (destination) {
            is PostBodyInternalDestination.Topic -> onTopicClick?.let { callback ->
                { callback(destination.topicId) }
            }

            is PostBodyInternalDestination.User -> onUserClick?.let { callback ->
                { callback(destination.username) }
            }

            is PostBodyInternalDestination.Hashtag -> onOpenLink?.let { callback ->
                {
                    val resolved = baseUrl.toHttpUrlOrNull()
                        ?.resolve(span.url)?.toString() ?: span.url
                    callback(resolved)
                }
            }

            null -> onOpenLink?.let { callback ->
                {
                    val resolvedUrl = baseUrl.toHttpUrlOrNull()
                        ?.resolve(span.url)?.toString() ?: span.url
                    callback(resolvedUrl)
                }
            }
        } ?: return@forEach

        val resolvedUrl = baseUrl.toHttpUrlOrNull()
            ?.resolve(span.url)?.toString() ?: span.url
        val chipStyle = when (destination) {
            is PostBodyInternalDestination.User -> ChipStyle.Mention
            is PostBodyInternalDestination.Hashtag -> ChipStyle.Hashtag
            else -> ChipStyle.None
        }

        replaceUrlSpan(
            builder = builder,
            span = span,
            resolvedUrl = resolvedUrl,
            onClick = onClick,
            chipStyle = chipStyle,
            accentColor = accentColor,
            chipBackgroundColor = chipBackgroundColor,
        )
    }

    return builder
}

private fun applyTelegramTextStyleSpans(
    builder: SpannableStringBuilder,
    inlineSpoilers: List<InlineSpoilerSpec>,
    inlineTextStyles: List<InlineTextStyleSpec>,
    textSizePx: Float,
) {
    val ranges = mutableListOf<PendingTextStyleRange>()
    placeInlineSpoilerMarkers(builder, inlineSpoilers)
    placeInlineTextStyleMarkers(builder, inlineTextStyles)
    collectInlineTextStyleMarkerRanges(builder, ranges)
    collectPlatformTextStyleRanges(builder, ranges)
    applyMergedTextStyleRanges(builder, ranges, textSizePx)
}

private fun placeInlineTextStyleMarkers(
    builder: SpannableStringBuilder,
    inlineTextStyles: List<InlineTextStyleSpec>,
) {
    inlineTextStyles.forEach { style ->
        val currentText = builder.toString()
        val startTokenStart = currentText.indexOf(style.startToken)
        val endTokenStart = currentText.indexOf(style.endToken)
        if (startTokenStart < 0 || endTokenStart < 0 || endTokenStart <= startTokenStart) {
            removeTokenIfPresent(builder, style.startToken)
            removeTokenIfPresent(builder, style.endToken)
            return@forEach
        }

        builder.delete(endTokenStart, endTokenStart + style.endToken.length)
        builder.delete(startTokenStart, startTokenStart + style.startToken.length)
        val spanStart = startTokenStart
        val spanEnd = endTokenStart - style.startToken.length
        if (spanStart >= spanEnd) {
            return@forEach
        }
        builder.setSpan(
            InlineTextStyleMarker(
                flags = inlineTextStyleFlags(style),
                sizeMultiplier = style.relativeSize ?: 1f,
            ),
            spanStart,
            spanEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
}

private fun placeInlineSpoilerMarkers(
    builder: SpannableStringBuilder,
    inlineSpoilers: List<InlineSpoilerSpec>,
) {
    inlineSpoilers.forEach { spoiler ->
        val currentText = builder.toString()
        val startTokenStart = currentText.indexOf(spoiler.startToken)
        val endTokenStart = currentText.indexOf(spoiler.endToken)
        if (startTokenStart < 0 || endTokenStart < 0 || endTokenStart <= startTokenStart) {
            removeTokenIfPresent(builder, spoiler.startToken)
            removeTokenIfPresent(builder, spoiler.endToken)
            return@forEach
        }

        builder.delete(endTokenStart, endTokenStart + spoiler.endToken.length)
        builder.delete(startTokenStart, startTokenStart + spoiler.startToken.length)
        val spoilerStart = startTokenStart
        val spoilerEnd = endTokenStart - spoiler.startToken.length
        if (spoilerStart >= spoilerEnd) {
            return@forEach
        }
        builder.setSpan(
            InlineTextStyleMarker(
                flags = TextStyleSpan.FLAG_STYLE_SPOILER,
                sizeMultiplier = 1f,
            ),
            spoilerStart,
            spoilerEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
}

private fun collectInlineTextStyleMarkerRanges(
    builder: SpannableStringBuilder,
    ranges: MutableList<PendingTextStyleRange>,
) {
    builder.getSpans(0, builder.length, InlineTextStyleMarker::class.java).forEach { marker ->
        val start = builder.getSpanStart(marker)
        val end = builder.getSpanEnd(marker)
        if (start >= 0 && end > start) {
            ranges += PendingTextStyleRange(
                start = start,
                end = end,
                flags = marker.flags,
                sizeMultiplier = marker.sizeMultiplier,
            )
        }
        builder.removeSpan(marker)
    }
}

private fun inlineTextStyleFlags(style: InlineTextStyleSpec): Int {
    var flags = 0
    if (style.underline) {
        flags = flags or TextStyleSpan.FLAG_STYLE_UNDERLINE
    }
    if (style.strike) {
        flags = flags or TextStyleSpan.FLAG_STYLE_STRIKE
    }
    return flags
}

private fun collectPlatformTextStyleRanges(
    builder: SpannableStringBuilder,
    ranges: MutableList<PendingTextStyleRange>,
) {
    builder.collectAndRemoveSpans(StyleSpan::class.java, ranges) { span, start, end ->
        val flags = when (span.style) {
            Typeface.BOLD -> TextStyleSpan.FLAG_STYLE_BOLD
            Typeface.ITALIC -> TextStyleSpan.FLAG_STYLE_ITALIC
            Typeface.BOLD_ITALIC -> TextStyleSpan.FLAG_STYLE_BOLD or TextStyleSpan.FLAG_STYLE_ITALIC
            else -> 0
        }
        PendingTextStyleRange(start = start, end = end, flags = flags)
    }
    builder.collectAndRemoveSpans(UnderlineSpan::class.java, ranges) { _, start, end ->
        PendingTextStyleRange(start = start, end = end, flags = TextStyleSpan.FLAG_STYLE_UNDERLINE)
    }
    builder.collectAndRemoveSpans(StrikethroughSpan::class.java, ranges) { _, start, end ->
        PendingTextStyleRange(start = start, end = end, flags = TextStyleSpan.FLAG_STYLE_STRIKE)
    }
    builder.collectAndRemoveSpans(TypefaceSpan::class.java, ranges) { span, start, end ->
        if (span.family.equals("monospace", ignoreCase = true)) {
            PendingTextStyleRange(start = start, end = end, flags = TextStyleSpan.FLAG_STYLE_MONO)
        } else {
            null
        }
    }
    builder.collectAndRemoveSpans(RelativeSizeSpan::class.java, ranges) { span, start, end ->
        PendingTextStyleRange(start = start, end = end, sizeMultiplier = span.sizeChange)
    }
    builder.collectAndRemoveSpans(AbsoluteSizeSpan::class.java, ranges) { span, start, end ->
        val sizePx = if (span.dip) {
            span.size * Resources.getSystem().displayMetrics.density
        } else {
            span.size.toFloat()
        }
        PendingTextStyleRange(start = start, end = end, absoluteSizePx = sizePx)
    }
}

private inline fun <T : Any> SpannableStringBuilder.collectAndRemoveSpans(
    klass: Class<T>,
    ranges: MutableList<PendingTextStyleRange>,
    buildRange: (T, Int, Int) -> PendingTextStyleRange?,
) {
    getSpans(0, length, klass).forEach { span ->
        val start = getSpanStart(span)
        val end = getSpanEnd(span)
        if (start >= 0 && end > start) {
            buildRange(span, start, end)?.let(ranges::add)
        }
        removeSpan(span)
    }
}

private fun applyMergedTextStyleRanges(
    builder: SpannableStringBuilder,
    ranges: List<PendingTextStyleRange>,
    textSizePx: Float,
) {
    if (ranges.isEmpty() || builder.isEmpty()) {
        return
    }
    val normalizedRanges = ranges
        .mapNotNull { range ->
            val start = range.start.coerceIn(0, builder.length)
            val end = range.end.coerceIn(0, builder.length)
            if (start < end) {
                range.copy(start = start, end = end)
            } else {
                null
            }
        }
    if (normalizedRanges.isEmpty()) {
        return
    }

    val boundaries = TreeSet<Int>()
    normalizedRanges.forEach { range ->
        boundaries += range.start
        boundaries += range.end
    }
    boundaries.zipWithNext().forEach { (start, end) ->
        if (start >= end) {
            return@forEach
        }
        val activeRanges = normalizedRanges.filter { range ->
            range.start <= start && range.end >= end
        }
        if (activeRanges.isEmpty()) {
            return@forEach
        }

        val flags = activeRanges.fold(0) { acc, range -> acc or range.flags }
        val sizeMultiplier = activeRanges.fold(1f) { acc, range -> acc * range.sizeMultiplier }
        val absoluteSizePx = activeRanges.lastOrNull { range -> range.absoluteSizePx != null }?.absoluteSizePx
        val textSize = absoluteSizePx
            ?.roundToInt()
            ?.coerceAtLeast(1)
            ?: if (textSizePx > 0f && sizeMultiplier != 1f) {
                (textSizePx * sizeMultiplier).roundToInt().coerceAtLeast(1)
            } else {
                0
            }
        if (flags == 0 && textSize == 0) {
            return@forEach
        }
        val run = TextStyleSpan.TextStyleRun().apply {
            this.flags = flags
            this.start = start
            this.end = end
        }
        builder.setSpan(
            TextStyleSpan(run, textSize),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
}

private fun removeTokenIfPresent(
    builder: SpannableStringBuilder,
    token: String,
) {
    val index = builder.toString().indexOf(token)
    if (index >= 0) {
        builder.delete(index, index + token.length)
    }
}

internal fun parseInternalLinkDestination(
    baseUrl: String,
    rawUrl: String,
): PostBodyInternalDestination? {
    val baseHttpUrl = baseUrl.toHttpUrlOrNull() ?: return null
    val resolvedUrl = baseHttpUrl.resolve(rawUrl) ?: rawUrl.toHttpUrlOrNull() ?: return null
    if (!resolvedUrl.host.equals(baseHttpUrl.host, ignoreCase = true)) {
        return null
    }

    val segments = resolvedUrl.pathSegments.filter { segment ->
        segment.isNotBlank()
    }
    if (segments.isEmpty()) {
        return null
    }

    return when (segments.first()) {
        "t" -> if (segments.size >= 2) parseTopicDestination(segments) else null
        "u" -> if (segments.size >= 2) PostBodyInternalDestination.User(username = segments[1]) else null
        "tag", "tags" -> segments.getOrNull(1)?.let {
            PostBodyInternalDestination.Hashtag(slug = it, type = HashtagType.Tag)
        }
        "c" -> segments.getOrNull(1)?.let {
            // /c/<slug>/<id> or /c/<slug>; treat the slug as the chip label.
            PostBodyInternalDestination.Hashtag(slug = it, type = HashtagType.Category)
        }
        else -> null
    }
}

private fun parseTopicDestination(
    segments: List<String>,
): PostBodyInternalDestination.Topic? {
    val directTopicId = segments.getOrNull(1)?.toLongOrNull()
    if (directTopicId != null) {
        return PostBodyInternalDestination.Topic(
            topicId = directTopicId,
            postNumber = segments.getOrNull(2)?.toIntOrNull(),
        )
    }

    val sluggedTopicId = segments.getOrNull(2)?.toLongOrNull() ?: return null
    return PostBodyInternalDestination.Topic(
        topicId = sluggedTopicId,
        postNumber = segments.getOrNull(3)?.toIntOrNull(),
    )
}

internal class PostBodyClickableSpan(
    val resolvedUrl: String,
    val clickAction: () -> Unit,
    private val emphasizeBold: Boolean,
) : ClickableSpan() {
    override fun onClick(widget: View) {
        clickAction()
    }

    override fun updateDrawState(ds: TextPaint) {
        super.updateDrawState(ds)
        ds.isUnderlineText = false
        if (emphasizeBold) {
            ds.typeface = android.graphics.Typeface.create(ds.typeface, android.graphics.Typeface.BOLD)
        }
    }
}

private fun replaceUrlSpan(
    builder: SpannableStringBuilder,
    span: URLSpan,
    resolvedUrl: String,
    onClick: () -> Unit,
    chipStyle: ChipStyle,
    @ColorInt accentColor: Int,
    @ColorInt chipBackgroundColor: Int,
) {
    val start = builder.getSpanStart(span)
    val end = builder.getSpanEnd(span)
    val flags = builder.getSpanFlags(span)
    builder.removeSpan(span)
    val emphasizeBold = chipStyle == ChipStyle.Mention && chipBackgroundColor == 0
    builder.setSpan(
        PostBodyClickableSpan(
            resolvedUrl = resolvedUrl,
            clickAction = onClick,
            emphasizeBold = emphasizeBold,
        ),
        start,
        end,
        flags,
    )

    if (chipStyle != ChipStyle.None && chipBackgroundColor != 0 && accentColor != 0 && start in 0 until end) {
        val density = Resources.getSystem().displayMetrics.density
        val cornerRadiusPx = 6f * density
        val horizontalPaddingPx = 4f * density
        builder.setSpan(
            ChipBackgroundSpan(
                backgroundColor = chipBackgroundColor,
                textColor = accentColor,
                cornerRadiusPx = cornerRadiusPx,
                horizontalPaddingPx = horizontalPaddingPx,
            ),
            start,
            end,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
}

/** Lighten/darken a color via alpha blending so chip backgrounds derive from the accent color. */
@ColorInt
internal fun chipBackgroundFromAccent(@ColorInt accent: Int, alpha: Int = 38): Int {
    return Color.argb(
        alpha,
        Color.red(accent),
        Color.green(accent),
        Color.blue(accent),
    )
}
