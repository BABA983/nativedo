// PORT-MODE: Exact Component Port
// UPSTREAM-SCOPE: org/telegram/ui/ArticleViewer.java
//   - getTextPaint (block typography sizes)
//   - BlockBlockquoteCell / quoteLinePaint (left-bar quote rendering)
//   - BlockListItemCell / BlockOrderedListItemCell (hanging-indent list layout)
//   - BlockPreformattedCell (horizontally-scrolling code block)
// UPSTREAM-COMMIT: master@2026-05-08
// EXACT-SUBSYSTEMS: typography, quote left bar, list bullet column, code scroll
// HOST-WIRING:
//   - Telegram custom-drawn TextLayout cells -> Compose Text + AndroidView(TextView).
//   - Telegram TLRPC PageBlock model -> Jsoup-parsed CookedPostBlock.
//   - Theme.key_chat_inReplyLine (Telegram) -> Theme.key_windowBackgroundWhiteBlueText
//     (NativeDo's link/accent blue; closest available equivalent).
// ADAPTED-BACKEND:
//   - Discourse "cooked" HTML is parsed locally with Jsoup; Telegram instant view
//     consumes a typed RichText tree from the server.
// ADAPTED-PRODUCT:
//   - Headings render with system bold (no serif font bundle yet).
//   - Quote block keeps a faint background tint for legibility on white cards
//     (Telegram instant view uses a flat background; ADAPTED-PRODUCT to preserve
//     contrast inside our PostSummaryCard which is already on a white surface).
//   - Discourse poll, onebox, spoiler, table, and collage blocks render as local
//     lightweight cards instead of Telegram's typed cached-page cells.
// OMITTED:
//   - Pull-quote, kicker, footer, related-articles, embed-post cells, exact
//     BlockTableCell span measurement, exact BlockCollageCell row packing, and
//     authenticated poll voting.
//   - Text selection helper (TextSelectionHelper) - selection currently relies on
//     the embedded TextView default selection handles.
package io.github.baba983.nativedo.core.ui

import android.graphics.RectF
import android.view.View
import android.widget.ImageView
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.rememberAsyncImagePainter
import coil3.load
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.ui.actionbar.Theme
import io.github.baba983.nativedo.ui.actionbar.rememberThemeRevision
import io.github.baba983.nativedo.ui.actionbar.themedColor
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

@Composable
fun HtmlPostBodyView(
    html: String,
    cookieStore: CookieStore,
    baseUrl: String,
    svgSpriteMarkup: String = "",
    onTopicClick: ((Long) -> Unit)? = null,
    onUserClick: ((String) -> Unit)? = null,
    onOpenLink: ((String) -> Unit)? = null,
    hiddenImageUrl: String? = null,
    onImageBoundsChanged: ((String, RectF?) -> Unit)? = null,
    onImageViewChanged: ((String, View?) -> Unit)? = null,
    onOpenImage: ((String, List<GalleryImage>, RectF?, android.graphics.Bitmap?, View?) -> Unit)? = null,
    onExpandQuote: (suspend (Long, Int) -> Result<String>)? = null,
    currentTopicId: Long? = null,
    localPostsByPostNumber: Map<Int, String> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    rememberThemeRevision()
    val blocks = remember(html, baseUrl) { parseCookedPostBody(html, baseUrl) }
    val bodyColor = themedColor(Theme.key_windowBackgroundWhiteBlackText)
    val secondaryColor = themedColor(Theme.key_windowBackgroundWhiteGrayText)
    val linkColor = themedColor(Theme.key_windowBackgroundWhiteBlueText)
    val dividerColor = themedColor(Theme.key_divider)
    val quoteBackground = themedColor(Theme.key_windowBackgroundGray)
    val codeBackground = themedColor(Theme.key_iv_backgroundGray)
    val cardBackground = themedColor(Theme.key_windowBackgroundWhite)
    val chipBackgroundArgb = remember(linkColor) {
        chipBackgroundFromAccent(linkColor.toArgb())
    }
    val galleryImages = remember(blocks, baseUrl) {
        cookedGalleryImages(blocks = blocks, baseUrl = baseUrl)
    }

    Column(modifier = modifier) {
        blocks.forEachIndexed { index, block ->
            val previous = blocks.getOrNull(index - 1)
            val topSpacing = topSpacingFor(previous = previous, current = block)
            if (topSpacing > 0.dp) {
                Box(modifier = Modifier.padding(top = topSpacing))
            }
            when (block) {
                is CookedPostBlock.Text -> CookedTextBlock(
                    html = block.html,
                    cookieStore = cookieStore,
                    baseUrl = baseUrl,
                    textColor = bodyColor,
                    linkColor = linkColor,
                    textSize = postBodyParagraphTextSize,
                    chipBackgroundArgb = chipBackgroundArgb,
                    onTopicClick = onTopicClick,
                    onUserClick = onUserClick,
                    onOpenLink = onOpenLink,
                )

                is CookedPostBlock.Heading -> CookedHeadingBlock(
                    block = block,
                    cookieStore = cookieStore,
                    baseUrl = baseUrl,
                    textColor = bodyColor,
                    linkColor = linkColor,
                    chipBackgroundArgb = chipBackgroundArgb,
                    onTopicClick = onTopicClick,
                    onUserClick = onUserClick,
                    onOpenLink = onOpenLink,
                )

                is CookedPostBlock.Quote -> CookedQuoteBlock(
                    block = block,
                    cookieStore = cookieStore,
                    baseUrl = baseUrl,
                    textColor = bodyColor,
                    secondaryColor = secondaryColor,
                    linkColor = linkColor,
                    backgroundColor = quoteBackground,
                    chipBackgroundArgb = chipBackgroundArgb,
                    onTopicClick = onTopicClick,
                    onUserClick = onUserClick,
                    onOpenLink = onOpenLink,
                )

                is CookedPostBlock.Code -> CookedCodeBlock(
                    code = block.code,
                    textColor = bodyColor,
                    backgroundColor = codeBackground,
                )

                is CookedPostBlock.ListItems -> CookedListBlock(
                    block = block,
                    cookieStore = cookieStore,
                    baseUrl = baseUrl,
                    textColor = bodyColor,
                    secondaryColor = secondaryColor,
                    linkColor = linkColor,
                    chipBackgroundArgb = chipBackgroundArgb,
                    onTopicClick = onTopicClick,
                    onUserClick = onUserClick,
                    onOpenLink = onOpenLink,
                )

                is CookedPostBlock.Image -> CookedImageBlock(
                    block = block,
                    baseUrl = baseUrl,
                    secondaryColor = secondaryColor,
                    dividerColor = dividerColor,
                    onOpenLink = onOpenLink,
                    hiddenImageUrl = hiddenImageUrl,
                    onImageBoundsChanged = onImageBoundsChanged,
                    onImageViewChanged = onImageViewChanged,
                    onOpenImage = onOpenImage,
                    galleryImages = galleryImages,
                )

                is CookedPostBlock.Collage -> CookedCollageBlock(
                    block = block,
                    baseUrl = baseUrl,
                    dividerColor = dividerColor,
                    onOpenLink = onOpenLink,
                    hiddenImageUrl = hiddenImageUrl,
                    onImageBoundsChanged = onImageBoundsChanged,
                    onImageViewChanged = onImageViewChanged,
                    onOpenImage = onOpenImage,
                    galleryImages = galleryImages,
                )

                is CookedPostBlock.Poll -> CookedPollBlock(
                    block = block,
                    textColor = bodyColor,
                    secondaryColor = secondaryColor,
                    accentColor = linkColor,
                    cardBackground = codeBackground,
                    dividerColor = dividerColor,
                )

                is CookedPostBlock.Spoiler -> CookedSpoilerBlock(
                    block = block,
                    cookieStore = cookieStore,
                    baseUrl = baseUrl,
                    textColor = bodyColor,
                    secondaryColor = secondaryColor,
                    linkColor = linkColor,
                    cardBackground = codeBackground,
                    chipBackgroundArgb = chipBackgroundArgb,
                    onTopicClick = onTopicClick,
                    onUserClick = onUserClick,
                    onOpenLink = onOpenLink,
                )

                is CookedPostBlock.Onebox -> CookedOneboxBlock(
                    block = block,
                    baseUrl = baseUrl,
                    textColor = bodyColor,
                    secondaryColor = secondaryColor,
                    cardBackground = codeBackground,
                    dividerColor = dividerColor,
                    onOpenLink = onOpenLink,
                )

                is CookedPostBlock.Table -> CookedTableBlock(
                    block = block,
                    textColor = bodyColor,
                    secondaryColor = secondaryColor,
                    cardBackground = codeBackground,
                    dividerColor = dividerColor,
                )

                CookedPostBlock.Rule -> CookedRuleBlock(dividerColor = dividerColor)
            }
        }
    }

    @Suppress("UNUSED_EXPRESSION")
    svgSpriteMarkup to onExpandQuote to currentTopicId to localPostsByPostNumber
}

