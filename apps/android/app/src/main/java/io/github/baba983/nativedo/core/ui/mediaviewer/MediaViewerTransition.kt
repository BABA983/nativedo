package io.github.baba983.nativedo.core.ui.mediaviewer

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

// --- Constants ---
internal val MEDIA_VIEWER_DISMISS_DISTANCE = 220.dp
internal const val MEDIA_VIEWER_MAX_SCRIM_ALPHA = 0.94f
internal const val MEDIA_VIEWER_DISMISS_SCRIM_ALPHA_REDUCTION = 0.3f
internal const val MEDIA_VIEWER_ENTER_DURATION_MS = 220
internal const val MEDIA_VIEWER_CLOSE_DURATION_MS = 180
internal const val MEDIA_VIEWER_CLOSE_CONTENT_FADE_OUT_FRACTION = 0.35f

// --- Data classes ---
internal data class MediaViewerEnterTransform(
    val scaleX: Float,
    val scaleY: Float,
    val translationX: Float,
    val translationY: Float,
)

internal data class MediaViewerClipInsets(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
)

internal data class MediaViewerContentMask(
    val visibleBounds: Rect,
    val roundedBounds: Rect,
    val cornerRadiusPx: Float,
)

internal data class MediaViewerCloseSnapshot(
    val page: Int,
    val contentSize: IntSize,
    val containerBoundsOnScreen: IntRect,
    val bitmap: Bitmap?,
    val scale: Float,
    val translation: Offset,
)

// --- Functions ---
internal fun mediaViewerBaseScrimAlpha(dismissProgress: Float): Float {
    return (MEDIA_VIEWER_MAX_SCRIM_ALPHA -
        dismissProgress.coerceIn(0f, 1f) * MEDIA_VIEWER_DISMISS_SCRIM_ALPHA_REDUCTION)
        .coerceIn(0f, MEDIA_VIEWER_MAX_SCRIM_ALPHA)
}

internal fun mediaViewerOpenScrimAlpha(enterProgress: Float, dismissProgress: Float): Float {
    return mediaViewerBaseScrimAlpha(dismissProgress) * enterProgress.coerceIn(0f, 1f)
}

internal fun mediaViewerCloseContentAlpha(progress: Float): Float {
    return (1f - progress.coerceIn(0f, 1f) / MEDIA_VIEWER_CLOSE_CONTENT_FADE_OUT_FRACTION).coerceIn(0f, 1f)
}

internal fun shouldRenderMediaViewerCloseOverlay(isClosing: Boolean, hasSnapshotBitmap: Boolean): Boolean =
    isClosing && hasSnapshotBitmap

internal fun resolveMediaViewerRenderedScale(displayedScale: Float, openTransform: MediaViewerEnterTransform?): Float =
    displayedScale * (openTransform?.scaleX ?: 1f)

internal fun resolveMediaViewerRenderedTranslation(
    activeOffset: Offset, activeDismissOffsetY: Float, openTransform: MediaViewerEnterTransform?,
): Offset = Offset(
    x = activeOffset.x + (openTransform?.translationX ?: 0f),
    y = activeOffset.y + activeDismissOffsetY + (openTransform?.translationY ?: 0f),
)

internal fun calculateMediaViewerWindowDismissTransform(
    currentScale: Float, currentTranslation: Offset, viewportHeight: Int, progress: Float,
): MediaViewerEnterTransform {
    val targetTranslationY = if (currentTranslation.y >= 0f) viewportHeight.toFloat() else -viewportHeight.toFloat()
    return MediaViewerEnterTransform(
        scaleX = currentScale, scaleY = currentScale, translationX = currentTranslation.x,
        translationY = lerpFloat(currentTranslation.y, targetTranslationY, progress.coerceIn(0f, 1f)),
    )
}

internal fun mediaViewerDismissedScale(progress: Float): Float = 1f - progress.coerceIn(0f, 1f) * 0.12f

internal fun calculateMediaViewerTargetRect(windowSize: IntSize, contentSize: IntSize): IntRect {
    return calculateMediaViewerTargetRectInContainer(
        containerBounds = IntRect(left = 0, top = 0, right = windowSize.width.coerceAtLeast(0), bottom = windowSize.height.coerceAtLeast(0)),
        contentSize = contentSize,
    )
}

