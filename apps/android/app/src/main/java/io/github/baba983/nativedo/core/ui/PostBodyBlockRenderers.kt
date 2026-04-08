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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras

@Composable
internal fun RenderPostBodyBlock(
    block: PostBodyBlock,
    cookieStore: CookieStore,
    baseUrl: String,
    svgSpriteMarkup: String = "",
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    onOpenLink: ((String) -> Unit)?,
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
                onOpenLink = onOpenLink,
                modifier = modifier,
            )
        }

        is PostBodyBlock.UnknownBlock -> {
            HtmlTextBlock(
                html = block.rawHtml,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                onOpenLink = onOpenLink,
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
                onOpenLink = onOpenLink,
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
                onOpenLink = onOpenLink,
                modifier = modifier,
            )
        }

        is PostBodyBlock.ParagraphBlock -> {
            PostTextDocumentView(
                document = block.content,
                textStyle = postBodyParagraphTextStyle(MaterialTheme.typography.bodyLarge),
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                onOpenLink = onOpenLink,
                modifier = modifier,
            )
        }

        is PostBodyBlock.HeadingBlock -> {
            PostTextDocumentView(
                document = block.content,
                textStyle = block.level.toHeadingTextStyle(),
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                modifier = modifier,
            )
        }

        is PostBodyBlock.HorizontalRuleBlock -> {
            HorizontalRuleBlockView(modifier = modifier)
        }

        is PostBodyBlock.DetailsBlock -> {
            DetailsBlockView(
                block = block,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                svgSpriteMarkup = svgSpriteMarkup,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                onOpenLink = onOpenLink,
                hiddenImage = hiddenImage,
                onImageSourceChanged = onImageSourceChanged,
                onImageClick = onImageClick,
                modifier = modifier,
            )
        }

        is PostBodyBlock.CalloutBlock -> {
            CalloutBlockView(
                block = block,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                svgSpriteMarkup = svgSpriteMarkup,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                onOpenLink = onOpenLink,
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
                svgSpriteMarkup = svgSpriteMarkup,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                onOpenLink = onOpenLink,
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
                svgSpriteMarkup = svgSpriteMarkup,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                onOpenLink = onOpenLink,
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
                svgSpriteMarkup = svgSpriteMarkup,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                onOpenLink = onOpenLink,
                hiddenImage = hiddenImage,
                onImageSourceChanged = onImageSourceChanged,
                onImageClick = onImageClick,
                modifier = modifier,
            )
        }
    }
}

@Composable
internal fun HorizontalRuleBlockView(
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .height(1.dp)
            .background(
                color = colors.textSecondary.copy(alpha = 0.18f),
            ),
    )
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
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(postBodyEmbeddedMediaCornerRadius)
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
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
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
    onOpenLink: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(postBodyEmbeddedBlockCornerRadius)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.backgroundElevated.copy(alpha = 0.82f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
                shape = shape,
            )
            .clickable(enabled = onOpenLink != null) {
                onOpenLink?.invoke(block.targetUrl)
            }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
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
    svgSpriteMarkup: String = "",
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    onOpenLink: ((String) -> Unit)?,
    hiddenImage: PostBodyImage? = null,
    onImageSourceChanged: ((PostBodyImage, PostImageViewerLaunchSource?) -> Unit)?,
    onImageClick: ((PostBodyImage, PostImageViewerLaunchSource?) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val headerInteractionSource = remember { MutableInteractionSource() }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(postBodyEmbeddedBlockCornerRadius)
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
            .background(colors.backgroundElevated.copy(alpha = 0.74f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f),
                shape = shape,
            )
            .animateContentSize()
            .padding(horizontal = 10.dp, vertical = 8.dp),
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
            PostTextDocumentView(
                document = block.summary,
                textStyle = MaterialTheme.typography.labelLarge,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                onOpenLink = onOpenLink,
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
            ) {
                block.children.forEachIndexed { index, child ->
                    RenderPostBodyBlock(
                        block = child,
                        cookieStore = cookieStore,
                        baseUrl = baseUrl,
                        svgSpriteMarkup = svgSpriteMarkup,
                        onTopicClick = onTopicClick,
                        onUserClick = onUserClick,
                        onOpenLink = onOpenLink,
                        hiddenImage = hiddenImage,
                        onImageSourceChanged = onImageSourceChanged,
                        onImageClick = onImageClick,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    val spacing = postBodyBlockSpacingAfter(
                        current = child,
                        next = block.children.getOrNull(index + 1),
                    )
                    if (spacing > 0.dp) {
                        Spacer(modifier = Modifier.height(spacing))
                    }
                }
            }
        }
    }
}

