package io.github.baba983.nativedo.core.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Telegram-style page scaffold with custom edge-swipe-to-go-back gesture.
 *
 * This helper remains for standalone routes such as login and examples.
 * Pushed native pages use `NativePageStackHost` as their only back-motion owner.
 *
 * Unlike PredictiveBackHandler (which hands visuals to the system), this
 * component captures touch events from the left edge itself, so we fully
 * control the translation + scale + dim animation — matching Telegram's
 * "card being pushed away" feel.
 *
 * Gesture rules (match Telegram):
 *  - Must start within [EdgeTriggerWidth] from the left edge.
 *  - Horizontal drag must dominate (otherwise cede to vertical scrolling).
 *  - Release with progress >= [CompletionThreshold] triggers back.
 *
 * On release/cancel, the card springs back via [CancelDuration] /
 * [CompleteDuration] with an ease-out-quint curve.
 */
@Composable
fun TelegramStyleScaffold(
    canNavigateBack: Boolean,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    enableSwipeBack: Boolean = true,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // How far the card travels when fully pulled (before release).
    val maxTravelPx = with(density) { 80.dp.toPx() }
    // Left-edge hit zone width — only drags that start here trigger back.
    val edgeTriggerPx = with(density) { 24.dp.toPx() }
    // Horizontal dominance threshold before we claim the gesture.
    val slopPx = with(density) { 8.dp.toPx() }

    // Animated visual state. Fraction 0..1 drives translation/scale/dim together.
    val fraction = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }

    BackHandler(enabled = canNavigateBack && !isDragging) {
        onNavigateBack()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (enableSwipeBack && canNavigateBack) {
                    Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            // Wait for a touch-down anywhere.
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (down.position.x > edgeTriggerPx) return@awaitEachGesture

                            var totalDx = 0f
                            var totalDy = 0f
                            var claimed = false

                            // Watch the drag; decide whether it's an edge-back swipe.
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    // Finger lifted — settle.
                                    if (claimed) {
                                        isDragging = false
                                        val progress = fraction.value
                                        scope.launch {
                                            if (progress >= CompletionThreshold) {
                                                // Complete: glide the card away, then pop.
                                                fraction.animateTo(
                                                    targetValue = 1.4f,
                                                    animationSpec = tween(
                                                        durationMillis = CompleteDuration,
                                                        easing = EaseOutQuint,
                                                    ),
                                                )
                                                onNavigateBack()
                                                fraction.snapTo(0f)
                                            } else {
                                                // Cancel: spring back home.
                                                fraction.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = tween(
                                                        durationMillis = CancelDuration,
                                                        easing = EaseOutQuint,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                    break
                                }

                                val delta = change.positionChange()
                                totalDx += delta.x
                                totalDy += delta.y

                                if (!claimed) {
                                    // Claim only when it's clearly a right-going horizontal drag.
                                    if (abs(totalDx) > slopPx && abs(totalDx) > abs(totalDy)) {
                                        if (totalDx > 0) {
                                            claimed = true
                                            isDragging = true
                                            change.consume()
                                        } else {
                                            break // left swipe — not ours
                                        }
                                    } else if (abs(totalDy) > slopPx) {
                                        break // vertical scroll — let children handle it
                                    }
                                } else {
                                    change.consume()
                                    // Linear fraction until maxTravelPx, then strong rubber-band.
                                    val raw = (totalDx / maxTravelPx).coerceAtLeast(0f)
                                    val eased = if (raw <= 1f) raw else 1f + (raw - 1f) * 0.25f
                                    scope.launch { fraction.snapTo(eased) }
                                }
                            }
                        }
                    }
                } else Modifier
            ),
    ) {
        // Dim overlay under the card — 0 → 0.35 alpha while pulling.
        val dimAlpha = (fraction.value.coerceIn(0f, 1f)) * 0.35f
        if (dimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dimAlpha)),
            )
        }

        // The card itself: slides right and scales down together.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = fraction.value
                    translationX = p * maxTravelPx
                    val s = 1f - p * 0.08f // 1.00 → 0.92
                    scaleX = s.coerceAtLeast(0.85f)
                    scaleY = s.coerceAtLeast(0.85f)
                },
        ) {
            content()
        }
    }
}

// --- Tunables (kept local so they're obvious when adjusting feel) ---

/** Release-progress at or above which we commit the back navigation. */
private const val CompletionThreshold = 0.4f

/** How long the spring-back-to-home animation takes on cancel. */
private const val CancelDuration = 280

/** How long the complete-and-pop animation takes. */
private const val CompleteDuration = 300

/** CubicBezier(0.22, 1, 0.36, 1) — Telegram's EASE_OUT_QUINT. */
private val EaseOutQuint = androidx.compose.animation.core.CubicBezierEasing(
    0.22f, 1f, 0.36f, 1f,
)

/**
 * Simplified version for scenes that don't need back navigation.
 */
@Composable
fun TelegramStyleScene(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        content()
    }
}
