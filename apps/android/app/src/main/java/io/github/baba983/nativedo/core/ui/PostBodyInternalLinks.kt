package io.github.baba983.nativedo.core.ui

import android.content.res.Resources
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ClickableSpan
import android.text.style.RelativeSizeSpan
import android.text.style.URLSpan
import android.view.View
import androidx.annotation.ColorInt
import java.util.TreeSet
import kotlin.math.roundToInt
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.telegram.tgnet.tl.TL_iv
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
)

internal fun buildPostBodyText(
    html: String,
    baseUrl: String,
    textSizePx: Float = 0f,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    onOpenLink: ((String) -> Unit)? = null,
    @ColorInt accentColor: Int = 0,
    @ColorInt chipBackgroundColor: Int = 0,
): CharSequence = buildPostBodyText(
    cooked = cookInlineHtml(html),
    baseUrl = baseUrl,
    textSizePx = textSizePx,
    onTopicClick = onTopicClick,
    onUserClick = onUserClick,
    onOpenLink = onOpenLink,
    accentColor = accentColor,
    chipBackgroundColor = chipBackgroundColor,
)

/**
 * Telegram's inline model as spans: the walk appends the text and leaves one `TextStyleSpan`
 * per style node, links become our clickable span, and the merged pass folds the overlapping
 * runs and the cookie-cutter sizes into one run per range (the shape `TextStyleRun` describes).
 */
internal fun buildPostBodyText(
    cooked: CookedInline,
    baseUrl: String,
    textSizePx: Float = 0f,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    onOpenLink: ((String) -> Unit)? = null,
    @ColorInt accentColor: Int = 0,
    @ColorInt chipBackgroundColor: Int = 0,
): CharSequence {
    val builder = SpannableStringBuilder()
    appendRichText(
        builder = builder,
        node = cooked.richText,
        baseUrl = baseUrl,
        onTopicClick = onTopicClick,
        onUserClick = onUserClick,
        onOpenLink = onOpenLink,
        accentColor = accentColor,
        chipBackgroundColor = chipBackgroundColor,
    )
    resolveSizeMarkers(builder, cooked.inlineSizes)
    mergeTextStyleRuns(builder, textSizePx)
    return builder
}

