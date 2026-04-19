package io.github.baba983.nativedo.feature.browser

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.baba983.nativedo.NativeDoApplication
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderBlock
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderContent
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderListItem
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderTableCell
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderTableCellAlignment
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderTableRow
import io.github.baba983.nativedo.core.ui.BulletinMessage
import io.github.baba983.nativedo.core.ui.BulletinTone
import io.github.baba983.nativedo.core.ui.LocalBulletinHostState
import io.github.baba983.nativedo.core.ui.buildMediaViewerImageIndexLookup
import io.github.baba983.nativedo.core.ui.findGalleryStartIndex
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.core.ui.NativeDoAnchoredPopup
import io.github.baba983.nativedo.core.ui.NativeDoBackIcon
import io.github.baba983.nativedo.core.ui.NativeDoPopupMenuDivider
import io.github.baba983.nativedo.core.ui.NativeDoPopupMenuItem
import io.github.baba983.nativedo.core.ui.PostBodyImage
import io.github.baba983.nativedo.core.ui.PostImageView
import io.github.baba983.nativedo.core.ui.PostTextDocumentView
import io.github.baba983.nativedo.core.ui.TextDocument
import io.github.baba983.nativedo.core.ui.mediaviewer.MediaItem
import io.github.baba983.nativedo.core.ui.mediaviewer.MediaViewerHost
import io.github.baba983.nativedo.core.ui.mediaviewer.MediaViewerLaunchSource
import io.github.baba983.nativedo.core.ui.mediaviewer.rememberMediaViewerState
import io.github.baba983.nativedo.ui.AppTopBarHorizontalPadding
import io.github.baba983.nativedo.ui.AppTopBarRowMinHeight
import io.github.baba983.nativedo.ui.NativeDoReaderScaffold
import io.github.baba983.nativedo.ui.theme.NativeDoEasing
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Velocity
import android.content.Intent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.lerp
import io.github.baba983.nativedo.core.navigation.openInSystemBrowser
import io.github.baba983.nativedo.ui.BrowserHostMinimizedPreview
import io.github.baba983.nativedo.ui.buildBrowserHostReaderPreview
import java.util.IdentityHashMap
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.max

private val ReaderHandleAreaHeight = 24.dp
private val ReaderHandleMinWidth = 32.dp
private val ReaderHandleMaxWidth = 48.dp
private val ReaderHandleHeight = 4.dp
private val ReaderSheetCornerRadius = 16.dp
private const val ReaderSheetPartialTopFraction = 0.4f
private const val ReaderSheetDismissResistance = 0.72f
private val ReaderHeaderMaxHeight = AppTopBarRowMinHeight
private val ReaderHeaderMinHeight = 32.dp
private const val ReaderFollowUpEnterDurationMillis = 320
private const val ReaderFollowUpReturnDurationMillis = 220

internal data class AppBrowserReaderContainerChromeState(
    val cornerRadius: Dp,
    val handleWidth: Dp,
    val handleAlpha: Float,
    val occupiesStatusBar: Boolean,
)

internal data class AppBrowserReaderFollowUpTransitionState(
    val translationFraction: Float,
    val alpha: Float,
)

private enum class AppBrowserReaderDismissMode {
    ReturnToBrowser,
    MinimizeToDock,
}

private enum class AppBrowserReaderDragOwner {
    Header,
    Content,
}

internal fun buildAppBrowserReaderContainerChromeState(
    fullscreenProgress: Float,
): AppBrowserReaderContainerChromeState {
    val progress = buildAppBrowserSheetAttachedProgress(fullscreenProgress)
    return AppBrowserReaderContainerChromeState(
        cornerRadius = lerp(ReaderSheetCornerRadius, 0.dp, progress),
        handleWidth = lerp(ReaderHandleMinWidth, ReaderHandleMaxWidth, progress),
        handleAlpha = 1f - progress,
        occupiesStatusBar = progress >= 0.999f,
    )
}

internal fun buildAppBrowserReaderTopChromeHeight(
    fullscreenProgress: Float,
    statusBarInset: Dp,
): Dp = lerp(
    start = ReaderHandleAreaHeight,
    stop = statusBarInset.coerceAtLeast(0.dp),
    fraction = buildAppBrowserSheetAttachedProgress(fullscreenProgress),
)

internal fun buildAppBrowserReaderFollowUpTransitionState(
    progress: Float,
): AppBrowserReaderFollowUpTransitionState {
    val normalizedProgress = progress.coerceIn(0f, 1f)
    return AppBrowserReaderFollowUpTransitionState(
        translationFraction = 1f - normalizedProgress,
        alpha = 0.92f + (0.08f * normalizedProgress),
    )
}

internal fun buildAppBrowserReaderSheetSettle(
    currentTopOffsetPx: Float,
    velocityPxPerSecond: Float,
    partialTopOffsetPx: Float,
    closedTopOffsetPx: Float,
    dismissThresholdFraction: Float = 0.34f,
    dismissVelocityThresholdPxPerSecond: Float = 900f,
    fullscreenVelocityThresholdPxPerSecond: Float = -900f,
): AppBrowserSheetSettle {
    val safeClosedTopOffsetPx = closedTopOffsetPx.coerceAtLeast(1f)
    val safePartialTopOffsetPx = partialTopOffsetPx.coerceIn(0f, safeClosedTopOffsetPx)
    val clampedTopOffsetPx = currentTopOffsetPx.coerceIn(0f, safeClosedTopOffsetPx)
    val dismissThresholdTopOffsetPx = safePartialTopOffsetPx +
        ((safeClosedTopOffsetPx - safePartialTopOffsetPx) * dismissThresholdFraction)
    val fullscreenThresholdTopOffsetPx = safePartialTopOffsetPx * 0.55f
    val target = when {
        velocityPxPerSecond >= dismissVelocityThresholdPxPerSecond -> AppBrowserSheetAnchor.Closed
        velocityPxPerSecond <= fullscreenVelocityThresholdPxPerSecond -> AppBrowserSheetAnchor.Fullscreen
        clampedTopOffsetPx >= dismissThresholdTopOffsetPx -> AppBrowserSheetAnchor.Closed
        clampedTopOffsetPx <= fullscreenThresholdTopOffsetPx -> AppBrowserSheetAnchor.Fullscreen
        else -> AppBrowserSheetAnchor.Partial
    }
    val targetOffsetPx = when (target) {
        AppBrowserSheetAnchor.Closed -> safeClosedTopOffsetPx
        AppBrowserSheetAnchor.Partial -> safePartialTopOffsetPx
        AppBrowserSheetAnchor.Fullscreen -> 0f
    }
    val travelDistancePx = kotlin.math.abs(targetOffsetPx - clampedTopOffsetPx)
    val durationMillis = maxOf(((360f / safeClosedTopOffsetPx) * travelDistancePx).toInt(), 220)
    return AppBrowserSheetSettle(
        target = target,
        durationMillis = durationMillis,
    )
}

@Composable
internal fun AppBrowserReaderScene(
    entryId: Long,
    content: AppBrowserReaderContent,
    foregroundActivationKey: Long,
    restoredFromDock: Boolean,
    onMinimize: () -> Unit = {},
    onClose: () -> Unit,
    onPreviewChanged: (BrowserHostMinimizedPreview) -> Unit = {},
    onOpenLink: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    isForeground: Boolean = true,
) {
    val context = LocalContext.current
    val appContainer = remember(context) {
        (context.applicationContext as NativeDoApplication).appContainer
    }
    AppBrowserReaderScene(
        entryId = entryId,
        content = content,
        cookieStore = appContainer.cookieStore,
        baseUrl = appContainer.baseUrl,
        foregroundActivationKey = foregroundActivationKey,
        restoredFromDock = restoredFromDock,
        onMinimize = onMinimize,
        onClose = onClose,
        onPreviewChanged = onPreviewChanged,
        onOpenLink = onOpenLink,
        modifier = modifier,
        isForeground = isForeground,
    )
}