private fun topSpacingFor(previous: CookedPostBlock?, current: CookedPostBlock): androidx.compose.ui.unit.Dp {
    if (previous == null) return 0.dp
    return when (current) {
        is CookedPostBlock.Heading -> when (current.level) {
            1, 2 -> postBodyHeadingTopSpacing
            else -> postBodyMinorHeadingTopSpacing
        }
        CookedPostBlock.Rule -> postBodyRuleVerticalSpacing
        else -> when (previous) {
            is CookedPostBlock.Heading -> postBodyHeadingBottomSpacing
            CookedPostBlock.Rule -> postBodyRuleVerticalSpacing
            else -> postBodySpacingBetweenBlocks
        }
    }
}

@Composable
private fun CookedTextBlock(
    html: String,
    cookieStore: CookieStore,
    baseUrl: String,
    textColor: Color,
    linkColor: Color,
    textSize: TextUnit,
    chipBackgroundArgb: Int,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    onOpenLink: ((String) -> Unit)?,
) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = {
            TextView(context).apply {
                movementMethod = LinkMovementMethod.getInstance()
                linksClickable = true
                setTextColor(textColor.toArgb())
                setLinkTextColor(linkColor.toArgb())
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, textSize.value)
                includeFontPadding = false
                // Telegram instant view uses ~1.15 line spacing for paragraphs.
                setLineSpacing(0f, 1.15f)
            }
        },
        update = { textView ->
            (textView.tag as? InlineImageRenderHandle)?.cancel()
            textView.setTextColor(textColor.toArgb())
            textView.setLinkTextColor(linkColor.toArgb())
            textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, textSize.value)
            textView.text = buildInlinePostBodyText(
                html = html,
                baseUrl = baseUrl,
                textSizePx = textView.textSize,
                accentColor = linkColor.toArgb(),
                chipBackgroundArgb = chipBackgroundArgb,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                onOpenLink = onOpenLink,
                cookieStore = cookieStore,
                textView = textView,
            )
        },
    )
}

@Composable
private fun CookedHeadingBlock(
    block: CookedPostBlock.Heading,
    cookieStore: CookieStore,
    baseUrl: String,
    textColor: Color,
    linkColor: Color,
    chipBackgroundArgb: Int,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    onOpenLink: ((String) -> Unit)?,
) {
    val context = LocalContext.current
    val sizeSp = headingSizeFor(block.level)
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = {
            TextView(context).apply {
                movementMethod = LinkMovementMethod.getInstance()
                linksClickable = true
                includeFontPadding = false
                setLineSpacing(0f, 1.15f)
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                setTextColor(textColor.toArgb())
                setLinkTextColor(linkColor.toArgb())
            }
        },
        update = { textView ->
            (textView.tag as? InlineImageRenderHandle)?.cancel()
            textView.setTextColor(textColor.toArgb())
            textView.setLinkTextColor(linkColor.toArgb())
            textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp.value)
            textView.text = buildInlinePostBodyText(
                html = block.html,
                baseUrl = baseUrl,
                textSizePx = textView.textSize,
                accentColor = linkColor.toArgb(),
                chipBackgroundArgb = chipBackgroundArgb,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                onOpenLink = onOpenLink,
                cookieStore = cookieStore,
                textView = textView,
            )
        },
    )
}

