package io.github.baba983.nativedo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.zIndex
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import kotlin.math.roundToInt

private const val BrowserTabsDismissViewportInsetPx = 32
private val BrowserTabsDockTransitionCornerRadius = 10.dp
private val BrowserTabsManagerTransitionCornerRadius = 8.dp

internal enum class BrowserTabsDismissDirection {
    Left,
    Right,
}

internal enum class BrowserTabsTransitionKind {
    MinimizeToDock,
    RestoreFromDock,
    OpenManager,
    CloseManager,
    RestoreFromManager,
    DismissFromManager,
}

internal enum class BrowserTabsTransitionSurface {
    Active,
    Dock,
    Manager,
    Dismissed,
}

internal enum class BrowserTabsTransitionRenderSource {
    Preview,
    LiveSurface,
}

internal data class BrowserTabsPreviewFrame(
    val sessionId: Long,
    val preview: BrowserHostMinimizedPreview,
    val boundsInRoot: IntRect,
)

internal data class BrowserTabsTransitionRequest(
    val transitionId: Long,
    val kind: BrowserTabsTransitionKind,
    val sessionId: Long,
    val preview: BrowserHostMinimizedPreview,
    val dismissDirection: BrowserTabsDismissDirection? = null,
    val sourceBoundsOverride: IntRect? = null,
)

internal data class BrowserTabsTransitionState(
    val transitionId: Long,
    val kind: BrowserTabsTransitionKind,
    val sessionId: Long,
    val preview: BrowserHostMinimizedPreview,
    val sourceSurface: BrowserTabsTransitionSurface,
    val targetSurface: BrowserTabsTransitionSurface,
    val sourceBounds: IntRect,
    val targetBounds: IntRect,
    val dismissDirection: BrowserTabsDismissDirection? = null,
)

internal data class BrowserTabsTransitionFrameState(
    val boundsInRoot: IntRect,
    val cornerRadius: Dp,
    val alpha: Float,
)

internal data class BrowserTabsLiveSurfaceTransitionState(
    val sessionId: Long,
    val boundsInRoot: IntRect,
    val cornerRadius: Dp,
    val alpha: Float,
)

internal fun buildBrowserTabsTransitionState(
    request: BrowserTabsTransitionRequest?,
    activeFrames: Map<Long, BrowserTabsPreviewFrame>,
    dockFrames: Map<Long, BrowserTabsPreviewFrame>,
    managerFrames: Map<Long, BrowserTabsPreviewFrame>,
    viewportWidthPx: Int,
): BrowserTabsTransitionState? {
    request ?: return null

    val sourceSurface = when (request.kind) {
        BrowserTabsTransitionKind.MinimizeToDock -> BrowserTabsTransitionSurface.Active
        BrowserTabsTransitionKind.RestoreFromDock,
        BrowserTabsTransitionKind.OpenManager,
        -> BrowserTabsTransitionSurface.Dock

        BrowserTabsTransitionKind.CloseManager,
        BrowserTabsTransitionKind.RestoreFromManager,
        BrowserTabsTransitionKind.DismissFromManager,
        -> BrowserTabsTransitionSurface.Manager
    }
    val targetSurface = when (request.kind) {
        BrowserTabsTransitionKind.MinimizeToDock,
        BrowserTabsTransitionKind.CloseManager,
        -> BrowserTabsTransitionSurface.Dock

        BrowserTabsTransitionKind.RestoreFromDock,
        BrowserTabsTransitionKind.RestoreFromManager,
        -> BrowserTabsTransitionSurface.Active

        BrowserTabsTransitionKind.OpenManager -> BrowserTabsTransitionSurface.Manager
        BrowserTabsTransitionKind.DismissFromManager -> BrowserTabsTransitionSurface.Dismissed
    }
    val sourceBounds = request.sourceBoundsOverride ?: when (sourceSurface) {
        BrowserTabsTransitionSurface.Active -> activeFrames[request.sessionId]?.boundsInRoot
        BrowserTabsTransitionSurface.Dock -> dockFrames[request.sessionId]?.boundsInRoot
        BrowserTabsTransitionSurface.Manager -> managerFrames[request.sessionId]?.boundsInRoot
        BrowserTabsTransitionSurface.Dismissed -> null
    } ?: return null
    val targetBounds = when (targetSurface) {
        BrowserTabsTransitionSurface.Active -> activeFrames[request.sessionId]?.boundsInRoot
        BrowserTabsTransitionSurface.Dock -> dockFrames[request.sessionId]?.boundsInRoot
        BrowserTabsTransitionSurface.Manager -> managerFrames[request.sessionId]?.boundsInRoot
        BrowserTabsTransitionSurface.Dismissed -> browserTabsDismissTargetBounds(
            sourceBounds = sourceBounds,
            direction = request.dismissDirection ?: BrowserTabsDismissDirection.Right,
            viewportWidthPx = viewportWidthPx,
        )
    } ?: return null

    return BrowserTabsTransitionState(
        transitionId = request.transitionId,
        kind = request.kind,
        sessionId = request.sessionId,
        preview = request.preview,
        sourceSurface = sourceSurface,
        targetSurface = targetSurface,
        sourceBounds = sourceBounds,
        targetBounds = targetBounds,
        dismissDirection = request.dismissDirection,
    )
}

