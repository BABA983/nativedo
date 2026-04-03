package io.github.baba983.nativedo.core.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras

@Composable
internal fun RenderPostBodyBlock(
    block: PostBodyBlock,
    cookieStore: CookieStore,
    baseUrl: String,
    onTopicClick: ((Long) -> Unit)?,
    onUserClick: ((String) -> Unit)?,
    onImageClick: ((PostBodyImage) -> Unit)?,
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
                onClick = onImageClick?.let { callback ->
                    { callback(block.image) }
                },
                modifier = modifier,
            )
        }

        is PostBodyBlock.ImageGridBlock -> {
            PostImageGrid(
                images = block.images,
                columns = block.columns,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                onImageClick = { image -> onImageClick?.invoke(image) },
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
                onImageClick = onImageClick,
                modifier = modifier,
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
    onImageClick: ((PostBodyImage) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
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
            .clip(MaterialTheme.shapes.large)
            .background(colors.glassBackground)
            .animateContentSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = if (expanded) "▾" else "▸",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textSecondary,
            )
            PostInlineTextBlock(
                inlines = block.summary.inlines,
                style = block.summary.style,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                modifier = Modifier.weight(1f),
            )
        }

        if (expanded && block.children.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                block.children.forEach { child ->
                    RenderPostBodyBlock(
                        block = child,
                        cookieStore = cookieStore,
                        baseUrl = baseUrl,
                        onTopicClick = onTopicClick,
                        onUserClick = onUserClick,
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
    onImageClick: ((PostBodyImage) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val accentColor = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val barWidth = 3.dp.toPx()
                drawRoundRect(
                    color = accentColor,
                    topLeft = Offset.Zero,
                    size = Size(barWidth, size.height),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                )
            }
            .padding(start = 15.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        block.children.forEach { child ->
            RenderPostBodyBlock(
                block = child,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
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
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        block.languageHint?.takeIf { it.isNotBlank() }?.let { language ->
            Text(
                text = language,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier.horizontalScroll(scrollState),
        ) {
            Text(
                text = block.code,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.onSurface,
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
    onImageClick: ((PostBodyImage) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        block.items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = if (block.ordered) "${index + 1}." else "•",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(20.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item.children.forEach { child ->
                        RenderPostBodyBlock(
                            block = child,
                            cookieStore = cookieStore,
                            baseUrl = baseUrl,
                            onTopicClick = onTopicClick,
                            onUserClick = onUserClick,
                            onImageClick = onImageClick,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