private fun buildInlinePostBodyText(
    html: String,
    baseUrl: String,
    textSizePx: Float,
    accentColor: Int,
    chipBackgroundArgb: Int,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    onOpenLink: ((String) -> Unit)?,
    cookieStore: CookieStore,
    textView: TextView,
): CharSequence {
    val inlineContent = prepareInlineContent(html)
    val appliedInlineImages = applyInlineImagePlaceholders(
        text = buildPostBodyText(
            html = inlineContent.html,
            baseUrl = baseUrl,
            onTopicClick = onTopicClick,
            onUserClick = onUserClick,
            onOpenLink = onOpenLink,
            accentColor = accentColor,
            chipBackgroundColor = chipBackgroundArgb,
        ),
        inlineImages = inlineContent.inlineImages,
        baseUrl = baseUrl,
        textSizePx = textSizePx,
    )
    val handle = InlineImageRenderHandle()
    textView.tag = handle
    appliedInlineImages.pendingImages.forEach { pendingImage ->
        handle.load(
            pendingImage = pendingImage,
            cookieStore = cookieStore,
            baseUrl = baseUrl,
        ) {
            textView.invalidate()
        }
    }
    return appliedInlineImages.text
}

private fun headingSizeFor(level: Int): TextUnit = when (level) {
    1 -> postBodyHeading1TextSize
    2 -> postBodyHeading2TextSize
    3 -> postBodyHeading3TextSize
    else -> postBodyHeadingMinorTextSize
}

@Composable
private fun CookedQuoteBlock(
    block: CookedPostBlock.Quote,
    cookieStore: CookieStore,
    baseUrl: String,
    textColor: Color,
    secondaryColor: Color,
    linkColor: Color,
    backgroundColor: Color,
    chipBackgroundArgb: Int,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    onOpenLink: ((String) -> Unit)?,
) {
    // Telegram instant view BlockBlockquoteCell draws a 2dp accent bar at dp(18..20)
    // with content offset to the right of the bar. We mirror that with a Row whose
    // first child is the bar (ADAPTED-PRODUCT: keep faint background tint for
    // legibility on already-white cards).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(postBodyEmbeddedBlockCornerRadius))
            .background(backgroundColor)
            .padding(vertical = 10.dp, horizontal = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .width(postBodyQuoteBarWidth)
                .background(linkColor),
        ) {
            // Spacer column inside the bar; height matches Row intrinsic height.
            Box(modifier = Modifier.heightIn(min = 24.dp))
        }
        Column(
            modifier = Modifier
                .padding(start = postBodyQuoteContentInset)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            block.title?.takeIf { it.isNotBlank() }?.let { title ->
                Text(
                    text = title,
                    color = secondaryColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = postBodyQuoteCaptionTextSize,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = postBodyQuoteCaptionTextSize),
                )
            }
            CookedTextBlock(
                html = block.html,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                textColor = textColor,
                linkColor = linkColor,
                textSize = postBodyQuoteTextSize,
                chipBackgroundArgb = chipBackgroundArgb,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                onOpenLink = onOpenLink,
            )
        }
    }
}

@Composable
private fun CookedCodeBlock(code: String, textColor: Color, backgroundColor: Color) {
    // Telegram BlockPreformattedCell uses a horizontal scroll so long code lines are
    // not wrapped. Mirror that with foundation.horizontalScroll on the inner Text.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(postBodyEmbeddedBlockCornerRadius))
            .background(backgroundColor),
    ) {
        Box(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(postBodyCodeBlockPadding),
        ) {
            Text(
                text = code,
                style = TextStyle(
                    color = textColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = postBodyCodeTextSize,
                    lineHeight = (postBodyCodeTextSize.value * 1.35f).sp,
                ),
                softWrap = false,
            )
        }
    }
}