internal fun LayoutCoordinates.toBrowserTabsPreviewFrame(
    sessionId: Long,
    preview: BrowserHostMinimizedPreview,
): BrowserTabsPreviewFrame = BrowserTabsPreviewFrame(
    sessionId = sessionId,
    preview = preview,
    boundsInRoot = boundsInRoot().roundToIntRect(),
)

internal fun IntRect.translate(
    dx: Int = 0,
    dy: Int = 0,
): IntRect = IntRect(
    left = left + dx,
    top = top + dy,
    right = right + dx,
    bottom = bottom + dy,
)

internal fun resolveBrowserTabsTransitionRenderSource(
    transition: BrowserTabsTransitionState?,
    hasLiveSurface: Boolean,
): BrowserTabsTransitionRenderSource? {
    transition ?: return null
    return if (
        hasLiveSurface &&
        (
            transition.sourceSurface == BrowserTabsTransitionSurface.Active ||
                transition.targetSurface == BrowserTabsTransitionSurface.Active
            )
    ) {
        BrowserTabsTransitionRenderSource.LiveSurface
    } else {
        BrowserTabsTransitionRenderSource.Preview
    }
}

internal fun buildBrowserTabsTransitionFrameState(
    transition: BrowserTabsTransitionState,
    progress: Float,
): BrowserTabsTransitionFrameState {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val hasActiveSurface = transition.sourceSurface == BrowserTabsTransitionSurface.Active ||
        transition.targetSurface == BrowserTabsTransitionSurface.Active
    return BrowserTabsTransitionFrameState(
        boundsInRoot = lerpIntRect(
            start = transition.sourceBounds,
            stop = transition.targetBounds,
            fraction = clampedProgress,
        ),
        cornerRadius = lerp(
            start = browserTabsSurfaceCornerRadius(transition.sourceSurface),
            stop = browserTabsSurfaceCornerRadius(transition.targetSurface),
            fraction = clampedProgress,
        ),
        alpha = when {
            hasActiveSurface -> 1f
            transition.kind == BrowserTabsTransitionKind.DismissFromManager -> 1f - (0.22f * clampedProgress)
            transition.kind == BrowserTabsTransitionKind.OpenManager -> 0.92f + (0.08f * clampedProgress)
            else -> 0.96f + (0.04f * clampedProgress)
        },
    )
}

internal fun buildBrowserTabsLiveSurfaceTransitionState(
    transition: BrowserTabsTransitionState?,
    hasLiveSurface: Boolean,
    progress: Float,
): BrowserTabsLiveSurfaceTransitionState? {
    transition ?: return null
    if (
        resolveBrowserTabsTransitionRenderSource(
            transition = transition,
            hasLiveSurface = hasLiveSurface,
        ) != BrowserTabsTransitionRenderSource.LiveSurface
    ) {
        return null
    }
    val frameState = buildBrowserTabsTransitionFrameState(
        transition = transition,
        progress = progress,
    )
    return BrowserTabsLiveSurfaceTransitionState(
        sessionId = transition.sessionId,
        boundsInRoot = frameState.boundsInRoot,
        cornerRadius = frameState.cornerRadius,
        alpha = frameState.alpha,
    )
}

