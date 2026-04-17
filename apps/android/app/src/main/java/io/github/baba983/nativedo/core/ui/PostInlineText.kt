package io.github.baba983.nativedo.core.ui

import android.graphics.Canvas
import android.graphics.Color.TRANSPARENT
import android.graphics.Paint
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ReplacementSpan
import android.text.style.BackgroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.StrikethroughSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.UnderlineSpan
import android.text.style.URLSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.math.ceil
import kotlin.math.roundToInt

internal fun buildPostInlineText(
    inlines: List<PostInline>,
    baseUrl: String,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    textSizePx: Float,
    onOpenLink: ((String) -> Unit)? = null,
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
        onOpenLink = onOpenLink,
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
    onOpenLink: ((String) -> Unit)? = null,
) {
    PostInlineText(
        inlines = inlines,
        textStyle = style.toTypographyStyle(),
        cookieStore = cookieStore,
        baseUrl = baseUrl,
        onTopicClick = onTopicClick,
        onUserClick = onUserClick,
        modifier = modifier,
        onOpenLink = onOpenLink,
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
    onOpenLink: ((String) -> Unit)? = null,
) {
    PostSpannableText(
        textStyle = textStyle,
        cookieStore = cookieStore,
        baseUrl = baseUrl,
        modifier = modifier,
        textColor = textColor,
        linkColor = linkColor,
        onClick = onClick,
    ) { textSizePx ->
        buildPostInlineText(
            inlines = inlines,
            baseUrl = baseUrl,
            onTopicClick = onTopicClick,
            onUserClick = onUserClick,
            textSizePx = textSizePx,
            onOpenLink = onOpenLink,
        )
    }
}

@Composable
internal fun PostSpannableText(
    textStyle: TextStyle,
    cookieStore: CookieStore,
    baseUrl: String,
    modifier: Modifier = Modifier,
    textColor: Color? = null,
    linkColor: Color? = null,
    onClick: (() -> Unit)? = null,
    lineSpacingAdd: Dp? = null,
    buildAppliedInlineImages: (textSizePx: Float) -> AppliedInlineImages,
) {
    val colors = NativeDoThemeExtras.colors
    val resolvedTextColor = (textColor ?: colors.textPrimary).toArgb()
    val resolvedLinkColor = (linkColor ?: colors.accent).toArgb()
    val coroutineScope = rememberCoroutineScope()
    val resolvedLineSpacingAddPx = lineSpacingAdd?.let { spacing ->
        with(LocalDensity.current) { spacing.toPx() }
    }

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
            textView.gravity = when (textStyle.textAlign) {
                TextAlign.Center -> Gravity.CENTER_HORIZONTAL
                TextAlign.End,
                TextAlign.Right -> Gravity.END
                else -> Gravity.START
            }
            textView.textAlignment = when (textStyle.textAlign) {
                TextAlign.Center -> View.TEXT_ALIGNMENT_CENTER
                TextAlign.End,
                TextAlign.Right -> View.TEXT_ALIGNMENT_VIEW_END
                else -> View.TEXT_ALIGNMENT_VIEW_START
            }
            if (resolvedLineSpacingAddPx != null) {
                textView.setLineSpacing(
                    resolvedLineSpacingAddPx,
                    1.0f,
                )
            } else if (textStyle.lineHeight.value > 0f && textStyle.fontSize.value > 0f) {
                textView.setLineSpacing(0f, textStyle.lineHeight.value / textStyle.fontSize.value)
            }

            val appliedInlineImages = buildAppliedInlineImages(textView.textSize)
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
    onOpenLink: ((String) -> Unit)? = null,
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
                spans = inline.toSpans(),
            ) {
                appendPostInlines(
                    builder = builder,
                    inlines = inline.children,
                    baseUrl = baseUrl,
                    onTopicClick = onTopicClick,
                    onUserClick = onUserClick,
                    inlineImages = inlineImages,
                    onOpenLink = onOpenLink,
                )
            }

            is PostInline.Link -> {
                val isMentionLink = inline.isMentionLink(baseUrl)
                builder.appendSpans(
                    spans = inline.toLinkSpans(
                        baseUrl = baseUrl,
                        onTopicClick = onTopicClick,
                        onUserClick = onUserClick,
                        onOpenLink = onOpenLink,
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
                                onOpenLink = onOpenLink,
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
                            onOpenLink = onOpenLink,
                        )
                    }
                }
            }
        }
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
): List<Any> {
    return when (style) {
        InlineStyle.Bold -> listOf(StyleSpan(Typeface.BOLD))
        InlineStyle.Italic -> listOf(StyleSpan(Typeface.ITALIC))
        InlineStyle.Strike -> listOf(StrikethroughSpan())
        InlineStyle.Underline -> listOf(UnderlineSpan())
        InlineStyle.Highlight -> listOf(BackgroundColorSpan(HIGHLIGHT_MARK_COLOR))
        InlineStyle.Superscript -> listOf(SuperscriptSpan(), RelativeSizeSpan(0.75f))
        InlineStyle.Subscript -> listOf(SubscriptSpan(), RelativeSizeSpan(0.75f))
        InlineStyle.Small -> listOf(RelativeSizeSpan(0.85f))
        InlineStyle.Kbd -> listOf(InlineCodeSpan())
        InlineStyle.Code -> listOf(InlineCodeSpan())
        InlineStyle.LargeText -> listOf(RelativeSizeSpan(1.8f))
    }
}

