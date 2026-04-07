package io.github.baba983.nativedo.core.ui

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MoreVert
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.network.addImageRequestHeaders
import io.github.baba983.nativedo.core.storage.CookieStore
import java.net.URLConnection
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.util.IdentityHashMap

internal data class PostImageViewerState(
    val images: List<PostBodyImage>,
    val initialIndex: Int,
    val launchSource: PostImageViewerLaunchSource? = null,
    val imageLaunchSources: Map<PostBodyImage, PostImageViewerLaunchSource> = emptyMap(),
)

internal data class PostImageViewerLaunchSource(
    val boundsOnScreen: IntRect,
    val fullBoundsOnScreen: IntRect = boundsOnScreen,
    val cornerRadiusPx: Float = 0f,
)

internal data class PostImageViewerEnterTransform(
    val scaleX: Float,
    val scaleY: Float,
    val translationX: Float,
    val translationY: Float,
)

internal data class PostImageViewerPinchTransform(
    val scale: Float,
    val offset: Offset,
)

internal data class PostImageViewerClipInsets(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
)

internal data class PostImageViewerContentMask(
    val visibleBounds: Rect,
    val roundedBounds: Rect,
    val cornerRadiusPx: Float,
)

private data class ActivePostImageViewerPinch(
    val startDistance: Float,
    val startScale: Float,
    val startOffset: Offset,
    val center: Offset,
)

private data class PostImageViewerCloseSnapshot(
    val page: Int,
    val contentSize: IntSize,
    val containerBoundsOnScreen: IntRect,
    val bitmap: Bitmap?,
    val scale: Float,
    val translation: Offset,
)

internal enum class PostImageViewerAction {
    Share,
    OpenExternal,
    SaveImage,
}

internal fun collectGalleryImages(blocks: List<PostBodyBlock>): List<PostBodyImage> {
    return buildList {
        blocks.forEach { block ->
            addBlockImages(block)
        }
    }
}

internal fun findGalleryStartIndex(
    galleryImages: List<PostBodyImage>,
    clickedImage: PostBodyImage,
): Int {
    val clickedKey = clickedImage.viewerCacheKey()
    val index = galleryImages.indexOfFirst { image ->
        image.viewerCacheKey() == clickedKey
    }
    return index.takeIf { it >= 0 } ?: 0
}

internal fun buildPostImageViewerImageIndexLookup(
    images: List<PostBodyImage>,
): IdentityHashMap<PostBodyImage, Int> {
    return IdentityHashMap<PostBodyImage, Int>(images.size).apply {
        images.forEachIndexed { index, image ->
            put(image, index)
        }
    }
}

internal fun resolvePostImageViewerDismissSource(
    state: PostImageViewerState,
    currentPage: Int,
): PostImageViewerLaunchSource? {
    val currentImage = state.images.getOrNull(currentPage)
    return when {
        currentImage != null && state.imageLaunchSources[currentImage] != null -> {
            state.imageLaunchSources[currentImage]
        }

        currentPage == state.initialIndex -> state.launchSource
        else -> null
    }
}

internal fun postImageViewerActions(): List<PostImageViewerAction> = listOf(
    PostImageViewerAction.Share,
    PostImageViewerAction.OpenExternal,
    PostImageViewerAction.SaveImage,
)

internal fun postImageViewerDialogProperties(): DialogProperties = DialogProperties(
    usePlatformDefaultWidth = false,
    decorFitsSystemWindows = false,
)

internal fun postImageViewerBaseScrimAlpha(
    dismissProgress: Float,
): Float {
    return (postImageViewerMaxScrimAlpha -
        dismissProgress.coerceIn(0f, 1f) * postImageViewerDismissScrimAlphaReduction)
        .coerceIn(0f, postImageViewerMaxScrimAlpha)
}

internal fun postImageViewerOpenScrimAlpha(
    enterProgress: Float,
    dismissProgress: Float,
): Float {
    return postImageViewerBaseScrimAlpha(dismissProgress) * enterProgress.coerceIn(0f, 1f)
}

internal fun shouldDelayPostImageViewerThumbnailHide(
    hasLaunchSource: Boolean,
    hasPlayedEnterAnimation: Boolean,
    hasContentContainerBoundsOnScreen: Boolean,
    currentPage: Int,
    initialPage: Int,
): Boolean {
    return hasLaunchSource &&
        !hasPlayedEnterAnimation &&
        (!hasContentContainerBoundsOnScreen || currentPage != initialPage)
}

internal fun updatePostImageViewerPagerGestureLock(
    wasLocked: Boolean,
    pressedPointerCount: Int,
): Boolean {
    return when {
        pressedPointerCount > 1 -> true
        pressedPointerCount == 0 -> false
        else -> wasLocked
    }
}

internal fun shouldEnablePostImageViewerPagerScroll(
    isTransitionAnimating: Boolean,
    scale: Float,
    dismissOffsetY: Float,
    isPagerGestureLocked: Boolean,
): Boolean {
    return !isTransitionAnimating &&
        scale <= 1f &&
        dismissOffsetY == 0f &&
        !isPagerGestureLocked
}

internal fun shouldAnimatePostImageViewerDismissToSource(
    dismissSource: PostImageViewerLaunchSource,
    viewportBoundsOnScreen: IntRect,
): Boolean {
    return dismissSource.boundsOnScreen.left < viewportBoundsOnScreen.right &&
        dismissSource.boundsOnScreen.right > viewportBoundsOnScreen.left &&
        dismissSource.boundsOnScreen.top < viewportBoundsOnScreen.bottom &&
        dismissSource.boundsOnScreen.bottom > viewportBoundsOnScreen.top
}

internal fun postImageViewerCloseContentAlpha(
    progress: Float,
): Float {
    return (1f - progress.coerceIn(0f, 1f) / postImageViewerCloseContentFadeOutFraction)
        .coerceIn(0f, 1f)
}

internal fun shouldRenderPostImageViewerCloseOverlay(
    isClosing: Boolean,
    hasSnapshotBitmap: Boolean,
): Boolean = isClosing && hasSnapshotBitmap

private suspend fun animatePostImageViewerClose(
    enterProgress: Animatable<Float, *>,
    closeScrimAlpha: Animatable<Float, *>,
) {
    enterProgress.snapTo(1f)
    coroutineScope {
        launch {
            enterProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = postImageViewerCloseDurationMillis,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
        launch {
            closeScrimAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = postImageViewerCloseDurationMillis,
                    easing = LinearOutSlowInEasing,
                ),
            )
        }
    }
}

internal fun resolvePostImageViewerRenderedScale(
    displayedScale: Float,
    openTransform: PostImageViewerEnterTransform?,
): Float = displayedScale * (openTransform?.scaleX ?: 1f)

internal fun resolvePostImageViewerRenderedTranslation(
    activeOffset: Offset,
    activeDismissOffsetY: Float,
    openTransform: PostImageViewerEnterTransform?,
): Offset = Offset(
    x = activeOffset.x + (openTransform?.translationX ?: 0f),
    y = activeOffset.y + activeDismissOffsetY + (openTransform?.translationY ?: 0f),
)

internal fun calculatePostImageViewerWindowDismissTransform(
    currentScale: Float,
    currentTranslation: Offset,
    viewportHeight: Int,
    progress: Float,
): PostImageViewerEnterTransform {
    val targetTranslationY = if (currentTranslation.y >= 0f) {
        viewportHeight.toFloat()
    } else {
        -viewportHeight.toFloat()
    }
    return PostImageViewerEnterTransform(
        scaleX = currentScale,
        scaleY = currentScale,
        translationX = currentTranslation.x,
        translationY = lerpPostImageViewerFloat(
            currentTranslation.y,
            targetTranslationY,
            progress.coerceIn(0f, 1f),
        ),
    )
}

internal fun togglePostImageViewerScale(
    currentScale: Float,
    zoomedScale: Float = postImageViewerZoomedScale,
): Float {
    return if (currentScale > 1f) {
        1f
    } else {
        zoomedScale
    }
}

internal fun postImageViewerDoubleTapTargetOffset(
    tapOffset: Offset,
    currentScale: Float,
    targetScale: Float,
    currentOffset: Offset,
    containerSize: IntSize,
    contentSize: IntSize,
): Offset {
    if (targetScale <= 1f) {
        return Offset.Zero
    }

    val containerCenterX = containerSize.width / 2f
    val containerCenterY = containerSize.height / 2f
    val relativeTapX = tapOffset.x - containerCenterX
    val relativeTapY = tapOffset.y - containerCenterY
    val scaleFactor = targetScale / currentScale
    val proposed = Offset(
        x = relativeTapX - (relativeTapX - currentOffset.x) * scaleFactor,
        y = relativeTapY - (relativeTapY - currentOffset.y) * scaleFactor,
    )

    return clampPostImageViewerOffset(
        proposed = proposed,
        scale = targetScale,
        containerSize = containerSize,
        contentSize = contentSize,
    )
}

