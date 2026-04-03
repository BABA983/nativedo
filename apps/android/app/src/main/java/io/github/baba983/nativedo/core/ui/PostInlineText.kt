package io.github.baba983.nativedo.core.ui

import android.graphics.Color.TRANSPARENT
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.viewinterop.AndroidView
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal fun buildPostInlineText(
    inlines: List<PostInline>,
    baseUrl: String,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    textSizePx: Float,
): AppliedInlineImages {
    val builder = SpannableStringBuilder()
    val inlineImages = mutableListOf<InlineImageSpec>()
    appendPostInlines(
        builder = builder,
        inlines = inlines,
        baseUrl = baseUrl,
        onTopicClick = onTopicClick,
        onUserClick = onUserClick,
        inlineImages = inlineImages,
    )
    return applyInlineImagePlaceholders(
        text = builder,
        inlineImages = inlineImages,
        baseUrl = baseUrl,
        textSizePx = textSizePx,
    )
}

@Composable
internal fun PostInlineTextBlock(
    inlines: List<PostInline>,
    style: RichTextBlockStyle,
    cookieStore: CookieStore,
    baseUrl: String,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val textStyle = style.toTypographyStyle()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val coroutineScope = rememberCoroutineScope()

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                movementMethod = LinkMovementMethod.getInstance()
                setBackgroundColor(TRANSPARENT)
                setTextIsSelectable(false)
            }
        },
        update = { textView ->
            (textView.tag as? InlineImageRenderHandle)?.cancel()

            textView.setTextColor(colors.textPrimary.toArgb())
            textView.setLinkTextColor(linkColor)
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textStyle.fontSize.value)
            if (textStyle.lineHeight.value > 0f && textStyle.fontSize.value > 0f) {
                textView.setLineSpacing(0f, textStyle.lineHeight.value / textStyle.fontSize.value)
            }

            val appliedInlineImages = buildPostInlineText(
                inlines = inlines,
                baseUrl = baseUrl,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                textSizePx = textView.textSize,
            )
            textView.text = appliedInlineImages.text

            val handle = InlineImageRenderHandle()
            textView.tag = handle
            appliedInlineImages.pendingImages.forEach { pendingImage ->
                handle.jobs += coroutineScope.launch {
                    val bitmap = fetchDiscourseBitmap(
                        resolvedUrl = pendingImage.resolvedUrl,
                        cookieStore = cookieStore,
                        baseUrl = baseUrl,
                    ) ?: return@launch

                    pendingImage.drawable.setBitmap(bitmap)
                    textView.invalidate()
                }
            }
        },
    )
}

private fun appendPostInlines(
    builder: SpannableStringBuilder,
    inlines: List<PostInline>,
    baseUrl: String,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    inlineImages: MutableList<InlineImageSpec>,
) {
    inlines.forEach { inline ->
        when (inline) {
            is PostInline.Text -> builder.append(inline.text)
            PostInline.LineBreak -> builder.append('\n')
            is PostInline.Emoji -> builder.appendInlineImage(
                inline = inline,
                kind = InlineImageKind.Emoji,
                inlineImages = inlineImages,
            )

            is PostInline.SiteIcon -> builder.appendInlineImage(
                inline = inline,
                kind = InlineImageKind.SiteIcon,
                inlineImages = inlineImages,
            )

            is PostInline.Styled -> builder.appendSpan(inline.toSpan()) {
                appendPostInlines(
                    builder = builder,
                    inlines = inline.children,
                    baseUrl = baseUrl,
                    onTopicClick = onTopicClick,
                    onUserClick = onUserClick,
                    inlineImages = inlineImages,
                )
            }

            is PostInline.Link -> builder.appendSpan(
                inline.toLinkSpan(
                    baseUrl = baseUrl,
                    onTopicClick = onTopicClick,
                    onUserClick = onUserClick,
                ),
            ) {
                appendPostInlines(
                    builder = builder,
                    inlines = inline.children,
                    baseUrl = baseUrl,
                    onTopicClick = onTopicClick,
                    onUserClick = onUserClick,
                    inlineImages = inlineImages,
                )
            }
        }
    }
}

private fun SpannableStringBuilder.appendSpan(
    span: Any,
    block: () -> Unit,
) {
    val start = length
    block()
    val end = length
    if (end > start) {
        setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

private fun SpannableStringBuilder.appendInlineImage(
    inline: PostInline,
    kind: InlineImageKind,
    inlineImages: MutableList<InlineImageSpec>,
) {
    val token = "inline_image_${inlineImages.size}"
    val (rawUrl, fallback) = when (inline) {
        is PostInline.Emoji -> inline.rawUrl to inline.fallback
        is PostInline.SiteIcon -> inline.rawUrl to inline.fallback
        else -> error("Unsupported inline image: $inline")
    }
    append(token)
    inlineImages += InlineImageSpec(
        token = token,
        rawUrl = rawUrl,
        contentDescription = fallback,
        kind = kind,
    )
}

private fun PostInline.Styled.toSpan(): Any {
    return when (style) {
        InlineStyle.Bold -> StyleSpan(Typeface.BOLD)
        InlineStyle.Italic -> StyleSpan(Typeface.ITALIC)
        InlineStyle.Code -> TypefaceSpan("monospace")
        InlineStyle.LargeText -> RelativeSizeSpan(1.8f)
    }
}

private fun PostInline.Link.toLinkSpan(
    baseUrl: String,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
): Any {
    val destination = parseInternalLinkDestination(
        baseUrl = baseUrl,
        rawUrl = rawUrl,
    )
    return when (destination) {
        is PostBodyInternalDestination.Topic -> {
            if (onTopicClick == null) {
                URLSpan(resolveUrl(baseUrl, rawUrl))
            } else {
                PostInlineClickSpan {
                    onTopicClick(destination.topicId)
                }
            }
        }

        is PostBodyInternalDestination.User -> {
            if (onUserClick == null) {
                URLSpan(resolveUrl(baseUrl, rawUrl))
            } else {
                PostInlineClickSpan {
                    onUserClick(destination.username)
                }
            }
        }

        null -> URLSpan(resolveUrl(baseUrl, rawUrl))
    }
}

private fun resolveUrl(
    baseUrl: String,
    rawUrl: String,
): String {
    val baseHttpUrl = baseUrl.toHttpUrlOrNull() ?: return rawUrl
    return baseHttpUrl.resolve(rawUrl)?.toString() ?: rawUrl
}

private class PostInlineClickSpan(
    private val onClick: () -> Unit,
) : ClickableSpan() {
    override fun onClick(widget: View) {
        onClick()
    }

    override fun updateDrawState(ds: TextPaint) {
        super.updateDrawState(ds)
        ds.isUnderlineText = true
    }
}

@Composable
private fun RichTextBlockStyle.toTypographyStyle(): TextStyle {
    return when (this) {
        RichTextBlockStyle.Paragraph -> MaterialTheme.typography.bodyLarge
        RichTextBlockStyle.Heading1 -> MaterialTheme.typography.headlineMedium
        RichTextBlockStyle.Heading2 -> MaterialTheme.typography.headlineSmall
        RichTextBlockStyle.Heading3 -> MaterialTheme.typography.titleLarge
    }
}
