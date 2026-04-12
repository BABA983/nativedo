package io.github.baba983.nativedo.core.ui.mediaviewer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
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
import io.github.baba983.nativedo.core.ui.PostBodyImage
import io.github.baba983.nativedo.core.ui.findMediaViewerPreviewBitmap
import io.github.baba983.nativedo.core.ui.rememberDiscourseBitmap
import io.github.baba983.nativedo.core.ui.resolvePostImageRenderRawUrl
import io.github.baba983.nativedo.core.ui.resolvePostImageUrl
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
    anchorBounds: IntRect?,
    windowSize: IntSize,
    actions: List<MediaViewerAction>,
    onDismissRequest: () -> Unit,
    onActionClick: (MediaViewerAction) -> Unit,
) {
    if (anchorBounds == null || windowSize == IntSize.Zero) {
        return
    }

    val density = LocalDensity.current
    val popupWidth = with(density) { mediaViewerPopupWidth.roundToPx() }
    val popupHeight = with(density) {
        (mediaViewerPopupVerticalPadding * 2 +
            mediaViewerPopupRowHeight * actions.size).roundToPx()
    }
    val popupMargin = with(density) { mediaViewerPopupMargin.roundToPx() }
    val popupSpacing = with(density) { mediaViewerPopupSpacing.roundToPx() }
    val popupOpenDuration = remember(actions.size) {
        mediaViewerPopupOpenDuration(actions.size)
    }
    val popupCornerRadiusPx = with(density) { mediaViewerPopupCornerRadius.toPx() }
    val popupBorderWidthPx = with(density) { mediaViewerPopupBorderWidth.toPx() }
    val popupCloseTranslationPx = with(density) {
        mediaViewerPopupCloseTranslation.toPx()
    }
    val transitionState = remember { MutableTransitionState(false) }

    LaunchedEffect(expanded) {
        transitionState.targetState = expanded
    }

    if (!transitionState.currentState && !transitionState.targetState) {
        return
    }

    BackHandler(enabled = expanded) {
        onDismissRequest()
    }

    val popupOffset = remember(anchorBounds, windowSize, popupWidth, popupHeight, popupMargin, popupSpacing) {
        calculateMediaViewerPopupOffset(
            anchorBounds = anchorBounds,
            windowSize = windowSize,
            popupSize = IntSize(width = popupWidth, height = popupHeight),
            margin = popupMargin,
            spacing = popupSpacing,
        )
    }
    val transition = updateTransition(transitionState, label = "viewerPopupTransition")
    val popupProgress = transition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = if (targetState) {
                    popupOpenDuration
                } else {
                    mediaViewerPopupCloseDurationMillis
                },
                easing = LinearOutSlowInEasing,
            )
        },
        label = "viewerPopupProgress",
    ) { visible ->
        if (visible) 1f else 0f
    }
    val containerTranslationY = transition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = mediaViewerPopupCloseDurationMillis,
                easing = LinearOutSlowInEasing,
            )
        },
        label = "viewerPopupTranslationY",
    ) { visible ->
        if (visible) 0f else -popupCloseTranslationPx
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (expanded || transition.currentState) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(onDismissRequest) {
                        detectTapGestures(onTap = { onDismissRequest() })
                    },
            )
        }

        Box(
            modifier = Modifier
                .offset { popupOffset }
                .graphicsLayer {
                    translationY = containerTranslationY.value
                }
                .width(mediaViewerPopupWidth)
                .drawWithContent {
                    val progress = popupProgress.value
                    val revealScale = max(progress, 0.001f)
                    val backgroundColor = Color(0xFF15171A).copy(alpha = 0.96f * progress)
                    val borderColor = Color.White.copy(alpha = 0.08f * progress)
                    withTransform({
                        scale(scaleX = 1f, scaleY = revealScale, pivot = androidx.compose.ui.geometry.Offset(size.width, 0f))
                    }) {
                        drawRoundRect(
                            color = backgroundColor,
                            cornerRadius = CornerRadius(popupCornerRadiusPx, popupCornerRadiusPx),
                        )
                        drawRoundRect(
                            color = borderColor,
                            cornerRadius = CornerRadius(popupCornerRadiusPx, popupCornerRadiusPx),
                            style = Stroke(width = popupBorderWidthPx),
                        )
                    }
                    clipRect(bottom = size.height * progress) {
                        this@drawWithContent.drawContent()
                    }
                },
        ) {
            Column(
                modifier = Modifier.padding(vertical = mediaViewerPopupVerticalPadding),
            ) {
                actions.forEachIndexed { index, action ->
                    ViewerActionPopupItem(
                        action = action,
                        index = index,
                        actionCount = actions.size,
                        popupProgress = popupProgress.value,
                        onClick = { onActionClick(action) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun ViewerActionPopupItem(
    action: MediaViewerAction,
    index: Int,
    actionCount: Int,
    popupProgress: Float,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val startTranslationPx = with(density) { mediaViewerPopupChildStartTranslation.toPx() }
    val rowProgress = remember(
        index,
        actionCount,
        popupProgress,
    ) {
        mediaViewerPopupCascadeProgress(
            fullAnimationT = popupProgress,
            position = index.toFloat(),
            count = actionCount.toFloat(),
            waveLength = mediaViewerPopupCascadeWaveLength,
        )
    }
    val rowAlpha = rowProgress
    val rowTranslationY = (1f - rowProgress) * -startTranslationPx

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(mediaViewerPopupRowHeight)
            .graphicsLayer {
                alpha = rowAlpha
                translationY = rowTranslationY
            }
            .background(
                color = if (pressed) {
                    Color.White.copy(alpha = 0.08f)
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = action.icon(),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.95f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = action.label(),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.96f),
        )
    }
}

// --- Popup constants ---
private val mediaViewerPopupWidth = 204.dp
private val mediaViewerPopupRowHeight = 48.dp
private val mediaViewerPopupVerticalPadding = 6.dp
private val mediaViewerPopupMargin = 12.dp
private val mediaViewerPopupSpacing = 8.dp
private val mediaViewerPopupCornerRadius = 18.dp
private val mediaViewerPopupBorderWidth = 1.dp
private val mediaViewerPopupChildStartTranslation = 6.dp
private val mediaViewerPopupCloseTranslation = 8.dp
private const val mediaViewerPopupCascadeWaveLength = 4f
private const val mediaViewerPopupCloseDurationMillis = 150

internal fun calculateMediaViewerPopupOffset(
    anchorBounds: IntRect,
    windowSize: IntSize,
    popupSize: IntSize,
    margin: Int,
    spacing: Int,
): IntOffset {
    val maxX = (windowSize.width - popupSize.width - margin).coerceAtLeast(margin)
    val maxY = (windowSize.height - popupSize.height - margin).coerceAtLeast(margin)
    val preferredX = anchorBounds.right - popupSize.width
    val preferredY = anchorBounds.bottom + spacing
    return IntOffset(
        x = preferredX.coerceIn(margin, maxX),
        y = preferredY.coerceIn(margin, maxY),
    )
}

internal fun mediaViewerPopupOpenDuration(
    itemCount: Int,
): Int = 150 + 16 * itemCount.coerceAtLeast(0)

internal fun mediaViewerPopupCascadeProgress(
    fullAnimationT: Float,
    position: Float,
    count: Float,
    waveLength: Float,
): Float {
    if (count <= 0f) {
        return fullAnimationT
    }
    val waveDuration = (1f / count) * min(waveLength, count)
    val waveOffset = (position / count) * (1f - waveDuration)
    return ((fullAnimationT - waveOffset) / waveDuration).coerceIn(0f, 1f)
}

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