private const val HIGHLIGHT_MARK_COLOR = 0x55FFEB3B

private fun PostInline.Link.toLinkSpans(
    baseUrl: String,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    onOpenLink: ((String) -> Unit)? = null,
): List<Any> {
    return buildList {
        add(
            buildPostBodyLinkSpan(
                rawUrl = rawUrl,
                baseUrl = baseUrl,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                onOpenLink = onOpenLink,
            ),
        )
        if (rawUrl.isHashtagLink()) {
            add(HashtagChipSpan())
        }
    }
}

internal fun buildPostBodyLinkSpan(
    rawUrl: String,
    baseUrl: String,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    onOpenLink: ((String) -> Unit)? = null,
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

        null -> {
            if (onOpenLink != null) {
                PostInlineClickSpan(
                    emphasize = false,
                ) {
                    onOpenLink(resolveUrl(baseUrl, rawUrl))
                }
            } else {
                PostInlineUrlSpan(
                    url = resolveUrl(baseUrl, rawUrl),
                    emphasize = false,
                )
            }
        }
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
        ds.applyPostInlineLinkStyle(emphasize = emphasize)
    }
}

private class PostInlineUrlSpan(
    url: String,
    private val emphasize: Boolean,
) : URLSpan(url) {
    override fun updateDrawState(ds: TextPaint) {
        super.updateDrawState(ds)
        ds.applyPostInlineLinkStyle(emphasize = emphasize)
    }
}

private fun TextPaint.applyPostInlineLinkStyle(
    emphasize: Boolean,
) {
    isUnderlineText = false
    if (emphasize) {
        typeface = Typeface.create(typeface, Typeface.BOLD)
        isFakeBoldText = true
    }
}

internal fun String.isHashtagLink(): Boolean {
    return startsWith("/tag/") || contains("://") && contains("/tag/")
}

internal class InlineCodeSpan : ReplacementSpan() {
    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        if (fm != null) {
            val metrics = paint.fontMetricsInt
            fm.ascent = metrics.ascent
            fm.descent = metrics.descent
            fm.top = metrics.top
            fm.bottom = metrics.bottom
        }
        val codePaint = paint.toInlineCodeTextPaint()
        return ceil(
            codePaint.measureText(text, start, end) +
                inlineCodeHorizontalPadding(codePaint) * 2f +
                inlineCodeOuterSpacing(paint) * 2f,
        ).toInt()
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
        val codePaint = paint.toInlineCodeTextPaint()
        val outerSpacing = inlineCodeOuterSpacing(paint)
        val horizontalPadding = inlineCodeHorizontalPadding(codePaint)
        val verticalPadding = inlineCodeVerticalPadding(codePaint)
        val contentWidth = codePaint.measureText(text, start, end)
        val chipLeft = x + outerSpacing
        val chipRight = chipLeft + contentWidth + horizontalPadding * 2f
        val baseline = centeredBaseline(
            containerPaint = paint,
            contentPaint = codePaint,
            baseline = y,
        )
        val rectTop = baseline + codePaint.fontMetrics.ascent - verticalPadding
        val rectBottom = baseline + codePaint.fontMetrics.descent + verticalPadding
        val radius = (rectBottom - rectTop) * 0.22f
        val originalColor = codePaint.color
        val originalStyle = codePaint.style

