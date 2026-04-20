package io.github.baba983.nativedo.feature.browser

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import io.github.baba983.nativedo.core.navigation.AppBrowserOpenRequest
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderContent
import io.github.baba983.nativedo.ui.BrowserHostMinimizedPreview
import io.github.baba983.nativedo.ui.theme.NativeDoEasing
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import kotlinx.coroutines.launch

private const val AppBrowserSheetOpenDurationMillis = 320
private const val AppBrowserSheetCloseDurationMillis = 250

internal fun buildAppBrowserSheetTopInset(
    openProgress: Float,
    statusBarInset: androidx.compose.ui.unit.Dp,
): androidx.compose.ui.unit.Dp = lerp(
    start = 24.dp,
    stop = statusBarInset.coerceAtLeast(0.dp),
    fraction = openProgress.coerceIn(0f, 1f),
)

internal data class BrowserSheetMotionState(
    val scale: Float,
    val alpha: Float,
    val scrimAlpha: Float,
)

internal fun buildAppBrowserSheetRestoreMotionState(
    restoredFromDock: Boolean,
): BrowserSheetMotionState? = if (restoredFromDock) {
    BrowserSheetMotionState(
        scale = 1f,
        alpha = 1f,
        scrimAlpha = 0f,
    )
} else {
    null
}

internal fun buildAppBrowserSheetRestoreSettleMotionState(
    progress: Float,
): BrowserSheetMotionState {
    val clampedProgress = progress.coerceIn(0f, 1f)
    return BrowserSheetMotionState(
        scale = 0.996f + ((1f - 0.996f) * clampedProgress),
        alpha = 0.985f + ((1f - 0.985f) * clampedProgress),
        scrimAlpha = 0f,
    )
}

internal fun buildBrowserSheetMotionState(
    openProgress: Float,
    scrimAlpha: Float? = null,
): BrowserSheetMotionState {
    val clampedProgress = openProgress.coerceIn(0f, 1f)
    return BrowserSheetMotionState(
        scale = 0.992f + ((1f - 0.992f) * clampedProgress),
        alpha = 0.97f + ((1f - 0.97f) * clampedProgress),
        scrimAlpha = scrimAlpha ?: (0f + (0.10f * clampedProgress)),
    )
}

@Composable
internal fun BrowserSheetContainer(
    entryId: Long,
    url: String,
    foregroundActivationKey: Long,
    restoredFromDock: Boolean = false,
    onOpenLink: (String) -> Unit,
    onOpenReader: (AppBrowserReaderContent, AppBrowserOpenRequest?) -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    onPreviewChanged: (BrowserHostMinimizedPreview) -> Unit = {},
    isForeground: Boolean = true,
) {
    val colors = NativeDoThemeExtras.colors
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val statusBarInsetDp = with(density) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    val screenHeightPx = remember(configuration.screenHeightDp, density, entryId) {
        with(density) { configuration.screenHeightDp.dp.toPx() }.coerceAtLeast(1f)
    }
    val sheetTopOffsetPx = remember(entryId, screenHeightPx) {
        Animatable(screenHeightPx)
    }
    var isClosing by remember(entryId) { mutableStateOf(false) }
    var lastAnimatedForegroundActivationKey by remember(entryId) {
        mutableLongStateOf(Long.MIN_VALUE)
    }
    val restoreSettleProgress = remember(entryId) { Animatable(1f) }

    suspend fun animateToOffset(
        targetOffsetPx: Float,
        durationMillis: Int? = null,
    ) {
        sheetTopOffsetPx.animateTo(
            targetValue = targetOffsetPx,
            animationSpec = tween(
                durationMillis = durationMillis ?: AppBrowserSheetOpenDurationMillis,
                easing = if (targetOffsetPx >= screenHeightPx) {
                    NativeDoEasing.BrowserDismiss
                } else {
                    NativeDoEasing.EaseOutQuint
                },
            ),
        )
    }

    fun requestDismiss(durationMillis: Int? = null) {
        if (isClosing) {
            return
        }
        isClosing = true
        coroutineScope.launch {
            animateToOffset(
                targetOffsetPx = screenHeightPx,
                durationMillis = durationMillis ?: AppBrowserSheetCloseDurationMillis,
            )
            onClose()
        }
    }

    LaunchedEffect(
        screenHeightPx,
        foregroundActivationKey,
        restoredFromDock,
        isForeground,
    ) {
        if (!isForeground || lastAnimatedForegroundActivationKey == foregroundActivationKey) {
            return@LaunchedEffect
        }
        lastAnimatedForegroundActivationKey = foregroundActivationKey
        isClosing = false
        if (restoredFromDock) {
            sheetTopOffsetPx.snapTo(0f)
            restoreSettleProgress.snapTo(0f)
            restoreSettleProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 180,
                    easing = NativeDoEasing.EaseOutQuint,
                ),
            )
        } else {
            restoreSettleProgress.snapTo(1f)
            sheetTopOffsetPx.snapTo(screenHeightPx)
            animateToOffset(
                targetOffsetPx = 0f,
                durationMillis = AppBrowserSheetOpenDurationMillis,
            )
        }
    }

    val currentTopOffsetPx = sheetTopOffsetPx.value
    val presentationState = remember(currentTopOffsetPx, screenHeightPx) {
        buildAppBrowserSheetPresentationState(
            topOffsetPx = currentTopOffsetPx,
            partialTopOffsetPx = 0f,
            closedTopOffsetPx = screenHeightPx,
        )
    }
    val motionState = remember(presentationState, restoredFromDock, restoreSettleProgress.value) {
        when {
            restoredFromDock -> buildAppBrowserSheetRestoreSettleMotionState(
                progress = restoreSettleProgress.value,
            )

            else -> buildBrowserSheetMotionState(
                openProgress = presentationState.openedProgress,
                scrimAlpha = presentationState.scrimAlpha,
            )
        }
    }
    val topInset = buildAppBrowserSheetTopInset(
        openProgress = presentationState.attachedProgress,
        statusBarInset = statusBarInsetDp,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = motionState.scrimAlpha)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxSize()
                .graphicsLayer {
                    translationY = currentTopOffsetPx
                    scaleX = motionState.scale
                    scaleY = motionState.scale
                    alpha = motionState.alpha
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
                .background(colors.backgroundBase),
        ) {
            BrowserRoute(
                entryId = entryId,
                url = url,
                onOpenLink = onOpenLink,
                onOpenReader = onOpenReader,
                onMinimize = onMinimize,
                onClose = { requestDismiss() },
                onPreviewChanged = onPreviewChanged,
                presentationProgress = presentationState.openedProgress,
                topInset = topInset,
                isForeground = isForeground,
                onSheetFullscreenPreferenceChanged = {},
            )
        }
    }
}
