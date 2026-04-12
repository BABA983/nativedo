package io.github.baba983.nativedo.core.ui.mediaviewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.sqrt

// --- Constants ---
internal const val MEDIA_VIEWER_ZOOMED_SCALE = 3f
internal const val MEDIA_VIEWER_MAX_SCALE = 4f
internal const val MEDIA_VIEWER_MIN_INTERACTIVE_SCALE = 0.88f
internal const val MEDIA_VIEWER_MAX_INTERACTIVE_SCALE = 4.6f
internal const val MEDIA_VIEWER_PAN_SLACK_PX = 96f
internal const val MEDIA_VIEWER_SNAP_ANIM_DURATION_MS = 180
internal const val MEDIA_VIEWER_FLING_STOP_VELOCITY_PX_PER_SEC = 90f
internal const val MEDIA_VIEWER_FLING_DECAY_PER_FRAME = 0.93f
internal const val MEDIA_VIEWER_DISMISS_VELOCITY_THRESHOLD = 2_000f
internal const val MEDIA_VIEWER_REST_SCALE_EPSILON = 0.01f
internal const val MEDIA_VIEWER_REST_DISMISS_EPSILON = 0.5f
internal const val MEDIA_VIEWER_REST_OFFSET_EPSILON = 0.5f

// --- Data classes ---
internal data class PinchTransform(
    val scale: Float,
    val offset: Offset,
)

internal data class MediaViewerPanResult(
    val offset: Offset,
    val pageTranslationX: Float,
)

internal enum class MediaViewerPageSwitchDirection {
    Previous,
    Next,
}

// --- Functions ---
internal fun clampMediaViewerOffset(
    proposed: Offset,
    scale: Float,
    containerSize: IntSize,
    contentSize: IntSize,
    extraSlack: Float = 0f,
): Offset {
    if (scale <= 1f) return Offset.Zero
    val maxX = ((contentSize.width * scale) - containerSize.width)
        .coerceAtLeast(0f) / 2f + extraSlack
    val maxY = ((contentSize.height * scale) - containerSize.height)
        .coerceAtLeast(0f) / 2f + extraSlack
    return Offset(
        x = proposed.x.coerceIn(-maxX, maxX),
        y = proposed.y.coerceIn(-maxY, maxY),
    )
}

internal fun settleMediaViewerScale(
    currentScale: Float,
): Float = currentScale.coerceIn(1f, MEDIA_VIEWER_MAX_SCALE)

internal fun toggleMediaViewerScale(
    currentScale: Float,
    zoomedScale: Float = MEDIA_VIEWER_ZOOMED_SCALE,
): Float = if (currentScale > 1f) 1f else zoomedScale

internal fun isMediaViewerAtRest(
    scale: Float,
    offset: Offset,
    dismissOffsetY: Float,
): Boolean {
    return abs(scale - 1f) <= MEDIA_VIEWER_REST_SCALE_EPSILON &&
        abs(offset.x) <= MEDIA_VIEWER_REST_OFFSET_EPSILON &&
        abs(offset.y) <= MEDIA_VIEWER_REST_OFFSET_EPSILON &&
        abs(dismissOffsetY) <= MEDIA_VIEWER_REST_DISMISS_EPSILON
}

internal fun shouldStartMediaViewerDismissDrag(
    totalDx: Float,
    totalDy: Float,
    dismissTriggerDistancePx: Float,
): Boolean {
    val absDx = abs(totalDx)
    val absDy = abs(totalDy)
    return absDy >= dismissTriggerDistancePx && absDy / 2f > absDx
}

internal fun applyMediaViewerPanDelta(
    currentOffset: Offset,
    delta: Offset,
    scale: Float,
    containerSize: IntSize,
    contentSize: IntSize,
    hasAdjacentPrevious: Boolean = false,
    hasAdjacentNext: Boolean = false,
    currentPageTranslationX: Float = 0f,
): MediaViewerPanResult {
    val boundedOffset = clampMediaViewerOffset(
        proposed = currentOffset,
        scale = scale,
        containerSize = containerSize,
        contentSize = contentSize,
    )
    val proposedOffset = currentOffset + delta
    val clampedOffset = clampMediaViewerOffset(
        proposed = proposedOffset,
        scale = scale,
        containerSize = containerSize,
        contentSize = contentSize,
    )
    val horizontalOverscroll = proposedOffset.x - clampedOffset.x
    val pageTranslationX = when {
        horizontalOverscroll > 0f && hasAdjacentPrevious -> currentPageTranslationX + horizontalOverscroll
        horizontalOverscroll < 0f && hasAdjacentNext -> currentPageTranslationX + horizontalOverscroll
        else -> currentPageTranslationX
    }.coerceIn(
        minimumValue = if (hasAdjacentNext) -containerSize.width.toFloat() else 0f,
        maximumValue = if (hasAdjacentPrevious) containerSize.width.toFloat() else 0f,
    )
    return MediaViewerPanResult(
        offset = clampedOffset,
        pageTranslationX = pageTranslationX,
    )
}