        codePaint.style = Paint.Style.FILL
        codePaint.color = originalColor.withAlpha(0.10f)
        canvas.drawRoundRect(
            chipLeft,
            rectTop,
            chipRight,
            rectBottom,
            radius,
            radius,
            codePaint,
        )

        codePaint.style = originalStyle
        codePaint.color = originalColor
        canvas.drawText(
            text,
            start,
            end,
            chipLeft + horizontalPadding,
            baseline,
            codePaint,
        )
    }
}

private fun TextPaint.applyInlineCodeStyle() {
    typeface = Typeface.MONOSPACE
    textSize *= INLINE_CODE_TEXT_SIZE_RATIO
    isUnderlineText = false
    flags = flags or Paint.SUBPIXEL_TEXT_FLAG
}

internal class HashtagChipSpan : ReplacementSpan() {
    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        val metrics = paint.fontMetricsInt
        val verticalPadding = hashtagVerticalPadding(paint)
        if (fm != null) {
            fm.ascent = (metrics.ascent - verticalPadding).roundToInt()
            fm.descent = (metrics.descent + verticalPadding).roundToInt()
            fm.top = fm.ascent
            fm.bottom = fm.descent
        }
        return ceil(
            paint.measureText(text, start, end) +
                hashtagHorizontalPadding(paint) * 2f +
                hashtagOuterSpacing(paint) * 2f,
        ).toInt()
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
        val outerSpacing = hashtagOuterSpacing(paint)
        val horizontalPadding = hashtagHorizontalPadding(paint)
        val verticalPadding = hashtagVerticalPadding(paint)
        val contentWidth = paint.measureText(text, start, end)
        val chipLeft = x + outerSpacing
        val chipRight = chipLeft + contentWidth + horizontalPadding * 2f
        val rectTop = y + paint.fontMetrics.ascent - verticalPadding
        val rectBottom = y + paint.fontMetrics.descent + verticalPadding
        val radius = (rectBottom - rectTop) / 2f
        val originalColor = paint.color
        val originalStyle = paint.style

        paint.style = Paint.Style.FILL
        paint.color = originalColor.withAlpha(0.16f)
        canvas.drawRoundRect(
            chipLeft,
            rectTop,
            chipRight,
            rectBottom,
            radius,
            radius,
            paint,
        )

        paint.style = originalStyle
        paint.color = originalColor
        canvas.drawText(
            text,
            start,
            end,
            chipLeft + horizontalPadding,
            y.toFloat(),
            paint,
        )
    }
}

private fun Int.withAlpha(alpha: Float): Int {
    val safeAlpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
    return (safeAlpha shl 24) or (this and 0x00FFFFFF)
}

private fun Paint.toInlineCodeTextPaint(): TextPaint = TextPaint(this).apply {
    applyInlineCodeStyle()
    color = this@toInlineCodeTextPaint.color
}

private fun centeredBaseline(
    containerPaint: Paint,
    contentPaint: Paint,
    baseline: Int,
): Float {
    val containerCenter =
        baseline + (containerPaint.fontMetrics.ascent + containerPaint.fontMetrics.descent) / 2f
    return containerCenter - (contentPaint.fontMetrics.ascent + contentPaint.fontMetrics.descent) / 2f
}

private fun hashtagOuterSpacing(paint: Paint): Float = paint.textSize * 0.16f

private fun hashtagHorizontalPadding(paint: Paint): Float = paint.textSize * 0.38f

private fun hashtagVerticalPadding(paint: Paint): Float = paint.textSize * 0.10f

private fun inlineCodeOuterSpacing(paint: Paint): Float = paint.textSize * 0.08f

private fun inlineCodeHorizontalPadding(paint: Paint): Float = paint.textSize * 0.12f

private fun inlineCodeVerticalPadding(paint: Paint): Float = paint.textSize * 0.035f

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
        lineHeight = 19.sp,
    )

private const val INLINE_CODE_TEXT_SIZE_RATIO = 0.94f