internal fun calculateMediaViewerTargetRectInContainer(containerBounds: IntRect, contentSize: IntSize): IntRect {
    val targetWidth = contentSize.width.coerceAtLeast(0)
    val targetHeight = contentSize.height.coerceAtLeast(0)
    val left = containerBounds.left + ((containerBounds.width - targetWidth) / 2).coerceAtLeast(0)
    val top = containerBounds.top + ((containerBounds.height - targetHeight) / 2).coerceAtLeast(0)
    return IntRect(left = left, top = top, right = left + targetWidth, bottom = top + targetHeight)
}

internal fun calculateMediaViewerEnterTransform(sourceBounds: IntRect, targetBounds: IntRect, progress: Float): MediaViewerEnterTransform {
    val normalizedProgress = progress.coerceIn(0f, 1f)
    val startScaleX = if (targetBounds.width > 0) sourceBounds.width.toFloat() / targetBounds.width.toFloat() else 1f
    val startScaleY = if (targetBounds.height > 0) sourceBounds.height.toFloat() / targetBounds.height.toFloat() else 1f
    val sourceCenterX = (sourceBounds.left + sourceBounds.right) / 2f
    val sourceCenterY = (sourceBounds.top + sourceBounds.bottom) / 2f
    val targetCenterX = (targetBounds.left + targetBounds.right) / 2f
    val targetCenterY = (targetBounds.top + targetBounds.bottom) / 2f
    return MediaViewerEnterTransform(
        scaleX = lerpFloat(startScaleX, 1f, normalizedProgress),
        scaleY = lerpFloat(startScaleY, 1f, normalizedProgress),
        translationX = lerpFloat(sourceCenterX - targetCenterX, 0f, normalizedProgress),
        translationY = lerpFloat(sourceCenterY - targetCenterY, 0f, normalizedProgress),
    )
}

internal fun calculateMediaViewerExitTransform(
    sourceBounds: IntRect, targetBounds: IntRect, currentScale: Float, currentTranslation: Offset, progress: Float,
): MediaViewerEnterTransform {
    val normalizedProgress = progress.coerceIn(0f, 1f)
    val thumbnailTransform = calculateMediaViewerEnterTransform(sourceBounds, targetBounds, 0f)
    return MediaViewerEnterTransform(
        scaleX = lerpFloat(currentScale, thumbnailTransform.scaleX, normalizedProgress),
        scaleY = lerpFloat(currentScale, thumbnailTransform.scaleY, normalizedProgress),
        translationX = lerpFloat(currentTranslation.x, thumbnailTransform.translationX, normalizedProgress),
        translationY = lerpFloat(currentTranslation.y, thumbnailTransform.translationY, normalizedProgress),
    )
}

internal fun calculateMediaViewerSourceClipInsets(
    sourceVisibleBounds: IntRect, sourceFullBounds: IntRect, targetBounds: IntRect,
): MediaViewerClipInsets {
    if (sourceFullBounds.width <= 0 || sourceFullBounds.height <= 0 || targetBounds.width <= 0 || targetBounds.height <= 0) {
        return MediaViewerClipInsets()
    }
    val widthScale = targetBounds.width.toFloat() / sourceFullBounds.width.toFloat()
    val heightScale = targetBounds.height.toFloat() / sourceFullBounds.height.toFloat()
    return MediaViewerClipInsets(
        left = (sourceVisibleBounds.left - sourceFullBounds.left).coerceAtLeast(0).toFloat() * widthScale,
        top = (sourceVisibleBounds.top - sourceFullBounds.top).coerceAtLeast(0).toFloat() * heightScale,
        right = (sourceFullBounds.right - sourceVisibleBounds.right).coerceAtLeast(0).toFloat() * widthScale,
        bottom = (sourceFullBounds.bottom - sourceVisibleBounds.bottom).coerceAtLeast(0).toFloat() * heightScale,
    )
}

