package io.github.baba983.nativedo.ui
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.baba983.nativedo.core.ui.NativeDoAnimatedLabel
import io.github.baba983.nativedo.core.ui.NativeDoBackIcon
import io.github.baba983.nativedo.core.ui.NativeDoGlassBar
import io.github.baba983.nativedo.feature.profile.ui.ProfileRoute
import io.github.baba983.nativedo.feature.profile.ui.ProfileViewer
import io.github.baba983.nativedo.feature.topic.ui.TopicScrollSnapshot
import io.github.baba983.nativedo.feature.topic.ui.TopicRoute
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import io.github.baba983.nativedo.ui.theme.TelegramAnimationSpec
import io.github.baba983.nativedo.ui.theme.TelegramEasing
import kotlinx.coroutines.launch
import kotlin.math.abs

private val PredictiveBackPivotInset = 82.dp

internal data class TelegramPredictiveBackLayerOffsets(
    val hostTranslationPx: Float,
    val innerCompensationTranslationPx: Float,
    val progress: Float,
)

internal sealed interface NativePageEntry {
    val id: String
    val routePattern: String

    data class Topic(
        val topicId: Long,
    ) : NativePageEntry {
        override val id: String = "topic:$topicId"
        override val routePattern: String = "topic/{topicId}"
    }

    data class User(
        val username: String,
    ) : NativePageEntry {
        override val id: String = "user:$username"
        override val routePattern: String = "user/{username}"
    }
}

@Stable
internal class NativePageStackState {
    private val stack = mutableStateListOf<NativePageEntry>()

    val currentEntry: NativePageEntry?
        get() = stack.lastOrNull()

    val previousEntry: NativePageEntry?
        get() = stack.getOrNull(stack.lastIndex - 1)

    val currentRoutePattern: String?
        get() = currentEntry?.routePattern

    val hasPushedEntry: Boolean
        get() = currentEntry != null

    val depth: Int
        get() = stack.size

    fun presentTopic(topicId: Long) {
        stack += NativePageEntry.Topic(topicId)
    }

    fun presentUser(username: String) {
        stack += NativePageEntry.User(username)
    }

    fun closeLast(): Boolean {
        if (stack.isEmpty()) return false
        stack.removeAt(stack.lastIndex)
        return true
    }
}

@Composable
internal fun rememberNativePageStackState(): NativePageStackState {
    return remember { NativePageStackState() }
}

