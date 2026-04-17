package io.github.baba983.nativedo.core.navigation

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Telegram-style navigation transition parameters
 */
object TelegramTransition {
    // Standard transition: 48dp slide + fade, 150ms
    val StandardDuration = 150
    val StandardSlideOffset = 48.dp
    val StandardEasing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f) // DecelerateInterpolator

    // Preview mode: scale + slide, 190ms with overshoot
    val PreviewDuration = 190
    val PreviewEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1.0f) // OvershootInterpolator

    // Predictive back: 56dp max drag, 320ms cancel / 380ms complete
    val PredictiveBackMaxOffset = 56.dp
    val PredictiveBackCancelDuration = 320
    val PredictiveBackCompleteDuration = 380
    val PredictiveBackEasing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f) // EASE_OUT_QUINT
}

/**
 * State for managing Telegram-style navigation transitions
 */
class TelegramNavigationState {
    var isTransitioning by mutableStateOf(false)
        private set

    var transitionProgress by mutableFloatStateOf(0f)
        private set

    var isPredictiveBackActive by mutableStateOf(false)
        private set

    fun startTransition() {
        isTransitioning = true
    }

    fun updateProgress(progress: Float) {
        transitionProgress = progress
    }

    fun endTransition() {
        isTransitioning = false
        transitionProgress = 0f
    }

    fun startPredictiveBack() {
        isPredictiveBackActive = true
    }

    fun endPredictiveBack() {
        isPredictiveBackActive = false
    }
}

/**
 * Composable that wraps content with Telegram-style predictive back gesture support
 */
@Composable
fun TelegramPredictiveBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
    content: @Composable (backProgress: Float) -> Unit,
) {
    var backProgress by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val maxOffsetPx = with(density) { TelegramTransition.PredictiveBackMaxOffset.toPx() }

    PredictiveBackHandler(enabled = enabled) { progress ->
        try {
            progress.collect { event ->
                // Convert swipe progress to offset (0 to 56dp)
                backProgress = event.progress
            }
            // Gesture completed - animate to finish
            onBack()
        } catch (e: Exception) {
            // Gesture cancelled - animate back to 0
            backProgress = 0f
        }
    }

    content(backProgress)
}

/**
 * Container that applies Telegram-style transition effects to its content
 */
@Composable
fun TelegramTransitionContainer(
    visible: Boolean,
    isEntering: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val slideOffsetPx = with(density) { TelegramTransition.StandardSlideOffset.toPx() }

    val alpha = remember { Animatable(if (visible) 1f else 0f) }
    val translationX = remember {
        Animatable(if (visible) 0f else (if (isEntering) slideOffsetPx else 0f))
    }

    val animationSpec: AnimationSpec<Float> = tween(
        durationMillis = TelegramTransition.StandardDuration,
        easing = TelegramTransition.StandardEasing,
    )

    LaunchedEffect(visible) {
        if (visible) {
            // Entering: slide from right + fade in
            launch { alpha.animateTo(1f, animationSpec) }
            launch { translationX.animateTo(0f, animationSpec) }
        } else {
            // Exiting: slide to right + fade out (for back navigation)
            if (!isEntering) {
                launch { alpha.animateTo(0f, animationSpec) }
                launch { translationX.animateTo(slideOffsetPx, animationSpec) }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                this.alpha = alpha.value
                this.translationX = translationX.value
            }
    ) {
        content()
    }
}

/**
 * Preview mode container with scale + slide animation (like Telegram's link preview)
 */
@Composable
fun TelegramPreviewContainer(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val slideOffsetPx = with(density) { 40.dp.toPx() }

    val alpha = remember { Animatable(if (visible) 1f else 0f) }
    val scale = remember { Animatable(if (visible) 1f else 0.7f) }
    val translationY = remember { Animatable(if (visible) 0f else slideOffsetPx) }

    val animationSpec: AnimationSpec<Float> = tween(
        durationMillis = TelegramTransition.PreviewDuration,
        easing = TelegramTransition.PreviewEasing,
    )

    LaunchedEffect(visible) {
        if (visible) {
            launch { alpha.animateTo(1f, animationSpec) }
            launch { scale.animateTo(1f, animationSpec) }
            launch { translationY.animateTo(0f, animationSpec) }
        } else {
            launch { alpha.animateTo(0f, animationSpec) }
            launch { scale.animateTo(0.9f, animationSpec) }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                this.alpha = alpha.value
                this.scaleX = scale.value
                this.scaleY = scale.value
                this.translationY = translationY.value
            }
    ) {
        content()
    }
}

/**
 * Gesture detector for edge-to-edge back navigation (like Telegram's swipe back)
 */
@Composable
fun TelegramSwipeBackGesture(
    enabled: Boolean = true,
    onSwipeProgress: (Float) -> Unit = {},
    onSwipeComplete: () -> Unit = {},
    onSwipeCancelled: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val maxOffsetPx = with(density) { TelegramTransition.PredictiveBackMaxOffset.toPx() }
    val scope = rememberCoroutineScope()
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput

                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        // Only trigger if starting from left edge (within 20dp)
                        if (offset.x < with(density) { 20.dp.toPx() }) {
                            dragOffset = 0f
                        }
                    },
                    onDragEnd = {
                        val progress = (dragOffset / maxOffsetPx).coerceIn(0f, 1f)
                        if (progress > 0.3f) {
                            // Complete the back navigation
                            onSwipeComplete()
                        } else {
                            // Cancel and animate back
                            scope.launch {
                                onSwipeCancelled()
                            }
                        }
                        dragOffset = 0f
                    },
                    onDragCancel = {
                        scope.launch {
                            onSwipeCancelled()
                        }
                        dragOffset = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        if (dragAmount > 0) { // Only allow right swipe
                            dragOffset = (dragOffset + dragAmount).coerceIn(0f, maxOffsetPx)
                            val progress = (dragOffset / maxOffsetPx).coerceIn(0f, 1f)
                            onSwipeProgress(progress)
                        }
                    }
                )
            }
    ) {
        content()
    }
}
