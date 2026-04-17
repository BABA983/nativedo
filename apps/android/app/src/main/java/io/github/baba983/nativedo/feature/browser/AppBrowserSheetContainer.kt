package io.github.baba983.nativedo.feature.browser

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import io.github.baba983.nativedo.core.navigation.AppBrowserOpenRequest
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderContent
import io.github.baba983.nativedo.ui.theme.NativeDoEasing
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.launch

private val AppBrowserSheetHandleAreaHeight = 24.dp
private val AppBrowserSheetHandleWidth = 42.dp
private val AppBrowserSheetHandleHeight = 4.dp
private val AppBrowserSheetCornerRadius = 28.dp
private const val AppBrowserSheetPartialTopFraction = 0.18f
private const val AppBrowserSheetDismissResistance = 0.72f

@Composable
internal fun AppBrowserSheetContainer(
    url: String,
    onOpenLink: (String) -> Unit,
    onOpenReader: (AppBrowserReaderContent, AppBrowserOpenRequest?) -> Unit,
    onClose: () -> Unit,
) {
    val colors = NativeDoThemeExtras.colors
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val statusBarInsetDp = remember(view, density) {
        with(density) {
            (view.rootWindowInsets?.systemWindowInsetTop ?: 0).toDp()
        }
    }
    val screenHeightPx = remember(configuration.screenHeightDp, density) {
        with(density) { configuration.screenHeightDp.dp.toPx() }.coerceAtLeast(1f)
    }
    val statusBarInsetPx = remember(statusBarInsetDp, density) {
        with(density) { statusBarInsetDp.toPx() }
    }
    val handleAreaPx = remember(density) {
        with(density) { AppBrowserSheetHandleAreaHeight.toPx() }
    }
    val minimumTopRevealPx = remember(density, statusBarInsetPx, handleAreaPx) {
        statusBarInsetPx + handleAreaPx + with(density) { 68.dp.toPx() }
    }
    val partialTopOffsetPx = remember(screenHeightPx, minimumTopRevealPx) {
        max(screenHeightPx * AppBrowserSheetPartialTopFraction, minimumTopRevealPx)
    }
    val defaultOpenAnchor = AppBrowserSheetAnchor.Fullscreen
    val sheetTopOffsetPx = remember(url, screenHeightPx) {
        Animatable(screenHeightPx)
    }
    var enterPlayed by remember(url) { mutableStateOf(false) }
    var isClosing by remember(url) { mutableStateOf(false) }
    var isDragging by remember(url) { mutableStateOf(false) }
    var dragTopOffsetPx by remember(url) { mutableStateOf(screenHeightPx) }
    var browserPrefersFullscreen by remember(url) { mutableStateOf(false) }
    var userLockedFullscreen by remember(url) { mutableStateOf(false) }

    suspend fun animateToAnchor(
        target: AppBrowserSheetAnchor,
        durationMillis: Int? = null,
    ) {
        val targetOffsetPx = when (target) {
            AppBrowserSheetAnchor.Closed -> screenHeightPx
            AppBrowserSheetAnchor.Partial -> partialTopOffsetPx
            AppBrowserSheetAnchor.Fullscreen -> 0f
        }
        if (durationMillis == null) {
            sheetTopOffsetPx.animateTo(
                targetValue = targetOffsetPx,
                animationSpec = spring(
                    stiffness = 620f,
                    dampingRatio = 0.86f,
                ),
            )
        } else {
            sheetTopOffsetPx.animateTo(
                targetValue = targetOffsetPx,
                animationSpec = tween(
                    durationMillis = durationMillis,
                    easing = NativeDoEasing.BrowserDismiss,
                ),
            )
        }
    }

    fun requestDismiss(durationMillis: Int? = null) {
        if (isClosing) {
            return
        }
        isClosing = true
        userLockedFullscreen = false
        coroutineScope.launch {
            if (isDragging) {
                sheetTopOffsetPx.snapTo(dragTopOffsetPx)
                isDragging = false
            }
            animateToAnchor(
                target = AppBrowserSheetAnchor.Closed,
                durationMillis = durationMillis ?: 220,
            )
            onClose()
        }
    }

    LaunchedEffect(screenHeightPx, partialTopOffsetPx) {
        if (enterPlayed) {
            return@LaunchedEffect
        }
        enterPlayed = true
        sheetTopOffsetPx.snapTo(screenHeightPx)
        val defaultOpenOffsetPx = when (defaultOpenAnchor) {
            AppBrowserSheetAnchor.Closed -> screenHeightPx
            AppBrowserSheetAnchor.Partial -> partialTopOffsetPx
            AppBrowserSheetAnchor.Fullscreen -> 0f
        }
        sheetTopOffsetPx.animateTo(
            targetValue = defaultOpenOffsetPx,
            animationSpec = tween(
                durationMillis = 220,
                easing = NativeDoEasing.EaseOutQuint,
            ),
        )
        if (defaultOpenAnchor == AppBrowserSheetAnchor.Fullscreen) {
            userLockedFullscreen = true
        }
    }

    LaunchedEffect(browserPrefersFullscreen, userLockedFullscreen, partialTopOffsetPx) {
        if (isClosing || isDragging) {
            return@LaunchedEffect
        }
        val target = when {
            browserPrefersFullscreen || userLockedFullscreen -> AppBrowserSheetAnchor.Fullscreen
            defaultOpenAnchor == AppBrowserSheetAnchor.Fullscreen -> AppBrowserSheetAnchor.Fullscreen
            else -> AppBrowserSheetAnchor.Partial
        }
        val targetOffsetPx = when (target) {
            AppBrowserSheetAnchor.Closed -> screenHeightPx
            AppBrowserSheetAnchor.Partial -> partialTopOffsetPx
            AppBrowserSheetAnchor.Fullscreen -> 0f
        }
        if (abs(sheetTopOffsetPx.value - targetOffsetPx) > 1f) {
            animateToAnchor(target)
        }
    }

    val currentTopOffsetPx = if (isDragging) {
        dragTopOffsetPx
    } else {
        sheetTopOffsetPx.value
    }
    val presentationState = remember(currentTopOffsetPx, partialTopOffsetPx, screenHeightPx) {
        buildAppBrowserSheetPresentationState(
            topOffsetPx = currentTopOffsetPx,
            partialTopOffsetPx = partialTopOffsetPx,
            closedTopOffsetPx = screenHeightPx,
        )
    }
    val sheetHeightDp = remember(presentationState.topOffsetPx, screenHeightPx, density) {
        with(density) {
            (screenHeightPx - presentationState.topOffsetPx).coerceAtLeast(0f).toDp()
        }
    }
    val cornerRadius = lerp(
        start = AppBrowserSheetCornerRadius,
        stop = 0.dp,
        fraction = presentationState.fullscreenProgress,
    )
    val topInset = AppBrowserSheetHandleAreaHeight + (statusBarInsetDp * presentationState.fullscreenProgress)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = presentationState.scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { requestDismiss() },
                ),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sheetHeightDp)
                .clip(
                    RoundedCornerShape(
                        topStart = cornerRadius,
                        topEnd = cornerRadius,
                    ),
                )
                .background(colors.backgroundBase),
        ) {
            AppBrowserRoute(
                url = url,
                onOpenLink = onOpenLink,
                onOpenReader = onOpenReader,
                onClose = { requestDismiss() },
                presentationMode = AppBrowserContainerMode.Sheet,
                presentationProgress = presentationState.openedProgress,
                topInset = topInset,
                onSheetFullscreenPreferenceChanged = { prefersFullscreen ->
                    browserPrefersFullscreen = prefersFullscreen
                },
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(AppBrowserSheetHandleAreaHeight)
                    .pointerInput(screenHeightPx, partialTopOffsetPx, browserPrefersFullscreen) {
                        var velocityTracker = VelocityTracker()
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                velocityTracker = VelocityTracker()
                                velocityTracker.addPosition(0L, offset)
                                dragTopOffsetPx = currentTopOffsetPx
                                isDragging = true
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                val adjustedDragAmount = if (dragTopOffsetPx <= partialTopOffsetPx && dragAmount > 0f) {
                                    dragAmount * AppBrowserSheetDismissResistance
                                } else {
                                    dragAmount
                                }
                                dragTopOffsetPx = (dragTopOffsetPx + adjustedDragAmount)
                                    .coerceIn(0f, screenHeightPx)
                            },
                            onDragCancel = {
                                val settle = buildAppBrowserSheetSettle(
                                    currentTopOffsetPx = dragTopOffsetPx,
                                    velocityPxPerSecond = 0f,
                                    partialTopOffsetPx = partialTopOffsetPx,
                                    closedTopOffsetPx = screenHeightPx,
                                )
                                coroutineScope.launch {
                                    sheetTopOffsetPx.snapTo(dragTopOffsetPx)
                                    isDragging = false
                                    userLockedFullscreen = settle.target == AppBrowserSheetAnchor.Fullscreen
                                    if (settle.target == AppBrowserSheetAnchor.Closed) {
                                        requestDismiss(settle.durationMillis)
                                    } else {
                                        animateToAnchor(
                                            target = settle.target,
                                            durationMillis = settle.durationMillis,
                                        )
                                    }
                                }
                            },
                            onDragEnd = {
                                val settle = buildAppBrowserSheetSettle(
                                    currentTopOffsetPx = dragTopOffsetPx,
                                    velocityPxPerSecond = velocityTracker.calculateVelocity().y,
                                    partialTopOffsetPx = partialTopOffsetPx,
                                    closedTopOffsetPx = screenHeightPx,
                                )
                                coroutineScope.launch {
                                    sheetTopOffsetPx.snapTo(dragTopOffsetPx)
                                    isDragging = false
                                    userLockedFullscreen = settle.target == AppBrowserSheetAnchor.Fullscreen
                                    if (settle.target == AppBrowserSheetAnchor.Closed) {
                                        requestDismiss(settle.durationMillis)
                                    } else {
                                        animateToAnchor(
                                            target = settle.target,
                                            durationMillis = settle.durationMillis,
                                        )
                                    }
                                }
                            },
                        )
                    },
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .size(
                            width = AppBrowserSheetHandleWidth,
                            height = AppBrowserSheetHandleHeight,
                        )
                        .clip(RoundedCornerShape(percent = 50))
                        .background(
                            colors.glassStrokeStrong.copy(
                                alpha = 0.42f - (presentationState.fullscreenProgress * 0.18f),
                            ),
                        ),
                )
            }
        }
    }
}
