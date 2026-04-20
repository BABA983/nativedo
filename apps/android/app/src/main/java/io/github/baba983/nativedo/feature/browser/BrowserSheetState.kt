package io.github.baba983.nativedo.feature.browser

import kotlin.math.abs

internal sealed interface BrowserSheetAnchor {
    data object Closed : BrowserSheetAnchor

    data object Partial : BrowserSheetAnchor

    data object Fullscreen : BrowserSheetAnchor
}

internal data class BrowserSheetPresentationState(
    val openProgress: Float,
    val fullscreenProgress: Float,
    val attachedProgress: Float,
    val topOffsetPx: Float,
    val scrimAlpha: Float,
)

internal data class BrowserSheetSettle(
    val target: BrowserSheetAnchor,
    val durationMillis: Int,
)

internal const val BrowserSheetOpenDurationMillis: Int = 320
internal const val BrowserSheetCloseDurationMillis: Int = 250
internal const val BrowserSheetDismissVelocityThresholdPxPerSecond: Float = 900f
internal const val BrowserSheetFullscreenVelocityThresholdPxPerSecond: Float = -900f
internal const val BrowserSheetDismissThresholdFraction: Float = 0.25f
internal const val BrowserSheetAttachThresholdProgress: Float = 0.85f

internal fun buildBrowserSheetPresentationState(
    topOffsetPx: Float,
    partialTopOffsetPx: Float,
    closedTopOffsetPx: Float,
): BrowserSheetPresentationState {
    val safeClosedTopOffsetPx = closedTopOffsetPx.coerceAtLeast(1f)
    val safePartialTopOffsetPx = partialTopOffsetPx.coerceIn(0f, safeClosedTopOffsetPx)
    val clampedTopOffsetPx = topOffsetPx.coerceIn(0f, safeClosedTopOffsetPx)
    val openedTravel = (safeClosedTopOffsetPx - safePartialTopOffsetPx).coerceAtLeast(1f)
    val openProgress = ((safeClosedTopOffsetPx - clampedTopOffsetPx) / openedTravel)
        .coerceIn(0f, 1f)
    val fullscreenProgress = if (safePartialTopOffsetPx == 0f) {
        openProgress
    } else {
        ((safePartialTopOffsetPx - clampedTopOffsetPx) / safePartialTopOffsetPx)
            .coerceIn(0f, 1f)
    }
    val attachedProgress = buildBrowserSheetAttachedProgress(fullscreenProgress)
    val scrimAlpha = (0.10f * openProgress).coerceIn(0f, 0.10f)
    return BrowserSheetPresentationState(
        openProgress = openProgress,
        fullscreenProgress = fullscreenProgress,
        attachedProgress = attachedProgress,
        topOffsetPx = clampedTopOffsetPx,
        scrimAlpha = scrimAlpha,
    )
}

internal fun buildBrowserSheetAttachedProgress(
    fullscreenProgress: Float,
    attachThresholdProgress: Float = BrowserSheetAttachThresholdProgress,
): Float {
    val clampedProgress = fullscreenProgress.coerceIn(0f, 1f)
    val clampedThreshold = attachThresholdProgress.coerceIn(0f, 0.999f)
    if (clampedProgress <= clampedThreshold) {
        return 0f
    }
    return ((clampedProgress - clampedThreshold) / (1f - clampedThreshold)).coerceIn(0f, 1f)
}

internal fun buildBrowserSheetSettle(
    currentTopOffsetPx: Float,
    velocityPxPerSecond: Float,
    partialTopOffsetPx: Float,
    closedTopOffsetPx: Float,
    dismissThresholdFraction: Float = BrowserSheetDismissThresholdFraction,
    dismissVelocityThresholdPxPerSecond: Float = BrowserSheetDismissVelocityThresholdPxPerSecond,
    fullscreenVelocityThresholdPxPerSecond: Float = BrowserSheetFullscreenVelocityThresholdPxPerSecond,
): BrowserSheetSettle {
    val safeClosedTopOffsetPx = closedTopOffsetPx.coerceAtLeast(1f)
    val safePartialTopOffsetPx = partialTopOffsetPx.coerceIn(0f, safeClosedTopOffsetPx)
    val clampedTopOffsetPx = currentTopOffsetPx.coerceIn(0f, safeClosedTopOffsetPx)
    val dismissThresholdTopOffsetPx = safePartialTopOffsetPx +
        ((safeClosedTopOffsetPx - safePartialTopOffsetPx) * dismissThresholdFraction)

    val target = when {
        velocityPxPerSecond >= dismissVelocityThresholdPxPerSecond -> BrowserSheetAnchor.Closed
        velocityPxPerSecond <= fullscreenVelocityThresholdPxPerSecond -> BrowserSheetAnchor.Fullscreen
        clampedTopOffsetPx >= dismissThresholdTopOffsetPx -> BrowserSheetAnchor.Closed
        else -> nearestBrowserSheetAnchor(
            currentTopOffsetPx = clampedTopOffsetPx,
            partialTopOffsetPx = safePartialTopOffsetPx,
            closedTopOffsetPx = safeClosedTopOffsetPx,
        )
    }
    val targetOffsetPx = when (target) {
        BrowserSheetAnchor.Closed -> safeClosedTopOffsetPx
        BrowserSheetAnchor.Partial -> safePartialTopOffsetPx
        BrowserSheetAnchor.Fullscreen -> 0f
    }
    val travelDistancePx = abs(targetOffsetPx - clampedTopOffsetPx)
    val baseDurationMillis = if (target == BrowserSheetAnchor.Closed) {
        BrowserSheetCloseDurationMillis
    } else {
        BrowserSheetOpenDurationMillis
    }
    val durationMillis = maxOf(((baseDurationMillis.toFloat() / safeClosedTopOffsetPx) * travelDistancePx).toInt(), 180)
    return BrowserSheetSettle(
        target = target,
        durationMillis = durationMillis,
    )
}

private fun nearestBrowserSheetAnchor(
    currentTopOffsetPx: Float,
    partialTopOffsetPx: Float,
    closedTopOffsetPx: Float,
): BrowserSheetAnchor {
    val fullscreenDistance = abs(currentTopOffsetPx)
    val partialDistance = abs(currentTopOffsetPx - partialTopOffsetPx)
    val closedDistance = abs(currentTopOffsetPx - closedTopOffsetPx)
    return when (minOf(fullscreenDistance, partialDistance, closedDistance)) {
        fullscreenDistance -> BrowserSheetAnchor.Fullscreen
        partialDistance -> BrowserSheetAnchor.Partial
        else -> BrowserSheetAnchor.Closed
    }
}