@Composable
private fun CookedListBlock(
    block: CookedPostBlock.ListItems,
    cookieStore: CookieStore,
    baseUrl: String,
    textColor: Color,
    secondaryColor: Color,
    linkColor: Color,
    chipBackgroundArgb: Int,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    onOpenLink: ((String) -> Unit)?,
) {
    // Telegram BlockListItemCell renders bullet glyphs in a fixed-width column with
    // hanging indent so wrapped lines align under the first character of the item.
    // Bullet sizes mirror listTextPointerPaint (dp 19) and listTextNumPaint (dp 16).
    Column(verticalArrangement = Arrangement.spacedBy(postBodyListItemSpacing)) {
        block.items.forEachIndexed { index, itemHtml ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.width(postBodyListBulletColumnWidth),
                    contentAlignment = Alignment.TopEnd,
                ) {
                    if (block.ordered) {
                        Text(
                            text = "${index + 1}.",
                            color = secondaryColor,
                            fontSize = postBodyListNumberTextSize,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    } else {
                        Text(
                            text = "\u2022",
                            color = secondaryColor,
                            fontSize = postBodyListBulletTextSize,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    CookedTextBlock(
                        html = itemHtml,
                        cookieStore = cookieStore,
                        baseUrl = baseUrl,
                        textColor = textColor,
                        linkColor = linkColor,
                        textSize = postBodyListItemTextSize,
                        chipBackgroundArgb = chipBackgroundArgb,
                        onTopicClick = onTopicClick,
                        onUserClick = onUserClick,
                        onOpenLink = onOpenLink,
                    )
                }
            }
        }
    }
}

@Composable
private fun CookedRuleBlock(dividerColor: Color) {
    HorizontalDivider(color = dividerColor, thickness = postBodyRuleThickness)
}

@Composable
private fun CookedImageBlock(
    block: CookedPostBlock.Image,
    baseUrl: String,
    secondaryColor: Color,
    dividerColor: Color,
    onOpenLink: ((String) -> Unit)?,
    hiddenImageUrl: String?,
    onImageBoundsChanged: ((String, RectF?) -> Unit)?,
    onImageViewChanged: ((String, View?) -> Unit)?,
    onOpenImage: ((String, List<GalleryImage>, RectF?, android.graphics.Bitmap?, View?) -> Unit)?,
    galleryImages: List<GalleryImage>,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var imageBoundsInWindow by remember { mutableStateOf<RectF?>(null) }
    val imageUrl = remember(baseUrl, block.src) { resolvePostImageUrl(baseUrl, block.src) }
    val targetUrl = remember(baseUrl, block.href, imageUrl) {
        block.href?.let { resolvePostImageUrl(baseUrl, it) } ?: imageUrl
    }
    DisposableEffect(targetUrl) {
        onDispose {
            onImageBoundsChanged?.invoke(targetUrl, null)
            onImageViewChanged?.invoke(targetUrl, null)
        }
    }
    val hideSourceImage = hiddenImageUrl == targetUrl

    // ADAPTED-BACKEND: render the post body image into a real Android ImageView
    // (RoundedImageView) instead of a Compose Image, so PhotoViewer can:
    //   1. Hide it synchronously via setVisibility() — no Compose snapshot delay.
    //   2. Read its drawable bitmap directly, avoiding a second URL decode in
    //      PhotoViewer's pipeline. This eliminates the cross-pipeline pixel
    //      drift that produced the "shimmer" at handoff between source view and
    //      PhotoViewer's morph view.
    // Coil 3 still drives the actual loading (gif / svg / webp / network cache
    // all continue to work via the same ImageLoader singleton).
    var imageViewRef by remember { mutableStateOf<RoundedImageView?>(null) }
    var drawableSize by remember(imageUrl) { mutableStateOf<Pair<Int, Int>?>(null) }
    var lastLoadedUrl by remember { mutableStateOf<String?>(null) }
    val cornerRadiusPx = with(density) { postBodyEmbeddedMediaCornerRadius.toPx() }

    // Prefer Discourse-provided dimensions over Coil's load listener: they are
    // available before Coil decodes (so contentScaleFitRect computes the correct
    // letterbox-aware rect from the very first onImageBoundsChanged call) and
    // they are the original-image aspect (not the View-sized decoded bitmap).
    // The listener-supplied drawableSize is only a fallback for blocks where
    // Discourse omitted width/height attributes.
    val effectiveBitmapWidth = if (block.widthPx > 0) block.widthPx else drawableSize?.first ?: 0
    val effectiveBitmapHeight = if (block.heightPx > 0) block.heightPx else drawableSize?.second ?: 0

    val sourceImageBoundsInWindow = imageBoundsInWindow?.let {
        contentScaleFitRect(
            bounds = it,
            bitmapWidth = effectiveBitmapWidth,
            bitmapHeight = effectiveBitmapHeight,
        )
    }
    LaunchedEffect(
        targetUrl,
        effectiveBitmapWidth,
        effectiveBitmapHeight,
        sourceImageBoundsInWindow?.left,
        sourceImageBoundsInWindow?.top,
        sourceImageBoundsInWindow?.right,
        sourceImageBoundsInWindow?.bottom,
    ) {
        onImageBoundsChanged?.invoke(targetUrl, sourceImageBoundsInWindow)
    }
    val imageChromeModifier = Modifier
        .clip(RoundedCornerShape(postBodyEmbeddedMediaCornerRadius))
        .background(dividerColor)
        .onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            imageBoundsInWindow = RectF(bounds.left, bounds.top, bounds.right, bounds.bottom)
        }
        .clickable(enabled = onOpenImage != null || onOpenLink != null) {
            if (onOpenImage != null) {
                val iv = imageViewRef
                val ivBitmap = iv?.bitmap
                onOpenImage(
                    targetUrl,
                    galleryImages,
                    sourceImageBoundsInWindow ?: imageBoundsInWindow,
                    ivBitmap,
                    iv,
                )
            } else {
                onOpenLink?.invoke(targetUrl)
            }
        }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // PhotoViewer relies on this View existing in the Compose tree at the
        // tapped image's position. setVisibility is the synchronous path
        // (one paint cycle), and the bitmap returned by drawable.getBitmap()
        // is the same instance Coil decoded — PhotoViewer borrows it for the
        // morph view rather than re-downloading & re-decoding.
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val imageModifier = if (block.aspectRatio != null) {
                val size = calculatePostImageSize(
                    availableWidth = maxWidth,
                    intrinsicWidthPx = block.widthPx,
                    intrinsicHeightPx = block.heightPx,
                    maxHeight = 480.dp,
                )
                Modifier
                    .width(size.width)
                    .height(size.height)
            } else {
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 480.dp)
            }.then(imageChromeModifier)
            AndroidView(
                factory = { ctx ->
                    RoundedImageView(ctx).also { iv ->
                        iv.scaleType = ImageView.ScaleType.FIT_CENTER
                        iv.cornerRadiusPx = cornerRadiusPx
                        imageViewRef = iv
                        onImageViewChanged?.invoke(targetUrl, iv)
                    }
                },
                modifier = imageModifier,
                update = { iv ->
                    iv.cornerRadiusPx = cornerRadiusPx
                    iv.visibility = if (hideSourceImage) View.INVISIBLE else View.VISIBLE
                    if (lastLoadedUrl != imageUrl) {
                        lastLoadedUrl = imageUrl
                        iv.load(imageUrl) {
                            memoryCacheKey(imageUrl)
                            allowHardware(false)
                            crossfade(true)
                            listener(
                                onSuccess = { _, result ->
                                    val image = result.image
                                    val w = image.width
                                    val h = image.height
                                    if (w > 0 && h > 0) {
                                        drawableSize = w to h
                                    }
                                },
                            )
                        }
                    }
                },
            )
        }
        block.alt.takeIf { it.isNotBlank() }?.let { alt ->
            Text(
                text = alt,
                color = secondaryColor,
                fontSize = postBodyImageCaptionTextSize,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = postBodyImageCaptionTextSize),
            )
        }
    }

    @Suppress("UNUSED_EXPRESSION")
    PaddingValues(0.dp)
}

