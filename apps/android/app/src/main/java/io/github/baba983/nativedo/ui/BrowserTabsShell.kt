package io.github.baba983.nativedo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.ui.theme.NativeDoEasing
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val BrowserTabsDockPreviewLayers = 3
private val BrowserTabsDockCardHeight = 50.dp
private val BrowserTabsDockCardCornerRadius = 10.dp
private val BrowserTabsDockSingleOccupiedHeight = 60.dp
private val BrowserTabsDockMultipleOccupiedHeight = 68.dp
private val BrowserTabsDockEdgeInset = 4.dp
private val BrowserTabsDockStackOffset = 8.dp
private val BrowserTabsManagerCardMaxWidth = 340.dp
private val BrowserTabsManagerCardMinHeight = 360.dp
private val BrowserTabsManagerCardMaxHeight = 560.dp
private val BrowserTabsManagerTopPadding = 22.dp
private val BrowserTabsManagerBottomPadding = 164.dp
private val BrowserTabsManagerOverlap = (-220).dp
private val BrowserTabsManagerCardCornerRadius = 8.dp
private val BrowserTabsManagerTabBarHeight = 50.dp
private const val BrowserTabsManagerDismissThreshold = 0.28f
private const val BrowserTabsManagerPullDismissVelocity = 1800f
private const val BrowserTabsManagerOpenDurationMillis = 320
private const val BrowserTabsManagerCloseDurationMillis = 220

internal val BrowserTabsCollapsedDockOccupiedHeight = BrowserTabsDockMultipleOccupiedHeight

internal enum class BrowserTabsShellDockAction {
    None,
    RestoreLatest,
    OpenManager,
}

internal data class BrowserTabsShellState(
    val showsCollapsedDock: Boolean,
    val occupiedHeight: Dp,
    val dockAction: BrowserTabsShellDockAction,
)

internal fun buildBrowserTabsShellState(
    hostState: BrowserHostState,
    isManagerVisible: Boolean,
): BrowserTabsShellState {
    val showsCollapsedDock = !isManagerVisible &&
        hostState.activeSession == null &&
        hostState.dockedSessions.isNotEmpty()
    val dockAction = when {
        !showsCollapsedDock -> BrowserTabsShellDockAction.None
        hostState.dockedSessions.size == 1 -> BrowserTabsShellDockAction.RestoreLatest
        else -> BrowserTabsShellDockAction.OpenManager
    }
    return BrowserTabsShellState(
        showsCollapsedDock = showsCollapsedDock,
        occupiedHeight = when {
            !showsCollapsedDock -> 0.dp
            hostState.dockedSessions.size > 1 -> BrowserTabsDockMultipleOccupiedHeight
            else -> BrowserTabsDockSingleOccupiedHeight
        },
        dockAction = dockAction,
    )
}

@Composable
internal fun BoxScope.BrowserTabsCollapsedDock(
    sessions: List<BrowserHostSession>,
    dockAction: BrowserTabsShellDockAction,
    onRestoreLatest: () -> Unit,
    onOpenManager: () -> Unit,
    onCloseLatest: (Long) -> Unit,
    onPreviewFrameChange: (BrowserTabsPreviewFrame) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val visibleSessions = remember(sessions) {
        sessions.take(BrowserTabsDockPreviewLayers)
    }
    val latestSession = visibleSessions.firstOrNull() ?: return
    val latestPreview = latestSession.preview ?: return
    val occupiedHeight = if (sessions.size > 1) {
        BrowserTabsDockMultipleOccupiedHeight
    } else {
        BrowserTabsDockSingleOccupiedHeight
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = 220,
                easing = NativeDoEasing.EaseOutQuint,
            ),
        ),
        exit = fadeOut(
            animationSpec = tween(
                durationMillis = 180,
                easing = NativeDoEasing.Standard,
            ),
        ),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(occupiedHeight)
                .background(colors.backgroundBase.copy(alpha = 0.985f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.Black.copy(alpha = 0.08f))
                    .align(Alignment.TopCenter),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = BrowserTabsDockEdgeInset)
                    .align(Alignment.BottomCenter),
                contentAlignment = Alignment.BottomCenter,
            ) {
                visibleSessions
                    .asReversed()
                    .forEachIndexed { index, session ->
                        val stackIndex = visibleSessions.lastIndex - index
                        val preview = session.preview ?: return@forEachIndexed
                        if (stackIndex > 0) {
                            BrowserTabsDockBackCard(
                                preview = preview,
                                modifier = Modifier
                                    .offset(y = -(BrowserTabsDockStackOffset * stackIndex))
                                    .graphicsLayer {
                                        scaleX = 1f - (0.05f * stackIndex)
                                        scaleY = 1f - (0.05f * stackIndex)
                                        alpha = 0.92f - (0.08f * stackIndex)
                                    },
                            )
                        }
                    }

                BrowserTabsDockCard(
                    sessionId = latestSession.stableId,
                    preview = latestPreview,
                    title = buildBrowserTabsCollapsedTitle(
                        preview = latestPreview,
                        extraCount = sessions.size - 1,
                    ),
                    onClick = {
                        when (dockAction) {
                            BrowserTabsShellDockAction.RestoreLatest -> onRestoreLatest()
                            BrowserTabsShellDockAction.OpenManager -> onOpenManager()
                            BrowserTabsShellDockAction.None -> Unit
                        }
                    },
                    onClose = {
                        onCloseLatest(latestSession.stableId)
                    },
                    onPreviewFrameChange = onPreviewFrameChange,
                )
            }
        }
    }
}

