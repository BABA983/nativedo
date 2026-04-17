package io.github.baba983.nativedo.core.ui

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.UnderlineSpan
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import io.github.baba983.nativedo.core.storage.CookieStore
import kotlin.math.roundToInt

@Composable
internal fun PostTextDocumentView(
    document: TextDocument,
    textStyle: TextStyle,
    cookieStore: CookieStore,
    baseUrl: String,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    textColor: Color? = null,
    linkColor: Color? = null,
    onClick: (() -> Unit)? = null,
    onOpenLink: ((String) -> Unit)? = null,
    lineSpacingAdd: Dp? = null,
) {
    PostSpannableText(
        textStyle = textStyle,
        cookieStore = cookieStore,
        baseUrl = baseUrl,
        modifier = modifier,
        textColor = textColor,
        linkColor = linkColor,
        onClick = onClick,
        lineSpacingAdd = lineSpacingAdd,
    ) { textSizePx ->
        buildTextDocumentSpannable(
            document = document,
            baseUrl = baseUrl,
            onTopicClick = onTopicClick,
            onUserClick = onUserClick,
            textSizePx = textSizePx,
            onOpenLink = onOpenLink,
        )
    }
}

internal fun buildTextDocumentSpannable(
    document: TextDocument,
    baseUrl: String,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    textSizePx: Float,
    onOpenLink: ((String) -> Unit)? = null,
): AppliedInlineImages {
    val builder = SpannableStringBuilder(document.text)
    val pendingImages = mutableListOf<PendingInlineImage>()
    val sortedEntities = document.entities
        .sortedWith(compareByDescending<TextEntity> { it.start }.thenByDescending { it.end })

    sortedEntities.forEach { entity ->
        when (entity) {
            is TextEntity.Emoji,
            is TextEntity.SiteIcon,
            is TextEntity.Spoiler -> Unit

            is TextEntity.Bold -> builder.setEntitySpan(
                span = StyleSpan(Typeface.BOLD),
                start = entity.start,
                end = entity.end,
            )

            is TextEntity.Italic -> builder.setEntitySpan(
                span = StyleSpan(Typeface.ITALIC),
                start = entity.start,
                end = entity.end,
            )

            is TextEntity.Strike -> builder.setEntitySpan(
                span = StrikethroughSpan(),
                start = entity.start,
                end = entity.end,
            )

            is TextEntity.Underline -> builder.setEntitySpan(
                span = UnderlineSpan(),
                start = entity.start,
                end = entity.end,
            )

            is TextEntity.Highlight -> builder.setEntitySpan(
                span = BackgroundColorSpan(HIGHLIGHT_MARK_COLOR),
                start = entity.start,
                end = entity.end,
            )

            is TextEntity.Superscript -> {
                builder.setEntitySpan(
                    span = SuperscriptSpan(),
                    start = entity.start,
                    end = entity.end,
                )
                builder.setEntitySpan(
                    span = RelativeSizeSpan(0.75f),
                    start = entity.start,
                    end = entity.end,
                )
            }

            is TextEntity.Subscript -> {
                builder.setEntitySpan(
                    span = SubscriptSpan(),
                    start = entity.start,
                    end = entity.end,
                )
                builder.setEntitySpan(
                    span = RelativeSizeSpan(0.75f),
                    start = entity.start,
                    end = entity.end,
                )
            }

            is TextEntity.Small -> builder.setEntitySpan(
                span = RelativeSizeSpan(0.85f),
                start = entity.start,
                end = entity.end,
            )

            is TextEntity.Kbd -> builder.setEntitySpan(
                span = InlineCodeSpan(),
                start = entity.start,
                end = entity.end,
            )

            is TextEntity.Link -> {
                builder.setEntitySpan(
                    span = buildPostBodyLinkSpan(
                        rawUrl = entity.rawUrl,
                        baseUrl = baseUrl,
                        onTopicClick = onTopicClick,
                        onUserClick = onUserClick,
                        onOpenLink = onOpenLink,
                    ),
                    start = entity.start,
                    end = entity.end,
                )
                if (entity.rawUrl.isHashtagLink()) {
                    builder.setEntitySpan(
                        span = HashtagChipSpan(),
                        start = entity.start,
                        end = entity.end,
                    )
                }
            }
            is TextEntity.Mention -> builder.setEntitySpan(
                span = buildPostBodyLinkSpan(
                    rawUrl = "/u/${entity.username}",
                    baseUrl = baseUrl,
                    onTopicClick = onTopicClick,
                    onUserClick = onUserClick,
                    onOpenLink = onOpenLink,
                ),
                start = entity.start,
                end = entity.end,
            )

            is TextEntity.InlineCode -> {
                builder.setEntitySpan(
                    span = InlineCodeSpan(),
                    start = entity.start,
                    end = entity.end,
                )
            }
        }
    }

    sortedEntities.forEach { entity ->
        when (entity) {
            is TextEntity.Bold,
            is TextEntity.Italic,
            is TextEntity.Strike,
            is TextEntity.Underline,
            is TextEntity.Highlight,
            is TextEntity.Superscript,
            is TextEntity.Subscript,
            is TextEntity.Small,
            is TextEntity.Kbd,
            is TextEntity.Link,
            is TextEntity.Mention,
            is TextEntity.InlineCode -> Unit

            is TextEntity.Emoji -> pendingImages += builder.replaceRangeWithInlineImage(
                start = entity.start,
                end = entity.end,
                rawUrl = entity.rawUrl,
                kind = InlineImageKind.Emoji,
                baseUrl = baseUrl,
                textSizePx = textSizePx,
            )

            is TextEntity.SiteIcon -> pendingImages += builder.replaceRangeWithInlineImage(
                start = entity.start,
                end = entity.end,
                rawUrl = entity.rawUrl,
                kind = InlineImageKind.SiteIcon,
                baseUrl = baseUrl,
                textSizePx = textSizePx,
            )

            is TextEntity.Spoiler -> Unit
        }
    }

    return AppliedInlineImages(
        text = builder,
        pendingImages = pendingImages,
    )
}

private fun SpannableStringBuilder.setEntitySpan(
    span: Any,
    start: Int,
    end: Int,
) {
    if (end <= start) {
        return
    }
    setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
}

private fun SpannableStringBuilder.replaceRangeWithInlineImage(
    start: Int,
    end: Int,
    rawUrl: String,
    kind: InlineImageKind,
    baseUrl: String,
    textSizePx: Float,
): PendingInlineImage {
    val placeholderSizePx = when (kind) {
        InlineImageKind.Emoji -> (textSizePx * 1.2f).roundToInt()
        InlineImageKind.SiteIcon -> textSizePx.roundToInt()
    }.coerceAtLeast(1)
    val drawable = DeferredInlineImageDrawable(
        widthPx = placeholderSizePx,
        heightPx = placeholderSizePx,
    )

    replace(start, end, "\uFFFC")
    setSpan(
        CenteredInlineImageSpan(
            drawable = drawable,
            kind = kind,
            emojiScale = emojiInlineImageScaleForUrl(rawUrl),
        ),
        start,
        start + 1,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
    )

    return PendingInlineImage(
        resolvedUrl = resolvePostImageUrl(
            baseUrl = baseUrl,
            rawUrl = rawUrl,
        ),
        drawable = drawable,
    )
}

private const val HIGHLIGHT_MARK_COLOR = 0x55FFEB3B