@Composable
private fun AppBrowserReaderScene(
    entryId: Long,
    content: AppBrowserReaderContent,
    cookieStore: CookieStore,
    baseUrl: String,
    foregroundActivationKey: Long,
    restoredFromDock: Boolean,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    onPreviewChanged: (BrowserHostMinimizedPreview) -> Unit,
    onOpenLink: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    isForeground: Boolean = true,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val bulletinHostState = LocalBulletinHostState.current
    val colors = NativeDoThemeExtras.colors
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }.coerceAtLeast(1f)
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }.coerceAtLeast(1f)
    val statusBarInsetDp = remember(view, density) {
        with(density) {
            (view.rootWindowInsets?.systemWindowInsetTop ?: 0).toDp()
        }
    }
    val statusBarInsetPx = remember(statusBarInsetDp, density) {
        with(density) { statusBarInsetDp.toPx() }
    }
    val partialTopOffsetPx = remember(screenHeightPx, statusBarInsetPx, density) {
        max(
            screenHeightPx * ReaderSheetPartialTopFraction,
            statusBarInsetPx + with(density) { (ReaderHandleAreaHeight + 56.dp).toPx() },
        )
    }
    val sheetTopOffsetPx = remember(entryId) { Animatable(0f) }
    val followUpTransitionProgress = remember(entryId) { Animatable(0f) }
    var isClosing by remember(entryId) { mutableStateOf(false) }
    var lastAnimatedForegroundActivationKey by remember(entryId) {
        mutableLongStateOf(Long.MIN_VALUE)
    }
    var dragOwner by remember(entryId) { mutableStateOf<AppBrowserReaderDragOwner?>(null) }
    var dragTopOffsetPx by remember(entryId) { mutableStateOf(0f) }
    val lazyListState = rememberLazyListState()
    var actionsExpanded by remember(entryId) { mutableStateOf(false) }
    var headerHeightDp by remember(entryId) { mutableStateOf(ReaderHeaderMaxHeight) }
    val isDragging = dragOwner != null
    val currentTopOffsetPx = if (isDragging) dragTopOffsetPx else sheetTopOffsetPx.value
    val presentationState = remember(currentTopOffsetPx, partialTopOffsetPx, screenHeightPx) {
        buildAppBrowserSheetPresentationState(
            topOffsetPx = currentTopOffsetPx,
            partialTopOffsetPx = partialTopOffsetPx,
            closedTopOffsetPx = screenHeightPx,
        )
    }
    val containerChromeState = remember(presentationState.fullscreenProgress) {
        buildAppBrowserReaderContainerChromeState(presentationState.fullscreenProgress)
    }
    val followUpTransitionState = remember(followUpTransitionProgress.value) {
        buildAppBrowserReaderFollowUpTransitionState(followUpTransitionProgress.value)
    }
    val topChromeHeight = remember(presentationState.fullscreenProgress, statusBarInsetDp) {
        buildAppBrowserReaderTopChromeHeight(
            fullscreenProgress = presentationState.fullscreenProgress,
            statusBarInset = statusBarInsetDp,
        )
    }
    val canCollapseHeader = containerChromeState.occupiesStatusBar && !isDragging
    val animatedHeaderHeight by animateDpAsState(
        targetValue = headerHeightDp,
        animationSpec = tween(durationMillis = 180, easing = NativeDoEasing.Standard),
        label = "readerHeaderHeight",
    )
    val headerCollapseProgress = remember(animatedHeaderHeight) {
        1f - ((animatedHeaderHeight - ReaderHeaderMinHeight) /
            (ReaderHeaderMaxHeight - ReaderHeaderMinHeight)).coerceIn(0f, 1f)
    }
    val headerScale = remember(headerCollapseProgress) {
        1f - (0.12f * headerCollapseProgress)
    }
    val readerMaxImageHeight = remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        readerImageMaxHeight(
            screenWidthDp = configuration.screenWidthDp,
            screenHeightDp = configuration.screenHeightDp,
        )
    }
    val standaloneHeroImage = remember(content.heroImageUrl) {
        content.heroImageUrl
            ?.takeIf(String::isNotBlank)
            ?.let { rawUrl ->
                PostBodyImage(rawUrl = rawUrl)
            }
    }
    val showStandaloneHero = remember(standaloneHeroImage, content.document.blocks) {
        standaloneHeroImage != null &&
            content.document.blocks.none { block -> block is AppBrowserReaderBlock.Image }
    }
    val readerImages = remember(content.document.blocks, showStandaloneHero, standaloneHeroImage) {
        collectReaderImages(
            blocks = content.document.blocks,
            standaloneHeroImage = standaloneHeroImage?.takeIf { showStandaloneHero },
        )
    }
    val imageIndexLookup = remember(readerImages) {
        buildMediaViewerImageIndexLookup(readerImages)
    }
    val imageMediaItems = remember(readerImages) {
        readerImages.map { image ->
            MediaItem.Image(
                rawUrl = image.rawUrl,
                originalUrl = image.originalUrl,
                width = image.width,
                height = image.height,
            )
        }
    }
    val imageLaunchSourceLookup = remember(readerImages) {
        IdentityHashMap<PostBodyImage, MediaViewerLaunchSource>(readerImages.size)
    }
    val mediaItemImageLookup = remember(imageMediaItems, readerImages) {
        IdentityHashMap<MediaItem, PostBodyImage>(readerImages.size).apply {
            imageMediaItems.forEachIndexed { index, item ->
                put(item, readerImages[index])
            }
        }
    }
    val mediaViewerState = rememberMediaViewerState()
    var hiddenViewerImage by remember(readerImages) { mutableStateOf<PostBodyImage?>(null) }
    var contentBoundsInWindow by remember(entryId) { mutableStateOf<IntRect?>(null) }
    val sourceHost = remember(content.url) {
        content.url.toHttpUrlOrNull()?.host ?: content.url
    }
    val topLabel = remember(content.sourceName, sourceHost) {
        content.sourceName
            ?.takeIf(String::isNotBlank)
            ?: sourceHost
    }
    val metaItems = remember(content.byline, content.publishedTime, topLabel, sourceHost) {
        buildAppBrowserReaderMetaItems(
            byline = content.byline,
            publishedTime = content.publishedTime,
            sourceLabel = topLabel,
            sourceHost = sourceHost,
        )
    }
    val metaLine = remember(metaItems) {
        metaItems.joinToString(separator = " • ")
    }
    val typography = remember { appBrowserReaderTypography() }
    val metrics = remember { appBrowserReaderBlockMetrics() }
    val paragraphStyle = typography.paragraph
    val quoteStyle = typography.quote
    val captionStyle = typography.caption

    fun publishReaderPreview(
        snapshot: Bitmap? = null,
    ) {
        onPreviewChanged(
            buildBrowserHostReaderPreview(
                content = content,
                snapshot = snapshot,
            ),
        )
    }

    suspend fun animateToAnchor(
        target: AppBrowserSheetAnchor,
        durationMillis: Int? = null,
    ) {
        val targetOffsetPx = when (target) {
            AppBrowserSheetAnchor.Closed -> screenHeightPx
            AppBrowserSheetAnchor.Partial -> partialTopOffsetPx
            AppBrowserSheetAnchor.Fullscreen -> 0f
        }
        sheetTopOffsetPx.animateTo(
            targetValue = targetOffsetPx,
            animationSpec = tween(
                durationMillis = durationMillis ?: 320,
                easing = if (target == AppBrowserSheetAnchor.Closed) {
                    NativeDoEasing.BrowserDismiss
                } else {
                    NativeDoEasing.EaseOutQuint
                },
            ),
        )
    }

    LaunchedEffect(
        screenHeightPx,
        partialTopOffsetPx,
        foregroundActivationKey,
        restoredFromDock,
        isForeground,
    ) {
        if (!isForeground || lastAnimatedForegroundActivationKey == foregroundActivationKey) {
            return@LaunchedEffect
        }
        lastAnimatedForegroundActivationKey = foregroundActivationKey
        isClosing = false
        dragOwner = null
        dragTopOffsetPx = 0f
        headerHeightDp = ReaderHeaderMaxHeight
        actionsExpanded = false
        if (restoredFromDock) {
            followUpTransitionProgress.snapTo(1f)
            sheetTopOffsetPx.snapTo(screenHeightPx)
            animateToAnchor(
                target = AppBrowserSheetAnchor.Fullscreen,
                durationMillis = ReaderFollowUpEnterDurationMillis,
            )
        } else {
            sheetTopOffsetPx.snapTo(0f)
            followUpTransitionProgress.snapTo(0f)
            followUpTransitionProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = ReaderFollowUpEnterDurationMillis,
                    easing = NativeDoEasing.EaseOutQuint,
                ),
            )
        }
    }

    fun requestClose(
        dismissMode: AppBrowserReaderDismissMode = AppBrowserReaderDismissMode.ReturnToBrowser,
        durationMillis: Int = when (dismissMode) {
            AppBrowserReaderDismissMode.ReturnToBrowser -> ReaderFollowUpReturnDurationMillis
            AppBrowserReaderDismissMode.MinimizeToDock -> 220
        },
    ) {
        if (isClosing) {
            return
        }
        isClosing = true
        coroutineScope.launch {
            if (isDragging) {
                sheetTopOffsetPx.snapTo(dragTopOffsetPx)
                dragOwner = null
            }
            when (dismissMode) {
                AppBrowserReaderDismissMode.ReturnToBrowser -> {
                    dragTopOffsetPx = 0f
                    sheetTopOffsetPx.snapTo(0f)
                    followUpTransitionProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = durationMillis,
                            easing = NativeDoEasing.EaseOutQuint,
                        ),
                    )
                }

                AppBrowserReaderDismissMode.MinimizeToDock -> {
                    publishReaderPreview()
                    onMinimize()
                    return@launch
                }
            }
            onClose()
        }
    }

    fun ensureDragOwner(owner: AppBrowserReaderDragOwner): Boolean {
        val currentOwner = dragOwner
        if (currentOwner != null && currentOwner != owner) {
            return false
        }
        if (currentOwner == null) {
            dragTopOffsetPx = sheetTopOffsetPx.value
            dragOwner = owner
        }
        return true
    }

    fun applyDraggedSheetDelta(
        owner: AppBrowserReaderDragOwner,
        dragAmount: Float,
    ) {
        if (!ensureDragOwner(owner)) {
            return
        }
        val adjustedDragAmount = if (dragTopOffsetPx >= partialTopOffsetPx && dragAmount > 0f) {
            dragAmount * ReaderSheetDismissResistance
        } else {
            dragAmount
        }
        dragTopOffsetPx = (dragTopOffsetPx + adjustedDragAmount)
            .coerceIn(0f, screenHeightPx)
    }

    suspend fun completeDraggedSheet(
        owner: AppBrowserReaderDragOwner,
        settle: AppBrowserSheetSettle,
    ) {
        if (dragOwner != owner) {
            return
        }
        sheetTopOffsetPx.snapTo(dragTopOffsetPx)
        dragOwner = null
        when (settle.target) {
            AppBrowserSheetAnchor.Closed -> requestClose(
                dismissMode = AppBrowserReaderDismissMode.MinimizeToDock,
                durationMillis = settle.durationMillis,
            )
            AppBrowserSheetAnchor.Partial,
            AppBrowserSheetAnchor.Fullscreen -> animateToAnchor(
                target = settle.target,
                durationMillis = settle.durationMillis,
            )
        }
    }

    fun settleDraggedSheet(owner: AppBrowserReaderDragOwner, velocityPxPerSecond: Float) {
        if (dragOwner != owner) {
            return
        }
        val settle = buildAppBrowserReaderSheetSettle(
            currentTopOffsetPx = dragTopOffsetPx,
            velocityPxPerSecond = velocityPxPerSecond,
            partialTopOffsetPx = partialTopOffsetPx,
            closedTopOffsetPx = screenHeightPx,
        )
        coroutineScope.launch {
            completeDraggedSheet(owner, settle)
        }
    }

    BackHandler(enabled = isForeground) { requestClose() }

    fun resolveCurrentTopOffsetPx(): Float = if (isDragging) dragTopOffsetPx else sheetTopOffsetPx.value

    fun resolveCanCollapseHeader(): Boolean {
        return buildAppBrowserReaderContainerChromeState(
            buildAppBrowserSheetPresentationState(
                topOffsetPx = resolveCurrentTopOffsetPx(),
                partialTopOffsetPx = partialTopOffsetPx,
                closedTopOffsetPx = screenHeightPx,
            ).attachedProgress,
        ).occupiesStatusBar && !isDragging
    }

    val headerMaxPx = with(density) { ReaderHeaderMaxHeight.toPx() }
    val headerMinPx = with(density) { ReaderHeaderMinHeight.toPx() }
    val nestedScrollConnection = remember(
        density,
        lazyListState,
        screenHeightPx,
        partialTopOffsetPx,
    ) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val isAtTop = lazyListState.firstVisibleItemIndex == 0 &&
                    lazyListState.firstVisibleItemScrollOffset == 0
                val currentTopOffsetPx = resolveCurrentTopOffsetPx()
                val canCollapseHeader = resolveCanCollapseHeader()

                if (currentTopOffsetPx > 0f && available.y < 0f) {
                    if (!ensureDragOwner(AppBrowserReaderDragOwner.Content)) {
                        return Offset.Zero
                    }
                    val consumable = minOf(-available.y, dragTopOffsetPx)
                    applyDraggedSheetDelta(
                        owner = AppBrowserReaderDragOwner.Content,
                        dragAmount = -consumable,
                    )
                    return Offset(0f, -consumable)
                }

                if (available.y > 0f && isAtTop) {
                    if (canCollapseHeader) {
                        val currentPx = with(density) { headerHeightDp.toPx() }
                        if (currentPx < headerMaxPx) {
                            val consumable = minOf(available.y, headerMaxPx - currentPx)
                            headerHeightDp = with(density) { (currentPx + consumable).toDp() }
                            return Offset(0f, consumable)
                        }
                    }
                    if (!ensureDragOwner(AppBrowserReaderDragOwner.Content)) {
                        return Offset.Zero
                    }
                    applyDraggedSheetDelta(
                        owner = AppBrowserReaderDragOwner.Content,
                        dragAmount = available.y,
                    )
                    return Offset(0f, available.y)
                }

                if (canCollapseHeader && available.y < 0f) {
                    val currentPx = with(density) { headerHeightDp.toPx() }
                    if (currentPx > headerMinPx) {
                        val consumable = minOf(-available.y, currentPx - headerMinPx)
                        headerHeightDp = with(density) { (currentPx - consumable).toDp() }
                        return Offset(0f, -consumable)
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (dragOwner == AppBrowserReaderDragOwner.Content) {
                    completeDraggedSheet(
                        owner = AppBrowserReaderDragOwner.Content,
                        settle = buildAppBrowserReaderSheetSettle(
                            currentTopOffsetPx = dragTopOffsetPx,
                            velocityPxPerSecond = available.y,
                            partialTopOffsetPx = partialTopOffsetPx,
                            closedTopOffsetPx = screenHeightPx,
                        ),
                    )
                } else if (resolveCanCollapseHeader()) {
                    val currentPx = with(density) { headerHeightDp.toPx() }
                    val mid = (headerMaxPx + headerMinPx) / 2f
                    headerHeightDp = if (currentPx > mid) {
                        ReaderHeaderMaxHeight
                    } else {
                        ReaderHeaderMinHeight
                    }
                } else {
                    headerHeightDp = ReaderHeaderMaxHeight
                }
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(canCollapseHeader) {
        if (!canCollapseHeader) {
            headerHeightDp = ReaderHeaderMaxHeight
        }
    }
    LaunchedEffect(
        content.title,
        content.sourceName,
        content.url,
    ) {
        publishReaderPreview()
    }

    Box(modifier = Modifier.fillMaxSize())

    NativeDoReaderScaffold(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = screenWidthPx * followUpTransitionState.translationFraction
                translationY = currentTopOffsetPx
                alpha = followUpTransitionState.alpha
            }
            .clip(
                RoundedCornerShape(
                    topStart = containerChromeState.cornerRadius,
                    topEnd = containerChromeState.cornerRadius,
                ),
            ),
        topBar = {
            val headerInteractionSource = remember { MutableInteractionSource() }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.backgroundElevated)
                    .clickable(
                        interactionSource = headerInteractionSource,
                        indication = null,
                        onClick = {},
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(topChromeHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(containerChromeState.handleWidth)
                            .height(ReaderHandleHeight)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(
                                colors.textSecondary.copy(alpha = 0.18f * containerChromeState.handleAlpha),
                            ),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(animatedHeaderHeight)
                        .padding(horizontal = AppTopBarHorizontalPadding)
                        .graphicsLayer {
                            scaleX = headerScale
                            scaleY = headerScale
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { requestClose() }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.app_browser_close),
                            tint = colors.textPrimary,
                        )
                    }
                    Text(
                        text = topLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                actionsExpanded = false
                                publishReaderPreview(
                                    snapshot = captureWindowRegionBitmap(
                                        rootView = view.rootView,
                                        boundsInWindow = contentBoundsInWindow,
                                        maxWidthPx = 720,
                                        maxHeightPx = 1280,
                                    ),
                                )
                                onMinimize()
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.app_browser_minimize),
                                tint = colors.textPrimary,
                            )
                        }
                        IconButton(onClick = { actionsExpanded = !actionsExpanded }) {
                            androidx.compose.material3.Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = stringResource(R.string.app_browser_more_actions),
                            tint = colors.textPrimary,
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { maxOf(0.66f.dp, 1f.toDp()) })
                        .background(
                            androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.08f),
                        ),
                )
            }
            ReaderActionsPopup(
                expanded = actionsExpanded,
                onDismissRequest = { actionsExpanded = false },
                onOpenInBrowser = {
                    context.openInSystemBrowser(content.url)
                },
                onCopyLink = {
                    clipboardManager.setText(AnnotatedString(content.url))
                    bulletinHostState?.show(
                        BulletinMessage(
                            title = context.getString(R.string.app_browser_link_copied),
                            tone = BulletinTone.Info,
                        ),
                    )
                },
                onShare = {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, content.url)
                            },
                            context.getString(R.string.app_browser_share),
                        ),
                    )
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .onGloballyPositioned { coordinates ->
                    contentBoundsInWindow = coordinates.boundsInWindow().roundToIntRect()
                }
                .padding(paddingValues)
                .background(colors.backgroundElevated),
            contentPadding = PaddingValues(
                top = 12.dp,
                bottom = 48.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = metrics.horizontalContentPadding,
                            end = metrics.horizontalContentPadding,
                            top = metrics.headerTopSpacing,
                            bottom = metrics.headerBottomSpacing,
                        ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = content.title,
                        style = typography.title,
                        color = colors.textPrimary,
                    )

                    content.excerpt
                        ?.takeIf(String::isNotBlank)
                        ?.takeIf { excerpt ->
                            !excerpt.equals(content.title, ignoreCase = true)
                        }
                        ?.let { excerpt ->
                            Text(
                                text = excerpt,
                                style = typography.subtitle,
                                color = colors.textSecondary,
                            )
                        }

                    topLabel
                        .takeIf { label ->
                            label.isNotBlank() &&
                                label != sourceHost &&
                                label !in metaItems
                        }
                        ?.let { label ->
                            Text(
                                text = label.uppercase(),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.6.sp,
                                ),
                                color = colors.accent,
                            )
                        }

                    metaLine
                        .takeIf(String::isNotBlank)
                        ?.let { meta ->
                            Text(
                                text = meta,
                                style = typography.meta,
                                color = colors.textSecondary,
                            )
                        }
                }
            }

            if (showStandaloneHero) {
                standaloneHeroImage?.let { heroImage ->
                        item {
                            ReaderImageBlock(
                                image = heroImage,
                                caption = null,
                                credit = null,
                                cookieStore = cookieStore,
                                baseUrl = baseUrl,
                                onOpenLink = onOpenLink,
                                hiddenImage = hiddenViewerImage,
                                onImageSourceChanged = { image, launchSource ->
                                    if (launchSource == null) {
                                        imageLaunchSourceLookup.remove(image)
                                    } else {
                                        imageLaunchSourceLookup[image] = launchSource
                                    }
                                },
                                onImageClick = { clickedImage, launchSource ->
                                    val initialIndex = imageIndexLookup[clickedImage]
                                        ?: findGalleryStartIndex(
                                            galleryImages = readerImages,
                                            clickedImage = clickedImage,
                                        )
                                    val itemLaunchSources = IdentityHashMap<MediaItem, MediaViewerLaunchSource>(
                                        imageMediaItems.size,
                                    )
                                    readerImages.forEachIndexed { mediaIndex, image ->
                                        val source = imageLaunchSourceLookup[image] ?: return@forEachIndexed
                                        imageMediaItems.getOrNull(mediaIndex)?.let { item ->
                                            itemLaunchSources[item] = source
                                        }
                                    }
                                    mediaViewerState.open(
                                        items = imageMediaItems,
                                        initialIndex = initialIndex,
                                        launchSource = launchSource ?: imageLaunchSourceLookup[clickedImage],
                                        itemLaunchSources = itemLaunchSources,
                                    )
                                },
                                captionStyle = captionStyle,
                                maxHeight = readerMaxImageHeight,
                                contentPadding = metrics.horizontalContentPadding,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp),
                            )
                        }
                    }
            }

            if (metaLine.isNotBlank() || showStandaloneHero) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            itemsIndexed(
                items = content.document.blocks,
                key = { index, _ -> "${content.url}-$index" },
            ) { index, block ->
                val bottomSpacing = readerBlockBottomSpacing(
                    current = block,
                    next = content.document.blocks.getOrNull(index + 1),
                )
                ReaderBlockView(
                    block = block,
                    cookieStore = cookieStore,
                    baseUrl = baseUrl,
                    onOpenLink = onOpenLink,
                    hiddenImage = hiddenViewerImage,
                    onImageSourceChanged = { image, launchSource ->
                        if (launchSource == null) {
                            imageLaunchSourceLookup.remove(image)
                        } else {
                            imageLaunchSourceLookup[image] = launchSource
                        }
                    },
                    onImageClick = { clickedImage, launchSource ->
                        val initialIndex = imageIndexLookup[clickedImage]
                            ?: findGalleryStartIndex(
                                galleryImages = readerImages,
                                clickedImage = clickedImage,
                            )
                        val itemLaunchSources = IdentityHashMap<MediaItem, MediaViewerLaunchSource>(
                            imageMediaItems.size,
                        )
                        readerImages.forEachIndexed { mediaIndex, image ->
                            val source = imageLaunchSourceLookup[image] ?: return@forEachIndexed
                            imageMediaItems.getOrNull(mediaIndex)?.let { item ->
                                itemLaunchSources[item] = source
                            }
                        }
                        mediaViewerState.open(
                            items = imageMediaItems,
                            initialIndex = initialIndex,
                            launchSource = launchSource ?: imageLaunchSourceLookup[clickedImage],
                            itemLaunchSources = itemLaunchSources,
                        )
                    },
                    paragraphStyle = paragraphStyle,
                    quoteStyle = quoteStyle,
                    captionStyle = captionStyle,
                    maxImageHeight = readerMaxImageHeight,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = bottomSpacing),
                )
            }
        }

        MediaViewerHost(
            state = mediaViewerState,
            cookieStore = cookieStore,
            baseUrl = baseUrl,
            onVisibleItemChanged = { visibleItem ->
                hiddenViewerImage = visibleItem?.let(mediaItemImageLookup::get)
            },
            onOpenLink = onOpenLink,
            onDismiss = { hiddenViewerImage = null },
        )
    }
}