@Composable
internal fun BoxScope.BrowserTabsManager(
    sessions: List<BrowserHostSession>,
    targetOpen: Boolean,
    onDismiss: () -> Unit,
    onRestoreSession: (Long) -> Unit,
    onCloseSession: (Long) -> Unit,
    onSwipeDismissSession: (Long, BrowserTabsDismissDirection, IntRect?) -> Unit,
    onPreviewFrameChange: (BrowserTabsPreviewFrame) -> Unit = {},
    onCloseAll: () -> Unit,
    onOpenAnimationFinished: () -> Unit = {},
    onCloseAnimationFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val visibleSessions = remember(sessions) {
        sessions.filter { it.preview != null }
    }
    val cardHeight = remember(configuration.screenHeightDp) {
        (configuration.screenHeightDp.dp * 0.74f)
            .coerceIn(BrowserTabsManagerCardMinHeight, BrowserTabsManagerCardMaxHeight)
    }
    val screenHeightPx = remember(configuration.screenHeightDp, density) {
        with(density) { configuration.screenHeightDp.dp.toPx() }.coerceAtLeast(1f)
    }
    val listState = rememberLazyListState()
    val openProgress = remember {
        Animatable(if (targetOpen) 0f else 1f)
    }
    val managerDragOffsetY = remember {
        Animatable(0f)
    }
    val dismissThresholdPx = screenHeightPx * 0.52f
    val maxDragOffsetPx = screenHeightPx
    val closingOffsetPx = screenHeightPx * 0.66f

    fun isAtTop(): Boolean = listState.firstVisibleItemIndex == 0 &&
        listState.firstVisibleItemScrollOffset == 0

    suspend fun settleManagerDrag(velocityY: Float = 0f) {
        if (managerDragOffsetY.value >= dismissThresholdPx || velocityY >= BrowserTabsManagerPullDismissVelocity) {
            onDismiss()
            return
        }
        if (managerDragOffsetY.value > 0f) {
            managerDragOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 220,
                    easing = NativeDoEasing.EaseOutQuint,
                ),
            )
        }
    }

    LaunchedEffect(targetOpen) {
        if (targetOpen) {
            managerDragOffsetY.snapTo(0f)
        } else {
            launch {
                managerDragOffsetY.animateTo(
                    targetValue = maxOf(managerDragOffsetY.value, closingOffsetPx),
                    animationSpec = tween(
                        durationMillis = BrowserTabsManagerCloseDurationMillis,
                        easing = NativeDoEasing.EaseOutQuint,
                    ),
                )
            }
        }
        val targetValue = if (targetOpen) 1f else 0f
        if (openProgress.value != targetValue) {
            openProgress.animateTo(
                targetValue = targetValue,
                animationSpec = tween(
                    durationMillis = if (targetOpen) {
                        BrowserTabsManagerOpenDurationMillis
                    } else {
                        BrowserTabsManagerCloseDurationMillis
                    },
                    easing = NativeDoEasing.EaseOutQuint,
                ),
            )
        }
        if (targetOpen) {
            onOpenAnimationFinished()
        } else {
            onCloseAnimationFinished()
        }
    }

    val managerDragProgress = remember(managerDragOffsetY.value, screenHeightPx) {
        (managerDragOffsetY.value / screenHeightPx).coerceIn(0f, 1f)
    }
    val managerTranslationYPx = with(density) {
        lerp(72.dp.toPx(), 0f, openProgress.value)
    } + managerDragOffsetY.value
    val scrimAlphaMultiplier = 1f - (0.42f * managerDragProgress)

    Box(
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = openProgress.value
                }
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.22f * scrimAlphaMultiplier),
                            Color.Black.copy(alpha = 0.40f * scrimAlphaMultiplier),
                        ),
                    ),
                )
                .clickable(
                    enabled = targetOpen,
                    onClick = onDismiss,
                ),
        )

        val managerNestedScrollConnection = remember(
            targetOpen,
            dismissThresholdPx,
            listState,
        ) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (!targetOpen) {
                        return Offset.Zero
                    }
                    val currentOffset = managerDragOffsetY.value
                    return when {
                        available.y > 0f && isAtTop() -> {
                            val nextOffset = (currentOffset + available.y)
                                .coerceAtMost(maxDragOffsetPx)
                            val consumed = nextOffset - currentOffset
                            if (consumed != 0f) {
                                coroutineScope.launch {
                                    managerDragOffsetY.snapTo(nextOffset)
                                }
                            }
                            Offset(x = 0f, y = consumed)
                        }

                        available.y < 0f && currentOffset > 0f -> {
                            val consumed = available.y.coerceAtLeast(-currentOffset)
                            coroutineScope.launch {
                                managerDragOffsetY.snapTo(currentOffset + consumed)
                            }
                            Offset(x = 0f, y = consumed)
                        }

                        else -> Offset.Zero
                    }
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (!targetOpen) {
                        return Velocity.Zero
                    }
                    if (managerDragOffsetY.value > 0f || (available.y > 0f && isAtTop())) {
                        settleManagerDrag(velocityY = available.y)
                        return available
                    }
                    return Velocity.Zero
                }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity,
                ): Velocity {
                    if (managerDragOffsetY.value > 0f) {
                        settleManagerDrag(velocityY = available.y)
                        return available
                    }
                    return Velocity.Zero
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .nestedScroll(managerNestedScrollConnection),
        ) {
            LazyColumn(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = BrowserTabsManagerTopPadding)
                    .graphicsLayer {
                        alpha = 0.6f + (0.4f * openProgress.value)
                        translationY = managerTranslationYPx
                    }
                    .widthIn(max = BrowserTabsManagerCardMaxWidth)
                    .fillMaxWidth(0.95f),
                state = listState,
                contentPadding = PaddingValues(
                    bottom = BrowserTabsManagerBottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(BrowserTabsManagerOverlap),
            ) {
                itemsIndexed(
                    items = visibleSessions,
                    key = { _, session -> session.stableId },
                ) { index, session ->
                    val preview = session.preview ?: return@itemsIndexed
                    BrowserTabsManagerCard(
                        session = session,
                        preview = preview,
                        stackIndex = index,
                        cardHeight = cardHeight,
                        openProgress = openProgress.value,
                        onRestore = { onRestoreSession(session.stableId) },
                        onClose = { onCloseSession(session.stableId) },
                        onSwipeDismiss = { direction, sourceBoundsOverride ->
                            onSwipeDismissSession(
                                session.stableId,
                                direction,
                                sourceBoundsOverride,
                            )
                        },
                        onPreviewFrameChange = onPreviewFrameChange,
                        gesturesEnabled = targetOpen,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .graphicsLayer {
                        translationY = managerTranslationYPx
                        alpha = (0.82f + (0.18f * openProgress.value)) * (1f - (0.16f * managerDragProgress))
                    },
            ) {
                BrowserTabsCloseAllPill(
                    onClick = onCloseAll,
                    openProgress = openProgress.value,
                    enabled = targetOpen,
                )
            }
        }
    }
}

