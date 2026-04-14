package io.github.baba983.nativedo.core.ui.mediaviewer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.core.ui.NativeDoContextualOptions
import io.github.baba983.nativedo.core.ui.NativeDoPopupPalette
import io.github.baba983.nativedo.core.ui.NativeDoPopupMenuItem
import io.github.baba983.nativedo.core.ui.PostBodyImage
import io.github.baba983.nativedo.core.ui.calculateNativeDoPopupOffset
import io.github.baba983.nativedo.core.ui.findMediaViewerPreviewBitmap
import io.github.baba983.nativedo.core.ui.nativeDoPopupCascadeProgress
import io.github.baba983.nativedo.core.ui.nativeDoPopupOpenDuration
import io.github.baba983.nativedo.core.ui.rememberDiscourseBitmap
import io.github.baba983.nativedo.core.ui.resolvePostImageRenderRawUrl
import io.github.baba983.nativedo.core.ui.resolvePostImageUrl
import io.github.baba983.nativedo.ui.theme.NativeDoGlassRole
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal enum class MediaViewerAction {
    Share,
    OpenExternal,
    SaveImage,
}

internal fun mediaViewerActions(item: MediaItem): List<MediaViewerAction> = when (item) {
    is MediaItem.Image -> listOf(
        MediaViewerAction.Share,
        MediaViewerAction.OpenExternal,
        MediaViewerAction.SaveImage,
    )
    is MediaItem.Video -> listOf(
        MediaViewerAction.Share,
        MediaViewerAction.OpenExternal,
    )
}

internal fun mediaViewerActions(): List<MediaViewerAction> = listOf(
    MediaViewerAction.Share,
    MediaViewerAction.OpenExternal,
    MediaViewerAction.SaveImage,
)

internal data class MediaViewerChromeText(
    val title: String,
    val subtitle: String?,
)

internal fun resolveMediaViewerChromeText(
    item: MediaItem,
    currentPage: Int,
    pageCount: Int,
    fallbackTitle: String = "Image",
): MediaViewerChromeText {
    val explicitTitle = when (item) {
        is MediaItem.Image -> item.title
        is MediaItem.Video -> null
    }.orNullIfBlank()
    val fallbackFileName = when (item) {
        is MediaItem.Image -> mediaViewerFileName(item.originalUrl ?: item.rawUrl)
        is MediaItem.Video -> mediaViewerFileName(item.url)
    }
    val title = explicitTitle ?: fallbackFileName ?: fallbackTitle
    val explicitSubtitle = when (item) {
        is MediaItem.Image -> item.subtitle
        is MediaItem.Video -> null
    }.orNullIfBlank()
    val pageLabel = if (pageCount > 1) {
        "${currentPage + 1}/$pageCount"
    } else {
        null
    }
    val subtitle = listOfNotNull(explicitSubtitle, pageLabel)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(separator = " | ")

    return MediaViewerChromeText(
        title = title,
        subtitle = subtitle,
    )
}

private fun String?.orNullIfBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun mediaViewerFileName(url: String): String? {
    return url.substringAfterLast('/')
        .substringBefore('?')
        .trim()
        .takeIf { it.isNotEmpty() }
}

@Composable
internal fun ViewerChromeButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String,
    alpha: Float = 1f,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .size(44.dp)
            .graphicsLayer { this.alpha = alpha },
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.34f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        IconButton(
            onClick = onClick,
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = Color.White,
            )
        }
    }
}

