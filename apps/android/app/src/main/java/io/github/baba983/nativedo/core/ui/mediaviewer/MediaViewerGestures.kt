package io.github.baba983.nativedo.core.ui.mediaviewer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Mutable state holder for the viewer's image transform.
 * Replaces the scattered `var scale`, `var offset`, etc.
 */
internal class MediaViewerGestureState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    var dismissOffsetY by mutableFloatStateOf(0f)

    /** True while any pinch pointers are down. Used to lock pager scroll. */
    var isPinchActive by mutableStateOf(false)

    /** Set true after any zoom/pinch ends; cleared on first pan delta. Prevents offset jump. */
    var needsReanchor by mutableStateOf(false)

    /** The single cancelable job for all transform animations (fling, snap, double-tap zoom). */
    var motionJob: Job? = null

    fun cancelMotion() {
        motionJob?.cancel()
        motionJob = null
    }

    fun reset() {
        cancelMotion()
        scale = 1f
        offset = Offset.Zero
        dismissOffsetY = 0f
        isPinchActive = false
        needsReanchor = false
    }
}

internal data class MediaViewerPageSwitchSpec(
    val targetPage: Int,
    val direction: MediaViewerSwitchDirection,
)

internal enum class MediaViewerTapAction {
    ToggleChrome,
    NavigatePrevious,
    NavigateNext,
}

internal fun calculateMediaViewerEdgeTapZoneWidth(
    containerWidth: Int,
): Float = minOf(135f, containerWidth / 8f)

internal fun resolveMediaViewerTapAction(
    tapOffset: Offset,
    containerSize: IntSize,
    currentScale: Float,
    hasPrevious: Boolean,
    hasNext: Boolean,
): MediaViewerTapAction {
    if (currentScale > 1f + MEDIA_VIEWER_REST_SCALE_EPSILON) {
        return MediaViewerTapAction.ToggleChrome
    }

    val edgeWidth = calculateMediaViewerEdgeTapZoneWidth(containerSize.width)
    return when {
        tapOffset.x < edgeWidth && hasPrevious -> MediaViewerTapAction.NavigatePrevious
        tapOffset.x > containerSize.width - edgeWidth && hasNext -> MediaViewerTapAction.NavigateNext
        else -> MediaViewerTapAction.ToggleChrome
    }
}

/**
 * Unified gesture Modifier. Handles pinch-zoom, pan, dismiss-drag,
 * tap, and double-tap in a single pointer event loop.
 *
 * Modeled after Telegram's PhotoViewer.onTouchEvent state machine.
 */
internal fun Modifier.mediaViewerGestures(
    gestureState: MediaViewerGestureState,
    containerSize: IntSize,
    contentSize: IntSize,
    enabled: Boolean,
    canSwitchToPrevious: Boolean,
    canSwitchToNext: Boolean,
    currentPage: Int,
    pageTranslationX: Float,
    dismissDistancePx: Float,
    onPageTranslationDelta: (Float) -> Unit,
    onPageTranslationRelease: (Float, Float) -> Unit,
    onSwitchToPrevious: (MediaViewerPageSwitchSpec?) -> Unit,
    onSwitchToNext: (MediaViewerPageSwitchSpec?) -> Unit,
    onDismissRequest: () -> Unit,
    onToggleChrome: () -> Unit,
    onMenuDismiss: () -> Unit,
): Modifier = this.pointerInput(
    enabled,
    containerSize,
    contentSize,
    dismissDistancePx,
    canSwitchToPrevious,
    canSwitchToNext,
    currentPage,
) {
    if (!enabled) return@pointerInput
    coroutineScope {
        mediaViewerGestureLoop(
            scope = this@pointerInput,
            launchScope = this,
            gestureState = gestureState,
            containerSize = containerSize,
            contentSize = contentSize,
            dismissDistancePx = dismissDistancePx,
            canSwitchToPrevious = canSwitchToPrevious,
            canSwitchToNext = canSwitchToNext,
            currentPage = currentPage,
            pageTranslationX = pageTranslationX,
            onSwitchToPrevious = onSwitchToPrevious,
            onSwitchToNext = onSwitchToNext,
            onPageTranslationDelta = onPageTranslationDelta,
            onPageTranslationRelease = onPageTranslationRelease,
            onDismissRequest = onDismissRequest,
            onToggleChrome = onToggleChrome,
            onMenuDismiss = onMenuDismiss,
        )
    }
}