@Composable
internal fun CalloutBlockView(
    block: PostBodyBlock.CalloutBlock,
    cookieStore: CookieStore,
    baseUrl: String,
    svgSpriteMarkup: String,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    onOpenLink: ((String) -> Unit)?,
    hiddenImage: PostBodyImage? = null,
    onImageSourceChanged: ((PostBodyImage, PostImageViewerLaunchSource?) -> Unit)?,
    onImageClick: ((PostBodyImage, PostImageViewerLaunchSource?) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val accentColor = discourseFlairColor(
        rawHex = block.spec.color,
        fallback = colors.accent,
    )
    val headerDocument = resolveCalloutHeaderDocument(block)
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(postBodyEmbeddedBlockCornerRadius)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(accentColor.copy(alpha = 0.11f))
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.24f),
                shape = shape,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (headerDocument != null || block.spec.icon.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (block.spec.icon.isNotBlank()) {
                    DiscourseSpriteIcon(
                        spriteMarkup = svgSpriteMarkup,
                        rawIcon = block.spec.icon,
                        label = block.spec.type,
                        rawTintColor = block.spec.color,
                        size = 16.dp,
                    )
                }
                headerDocument?.let { title ->
                    PostTextDocumentView(
                        document = title,
                        textStyle = MaterialTheme.typography.labelLarge,
                        cookieStore = cookieStore,
                        baseUrl = baseUrl,
                        onTopicClick = onTopicClick,
                        onUserClick = onUserClick,
                        onOpenLink = onOpenLink,
                        textColor = accentColor,
                        linkColor = accentColor,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        block.children.forEachIndexed { index, child ->
            RenderPostBodyBlock(
                block = child,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                svgSpriteMarkup = svgSpriteMarkup,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                onOpenLink = onOpenLink,
                hiddenImage = hiddenImage,
                onImageSourceChanged = onImageSourceChanged,
                onImageClick = onImageClick,
                modifier = Modifier.fillMaxWidth(),
            )

            val spacing = postBodyBlockSpacingAfter(
                current = child,
                next = block.children.getOrNull(index + 1),
            )
            if (spacing > 0.dp) {
                Spacer(modifier = Modifier.height(spacing))
            }
        }
    }
}

internal fun resolveCalloutHeaderDocument(
    block: PostBodyBlock.CalloutBlock,
): TextDocument? {
    val title = block.title?.takeIf { document ->
        document.text.isNotBlank() || document.entities.isNotEmpty()
    }
    if (title != null) {
        return title
    }
    val fallbackLabel = block.spec.type
        .trim()
        .replaceFirstChar { character ->
            if (character.isLowerCase()) {
                character.titlecase()
            } else {
                character.toString()
            }
        }
        .ifBlank { return null }
    return TextDocument(
        text = fallbackLabel,
        entities = emptyList(),
    )
}

@Composable
internal fun DiscourseQuoteBlockView(
    block: PostBodyBlock.DiscourseQuoteBlock,
    cookieStore: CookieStore,
    baseUrl: String,
    svgSpriteMarkup: String = "",
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    onOpenLink: ((String) -> Unit)?,
    hiddenImage: PostBodyImage? = null,
    onImageSourceChanged: ((PostBodyImage, PostImageViewerLaunchSource?) -> Unit)?,
    onImageClick: ((PostBodyImage, PostImageViewerLaunchSource?) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val accentColor = colors.accent.copy(alpha = 0.92f)
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(postBodyEmbeddedBlockCornerRadius)
    val containerColor = postBodyQuoteContainerColor()
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = shape,
            )
            .drawBehind {
                val barWidth = 2.5.dp.toPx()
                val verticalInset = 5.dp.toPx()
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
            .padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 8.dp),
    ) {
        block.title
            ?.takeIf { title -> title.text.isNotBlank() }
            ?.let { title ->
                PostTextDocumentView(
                    document = title,
                    textStyle = MaterialTheme.typography.labelMedium,
                    cookieStore = cookieStore,
                    baseUrl = baseUrl,
                    onTopicClick = onTopicClick,
                    onUserClick = onUserClick,
                    onOpenLink = onOpenLink,
                    textColor = colors.accent,
                    linkColor = colors.accent,
                )
            }
            ?: block.authorDisplayName
                .takeIf(String::isNotBlank)
                ?.let { author ->
                    Text(
                        text = author,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                }

        if (block.children.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                block.children.forEachIndexed { index, child ->
                    RenderPostBodyBlock(
                        block = child,
                        cookieStore = cookieStore,
                        baseUrl = baseUrl,
                        svgSpriteMarkup = svgSpriteMarkup,
                        onTopicClick = onTopicClick,
                        onUserClick = onUserClick,
                        onOpenLink = onOpenLink,
                        hiddenImage = hiddenImage,
                        onImageSourceChanged = onImageSourceChanged,
                        onImageClick = onImageClick,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    val spacing = postBodyBlockSpacingAfter(
                        current = child,
                        next = block.children.getOrNull(index + 1),
                    )
                    if (spacing > 0.dp) {
                        Spacer(modifier = Modifier.height(spacing))
                    }
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
    svgSpriteMarkup: String = "",
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    onOpenLink: ((String) -> Unit)?,
    hiddenImage: PostBodyImage? = null,
    onImageSourceChanged: ((PostBodyImage, PostImageViewerLaunchSource?) -> Unit)?,
    onImageClick: ((PostBodyImage, PostImageViewerLaunchSource?) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val accentColor = colors.accent.copy(alpha = 0.92f)
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(postBodyEmbeddedBlockCornerRadius)
    val containerColor = postBodyQuoteContainerColor()
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = shape,
            )
            .drawBehind {
                val barWidth = 2.5.dp.toPx()
                val verticalInset = 5.dp.toPx()
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
            .padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 8.dp),
    ) {
        block.children.forEachIndexed { index, child ->
            RenderPostBodyBlock(
                block = child,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                svgSpriteMarkup = svgSpriteMarkup,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                onOpenLink = onOpenLink,
                hiddenImage = hiddenImage,
                onImageSourceChanged = onImageSourceChanged,
                onImageClick = onImageClick,
                modifier = Modifier.fillMaxWidth(),
            )

            val spacing = postBodyBlockSpacingAfter(
                current = child,
                next = block.children.getOrNull(index + 1),
            )
            if (spacing > 0.dp) {
                Spacer(modifier = Modifier.height(spacing))
            }
        }
    }
}

@Composable
internal fun CodeBlockView(
    block: PostBodyBlock.CodeBlock,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val colorScheme = MaterialTheme.colorScheme
    val clipboardManager = LocalClipboardManager.current
    val bulletinHostState = LocalBulletinHostState.current
    val highlighted = remember(block.code, block.languageHint) {
        highlightCode(
            code = block.code,
            language = block.languageHint,
        )
    }
    val highlightedText = remember(highlighted, colorScheme, colors.textPrimary) {
        buildAnnotatedString {
            highlighted.forEach { token ->
                withStyle(
                    SpanStyle(
                        color = when (token.kind) {
                            PostCodeTokenKind.Keyword -> colorScheme.primary
                            PostCodeTokenKind.String -> colorScheme.tertiary
                            PostCodeTokenKind.Comment -> colorScheme.outline
                            PostCodeTokenKind.Number -> colorScheme.secondary
                            PostCodeTokenKind.Plain -> colors.textPrimary
                        },
                    ),
                ) {
                    append(token.text)
                }
            }
        }
    }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(postBodyEmbeddedBlockCornerRadius)
    val contentShape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()
    val copyChipShape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp)
    val copyLabel = stringResource(R.string.post_code_block_copy)
    val copiedTitle = stringResource(R.string.post_code_block_copied_title)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
                shape = shape,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            block.languageHint?.takeIf { it.isNotBlank() }?.let { language ->
                Text(
                    text = language,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } ?: Spacer(modifier = Modifier.weight(1f))

            Text(
                text = copyLabel,
                style = MaterialTheme.typography.labelSmall,
                color = colors.accent,
                modifier = Modifier
                    .clip(copyChipShape)
                    .background(colors.backgroundBase.copy(alpha = 0.66f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f),
                        shape = copyChipShape,
                    )
                    .clickable {
                        clipboardManager.setText(AnnotatedString(block.code))
                        bulletinHostState?.show(
                            BulletinMessage(
                                title = copiedTitle,
                                tone = BulletinTone.Success,
                            ),
                        )
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(contentShape)
                .background(colors.backgroundBase.copy(alpha = 0.26f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f),
                    shape = contentShape,
                )
                .heightIn(max = postBodyCodeBlockMaxHeight)
                .verticalScroll(verticalScrollState)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Box(
                modifier = Modifier.horizontalScroll(horizontalScrollState),
            ) {
                Text(
                    text = highlightedText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                    ),
                    color = colors.textPrimary,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
internal fun ListBlockView(
    block: PostBodyBlock.ListBlock,
    cookieStore: CookieStore,
    baseUrl: String,
    svgSpriteMarkup: String = "",
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    onOpenLink: ((String) -> Unit)?,
    hiddenImage: PostBodyImage? = null,
    onImageSourceChanged: ((PostBodyImage, PostImageViewerLaunchSource?) -> Unit)?,
    onImageClick: ((PostBodyImage, PostImageViewerLaunchSource?) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val markerWidth = listMarkerWidth(
        ordered = block.ordered,
        itemCount = block.items.size,
    )

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
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(markerWidth),
                )
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    item.children.forEachIndexed { childIndex, child ->
                        RenderPostBodyBlock(
                            block = child,
                            cookieStore = cookieStore,
                            baseUrl = baseUrl,
                            svgSpriteMarkup = svgSpriteMarkup,
                            onTopicClick = onTopicClick,
                            onUserClick = onUserClick,
                            onOpenLink = onOpenLink,
                            hiddenImage = hiddenImage,
                            onImageSourceChanged = onImageSourceChanged,
                            onImageClick = onImageClick,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        val spacing = postBodyBlockSpacingAfter(
                            current = child,
                            next = item.children.getOrNull(childIndex + 1),
                        )
                        if (spacing > 0.dp) {
                            Spacer(modifier = Modifier.height(spacing))
                        }
                    }
                }
            }
        }
    }
}

internal fun listMarkerWidth(
    ordered: Boolean,
    itemCount: Int,
): Dp {
    if (!ordered) {
        return 18.dp
    }

    return when {
        itemCount >= 100 -> 32.dp
        itemCount >= 10 -> 26.dp
        else -> 18.dp
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
        current.isParagraphBlock() && next.isParagraphBlock() -> 12.dp
        current.isHeadingBlock() && next.isParagraphBlock() -> 12.dp
        current.isParagraphBlock() && next.isHeadingBlock() -> 14.dp
        current.isHeadingBlock() && next.isHeadingBlock() -> 14.dp
        current.isMediaBlock() && next.isParagraphBlock() -> 4.dp
        current.isParagraphBlock() && next.isMediaBlock() -> 4.dp
        current is PostBodyBlock.HorizontalRuleBlock || next is PostBodyBlock.HorizontalRuleBlock -> 14.dp
        current.isStructuredBlock() || next.isStructuredBlock() -> 10.dp
        else -> 10.dp
    }
}

private fun PostBodyBlock.isHeadingBlock(): Boolean {
    return this is PostBodyBlock.HeadingBlock ||
        (this is PostBodyBlock.RichTextBlock && style != RichTextBlockStyle.Paragraph)
}

private fun PostBodyBlock.isParagraphBlock(): Boolean {
    return this is PostBodyBlock.ParagraphBlock ||
        (this is PostBodyBlock.RichTextBlock && style == RichTextBlockStyle.Paragraph)
}

private fun PostBodyBlock.isMediaBlock(): Boolean {
    return this is PostBodyBlock.ImageBlock ||
        this is PostBodyBlock.LightboxBlock ||
        this is PostBodyBlock.ImageGridBlock
}

private fun PostBodyBlock.isStructuredBlock(): Boolean {
    return this is PostBodyBlock.OneboxBlock ||
        this is PostBodyBlock.HorizontalRuleBlock ||
        this is PostBodyBlock.DetailsBlock ||
        this is PostBodyBlock.CalloutBlock ||
        this is PostBodyBlock.QuoteBlock ||
        this is PostBodyBlock.DiscourseQuoteBlock ||
        this is PostBodyBlock.CodeBlock ||
        this is PostBodyBlock.ListBlock
}

@Composable
private fun postBodyQuoteContainerColor() = lerp(
    start = NativeDoThemeExtras.colors.backgroundElevated,
    stop = NativeDoThemeExtras.colors.accentMuted,
    fraction = 0.46f,
).copy(alpha = 0.98f)

@Composable
private fun Int.toHeadingTextStyle() = when (this) {
    1 -> MaterialTheme.typography.headlineMedium
    2 -> MaterialTheme.typography.headlineSmall
    else -> MaterialTheme.typography.titleLarge
}

private val postBodyCodeBlockMaxHeight = 220.dp
