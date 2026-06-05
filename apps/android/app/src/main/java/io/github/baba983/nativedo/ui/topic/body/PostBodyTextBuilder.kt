package io.github.baba983.nativedo.ui.topic.body

import io.github.baba983.nativedo.core.ui.PostBodyClickableSpan
import io.github.baba983.nativedo.core.ui.buildPostBodyInlineSpans

internal class PostBodyTextBuilder(
    private val context: PostBodyBuildContext,
) {
    fun build(
        html: String,
        textSizePx: Float = context.paragraphPaint.textSize,
    ): BuiltText {
        val withImages = buildPostBodyInlineSpans(
            html = html,
            baseUrl = context.baseUrl,
            textSizePx = textSizePx,
            accentColor = context.accentColor,
            chipBackgroundArgb = context.chipBackgroundColor,
            onTopicClick = context.delegate::onTopicClick,
            onUserClick = context.delegate::onUserClick,
            onOpenLink = context.delegate::onLinkClick,
        )
        val spannable = withImages.text.asSpannable()
        val links = spannable.getSpans(0, spannable.length, PostBodyClickableSpan::class.java).map { span ->
            LinkRegion(
                start = spannable.getSpanStart(span),
                end = spannable.getSpanEnd(span),
                url = span.resolvedUrl,
                onClick = span.clickAction,
            )
        }
        return BuiltText(
            spannable = spannable,
            pendingImages = withImages.pendingImages,
            links = links,
        )
    }
}