@Composable
private fun CookedOneboxBlock(
    block: CookedPostBlock.Onebox,
    baseUrl: String,
    textColor: Color,
    secondaryColor: Color,
    cardBackground: Color,
    dividerColor: Color,
    onOpenLink: ((String) -> Unit)?,
) {
    val context = LocalContext.current
    val resolvedHref = remember(baseUrl, block.href) {
        runCatching { resolvePostImageUrl(baseUrl, block.href) }.getOrDefault(block.href)
    }
    val resolvedThumbnail = remember(baseUrl, block.thumbnailUrl) {
        block.thumbnailUrl?.let { resolvePostImageUrl(baseUrl, it) }
    }
    val resolvedSiteIcon = remember(baseUrl, block.siteIconUrl) {
        block.siteIconUrl?.let { resolvePostImageUrl(baseUrl, it) }
    }
    val thumbnailRequest = remember(context, resolvedThumbnail) {
        resolvedThumbnail?.let { ImageRequest.Builder(context).data(it).build() }
    }
    val siteIconRequest = remember(context, resolvedSiteIcon) {
        resolvedSiteIcon?.let { ImageRequest.Builder(context).data(it).build() }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(postBodyEmbeddedBlockCornerRadius))
            .background(cardBackground)
            .clickable(enabled = onOpenLink != null) { onOpenLink?.invoke(resolvedHref) },
    ) {
        if (thumbnailRequest != null) {
            Image(
                painter = rememberAsyncImagePainter(model = thumbnailRequest),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 220.dp)
                    .background(dividerColor),
            )
        }
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (siteIconRequest != null) {
                    Image(
                        painter = rememberAsyncImagePainter(model = siteIconRequest),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .heightIn(min = 14.dp, max = 14.dp)
                            .width(14.dp),
                    )
                }
                block.domain?.takeIf { it.isNotBlank() }?.let { domain ->
                    Text(
                        text = domain,
                        color = secondaryColor,
                        fontSize = postBodyImageCaptionTextSize,
                    )
                }
            }
            Text(
                text = block.title,
                color = textColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = postBodyParagraphTextSize,
                maxLines = 2,
            )
            block.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    color = secondaryColor,
                    fontSize = postBodyImageCaptionTextSize,
                    maxLines = 3,
                )
            }
        }
    }
}