@Composable
internal fun BoxScope.NativePageStackHost(
    state: NativePageStackState,
    onSessionRecoveryRequired: () -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    interactionsEnabled: Boolean = true,
    overlayZIndex: Float = 1f,
) {
    val currentEntry = state.currentEntry ?: return
    val previousEntry = state.previousEntry
    val saveableStateHolder = rememberSaveableStateHolder()
    val titlesByEntryId = remember { mutableStateMapOf<String, String?>() }
    val topicScrollSnapshotsByEntryId = remember { mutableStateMapOf<String, TopicScrollSnapshot>() }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val edgeTriggerPx = with(density) { 24.dp.toPx() }
    val slopPx = with(density) { 8.dp.toPx() }
    val slideOffsetPx = with(density) { TelegramAnimationSpec.STANDARD_SLIDE_OFFSET_DP.dp.toPx() }
    val predictiveBackMaxOffsetPx = with(density) { TelegramAnimationSpec.PREDICTIVE_BACK_MAX_OFFSET_DP.dp.toPx() }
    val predictiveBackPivotInsetPx = with(density) { PredictiveBackPivotInset.toPx() }
    var previousDepth by remember { mutableIntStateOf(0) }
    val entryProgress = remember(currentEntry.id, state.depth) {
        Animatable(
            initialValue = if (state.depth > previousDepth) 0f else 1f,
        )
    }
    var interactiveBackProgress by remember { mutableFloatStateOf(0f) }
    var interactiveBackSettleTranslationPx by remember { mutableFloatStateOf(0f) }
    var interactiveBackSettleAlpha by remember { mutableFloatStateOf(1f) }
    var isClosingEntry by remember { mutableStateOf(false) }
    var isInteractiveCompleting by remember { mutableStateOf(false) }
    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    var containerHeightPx by remember { mutableFloatStateOf(0f) }
    var dragPivotYFraction by remember { mutableFloatStateOf(0.5f) }
    var pivotFromTrailingEdge by remember { mutableStateOf(true) }
    var systemBackInProgress by remember { mutableStateOf(false) }

    fun resetInteractiveBackState() {
        interactiveBackProgress = 0f
        interactiveBackSettleTranslationPx = 0f
        interactiveBackSettleAlpha = 1f
        isInteractiveCompleting = false
        pivotFromTrailingEdge = true
        dragPivotYFraction = 0.5f
    }

    suspend fun closeCurrentEntryStandard() {
        if (isClosingEntry) return
        val closingEntryId = currentEntry.id
        isClosingEntry = true
        entryProgress.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = TelegramAnimationSpec.STANDARD_DURATION,
                easing = TelegramEasing.Decelerate,
            ),
        )
        state.closeLast()
        topicScrollSnapshotsByEntryId.remove(closingEntryId)
        resetInteractiveBackState()
        entryProgress.snapTo(1f)
        isClosingEntry = false
    }

    suspend fun cancelInteractiveBack() {
        animate(
            initialValue = interactiveBackProgress,
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = TelegramAnimationSpec.PREDICTIVE_BACK_CANCEL_DURATION,
                easing = TelegramEasing.EaseOutQuint,
            ),
        ) { value, _ ->
            interactiveBackProgress = value
        }
        resetInteractiveBackState()
    }

    suspend fun completeInteractiveBack(targetOffsetPx: Float) {
        if (isClosingEntry) return
        val closingEntryId = currentEntry.id
        isClosingEntry = true
        isInteractiveCompleting = true
        val startTranslationPx = predictiveBackMaxOffsetPx * interactiveBackProgress
        interactiveBackSettleTranslationPx = startTranslationPx
        interactiveBackSettleAlpha = 1f
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = TelegramAnimationSpec.PREDICTIVE_BACK_COMPLETE_DURATION,
                easing = TelegramEasing.EaseOutQuint,
            ),
        ) { value, _ ->
            interactiveBackSettleTranslationPx = lerp(
                start = startTranslationPx,
                stop = targetOffsetPx,
                fraction = value,
            )
            interactiveBackSettleAlpha = 1f - value
        }
        state.closeLast()
        topicScrollSnapshotsByEntryId.remove(closingEntryId)
        resetInteractiveBackState()
        entryProgress.snapTo(1f)
        isClosingEntry = false
    }

    LaunchedEffect(currentEntry.id, state.depth) {
        resetInteractiveBackState()
        if (state.depth > previousDepth) {
            entryProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = TelegramAnimationSpec.STANDARD_DURATION,
                    easing = TelegramEasing.Decelerate,
                ),
            )
        } else {
            entryProgress.snapTo(1f)
        }
        previousDepth = state.depth
    }

    BackHandler(enabled = interactionsEnabled && state.hasPushedEntry && !isClosingEntry) {
        scope.launch {
            closeCurrentEntryStandard()
        }
    }

    PredictiveBackHandler(
        enabled = interactionsEnabled && state.hasPushedEntry && !isClosingEntry,
    ) { backEvents ->
        systemBackInProgress = true
        var receivedPredictiveProgressEvents = false
        try {
            backEvents.collect { event ->
                receivedPredictiveProgressEvents = true
                pivotFromTrailingEdge = event.swipeEdge != BackEventCompat.EDGE_RIGHT
                dragPivotYFraction = predictiveBackPivotYFraction(
                    containerHeightPx = containerHeightPx,
                    touchYPx = event.touchY,
                )
                interactiveBackProgress = event.progress.coerceIn(0f, 1f)
            }
            if (shouldUseInteractiveBackCompletion(receivedPredictiveProgressEvents)) {
                completeInteractiveBack(
                    targetOffsetPx = containerWidthPx.takeIf { it > 0f } ?: predictiveBackMaxOffsetPx,
                )
            } else {
                closeCurrentEntryStandard()
            }
        } catch (throwable: Throwable) {
            if (receivedPredictiveProgressEvents) {
                cancelInteractiveBack()
            } else {
                resetInteractiveBackState()
            }
        } finally {
            systemBackInProgress = false
        }
    }

    val hasInteractiveBackProgress = interactiveBackProgress > 0f || isInteractiveCompleting
    val interactiveLayerOffsets = resolveTelegramPredictiveBackLayerOffsets(
        progress = interactiveBackProgress,
        settleTranslationPx = interactiveBackSettleTranslationPx,
        isCompleting = isInteractiveCompleting,
        maxOffsetPx = predictiveBackMaxOffsetPx,
    )
    val standardEntryProgress = resolveStandardEntryRenderProgress(
        entryProgress = entryProgress.value,
        currentDepth = state.depth,
        previousDepth = previousDepth,
    )
    val currentHostTranslationX = when {
        hasInteractiveBackProgress -> interactiveLayerOffsets.hostTranslationPx
        else -> (1f - standardEntryProgress) * slideOffsetPx
    }
    val currentInnerTranslationX = if (hasInteractiveBackProgress) {
        interactiveLayerOffsets.innerCompensationTranslationPx
    } else {
        0f
    }
    val dragProgress = if (hasInteractiveBackProgress) {
        interactiveLayerOffsets.progress
    } else {
        0f
    }
    val currentAlpha = when {
        isInteractiveCompleting -> interactiveBackSettleAlpha
        interactiveBackProgress > 0f -> 1f
        else -> standardEntryProgress
    }
    val currentScale = if (hasInteractiveBackProgress) {
        telegramPredictiveBackScale(
            progress = dragProgress,
            currentAlpha = currentAlpha,
        )
    } else {
        1f
    }
    val currentCornerRadius = 28.dp * dragProgress
    val currentTransformOrigin = TransformOrigin(
        pivotFractionX = predictiveBackPivotXFraction(
            containerWidthPx = containerWidthPx,
            pivotInsetPx = predictiveBackPivotInsetPx,
            pivotFromTrailingEdge = pivotFromTrailingEdge,
        ),
        pivotFractionY = dragPivotYFraction,
    )
    val previousSceneScrimAlpha = telegramPredictiveBackScrimAlpha(
        translationXPx = interactiveLayerOffsets.hostTranslationPx,
        containerWidthPx = containerWidthPx.takeIf { it > 0f } ?: predictiveBackMaxOffsetPx,
    )
        .takeIf { hasInteractiveBackProgress }
        ?: 0f

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(overlayZIndex)
            .pointerInput(currentEntry.id, isClosingEntry, interactionsEnabled, systemBackInProgress) {
                if (!interactionsEnabled || isClosingEntry || systemBackInProgress) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.position.x > edgeTriggerPx) return@awaitEachGesture
                    pivotFromTrailingEdge = true
                    dragPivotYFraction = predictiveBackPivotYFraction(
                        containerHeightPx = size.height.toFloat(),
                        touchYPx = down.position.y,
                    )

                    var totalDx = 0f
                    var totalDy = 0f
                    var claimed = false

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break

                        if (!change.pressed) {
                            if (claimed) {
                                val shouldClose = interactiveBackProgress >= 1f
                                if (shouldClose) {
                                    scope.launch {
                                        completeInteractiveBack(
                                            targetOffsetPx = size.width.toFloat(),
                                        )
                                    }
                                } else {
                                    scope.launch {
                                        cancelInteractiveBack()
                                    }
                                }
                            }
                            break
                        }

                        val delta = change.positionChange()
                        totalDx += delta.x
                        totalDy += delta.y

                        if (!claimed) {
                            if (abs(totalDx) > slopPx && abs(totalDx) > abs(totalDy)) {
                                if (totalDx > 0f) {
                                    claimed = true
                                    change.consume()
                                    interactiveBackProgress = manualEdgeBackProgress(
                                        offsetPx = totalDx,
                                        containerWidthPx = size.width.toFloat(),
                                    )
                                } else {
                                    break
                                }
                            } else if (abs(totalDy) > slopPx) {
                                break
                            }
                        } else {
                            change.consume()
                            interactiveBackProgress = manualEdgeBackProgress(
                                offsetPx = totalDx.coerceAtLeast(0f),
                                containerWidthPx = size.width.toFloat(),
                            )
                        }
                    }
                }
            },
    ) {
        if (previousEntry != null) {
            NativePageEntryContent(
                entry = previousEntry,
                topicScrollSnapshotsByEntryId = topicScrollSnapshotsByEntryId,
                titlesByEntryId = titlesByEntryId,
                stateHolder = saveableStateHolder,
                onSessionRecoveryRequired = onSessionRecoveryRequired,
                onOpenLink = onOpenLink,
                onPresentTopic = state::presentTopic,
                onPresentUser = state::presentUser,
                onClose = {
                    scope.launch {
                        closeCurrentEntryStandard()
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

        }

        if (previousSceneScrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = previousSceneScrimAlpha)),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    clipRect {
                        this@drawWithContent.drawContent()
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        containerWidthPx = size.width.toFloat()
                        containerHeightPx = size.height.toFloat()
                    }
                    .graphicsLayer {
                        translationX = currentHostTranslationX
                        alpha = currentAlpha
                    },
            ) {
                NativePageEntryContent(
                    entry = currentEntry,
                    topicScrollSnapshotsByEntryId = topicScrollSnapshotsByEntryId,
                    titlesByEntryId = titlesByEntryId,
                    stateHolder = saveableStateHolder,
                    onSessionRecoveryRequired = onSessionRecoveryRequired,
                    onOpenLink = onOpenLink,
                    onPresentTopic = state::presentTopic,
                    onPresentUser = state::presentUser,
                    onClose = {
                        scope.launch {
                            closeCurrentEntryStandard()
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = currentInnerTranslationX
                            scaleX = currentScale
                            scaleY = currentScale
                            transformOrigin = currentTransformOrigin
                            if (dragProgress > 0f) {
                                clip = true
                                shape = RoundedCornerShape(currentCornerRadius)
                                shadowElevation = with(density) { 20.dp.toPx() * dragProgress }
                            } else {
                                clip = false
                                shadowElevation = 0f
                            }
                        },
                )
            }
        }
    }
}