internal fun isPostImageViewerTapInsideContent(
    tapOffset: Offset,
    containerSize: IntSize,
    contentSize: IntSize,
): Boolean {
    val left = (containerSize.width - contentSize.width) / 2f
    val top = (containerSize.height - contentSize.height) / 2f
    val right = left + contentSize.width
    val bottom = top + contentSize.height
    return tapOffset.x in left..right && tapOffset.y in top..bottom
}

internal fun clampPostImageViewerOffset(
    proposed: Offset,
    scale: Float,
    containerSize: IntSize,
    contentSize: IntSize,
    extraSlack: Float = 0f,
): Offset {
    if (scale <= 1f) {
        return Offset.Zero
    }

    val maxX = ((contentSize.width * scale) - containerSize.width)
        .coerceAtLeast(0f) / 2f + extraSlack
    val maxY = ((contentSize.height * scale) - containerSize.height)
        .coerceAtLeast(0f) / 2f + extraSlack

    return Offset(
        x = proposed.x.coerceIn(-maxX, maxX),
        y = proposed.y.coerceIn(-maxY, maxY),
    )
}

internal fun settlePostImageViewerScale(
    currentScale: Float,
): Float = currentScale.coerceIn(1f, postImageViewerMaxScale)

internal fun calculatePostImageViewerPinchTransform(
    pinchStartDistance: Float,
    currentDistance: Float,
    pinchStartScale: Float,
    pinchStartOffset: Offset,
    pinchCenter: Offset,
    containerSize: IntSize,
    contentSize: IntSize,
): PostImageViewerPinchTransform {
    val nextScale = if (pinchStartDistance > 0f && currentDistance > 0f) {
        (pinchStartScale * currentDistance / pinchStartDistance)
            .coerceIn(postImageViewerMinInteractiveScale, postImageViewerMaxInteractiveScale)
    } else {
        pinchStartScale.coerceIn(postImageViewerMinInteractiveScale, postImageViewerMaxInteractiveScale)
    }
    val scaleFactor = nextScale / pinchStartScale.coerceAtLeast(0.0001f)
    val containerCenterX = containerSize.width / 2f
    val containerCenterY = containerSize.height / 2f
    val relativePinchCenterX = pinchCenter.x - containerCenterX
    val relativePinchCenterY = pinchCenter.y - containerCenterY
    val proposedOffset = Offset(
        x = relativePinchCenterX -
            (relativePinchCenterX - pinchStartOffset.x) * scaleFactor,
        y = relativePinchCenterY -
            (relativePinchCenterY - pinchStartOffset.y) * scaleFactor,
    )

    return PostImageViewerPinchTransform(
        scale = nextScale,
        offset = clampPostImageViewerOffset(
            proposed = proposedOffset,
            scale = nextScale,
            containerSize = containerSize,
            contentSize = contentSize,
            extraSlack = postImageViewerPanSlackPx,
        ),
    )
}

internal fun shouldDismissPostImageViewer(
    verticalOffset: Float,
    velocityY: Float,
    dismissDistance: Float,
): Boolean {
    return verticalOffset >= dismissDistance ||
        velocityY >= postImageViewerDismissVelocityThreshold
}

internal fun postImageViewerDismissedScale(
    progress: Float,
): Float = 1f - progress.coerceIn(0f, 1f) * 0.12f

internal fun calculatePostImageViewerTargetRect(
    windowSize: IntSize,
    contentSize: IntSize,
): IntRect {
    return calculatePostImageViewerTargetRectInContainer(
        containerBounds = IntRect(
            left = 0,
            top = 0,
            right = windowSize.width.coerceAtLeast(0),
            bottom = windowSize.height.coerceAtLeast(0),
        ),
        contentSize = contentSize,
    )
}

internal fun calculatePostImageViewerTargetRectInContainer(
    containerBounds: IntRect,
    contentSize: IntSize,
): IntRect {
    val targetWidth = contentSize.width.coerceAtLeast(0)
    val targetHeight = contentSize.height.coerceAtLeast(0)
    val left = containerBounds.left + ((containerBounds.width - targetWidth) / 2).coerceAtLeast(0)
    val top = containerBounds.top + ((containerBounds.height - targetHeight) / 2).coerceAtLeast(0)
    return IntRect(
        left = left,
        top = top,
        right = left + targetWidth,
        bottom = top + targetHeight,
    )
}

internal fun calculatePostImageViewerEnterTransform(
    sourceBounds: IntRect,
    targetBounds: IntRect,
    progress: Float,
): PostImageViewerEnterTransform {
    val normalizedProgress = progress.coerceIn(0f, 1f)
    val startScaleX = if (targetBounds.width > 0) {
        sourceBounds.width.toFloat() / targetBounds.width.toFloat()
    } else {
        1f
    }
    val startScaleY = if (targetBounds.height > 0) {
        sourceBounds.height.toFloat() / targetBounds.height.toFloat()
    } else {
        1f
    }
    val sourceCenterX = (sourceBounds.left + sourceBounds.right) / 2f
    val sourceCenterY = (sourceBounds.top + sourceBounds.bottom) / 2f
    val targetCenterX = (targetBounds.left + targetBounds.right) / 2f
    val targetCenterY = (targetBounds.top + targetBounds.bottom) / 2f
    return PostImageViewerEnterTransform(
        scaleX = lerpPostImageViewerFloat(startScaleX, 1f, normalizedProgress),
        scaleY = lerpPostImageViewerFloat(startScaleY, 1f, normalizedProgress),
        translationX = lerpPostImageViewerFloat(sourceCenterX - targetCenterX, 0f, normalizedProgress),
        translationY = lerpPostImageViewerFloat(sourceCenterY - targetCenterY, 0f, normalizedProgress),
    )
}

internal fun calculatePostImageViewerExitTransform(
    sourceBounds: IntRect,
    targetBounds: IntRect,
    currentScale: Float,
    currentTranslation: Offset,
    progress: Float,
): PostImageViewerEnterTransform {
    val normalizedProgress = progress.coerceIn(0f, 1f)
    val thumbnailTransform = calculatePostImageViewerEnterTransform(
        sourceBounds = sourceBounds,
        targetBounds = targetBounds,
        progress = 0f,
    )
    return PostImageViewerEnterTransform(
        scaleX = lerpPostImageViewerFloat(currentScale, thumbnailTransform.scaleX, normalizedProgress),
        scaleY = lerpPostImageViewerFloat(currentScale, thumbnailTransform.scaleY, normalizedProgress),
        translationX = lerpPostImageViewerFloat(
            currentTranslation.x,
            thumbnailTransform.translationX,
            normalizedProgress,
        ),
        translationY = lerpPostImageViewerFloat(
            currentTranslation.y,
            thumbnailTransform.translationY,
            normalizedProgress,
        ),
    )
}

internal fun calculatePostImageViewerSourceClipInsets(
    sourceVisibleBounds: IntRect,
    sourceFullBounds: IntRect,
    targetBounds: IntRect,
): PostImageViewerClipInsets {
    if (
        sourceFullBounds.width <= 0 ||
        sourceFullBounds.height <= 0 ||
        targetBounds.width <= 0 ||
        targetBounds.height <= 0
    ) {
        return PostImageViewerClipInsets()
    }

    val widthScale = targetBounds.width.toFloat() / sourceFullBounds.width.toFloat()
    val heightScale = targetBounds.height.toFloat() / sourceFullBounds.height.toFloat()
    return PostImageViewerClipInsets(
        left = (sourceVisibleBounds.left - sourceFullBounds.left)
            .coerceAtLeast(0)
            .toFloat() * widthScale,
        top = (sourceVisibleBounds.top - sourceFullBounds.top)
            .coerceAtLeast(0)
            .toFloat() * heightScale,
        right = (sourceFullBounds.right - sourceVisibleBounds.right)
            .coerceAtLeast(0)
            .toFloat() * widthScale,
        bottom = (sourceFullBounds.bottom - sourceVisibleBounds.bottom)
            .coerceAtLeast(0)
            .toFloat() * heightScale,
    )
}

