package io.github.baba983.nativedo.ui.topic.body

import android.text.TextPaint
import android.text.style.DynamicDrawableSpan
import io.github.baba983.nativedo.core.ui.CookedInline
import io.github.baba983.nativedo.core.ui.PostBodyClickableSpan
import io.github.baba983.nativedo.core.ui.buildPostBodyInlineSpans
import org.telegram.messenger.Emoji

internal class PostBodyTextBuilder(
    private val context: PostBodyBuildContext,
) {
    fun build(
        html: String,
        textSizePx: Float = context.paragraphPaint.textSize,
    ): BuiltText {
        val textPaint = TextPaint(context.paragraphPaint).apply {
            textSize = textSizePx
        }
        return build(html, textPaint)
    }

    fun build(
        html: String,
        textPaint: TextPaint,
    ): BuiltText = build(context.cookedInline(html), textPaint)

    fun build(
        cooked: CookedInline,
        textPaint: TextPaint,
    ): BuiltText {
        val withImages = buildPostBodyInlineSpans(
            cooked = cooked,
            baseUrl = context.baseUrl,
            textSizePx = textPaint.textSize,
            accentColor = context.accentColor,
            chipBackgroundArgb = context.chipBackgroundColor,
            onTopicClick = context.delegate::onTopicClick,
            onUserClick = context.delegate::onUserClick,
            onOpenLink = context.delegate::onLinkClick,
            svgSpriteMarkup = context.svgSpriteMarkup,
        )
        val spannable = Emoji.replaceEmoji(
            withImages.text.asSpannable(),
            textPaint.fontMetricsInt,
            false,
            null,
            DynamicDrawableSpan.ALIGN_BASELINE,
        ).asSpannable()
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