internal fun resolveMediaViewerPageSwitchSpec(
    currentPage: Int,
    offset: Offset,
    pageTranslationX: Float,
    scale: Float,
    containerSize: IntSize,
    contentSize: IntSize,
    canSwitchToPrevious: Boolean,
    canSwitchToNext: Boolean,
): MediaViewerPageSwitchSpec? {
    if (scale <= 1f) return null
    val boundedOffset = clampMediaViewerOffset(
        proposed = offset,
        scale = scale,
        containerSize = containerSize,
        contentSize = contentSize,
    )
    val horizontalOverscroll = offset.x - boundedOffset.x + pageTranslationX
    val switchThresholdPx = containerSize.width / 3f
    return when {
        horizontalOverscroll < -switchThresholdPx && canSwitchToNext -> {
            MediaViewerPageSwitchSpec(
                targetPage = currentPage + 1,
                direction = MediaViewerSwitchDirection.Next,
            )
        }
        horizontalOverscroll > switchThresholdPx && canSwitchToPrevious -> {
            MediaViewerPageSwitchSpec(
                targetPage = currentPage - 1,
                direction = MediaViewerSwitchDirection.Previous,
            )
        }
        else -> null
    }
}

internal fun mediaViewerDoubleTapTargetOffset(
    tapOffset: Offset,
    currentScale: Float,
    targetScale: Float,
    currentOffset: Offset,
    containerSize: IntSize,
    contentSize: IntSize,
): Offset {
    if (targetScale <= 1f) return Offset.Zero
    val containerCenterX = containerSize.width / 2f
    val containerCenterY = containerSize.height / 2f
    val relativeTapX = tapOffset.x - containerCenterX
    val relativeTapY = tapOffset.y - containerCenterY
    val scaleFactor = targetScale / currentScale
    val proposed = Offset(
        x = relativeTapX - (relativeTapX - currentOffset.x) * scaleFactor,
        y = relativeTapY - (relativeTapY - currentOffset.y) * scaleFactor,
    )
    return clampMediaViewerOffset(
        proposed = proposed,
        scale = targetScale,
        containerSize = containerSize,
        contentSize = contentSize,
    )
}

internal fun calculatePinchTransform(
    previousDistance: Float,
    currentDistance: Float,
    currentScale: Float,
    currentOffset: Offset,
    previousCenter: Offset,
    currentCenter: Offset,
    containerSize: IntSize,
    contentSize: IntSize,
): PinchTransform {
    val safeCurrentScale = currentScale.coerceAtLeast(0.0001f)
    val nextScale = if (previousDistance > 0f && currentDistance > 0f) {
        (safeCurrentScale * currentDistance / previousDistance)
            .coerceIn(MEDIA_VIEWER_MIN_INTERACTIVE_SCALE, MEDIA_VIEWER_MAX_INTERACTIVE_SCALE)
    } else {
        safeCurrentScale.coerceIn(MEDIA_VIEWER_MIN_INTERACTIVE_SCALE, MEDIA_VIEWER_MAX_INTERACTIVE_SCALE)
    }
    val scaleFactor = nextScale / safeCurrentScale
    val containerCenterX = containerSize.width / 2f
    val containerCenterY = containerSize.height / 2f
    val previousRelativeCenterX = previousCenter.x - containerCenterX
    val previousRelativeCenterY = previousCenter.y - containerCenterY
    val currentRelativeCenterX = currentCenter.x - containerCenterX
    val currentRelativeCenterY = currentCenter.y - containerCenterY
    val proposedOffset = Offset(
        x = currentRelativeCenterX - (previousRelativeCenterX - currentOffset.x) * scaleFactor,
        y = currentRelativeCenterY - (previousRelativeCenterY - currentOffset.y) * scaleFactor,
    )
    return PinchTransform(
        scale = nextScale,
        // Match Telegram more closely: allow transient overshoot while pinching,
        // then settle/clamp when the pinch ends.
        offset = proposedOffset,
    )
}

internal fun isTapInsideContent(
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

internal fun shouldDismissViewer(
    verticalOffset: Float,
    velocityY: Float,
    dismissDistance: Float,
): Boolean {
    return abs(verticalOffset) >= dismissDistance ||
        abs(velocityY) >= MEDIA_VIEWER_DISMISS_VELOCITY_THRESHOLD
}

internal fun lerpFloat(start: Float, stop: Float, progress: Float): Float =
    start + (stop - start) * progress

internal fun velocityMagnitude(velocity: Offset): Float =
    sqrt(velocity.x * velocity.x + velocity.y * velocity.y)

/**
 * Easing equivalent to Android's `DecelerateInterpolator(1.5f)`.
 * Formula: `1 - (1 - t)^(2 * factor)` where factor = 1.5, i.e. `1 - (1-t)^3`.
 */
internal val MediaViewerDecelerateEasing = androidx.compose.animation.core.Easing { fraction ->
    val t = fraction.coerceIn(0f, 1f)
    1f - (1f - t) * (1f - t) * (1f - t)
}