@Composable
private fun BrowserTabsCloseAllPill(
    onClick: () -> Unit,
    openProgress: Float,
    enabled: Boolean,
) {
    val colors = NativeDoThemeExtras.colors
    val translationYPx = with(LocalDensity.current) {
        lerp(20.dp.toPx(), 0f, openProgress)
    }

    Surface(
        modifier = Modifier
            .graphicsLayer {
                alpha = openProgress
                translationY = translationYPx
            }
            .clickable(
                enabled = enabled,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(999.dp),
        color = colors.backgroundElevated.copy(alpha = 0.78f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            text = "Close All Tabs",
            style = MaterialTheme.typography.labelLarge,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun BrowserTabsManagerCard(
    session: BrowserHostSession,
    preview: BrowserHostMinimizedPreview,
    stackIndex: Int,
    cardHeight: Dp,
    openProgress: Float,
    onRestore: () -> Unit,
    onClose: () -> Unit,
    onSwipeDismiss: (BrowserTabsDismissDirection, IntRect?) -> Unit,
    onPreviewFrameChange: (BrowserTabsPreviewFrame) -> Unit,
    gesturesEnabled: Boolean,
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val swipeOffsetX = remember(session.stableId) { Animatable(0f) }
    var cardWidthPx by remember(session.stableId) { mutableFloatStateOf(1f) }
    var baseBoundsInRoot by remember(session.stableId) { mutableStateOf<IntRect?>(null) }
    val stackDepth = stackIndex.coerceAtMost(4)
    val finalScaleX = 1f - (stackDepth * 0.046f)
    val finalScaleY = 1f - (stackDepth * 0.12f)
    val closedScaleX = 1f - (stackDepth * 0.03f)
    val closedScaleY = 0.18f + (stackDepth * 0.02f)
    val swipeAlpha = remember(swipeOffsetX.value, cardWidthPx) {
        (1f - ((abs(swipeOffsetX.value) / cardWidthPx) * 0.42f)).coerceIn(0.58f, 1f)
    }
    val swipeRotation = remember(swipeOffsetX.value, cardWidthPx) {
        ((swipeOffsetX.value / cardWidthPx).coerceIn(-1f, 1f)) * 7f
    }
    val stackedRotationZ = stackDepth * -1.15f
    val finalTranslationY = stackDepth * -18f
    val closedTranslationY = with(density) { (280.dp - (stackDepth * 12).dp).toPx() }
    val visualScaleX = lerp(closedScaleX, finalScaleX, openProgress)
    val visualScaleY = lerp(closedScaleY, finalScaleY, openProgress)
    val visualTranslationY = lerp(closedTranslationY, finalTranslationY, openProgress)
    val visualAlpha = lerp(0.64f, swipeAlpha, openProgress)
    val visualRotationX = lerp(0f, 10f + (stackDepth * 3f), openProgress)
    val visualRotationZ = lerp(0f, stackedRotationZ, openProgress) + (swipeRotation * openProgress)

    suspend fun settleSwipe() {
        val dismissThreshold = cardWidthPx * BrowserTabsManagerDismissThreshold
        if (abs(swipeOffsetX.value) >= dismissThreshold) {
            onSwipeDismiss(
                if (swipeOffsetX.value < 0f) {
                    BrowserTabsDismissDirection.Left
                } else {
                    BrowserTabsDismissDirection.Right
                },
                baseBoundsInRoot?.translate(
                    dx = swipeOffsetX.value.roundToInt(),
                    dy = visualTranslationY.roundToInt(),
                ),
            )
        } else {
            swipeOffsetX.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 220,
                    easing = NativeDoEasing.EaseOutQuint,
                ),
            )
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight)
            .onSizeChanged { size ->
                cardWidthPx = size.width.toFloat().coerceAtLeast(1f)
            }
            .onGloballyPositioned { coordinates ->
                val frame = coordinates.toBrowserTabsPreviewFrame(
                    sessionId = session.stableId,
                    preview = preview,
                )
                baseBoundsInRoot = frame.boundsInRoot
                onPreviewFrameChange(frame)
            }
            .graphicsLayer {
                val densityScale = density.density
                scaleX = visualScaleX
                scaleY = visualScaleY
                translationX = swipeOffsetX.value
                translationY = visualTranslationY
                rotationZ = visualRotationZ
                rotationX = visualRotationX
                cameraDistance = 24f * densityScale
                alpha = visualAlpha
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
            }
            .pointerInput(session.stableId, gesturesEnabled) {
                if (!gesturesEnabled) {
                    return@pointerInput
                }
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val nextOffset = swipeOffsetX.value + dragAmount
                        coroutineScope.launch {
                            swipeOffsetX.snapTo(nextOffset)
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch { settleSwipe() }
                    },
                    onDragEnd = {
                        coroutineScope.launch { settleSwipe() }
                    },
                )
            },
        shape = RoundedCornerShape(BrowserTabsManagerCardCornerRadius),
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 20.dp,
    ) {
        BrowserTabsLargePreviewSurface(
            preview = preview,
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    enabled = gesturesEnabled,
                    onClick = onRestore,
                ),
            onClose = onClose,
        )
    }
}