internal fun predictiveBackPivotXFraction(
    containerWidthPx: Float,
    pivotInsetPx: Float,
    pivotFromTrailingEdge: Boolean,
): Float {
    val safeContainerWidthPx = containerWidthPx.coerceAtLeast(1f)
    val clampedInsetPx = pivotInsetPx.coerceIn(0f, safeContainerWidthPx)
    return if (pivotFromTrailingEdge) {
        ((safeContainerWidthPx - clampedInsetPx) / safeContainerWidthPx)
            .coerceIn(0f, 1f)
    } else {
        (clampedInsetPx / safeContainerWidthPx)
            .coerceIn(0f, 1f)
    }
}

internal fun predictiveBackPivotYFraction(
    containerHeightPx: Float,
    touchYPx: Float,
): Float {
    val safeContainerHeightPx = containerHeightPx.coerceAtLeast(1f)
    return (touchYPx / safeContainerHeightPx).coerceIn(0f, 1f)
}

internal fun normalizeInteractiveBackProgress(
    offsetPx: Float,
    maxOffsetPx: Float,
): Float {
    val safeMaxOffsetPx = maxOffsetPx.coerceAtLeast(1f)
    return (offsetPx / safeMaxOffsetPx).coerceIn(0f, 1f)
}

internal fun manualEdgeBackProgress(
    offsetPx: Float,
    containerWidthPx: Float,
): Float {
    val visualProgressWidthPx = containerWidthPx
        .coerceAtLeast(1f) * TelegramAnimationSpec.PREDICTIVE_BACK_THRESHOLD
    return normalizeInteractiveBackProgress(
        offsetPx = offsetPx,
        maxOffsetPx = visualProgressWidthPx,
    )
}