@Composable
internal fun BoxScope.BrowserTabsTransitionOverlay(
    transition: BrowserTabsTransitionState?,
    progress: Float,
    renderSource: BrowserTabsTransitionRenderSource?,
) {
    transition ?: return
    if (renderSource != BrowserTabsTransitionRenderSource.Preview) {
        return
    }

    val density = LocalDensity.current
    val view = LocalView.current
    val activeSurfaceTopInset = remember(
        transition.sourceSurface,
        transition.targetSurface,
        view,
        density,
    ) {
        if (
            transition.sourceSurface == BrowserTabsTransitionSurface.Active ||
            transition.targetSurface == BrowserTabsTransitionSurface.Active
        ) {
            with(density) {
                (view.rootWindowInsets?.systemWindowInsetTop ?: 0).toDp()
            }
        } else {
            0.dp
        }
    }
    val frameState = remember(
        transition,
        progress,
    ) {
        buildBrowserTabsTransitionFrameState(
            transition = transition,
            progress = progress,
        )
    }
    val isLargeSurface = frameState.boundsInRoot.height >= with(density) { 180.dp.roundToPx() }
    val cardWidth = with(density) { frameState.boundsInRoot.width.toDp() }
    val cardHeight = with(density) { frameState.boundsInRoot.height.toDp() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(12f),
    ) {
        Surface(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = frameState.boundsInRoot.left,
                        y = frameState.boundsInRoot.top,
                    )
                }
                .graphicsLayer {
                    alpha = frameState.alpha
                    shadowElevation = if (isLargeSurface) 22.dp.toPx() else 10.dp.toPx()
                }
                .size(
                    width = cardWidth,
                    height = cardHeight,
                ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(frameState.cornerRadius),
            color = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            if (isLargeSurface) {
                BrowserTabsLargePreviewSurface(
                    preview = transition.preview,
                    modifier = Modifier.fillMaxSize(),
                    topInset = activeSurfaceTopInset,
                )
            } else {
                BrowserTabsTransitionDockSurface(
                    preview = transition.preview,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun BrowserTabsTransitionDockSurface(
    preview: BrowserHostMinimizedPreview,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors

    Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(BrowserTabsDockTransitionCornerRadius),
        color = colors.backgroundElevated.copy(alpha = 0.985f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 2.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.app_browser_close),
                    tint = colors.textPrimary.copy(alpha = 0.86f),
                )
            }
            Text(
                text = preview.title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 8.dp),
            )
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowUp,
                contentDescription = null,
                tint = colors.textPrimary.copy(alpha = 0.64f),
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

private fun browserTabsDismissTargetBounds(
    sourceBounds: IntRect,
    direction: BrowserTabsDismissDirection,
    viewportWidthPx: Int,
): IntRect = when (direction) {
    BrowserTabsDismissDirection.Right -> {
        val left = viewportWidthPx + BrowserTabsDismissViewportInsetPx
        IntRect(
            left = left,
            top = sourceBounds.top,
            right = left + sourceBounds.width,
            bottom = sourceBounds.bottom,
        )
    }

    BrowserTabsDismissDirection.Left -> {
        val right = -BrowserTabsDismissViewportInsetPx
        IntRect(
            left = right - sourceBounds.width,
            top = sourceBounds.top,
            right = right,
            bottom = sourceBounds.bottom,
        )
    }
}

internal fun browserTabsTransitionDurationMillis(
    kind: BrowserTabsTransitionKind,
): Int = when (kind) {
    BrowserTabsTransitionKind.MinimizeToDock -> 220
    BrowserTabsTransitionKind.RestoreFromDock -> 320
    BrowserTabsTransitionKind.OpenManager -> 320
    BrowserTabsTransitionKind.CloseManager -> 320
    BrowserTabsTransitionKind.RestoreFromManager -> 320
    BrowserTabsTransitionKind.DismissFromManager -> 220
}

private fun browserTabsSurfaceCornerRadius(
    surface: BrowserTabsTransitionSurface,
): Dp = when (surface) {
    BrowserTabsTransitionSurface.Active -> 0.dp
    BrowserTabsTransitionSurface.Dock -> BrowserTabsDockTransitionCornerRadius
    BrowserTabsTransitionSurface.Manager,
    BrowserTabsTransitionSurface.Dismissed,
    -> BrowserTabsManagerTransitionCornerRadius
}

private fun Rect.roundToIntRect(): IntRect = IntRect(
    left = left.roundToInt(),
    top = top.roundToInt(),
    right = right.roundToInt(),
    bottom = bottom.roundToInt(),
)

private fun lerpIntRect(
    start: IntRect,
    stop: IntRect,
    fraction: Float,
): IntRect = IntRect(
    left = lerp(start.left, stop.left, fraction),
    top = lerp(start.top, stop.top, fraction),
    right = lerp(start.right, stop.right, fraction),
    bottom = lerp(start.bottom, stop.bottom, fraction),
)

private fun lerp(
    start: Int,
    stop: Int,
    fraction: Float,
): Int = (start + ((stop - start) * fraction)).roundToInt()