@Composable
private fun BrowserTabsDockBackCard(
    preview: BrowserHostMinimizedPreview,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(BrowserTabsDockCardHeight),
        shape = RoundedCornerShape(BrowserTabsDockCardCornerRadius),
        color = colors.backgroundElevated.copy(alpha = 0.94f),
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BrowserTabsLeadingIcon(
                preview = preview,
                size = 22.dp,
                modifier = Modifier.graphicsLayer { alpha = 0.65f },
            )
            Text(
                text = preview.title,
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BrowserTabsDockCard(
    sessionId: Long,
    preview: BrowserHostMinimizedPreview,
    title: String,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onPreviewFrameChange: (BrowserTabsPreviewFrame) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(BrowserTabsDockCardHeight)
            .onGloballyPositioned { coordinates ->
                onPreviewFrameChange(
                    coordinates.toBrowserTabsPreviewFrame(
                        sessionId = sessionId,
                        preview = preview,
                    ),
                )
            },
        shape = RoundedCornerShape(BrowserTabsDockCardCornerRadius),
        color = colors.backgroundElevated.copy(alpha = 0.985f),
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick)
                .padding(start = 2.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.app_browser_close),
                    tint = colors.textPrimary.copy(alpha = 0.86f),
                )
            }
            Text(
                text = title,
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

@Composable
internal fun BrowserTabsLargePreviewSurface(
    preview: BrowserHostMinimizedPreview,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    onClose: () -> Unit = {},
) {
    val colors = NativeDoThemeExtras.colors

    Box(
        modifier = modifier
            .background(
                color = colors.backgroundElevated.copy(alpha = 0.94f),
                shape = RoundedCornerShape(BrowserTabsManagerCardCornerRadius),
            ),
    ) {
        preview.snapshot?.let { bitmap ->
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.08f
                            scaleY = 1.08f
                            alpha = 0.44f
                        }
                        .blur(24.dp),
                )
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = 0.96f
                        },
                )
            }
        } ?: Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.glassBackgroundStrong.copy(alpha = 0.90f),
                            colors.backgroundBase.copy(alpha = 0.98f),
                        ),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(
                                fraction = when (index) {
                                    0 -> 0.86f
                                    1 -> 0.72f
                                    2 -> 0.58f
                                    else -> 0.42f
                                },
                            )
                            .height(if (index == 0) 14.dp else 10.dp)
                            .background(
                                color = colors.textSecondary.copy(
                                    alpha = if (index == 0) 0.16f else 0.10f,
                                ),
                                shape = RoundedCornerShape(999.dp),
                            ),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.24f),
                        ),
                    ),
                ),
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(BrowserTabsManagerTabBarHeight + topInset)
                .align(Alignment.TopCenter),
            shape = RoundedCornerShape(
                topStart = BrowserTabsManagerCardCornerRadius,
                topEnd = BrowserTabsManagerCardCornerRadius,
            ),
            color = colors.glassBackgroundStrong.copy(alpha = 0.68f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.height(topInset))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(BrowserTabsManagerTabBarHeight)
                        .padding(start = 2.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
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
                        tint = colors.textPrimary.copy(alpha = 0.56f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun BrowserTabsLeadingIcon(
    preview: BrowserHostMinimizedPreview,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val icon = when (preview.kind) {
        BrowserHostMinimizedKind.Browser -> Icons.Outlined.OpenInBrowser
        BrowserHostMinimizedKind.Reader -> Icons.Outlined.Article
    }

    preview.favicon?.let { favicon ->
        Image(
            bitmap = favicon.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.size(size),
            contentScale = ContentScale.Crop,
        )
        return
    }

    Box(
        modifier = modifier
            .size(size)
            .background(
                color = colors.glassBackgroundStrong.copy(alpha = 0.76f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textPrimary,
        )
    }
}

private fun buildBrowserTabsCollapsedTitle(
    preview: BrowserHostMinimizedPreview,
    extraCount: Int,
): String {
    if (extraCount <= 0) {
        return preview.title
    }
    return "${preview.title} + $extraCount more"
}

private fun lerp(
    start: Float,
    stop: Float,
    fraction: Float,
): Float = start + ((stop - start) * fraction)