internal fun resolveTelegramPredictiveBackLayerOffsets(
    progress: Float,
    settleTranslationPx: Float,
    isCompleting: Boolean,
    maxOffsetPx: Float,
): TelegramPredictiveBackLayerOffsets {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val liveGestureTranslationPx = maxOffsetPx.coerceAtLeast(0f) * clampedProgress
    val hostTranslationPx = if (isCompleting) {
        settleTranslationPx.coerceAtLeast(0f)
    } else {
        liveGestureTranslationPx
    }
    val gestureRangeTranslationPx = hostTranslationPx.coerceIn(0f, maxOffsetPx.coerceAtLeast(0f))
    return TelegramPredictiveBackLayerOffsets(
        hostTranslationPx = hostTranslationPx,
        innerCompensationTranslationPx = -gestureRangeTranslationPx,
        progress = normalizeInteractiveBackProgress(
            offsetPx = gestureRangeTranslationPx,
            maxOffsetPx = maxOffsetPx,
        ),
    )
}

internal fun shouldUseInteractiveBackCompletion(
    receivedPredictiveProgressEvents: Boolean,
): Boolean = receivedPredictiveProgressEvents

internal fun resolveStandardEntryRenderProgress(
    entryProgress: Float,
    currentDepth: Int,
    previousDepth: Int,
): Float {
    return entryProgress.coerceIn(0f, 1f)
}

internal fun telegramPredictiveBackScale(
    progress: Float,
    currentAlpha: Float,
): Float {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val targetScale = lerp(
        start = TelegramAnimationSpec.PREDICTIVE_BACK_GESTURE_SCALE,
        stop = TelegramAnimationSpec.PREDICTIVE_BACK_COMPLETE_SCALE,
        fraction = 1f - currentAlpha.coerceIn(0f, 1f),
    )
    return lerp(
        start = 1f,
        stop = targetScale,
        fraction = clampedProgress,
    )
}