private suspend fun mediaViewerGestureLoop(
    scope: PointerInputScope,
    launchScope: CoroutineScope,
    gestureState: MediaViewerGestureState,
    containerSize: IntSize,
    contentSize: IntSize,
    dismissDistancePx: Float,
    canSwitchToPrevious: Boolean,
    canSwitchToNext: Boolean,
    currentPage: Int,
    pageTranslationX: Float,
    onPageTranslationDelta: (Float) -> Unit,
    onPageTranslationRelease: (Float, Float) -> Unit,
    onSwitchToPrevious: (MediaViewerPageSwitchSpec?) -> Unit,
    onSwitchToNext: (MediaViewerPageSwitchSpec?) -> Unit,
    onDismissRequest: () -> Unit,
    onToggleChrome: () -> Unit,
    onMenuDismiss: () -> Unit,
) = scope.awaitPointerEventScope {
    while (true) {
        val downEvent = awaitPointerEvent(PointerEventPass.Main)
        if (downEvent.type != PointerEventType.Press) continue
        val downChange = downEvent.changes.firstOrNull { it.pressed } ?: continue

        gestureState.cancelMotion()

        val downPosition = downChange.position
        var tapCandidate = true
        var wasPinching = false
        var previousPinchDistance = 0f
        var previousPinchCenter = Offset.Zero
        val dismissTriggerDistancePx = max(
            scope.viewConfiguration.touchSlop,
            dismissDistancePx / 7f,
        )

        var isDraggingDown = false
        var isPanning = false
        var dragAnchorY = downPosition.y
        var panAnchorX = downPosition.x
        var panAnchorY = downPosition.y
        var pageTranslation = pageTranslationX

        var velocityTracker = VelocityTracker()

        var allUp = false
        while (!allUp) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val pressedChanges = event.changes.filter { it.pressed }

            when {
                pressedChanges.size >= 2 -> {
                    tapCandidate = false
                    onMenuDismiss()
                    val p0 = pressedChanges[0]
                    val p1 = pressedChanges[1]
                    val currentDistance = sqrt(
                        (p1.position.x - p0.position.x).pow(2) +
                            (p1.position.y - p0.position.y).pow(2),
                    )
                    val center = Offset(
                        x = (p0.position.x + p1.position.x) / 2f,
                        y = (p0.position.y + p1.position.y) / 2f,
                    )

                    if (!gestureState.isPinchActive) {
                        gestureState.isPinchActive = true
                        previousPinchDistance = currentDistance
                        previousPinchCenter = center
                        gestureState.dismissOffsetY = 0f
                        isDraggingDown = false
                        isPanning = false
                        velocityTracker = VelocityTracker()
                    } else {
                        val transform = calculatePinchTransform(
                            previousDistance = previousPinchDistance,
                            currentDistance = currentDistance,
                            currentScale = gestureState.scale,
                            currentOffset = gestureState.offset,
                            previousCenter = previousPinchCenter,
                            currentCenter = center,
                            containerSize = containerSize,
                            contentSize = contentSize,
                        )
                        gestureState.scale = transform.scale
                        gestureState.offset = transform.offset
                    }

                    previousPinchDistance = currentDistance
                    previousPinchCenter = center
                    event.changes.forEach { it.consume() }
                    wasPinching = true
                }

                pressedChanges.size == 1 -> {
                    val change = pressedChanges[0]
                    val dx = change.position.x - downPosition.x
                    val dy = change.position.y - downPosition.y

                    if (
                        tapCandidate &&
                        (abs(dx) > scope.viewConfiguration.touchSlop || abs(dy) > scope.viewConfiguration.touchSlop)
                    ) {
                        tapCandidate = false
                    }

                    if (gestureState.isPinchActive) {
                        gestureState.isPinchActive = false
                        gestureState.needsReanchor = true
                        wasPinching = true
                        panAnchorX = change.position.x
                        panAnchorY = change.position.y
                        velocityTracker = VelocityTracker()
                        continue
                    }

                    if (!tapCandidate) {
                        onMenuDismiss()

                        if (gestureState.scale > 1f + MEDIA_VIEWER_REST_SCALE_EPSILON) {
                            if (gestureState.needsReanchor) {
                                gestureState.needsReanchor = false
                                panAnchorX = change.position.x
                                panAnchorY = change.position.y
                                velocityTracker = VelocityTracker()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                continue
                            }
                            if (!isPanning) {
                                isPanning = true
                                isDraggingDown = false
                                panAnchorX = change.position.x
                                panAnchorY = change.position.y
                                velocityTracker = VelocityTracker()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                continue
                            }
                            val moveDx = change.position.x - panAnchorX
                            val moveDy = change.position.y - panAnchorY
                            panAnchorX = change.position.x
                            panAnchorY = change.position.y

                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val panResult = applyMediaViewerPanDelta(
                                currentOffset = gestureState.offset,
                                delta = Offset(moveDx, moveDy),
                                scale = gestureState.scale,
                                containerSize = containerSize,
                                contentSize = contentSize,
                                hasAdjacentPrevious = canSwitchToPrevious,
                                hasAdjacentNext = canSwitchToNext,
                                currentPageTranslationX = pageTranslation,
                            )
                            gestureState.offset = panResult.offset
                            pageTranslation = panResult.pageTranslationX
                            onPageTranslationDelta(pageTranslation)
                            change.consume()
                        } else {
                            if (!isDraggingDown && shouldStartMediaViewerDismissDrag(
                                    totalDx = dx,
                                    totalDy = dy,
                                    dismissTriggerDistancePx = dismissTriggerDistancePx,
                                )
                            ) {
                                isDraggingDown = true
                                dragAnchorY = change.position.y
                                velocityTracker = VelocityTracker()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                            }
                            if (isDraggingDown) {
                                val delta = change.position.y - dragAnchorY
                                dragAnchorY = change.position.y
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                gestureState.dismissOffsetY += delta
                                change.consume()
                            }
                        }
                    }
                }

                pressedChanges.isEmpty() -> {
                    allUp = true

                    if (gestureState.isPinchActive || wasPinching) {
                        gestureState.isPinchActive = false
                        gestureState.needsReanchor = true
                        val settled = settleMediaViewerScale(gestureState.scale)
                        val settledOffset = if (settled <= 1f + MEDIA_VIEWER_REST_SCALE_EPSILON) {
                            Offset.Zero
                        } else {
                            clampMediaViewerOffset(
                                proposed = gestureState.offset,
                                scale = settled,
                                containerSize = containerSize,
                                contentSize = contentSize,
                            )
                        }
                        if (settled != gestureState.scale || settledOffset != gestureState.offset) {
                            launchScope.launch {
                                animateMediaViewerState(
                                    fromScale = gestureState.scale,
                                    toScale = settled,
                                    fromOffset = gestureState.offset,
                                    toOffset = settledOffset,
                                ) { s, o ->
                                    gestureState.scale = s
                                    gestureState.offset = o
                                }
                            }.also { gestureState.motionJob = it }
                        }
                    }

                    if (isDraggingDown) {
                        val dismissVelocityY = runCatching {
                            velocityTracker.calculateVelocity().y
                        }.getOrDefault(0f)
                        if (shouldDismissViewer(
                                verticalOffset = gestureState.dismissOffsetY,
                                velocityY = dismissVelocityY,
                                dismissDistance = dismissDistancePx,
                            )
                        ) {
                            onDismissRequest()
                        } else {
                            launchScope.launch {
                                val anim = Animatable(gestureState.dismissOffsetY)
                                anim.animateTo(
                                    0f,
                                    tween(
                                        durationMillis = 250,
                                        easing = FastOutSlowInEasing,
                                    ),
                                ) { gestureState.dismissOffsetY = value }
                            }.also { gestureState.motionJob = it }
                        }
                    }

                    if (isPanning && gestureState.scale > 1f + MEDIA_VIEWER_REST_SCALE_EPSILON) {
                        val velocityX = runCatching {
                            velocityTracker.calculateVelocity().x
                        }.getOrDefault(0f)
                        val pageSwitchSpec = resolveMediaViewerPageSwitchSpec(
                            currentPage = currentPage,
                            offset = gestureState.offset,
                            pageTranslationX = pageTranslation,
                            scale = gestureState.scale,
                            containerSize = containerSize,
                            contentSize = contentSize,
                            canSwitchToPrevious = canSwitchToPrevious,
                            canSwitchToNext = canSwitchToNext,
                        )
                        if (pageSwitchSpec != null) {
                            gestureState.cancelMotion()
                            when (pageSwitchSpec.direction) {
                                MediaViewerSwitchDirection.Previous -> onSwitchToPrevious(pageSwitchSpec)
                                MediaViewerSwitchDirection.Next -> onSwitchToNext(pageSwitchSpec)
                            }
                            continue
                        }
                        if (pageTranslation != 0f) {
                            onPageTranslationRelease(pageTranslation, velocityX)
                        }

                        val velocity = runCatching {
                            velocityTracker.calculateVelocity()
                        }.getOrNull()
                        if (velocity != null && velocityMagnitude(
                                Offset(velocity.x, velocity.y)
                            ) > MEDIA_VIEWER_FLING_STOP_VELOCITY_PX_PER_SEC
                        ) {
                            launchScope.launch {
                                animateMediaViewerFling(
                                    fromOffset = gestureState.offset,
                                    initialVelocity = Offset(velocity.x, velocity.y),
                                    scale = gestureState.scale,
                                    containerSize = containerSize,
                                    contentSize = contentSize,
                                ) { gestureState.offset = it }
                            }.also { gestureState.motionJob = it }
                        } else {
                            // Snap to bounds
                            val settledOffset = clampMediaViewerOffset(
                                proposed = gestureState.offset,
                                scale = gestureState.scale,
                                containerSize = containerSize,
                                contentSize = contentSize,
                            )
                            if (settledOffset != gestureState.offset) {
                                launchScope.launch {
                                    animateMediaViewerState(
                                        fromScale = gestureState.scale,
                                        toScale = gestureState.scale,
                                        fromOffset = gestureState.offset,
                                        toOffset = settledOffset,
                                    ) { _, o -> gestureState.offset = o }
                                }.also { gestureState.motionJob = it }
                            }
                        }
                    }

                    if (!isPanning && pageTranslation != 0f) {
                        val velocityX = runCatching {
                            velocityTracker.calculateVelocity().x
                        }.getOrDefault(0f)
                        onPageTranslationRelease(pageTranslation, velocityX)
                    }

                    if (tapCandidate) {
                        val secondTap = withTimeoutOrNull(300L) {
                            while (true) {
                                val nextEvent = awaitPointerEvent(PointerEventPass.Main)
                                if (nextEvent.type == PointerEventType.Press) {
                                    val nextChange = nextEvent.changes.firstOrNull { it.pressed }
                                    if (nextChange != null) {
                                        return@withTimeoutOrNull nextChange.position
                                    }
                                }
                            }
                            @Suppress("UNREACHABLE_CODE")
                            null
                        }
                        if (secondTap != null) {
                            var secondUp = false
                            while (!secondUp) {
                                val ev = awaitPointerEvent(PointerEventPass.Main)
                                if (ev.changes.none { it.pressed }) secondUp = true
                            }
                            gestureState.cancelMotion()
                            onMenuDismiss()
                            gestureState.dismissOffsetY = 0f
                            val targetScale = toggleMediaViewerScale(gestureState.scale)
                            val targetOffset = mediaViewerDoubleTapTargetOffset(
                                tapOffset = secondTap,
                                currentScale = gestureState.scale,
                                targetScale = targetScale,
                                currentOffset = gestureState.offset,
                                containerSize = containerSize,
                                contentSize = contentSize,
                            )
                            launchScope.launch {
                                animateMediaViewerState(
                                    fromScale = gestureState.scale,
                                    toScale = targetScale,
                                    fromOffset = gestureState.offset,
                                    toOffset = targetOffset,
                                ) { s, o ->
                                    gestureState.scale = s
                                    gestureState.offset = o
                                }
                            }.also { gestureState.motionJob = it }
                            gestureState.needsReanchor = true
                        } else {
                            when (
                                resolveMediaViewerTapAction(
                                    tapOffset = downPosition,
                                    containerSize = containerSize,
                                    currentScale = gestureState.scale,
                                    hasPrevious = canSwitchToPrevious,
                                    hasNext = canSwitchToNext,
                                )
                            ) {
                                MediaViewerTapAction.ToggleChrome -> onToggleChrome()
                                MediaViewerTapAction.NavigatePrevious -> onSwitchToPrevious(null)
                                MediaViewerTapAction.NavigateNext -> onSwitchToNext(null)
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun shouldStartMediaViewerPageSwitchDrag(
    totalDx: Float,
    totalDy: Float,
    touchSlop: Float,
    canSwitchToPrevious: Boolean,
    canSwitchToNext: Boolean,
): Boolean {
    if (!canSwitchToPrevious && !canSwitchToNext) return false
    val absDx = abs(totalDx)
    val absDy = abs(totalDy)
    if (absDx < touchSlop || absDx <= absDy) return false
    return when {
        totalDx > 0f -> canSwitchToPrevious
        totalDx < 0f -> canSwitchToNext
        else -> false
    }
}

internal fun updateMediaViewerPageTranslation(
    currentTranslationX: Float,
    deltaX: Float,
    containerWidth: Float,
    canSwitchToPrevious: Boolean,
    canSwitchToNext: Boolean,
): Float {
    if (containerWidth <= 0f) return 0f
    val nextTranslation = currentTranslationX + deltaX
    val minTranslation = if (canSwitchToNext) -containerWidth else 0f
    val maxTranslation = if (canSwitchToPrevious) containerWidth else 0f
    return nextTranslation.coerceIn(minTranslation, maxTranslation)
}

// --- Animations ---

internal suspend fun animateMediaViewerState(
    fromScale: Float,
    toScale: Float,
    fromOffset: Offset,
    toOffset: Offset,
    onUpdate: (Float, Offset) -> Unit,
) = coroutineScope {
    val scaleAnim = Animatable(fromScale)
    val xAnim = Animatable(fromOffset.x)
    val yAnim = Animatable(fromOffset.y)
    val spec = tween<Float>(
        durationMillis = MEDIA_VIEWER_SNAP_ANIM_DURATION_MS,
        easing = FastOutSlowInEasing,
    )
    var animatedScale = fromScale
    var animatedOffset = fromOffset
    launch {
        scaleAnim.animateTo(toScale, spec) {
            animatedScale = value
            onUpdate(animatedScale, animatedOffset)
        }
    }
    launch {
        xAnim.animateTo(toOffset.x, spec) {
            animatedOffset = animatedOffset.copy(x = value)
            onUpdate(animatedScale, animatedOffset)
        }
    }
    launch {
        yAnim.animateTo(toOffset.y, spec) {
            animatedOffset = animatedOffset.copy(y = value)
            onUpdate(animatedScale, animatedOffset)
        }
    }
}

internal suspend fun animateMediaViewerFling(
    fromOffset: Offset,
    initialVelocity: Offset,
    scale: Float,
    containerSize: IntSize,
    contentSize: IntSize,
    onUpdate: (Offset) -> Unit,
) {
    var currentOffset = fromOffset
    var currentVelocity = initialVelocity
    var previousFrameTimeNanos = androidx.compose.runtime.withFrameNanos { it }

    while (velocityMagnitude(currentVelocity) >= MEDIA_VIEWER_FLING_STOP_VELOCITY_PX_PER_SEC) {
        val frameTimeNanos = androidx.compose.runtime.withFrameNanos { it }
        val deltaSeconds = ((frameTimeNanos - previousFrameTimeNanos) / 1_000_000_000f)
            .coerceIn(0.001f, 0.032f)
        previousFrameTimeNanos = frameTimeNanos

        val proposedNext = currentOffset + Offset(
            x = currentVelocity.x * deltaSeconds,
            y = currentVelocity.y * deltaSeconds,
        )
        val nextOffset = clampMediaViewerOffset(
            proposed = proposedNext,
            scale = scale,
            containerSize = containerSize,
            contentSize = contentSize,
            extraSlack = MEDIA_VIEWER_PAN_SLACK_PX,
        )
        val hitBoundary = nextOffset != proposedNext
        currentOffset = nextOffset
        onUpdate(currentOffset)

        val decay = MEDIA_VIEWER_FLING_DECAY_PER_FRAME.pow(deltaSeconds * 60f)
        currentVelocity = Offset(
            x = currentVelocity.x * decay,
            y = currentVelocity.y * decay,
        )
        if (hitBoundary) {
            currentVelocity = Offset(
                x = currentVelocity.x * 0.72f,
                y = currentVelocity.y * 0.72f,
            )
        }
    }

    // Settle to final clamped position
    val settledOffset = clampMediaViewerOffset(
        proposed = currentOffset,
        scale = scale,
        containerSize = containerSize,
        contentSize = contentSize,
    )
    if (settledOffset != currentOffset) {
        animateMediaViewerState(
            fromScale = scale,
            toScale = scale,
            fromOffset = currentOffset,
            toOffset = settledOffset,
        ) { _, o -> onUpdate(o) }
    }
}
