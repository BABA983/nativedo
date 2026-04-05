package io.github.baba983.nativedo.core.ui

import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.View
import androidx.core.text.HtmlCompat
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal sealed interface PostBodyInternalDestination {
    data class Topic(
        val topicId: Long,
        val postNumber: Int?,
    ) : PostBodyInternalDestination

    data class User(
        val username: String,
    ) : PostBodyInternalDestination
}

internal fun buildPostBodyText(
    html: String,
    baseUrl: String,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
): CharSequence {
    val spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
    val builder = SpannableStringBuilder(spanned)
    builder.getSpans(0, builder.length, URLSpan::class.java).forEach { span ->
        val destination = parseInternalLinkDestination(
            baseUrl = baseUrl,
            rawUrl = span.url,
        ) ?: return@forEach

        val onClick = when (destination) {
            is PostBodyInternalDestination.Topic -> onTopicClick?.let { callback ->
                { callback(destination.topicId) }
            }

            is PostBodyInternalDestination.User -> onUserClick?.let { callback ->
                { callback(destination.username) }
            }
        } ?: return@forEach

        replaceUrlSpan(
            builder = builder,
            span = span,
            onClick = onClick,
            emphasize = destination is PostBodyInternalDestination.User,
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
    if (segments.size < 2) {
        return null
    }

    return when (segments.first()) {
        "t" -> parseTopicDestination(segments)
        "u" -> PostBodyInternalDestination.User(username = segments[1])
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
    emphasize: Boolean,
) {
    val start = builder.getSpanStart(span)
    val end = builder.getSpanEnd(span)
    val flags = builder.getSpanFlags(span)
    builder.removeSpan(span)
    builder.setSpan(
        object : ClickableSpan() {
            override fun onClick(widget: View) {
                onClick()
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText = false
                if (emphasize) {
                    ds.typeface = android.graphics.Typeface.create(ds.typeface, android.graphics.Typeface.BOLD)
                }
            }
        },
        start,
        end,
        flags,
    )
}