@Composable
internal fun ViewerCountPill(
    currentPage: Int,
    pageCount: Int,
    alpha: Float = 1f,
) {
    Surface(
        modifier = Modifier.graphicsLayer { this.alpha = alpha },
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.34f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            text = "$currentPage/$pageCount",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
internal fun ViewerTitleBlock(
    items: List<MediaItem>,
    currentPage: Int,
    targetPage: Int? = null,
    switchProgress: Float = 0f,
    alpha: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val resolvedAlpha = rememberUpdatedState(alpha)
    val clampedTargetPage = targetPage?.takeIf { it in items.indices && it != currentPage }
    val progress = switchProgress.coerceIn(0f, 1f)
    val currentText = resolveMediaViewerChromeText(
        item = items[currentPage],
        currentPage = currentPage,
        pageCount = items.size,
    )

    Box(
        modifier = modifier.graphicsLayer { this.alpha = resolvedAlpha.value },
        contentAlignment = Alignment.CenterStart,
    ) {
        ViewerTitleBlockContent(
            title = currentText.title,
            subtitle = currentText.subtitle,
            modifier = Modifier.graphicsLayer {
                this.alpha = if (clampedTargetPage == null) 1f else 1f - progress
                translationX = if (clampedTargetPage == null) {
                    0f
                } else if (clampedTargetPage > currentPage) {
                    -24f * progress
                } else {
                    24f * progress
                }
            },
        )
        clampedTargetPage?.let { page ->
            val targetText = resolveMediaViewerChromeText(
                item = items[page],
                currentPage = page,
                pageCount = items.size,
            )
            ViewerTitleBlockContent(
                title = targetText.title,
                subtitle = targetText.subtitle,
                modifier = Modifier.graphicsLayer {
                    this.alpha = progress
                    translationX = if (page > currentPage) {
                        24f * (1f - progress)
                    } else {
                        -24f * (1f - progress)
                    }
                },
            )
        }
    }
}

@Composable
private fun ViewerTitleBlockContent(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val hasSubtitle = subtitle != null
    val titleTranslationY = remember(hasSubtitle, density) {
        with(density) { if (hasSubtitle) (-4).dp.toPx() else 0f }
    }
    val titleScale = if (hasSubtitle) 0.92f else 1f

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayer {
                scaleX = titleScale
                scaleY = titleScale
                translationY = titleTranslationY
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            },
        )
        subtitle?.let { value ->
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun ViewerActionPopup(
    expanded: Boolean,
    actions: List<MediaViewerAction>,
    onDismissRequest: () -> Unit,
    onActionClick: (MediaViewerAction) -> Unit,
) {
    BackHandler(enabled = expanded) {
        onDismissRequest()
    }

    val popupGlass = NativeDoThemeExtras.glass(NativeDoGlassRole.Popup).copy(
        containerColor = Color(0xFF181A1E).copy(alpha = 0.96f),
        borderColor = Color.White.copy(alpha = 0.08f),
    )
    val popupPalette = remember(popupGlass) {
        NativeDoPopupPalette(
            text = Color.White.copy(alpha = 0.97f),
            secondaryText = Color.White.copy(alpha = 0.72f),
            icon = Color.White.copy(alpha = 0.92f),
            pressed = Color.White.copy(alpha = 0.18f),
            iconContainer = Color.White.copy(alpha = 0.14f),
        )
    }
    NativeDoContextualOptions(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        width = mediaViewerPopupWidth,
        itemCount = actions.size,
        margin = mediaViewerPopupMargin,
        spacing = mediaViewerPopupSpacing,
        closeTranslationY = mediaViewerPopupCloseTranslation,
        shape = RoundedCornerShape(mediaViewerPopupCornerRadius),
        surfaceTopInset = 12.dp,
        contentPadding = PaddingValues(vertical = mediaViewerPopupVerticalPadding),
        glassSpec = popupGlass,
    ) { popupProgress ->
        Column {
            actions.forEachIndexed { index, action ->
                NativeDoPopupMenuItem(
                    label = action.label(),
                    icon = action.icon(),
                    index = index,
                    itemCount = actions.size.coerceAtLeast(1),
                    popupProgress = popupProgress,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    glassSpec = popupGlass,
                    paletteOverride = popupPalette,
                    onClick = { onActionClick(action) },
                )
            }
        }
    }
}

// --- Popup constants ---
private val mediaViewerPopupWidth = 204.dp
private val mediaViewerPopupVerticalPadding = 6.dp
private val mediaViewerPopupMargin = 12.dp
private val mediaViewerPopupSpacing = 8.dp
private val mediaViewerPopupCornerRadius = 18.dp
private val mediaViewerPopupCloseTranslation = 8.dp

internal fun calculateMediaViewerPopupOffset(
    anchorBounds: IntRect,
    windowSize: IntSize,
    popupSize: IntSize,
    margin: Int,
    spacing: Int,
): IntOffset = calculateNativeDoPopupOffset(
    anchorBounds = anchorBounds,
    windowSize = windowSize,
    popupSize = popupSize,
    margin = margin,
    spacing = spacing,
)

internal fun mediaViewerPopupOpenDuration(
    itemCount: Int,
): Int = nativeDoPopupOpenDuration(
    itemCount = itemCount,
    baseDuration = 150,
    cascadeStepMillis = 16,
)

internal fun mediaViewerPopupCascadeProgress(
    fullAnimationT: Float,
    position: Float,
    count: Float,
    waveLength: Float,
): Float = nativeDoPopupCascadeProgress(
    fullAnimationT = fullAnimationT,
    position = position,
    count = count,
    waveLength = waveLength,
)

@Composable
internal fun MediaViewerAction.label(): String = when (this) {
    MediaViewerAction.Share -> stringResource(R.string.post_image_viewer_action_share)
    MediaViewerAction.OpenExternal -> stringResource(R.string.post_image_viewer_action_open_in_browser)
    MediaViewerAction.SaveImage -> stringResource(R.string.post_image_viewer_action_save_image)
}

internal fun MediaViewerAction.icon(): ImageVector = when (this) {
    MediaViewerAction.Share -> Icons.Outlined.Share
    MediaViewerAction.OpenExternal -> Icons.Outlined.OpenInBrowser
    MediaViewerAction.SaveImage -> Icons.Outlined.Download
}

internal fun calculateMediaViewerThumbnailWindow(
    currentPage: Int,
    pageCount: Int,
    maxVisible: Int,
): IntRange {
    if (pageCount <= 0 || maxVisible <= 0) {
        return IntRange.EMPTY
    }

    val visibleCount = min(pageCount, maxVisible)
    val half = visibleCount / 2
    val unclampedStart = currentPage - half
    val start = unclampedStart.coerceIn(0, pageCount - visibleCount)
    val end = start + visibleCount - 1
    return start..end
}

internal fun calculateMediaViewerThumbnailAnchorIndex(
    currentPage: Int,
    pageCount: Int,
    maxVisible: Int,
): Int {
    return calculateMediaViewerThumbnailWindow(
        currentPage = currentPage,
        pageCount = pageCount,
        maxVisible = maxVisible,
    ).first.coerceAtLeast(0)
}

private val mediaViewerThumbnailBaseWidth = 42.dp
private val mediaViewerThumbnailHeight = 56.dp
private val mediaViewerThumbnailSelectedMaxWidth = 64.dp
private val mediaViewerThumbnailCornerRadius = 2.dp
private val mediaViewerThumbnailSpacing = 1.dp
private const val mediaViewerThumbnailFallbackAspectRatio = 42f / 56f

internal fun resolveMediaViewerThumbnailTargetWidth(
    item: MediaItem,
    isSelected: Boolean,
    loadedBitmapSize: IntSize? = null,
): Dp {
    return resolveMediaViewerThumbnailTargetWidth(
        item = item,
        selectionProgress = if (isSelected) 1f else 0f,
        loadedBitmapSize = loadedBitmapSize,
    )
}

internal fun resolveMediaViewerThumbnailTargetWidth(
    item: MediaItem,
    selectionProgress: Float,
    loadedBitmapSize: IntSize? = null,
): Dp {
    val clampedProgress = selectionProgress.coerceIn(0f, 1f)
    if (clampedProgress <= 0f) {
        return mediaViewerThumbnailBaseWidth
    }

    val selectedWidth = resolveMediaViewerThumbnailAspectRatio(
        item = item,
        loadedBitmapSize = loadedBitmapSize,
    ).let { mediaViewerThumbnailHeight * it }
        .coerceIn(mediaViewerThumbnailBaseWidth, mediaViewerThumbnailSelectedMaxWidth)

    if (clampedProgress >= 1f) {
        return selectedWidth
    }

    return lerpDp(
        start = mediaViewerThumbnailBaseWidth,
        stop = selectedWidth,
        progress = clampedProgress,
    )
}

private fun resolveMediaViewerThumbnailAspectRatio(
    item: MediaItem,
    loadedBitmapSize: IntSize? = null,
): Float {
    val intrinsicRatio = when (item) {
        is MediaItem.Image -> {
            val width = item.width
            val height = item.height
            if (width != null && height != null && width > 0 && height > 0) {
                width.toFloat() / height.toFloat()
            } else {
                null
            }
        }

        is MediaItem.Video -> null
    }
    val bitmapRatio = loadedBitmapSize
        ?.takeIf { it.width > 0 && it.height > 0 }
        ?.let { it.width.toFloat() / it.height.toFloat() }

    return intrinsicRatio ?: bitmapRatio ?: mediaViewerThumbnailFallbackAspectRatio
}

internal fun resolveMediaViewerThumbnailSelectionProgress(
    itemIndex: Int,
    currentPage: Int,
    targetPage: Int?,
    switchProgress: Float,
): Float {
    val transitionProgress = switchProgress.coerceIn(0f, 1f)
    val resolvedTargetPage = targetPage ?: currentPage

    return when (itemIndex) {
        currentPage -> 1f - transitionProgress
        resolvedTargetPage -> transitionProgress
        else -> 0f
    }
}

internal fun calculateMediaViewerThumbnailStripOffset(
    itemWidthsPx: List<Float>,
    currentPage: Int,
    targetPage: Int?,
    switchProgress: Float,
    spacingPx: Float,
): Float {
    if (itemWidthsPx.isEmpty()) return 0f

    val clampedCurrentPage = currentPage.coerceIn(0, itemWidthsPx.lastIndex)
    val resolvedTargetPage = (targetPage ?: clampedCurrentPage)
        .coerceIn(0, itemWidthsPx.lastIndex)
    val progress = switchProgress.coerceIn(0f, 1f)

    val currentCenter = calculateMediaViewerThumbnailCenterPx(
        itemWidthsPx = itemWidthsPx,
        index = clampedCurrentPage,
        spacingPx = spacingPx,
    )
    if (resolvedTargetPage == clampedCurrentPage || progress == 0f) {
        return currentCenter
    }

    val targetCenter = calculateMediaViewerThumbnailCenterPx(
        itemWidthsPx = itemWidthsPx,
        index = resolvedTargetPage,
        spacingPx = spacingPx,
    )
    return lerpFloat(currentCenter, targetCenter, progress)
}

internal fun calculateMediaViewerThumbnailStripScrollTarget(
    itemWidthsPx: List<Float>,
    currentPage: Int,
    targetPage: Int?,
    switchProgress: Float,
    spacingPx: Float,
    contentPaddingPx: Float,
    viewportWidthPx: Float,
): Float {
    val centerPx = calculateMediaViewerThumbnailStripOffset(
        itemWidthsPx = itemWidthsPx,
        currentPage = currentPage,
        targetPage = targetPage,
        switchProgress = switchProgress,
        spacingPx = spacingPx,
    )
    return (centerPx + contentPaddingPx - (viewportWidthPx / 2f)).coerceAtLeast(0f)
}

internal fun calculateMediaViewerThumbnailStripScrollPosition(
    itemWidthsPx: List<Float>,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    spacingPx: Float,
): Float {
    if (itemWidthsPx.isEmpty()) return 0f
    val clampedIndex = firstVisibleItemIndex.coerceIn(0, itemWidthsPx.size)
    val leadingWidth = itemWidthsPx
        .take(clampedIndex)
        .sum() + spacingPx * clampedIndex
    return (leadingWidth - firstVisibleItemScrollOffset).coerceAtLeast(0f)
}

private fun calculateMediaViewerThumbnailCenterPx(
    itemWidthsPx: List<Float>,
    index: Int,
    spacingPx: Float,
): Float {
    val clampedIndex = index.coerceIn(0, itemWidthsPx.lastIndex)
    val leadingWidth = itemWidthsPx
        .take(clampedIndex)
        .sum() + spacingPx * clampedIndex
    return leadingWidth + itemWidthsPx[clampedIndex] / 2f
}

internal fun calculateMediaViewerFilmstripCenterOffset(
    currentPage: Int,
    targetPage: Int?,
    switchProgress: Float,
    baseItemWidthPx: Float,
    spacingPx: Float,
): Float {
    val resolvedTargetPage = targetPage ?: currentPage
    val anchoredPage = lerpFloat(
        start = currentPage.toFloat(),
        stop = resolvedTargetPage.toFloat(),
        progress = switchProgress.coerceIn(0f, 1f),
    )
    return -anchoredPage * (baseItemWidthPx + spacingPx)
}

internal fun calculateMediaViewerFilmstripItemLeft(
    itemIndex: Int,
    currentPage: Int,
    targetPage: Int?,
    switchProgress: Float,
    baseItemWidthPx: Float,
    itemWidthPx: Float,
    spacingPx: Float,
): Float {
    return itemIndex * (baseItemWidthPx + spacingPx) +
        (baseItemWidthPx - itemWidthPx) / 2f
}

internal fun calculateMediaViewerFilmstripWindow(
    currentPage: Int,
    targetPage: Int?,
    pageCount: Int,
    extraBuffer: Int,
): IntRange {
    if (pageCount <= 0) return IntRange.EMPTY

    val resolvedTargetPage = targetPage ?: currentPage
    val start = min(currentPage, resolvedTargetPage)
        .minus(extraBuffer.coerceAtLeast(0))
        .coerceAtLeast(0)
    val end = max(currentPage, resolvedTargetPage)
        .plus(extraBuffer.coerceAtLeast(0))
        .coerceAtMost(pageCount - 1)
    return start..end
}

internal data class MediaViewerFilmstripItemLayout(
    val itemIndex: Int,
    val leftPx: Float,
    val widthPx: Float,
)

internal fun calculateMediaViewerFilmstripItemLayouts(
    currentPage: Int,
    targetPage: Int?,
    switchProgress: Float,
    itemWidthsPx: List<Float>,
    baseItemWidthPx: Float,
    spacingPx: Float,
    viewportWidthPx: Float,
): List<MediaViewerFilmstripItemLayout> {
    if (itemWidthsPx.isEmpty()) return emptyList()

    val clampedCurrentPage = currentPage.coerceIn(0, itemWidthsPx.lastIndex)
    val resolvedTargetPage = (targetPage ?: clampedCurrentPage)
        .coerceIn(0, itemWidthsPx.lastIndex)
    val progress = switchProgress.coerceIn(0f, 1f)
    val currentWidth = itemWidthsPx[clampedCurrentPage]

    if (resolvedTargetPage == clampedCurrentPage || progress == 0f) {
        val currentLeft = (viewportWidthPx - currentWidth) / 2f
        return itemWidthsPx.indices.map { index ->
            val widthPx = itemWidthsPx[index]
            val leftPx = when {
                index < clampedCurrentPage -> {
                    currentLeft + (index - clampedCurrentPage) * (baseItemWidthPx + spacingPx)
                }

                index == clampedCurrentPage -> currentLeft
                else -> {
                    currentLeft + currentWidth + spacingPx +
                        (index - clampedCurrentPage - 1) * (baseItemWidthPx + spacingPx)
                }
            }
            MediaViewerFilmstripItemLayout(
                itemIndex = index,
                leftPx = leftPx,
                widthPx = widthPx,
            )
        }
    }

    val targetWidth = itemWidthsPx[resolvedTargetPage]
    val currentStartLeft = (viewportWidthPx - currentWidth) / 2f
    val currentEndLeft = if (resolvedTargetPage > clampedCurrentPage) {
        (viewportWidthPx - targetWidth) / 2f - baseItemWidthPx - spacingPx
    } else {
        (viewportWidthPx - targetWidth) / 2f + targetWidth + spacingPx
    }
    val currentLeft = lerpFloat(
        start = currentStartLeft,
        stop = currentEndLeft,
        progress = progress,
    )
    val targetLeft = if (resolvedTargetPage > clampedCurrentPage) {
        currentLeft + currentWidth + spacingPx
    } else {
        currentLeft - spacingPx - targetWidth
    }

    return itemWidthsPx.indices.map { index ->
        val widthPx = itemWidthsPx[index]
        val leftPx = when {
            index < min(clampedCurrentPage, resolvedTargetPage) -> {
                if (resolvedTargetPage > clampedCurrentPage) {
                    currentLeft + (index - clampedCurrentPage) * (baseItemWidthPx + spacingPx)
                } else {
                    targetLeft + (index - resolvedTargetPage) * (baseItemWidthPx + spacingPx)
                }
            }

            index == resolvedTargetPage -> targetLeft
            index == clampedCurrentPage -> currentLeft
            index > max(clampedCurrentPage, resolvedTargetPage) -> {
                if (resolvedTargetPage > clampedCurrentPage) {
                    targetLeft + targetWidth + spacingPx +
                        (index - resolvedTargetPage - 1) * (baseItemWidthPx + spacingPx)
                } else {
                    currentLeft + currentWidth + spacingPx +
                        (index - clampedCurrentPage - 1) * (baseItemWidthPx + spacingPx)
                }
            }

            resolvedTargetPage > clampedCurrentPage -> {
                targetLeft - (resolvedTargetPage - index) * (baseItemWidthPx + spacingPx)
            }

            else -> {
                targetLeft + targetWidth + spacingPx +
                    (index - resolvedTargetPage - 1) * (baseItemWidthPx + spacingPx)
            }
        }

        MediaViewerFilmstripItemLayout(
            itemIndex = index,
            leftPx = leftPx,
            widthPx = widthPx,
        )
    }
}

private fun lerpDp(
    start: Dp,
    stop: Dp,
    progress: Float,
): Dp = start + (stop - start) * progress.coerceIn(0f, 1f)

private fun Dp.coerceIn(
    minimumValue: Dp,
    maximumValue: Dp,
): Dp = value.coerceIn(minimumValue.value, maximumValue.value).dp

@Composable
internal fun MediaViewerThumbnailStrip(
    items: List<MediaItem>,
    currentPage: Int,
    targetPage: Int?,
    switchProgress: Float,
    cookieStore: CookieStore,
    baseUrl: String,
    alpha: Float,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.size <= 1 || alpha <= 0f) {
        return
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .clipToBounds()
            .height(mediaViewerThumbnailHeight),
    ) {
        val density = LocalDensity.current
        val spacingPx = with(density) { mediaViewerThumbnailSpacing.toPx() }
        val baseWidthPx = with(density) { mediaViewerThumbnailBaseWidth.toPx() }
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val visibleBuffer = remember(viewportWidthPx, baseWidthPx, spacingPx) {
            ceil(viewportWidthPx / (baseWidthPx + spacingPx)).toInt() / 2 + 2
        }
        val visibleRange = remember(
            currentPage,
            targetPage,
            items.size,
            visibleBuffer,
        ) {
            calculateMediaViewerFilmstripWindow(
                currentPage = currentPage,
                targetPage = targetPage,
                pageCount = items.size,
                extraBuffer = visibleBuffer,
            )
        }
        val itemWidthsPx = remember(
            items,
            currentPage,
            targetPage,
            switchProgress,
            density,
        ) {
            items.mapIndexed { index, item ->
                val selectionProgress = resolveMediaViewerThumbnailSelectionProgress(
                    itemIndex = index,
                    currentPage = currentPage,
                    targetPage = targetPage,
                    switchProgress = switchProgress,
                )
                with(density) {
                    resolveMediaViewerThumbnailTargetWidth(
                        item = item,
                        selectionProgress = selectionProgress,
                    ).toPx()
                }
            }
        }
        val itemLayouts = remember(
            currentPage,
            targetPage,
            switchProgress,
            itemWidthsPx,
            baseWidthPx,
            spacingPx,
            viewportWidthPx,
        ) {
            calculateMediaViewerFilmstripItemLayouts(
                currentPage = currentPage,
                targetPage = targetPage,
                switchProgress = switchProgress,
                itemWidthsPx = itemWidthsPx,
                baseItemWidthPx = baseWidthPx,
                spacingPx = spacingPx,
                viewportWidthPx = viewportWidthPx,
            ).associateBy { it.itemIndex }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            visibleRange.forEach { index ->
                val item = items[index]
                val selectionProgress = resolveMediaViewerThumbnailSelectionProgress(
                    itemIndex = index,
                    currentPage = currentPage,
                    targetPage = targetPage,
                    switchProgress = switchProgress,
                )
                val targetWidth = resolveMediaViewerThumbnailTargetWidth(
                    item = item,
                    selectionProgress = selectionProgress,
                )
                val itemLeftPx = itemLayouts.getValue(index).leftPx
                key(index, item.cacheKey) {
                    MediaViewerThumbnailItem(
                        item = item,
                        selectionProgress = selectionProgress,
                        cookieStore = cookieStore,
                        baseUrl = baseUrl,
                        onClick = {
                            onItemClick(index)
                        },
                        modifier = Modifier.offset {
                            IntOffset(
                                x = itemLeftPx.roundToInt(),
                                y = 0,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaViewerThumbnailItem(
    item: MediaItem,
    selectionProgress: Float,
    cookieStore: CookieStore,
    baseUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewerImage = remember(item) {
        when (item) {
            is MediaItem.Image -> item.toPostBodyImage()
            is MediaItem.Video -> null
        }
    }
    val previewBitmap = remember(viewerImage) {
        viewerImage?.let(::findMediaViewerPreviewBitmap)
    }
    val resolvedUrl = remember(item, viewerImage, baseUrl) {
        when (item) {
            is MediaItem.Image -> {
                val renderImage = viewerImage ?: PostBodyImage(
                    rawUrl = item.rawUrl,
                    originalUrl = item.originalUrl,
                    width = item.width,
                    height = item.height,
                )
                resolvePostImageUrl(
                    baseUrl = baseUrl,
                    rawUrl = resolvePostImageRenderRawUrl(renderImage),
                )
            }
            is MediaItem.Video -> null
        }
    }
    val bitmap = if (resolvedUrl == null) {
        previewBitmap
    } else {
        val loadedBitmap by rememberDiscourseBitmap(
            resolvedUrl = resolvedUrl,
            cookieStore = cookieStore,
            baseUrl = baseUrl,
            previewBitmap = previewBitmap,
        )
        loadedBitmap
    }
    val targetWidth = remember(item, bitmap, selectionProgress) {
        resolveMediaViewerThumbnailTargetWidth(
            item = item,
            selectionProgress = selectionProgress,
            loadedBitmapSize = bitmap?.let { IntSize(it.width, it.height) },
        )
    }
    val thumbnailAlpha = lerpFloat(0.72f, 1f, selectionProgress)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(width = targetWidth, height = mediaViewerThumbnailHeight)
            .zIndex(selectionProgress)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = thumbnailAlpha
                }
                .clip(RoundedCornerShape(mediaViewerThumbnailCornerRadius)),
            contentAlignment = Alignment.Center,
        ) {
            bitmap?.let { loaded ->
                androidx.compose.foundation.Image(
                    bitmap = loaded.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(mediaViewerThumbnailCornerRadius)),
                )
            }
        }
    }
}