@Composable
private fun ReaderActionsPopup(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onCopyLink: () -> Unit,
    onShare: () -> Unit,
) {
    NativeDoAnchoredPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        width = 220.dp,
        itemCount = 3,
        contentPadding = PaddingValues(vertical = 4.dp),
    ) { popupProgress ->
        Column {
            NativeDoPopupMenuItem(
                label = stringResource(R.string.app_browser_open_in_browser),
                icon = Icons.Outlined.OpenInBrowser,
                index = 0,
                itemCount = 3,
                popupProgress = popupProgress,
                onClick = onOpenInBrowser,
            )
            NativeDoPopupMenuItem(
                label = stringResource(R.string.app_browser_share),
                icon = Icons.Outlined.Share,
                index = 1,
                itemCount = 3,
                popupProgress = popupProgress,
                onClick = onShare,
            )
            NativeDoPopupMenuDivider()
            NativeDoPopupMenuItem(
                label = stringResource(R.string.app_browser_copy_link),
                icon = Icons.Outlined.ContentCopy,
                index = 2,
                itemCount = 3,
                popupProgress = popupProgress,
                onClick = onCopyLink,
            )
        }
    }
}

@Composable
private fun ReaderBlockView(
    block: AppBrowserReaderBlock,
    cookieStore: CookieStore,
    baseUrl: String,
    onOpenLink: ((String) -> Unit)?,
    hiddenImage: PostBodyImage?,
    onImageSourceChanged: ((PostBodyImage, MediaViewerLaunchSource?) -> Unit)?,
    onImageClick: ((PostBodyImage, MediaViewerLaunchSource?) -> Unit)?,
    paragraphStyle: TextStyle,
    quoteStyle: TextStyle,
    captionStyle: TextStyle,
    maxImageHeight: Dp,
    contentPadding: Dp = appBrowserReaderBlockMetrics().horizontalContentPadding,
    compactTextSpacing: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val typography = remember { appBrowserReaderTypography() }
    when (block) {
        is AppBrowserReaderBlock.Paragraph -> {
            val topPadding = if (compactTextSpacing) 0.dp else 8.dp
            PostTextDocumentView(
                document = block.content,
                textStyle = paragraphStyle,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                onTopicClick = null,
                onUserClick = null,
                onOpenLink = onOpenLink,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(
                        start = contentPadding,
                        end = contentPadding,
                        top = topPadding,
                        bottom = 8.dp,
                    ),
                textColor = NativeDoThemeExtras.colors.textPrimary,
                lineSpacingAdd = 4.dp,
            )
        }

        is AppBrowserReaderBlock.Heading -> {
            val headingTopPadding = if (block.level <= 2) 8.dp else 8.dp
            val headingBottomPadding = 8.dp
            PostTextDocumentView(
                document = block.content,
                textStyle = readerHeadingTextStyle(block.level),
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                onTopicClick = null,
                onUserClick = null,
                onOpenLink = onOpenLink,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(
                        start = contentPadding,
                        end = contentPadding,
                        top = headingTopPadding,
                        bottom = headingBottomPadding,
                    ),
                textColor = NativeDoThemeExtras.colors.textPrimary,
                lineSpacingAdd = 4.dp,
            )
        }

        is AppBrowserReaderBlock.Quote -> {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = contentPadding),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                val quoteLineColor = NativeDoThemeExtras.colors.accent.copy(alpha = 0.42f)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            val lineWidth = 2.dp.toPx()
                            val topInset = 6.dp.toPx()
                            val bottomInset = 6.dp.toPx()
                            drawRect(
                                color = quoteLineColor,
                                size = androidx.compose.ui.geometry.Size(
                                    width = lineWidth,
                                    height = max(0f, size.height - topInset - bottomInset),
                                ),
                                topLeft = androidx.compose.ui.geometry.Offset(0f, topInset),
                            )
                        }
                        .padding(start = 14.dp, top = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    block.blocks.forEach { child ->
                        ReaderBlockView(
                            block = child,
                            cookieStore = cookieStore,
                            baseUrl = baseUrl,
                            onOpenLink = onOpenLink,
                            hiddenImage = hiddenImage,
                            onImageSourceChanged = onImageSourceChanged,
                            onImageClick = onImageClick,
                            paragraphStyle = quoteStyle,
                            quoteStyle = quoteStyle,
                            captionStyle = captionStyle,
                            maxImageHeight = maxImageHeight,
                            contentPadding = 0.dp,
                            compactTextSpacing = true,
                        )
                    }
                }
            }
        }

        is AppBrowserReaderBlock.PullQuote -> {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(
                        start = contentPadding,
                        end = contentPadding,
                        top = 8.dp,
                        bottom = 8.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PostTextDocumentView(
                    document = block.content,
                    textStyle = typography.pullQuote,
                    cookieStore = cookieStore,
                    baseUrl = baseUrl,
                    onTopicClick = null,
                    onUserClick = null,
                    onOpenLink = onOpenLink,
                    modifier = Modifier.fillMaxWidth(),
                    textColor = NativeDoThemeExtras.colors.textPrimary,
                    lineSpacingAdd = 0.dp,
                )
                block.attribution
                    ?.takeIf { attribution ->
                        attribution.text.isNotBlank() || attribution.entities.isNotEmpty()
                    }
                    ?.let { attribution ->
                        PostTextDocumentView(
                            document = attribution,
                            textStyle = captionStyle,
                            cookieStore = cookieStore,
                            baseUrl = baseUrl,
                            onTopicClick = null,
                            onUserClick = null,
                            onOpenLink = onOpenLink,
                            modifier = Modifier.fillMaxWidth(),
                            textColor = NativeDoThemeExtras.colors.textSecondary,
                            lineSpacingAdd = 4.dp,
                        )
                    }
            }
        }

        is AppBrowserReaderBlock.Details -> {
            ReaderDetailsBlockView(
                block = block,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                onOpenLink = onOpenLink,
                hiddenImage = hiddenImage,
                onImageSourceChanged = onImageSourceChanged,
                onImageClick = onImageClick,
                paragraphStyle = paragraphStyle,
                quoteStyle = quoteStyle,
                captionStyle = captionStyle,
                maxImageHeight = maxImageHeight,
                contentPadding = contentPadding,
                modifier = modifier,
            )
        }

        is AppBrowserReaderBlock.ListBlock -> {
            val markerWidth = readerListMarkerWidth(
                ordered = block.ordered,
                itemCount = block.items.size,
            )
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = contentPadding),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                block.items.forEachIndexed { index, item ->
                    ReaderListItemView(
                        index = index,
                        item = item,
                        ordered = block.ordered,
                        cookieStore = cookieStore,
                        baseUrl = baseUrl,
                        onOpenLink = onOpenLink,
                        hiddenImage = hiddenImage,
                        onImageSourceChanged = onImageSourceChanged,
                        onImageClick = onImageClick,
                        paragraphStyle = paragraphStyle,
                        quoteStyle = quoteStyle,
                        captionStyle = captionStyle,
                        maxImageHeight = maxImageHeight,
                        markerWidth = markerWidth,
                        topPadding = if (index == 0 && contentPadding > 0.dp) 10.dp else 0.dp,
                        bottomPadding = 8.dp + if (index == block.items.lastIndex) 8.dp else 0.dp,
                    )
                }
            }
        }

        is AppBrowserReaderBlock.CodeBlock -> {
            ReaderCodeBlockView(
                block = block,
                modifier = modifier,
            )
        }

        is AppBrowserReaderBlock.Image -> {
            ReaderImageBlock(
                image = block.image,
                caption = block.caption,
                credit = block.credit,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                onOpenLink = onOpenLink,
                hiddenImage = hiddenImage,
                onImageSourceChanged = onImageSourceChanged,
                onImageClick = onImageClick,
                captionStyle = captionStyle,
                maxHeight = maxImageHeight,
                contentPadding = contentPadding,
                modifier = modifier.padding(vertical = 8.dp),
            )
        }

        is AppBrowserReaderBlock.Table -> {
            ReaderTableBlockView(
                block = block,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                onOpenLink = onOpenLink,
                captionStyle = captionStyle,
                contentPadding = contentPadding,
                modifier = modifier.padding(vertical = 8.dp),
            )
        }

        AppBrowserReaderBlock.Divider -> {
            BoxWithConstraints(
                modifier = modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .padding(vertical = 8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width((maxWidth / 3f).coerceAtLeast(88.dp))
                        .height(2.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
                        .align(androidx.compose.ui.Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun ReaderDetailsBlockView(
    block: AppBrowserReaderBlock.Details,
    cookieStore: CookieStore,
    baseUrl: String,
    onOpenLink: ((String) -> Unit)?,
    hiddenImage: PostBodyImage?,
    onImageSourceChanged: ((PostBodyImage, MediaViewerLaunchSource?) -> Unit)?,
    onImageClick: ((PostBodyImage, MediaViewerLaunchSource?) -> Unit)?,
    paragraphStyle: TextStyle,
    quoteStyle: TextStyle,
    captionStyle: TextStyle,
    maxImageHeight: Dp,
    contentPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val headerInteractionSource = remember { MutableInteractionSource() }
    var expanded by rememberSaveable(
        block.summary,
        block.children,
        block.expandedByDefault,
    ) {
        mutableStateOf(block.expandedByDefault)
    }

    Column(
        modifier = modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = contentPadding,
                    end = contentPadding,
                    top = 9.dp,
                    bottom = 9.dp,
                )
                .clickable(
                    interactionSource = headerInteractionSource,
                    indication = null,
                ) {
                    expanded = !expanded
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (expanded) "▾" else "▸",
                style = paragraphStyle,
                color = colors.textSecondary,
                modifier = Modifier.width(18.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
            PostTextDocumentView(
                document = block.summary,
                textStyle = paragraphStyle,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                onTopicClick = null,
                onUserClick = null,
                onOpenLink = onOpenLink,
                modifier = Modifier.weight(1f),
                textColor = colors.textPrimary,
                linkColor = colors.accent,
                onClick = { expanded = !expanded },
                lineSpacingAdd = 4.dp,
            )
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f),
        )

        if (expanded && block.children.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                block.children.forEachIndexed { index, child ->
                    ReaderBlockView(
                        block = child,
                        cookieStore = cookieStore,
                        baseUrl = baseUrl,
                        onOpenLink = onOpenLink,
                        hiddenImage = hiddenImage,
                        onImageSourceChanged = onImageSourceChanged,
                        onImageClick = onImageClick,
                        paragraphStyle = paragraphStyle,
                        quoteStyle = quoteStyle,
                        captionStyle = captionStyle,
                        maxImageHeight = maxImageHeight,
                        contentPadding = contentPadding,
                        compactTextSpacing = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = if (index == 0) 0.dp else 4.dp,
                                bottom = 6.dp,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderTableBlockView(
    block: AppBrowserReaderBlock.Table,
    cookieStore: CookieStore,
    baseUrl: String,
    onOpenLink: ((String) -> Unit)?,
    captionStyle: TextStyle,
    contentPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val horizontalScrollState = rememberScrollState()
    val tableLayout = remember(block.rows) {
        resolveReaderTableLayout(block.rows)
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        block.title
            ?.takeIf { title -> title.text.isNotBlank() || title.entities.isNotEmpty() }
            ?.let { title ->
                PostTextDocumentView(
                    document = title,
                    textStyle = captionStyle.copy(
                        textAlign = TextAlign.Center,
                    ),
                    cookieStore = cookieStore,
                    baseUrl = baseUrl,
                    onTopicClick = null,
                    onUserClick = null,
                    onOpenLink = onOpenLink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = contentPadding),
                    textColor = colors.textSecondary,
                    lineSpacingAdd = 4.dp,
                )
            }

        if (tableLayout.placements.isNotEmpty()) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
            ) {
                val availableWidth = maxWidth.coerceAtLeast(220.dp)
                val columnWidths = remember(tableLayout, availableWidth) {
                    resolveReaderTableColumnWidths(
                        tableLayout = tableLayout,
                        availableWidth = availableWidth,
                    )
                }
                val tableWidth = columnWidths.fold(0.dp) { total, width ->
                    total + width
                }.coerceAtLeast(availableWidth)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(horizontalScrollState),
                ) {
                    Row {
                        Spacer(modifier = Modifier.width(contentPadding))
                        ReaderTableGrid(
                            tableLayout = tableLayout,
                            striped = block.striped,
                            bordered = block.bordered,
                            cookieStore = cookieStore,
                            baseUrl = baseUrl,
                            onOpenLink = onOpenLink,
                            columnWidths = columnWidths,
                            modifier = Modifier.width(tableWidth),
                        )
                        Spacer(modifier = Modifier.width(contentPadding))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderTableGrid(
    tableLayout: ReaderTableLayout,
    striped: Boolean,
    bordered: Boolean,
    cookieStore: CookieStore,
    baseUrl: String,
    onOpenLink: ((String) -> Unit)?,
    columnWidths: List<Dp>,
    modifier: Modifier = Modifier,
) {
    val hasHeaderRow = remember(tableLayout.placements) {
        tableLayout.placements
            .filter { placement -> placement.row == 0 }
            .all { placement -> placement.cell.isHeader }
    }
    val columnWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        IntArray(columnWidths.size) { index ->
            columnWidths[index].roundToPx().coerceAtLeast(1)
        }
    }
    SubcomposeLayout(
        modifier = modifier,
    ) { constraints ->
        if (tableLayout.placements.isEmpty() || tableLayout.rowCount <= 0 || tableLayout.columnCount <= 0) {
            return@SubcomposeLayout layout(width = constraints.minWidth, height = 0) {}
        }

        val probePlaceables = subcompose(ReaderTableGridSlot.Probe) {
            tableLayout.placements.forEach { placement ->
                ReaderTableCellView(
                    cell = placement.cell,
                    rowIndex = placement.row,
                    striped = striped,
                    hasHeaderRow = hasHeaderRow,
                    bordered = bordered,
                    drawTopBorder = placement.row == 0,
                    drawLeftBorder = placement.column == 0,
                    cookieStore = cookieStore,
                    baseUrl = baseUrl,
                    onOpenLink = onOpenLink,
                )
            }
        }.mapIndexed { index, measurable ->
            val placement = tableLayout.placements[index]
            val cellWidth = readerTableSpanWidthPx(
                columnWidthsPx = columnWidthPx,
                startColumn = placement.column,
                span = placement.columnSpan,
            )
            placement to measurable.measure(
                Constraints(
                    minWidth = cellWidth,
                    maxWidth = cellWidth,
                ),
            )
        }

        val rowHeights = IntArray(tableLayout.rowCount)
        probePlaceables.forEach { (placement, placeable) ->
            if (placement.rowSpan == 1) {
                rowHeights[placement.row] = max(rowHeights[placement.row], placeable.height)
            }
        }
        probePlaceables
            .sortedBy { (placement, _) -> placement.rowSpan }
            .forEach { (placement, placeable) ->
                val lastRowExclusive = (placement.row + placement.rowSpan)
                    .coerceAtMost(tableLayout.rowCount)
                val currentSpanHeight = (placement.row until lastRowExclusive)
                    .sumOf { rowHeights[it] }
                if (currentSpanHeight < placeable.height) {
                    val deficit = placeable.height - currentSpanHeight
                    val span = (lastRowExclusive - placement.row).coerceAtLeast(1)
                    val baseExtra = deficit / span
                    val remainder = deficit % span
                    for (rowIndex in placement.row until lastRowExclusive) {
                        rowHeights[rowIndex] += baseExtra + if (rowIndex - placement.row < remainder) 1 else 0
                    }
                }
            }
        rowHeights.indices
            .filter { index -> rowHeights[index] == 0 }
            .forEach { index ->
                rowHeights[index] = probePlaceables
                    .firstOrNull { (placement, _) -> placement.row == index }
                    ?.second
                    ?.height
                    ?.coerceAtLeast(1)
                    ?: 1
            }

        val rowOffsets = IntArray(tableLayout.rowCount)
        for (rowIndex in 1 until tableLayout.rowCount) {
            rowOffsets[rowIndex] = rowOffsets[rowIndex - 1] + rowHeights[rowIndex - 1]
        }
        val tableHeight = rowHeights.sum().coerceAtLeast(1)
        val tableWidth = columnWidthPx.sum()
            .coerceAtLeast(1)
            .coerceIn(constraints.minWidth, constraints.maxWidth)

        val finalPlaceables = subcompose(ReaderTableGridSlot.Final) {
            tableLayout.placements.forEach { placement ->
                ReaderTableCellView(
                    cell = placement.cell,
                    rowIndex = placement.row,
                    striped = striped,
                    hasHeaderRow = hasHeaderRow,
                    bordered = bordered,
                    drawTopBorder = placement.row == 0,
                    drawLeftBorder = placement.column == 0,
                    cookieStore = cookieStore,
                    baseUrl = baseUrl,
                    onOpenLink = onOpenLink,
                )
            }
        }.mapIndexed { index, measurable ->
            val placement = tableLayout.placements[index]
            val cellWidth = readerTableSpanWidthPx(
                columnWidthsPx = columnWidthPx,
                startColumn = placement.column,
                span = placement.columnSpan,
            )
            val cellHeight = (placement.row until (placement.row + placement.rowSpan).coerceAtMost(tableLayout.rowCount))
                .sumOf { rowHeights[it] }
                .coerceAtLeast(1)
            measurable.measure(
                Constraints(
                    minWidth = cellWidth,
                    maxWidth = cellWidth,
                    minHeight = cellHeight,
                    maxHeight = cellHeight,
                ),
            )
        }

        layout(width = tableWidth, height = tableHeight) {
            finalPlaceables.forEachIndexed { index, placeable ->
                val placement = tableLayout.placements[index]
                placeable.placeRelative(
                    x = readerTableColumnOffsetPx(
                        columnWidthsPx = columnWidthPx,
                        column = placement.column,
                    ),
                    y = rowOffsets[placement.row],
                )
            }
        }
    }
}

private enum class ReaderTableGridSlot {
    Probe,
    Final,
}

private fun resolveReaderTableColumnWidths(
    tableLayout: ReaderTableLayout,
    availableWidth: Dp,
): List<Dp> {
    if (tableLayout.columnCount <= 0) {
        return emptyList()
    }
    val baseWidth = (availableWidth / tableLayout.columnCount)
        .coerceAtLeast(88.dp)
    val widths = MutableList(tableLayout.columnCount) { baseWidth }
    tableLayout.placements.forEach { placement ->
        val preferredWidth = readerTableCellPreferredWidth(placement.cell)
        val perColumnWidth = (preferredWidth / placement.columnSpan.coerceAtLeast(1))
            .coerceAtLeast(baseWidth)
        val endColumn = (placement.column + placement.columnSpan).coerceAtMost(widths.size)
        for (column in placement.column until endColumn) {
            widths[column] = maxOf(widths[column], perColumnWidth)
        }
    }
    return widths
}

private fun readerTableCellPreferredWidth(
    cell: AppBrowserReaderTableCell,
): Dp {
    val text = cell.content.text.trim()
    if (text.isBlank()) {
        return 88.dp
    }
    val longestLine = text.lineSequence()
        .map { line -> line.trim().length }
        .maxOrNull()
        ?: text.length
    val longestToken = text
        .split(Regex("""\s+"""))
        .map(String::length)
        .maxOrNull()
        ?: text.length
    val preferredChars = when {
        text.contains("://") -> minOf(maxOf(longestToken, 28), 44)
        else -> minOf(maxOf(longestToken, longestLine.coerceAtMost(26), 10), 30)
    }
    return (preferredChars * 8 + 28).dp
}

private fun readerTableSpanWidthPx(
    columnWidthsPx: IntArray,
    startColumn: Int,
    span: Int,
): Int {
    val start = startColumn.coerceIn(0, columnWidthsPx.size)
    val end = (start + span).coerceIn(start, columnWidthsPx.size)
    var total = 0
    for (index in start until end) {
        total += columnWidthsPx[index]
    }
    return total.coerceAtLeast(1)
}

private fun readerTableColumnOffsetPx(
    columnWidthsPx: IntArray,
    column: Int,
): Int {
    var offset = 0
    for (index in 0 until column.coerceIn(0, columnWidthsPx.size)) {
        offset += columnWidthsPx[index]
    }
    return offset
}

@Composable
private fun ReaderTableCellView(
    cell: AppBrowserReaderTableCell,
    rowIndex: Int,
    striped: Boolean,
    hasHeaderRow: Boolean,
    bordered: Boolean,
    drawTopBorder: Boolean,
    drawLeftBorder: Boolean,
    cookieStore: CookieStore,
    baseUrl: String,
    onOpenLink: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val typography = appBrowserReaderTypography()
    val textAlign = when (cell.alignment) {
        AppBrowserReaderTableCellAlignment.Start -> TextAlign.Start
        AppBrowserReaderTableCellAlignment.Center -> TextAlign.Center
        AppBrowserReaderTableCellAlignment.End -> TextAlign.End
    }
    val backgroundColor = when {
        cell.isHeader -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        striped && rowIndex % 2 == 1 -> colors.backgroundBase.copy(alpha = 0.26f)
        hasHeaderRow && rowIndex == 0 -> colors.backgroundBase.copy(alpha = 0.18f)
        else -> colors.backgroundElevated.copy(alpha = 0.96f)
    }
    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(
        alpha = if (bordered) 0.38f else 0f,
    )
    PostTextDocumentView(
        document = cell.content,
        textStyle = typography.table.copy(
            fontWeight = if (cell.isHeader) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = textAlign,
        ),
        cookieStore = cookieStore,
        baseUrl = baseUrl,
        onTopicClick = null,
        onUserClick = null,
        onOpenLink = onOpenLink,
        modifier = modifier
            .background(backgroundColor)
            .drawBehind {
                if (!bordered) {
                    return@drawBehind
                }
                val lineWidth = 1.dp.toPx()
                val half = lineWidth / 2f
                if (drawTopBorder) {
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(0f, half),
                        end = androidx.compose.ui.geometry.Offset(size.width, half),
                        strokeWidth = lineWidth,
                    )
                }
                if (drawLeftBorder) {
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(half, 0f),
                        end = androidx.compose.ui.geometry.Offset(half, size.height),
                        strokeWidth = lineWidth,
                    )
                }
                drawLine(
                    color = lineColor,
                    start = androidx.compose.ui.geometry.Offset(size.width - half, 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width - half, size.height),
                    strokeWidth = lineWidth,
                )
                drawLine(
                    color = lineColor,
                    start = androidx.compose.ui.geometry.Offset(0f, size.height - half),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height - half),
                    strokeWidth = lineWidth,
                )
            }
            .padding(horizontal = 8.dp, vertical = 7.dp),
        textColor = colors.textPrimary,
        linkColor = colors.accent,
        lineSpacingAdd = 4.dp,
    )
}

@Composable
private fun ReaderListItemView(
    index: Int,
    item: AppBrowserReaderListItem,
    ordered: Boolean,
    cookieStore: CookieStore,
    baseUrl: String,
    onOpenLink: ((String) -> Unit)?,
    hiddenImage: PostBodyImage?,
    onImageSourceChanged: ((PostBodyImage, MediaViewerLaunchSource?) -> Unit)?,
    onImageClick: ((PostBodyImage, MediaViewerLaunchSource?) -> Unit)?,
    paragraphStyle: TextStyle,
    quoteStyle: TextStyle,
    captionStyle: TextStyle,
    maxImageHeight: Dp,
    markerWidth: Dp,
    topPadding: Dp,
    bottomPadding: Dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = bottomPadding),
        verticalAlignment = Alignment.Top,
    ) {
        val markerDocument = remember(index, ordered) {
            TextDocument(
                text = if (ordered) "${index + 1}." else "\u2022",
                entities = emptyList(),
            )
        }
        PostTextDocumentView(
            document = markerDocument,
            textStyle = paragraphStyle.copy(
                textAlign = TextAlign.End,
            ),
            cookieStore = cookieStore,
            baseUrl = baseUrl,
            onTopicClick = null,
            onUserClick = null,
            modifier = Modifier.width(markerWidth),
            textColor = NativeDoThemeExtras.colors.textPrimary,
            lineSpacingAdd = 4.dp,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item.blocks.forEach { block ->
                ReaderBlockView(
                    block = block,
                    cookieStore = cookieStore,
                    baseUrl = baseUrl,
                    onOpenLink = onOpenLink,
                    hiddenImage = hiddenImage,
                    onImageSourceChanged = onImageSourceChanged,
                    onImageClick = onImageClick,
                    paragraphStyle = paragraphStyle,
                    quoteStyle = quoteStyle,
                    captionStyle = captionStyle,
                    maxImageHeight = maxImageHeight,
                    contentPadding = 0.dp,
                    compactTextSpacing = true,
                )
            }
        }
    }
}

private fun collectReaderImages(
    blocks: List<AppBrowserReaderBlock>,
    standaloneHeroImage: PostBodyImage? = null,
): List<PostBodyImage> {
    return buildList {
        standaloneHeroImage?.let(::add)
        blocks.forEach { block ->
            addReaderBlockImages(block)
        }
    }
}

@Composable
private fun ReaderCodeBlockView(
    block: AppBrowserReaderBlock.CodeBlock,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val typography = appBrowserReaderTypography()
    val horizontalScrollState = rememberScrollState()
    val backgroundColor = colors.textPrimary.copy(alpha = 0.08f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val inset = 8.dp.toPx()
                drawRect(
                    color = backgroundColor,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, inset),
                    size = androidx.compose.ui.geometry.Size(
                        width = size.width,
                        height = (size.height - inset * 2f).coerceAtLeast(0f),
                    ),
                )
            }
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = block.code,
            modifier = Modifier
                .horizontalScroll(horizontalScrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            style = typography.preformatted.copy(
                fontFamily = FontFamily.Monospace,
            ),
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun ReaderImageBlock(
    image: PostBodyImage,
    caption: TextDocument?,
    credit: TextDocument?,
    cookieStore: CookieStore,
    baseUrl: String,
    onOpenLink: ((String) -> Unit)?,
    hiddenImage: PostBodyImage?,
    onImageSourceChanged: ((PostBodyImage, MediaViewerLaunchSource?) -> Unit)?,
    onImageClick: ((PostBodyImage, MediaViewerLaunchSource?) -> Unit)?,
    captionStyle: TextStyle,
    maxHeight: Dp,
    contentPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val hasCaption = caption?.let { document ->
        document.text.isNotBlank() || document.entities.isNotEmpty()
    } == true
    val hasCredit = credit?.let { document ->
        document.text.isNotBlank() || document.entities.isNotEmpty()
    } == true
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        PostImageView(
            image = image,
            cookieStore = cookieStore,
            baseUrl = baseUrl,
            modifier = Modifier.fillMaxWidth(),
            maxHeight = maxHeight,
            imageAlignment = Alignment.Center,
            clipRoundedCorners = false,
            isHidden = image === hiddenImage,
            onLaunchSourceChanged = onImageSourceChanged?.let { callback ->
                { launchSource -> callback(image, launchSource) }
            },
            onClick = onImageClick?.let { callback ->
                { launchSource -> callback(image, launchSource) }
            },
        )
        if (hasCaption || hasCredit) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = contentPadding,
                        end = contentPadding,
                        top = 8.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                caption
                    ?.takeIf { document -> document.text.isNotBlank() || document.entities.isNotEmpty() }
                    ?.let { document ->
                        PostTextDocumentView(
                            document = document,
                            textStyle = captionStyle,
                            cookieStore = cookieStore,
                            baseUrl = baseUrl,
                            onTopicClick = null,
                            onUserClick = null,
                            onOpenLink = onOpenLink,
                            modifier = Modifier.fillMaxWidth(),
                            textColor = NativeDoThemeExtras.colors.textSecondary.copy(alpha = 0.92f),
                            lineSpacingAdd = 4.dp,
                        )
                    }
                credit
                    ?.takeIf { document -> document.text.isNotBlank() || document.entities.isNotEmpty() }
                    ?.let { document ->
                        PostTextDocumentView(
                            document = document,
                            textStyle = appBrowserReaderTypography().credit,
                            cookieStore = cookieStore,
                            baseUrl = baseUrl,
                            onTopicClick = null,
                            onUserClick = null,
                            onOpenLink = onOpenLink,
                            modifier = Modifier.fillMaxWidth(),
                            textColor = NativeDoThemeExtras.colors.textSecondary.copy(alpha = 0.78f),
                            lineSpacingAdd = 4.dp,
                        )
                    }
            }
        }
    }
}

private fun MutableList<PostBodyImage>.addReaderBlockImages(
    block: AppBrowserReaderBlock,
) {
    when (block) {
        is AppBrowserReaderBlock.Paragraph -> Unit
        is AppBrowserReaderBlock.Heading -> Unit
        is AppBrowserReaderBlock.CodeBlock -> Unit
        AppBrowserReaderBlock.Divider -> Unit
        is AppBrowserReaderBlock.PullQuote -> Unit
        is AppBrowserReaderBlock.Table -> Unit
        is AppBrowserReaderBlock.Image -> add(block.image)
        is AppBrowserReaderBlock.Details -> block.children.forEach(::addReaderBlockImages)
        is AppBrowserReaderBlock.Quote -> block.blocks.forEach(::addReaderBlockImages)
        is AppBrowserReaderBlock.ListBlock -> block.items.forEach { item ->
            item.blocks.forEach(::addReaderBlockImages)
        }
    }
}

@Composable
private fun readerHeadingTextStyle(
    level: Int,
): TextStyle {
    val typography = appBrowserReaderTypography()
    return when (level) {
        1 -> typography.header
        2 -> typography.header
        3 -> typography.subheader
        else -> typography.subheader
    }
}

internal data class AppBrowserReaderTypography(
    val title: TextStyle,
    val subtitle: TextStyle,
    val meta: TextStyle,
    val header: TextStyle,
    val subheader: TextStyle,
    val paragraph: TextStyle,
    val quote: TextStyle,
    val pullQuote: TextStyle,
    val preformatted: TextStyle,
    val caption: TextStyle,
    val credit: TextStyle,
    val table: TextStyle,
)

internal fun appBrowserReaderTypography(): AppBrowserReaderTypography {
    return AppBrowserReaderTypography(
        title = TextStyle(
            fontSize = 23.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        subtitle = TextStyle(
            fontSize = 20.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Normal,
        ),
        meta = TextStyle(
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Normal,
        ),
        header = TextStyle(
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        subheader = TextStyle(
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        paragraph = TextStyle(
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Normal,
        ),
        quote = TextStyle(
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Normal,
        ),
        pullQuote = TextStyle(
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
        ),
        preformatted = TextStyle(
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Normal,
        ),
        caption = TextStyle(
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Normal,
        ),
        credit = TextStyle(
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Normal,
        ),
        table = TextStyle(
            fontSize = 15.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Normal,
        ),
    )
}

internal data class AppBrowserReaderBlockMetrics(
    val horizontalContentPadding: Dp,
    val headerTopSpacing: Dp,
    val headerBottomSpacing: Dp,
) {
    fun blockSpacing(
        current: AppBrowserReaderBlock,
        next: AppBrowserReaderBlock? = null,
    ): Dp {
        return when (current) {
            is AppBrowserReaderBlock.Divider -> 0.dp
            is AppBrowserReaderBlock.Image -> 0.dp
            is AppBrowserReaderBlock.CodeBlock -> 0.dp
            is AppBrowserReaderBlock.PullQuote -> 0.dp
            is AppBrowserReaderBlock.Table -> 0.dp
            is AppBrowserReaderBlock.Details -> 0.dp
            is AppBrowserReaderBlock.Quote -> 0.dp
            is AppBrowserReaderBlock.Heading -> 0.dp
            is AppBrowserReaderBlock.ListBlock -> 0.dp
            is AppBrowserReaderBlock.Paragraph -> 0.dp
        }
    }
}

internal fun readerListMarkerWidth(
    ordered: Boolean,
    itemCount: Int,
): Dp {
    return when {
        !ordered -> 26.dp
        itemCount >= 100 -> 34.dp
        itemCount >= 10 -> 30.dp
        else -> 26.dp
    }
}

internal data class ReaderTablePlacement(
    val cell: AppBrowserReaderTableCell,
    val row: Int,
    val column: Int,
    val columnSpan: Int,
    val rowSpan: Int,
)

internal data class ReaderTableLayout(
    val placements: List<ReaderTablePlacement>,
    val rowCount: Int,
    val columnCount: Int,
)

internal fun appBrowserReaderBlockMetrics(): AppBrowserReaderBlockMetrics {
    return AppBrowserReaderBlockMetrics(
        horizontalContentPadding = 18.dp,
        headerTopSpacing = 4.dp,
        headerBottomSpacing = 12.dp,
    )
}

internal fun resolveReaderTableLayout(
    rows: List<AppBrowserReaderTableRow>,
): ReaderTableLayout {
    if (rows.isEmpty()) {
        return ReaderTableLayout(
            placements = emptyList(),
            rowCount = 0,
            columnCount = 0,
        )
    }

    val occupiedSlots = hashSetOf<Pair<Int, Int>>()
    val placements = mutableListOf<ReaderTablePlacement>()
    var maxColumnCount = 0

    rows.forEachIndexed { rowIndex, row ->
        var columnIndex = 0
        row.cells.forEach { cell ->
            while ((rowIndex to columnIndex) in occupiedSlots) {
                columnIndex += 1
            }

            val columnSpan = cell.columnSpan.coerceAtLeast(1)
            val rowSpan = cell.rowSpan.coerceAtLeast(1)
            placements += ReaderTablePlacement(
                cell = cell,
                row = rowIndex,
                column = columnIndex,
                columnSpan = columnSpan,
                rowSpan = rowSpan,
            )

            for (spannedRow in rowIndex until rowIndex + rowSpan) {
                for (spannedColumn in columnIndex until columnIndex + columnSpan) {
                    occupiedSlots += spannedRow to spannedColumn
                }
            }

            columnIndex += columnSpan
            maxColumnCount = max(maxColumnCount, columnIndex)
        }
    }

    val rowCount = placements.maxOfOrNull { placement -> placement.row + placement.rowSpan }
        ?.coerceAtLeast(rows.size)
        ?: rows.size
    val columnCount = placements.maxOfOrNull { placement -> placement.column + placement.columnSpan }
        ?.coerceAtLeast(maxColumnCount)
        ?: maxColumnCount
    return ReaderTableLayout(
        placements = placements,
        rowCount = rowCount,
        columnCount = columnCount,
    )
}

internal fun readerImageMaxHeight(
    screenWidthDp: Int,
    screenHeightDp: Int,
): Dp {
    val widthCap = (screenWidthDp.dp - 24.dp).coerceAtLeast(220.dp)
    val heightCap = ((max(screenWidthDp, screenHeightDp) - 56).dp * 0.9f)
        .coerceAtLeast(260.dp)
    return minOf(widthCap, heightCap)
}

private fun readerBlockBottomSpacing(
    current: AppBrowserReaderBlock,
    next: AppBrowserReaderBlock?,
): Dp {
    return appBrowserReaderBlockMetrics().blockSpacing(
        current = current,
        next = next,
    )
}

private val appBrowserReaderMetaDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
private val appBrowserReaderMetaUtcFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")

private fun buildAppBrowserReaderMetaItems(
    byline: String?,
    publishedTime: String?,
    sourceLabel: String?,
    sourceHost: String,
): List<String> {
    val formattedPublishedTime = formatAppBrowserReaderPublishedTime(publishedTime)
    return buildList {
        formattedPublishedTime?.let(::add)
        byline?.takeIf(String::isNotBlank)?.let(::add)
        sourceLabel
            ?.takeIf(String::isNotBlank)
            ?.takeUnless { it == sourceHost }
            ?.let(::add)
        sourceHost.takeIf(String::isNotBlank)?.let(::add)
    }.distinct()
}

private fun formatAppBrowserReaderPublishedTime(
    rawValue: String?,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String? {
    val normalized = rawValue?.trim()?.takeIf(String::isNotBlank) ?: return null
    val instant = runCatching { OffsetDateTime.parse(normalized).toInstant() }
        .recoverCatching {
            LocalDateTime.parse(normalized, appBrowserReaderMetaUtcFormatter)
                .atOffset(ZoneOffset.UTC)
                .toInstant()
        }
        .getOrNull()
        ?: return normalized
    return instant
        .atZone(zoneId)
        .format(appBrowserReaderMetaDateFormatter)
}

private fun captureWindowRegionBitmap(
    rootView: View?,
    boundsInWindow: IntRect?,
    maxWidthPx: Int,
    maxHeightPx: Int,
): Bitmap? {
    val safeRootView = rootView ?: return null
    val rect = boundsInWindow ?: return null
    if (rect.width <= 0 || rect.height <= 0) {
        return null
    }
    val scale = minOf(
        1f,
        maxWidthPx.toFloat() / rect.width.toFloat(),
        maxHeightPx.toFloat() / rect.height.toFloat(),
    )
    val targetWidth = (rect.width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (rect.height * scale).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(
        targetWidth,
        targetHeight,
        Bitmap.Config.ARGB_8888,
    )
    val canvas = Canvas(bitmap)
    canvas.scale(scale, scale)
    canvas.translate(-rect.left.toFloat(), -rect.top.toFloat())
    safeRootView.draw(canvas)
    return bitmap
}

private fun androidx.compose.ui.geometry.Rect.roundToIntRect(): IntRect = IntRect(
    left = left.toInt(),
    top = top.toInt(),
    right = right.toInt(),
    bottom = bottom.toInt(),
)