internal fun calculatePostImageViewerSourceCornerRadius(
    sourceCornerRadiusPx: Float,
    sourceFullBounds: IntRect,
    targetBounds: IntRect,
): Float {
    if (
        sourceCornerRadiusPx <= 0f ||
        sourceFullBounds.width <= 0 ||
        sourceFullBounds.height <= 0 ||
        targetBounds.width <= 0 ||
        targetBounds.height <= 0
    ) {
        return 0f
    }

    val widthScale = targetBounds.width.toFloat() / sourceFullBounds.width.toFloat()
    val heightScale = targetBounds.height.toFloat() / sourceFullBounds.height.toFloat()
    return sourceCornerRadiusPx * min(widthScale, heightScale)
}

internal fun lerpPostImageViewerClipInsets(
    start: PostImageViewerClipInsets,
    end: PostImageViewerClipInsets,
    progress: Float,
): PostImageViewerClipInsets {
    val t = progress.coerceIn(0f, 1f)
    return PostImageViewerClipInsets(
        left = lerpPostImageViewerFloat(start.left, end.left, t),
        top = lerpPostImageViewerFloat(start.top, end.top, t),
        right = lerpPostImageViewerFloat(start.right, end.right, t),
        bottom = lerpPostImageViewerFloat(start.bottom, end.bottom, t),
    )
}

internal fun calculatePostImageViewerContentMask(
    contentWidth: Float,
    contentHeight: Float,
    clipInsets: PostImageViewerClipInsets? = null,
    cornerRadiusPx: Float = 0f,
): PostImageViewerContentMask? {
    if (contentWidth <= 0f || contentHeight <= 0f) {
        return null
    }

    val insets = clipInsets ?: PostImageViewerClipInsets()
    val visibleLeft = insets.left.coerceIn(0f, contentWidth)
    val visibleTop = insets.top.coerceIn(0f, contentHeight)
    val visibleRight = (contentWidth - insets.right).coerceIn(visibleLeft, contentWidth)
    val visibleBottom = (contentHeight - insets.bottom).coerceIn(visibleTop, contentHeight)
    val radius = cornerRadiusPx
        .coerceAtLeast(0f)
        .coerceAtMost(min(contentWidth / 2f, contentHeight / 2f))

    return PostImageViewerContentMask(
        visibleBounds = Rect(
            left = visibleLeft,
            top = visibleTop,
            right = visibleRight,
            bottom = visibleBottom,
        ),
        roundedBounds = Rect(
            left = 0f,
            top = 0f,
            right = contentWidth,
            bottom = contentHeight,
        ),
        cornerRadiusPx = radius,
    )
}

internal fun resolvePostImageViewerAspectRatio(
    image: PostBodyImage,
    loadedBitmapSize: IntSize? = null,
    previewBitmapSize: IntSize? = null,
): Float {
    return if (
        image.width != null &&
        image.height != null &&
        image.width > 0 &&
        image.height > 0
    ) {
        image.width.toFloat() / image.height.toFloat()
    } else if (loadedBitmapSize != null && loadedBitmapSize.width > 0 && loadedBitmapSize.height > 0) {
        loadedBitmapSize.width.toFloat() / loadedBitmapSize.height.toFloat()
    } else if (previewBitmapSize != null && previewBitmapSize.width > 0 && previewBitmapSize.height > 0) {
        previewBitmapSize.width.toFloat() / previewBitmapSize.height.toFloat()
    } else {
        4f / 3f
    }
}

internal fun buildPostImageViewerRequestHeaders(
    resolvedUrl: String,
    cookieStore: CookieStore,
    baseUrl: String,
): Map<String, String> {
    val imageUrl = resolvedUrl.toHttpUrl()
    val requestBuilder = Request.Builder()
        .url(imageUrl)
        .apply { addImageRequestHeaders(baseUrl = baseUrl) }

    val cookieHeader = cookieStore.load(imageUrl)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(separator = "; ") { cookie ->
            "${cookie.name}=${cookie.value}"
        }
    if (!cookieHeader.isNullOrBlank()) {
        requestBuilder.header("Cookie", cookieHeader)
    }

    val headers = requestBuilder.build().headers
    return headers.names().associateWith { name ->
        headers[name].orEmpty()
    }
}

