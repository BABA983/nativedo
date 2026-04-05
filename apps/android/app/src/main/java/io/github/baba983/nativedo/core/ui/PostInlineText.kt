package io.github.baba983.nativedo.core.ui

import android.graphics.Color.TRANSPARENT
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.StrikethroughSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
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
    inlineCodeBackgroundColor: Int = DEFAULT_INLINE_CODE_BACKGROUND_COLOR,
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
        inlineCodeBackgroundColor = inlineCodeBackgroundColor,
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
    PostInlineText(
        inlines = inlines,
        textStyle = style.toTypographyStyle(),
        cookieStore = cookieStore,
        baseUrl = baseUrl,
        onTopicClick = onTopicClick,
        onUserClick = onUserClick,
        modifier = modifier,
    )
}

@Composable
internal fun PostInlineText(
    inlines: List<PostInline>,
    textStyle: TextStyle,
    cookieStore: CookieStore,
    baseUrl: String,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    textColor: Color? = null,
    linkColor: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = NativeDoThemeExtras.colors
    val resolvedTextColor = (textColor ?: colors.textPrimary).toArgb()
    val resolvedLinkColor = (linkColor ?: colors.accent).toArgb()
    val inlineCodeBackgroundColor = colors.accentMuted.copy(alpha = 0.38f).toArgb()
    val coroutineScope = rememberCoroutineScope()

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                movementMethod = LinkMovementMethod.getInstance()
                setBackgroundColor(TRANSPARENT)
                setTextIsSelectable(false)
                includeFontPadding = false
            }
        },
        update = { textView ->
            (textView.tag as? InlineImageRenderHandle)?.cancel()

            textView.setTextColor(resolvedTextColor)
            textView.setLinkTextColor(resolvedLinkColor)
            textView.setOnClickListener(onClick?.let { callback ->
                View.OnClickListener { callback() }
            })
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
                inlineCodeBackgroundColor = inlineCodeBackgroundColor,
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
    inlineCodeBackgroundColor: Int,
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

            is PostInline.Styled -> builder.appendSpans(
                spans = inline.toSpans(
                    inlineCodeBackgroundColor = inlineCodeBackgroundColor,
                ),
            ) {
                appendPostInlines(
                    builder = builder,
                    inlines = inline.children,
                    baseUrl = baseUrl,
                    onTopicClick = onTopicClick,
                    onUserClick = onUserClick,
                    inlineImages = inlineImages,
                    inlineCodeBackgroundColor = inlineCodeBackgroundColor,
                )
            }

            is PostInline.Link -> {
                val isMentionLink = inline.isMentionLink(baseUrl)
                builder.appendSpan(
                    inline.toLinkSpan(
                        baseUrl = baseUrl,
                        onTopicClick = onTopicClick,
                        onUserClick = onUserClick,
                    ),
                ) {
                    if (isMentionLink) {
                        builder.appendSpans(listOf(StyleSpan(Typeface.BOLD))) {
                            appendPostInlines(
                                builder = builder,
                                inlines = inline.children,
                                baseUrl = baseUrl,
                                onTopicClick = onTopicClick,
                                onUserClick = onUserClick,
                                inlineImages = inlineImages,
                                inlineCodeBackgroundColor = inlineCodeBackgroundColor,
                            )
                        }
                    } else {
                        appendPostInlines(
                            builder = builder,
                            inlines = inline.children,
                            baseUrl = baseUrl,
                            onTopicClick = onTopicClick,
                            onUserClick = onUserClick,
                            inlineImages = inlineImages,
                            inlineCodeBackgroundColor = inlineCodeBackgroundColor,
                        )
                    }
                }
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

private fun SpannableStringBuilder.appendSpans(
    spans: List<Any>,
    block: () -> Unit,
) {
    val start = length
    block()
    val end = length
    if (end > start) {
        spans.forEach { span ->
            setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
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

private fun PostInline.Styled.toSpans(
    inlineCodeBackgroundColor: Int,
): List<Any> {
    return when (style) {
        InlineStyle.Bold -> listOf(StyleSpan(Typeface.BOLD))
        InlineStyle.Italic -> listOf(StyleSpan(Typeface.ITALIC))
        InlineStyle.Strike -> listOf(StrikethroughSpan())
        InlineStyle.Code -> listOf(
            TypefaceSpan("monospace"),
            BackgroundColorSpan(inlineCodeBackgroundColor),
        )
        InlineStyle.LargeText -> listOf(RelativeSizeSpan(1.8f))
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
    val emphasize = destination is PostBodyInternalDestination.User
    return when (destination) {
        is PostBodyInternalDestination.Topic -> {
            if (onTopicClick == null) {
                PostInlineUrlSpan(
                    url = resolveUrl(baseUrl, rawUrl),
                    emphasize = emphasize,
                )
            } else {
                PostInlineClickSpan(
                    emphasize = emphasize,
                ) {
                    onTopicClick(destination.topicId)
                }
            }
        }

        is PostBodyInternalDestination.User -> {
            if (onUserClick == null) {
                PostInlineUrlSpan(
                    url = resolveUrl(baseUrl, rawUrl),
                    emphasize = emphasize,
                )
            } else {
                PostInlineClickSpan(
                    emphasize = emphasize,
                ) {
                    onUserClick(destination.username)
                }
            }
        }

        null -> PostInlineUrlSpan(
            url = resolveUrl(baseUrl, rawUrl),
            emphasize = false,
        )
    }
}

private fun resolveUrl(
    baseUrl: String,
    rawUrl: String,
): String {
    val baseHttpUrl = baseUrl.toHttpUrlOrNull() ?: return rawUrl
    return baseHttpUrl.resolve(rawUrl)?.toString() ?: rawUrl
}

private fun PostInline.Link.isMentionLink(baseUrl: String): Boolean {
    return parseInternalLinkDestination(
        baseUrl = baseUrl,
        rawUrl = rawUrl,
    ) is PostBodyInternalDestination.User
}

private class PostInlineClickSpan(
    private val emphasize: Boolean = false,
    private val onClick: () -> Unit,
) : ClickableSpan() {
    override fun onClick(widget: View) {
        onClick()
    }

    override fun updateDrawState(ds: TextPaint) {
        super.updateDrawState(ds)
        ds.isUnderlineText = false
        if (emphasize) {
            ds.typeface = Typeface.create(ds.typeface, Typeface.BOLD)
            ds.isFakeBoldText = true
        }
    }
}

private class PostInlineUrlSpan(
    url: String,
    private val emphasize: Boolean,
) : URLSpan(url) {
    override fun updateDrawState(ds: TextPaint) {
        super.updateDrawState(ds)
        ds.isUnderlineText = false
        if (emphasize) {
            ds.typeface = Typeface.create(ds.typeface, Typeface.BOLD)
            ds.isFakeBoldText = true
        }
    }
}

@Composable
private fun RichTextBlockStyle.toTypographyStyle(): TextStyle {
    return when (this) {
        RichTextBlockStyle.Paragraph -> postBodyParagraphTextStyle(MaterialTheme.typography.bodyLarge)
        RichTextBlockStyle.Heading1 -> MaterialTheme.typography.headlineMedium
        RichTextBlockStyle.Heading2 -> MaterialTheme.typography.headlineSmall
        RichTextBlockStyle.Heading3 -> MaterialTheme.typography.titleLarge
    }
}

internal fun postBodyParagraphTextStyle(baseStyle: TextStyle): TextStyle =
    baseStyle.copy(
        fontSize = 15.sp,
        lineHeight = 20.sp,
    )

private const val DEFAULT_INLINE_CODE_BACKGROUND_COLOR = 0x14000000
