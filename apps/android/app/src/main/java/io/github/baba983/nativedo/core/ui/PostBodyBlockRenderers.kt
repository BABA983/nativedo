package io.github.baba983.nativedo.core.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras

@Composable
internal fun RenderPostBodyBlock(
    block: PostBodyBlock,
    cookieStore: CookieStore,
    baseUrl: String,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    hiddenImage: PostBodyImage? = null,
    onImageSourceChanged: ((PostBodyImage, PostImageViewerLaunchSource?) -> Unit)?,
    onImageClick: ((PostBodyImage, PostImageViewerLaunchSource?) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    when (block) {
        is PostBodyBlock.HtmlBlock -> {
            HtmlTextBlock(
                html = block.html,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                modifier = modifier,
            )
        }

        is PostBodyBlock.ImageBlock -> {
            PostImageView(
                image = block.image,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                isHidden = shouldHidePostImageThumbnail(block.image, hiddenImage),
                onLaunchSourceChanged = onImageSourceChanged?.let { callback ->
                    { launchSource -> callback(block.image, launchSource) }
                },
                onClick = onImageClick?.let { callback ->
                    { launchSource -> callback(block.image, launchSource) }
                },
                modifier = modifier,
            )
        }

        is PostBodyBlock.LightboxBlock -> {
            LightboxBlockView(
                block = block,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                hiddenImage = hiddenImage,
                onImageSourceChanged = onImageSourceChanged,
                onImageClick = onImageClick,
                modifier = modifier,
            )
        }

        is PostBodyBlock.ImageGridBlock -> {
            PostImageGrid(
                images = block.images,
                columns = block.columns,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                hiddenImage = hiddenImage,
                onImageSourceChanged = onImageSourceChanged,
                onImageClick = { image, launchSource ->
                    onImageClick?.invoke(image, launchSource)
                },
                modifier = modifier,
            )
        }

        is PostBodyBlock.OneboxBlock -> {
            OneboxBlockView(
                block = block,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                modifier = modifier,
            )
        }

        is PostBodyBlock.RichTextBlock -> {
            PostInlineTextBlock(
                inlines = block.inlines,
                style = block.style,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                modifier = modifier,
            )
        }

        is PostBodyBlock.DetailsBlock -> {
            DetailsBlockView(
                block = block,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                hiddenImage = hiddenImage,
                onImageSourceChanged = onImageSourceChanged,
                onImageClick = onImageClick,
                modifier = modifier,
            )
        }

        is PostBodyBlock.QuoteBlock -> {
            QuoteBlockView(
                block = block,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                hiddenImage = hiddenImage,
                onImageSourceChanged = onImageSourceChanged,
                onImageClick = onImageClick,
                modifier = modifier,
            )
        }

        is PostBodyBlock.DiscourseQuoteBlock -> {
            DiscourseQuoteBlockView(
                block = block,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                hiddenImage = hiddenImage,
                onImageSourceChanged = onImageSourceChanged,
                onImageClick = onImageClick,
                modifier = modifier,
            )
        }

        is PostBodyBlock.CodeBlock -> {
            CodeBlockView(
                block = block,
                modifier = modifier,
            )
        }

        is PostBodyBlock.ListBlock -> {
            ListBlockView(
                block = block,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                hiddenImage = hiddenImage,
                onImageSourceChanged = onImageSourceChanged,
                onImageClick = onImageClick,
                modifier = modifier,
            )
        }
    }
}

@Composable
internal fun LightboxBlockView(
    block: PostBodyBlock.LightboxBlock,
    cookieStore: CookieStore,
    baseUrl: String,
    hiddenImage: PostBodyImage? = null,
    onImageSourceChanged: ((PostBodyImage, PostImageViewerLaunchSource?) -> Unit)?,
    onImageClick: ((PostBodyImage, PostImageViewerLaunchSource?) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
    var launchSource by remember(block.image) { mutableStateOf<PostImageViewerLaunchSource?>(null) }
    val clickModifier = if (onImageClick != null) {
        Modifier.clickable { onImageClick(block.image, launchSource) }
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.glassBackground)
            .border(
                width = 1.dp,
                color = colors.glassStroke,
                shape = shape,
            )
            .then(clickModifier)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PostImageView(
            image = block.image,
            cookieStore = cookieStore,
            baseUrl = baseUrl,
            isHidden = shouldHidePostImageThumbnail(block.image, hiddenImage),
            onLaunchSourceChanged = { nextLaunchSource ->
                launchSource = nextLaunchSource
                onImageSourceChanged?.invoke(block.image, nextLaunchSource)
            },
            onClick = onImageClick?.let { callback ->
                { launchSource -> callback(block.image, launchSource) }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (!block.filename.isNullOrBlank() || !block.information.isNullOrBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                block.filename?.takeIf { it.isNotBlank() }?.let { filename ->
                    Text(
                        text = filename,
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } ?: Spacer(modifier = Modifier.weight(1f))

                block.information?.takeIf { it.isNotBlank() }?.let { information ->
                    Text(
                        text = information,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun OneboxBlockView(
    block: PostBodyBlock.OneboxBlock,
    cookieStore: CookieStore,
    baseUrl: String,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val uriHandler = LocalUriHandler.current
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.glassBackground)
            .border(
                width = 1.dp,
                color = colors.glassStroke,
                shape = shape,
            )
            .clickable { uriHandler.openUri(block.targetUrl) }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                    .background(colors.backgroundElevated),
                contentAlignment = Alignment.Center,
            ) {
                block.sourceIconUrl?.takeIf { it.isNotBlank() }?.let { sourceIconUrl ->
                    AsyncImage(
                        model = sourceIconUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Text(
                text = block.sourceLabel,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        block.image?.let { image ->
            PostImageView(
                image = image,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                maxHeight = 190.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Text(
            text = block.title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        block.description?.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun DetailsBlockView(
    block: PostBodyBlock.DetailsBlock,
    cookieStore: CookieStore,
    baseUrl: String,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    hiddenImage: PostBodyImage? = null,
    onImageSourceChanged: ((PostBodyImage, PostImageViewerLaunchSource?) -> Unit)?,
    onImageClick: ((PostBodyImage, PostImageViewerLaunchSource?) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val headerInteractionSource = remember { MutableInteractionSource() }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
    val detailsBackground = colors.accentMuted.copy(alpha = 0.42f)
    var expanded by rememberSaveable(
        block.summary,
        block.children,
        block.expandedByDefault,
    ) {
        mutableStateOf(block.expandedByDefault)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(detailsBackground)
            .animateContentSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = headerInteractionSource,
                    indication = null,
                ) {
                    expanded = !expanded
                }
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PostInlineText(
                inlines = block.summary.inlines,
                textStyle = MaterialTheme.typography.labelLarge,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                textColor = colors.textPrimary,
                linkColor = colors.accent,
                onClick = { expanded = !expanded },
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (expanded) "▾" else "▸",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
            )
        }

        if (expanded && block.children.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                block.children.forEach { child ->
                    RenderPostBodyBlock(
                        block = child,
                        cookieStore = cookieStore,
                        baseUrl = baseUrl,
                        onTopicClick = onTopicClick,
                        onUserClick = onUserClick,
                        hiddenImage = hiddenImage,
                        onImageSourceChanged = onImageSourceChanged,
                        onImageClick = onImageClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
internal fun DiscourseQuoteBlockView(
    block: PostBodyBlock.DiscourseQuoteBlock,
    cookieStore: CookieStore,
    baseUrl: String,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    hiddenImage: PostBodyImage? = null,
    onImageSourceChanged: ((PostBodyImage, PostImageViewerLaunchSource?) -> Unit)?,
    onImageClick: ((PostBodyImage, PostImageViewerLaunchSource?) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val quoteBackground = colors.accentMuted.copy(alpha = 0.52f)
    val accentColor = colors.accent
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
    val authorUsername = block.authorUsername?.takeIf { username ->
        username.isNotBlank()
    }
    val authorClickModifier = if (authorUsername != null && onUserClick != null) {
        Modifier.clickable { onUserClick(authorUsername) }
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(quoteBackground)
            .drawBehind {
                val barWidth = 3.dp.toPx()
                val verticalInset = 8.dp.toPx()
                drawRoundRect(
                    color = accentColor,
                    topLeft = Offset(0f, verticalInset),
                    size = Size(
                        barWidth,
                        (size.height - verticalInset * 2f).coerceAtLeast(barWidth),
                    ),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                )
            }
            .padding(start = 14.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            UserAvatar(
                label = block.authorDisplayName,
                avatarTemplate = block.avatarUrl.orEmpty(),
                baseUrl = baseUrl,
                size = 22.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = authorClickModifier,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = block.authorDisplayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textPrimary,
                    )
                    authorUsername
                        ?.takeIf { username -> username != block.authorDisplayName }
                        ?.let { username ->
                            Text(
                                text = "@$username",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                }

                if (block.titleInlines.isNotEmpty()) {
                    PostInlineText(
                        inlines = block.titleInlines,
                        textStyle = MaterialTheme.typography.labelMedium,
                        cookieStore = cookieStore,
                        baseUrl = baseUrl,
                        onTopicClick = onTopicClick,
                        onUserClick = onUserClick,
                        textColor = colors.accent,
                        linkColor = colors.accent,
                    )
                }
            }
        }

        if (block.children.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                block.children.forEach { child ->
                    RenderPostBodyBlock(
                        block = child,
                        cookieStore = cookieStore,
                        baseUrl = baseUrl,
                        onTopicClick = onTopicClick,
                        onUserClick = onUserClick,
                        hiddenImage = hiddenImage,
                        onImageSourceChanged = onImageSourceChanged,
                        onImageClick = onImageClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
internal fun QuoteBlockView(
    block: PostBodyBlock.QuoteBlock,
    cookieStore: CookieStore,
    baseUrl: String,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    hiddenImage: PostBodyImage? = null,
    onImageSourceChanged: ((PostBodyImage, PostImageViewerLaunchSource?) -> Unit)?,
    onImageClick: ((PostBodyImage, PostImageViewerLaunchSource?) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val accentColor = colors.accent
    val quoteBackground = colors.accentMuted.copy(alpha = 0.52f)
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(quoteBackground)
            .drawBehind {
                val barWidth = 3.dp.toPx()
                val verticalInset = 8.dp.toPx()
                drawRoundRect(
                    color = accentColor,
                    topLeft = Offset(0f, verticalInset),
                    size = Size(
                        barWidth,
                        (size.height - verticalInset * 2f).coerceAtLeast(barWidth),
                    ),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                )
            }
            .padding(start = 14.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        block.children.forEach { child ->
            RenderPostBodyBlock(
                block = child,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                hiddenImage = hiddenImage,
                onImageSourceChanged = onImageSourceChanged,
                onImageClick = onImageClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun CodeBlockView(
    block: PostBodyBlock.CodeBlock,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f))
            .border(
                width = 1.dp,
                color = colors.glassStroke,
                shape = shape,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        block.languageHint?.takeIf { it.isNotBlank() }?.let { language ->
            Text(
                text = language,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
        ) {
            Text(
                text = block.code,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp,
                ),
                color = colors.textPrimary,
                softWrap = false,
            )
        }
    }
}

@Composable
internal fun ListBlockView(
    block: PostBodyBlock.ListBlock,
    cookieStore: CookieStore,
    baseUrl: String,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    hiddenImage: PostBodyImage? = null,
    onImageSourceChanged: ((PostBodyImage, PostImageViewerLaunchSource?) -> Unit)?,
    onImageClick: ((PostBodyImage, PostImageViewerLaunchSource?) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        block.items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = if (block.ordered) "${index + 1}." else "•",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textSecondary,
                    modifier = Modifier.width(18.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item.children.forEach { child ->
                        RenderPostBodyBlock(
                            block = child,
                            cookieStore = cookieStore,
                            baseUrl = baseUrl,
                            onTopicClick = onTopicClick,
                            onUserClick = onUserClick,
                            hiddenImage = hiddenImage,
                            onImageSourceChanged = onImageSourceChanged,
                            onImageClick = onImageClick,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

internal fun postBodyBlockSpacingAfter(
    current: PostBodyBlock,
    next: PostBodyBlock?,
): Dp {
    if (next == null) {
        return 0.dp
    }

    return when {
        current.isHeadingBlock() && next.isParagraphBlock() -> 8.dp
        current.isMediaBlock() && next.isParagraphBlock() -> 10.dp
        current.isParagraphBlock() && next.isMediaBlock() -> 10.dp
        current.isStructuredBlock() || next.isStructuredBlock() -> 12.dp
        else -> 10.dp
    }
}

private fun PostBodyBlock.isHeadingBlock(): Boolean {
    return this is PostBodyBlock.RichTextBlock && style != RichTextBlockStyle.Paragraph
}

private fun PostBodyBlock.isParagraphBlock(): Boolean {
    return this is PostBodyBlock.RichTextBlock && style == RichTextBlockStyle.Paragraph
}

private fun PostBodyBlock.isMediaBlock(): Boolean {
    return this is PostBodyBlock.ImageBlock ||
        this is PostBodyBlock.LightboxBlock ||
        this is PostBodyBlock.ImageGridBlock
}

private fun PostBodyBlock.isStructuredBlock(): Boolean {
    return this is PostBodyBlock.OneboxBlock ||
        this is PostBodyBlock.DetailsBlock ||
        this is PostBodyBlock.QuoteBlock ||
        this is PostBodyBlock.DiscourseQuoteBlock ||
        this is PostBodyBlock.CodeBlock ||
        this is PostBodyBlock.ListBlock
}