@Composable
private fun CookedCollageBlock(
    block: CookedPostBlock.Collage,
    baseUrl: String,
    dividerColor: Color,
    onOpenLink: ((String) -> Unit)?,
    hiddenImageUrl: String?,
    onImageBoundsChanged: ((String, RectF?) -> Unit)?,
    onImageViewChanged: ((String, View?) -> Unit)?,
    onOpenImage: ((String, List<GalleryImage>, RectF?, android.graphics.Bitmap?, View?) -> Unit)?,
    galleryImages: List<GalleryImage>,
) {
    // ADAPTED-PRODUCT: Telegram BlockCollageCell does aspect-ratio aware row
    // packing. We use a fixed 2-column grid with square cells, which matches
    // how Discourse multi-attachment posts typically read on mobile.
    //
    // PORT-MODE: Feature Workflow Port / ADAPTED-BACKEND (source view transport)
    // Collage cells render via AndroidView { RoundedImageView } — same pattern as
    // CookedImageBlock — so PhotoViewer.PlaceProviderObject.sourceView gets a real
    // Android View. That makes setVisible(false) synchronous (one paint cycle)
    // instead of going through the Compose hiddenImageUrl state, which would only
    // settle on the next snapshot apply and produce a one-frame source-flash on
    // close. It also lets PhotoViewer borrow the View's drawable bitmap rather
    // than re-decoding the URL through its own pipeline.
    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { postBodyEmbeddedMediaCornerRadius.toPx() }
    val pairs = remember(block.images) { block.images.chunked(2) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        pairs.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                row.forEach { image ->
                    val imageUrl = remember(baseUrl, image.src) {
                        resolvePostImageUrl(baseUrl, image.src)
                    }
                    val targetUrl = remember(baseUrl, image.href, imageUrl) {
                        image.href?.let { resolvePostImageUrl(baseUrl, it) } ?: imageUrl
                    }
                    DisposableEffect(targetUrl) {
                        onDispose {
                            onImageBoundsChanged?.invoke(targetUrl, null)
                            onImageViewChanged?.invoke(targetUrl, null)
                        }
                    }
                    var imageBoundsInWindow by remember(imageUrl) { mutableStateOf<RectF?>(null) }
                    var imageViewRef by remember(imageUrl) { mutableStateOf<RoundedImageView?>(null) }
                    var lastLoadedUrl by remember(imageUrl) { mutableStateOf<String?>(null) }
                    val hideSourceImage = hiddenImageUrl == targetUrl
                    AndroidView(
                        factory = { ctx ->
                            RoundedImageView(ctx).also { iv ->
                                iv.scaleType = ImageView.ScaleType.CENTER_CROP
                                iv.cornerRadiusPx = cornerRadiusPx
                                iv.setBackgroundColor(dividerColor.toArgb())
                                imageViewRef = iv
                                onImageViewChanged?.invoke(targetUrl, iv)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .onGloballyPositioned { coordinates ->
                                val bounds = coordinates.boundsInWindow()
                                imageBoundsInWindow = RectF(bounds.left, bounds.top, bounds.right, bounds.bottom)
                                onImageBoundsChanged?.invoke(targetUrl, imageBoundsInWindow)
                            }
                            .clickable(enabled = onOpenImage != null || onOpenLink != null) {
                                if (onOpenImage != null) {
                                    val iv = imageViewRef
                                    val ivBitmap = iv?.bitmap
                                    onOpenImage(targetUrl, galleryImages, imageBoundsInWindow, ivBitmap, iv)
                                } else {
                                    onOpenLink?.invoke(targetUrl)
                                }
                            },
                        update = { iv ->
                            iv.cornerRadiusPx = cornerRadiusPx
                            iv.visibility = if (hideSourceImage) View.INVISIBLE else View.VISIBLE
                            if (lastLoadedUrl != imageUrl) {
                                lastLoadedUrl = imageUrl
                                iv.load(imageUrl) {
                                    memoryCacheKey(imageUrl)
                                    allowHardware(false)
                                    crossfade(true)
                                }
                            }
                        },
                    )
                }
                if (row.size == 1) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun contentScaleFitRect(bounds: RectF, bitmapWidth: Int, bitmapHeight: Int): RectF {
    if (bitmapWidth <= 0 || bitmapHeight <= 0 || bounds.width() <= 0f || bounds.height() <= 0f) {
        return RectF(bounds)
    }
    val scale = minOf(bounds.width() / bitmapWidth, bounds.height() / bitmapHeight)
    val width = bitmapWidth * scale
    val height = bitmapHeight * scale
    return RectF(
        bounds.centerX() - width / 2f,
        bounds.centerY() - height / 2f,
        bounds.centerX() + width / 2f,
        bounds.centerY() + height / 2f,
    )
}

@Composable
private fun CookedPollBlock(
    block: CookedPostBlock.Poll,
    textColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    cardBackground: Color,
    dividerColor: Color,
) {
    // ADAPTED-PRODUCT: Voting requires authenticated POST /polls/vote which is
    // not yet wired in NativeDo. Render a read-only placeholder card so the
    // user at least sees the question and the options instead of an empty gap.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(postBodyEmbeddedBlockCornerRadius))
            .background(cardBackground)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = block.name?.takeIf { it.isNotBlank() } ?: "投票",
                color = textColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = postBodyParagraphTextSize,
                modifier = Modifier.weight(1f),
            )
            block.status?.takeIf { it.isNotBlank() }?.let { status ->
                Text(
                    text = status,
                    color = accentColor,
                    fontSize = postBodyQuoteCaptionTextSize,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        if (block.options.isEmpty()) {
            Text(
                text = "暂无选项",
                color = secondaryColor,
                fontSize = postBodyQuoteTextSize,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                block.options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(dividerColor.copy(alpha = 0.4f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = option,
                            color = textColor,
                            fontSize = postBodyQuoteTextSize,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        Text(
            text = buildString {
                append(block.voters?.let { "$it 票" } ?: "投票功能待接入")
                block.type?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
            },
            color = secondaryColor,
            fontSize = postBodyQuoteCaptionTextSize,
        )
    }
}

@Composable
private fun CookedSpoilerBlock(
    block: CookedPostBlock.Spoiler,
    cookieStore: CookieStore,
    baseUrl: String,
    textColor: Color,
    secondaryColor: Color,
    linkColor: Color,
    cardBackground: Color,
    chipBackgroundArgb: Int,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    onOpenLink: ((String) -> Unit)?,
) {
    // ADAPTED-PRODUCT: Telegram instant view obscures spoiler runs with an
    // animated mask span. Compose has no equivalent ReplacementSpan plumbing
    // in our rendering pipeline, so we wrap the whole spoiler block with a
    // tap-to-reveal card.
    var revealed by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(postBodyEmbeddedBlockCornerRadius))
            .background(cardBackground)
            .clickable(enabled = !revealed) { revealed = true }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = if (revealed) "剧透内容" else "点击查看剧透",
            color = if (revealed) secondaryColor else linkColor,
            fontSize = postBodyQuoteCaptionTextSize,
            fontWeight = FontWeight.Medium,
        )
        if (revealed) {
            CookedTextBlock(
                html = block.html,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                textColor = textColor,
                linkColor = linkColor,
                textSize = postBodyParagraphTextSize,
                chipBackgroundArgb = chipBackgroundArgb,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                onOpenLink = onOpenLink,
            )
        }
    }
}

@Composable
private fun CookedTableBlock(
    block: CookedPostBlock.Table,
    textColor: Color,
    secondaryColor: Color,
    cardBackground: Color,
    dividerColor: Color,
) {
    // Lightweight horizontally-scrolling table. Telegram BlockTableCell does
    // span/measurement; we keep cells text-only and uniform-width for now.
    val minColumnWidth = 96.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(postBodyEmbeddedBlockCornerRadius))
            .background(cardBackground),
    ) {
        Column(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            if (block.headers.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    block.headers.forEach { header ->
                        Text(
                            text = header,
                            color = textColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = postBodyQuoteTextSize,
                            modifier = Modifier
                                .width(minColumnWidth)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                HorizontalDivider(color = dividerColor, thickness = 1.dp)
            }
            block.rows.forEachIndexed { rowIndex, row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEach { cell ->
                        Text(
                            text = cell,
                            color = textColor,
                            fontSize = postBodyQuoteTextSize,
                            modifier = Modifier
                                .width(minColumnWidth)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                if (rowIndex < block.rows.lastIndex) {
                    HorizontalDivider(color = dividerColor.copy(alpha = 0.5f), thickness = 1.dp)
                }
            }
        }
    }

    @Suppress("UNUSED_EXPRESSION")
    secondaryColor
}

internal sealed interface CookedPostBlock {
    data class Text(val html: String) : CookedPostBlock
    data class Heading(val level: Int, val html: String) : CookedPostBlock
    data class Quote(val title: String?, val html: String) : CookedPostBlock
    data class Code(val code: String) : CookedPostBlock
    data class ListItems(val ordered: Boolean, val items: List<String>) : CookedPostBlock
    data class Image(
        val src: String,
        val alt: String,
        val href: String?,
        val widthPx: Int = 0,
        val heightPx: Int = 0,
    ) : CookedPostBlock {
        val aspectRatio: Float? = widthPx
            .takeIf { it > 0 }
            ?.let { width ->
                heightPx.takeIf { it > 0 }?.let { height -> width.toFloat() / height.toFloat() }
            }
    }
    data class Collage(val images: List<Image>) : CookedPostBlock
    data class Onebox(
        val href: String,
        val domain: String?,
        val siteIconUrl: String?,
        val thumbnailUrl: String?,
        val title: String,
        val description: String?,
    ) : CookedPostBlock

    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>,
    ) : CookedPostBlock

    data class Poll(
        val name: String?,
        val type: String?,
        val status: String?,
        val voters: Int?,
        val options: List<String>,
    ) : CookedPostBlock

    data class Spoiler(val html: String) : CookedPostBlock

    data object Rule : CookedPostBlock
}

internal fun parseCookedPostBody(html: String, baseUrl: String): List<CookedPostBlock> {
    val body = Jsoup.parseBodyFragment(html).body()
    val raw = body.children().flatMap { element -> parseCookedElement(element) }
    val blocks = coalesceImagesIntoCollage(raw)
    return blocks.ifEmpty {
        body.text().takeIf { it.isNotBlank() }?.let { text ->
            listOf(CookedPostBlock.Text("<p>$text</p>"))
        }.orEmpty()
    }
}

// ADAPTED-PRODUCT: Telegram TL_pageBlockCollage delivers a typed list of photo
// blocks from the server. Discourse cooked HTML emits them as adjacent <p> /
// .lightbox-wrapper images, so we coalesce 2+ adjacent Image blocks into a
// Collage and let the renderer lay them out as a 2-column grid.
private fun coalesceImagesIntoCollage(blocks: List<CookedPostBlock>): List<CookedPostBlock> {
    if (blocks.size < 2) return blocks
    val out = mutableListOf<CookedPostBlock>()
    var i = 0
    while (i < blocks.size) {
        val block = blocks[i]
        if (block is CookedPostBlock.Image) {
            var j = i + 1
            val group = mutableListOf(block)
            while (j < blocks.size && blocks[j] is CookedPostBlock.Image) {
                group.add(blocks[j] as CookedPostBlock.Image)
                j++
            }
            if (group.size >= 2) {
                out.add(CookedPostBlock.Collage(images = group.toList()))
            } else {
                out.add(block)
            }
            i = j
        } else {
            out.add(block)
            i++
        }
    }
    return out
}

// Pair of (full image URL, optional small-thumb URL) collected from a post body
// and handed to PhotoViewer. The full URL is the lightbox target — the <a href>
// when Discourse wrapped the image, otherwise the <img src>. The thumb URL is
// the <img src> (the srcset's smallest optimized variant, typically ~230px wide),
// which GroupedPhotosListView uses to populate its 55x42dp strip cells instead
// of downloading the full original.
//
// ADAPTED-BACKEND: Telegram carries separate thumb PhotoSizes inside a MessageObject.
// NativeDo carries them as a parallel String here.
data class GalleryImage(val url: String, val thumbUrl: String?, val widthPx: Int = 0, val heightPx: Int = 0)

private fun cookedGalleryImages(blocks: List<CookedPostBlock>, baseUrl: String): List<GalleryImage> {
    val seen = linkedMapOf<String, GalleryImage>()
    blocks.flatMap { block ->
        when (block) {
            is CookedPostBlock.Image -> listOf(block)
            is CookedPostBlock.Collage -> block.images
            else -> emptyList()
        }
    }.forEach { image ->
        val srcResolved = resolvePostImageUrl(baseUrl, image.src)
        val fullUrl = image.href?.let { resolvePostImageUrl(baseUrl, it) } ?: srcResolved
        val thumbUrl = if (srcResolved != fullUrl) srcResolved else null
        if (!seen.containsKey(fullUrl)) {
            seen[fullUrl] = GalleryImage(url = fullUrl, thumbUrl = thumbUrl, widthPx = image.widthPx, heightPx = image.heightPx)
        }
    }
    return seen.values.toList()
}

private fun parseCookedElement(element: Element): List<CookedPostBlock> {
    return when (element.normalName()) {
        "p" -> parseParagraphElement(element)
        "blockquote" -> element.html()
            .takeIfHtmlNotBlank()
            ?.let { listOf(CookedPostBlock.Quote(title = null, html = it)) }
            .orEmpty()
        "aside" -> parseAsideElement(element)
        "pre" -> listOf(CookedPostBlock.Code((element.selectFirst("code")?.wholeText() ?: element.wholeText()).trimEnd()))
        "ul", "ol" -> parseListElement(element)
        "details" -> parseDetailsElement(element)
        "h1" -> listOf(CookedPostBlock.Heading(level = 1, html = element.html()))
        "h2" -> listOf(CookedPostBlock.Heading(level = 2, html = element.html()))
        "h3" -> listOf(CookedPostBlock.Heading(level = 3, html = element.html()))
        "h4" -> listOf(CookedPostBlock.Heading(level = 4, html = element.html()))
        "h5" -> listOf(CookedPostBlock.Heading(level = 5, html = element.html()))
        "h6" -> listOf(CookedPostBlock.Heading(level = 6, html = element.html()))
        "hr" -> listOf(CookedPostBlock.Rule)
        "table" -> parseTableElement(element)
        "div" -> parseDivElement(element)
        else -> element.html().takeIf { it.isNotBlank() }?.let { listOf(CookedPostBlock.Text(it)) }.orEmpty()
    }
}

private fun parseParagraphElement(element: Element): List<CookedPostBlock> {
    val images = imageBlocksFrom(element)
    val textHtml = element.clone().apply { select("img:not(.emoji):not(.site-icon), .lightbox-wrapper").remove() }.html().takeIfHtmlNotBlank()
    return listOfNotNull(textHtml?.let(CookedPostBlock::Text)) + images
}

private fun parseAsideElement(element: Element): List<CookedPostBlock> {
    if (element.hasClass("quote")) {
        val title = element.selectFirst(".title")?.text()?.takeIf { it.isNotBlank() }
        val quoteHtml = element.selectFirst("blockquote")?.html()?.takeIfHtmlNotBlank() ?: element.html()
        return listOf(CookedPostBlock.Quote(title = title, html = quoteHtml))
    }
    if (element.hasClass("onebox")) {
        val onebox = parseOneboxAside(element)
        if (onebox != null) return listOf(onebox)
    }
    val link = element.selectFirst("a[href]")
    val title = link?.text()?.takeIf { it.isNotBlank() }
    val href = link?.attr("href").orEmpty()
    val bodyText = element.selectFirst("article")?.html()?.takeIfHtmlNotBlank()
    return listOfNotNull(
        title?.let { CookedPostBlock.Text("<p><a href=\"$href\">$it</a></p>") },
        bodyText?.let(CookedPostBlock::Text),
    ) + imageBlocksFrom(element)
}

private fun parseOneboxAside(element: Element): CookedPostBlock.Onebox? {
    val article = element.selectFirst("article.onebox-body") ?: element.selectFirst("article")
    val headerLink = element.selectFirst("header.source a[href]")
    val articleLink = article?.selectFirst("h3 a[href]")
    val href = element.attr("data-onebox-src")
        .ifBlank { articleLink?.attr("href").orEmpty() }
        .ifBlank { headerLink?.attr("href").orEmpty() }
        .ifBlank { return null }
    val domain = headerLink?.text()?.takeIf { it.isNotBlank() }
    val siteIconUrl = element.selectFirst("header.source img.site-icon")?.attr("src")?.takeIf { it.isNotBlank() }
    val thumbnailUrl = article?.selectFirst("img.thumbnail")?.attr("src")
        ?: article?.selectFirst("img[src]")?.attr("src")
    val title = (articleLink?.text() ?: article?.selectFirst("h3")?.text())
        ?.takeIf { it.isNotBlank() }
        ?: domain
        ?: href
    val description = article?.select("p")
        ?.joinToString(separator = "\n") { it.text() }
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    return CookedPostBlock.Onebox(
        href = href,
        domain = domain,
        siteIconUrl = siteIconUrl,
        thumbnailUrl = thumbnailUrl,
        title = title,
        description = description,
    )
}

private fun parsePollElement(element: Element): CookedPostBlock.Poll {
    val name = element.attr("data-poll-name").takeIf { it.isNotBlank() }
    val type = element.attr("data-poll-type").takeIf { it.isNotBlank() }
    val status = element.attr("data-poll-status").takeIf { it.isNotBlank() }
    val voters = element.selectFirst(".poll-info_counts-count .info-number")?.text()
        ?.trim()
        ?.toIntOrNull()
    val options = element.select(".poll-container li[data-poll-option-id]")
        .map { it.text().trim() }
        .filter { it.isNotEmpty() }
    return CookedPostBlock.Poll(
        name = name,
        type = type,
        status = status,
        voters = voters,
        options = options,
    )
}

private fun parseTableElement(element: Element): List<CookedPostBlock> {
    val table = if (element.normalName() == "table") element else element.selectFirst("table") ?: return emptyList()
    val headers = table.selectFirst("thead")?.select("tr")?.firstOrNull()
        ?.select("th")
        ?.map { it.text().trim() }
        .orEmpty()
    val rows = table.select("tbody tr").map { row ->
        row.select("th, td").map { it.text().trim() }
    }.filter { it.isNotEmpty() }
    if (headers.isEmpty() && rows.isEmpty()) return emptyList()
    return listOf(CookedPostBlock.Table(headers = headers, rows = rows))
}

private fun parseListElement(element: Element): List<CookedPostBlock> {
    val items = element.children()
        .filter { child -> child.normalName() == "li" }
        .mapNotNull { item -> item.html().takeIfHtmlNotBlank() }
    return if (items.isEmpty()) emptyList() else listOf(CookedPostBlock.ListItems(ordered = element.normalName() == "ol", items = items))
}

private fun parseDetailsElement(element: Element): List<CookedPostBlock> {
    val summary = element.selectFirst("summary")?.text()?.takeIf { it.isNotBlank() }
    val content = element.clone().apply { select("summary").remove() }
    val blocks = content.children().flatMap { child -> parseCookedElement(child) }
    return if (summary == null) blocks else listOf(CookedPostBlock.Text("<p><strong>$summary</strong></p>")) + blocks
}

private fun parseDivElement(element: Element): List<CookedPostBlock> {
    if (element.hasClass("poll")) {
        return listOf(parsePollElement(element))
    }
    if (element.hasClass("spoiler") || element.hasClass("spoiled")) {
        val inner = element.html().takeIfHtmlNotBlank() ?: return emptyList()
        return listOf(CookedPostBlock.Spoiler(html = inner))
    }
    if (element.hasClass("md-table") || element.selectFirst(":root > table") != null) {
        val tableBlocks = parseTableElement(element)
        if (tableBlocks.isNotEmpty()) return tableBlocks
    }
    val images = imageBlocksFrom(element)
    if (images.isNotEmpty()) {
        val textHtml = element.clone().apply { select("img:not(.emoji):not(.site-icon), .lightbox-wrapper").remove() }.html().takeIfHtmlNotBlank()
        return listOfNotNull(textHtml?.let(CookedPostBlock::Text)) + images
    }
    return if (element.children().isNotEmpty()) {
        element.children().flatMap { child -> parseCookedElement(child) }
    } else {
        element.html().takeIfHtmlNotBlank()?.let { listOf(CookedPostBlock.Text(it)) }.orEmpty()
    }
}

private fun imageBlocksFrom(element: Element): List<CookedPostBlock.Image> {
    return element.select("img[src]")
        .filterNot { image -> image.hasClass("emoji") || image.hasClass("avatar") || image.hasClass("site-icon") }
        .map { image ->
            CookedPostBlock.Image(
                src = image.attr("src"),
                alt = image.attr("alt").ifBlank { image.attr("title") },
                href = image.parents().firstOrNull { parent -> parent.normalName() == "a" && parent.hasAttr("href") }?.attr("href"),
                widthPx = image.attr("width").toIntOrNull() ?: 0,
                heightPx = image.attr("height").toIntOrNull() ?: 0,
            )
        }
}

private fun String?.takeIfHtmlNotBlank(): String? = this
    ?.takeIf { Jsoup.parseBodyFragment(it).body().text().isNotBlank() || Jsoup.parseBodyFragment(it).select("img[src]").isNotEmpty() }

internal val postBodyEmbeddedBlockCornerRadius = 10.dp
internal val postBodyEmbeddedMediaCornerRadius = 14.dp

internal fun resolvePostImageUrl(baseUrl: String, rawUrl: String): String =
    baseUrl.toHttpUrl().resolve(rawUrl)?.toString() ?: rawUrl

internal fun calculatePostImageSize(
    availableWidth: androidx.compose.ui.unit.Dp,
    intrinsicWidthPx: Int,
    intrinsicHeightPx: Int,
    maxHeight: androidx.compose.ui.unit.Dp,
): androidx.compose.ui.unit.DpSize {
    val maxWidth = if (availableWidth == androidx.compose.ui.unit.Dp.Unspecified || availableWidth <= 0.dp) {
        240.dp
    } else {
        availableWidth
    }
    if (intrinsicWidthPx <= 0 || intrinsicHeightPx <= 0) {
        return androidx.compose.ui.unit.DpSize(width = maxWidth, height = maxWidth)
    }
    val intrinsicWidth = with(androidx.compose.ui.unit.Density(1f)) { intrinsicWidthPx.toDp() }
    val intrinsicHeight = with(androidx.compose.ui.unit.Density(1f)) { intrinsicHeightPx.toDp() }
    val scale = minOf(1f, maxWidth / intrinsicWidth)
    return androidx.compose.ui.unit.DpSize(
        width = intrinsicWidth * scale,
        height = intrinsicHeight * scale,
    )
}

internal fun shouldHidePostImageThumbnail(image: Any, hiddenImage: Any?): Boolean = image === hiddenImage