internal fun calculateMediaViewerSourceCornerRadius(
    sourceCornerRadiusPx: Float, sourceFullBounds: IntRect, targetBounds: IntRect,
): Float {
    if (sourceCornerRadiusPx <= 0f || sourceFullBounds.width <= 0 || sourceFullBounds.height <= 0 || targetBounds.width <= 0 || targetBounds.height <= 0) return 0f
    val widthScale = targetBounds.width.toFloat() / sourceFullBounds.width.toFloat()
    val heightScale = targetBounds.height.toFloat() / sourceFullBounds.height.toFloat()
    return sourceCornerRadiusPx * min(widthScale, heightScale)
}

internal fun lerpMediaViewerClipInsets(start: MediaViewerClipInsets, end: MediaViewerClipInsets, progress: Float): MediaViewerClipInsets {
    val t = progress.coerceIn(0f, 1f)
    return MediaViewerClipInsets(
        left = lerpFloat(start.left, end.left, t),
        top = lerpFloat(start.top, end.top, t),
        right = lerpFloat(start.right, end.right, t),
        bottom = lerpFloat(start.bottom, end.bottom, t),
    )
}

internal fun calculateMediaViewerContentMask(
    contentWidth: Float, contentHeight: Float, clipInsets: MediaViewerClipInsets? = null, cornerRadiusPx: Float = 0f,
): MediaViewerContentMask? {
    if (contentWidth <= 0f || contentHeight <= 0f) return null
    val insets = clipInsets ?: MediaViewerClipInsets()
    val visibleLeft = insets.left.coerceIn(0f, contentWidth)
    val visibleTop = insets.top.coerceIn(0f, contentHeight)
    val visibleRight = (contentWidth - insets.right).coerceIn(visibleLeft, contentWidth)
    val visibleBottom = (contentHeight - insets.bottom).coerceIn(visibleTop, contentHeight)
    val radius = cornerRadiusPx.coerceAtLeast(0f).coerceAtMost(min(contentWidth / 2f, contentHeight / 2f))
    return MediaViewerContentMask(
        visibleBounds = Rect(left = visibleLeft, top = visibleTop, right = visibleRight, bottom = visibleBottom),
        roundedBounds = Rect(left = 0f, top = 0f, right = contentWidth, bottom = contentHeight),
        cornerRadiusPx = radius,
    )
}

internal fun shouldDelayMediaViewerThumbnailHide(
    hasLaunchSource: Boolean, hasPlayedEnterAnimation: Boolean,
    hasContentContainerBoundsOnScreen: Boolean, currentPage: Int, initialPage: Int,
): Boolean {
    return hasLaunchSource && !hasPlayedEnterAnimation &&
        (!hasContentContainerBoundsOnScreen || currentPage != initialPage)
}

internal fun shouldAnimateMediaViewerDismissToSource(
    dismissSource: MediaViewerLaunchSource, viewportBoundsOnScreen: IntRect,
): Boolean {
    return dismissSource.boundsOnScreen.left < viewportBoundsOnScreen.right &&
        dismissSource.boundsOnScreen.right > viewportBoundsOnScreen.left &&
        dismissSource.boundsOnScreen.top < viewportBoundsOnScreen.bottom &&
        dismissSource.boundsOnScreen.bottom > viewportBoundsOnScreen.top
}

internal fun mediaViewerDialogProperties(): DialogProperties = DialogProperties(
    usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
)

internal fun dismissProgressFraction(dismissOffsetY: Float, dismissDistancePx: Float): Float {
    if (dismissDistancePx <= 0f) return 0f
    return (abs(dismissOffsetY) / dismissDistancePx).coerceIn(0f, 1f)
}

internal suspend fun animateMediaViewerClose(
    enterProgress: Animatable<Float, AnimationVector1D>, closeScrimAlpha: Animatable<Float, AnimationVector1D>,
) {
    enterProgress.snapTo(1f)
    coroutineScope {
        launch {
            enterProgress.animateTo(0f, tween(MEDIA_VIEWER_CLOSE_DURATION_MS, easing = FastOutSlowInEasing))
        }
        launch {
            closeScrimAlpha.animateTo(0f, tween(MEDIA_VIEWER_CLOSE_DURATION_MS, easing = LinearOutSlowInEasing))
        }
    }
}

internal suspend fun animateMediaViewerDismissOffset(from: Float, to: Float, onUpdate: (Float) -> Unit) {
    val anim = Animatable(from)
    anim.animateTo(to, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) { onUpdate(value) }
}