private fun appendRichText(
    builder: SpannableStringBuilder,
    node: TL_iv.RichText,
    baseUrl: String,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    onOpenLink: ((String) -> Unit)?,
    @ColorInt accentColor: Int,
    @ColorInt chipBackgroundColor: Int,
) {
    when (node) {
        is TL_iv.textEmpty -> Unit
        is TL_iv.textPlain -> builder.append(node.text)
        is TL_iv.textConcat -> node.texts.forEach { child ->
            appendRichText(builder, child, baseUrl, onTopicClick, onUserClick, onOpenLink, accentColor, chipBackgroundColor)
        }

        is TL_iv.textUrl -> {
            val start = builder.length
            node.text?.let { child ->
                appendRichText(builder, child, baseUrl, onTopicClick, onUserClick, onOpenLink, accentColor, chipBackgroundColor)
            }
            val end = builder.length
            if (end > start) {
                applyLinkSpan(
                    builder = builder,
                    start = start,
                    end = end,
                    rawUrl = node.url.orEmpty(),
                    baseUrl = baseUrl,
                    onTopicClick = onTopicClick,
                    onUserClick = onUserClick,
                    onOpenLink = onOpenLink,
                    accentColor = accentColor,
                    chipBackgroundColor = chipBackgroundColor,
                )
            }
        }

        else -> {
            val start = builder.length
            node.text?.let { child ->
                appendRichText(builder, child, baseUrl, onTopicClick, onUserClick, onOpenLink, accentColor, chipBackgroundColor)
            }
            val end = builder.length
            val flags = styleFlagsOf(node)
            if (flags != 0 && start < end) {
                val run = TextStyleSpan.TextStyleRun().apply {
                    this.flags = flags
                    this.start = start
                    this.end = end
                }
                builder.setSpan(TextStyleSpan(run), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }
}

private fun styleFlagsOf(node: TL_iv.RichText): Int = when (node) {
    is TL_iv.textBold -> TextStyleSpan.FLAG_STYLE_BOLD
    is TL_iv.textItalic -> TextStyleSpan.FLAG_STYLE_ITALIC
    is TL_iv.textUnderline -> TextStyleSpan.FLAG_STYLE_UNDERLINE
    is TL_iv.textStrike -> TextStyleSpan.FLAG_STYLE_STRIKE
    is TL_iv.textFixed -> TextStyleSpan.FLAG_STYLE_MONO
    is TL_iv.textSubscript -> TextStyleSpan.FLAG_STYLE_SUBSCRIPT
    is TL_iv.textSuperscript -> TextStyleSpan.FLAG_STYLE_SUPERSCRIPT
    is TL_iv.textMarked -> TextStyleSpan.FLAG_STYLE_MARKED
    is TL_iv.textSpoiler -> TextStyleSpan.FLAG_STYLE_SPOILER
    else -> 0
}

/** `[size]` spans travel as token pairs (the model has no size case); the pair becomes a relative size span. */
private fun resolveSizeMarkers(
    builder: SpannableStringBuilder,
    sizes: List<InlineSizeSpec>,
) {
    sizes.forEach { size ->
        val currentText = builder.toString()
        val startTokenStart = currentText.indexOf(size.startToken)
        val endTokenStart = currentText.indexOf(size.endToken)
        if (startTokenStart < 0 || endTokenStart < 0 || endTokenStart <= startTokenStart) {
            removeTokenIfPresent(builder, size.startToken)
            removeTokenIfPresent(builder, size.endToken)
            return@forEach
        }

        builder.delete(endTokenStart, endTokenStart + size.endToken.length)
        builder.delete(startTokenStart, startTokenStart + size.startToken.length)
        val start = startTokenStart
        val end = endTokenStart - size.startToken.length
        if (start < end && size.relativeSize != 1f) {
            builder.setSpan(
                RelativeSizeSpan(size.relativeSize),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }
}

/** Folds the walk's spans and the size spans into one run per range, the shape `TextStyleRun` describes. */
private fun mergeTextStyleRuns(
    builder: SpannableStringBuilder,
    textSizePx: Float,
) {
    val ranges = mutableListOf<PendingTextStyleRange>()
    builder.getSpans(0, builder.length, TextStyleSpan::class.java).forEach { span ->
        val start = builder.getSpanStart(span)
        val end = builder.getSpanEnd(span)
        if (start >= 0 && end > start) {
            ranges += PendingTextStyleRange(start = start, end = end, flags = span.styleFlags)
        }
        builder.removeSpan(span)
    }
    builder.getSpans(0, builder.length, RelativeSizeSpan::class.java).forEach { span ->
        val start = builder.getSpanStart(span)
        val end = builder.getSpanEnd(span)
        if (start >= 0 && end > start) {
            ranges += PendingTextStyleRange(start = start, end = end, sizeMultiplier = span.sizeChange)
        }
        builder.removeSpan(span)
    }
    applyMergedTextStyleRanges(builder, ranges, textSizePx)
}

private fun applyLinkSpan(
    builder: SpannableStringBuilder,
    start: Int,
    end: Int,
    rawUrl: String,
    baseUrl: String,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    onOpenLink: ((String) -> Unit)?,
    @ColorInt accentColor: Int,
    @ColorInt chipBackgroundColor: Int,
) {
    val destination = parseInternalLinkDestination(baseUrl = baseUrl, rawUrl = rawUrl)
    val resolvedUrl = baseUrl.toHttpUrlOrNull()?.resolve(rawUrl)?.toString() ?: rawUrl
    val onClick = when (destination) {
        is PostBodyInternalDestination.Topic -> onTopicClick?.let { callback ->
            { callback(destination.topicId) }
        }

        is PostBodyInternalDestination.User -> onUserClick?.let { callback ->
            { callback(destination.username) }
        }

        is PostBodyInternalDestination.Hashtag -> onOpenLink?.let { callback ->
            { callback(resolvedUrl) }
        }

        null -> onOpenLink?.let { callback ->
            { callback(resolvedUrl) }
        }
    }
    val chipStyle = when (destination) {
        is PostBodyInternalDestination.User -> ChipStyle.Mention
        is PostBodyInternalDestination.Hashtag -> ChipStyle.Hashtag
        else -> ChipStyle.None
    }

    if (onClick == null) {
        // No handler for this destination: keep the link as a plain platform span, as before.
        builder.setSpan(URLSpan(resolvedUrl), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return
    }

    val emphasizeBold = chipStyle == ChipStyle.Mention && chipBackgroundColor == 0
    builder.setSpan(
        PostBodyClickableSpan(
            resolvedUrl = resolvedUrl,
            clickAction = onClick,
            emphasizeBold = emphasizeBold,
        ),
        start,
        end,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
    )

    if (chipStyle != ChipStyle.None && chipBackgroundColor != 0 && accentColor != 0) {
        val density = Resources.getSystem().displayMetrics.density
        builder.setSpan(
            ChipBackgroundSpan(
                backgroundColor = chipBackgroundColor,
                textColor = accentColor,
                cornerRadiusPx = 6f * density,
                horizontalPaddingPx = 4f * density,
            ),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
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
            if (start < end) range.copy(start = start, end = end) else null
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
        val textSize = if (textSizePx > 0f && sizeMultiplier != 1f) {
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