internal fun suggestPostImageDownloadFileName(
    resolvedUrl: String,
): String {
    return runCatching {
        resolvedUrl.toHttpUrl().pathSegments.lastOrNull()
            ?.substringBefore("?")
            ?.takeIf { it.isNotBlank() }
    }.getOrNull() ?: "nativedo-image"
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun PostImageViewerDialog(
    state: PostImageViewerState,
    cookieStore: CookieStore,
    baseUrl: String,
    onVisibleImageChanged: (PostBodyImage?) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val bulletinHostState = LocalBulletinHostState.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val initialPage = state.initialIndex.coerceIn(0, state.images.lastIndex)
    val pagerState = rememberPagerState(initialPage = initialPage) {
        state.images.size
    }
    val enterProgress = remember(state.launchSource, initialPage) {
        Animatable(if (state.launchSource == null) 1f else 0f)
    }
    val closeScrimAlpha = remember(state.launchSource, initialPage) {
        Animatable(postImageViewerMaxScrimAlpha)
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var dismissOffsetY by remember { mutableFloatStateOf(0f) }
    var isMenuExpanded by remember { mutableStateOf(false) }
    var transformGestureActive by remember { mutableStateOf(false) }
    var isPinchGestureInProgress by remember { mutableStateOf(false) }
    var isPagerGestureLocked by remember { mutableStateOf(false) }
    var viewerWindowSize by remember { mutableStateOf(IntSize.Zero) }
    var menuAnchorBounds by remember { mutableStateOf<IntRect?>(null) }
    var currentPageContentSize by remember { mutableStateOf(IntSize.Zero) }
    var currentPageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var contentContainerBoundsOnScreen by remember { mutableStateOf<IntRect?>(null) }
    var isClosing by remember(state.images, initialPage) { mutableStateOf(false) }
    var closeSource by remember(state.images, initialPage) {
        mutableStateOf<PostImageViewerLaunchSource?>(null)
    }
    var closeSnapshot by remember(state.images, initialPage) {
        mutableStateOf<PostImageViewerCloseSnapshot?>(null)
    }
    var hasPlayedEnterAnimation by remember(state.launchSource, initialPage) {
        mutableStateOf(state.launchSource == null)
    }
    val dismissDistancePx = remember(density) {
        with(density) { postImageViewerDismissDistance.toPx() }
    }
    val chromeAlpha = enterProgress.value * (1f - dismissProgressFraction(dismissOffsetY, dismissDistancePx) * 0.35f)
    val isEnterAnimating = state.launchSource != null && !hasPlayedEnterAnimation
    val isTransitionAnimating = isEnterAnimating || isClosing
    val activeTransitionSource = if (isClosing) closeSource else state.launchSource
    val transitionPage = if (isClosing) pagerState.currentPage else initialPage

    LaunchedEffect(pagerState.currentPage) {
        scale = 1f
        offset = Offset.Zero
        dismissOffsetY = 0f
        isMenuExpanded = false
        transformGestureActive = false
        isPinchGestureInProgress = false
        isPagerGestureLocked = false
        currentPageContentSize = IntSize.Zero
        currentPageBitmap = null
    }

    val currentViewerImage = state.images[pagerState.currentPage].asViewerImage()
    val currentImageUrl = remember(currentViewerImage, baseUrl) {
        resolvePostImageUrl(
            baseUrl = baseUrl,
            rawUrl = currentViewerImage.rawUrl,
        )
    }

    LaunchedEffect(
        pagerState.currentPage,
        state.images,
        state.launchSource,
        contentContainerBoundsOnScreen,
        hasPlayedEnterAnimation,
        isClosing,
    ) {
        if (isClosing) {
            return@LaunchedEffect
        }
        if (
            shouldDelayPostImageViewerThumbnailHide(
                hasLaunchSource = state.launchSource != null,
                hasPlayedEnterAnimation = hasPlayedEnterAnimation,
                hasContentContainerBoundsOnScreen = contentContainerBoundsOnScreen != null,
                currentPage = pagerState.currentPage,
                initialPage = initialPage,
            )
        ) {
            return@LaunchedEffect
        }
        onVisibleImageChanged(state.images.getOrNull(pagerState.currentPage))
    }

    suspend fun requestDismiss() {
        if (isClosing) {
            return
        }

        isMenuExpanded = false
        val currentDismissProgress = dismissProgressFraction(dismissOffsetY, dismissDistancePx)
        val currentOpenTransform = state.launchSource
            ?.takeIf {
                pagerState.currentPage == initialPage &&
                    currentPageContentSize != IntSize.Zero &&
                    contentContainerBoundsOnScreen != null
            }
            ?.let { launchSource ->
                calculatePostImageViewerEnterTransform(
                    sourceBounds = launchSource.fullBoundsOnScreen,
                    targetBounds = calculatePostImageViewerTargetRectInContainer(
                        containerBounds = contentContainerBoundsOnScreen!!,
                        contentSize = currentPageContentSize,
                    ),
                    progress = enterProgress.value,
                )
            }
        val currentDisplayedScale = if (isEnterAnimating) {
            1f
        } else {
            scale * postImageViewerDismissedScale(currentDismissProgress)
        }
        val currentRenderedScale = resolvePostImageViewerRenderedScale(
            displayedScale = currentDisplayedScale,
            openTransform = currentOpenTransform,
        )
        val currentRenderedTranslation = resolvePostImageViewerRenderedTranslation(
            activeOffset = offset,
            activeDismissOffsetY = dismissOffsetY,
            openTransform = currentOpenTransform,
        )
        val currentVisibleScrimAlpha = postImageViewerOpenScrimAlpha(
            enterProgress = enterProgress.value,
            dismissProgress = currentDismissProgress,
        )
        val viewportBoundsOnScreen = viewerWindowSize
            .takeIf { it != IntSize.Zero }
            ?.let { size ->
                IntRect(
                    left = 0,
                    top = 0,
                    right = size.width,
                    bottom = size.height,
                )
            }
        val dismissSource = resolvePostImageViewerDismissSource(
            state = state,
            currentPage = pagerState.currentPage,
        )?.takeIf { source ->
            viewportBoundsOnScreen == null ||
                shouldAnimatePostImageViewerDismissToSource(
                    dismissSource = source,
                    viewportBoundsOnScreen = viewportBoundsOnScreen,
                )
        }
        if (
            currentPageContentSize == IntSize.Zero ||
            contentContainerBoundsOnScreen == null
        ) {
            isClosing = true
            onVisibleImageChanged(null)
            closeScrimAlpha.snapTo(currentVisibleScrimAlpha)
            animatePostImageViewerClose(enterProgress, closeScrimAlpha)
            withFrameNanos { }
            onDismiss()
            return
        }

        closeSource = dismissSource
        closeSnapshot = PostImageViewerCloseSnapshot(
            page = pagerState.currentPage,
            contentSize = currentPageContentSize,
            containerBoundsOnScreen = contentContainerBoundsOnScreen!!,
            bitmap = currentPageBitmap,
            scale = currentRenderedScale,
            translation = currentRenderedTranslation,
        )
        closeScrimAlpha.snapTo(currentVisibleScrimAlpha)
        onVisibleImageChanged(null)
        isClosing = true
        animatePostImageViewerClose(enterProgress, closeScrimAlpha)
        withFrameNanos { }
        onDismiss()
    }

    LaunchedEffect(
        state.launchSource,
        contentContainerBoundsOnScreen,
        pagerState.currentPage,
        hasPlayedEnterAnimation,
    ) {
        if (state.launchSource == null || hasPlayedEnterAnimation) {
            if (enterProgress.value != 1f) {
                enterProgress.snapTo(1f)
            }
            return@LaunchedEffect
        }
        if (contentContainerBoundsOnScreen == null || pagerState.currentPage != initialPage) {
            return@LaunchedEffect
        }

        enterProgress.snapTo(0f)
        enterProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = postImageViewerEnterDurationMillis,
                easing = LinearOutSlowInEasing,
            ),
        )
        hasPlayedEnterAnimation = true
    }

    Dialog(
        onDismissRequest = {
            coroutineScope.launch {
                requestDismiss()
            }
        },
        properties = postImageViewerDialogProperties(),
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { w ->
                w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                w.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            }
        }
        val dismissProgress = dismissProgressFraction(dismissOffsetY, dismissDistancePx)
        val closeProgress = 1f - enterProgress.value
        val scrimAlpha = if (isClosing) {
            closeScrimAlpha.value
        } else {
            postImageViewerOpenScrimAlpha(
                enterProgress = enterProgress.value,
                dismissProgress = dismissProgress,
            )
        }
        val showCloseOverlay = shouldRenderPostImageViewerCloseOverlay(
            isClosing = isClosing,
            hasSnapshotBitmap = closeSnapshot?.bitmap != null,
        )
        val liveContentAlpha = if (isClosing) {
            if (showCloseOverlay) 0f else postImageViewerCloseContentAlpha(closeProgress)
        } else {
            1f
        }
        val activeCloseSnapshot = closeSnapshot
        val closeOverlayTargetBounds = remember(activeCloseSnapshot) {
            activeCloseSnapshot?.let { snapshot ->
                calculatePostImageViewerTargetRectInContainer(
                    containerBounds = snapshot.containerBoundsOnScreen,
                    contentSize = snapshot.contentSize,
                )
            }
        }
        val closeOverlayTransform = remember(
            activeCloseSnapshot,
            closeSource,
            closeOverlayTargetBounds,
            closeProgress,
        ) {
            val snapshot = activeCloseSnapshot
            val activeCloseSource = closeSource
            if (
                snapshot == null ||
                activeCloseSource == null ||
                closeOverlayTargetBounds == null
            ) {
                null
            } else {
                calculatePostImageViewerExitTransform(
                    sourceBounds = activeCloseSource.fullBoundsOnScreen,
                    targetBounds = closeOverlayTargetBounds,
                    currentScale = snapshot.scale,
                    currentTranslation = snapshot.translation,
                    progress = closeProgress,
                )
            }
        }
        val closeOverlayClipInsets = remember(
            activeCloseSnapshot,
            closeSource,
            closeOverlayTargetBounds,
            closeProgress,
        ) {
            val activeCloseSource = closeSource
            val targetBounds = closeOverlayTargetBounds
            if (activeCloseSource == null || targetBounds == null) {
                null
            } else {
                lerpPostImageViewerClipInsets(
                    start = PostImageViewerClipInsets(),
                    end = calculatePostImageViewerSourceClipInsets(
                        sourceVisibleBounds = activeCloseSource.boundsOnScreen,
                        sourceFullBounds = activeCloseSource.fullBoundsOnScreen,
                        targetBounds = targetBounds,
                    ),
                    progress = closeProgress,
                )
            }
        }
        val closeOverlayCornerRadiusPx = remember(
            activeCloseSnapshot,
            closeSource,
            closeOverlayTargetBounds,
            closeProgress,
        ) {
            val activeCloseSource = closeSource
            val targetBounds = closeOverlayTargetBounds
            if (activeCloseSource == null || targetBounds == null) {
                null
            } else {
                lerpPostImageViewerFloat(
                    0f,
                    calculatePostImageViewerSourceCornerRadius(
                        sourceCornerRadiusPx = activeCloseSource.cornerRadiusPx,
                        sourceFullBounds = activeCloseSource.fullBoundsOnScreen,
                        targetBounds = targetBounds,
                    ),
                    closeProgress,
                )
            }
        }
        val closeWindowDismissTransform = remember(
            activeCloseSnapshot,
            closeSource,
            viewerWindowSize,
            closeProgress,
        ) {
            val snapshot = activeCloseSnapshot
            if (
                snapshot == null ||
                closeSource != null ||
                viewerWindowSize.height <= 0
            ) {
                null
            } else {
                calculatePostImageViewerWindowDismissTransform(
                    currentScale = snapshot.scale,
                    currentTranslation = snapshot.translation,
                    viewportHeight = viewerWindowSize.height,
                    progress = closeProgress,
                )
            }
        }
        val activeCloseTransform = closeOverlayTransform ?: closeWindowDismissTransform
        val closeOverlayAlpha = if (closeSource == null) {
            1f - closeProgress.coerceIn(0f, 1f)
        } else {
            1f
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewerWindowSize = it }
                .pointerInput(isTransitionAnimating, pagerState.currentPage) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val nextLocked = if (isTransitionAnimating) {
                                false
                            } else {
                                updatePostImageViewerPagerGestureLock(
                                    wasLocked = isPagerGestureLocked,
                                    pressedPointerCount = event.changes.count { it.pressed },
                                )
                            }
                            if (nextLocked != isPagerGestureLocked) {
                                isPagerGestureLocked = nextLocked
                            }
                        }
                    }
                }
                .background(
                    Color.Black.copy(
                        alpha = scrimAlpha,
                    ),
                ),
        ) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = liveContentAlpha },
                userScrollEnabled = shouldEnablePostImageViewerPagerScroll(
                    isTransitionAnimating = isTransitionAnimating,
                    scale = scale,
                    dismissOffsetY = dismissOffsetY,
                    isPagerGestureLocked = isPagerGestureLocked,
                ),
            ) { page ->
                val isCurrentPage = page == pagerState.currentPage
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                        .then(
                            if (isCurrentPage && !isClosing) {
                                Modifier.onGloballyPositioned { coordinates ->
                                    val position = coordinates.positionOnScreen()
                                    val nextBounds = IntRect(
                                        left = position.x.roundToInt(),
                                        top = position.y.roundToInt(),
                                        right = (position.x + coordinates.size.width).roundToInt(),
                                        bottom = (position.y + coordinates.size.height).roundToInt(),
                                    )
                                    if (contentContainerBoundsOnScreen != nextBounds) {
                                        contentContainerBoundsOnScreen = nextBounds
                                    }
                                }
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    val viewerImage = state.images[page].asViewerImage()
                    val previewBitmap = remember(viewerImage) {
                        findPostImageViewerPreviewBitmap(viewerImage)
                    }
                    val resolvedUrl = remember(viewerImage, baseUrl) {
                        resolvePostImageUrl(
                            baseUrl = baseUrl,
                            rawUrl = viewerImage.rawUrl,
                        )
                    }
                    val shouldAnimate = remember(resolvedUrl) {
                        isAnimatedImageUrl(resolvedUrl)
                    }
                    val bitmap = if (shouldAnimate) {
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
                    val contentSize = rememberViewerContentSize(
                        image = viewerImage,
                        loadedBitmap = bitmap,
                        previewBitmap = previewBitmap,
                        availableWidth = maxWidth,
                        availableHeight = maxHeight,
                    )
                    val containerSize = rememberViewerContainerSize(
                        availableWidth = maxWidth,
                        availableHeight = maxHeight,
                    )
                    val closeSnapshotForPage = closeSnapshot?.takeIf { snapshot ->
                        isClosing && snapshot.page == page
                    }
                    val renderedContentSize = closeSnapshotForPage?.contentSize ?: contentSize
                    if (isCurrentPage && !isClosing) {
                        SideEffect {
                            if (currentPageContentSize != contentSize) {
                                currentPageContentSize = contentSize
                            }
                            val nextBitmap = bitmap ?: previewBitmap
                            if (currentPageBitmap !== nextBitmap) {
                                currentPageBitmap = nextBitmap
                            }
                        }
                    }
                    val activeScale = if (isCurrentPage) scale else 1f
                    val activeOffset = if (isCurrentPage) offset else Offset.Zero
                    val activeDismissOffsetY = if (isCurrentPage) dismissOffsetY else 0f
                    val displayedScale = if (isCurrentPage && !isEnterAnimating) {
                        activeScale * postImageViewerDismissedScale(dismissProgress)
                    } else {
                        1f
                    }
                    val openTargetBounds = remember(
                        isCurrentPage,
                        page,
                        transitionPage,
                        contentContainerBoundsOnScreen,
                        renderedContentSize,
                    ) {
                        val containerBounds = contentContainerBoundsOnScreen
                        if (
                            !isCurrentPage ||
                            page != transitionPage ||
                            containerBounds == null
                        ) {
                            null
                        } else {
                            calculatePostImageViewerTargetRectInContainer(
                                containerBounds = containerBounds,
                                contentSize = renderedContentSize,
                            )
                        }
                    }
                    val openTransform = remember(
                        isCurrentPage,
                        page,
                        transitionPage,
                        activeTransitionSource,
                        openTargetBounds,
                        enterProgress.value,
                        isClosing,
                    ) {
                        if (
                            isClosing ||
                            !isCurrentPage ||
                            page != transitionPage ||
                            activeTransitionSource == null ||
                            openTargetBounds == null
                        ) {
                            null
                        } else {
                            calculatePostImageViewerEnterTransform(
                                sourceBounds = activeTransitionSource.fullBoundsOnScreen,
                                targetBounds = openTargetBounds,
                                progress = enterProgress.value,
                            )
                        }
                    }
                    val openClipInsets = remember(
                        isCurrentPage,
                        page,
                        transitionPage,
                        activeTransitionSource,
                        openTargetBounds,
                        enterProgress.value,
                        isClosing,
                    ) {
                        if (
                            isClosing ||
                            !isCurrentPage ||
                            page != transitionPage ||
                            activeTransitionSource == null ||
                            openTargetBounds == null
                        ) {
                            null
                        } else {
                            lerpPostImageViewerClipInsets(
                                start = calculatePostImageViewerSourceClipInsets(
                                    sourceVisibleBounds = activeTransitionSource.boundsOnScreen,
                                    sourceFullBounds = activeTransitionSource.fullBoundsOnScreen,
                                    targetBounds = openTargetBounds,
                                ),
                                end = PostImageViewerClipInsets(),
                                progress = enterProgress.value,
                            )
                        }
                    }
                    val openCornerRadiusPx = remember(
                        isCurrentPage,
                        page,
                        transitionPage,
                        activeTransitionSource,
                        openTargetBounds,
                        enterProgress.value,
                        isClosing,
                    ) {
                        if (
                            isClosing ||
                            !isCurrentPage ||
                            page != transitionPage ||
                            activeTransitionSource == null ||
                            openTargetBounds == null
                        ) {
                            null
                        } else {
                            lerpPostImageViewerFloat(
                                calculatePostImageViewerSourceCornerRadius(
                                    sourceCornerRadiusPx = activeTransitionSource.cornerRadiusPx,
                                    sourceFullBounds = activeTransitionSource.fullBoundsOnScreen,
                                    targetBounds = openTargetBounds,
                                ),
                                0f,
                                enterProgress.value,
                            )
                        }
                    }
                    val dismissDragState = rememberDraggableState { delta ->
                        if (!isCurrentPage || scale > 1f) {
                            return@rememberDraggableState
                        }
                        dismissOffsetY = (dismissOffsetY + delta).coerceAtLeast(0f)
                        isMenuExpanded = false
                    }

                    LaunchedEffect(
                        isCurrentPage,
                        isPinchGestureInProgress,
                        containerSize,
                        contentSize,
                        isClosing,
                    ) {
                        if (
                            !isCurrentPage ||
                            isClosing ||
                            isPinchGestureInProgress ||
                            !transformGestureActive
                        ) {
                            return@LaunchedEffect
                        }

                        transformGestureActive = false
                        val settledScale = settlePostImageViewerScale(scale)
                        val settledOffset = if (settledScale <= 1f) {
                            Offset.Zero
                        } else {
                            clampPostImageViewerOffset(
                                proposed = offset,
                                scale = settledScale,
                                containerSize = containerSize,
                                contentSize = contentSize,
                            )
                        }
                        animatePostImageViewerState(
                            fromScale = scale,
                            toScale = settledScale,
                            fromOffset = offset,
                            toOffset = settledOffset,
                            onUpdate = { nextScale, nextOffset ->
                                scale = nextScale
                                offset = nextOffset
                            },
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(isCurrentPage, isTransitionAnimating, containerSize, contentSize) {
                                try {
                                    awaitEachGesture {
                                        var activePinch: ActivePostImageViewerPinch? = null
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            val pressedChanges = event.changes.filter { it.pressed }
                                            if (!isCurrentPage || isTransitionAnimating) {
                                                activePinch = null
                                                isPinchGestureInProgress = false
                                                break
                                            }

                                            when {
                                                pressedChanges.size > 1 -> {
                                                    val firstPointer = pressedChanges[0]
                                                    val secondPointer = pressedChanges[1]
                                                    val pinch = activePinch ?: ActivePostImageViewerPinch(
                                                        startDistance = sqrt(
                                                            (secondPointer.position.x - firstPointer.position.x)
                                                                .pow(2) +
                                                                (secondPointer.position.y - firstPointer.position.y)
                                                                    .pow(2),
                                                        ),
                                                        startScale = scale,
                                                        startOffset = offset,
                                                        center = Offset(
                                                            x = (firstPointer.position.x + secondPointer.position.x) / 2f,
                                                            y = (firstPointer.position.y + secondPointer.position.y) / 2f,
                                                        ),
                                                    ).also {
                                                        activePinch = it
                                                        transformGestureActive = true
                                                        isPinchGestureInProgress = true
                                                        dismissOffsetY = 0f
                                                        isMenuExpanded = false
                                                    }
                                                    val nextTransform = calculatePostImageViewerPinchTransform(
                                                        pinchStartDistance = pinch.startDistance,
                                                        currentDistance = sqrt(
                                                            (secondPointer.position.x - firstPointer.position.x)
                                                                .pow(2) +
                                                                (secondPointer.position.y - firstPointer.position.y)
                                                                    .pow(2),
                                                        ),
                                                        pinchStartScale = pinch.startScale,
                                                        pinchStartOffset = pinch.startOffset,
                                                        pinchCenter = pinch.center,
                                                        containerSize = containerSize,
                                                        contentSize = contentSize,
                                                    )
                                                    scale = nextTransform.scale
                                                    offset = nextTransform.offset
                                                    event.changes.forEach { it.consume() }
                                                }

                                                activePinch != null -> {
                                                    activePinch = null
                                                    isPinchGestureInProgress = false
                                                    if (pressedChanges.isEmpty()) {
                                                        break
                                                    }
                                                }

                                                pressedChanges.isEmpty() -> break
                                            }
                                        }
                                    }
                                } finally {
                                    isPinchGestureInProgress = false
                                }
                            }
                            .draggable(
                                state = dismissDragState,
                                orientation = Orientation.Vertical,
                                enabled = isCurrentPage &&
                                    !isTransitionAnimating &&
                                    scale <= 1f &&
                                    !isPinchGestureInProgress,
                                onDragStopped = { velocity ->
                                    if (!isCurrentPage) {
                                        return@draggable
                                    }
                                    if (shouldDismissPostImageViewer(
                                            verticalOffset = dismissOffsetY,
                                            velocityY = velocity,
                                            dismissDistance = dismissDistancePx,
                                        )
                                    ) {
                                        coroutineScope.launch {
                                            requestDismiss()
                                        }
                                    } else {
                                        coroutineScope.launch {
                                            animatePostImageViewerDismissOffset(
                                                from = dismissOffsetY,
                                                to = 0f,
                                                onUpdate = { dismissOffsetY = it },
                                            )
                                        }
                                    }
                                },
                            )
                            .then(
                                if (
                                    isCurrentPage &&
                                    !isTransitionAnimating &&
                                    scale > 1f &&
                                    !isPinchGestureInProgress
                                ) {
                                    Modifier.pointerInput(containerSize, contentSize, scale) {
                                        var lastDragEventTime = 0L
                                        var trackedVelocity = Offset.Zero
                                        detectDragGestures(
                                            onDragStart = {
                                                lastDragEventTime = 0L
                                                trackedVelocity = Offset.Zero
                                                isMenuExpanded = false
                                            },
                                            onDragEnd = {
                                                coroutineScope.launch {
                                                    animatePostImageViewerPanFling(
                                                        fromOffset = offset,
                                                        initialVelocity = trackedVelocity,
                                                        scale = scale,
                                                        containerSize = containerSize,
                                                        contentSize = contentSize,
                                                        onUpdate = { nextOffset -> offset = nextOffset },
                                                    )
                                                }
                                            },
                                            onDragCancel = {
                                                coroutineScope.launch {
                                                    val settledOffset = clampPostImageViewerOffset(
                                                        proposed = offset,
                                                        scale = scale,
                                                        containerSize = containerSize,
                                                        contentSize = contentSize,
                                                    )
                                                    if (settledOffset != offset) {
                                                        animatePostImageViewerState(
                                                            fromScale = scale,
                                                            toScale = scale,
                                                            fromOffset = offset,
                                                            toOffset = settledOffset,
                                                            onUpdate = { _, nextOffset ->
                                                                offset = nextOffset
                                                            },
                                                        )
                                                    }
                                                }
                                            },
                                            onDrag = { change, dragAmount ->
                                                isMenuExpanded = false
                                                val eventTime = change.uptimeMillis
                                                if (lastDragEventTime > 0L) {
                                                    val deltaSeconds =
                                                        (eventTime - lastDragEventTime) / 1_000f
                                                    if (deltaSeconds > 0f) {
                                                        val instantVelocity = Offset(
                                                            x = dragAmount.x / deltaSeconds,
                                                            y = dragAmount.y / deltaSeconds,
                                                        )
                                                        trackedVelocity = Offset(
                                                            x = lerpPostImageViewerFloat(
                                                                trackedVelocity.x,
                                                                instantVelocity.x,
                                                                0.35f,
                                                            ),
                                                            y = lerpPostImageViewerFloat(
                                                                trackedVelocity.y,
                                                                instantVelocity.y,
                                                                0.35f,
                                                            ),
                                                        )
                                                    }
                                                }
                                                lastDragEventTime = eventTime
                                                offset = clampPostImageViewerOffset(
                                                    proposed = offset + dragAmount,
                                                    scale = scale,
                                                    containerSize = containerSize,
                                                    contentSize = contentSize,
                                                    extraSlack = postImageViewerPanSlackPx,
                                                )
                                            },
                                        )
                                    }
                                } else {
                                    Modifier
                                },
                            )
                            .pointerInput(isCurrentPage, isTransitionAnimating, scale, containerSize, contentSize) {
                                detectTapGestures(
                                    onTap = { tapOffset ->
                                        if (!isCurrentPage || isTransitionAnimating || scale > 1f) {
                                            return@detectTapGestures
                                        }
                                        if (!isPostImageViewerTapInsideContent(
                                                tapOffset = tapOffset,
                                                containerSize = containerSize,
                                                contentSize = contentSize,
                                            )
                                        ) {
                                            coroutineScope.launch {
                                                requestDismiss()
                                            }
                                        }
                                    },
                                    onDoubleTap = { tapOffset ->
                                        if (!isCurrentPage || isTransitionAnimating) {
                                            return@detectTapGestures
                                        }
                                        dismissOffsetY = 0f
                                        isMenuExpanded = false
                                        val targetScale = togglePostImageViewerScale(scale)
                                        val targetOffset = postImageViewerDoubleTapTargetOffset(
                                            tapOffset = tapOffset,
                                            currentScale = scale,
                                            targetScale = targetScale,
                                            currentOffset = offset,
                                            containerSize = containerSize,
                                            contentSize = contentSize,
                                        )
                                        coroutineScope.launch {
                                            animatePostImageViewerState(
                                                fromScale = scale,
                                                toScale = targetScale,
                                                fromOffset = offset,
                                                toOffset = targetOffset,
                                                onUpdate = { nextScale, nextOffset ->
                                                    scale = nextScale
                                                    offset = nextOffset
                                                },
                                            )
                                        }
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        ViewerImageContent(
                            contentSize = renderedContentSize,
                            bitmap = bitmap,
                            resolvedUrl = resolvedUrl,
                            shouldAnimate = shouldAnimate,
                            clipInsets = openClipInsets,
                            cornerRadiusPx = openCornerRadiusPx,
                            modifier = Modifier.graphicsLayer {
                                scaleX = displayedScale * (openTransform?.scaleX ?: 1f)
                                scaleY = displayedScale * (openTransform?.scaleY ?: 1f)
                                translationX = activeOffset.x + (openTransform?.translationX ?: 0f)
                                translationY = activeOffset.y + activeDismissOffsetY +
                                    (openTransform?.translationY ?: 0f)
                            },
                        )
                    }
                }
            }

            if (showCloseOverlay && activeCloseSnapshot != null && activeCloseTransform != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ViewerImageContent(
                        contentSize = activeCloseSnapshot.contentSize,
                        bitmap = activeCloseSnapshot.bitmap,
                        clipInsets = if (closeSource != null) closeOverlayClipInsets else null,
                        cornerRadiusPx = if (closeSource != null) closeOverlayCornerRadiusPx else null,
                        modifier = Modifier.graphicsLayer {
                            alpha = closeOverlayAlpha
                            scaleX = activeCloseTransform.scaleX
                            scaleY = activeCloseTransform.scaleY
                            translationX = activeCloseTransform.translationX
                            translationY = activeCloseTransform.translationY
                        },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ViewerChromeButton(
                    onClick = {
                        coroutineScope.launch {
                            requestDismiss()
                        }
                    },
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.post_image_viewer_close),
                    alpha = chromeAlpha,
                )

                Spacer(modifier = Modifier.weight(1f))

                if (state.images.size > 1) {
                    ViewerCountPill(
                        currentPage = pagerState.currentPage + 1,
                        pageCount = state.images.size,
                        alpha = chromeAlpha,
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Box {
                    ViewerChromeButton(
                        onClick = {
                            if (!isTransitionAnimating) {
                                isMenuExpanded = true
                            }
                        },
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.post_image_viewer_more_actions),
                        alpha = chromeAlpha,
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            val position = coordinates.positionInRoot()
                            menuAnchorBounds = IntRect(
                                left = position.x.roundToInt(),
                                top = position.y.roundToInt(),
                                right = (position.x + coordinates.size.width).roundToInt(),
                                bottom = (position.y + coordinates.size.height).roundToInt(),
                            )
                        },
                    )
                }
            }

            ViewerActionPopup(
                expanded = isMenuExpanded,
                anchorBounds = menuAnchorBounds,
                windowSize = viewerWindowSize,
                actions = postImageViewerActions(),
                onDismissRequest = { isMenuExpanded = false },
                onActionClick = { action ->
                    isMenuExpanded = false
                    when (action) {
                        PostImageViewerAction.Share -> {
                            context.startPostImageViewerIntent(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND)
                                        .putExtra(Intent.EXTRA_TEXT, currentImageUrl)
                                        .setType("text/plain"),
                                    context.getString(R.string.post_image_viewer_action_share),
                                ),
                            ) {
                                bulletinHostState?.show(
                                    BulletinMessage(
                                        title = context.getString(R.string.post_image_viewer_action_failed),
                                        tone = BulletinTone.Error,
                                    ),
                                )
                            }
                        }

                        PostImageViewerAction.OpenExternal -> {
                            context.startPostImageViewerIntent(
                                Intent(Intent.ACTION_VIEW, Uri.parse(currentImageUrl)),
                            ) {
                                bulletinHostState?.show(
                                    BulletinMessage(
                                        title = context.getString(R.string.post_image_viewer_action_failed),
                                        tone = BulletinTone.Error,
                                    ),
                                )
                            }
                        }

                        PostImageViewerAction.SaveImage -> {
                            val enqueued = enqueuePostImageViewerDownload(
                                context = context,
                                resolvedUrl = currentImageUrl,
                                cookieStore = cookieStore,
                                baseUrl = baseUrl,
                            )
                            bulletinHostState?.show(
                                BulletinMessage(
                                    title = context.getString(
                                        if (enqueued) {
                                            R.string.post_image_viewer_download_started
                                        } else {
                                            R.string.post_image_viewer_action_failed
                                        },
                                    ),
                                    tone = if (enqueued) {
                                        BulletinTone.Info
                                    } else {
                                        BulletinTone.Error
                                    },
                                ),
                            )
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun ViewerImageContent(
    contentSize: IntSize,
    bitmap: Bitmap?,
    resolvedUrl: String? = null,
    shouldAnimate: Boolean = false,
    clipInsets: PostImageViewerClipInsets? = null,
    cornerRadiusPx: Float? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val contentWidth = remember(contentSize, density) {
        with(density) { contentSize.width.toDp() }
    }
    val contentHeight = remember(contentSize, density) {
        with(density) { contentSize.height.toDp() }
    }

    Box(
        modifier = modifier
            .size(width = contentWidth, height = contentHeight)
            .then(
                if (clipInsets != null || (cornerRadiusPx ?: 0f) > 0f) {
                    Modifier.drawWithContent {
                        val mask = calculatePostImageViewerContentMask(
                            contentWidth = size.width,
                            contentHeight = size.height,
                            clipInsets = clipInsets,
                            cornerRadiusPx = cornerRadiusPx ?: 0f,
                        )
                        if (mask != null) {
                            clipRect(
                                left = mask.visibleBounds.left,
                                top = mask.visibleBounds.top,
                                right = mask.visibleBounds.right,
                                bottom = mask.visibleBounds.bottom,
                            ) {
                                if (mask.cornerRadiusPx > 0f) {
                                    clipPath(
                                        Path().apply {
                                            addRoundRect(
                                                RoundRect(
                                                    left = mask.roundedBounds.left,
                                                    top = mask.roundedBounds.top,
                                                    right = mask.roundedBounds.right,
                                                    bottom = mask.roundedBounds.bottom,
                                                    cornerRadius = CornerRadius(
                                                        mask.cornerRadiusPx,
                                                        mask.cornerRadiusPx,
                                                    ),
                                                ),
                                            )
                                        },
                                    ) {
                                        this@drawWithContent.drawContent()
                                    }
                                } else {
                                    this@drawWithContent.drawContent()
                                }
                            }
                        }
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (shouldAnimate) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(resolvedUrl)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (bitmap == null) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ViewerChromeButton(
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
private fun ViewerActionPopup(
    expanded: Boolean,
    anchorBounds: IntRect?,
    windowSize: IntSize,
    actions: List<PostImageViewerAction>,
    onDismissRequest: () -> Unit,
    onActionClick: (PostImageViewerAction) -> Unit,
) {
    if (anchorBounds == null || windowSize == IntSize.Zero) {
        return
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val popupWidth = with(density) { postImageViewerPopupWidth.roundToPx() }
    val popupHeight = with(density) {
        (postImageViewerPopupVerticalPadding * 2 +
            postImageViewerPopupRowHeight * actions.size).roundToPx()
    }
    val popupMargin = with(density) { postImageViewerPopupMargin.roundToPx() }
    val popupSpacing = with(density) { postImageViewerPopupSpacing.roundToPx() }
    val popupOpenDuration = remember(actions.size) {
        postImageViewerPopupOpenDuration(actions.size)
    }
    val popupCornerRadiusPx = with(density) { postImageViewerPopupCornerRadius.toPx() }
    val popupBorderWidthPx = with(density) { postImageViewerPopupBorderWidth.toPx() }
    val popupCloseTranslationPx = with(density) {
        postImageViewerPopupCloseTranslation.toPx()
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
        calculatePostImageViewerPopupOffset(
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
                    postImageViewerPopupCloseDurationMillis
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
                durationMillis = postImageViewerPopupCloseDurationMillis,
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
                .width(postImageViewerPopupWidth)
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
                modifier = Modifier.padding(vertical = postImageViewerPopupVerticalPadding),
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

internal fun calculatePostImageViewerPopupOffset(
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

internal fun postImageViewerPopupOpenDuration(
    itemCount: Int,
): Int = 150 + 16 * itemCount.coerceAtLeast(0)

internal fun postImageViewerPopupCascadeProgress(
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
private fun ViewerActionPopupItem(
    action: PostImageViewerAction,
    index: Int,
    actionCount: Int,
    popupProgress: Float,
    onClick: () -> Unit,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val startTranslationPx = with(density) { postImageViewerPopupChildStartTranslation.toPx() }
    val rowProgress = remember(
        index,
        actionCount,
        popupProgress,
    ) {
        postImageViewerPopupCascadeProgress(
            fullAnimationT = popupProgress,
            position = index.toFloat(),
            count = actionCount.toFloat(),
            waveLength = postImageViewerPopupCascadeWaveLength,
        )
    }
    val rowAlpha = rowProgress
    val rowTranslationY = (1f - rowProgress) * -startTranslationPx

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(postImageViewerPopupRowHeight)
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

@Composable
private fun ViewerCountPill(
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
private fun rememberViewerContainerSize(
    availableWidth: androidx.compose.ui.unit.Dp,
    availableHeight: androidx.compose.ui.unit.Dp,
): IntSize {
    val density = LocalDensity.current
    return remember(availableWidth, availableHeight, density) {
        with(density) {
            IntSize(
                width = availableWidth.roundToPx(),
                height = availableHeight.roundToPx(),
            )
        }
    }
}

@Composable
private fun rememberViewerContentSize(
    image: PostBodyImage,
    loadedBitmap: Bitmap?,
    previewBitmap: Bitmap?,
    availableWidth: androidx.compose.ui.unit.Dp,
    availableHeight: androidx.compose.ui.unit.Dp,
): IntSize {
    val density = LocalDensity.current
    val imageSize = remember(availableWidth, availableHeight, image, loadedBitmap, previewBitmap) {
        calculatePostImageSize(
            availableWidth = availableWidth,
            aspectRatio = resolvePostImageViewerAspectRatio(
                image = image,
                loadedBitmapSize = loadedBitmap?.let { IntSize(it.width, it.height) },
                previewBitmapSize = previewBitmap?.let { IntSize(it.width, it.height) },
            ),
            maxHeight = availableHeight,
        )
    }
    return remember(imageSize, density) {
        with(density) {
            IntSize(
                width = imageSize.width.roundToPx(),
                height = imageSize.height.roundToPx(),
            )
        }
    }
}

@Composable
private fun PostImageViewerAction.label(): String = when (this) {
    PostImageViewerAction.Share -> stringResource(R.string.post_image_viewer_action_share)
    PostImageViewerAction.OpenExternal -> stringResource(R.string.post_image_viewer_action_open_in_browser)
    PostImageViewerAction.SaveImage -> stringResource(R.string.post_image_viewer_action_save_image)
}

private fun PostImageViewerAction.icon(): ImageVector = when (this) {
    PostImageViewerAction.Share -> Icons.Outlined.Share
    PostImageViewerAction.OpenExternal -> Icons.Outlined.OpenInBrowser
    PostImageViewerAction.SaveImage -> Icons.Outlined.Download
}

private fun Context.startPostImageViewerIntent(
    intent: Intent,
    onFailure: () -> Unit,
) {
    runCatching {
        if (this !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }.onFailure {
        onFailure()
    }
}

private fun enqueuePostImageViewerDownload(
    context: Context,
    resolvedUrl: String,
    cookieStore: CookieStore,
    baseUrl: String,
): Boolean {
    return runCatching {
        val downloadManager = context.getSystemService(DownloadManager::class.java)
            ?: return false
        val fileName = suggestPostImageDownloadFileName(resolvedUrl)
        val request = DownloadManager.Request(Uri.parse(resolvedUrl))
            .setTitle(fileName)
            .setMimeType(URLConnection.guessContentTypeFromName(fileName))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

        buildPostImageViewerRequestHeaders(
            resolvedUrl = resolvedUrl,
            cookieStore = cookieStore,
            baseUrl = baseUrl,
        ).forEach { (name, value) ->
            request.addRequestHeader(name, value)
        }

        downloadManager.enqueue(request)
        true
    }.getOrDefault(false)
}

private fun dismissProgressFraction(
    dismissOffsetY: Float,
    dismissDistancePx: Float,
): Float {
    if (dismissDistancePx <= 0f) {
        return 0f
    }
    return (dismissOffsetY / dismissDistancePx).coerceIn(0f, 1f)
}

private suspend fun animatePostImageViewerState(
    fromScale: Float,
    toScale: Float,
    fromOffset: Offset,
    toOffset: Offset,
    onUpdate: (Float, Offset) -> Unit,
) = coroutineScope {
    var animatedScale = fromScale
    var animatedOffset = fromOffset
    val scaleAnim = Animatable(fromScale)
    val xAnim = Animatable(fromOffset.x)
    val yAnim = Animatable(fromOffset.y)
    val animationSpec = tween<Float>(
        durationMillis = postImageViewerSnapAnimationDurationMillis,
        easing = FastOutSlowInEasing,
    )

    launch {
        scaleAnim.animateTo(toScale, animationSpec) {
            animatedScale = value
            onUpdate(animatedScale, animatedOffset)
        }
    }
    launch {
        xAnim.animateTo(toOffset.x, animationSpec) {
            animatedOffset = animatedOffset.copy(x = value)
            onUpdate(animatedScale, animatedOffset)
        }
    }
    launch {
        yAnim.animateTo(toOffset.y, animationSpec) {
            animatedOffset = animatedOffset.copy(y = value)
            onUpdate(animatedScale, animatedOffset)
        }
    }
}

private suspend fun animatePostImageViewerPanFling(
    fromOffset: Offset,
    initialVelocity: Offset,
    scale: Float,
    containerSize: IntSize,
    contentSize: IntSize,
    onUpdate: (Offset) -> Unit,
) {
    var currentOffset = fromOffset
    var currentVelocity = initialVelocity
    var previousFrameTimeNanos = withFrameNanos { it }

    while (postImageViewerVelocityMagnitude(currentVelocity) >= postImageViewerFlingStopVelocityPxPerSecond) {
        val frameTimeNanos = withFrameNanos { it }
        val deltaSeconds = ((frameTimeNanos - previousFrameTimeNanos) / 1_000_000_000f)
            .coerceIn(0.001f, 0.032f)
        previousFrameTimeNanos = frameTimeNanos

        val nextOffset = clampPostImageViewerOffset(
            proposed = currentOffset + Offset(
                x = currentVelocity.x * deltaSeconds,
                y = currentVelocity.y * deltaSeconds,
            ),
            scale = scale,
            containerSize = containerSize,
            contentSize = contentSize,
            extraSlack = postImageViewerPanSlackPx,
        )
        val hitSlackBoundary = nextOffset != currentOffset + Offset(
            x = currentVelocity.x * deltaSeconds,
            y = currentVelocity.y * deltaSeconds,
        )
        currentOffset = nextOffset
        onUpdate(currentOffset)

        val decay = postImageViewerFlingDecayPerFrame.pow(deltaSeconds * 60f)
        currentVelocity = Offset(
            x = currentVelocity.x * decay,
            y = currentVelocity.y * decay,
        )
        if (hitSlackBoundary) {
            currentVelocity = Offset(
                x = currentVelocity.x * 0.72f,
                y = currentVelocity.y * 0.72f,
            )
        }
    }

    val settledOffset = clampPostImageViewerOffset(
        proposed = currentOffset,
        scale = scale,
        containerSize = containerSize,
        contentSize = contentSize,
    )
    if (settledOffset != currentOffset) {
        animatePostImageViewerState(
            fromScale = scale,
            toScale = scale,
            fromOffset = currentOffset,
            toOffset = settledOffset,
            onUpdate = { _, nextOffset -> onUpdate(nextOffset) },
        )
    }
}

private suspend fun animatePostImageViewerDismissOffset(
    from: Float,
    to: Float,
    onUpdate: (Float) -> Unit,
) {
    val anim = Animatable(from)
    anim.animateTo(
        targetValue = to,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
    ) {
        onUpdate(value)
    }
}

private fun lerpPostImageViewerFloat(
    start: Float,
    stop: Float,
    progress: Float,
): Float = start + (stop - start) * progress

private fun postImageViewerVelocityMagnitude(
    velocity: Offset,
): Float = sqrt(velocity.x * velocity.x + velocity.y * velocity.y)

private val postImageViewerDismissDistance = 220.dp
private const val postImageViewerMaxScrimAlpha = 0.94f
private const val postImageViewerDismissScrimAlphaReduction = 0.3f
private const val postImageViewerEnterDurationMillis = 220
private const val postImageViewerCloseDurationMillis = 180
private const val postImageViewerCloseContentFadeOutFraction = 0.35f
private const val postImageViewerZoomedScale = 3f
private const val postImageViewerMaxScale = 4f
private const val postImageViewerMinInteractiveScale = 0.88f
private const val postImageViewerMaxInteractiveScale = 4.6f
private const val postImageViewerDismissVelocityThreshold = 2_000f
private const val postImageViewerPanSlackPx = 96f
private const val postImageViewerSnapAnimationDurationMillis = 180
private const val postImageViewerFlingStopVelocityPxPerSecond = 90f
private const val postImageViewerFlingDecayPerFrame = 0.93f
private val postImageViewerPopupWidth = 204.dp
private val postImageViewerPopupRowHeight = 48.dp
private val postImageViewerPopupVerticalPadding = 6.dp
private val postImageViewerPopupMargin = 12.dp
private val postImageViewerPopupSpacing = 8.dp
private val postImageViewerPopupCornerRadius = 18.dp
private val postImageViewerPopupBorderWidth = 1.dp
private val postImageViewerPopupChildStartTranslation = 6.dp
private val postImageViewerPopupCloseTranslation = 8.dp
private const val postImageViewerPopupCascadeWaveLength = 4f
private const val postImageViewerPopupCloseDurationMillis = 150

internal fun PostBodyImage.viewerCacheKey(): String = originalUrl ?: rawUrl

private fun MutableList<PostBodyImage>.addBlockImages(block: PostBodyBlock) {
    when (block) {
        is PostBodyBlock.HtmlBlock -> Unit
        is PostBodyBlock.UnknownBlock -> Unit
        is PostBodyBlock.RichTextBlock -> Unit
        is PostBodyBlock.ParagraphBlock -> Unit
        is PostBodyBlock.HeadingBlock -> Unit
        is PostBodyBlock.HorizontalRuleBlock -> Unit
        is PostBodyBlock.ImageBlock -> add(block.image)
        is PostBodyBlock.LightboxBlock -> add(block.image)
        is PostBodyBlock.ImageGridBlock -> addAll(block.images)
        is PostBodyBlock.OneboxBlock -> Unit
        is PostBodyBlock.DetailsBlock -> block.children.forEach(::addBlockImages)
        is PostBodyBlock.CalloutBlock -> block.children.forEach(::addBlockImages)
        is PostBodyBlock.QuoteBlock -> block.children.forEach(::addBlockImages)
        is PostBodyBlock.DiscourseQuoteBlock -> block.children.forEach(::addBlockImages)
        is PostBodyBlock.CodeBlock -> Unit
        is PostBodyBlock.ListBlock -> {
            block.items.forEach { item ->
                item.children.forEach(::addBlockImages)
            }
        }
    }
}

private fun PostBodyImage.asViewerImage(): PostBodyImage = copy(
    rawUrl = originalUrl ?: rawUrl,
)