internal fun telegramPredictiveBackScrimAlpha(
    translationXPx: Float,
    containerWidthPx: Float,
): Float {
    val safeContainerWidthPx = containerWidthPx.coerceAtLeast(1f)
    val widthOffsetPx = (safeContainerWidthPx - translationXPx).coerceIn(0f, safeContainerWidthPx)
    val opacity = (widthOffsetPx / safeContainerWidthPx)
        .coerceIn(0f, TelegramAnimationSpec.PREDICTIVE_BACK_SCRIM_MAX_OPACITY)
    return TelegramAnimationSpec.PREDICTIVE_BACK_SCRIM_MAX_ALPHA * opacity
}

private fun lerp(
    start: Float,
    stop: Float,
    fraction: Float,
): Float {
    return start + (stop - start) * fraction.coerceIn(0f, 1f)
}

@Composable
private fun NativePageEntryContent(
    entry: NativePageEntry,
    topicScrollSnapshotsByEntryId: MutableMap<String, TopicScrollSnapshot>,
    titlesByEntryId: MutableMap<String, String?>,
    stateHolder: SaveableStateHolder,
    onSessionRecoveryRequired: () -> Unit,
    onOpenLink: (String) -> Unit,
    onPresentTopic: (Long) -> Unit,
    onPresentUser: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    key(entry.id) {
        stateHolder.SaveableStateProvider(entry.id) {
            NativeDoReaderScaffold(
                modifier = modifier.fillMaxSize(),
                topBar = {
                    NativeDoPushedTopBar(
                        title = titlesByEntryId[entry.id] ?: defaultNavigationTitle(entry),
                        subtitle = defaultNavigationSubtitle(entry),
                        onBackClick = onClose,
                    )
                },
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                ) {
                    when (entry) {
                        is NativePageEntry.Topic -> {
                            TopicRoute(
                                topicId = entry.topicId,
                                onTopicClick = onPresentTopic,
                                onUserClick = onPresentUser,
                                onOpenLink = onOpenLink,
                                initialScrollSnapshot = topicScrollSnapshotsByEntryId[entry.id],
                                onScrollSnapshotChanged = { snapshot ->
                                    topicScrollSnapshotsByEntryId[entry.id] = snapshot
                                },
                                onHeaderTitleChanged = { title ->
                                    if (title == null) {
                                        titlesByEntryId.remove(entry.id)
                                    } else {
                                        titlesByEntryId[entry.id] = title
                                    }
                                },
                                onSessionRecoveryRequired = onSessionRecoveryRequired,
                            )
                        }

                        is NativePageEntry.User -> {
                            ProfileRoute(
                                viewer = ProfileViewer.Other(entry.username),
                                onSessionRecoveryRequired = onSessionRecoveryRequired,
                                onTopicClick = onPresentTopic,
                                onUserClick = onPresentUser,
                                onOpenLink = onOpenLink,
                                onHeaderTitleChanged = { title ->
                                    if (title == null) {
                                        titlesByEntryId.remove(entry.id)
                                    } else {
                                        titlesByEntryId[entry.id] = title
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NativeDoPushedTopBar(
    title: String,
    subtitle: String?,
    onBackClick: () -> Unit,
) {
    val colors = NativeDoThemeExtras.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        NativeDoGlassBar(
            modifier = Modifier.fillMaxWidth(),
            minHeight = AppTopBarRowMinHeight,
            contentPadding = PaddingValues(
                horizontal = AppTopBarHorizontalPadding,
                vertical = AppTopBarVerticalPadding,
            ),
            sectionSpacing = 12.dp,
            leading = {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(44.dp),
                ) {
                    NativeDoBackIcon(
                        progress = 0f,
                        contentDescription = null,
                    )
                }
            },
            title = {
                NativeDoAnimatedLabel(
                    title = title,
                    subtitle = subtitle,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    titleStyle = MaterialTheme.typography.titleLarge,
                    subtitleStyle = MaterialTheme.typography.labelMedium,
                    titleColor = colors.textPrimary,
                    subtitleColor = colors.textSecondary,
                )
            },
            trailing = {
                Box(modifier = Modifier.size(44.dp))
            },
        )

        HorizontalDivider(
            modifier = Modifier.align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        )
    }
}

private fun defaultNavigationTitle(
    entry: NativePageEntry,
): String = when (entry) {
    is NativePageEntry.Topic -> "Topic"
    is NativePageEntry.User -> "@${entry.username}"
}

private fun defaultNavigationSubtitle(
    entry: NativePageEntry,
): String? = when (entry) {
    is NativePageEntry.Topic -> null
    is NativePageEntry.User -> "Profile"
}
