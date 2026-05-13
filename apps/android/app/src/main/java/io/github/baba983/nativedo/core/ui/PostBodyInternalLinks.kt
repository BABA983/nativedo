package io.github.baba983.nativedo.core.ui

import android.content.res.Resources
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.text.HtmlCompat
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

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

internal fun buildPostBodyText(
    html: String,
    baseUrl: String,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    onOpenLink: ((String) -> Unit)? = null,
    @ColorInt accentColor: Int = 0,
    @ColorInt chipBackgroundColor: Int = 0,
): CharSequence {
    val spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
    val builder = SpannableStringBuilder(spanned)
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

        val chipStyle = when (destination) {
            is PostBodyInternalDestination.User -> ChipStyle.Mention
            is PostBodyInternalDestination.Hashtag -> ChipStyle.Hashtag
            else -> ChipStyle.None
        }

        replaceUrlSpan(
            builder = builder,
            span = span,
            onClick = onClick,
            chipStyle = chipStyle,
            accentColor = accentColor,
            chipBackgroundColor = chipBackgroundColor,
        )
    }

    return builder
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

private fun replaceUrlSpan(
    builder: SpannableStringBuilder,
    span: URLSpan,
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
        object : ClickableSpan() {
            override fun onClick(widget: View) {
                onClick()
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText = false
                if (emphasizeBold) {
                    ds.typeface = android.graphics.Typeface.create(ds.typeface, android.graphics.Typeface.BOLD)
                }
            }
        },
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
