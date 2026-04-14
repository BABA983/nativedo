package io.github.baba983.nativedo.core.ui.mediaviewer

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Environment
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.network.addImageRequestHeaders
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.core.ui.BulletinMessage
import io.github.baba983.nativedo.core.ui.BulletinTone
import io.github.baba983.nativedo.core.ui.LocalBulletinHostState
import io.github.baba983.nativedo.core.ui.PostBodyImage
import io.github.baba983.nativedo.core.ui.calculatePostImageSize
import io.github.baba983.nativedo.core.ui.findMediaViewerPreviewBitmap
import io.github.baba983.nativedo.core.ui.isAnimatedImageUrl
import io.github.baba983.nativedo.core.ui.rememberDiscourseBitmap
import io.github.baba983.nativedo.core.ui.resolvePostImageRenderRawUrl
import io.github.baba983.nativedo.core.ui.resolvePostImageUrl
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import java.net.URLConnection
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import kotlin.math.roundToInt

private const val MEDIA_VIEWER_ANIMATED_PAGE_JUMP_DISTANCE = 1
private val MEDIA_VIEWER_PAGE_GAP = 14.dp

internal fun shouldAnimateMediaViewerPageJump(
    currentPage: Int,
    targetPage: Int,
): Boolean = kotlin.math.abs(targetPage - currentPage) <= MEDIA_VIEWER_ANIMATED_PAGE_JUMP_DISTANCE

internal data class MediaViewerPagerMotion(
    val currentPage: Int,
    val targetPage: Int? = null,
    val switchProgress: Float = 0f,
)

internal fun updateMediaViewerPagerGestureLock(
    wasLocked: Boolean,
    pressedPointerCount: Int,
): Boolean {
    return when {
        pressedPointerCount > 1 -> true
        pressedPointerCount == 0 -> false
        else -> wasLocked
    }
}

internal fun shouldEnableMediaViewerPagerScroll(
    isTransitionAnimating: Boolean,
    scale: Float,
    dismissOffsetY: Float,
    isPagerGestureLocked: Boolean,
): Boolean {
    return !isTransitionAnimating &&
        scale <= 1f + MEDIA_VIEWER_REST_SCALE_EPSILON &&
        dismissOffsetY == 0f &&
        !isPagerGestureLocked
}

internal fun resolveMediaViewerPagerMotion(
    settledPage: Int,
    currentPage: Int,
    targetPage: Int,
    currentPageOffsetFraction: Float,
    pageCount: Int,
): MediaViewerPagerMotion {
    if (pageCount <= 0) {
        return MediaViewerPagerMotion(currentPage = 0)
    }
    val clampedSettledPage = settledPage.coerceIn(0, pageCount - 1)
    val clampedCurrentPage = currentPage.coerceIn(0, pageCount - 1)
    val clampedTargetPage = targetPage.coerceIn(0, pageCount - 1)
    val offset = kotlin.math.abs(currentPageOffsetFraction).coerceIn(0f, 1f)

    if (clampedTargetPage == clampedSettledPage || offset == 0f) {
        return MediaViewerPagerMotion(currentPage = clampedCurrentPage)
    }
    if (clampedCurrentPage == clampedSettledPage) {
        return MediaViewerPagerMotion(
            currentPage = clampedSettledPage,
            targetPage = clampedTargetPage,
            switchProgress = offset,
        )
    }
    if (clampedCurrentPage == clampedTargetPage) {
        return MediaViewerPagerMotion(
            currentPage = clampedSettledPage,
            targetPage = clampedTargetPage,
            switchProgress = 1f - offset,
        )
    }
    return MediaViewerPagerMotion(currentPage = clampedCurrentPage)
}

internal fun resolveMediaViewerDismissSource(
    request: MediaViewerRequest,
    currentPage: Int,
): MediaViewerLaunchSource? {
    val currentItem = request.items.getOrNull(currentPage)
    return when {
        currentItem != null && request.itemLaunchSources[currentItem] != null -> {
            request.itemLaunchSources[currentItem]
        }

        currentPage == request.initialIndex -> request.launchSource
        else -> null
    }
}

internal fun resolveMediaViewerAspectRatio(
    item: MediaItem,
    loadedBitmapSize: IntSize? = null,
    previewBitmapSize: IntSize? = null,
): Float {
    val width = when (item) {
        is MediaItem.Image -> item.width
        is MediaItem.Video -> item.width
    }
    val height = when (item) {
        is MediaItem.Image -> item.height
        is MediaItem.Video -> item.height
    }
    return if (width != null && height != null && width > 0 && height > 0) {
        width.toFloat() / height.toFloat()
    } else if (loadedBitmapSize != null && loadedBitmapSize.width > 0 && loadedBitmapSize.height > 0) {
        loadedBitmapSize.width.toFloat() / loadedBitmapSize.height.toFloat()
    } else if (previewBitmapSize != null && previewBitmapSize.width > 0 && previewBitmapSize.height > 0) {
        previewBitmapSize.width.toFloat() / previewBitmapSize.height.toFloat()
    } else {
        4f / 3f
    }
}

internal fun buildMediaViewerRequestHeaders(
    resolvedUrl: String,
    cookieStore: CookieStore,
    baseUrl: String,
): Map<String, String> {
    val imageUrl = resolvedUrl.toHttpUrl()
    val requestBuilder = Request.Builder()
        .url(imageUrl)
        .apply { addImageRequestHeaders(baseUrl = baseUrl) }

    val cookieHeader = cookieStore.load(imageUrl)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(separator = "; ") { cookie ->
            "${cookie.name}=${cookie.value}"
        }
    if (!cookieHeader.isNullOrBlank()) {
        requestBuilder.header("Cookie", cookieHeader)
    }

    val headers = requestBuilder.build().headers
    return headers.names().associateWith { name ->
        headers[name].orEmpty()
    }
}

internal fun suggestMediaDownloadFileName(
    resolvedUrl: String,
): String {
    return runCatching {
        resolvedUrl.toHttpUrl().pathSegments.lastOrNull()
            ?.substringBefore("?")
            ?.takeIf { it.isNotBlank() }
    }.getOrNull() ?: "nativedo-media"
}

private fun MediaItem.asViewerImage(): PostBodyImage? = when (this) {
    is MediaItem.Image -> toPostBodyImage().copy(rawUrl = originalUrl ?: rawUrl)
    is MediaItem.Video -> thumbnailUrl?.let { thumb ->
        PostBodyImage(
            rawUrl = thumb,
            width = width,
            height = height,
        )
    }
}

@Composable
private fun rememberMediaViewerContainerSize(
    availableWidth: androidx.compose.ui.unit.Dp,
    availableHeight: androidx.compose.ui.unit.Dp,
): IntSize {
    val density = LocalDensity.current
    return remember(availableWidth, availableHeight, density) {
        with(density) {
            IntSize(
                width = availableWidth.roundToPx(),
                height = availableHeight.roundToPx(),
            )
        }
    }
}

@Composable
private fun rememberMediaViewerContentSize(
    item: MediaItem,
    loadedBitmap: Bitmap?,
    previewBitmap: Bitmap?,
    availableWidth: androidx.compose.ui.unit.Dp,
    availableHeight: androidx.compose.ui.unit.Dp,
): IntSize {
    val density = LocalDensity.current
    val contentSize = remember(item, loadedBitmap, previewBitmap, availableWidth, availableHeight) {
        calculatePostImageSize(
            availableWidth = availableWidth,
            aspectRatio = resolveMediaViewerAspectRatio(
                item = item,
                loadedBitmapSize = loadedBitmap?.let { IntSize(it.width, it.height) },
                previewBitmapSize = previewBitmap?.let { IntSize(it.width, it.height) },
            ),
            maxHeight = availableHeight,
        )
    }
    return remember(contentSize, density) {
        with(density) {
            IntSize(
                width = contentSize.width.roundToPx(),
                height = contentSize.height.roundToPx(),
            )
        }
    }
}

private fun Context.startMediaViewerIntent(
    intent: Intent,
    onFailure: () -> Unit,
) {
    runCatching {
        if (this !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }.onFailure {
        onFailure()
    }
}

private fun enqueueMediaViewerDownload(
    context: Context,
    resolvedUrl: String,
    cookieStore: CookieStore,
    baseUrl: String,
): Boolean {
    return runCatching {
        val downloadManager = context.getSystemService(DownloadManager::class.java)
            ?: return false
        val fileName = suggestMediaDownloadFileName(resolvedUrl)
        val request = DownloadManager.Request(Uri.parse(resolvedUrl))
            .setTitle(fileName)
            .setMimeType(URLConnection.guessContentTypeFromName(fileName))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

        buildMediaViewerRequestHeaders(
            resolvedUrl = resolvedUrl,
            cookieStore = cookieStore,
            baseUrl = baseUrl,
        ).forEach { (name, value) ->
            request.addRequestHeader(name, value)
        }

        downloadManager.enqueue(request)
        true
    }.getOrDefault(false)
}

@Composable
private fun MediaViewerScenePage(
    page: Int,
    request: MediaViewerRequest,
    baseUrl: String,
    cookieStore: CookieStore,
    currentPage: Int,
    transitionPage: Int,
    pageTranslationX: Float,
    slotAlpha: Float,
    slotScale: Float,
    captureContentBounds: Boolean,
    publishCurrentPageContent: Boolean,
    isInteractivePage: Boolean,
    dismissProgress: Float,
    dismissDistancePx: Float,
    gestureState: MediaViewerGestureState,
    contentContainerBoundsOnScreen: IntRect?,
    activeTransitionSource: MediaViewerLaunchSource?,
    enterProgress: Float,
    isEnterAnimating: Boolean,
    isClosing: Boolean,
    isTransitionAnimating: Boolean,
    closeSnapshot: MediaViewerCloseSnapshot?,
    onContentContainerBoundsChange: (IntRect) -> Unit,
    onCurrentPageContentChange: (IntSize, Bitmap?) -> Unit,
    onPageTranslationDelta: (Float) -> Unit,
    onPageTranslationRelease: (Float, Float) -> Unit,
    onSwitchToPrevious: (MediaViewerPageSwitchSpec?) -> Unit,
    onSwitchToNext: (MediaViewerPageSwitchSpec?) -> Unit,
    onDismissRequest: () -> Unit,
    onToggleChrome: () -> Unit,
    onMenuDismiss: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .then(
                if (captureContentBounds && !isClosing) {
                    Modifier.onGloballyPositioned { coordinates ->
                        val position = coordinates.positionOnScreen()
                        onContentContainerBoundsChange(
                            IntRect(
                                left = position.x.roundToInt(),
                                top = position.y.roundToInt(),
                                right = (position.x + coordinates.size.width).roundToInt(),
                                bottom = (position.y + coordinates.size.height).roundToInt(),
                            ),
                        )
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        val item = request.items[page]
        val viewerImage = remember(item) { item.asViewerImage() }
        val previewBitmap = remember(viewerImage) {
            viewerImage?.let(::findMediaViewerPreviewBitmap)
        }
        val resolvedUrl = remember(item, viewerImage, baseUrl) {
            when (item) {
                is MediaItem.Image -> {
                    val rawUrl = viewerImage?.rawUrl ?: item.rawUrl
                    resolvePostImageUrl(baseUrl = baseUrl, rawUrl = rawUrl)
                }
                is MediaItem.Video -> {
                    item.thumbnailUrl?.let { thumb ->
                        resolvePostImageUrl(baseUrl = baseUrl, rawUrl = thumb)
                    }
                }
            }
        }
        val shouldAnimate = remember(item, resolvedUrl) {
            item is MediaItem.Image && resolvedUrl != null && isAnimatedImageUrl(resolvedUrl)
        }
        val bitmap = if (shouldAnimate || resolvedUrl == null) {
            previewBitmap
        } else {
            val loadedBitmap by rememberDiscourseBitmap(
                resolvedUrl = resolvedUrl,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                previewBitmap = previewBitmap,
            )
            loadedBitmap
        }
        val contentSize = rememberMediaViewerContentSize(
            item = item,
            loadedBitmap = bitmap,
            previewBitmap = previewBitmap,
            availableWidth = maxWidth,
            availableHeight = maxHeight,
        )
        val containerSize = rememberMediaViewerContainerSize(
            availableWidth = maxWidth,
            availableHeight = maxHeight,
        )
        val closeSnapshotForPage = closeSnapshot?.takeIf { snapshot ->
            isClosing && snapshot.page == page
        }
        val renderedContentSize = closeSnapshotForPage?.contentSize ?: contentSize

        if (publishCurrentPageContent && !isClosing) {
            SideEffect {
                onCurrentPageContentChange(
                    contentSize,
                    bitmap ?: previewBitmap,
                )
            }
        }

        val activeScale = if (isInteractivePage) gestureState.scale else 1f
        val activeOffset = if (isInteractivePage) gestureState.offset else Offset.Zero
        val activeDismissOffsetY = if (isInteractivePage) gestureState.dismissOffsetY else 0f
        val displayedScale = if (isInteractivePage && !isEnterAnimating) {
            activeScale * mediaViewerDismissedScale(dismissProgress)
        } else {
            1f
        }
        val openTargetBounds = remember(
            captureContentBounds,
            page,
            transitionPage,
            contentContainerBoundsOnScreen,
            renderedContentSize,
        ) {
            val containerBounds = contentContainerBoundsOnScreen
            if (!captureContentBounds || page != transitionPage || containerBounds == null) {
                null
            } else {
                calculateMediaViewerTargetRectInContainer(
                    containerBounds = containerBounds,
                    contentSize = renderedContentSize,
                )
            }
        }
        val openTransform = remember(
            captureContentBounds,
            page,
            transitionPage,
            activeTransitionSource,
            openTargetBounds,
            enterProgress,
            isClosing,
        ) {
            if (
                isClosing ||
                !captureContentBounds ||
                page != transitionPage ||
                activeTransitionSource == null ||
                openTargetBounds == null
            ) {
                null
            } else {
                calculateMediaViewerEnterTransform(
                    sourceBounds = activeTransitionSource.fullBoundsOnScreen,
                    targetBounds = openTargetBounds,
                    progress = enterProgress,
                )
            }
        }
        val openClipInsets = remember(
            captureContentBounds,
            page,
            transitionPage,
            activeTransitionSource,
            openTargetBounds,
            enterProgress,
            isClosing,
        ) {
            if (
                isClosing ||
                !captureContentBounds ||
                page != transitionPage ||
                activeTransitionSource == null ||
                openTargetBounds == null
            ) {
                null
            } else {
                lerpMediaViewerClipInsets(
                    start = calculateMediaViewerSourceClipInsets(
                        sourceVisibleBounds = activeTransitionSource.boundsOnScreen,
                        sourceFullBounds = activeTransitionSource.fullBoundsOnScreen,
                        targetBounds = openTargetBounds,
                    ),
                    end = MediaViewerClipInsets(),
                    progress = enterProgress,
                )
            }
        }
        val openCornerRadiusPx = remember(
            captureContentBounds,
            page,
            transitionPage,
            activeTransitionSource,
            openTargetBounds,
            enterProgress,
            isClosing,
        ) {
            if (
                isClosing ||
                !captureContentBounds ||
                page != transitionPage ||
                activeTransitionSource == null ||
                openTargetBounds == null
            ) {
                null
            } else {
                lerpFloat(
                    calculateMediaViewerSourceCornerRadius(
                        sourceCornerRadiusPx = activeTransitionSource.cornerRadiusPx,
                        sourceFullBounds = activeTransitionSource.fullBoundsOnScreen,
                        targetBounds = openTargetBounds,
                    ),
                    0f,
                    enterProgress,
                )
            }
        }

        MediaViewerPage(
            item = item,
            gestureState = gestureState,
            containerSize = containerSize,
            contentSize = renderedContentSize,
            bitmap = bitmap,
            resolvedUrl = resolvedUrl,
            shouldAnimate = shouldAnimate,
            clipInsets = openClipInsets,
            cornerRadiusPx = openCornerRadiusPx,
            enabled = isInteractivePage && !isTransitionAnimating,
            currentPage = currentPage,
            canSwitchToPrevious = isInteractivePage && currentPage > 0,
            canSwitchToNext = isInteractivePage && currentPage < request.items.lastIndex,
            pageTranslationX = if (isInteractivePage) pageTranslationX else 0f,
            dismissDistancePx = dismissDistancePx,
            onPageTranslationDelta = onPageTranslationDelta,
            onPageTranslationRelease = onPageTranslationRelease,
            onSwitchToPrevious = onSwitchToPrevious,
            onSwitchToNext = onSwitchToNext,
            onDismissRequest = onDismissRequest,
            onToggleChrome = onToggleChrome,
            onMenuDismiss = onMenuDismiss,
            modifier = Modifier.graphicsLayer {
                alpha = slotAlpha
                scaleX = displayedScale * slotScale * (openTransform?.scaleX ?: 1f)
                scaleY = displayedScale * slotScale * (openTransform?.scaleY ?: 1f)
                translationX = pageTranslationX + activeOffset.x + (openTransform?.translationX ?: 0f)
                translationY = activeOffset.y +
                    activeDismissOffsetY +
                    (openTransform?.translationY ?: 0f)
            },
        )
    }
}

@Composable
internal fun MediaViewerDialog(
    state: MediaViewerState,
    request: MediaViewerRequest,
    cookieStore: CookieStore,
    baseUrl: String,
    onVisibleItemChanged: (MediaItem?) -> Unit = {},
    onOpenLink: ((String) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val bulletinHostState = LocalBulletinHostState.current
    val colors = NativeDoThemeExtras.colors
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val initialPage = request.initialIndex.coerceIn(0, request.items.lastIndex)
    val pagerState = rememberPagerState(initialPage = initialPage) {
        request.items.size
    }
    val enterProgress = remember(request.launchSource, initialPage) {
        Animatable(if (request.launchSource == null) 1f else 0f)
    }
    val closeScrimAlpha = remember(request.launchSource, initialPage) {
        Animatable(MEDIA_VIEWER_MAX_SCRIM_ALPHA)
    }
    val gestureState = remember { MediaViewerGestureState() }
    val pageMotionState = remember(initialPage) { MediaViewerPageMotionState(initialPage) }
    var viewerWindowSize by remember { mutableStateOf(IntSize.Zero) }
    var currentPageContentSize by remember { mutableStateOf(IntSize.Zero) }
    var currentPageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var contentContainerBoundsOnScreen by remember { mutableStateOf<IntRect?>(null) }
    var isClosing by remember(request.items, initialPage) { mutableStateOf(false) }
    var closeSource by remember(request.items, initialPage) {
        mutableStateOf<MediaViewerLaunchSource?>(null)
    }
    var closeSnapshot by remember(request.items, initialPage) {
        mutableStateOf<MediaViewerCloseSnapshot?>(null)
    }
    var isPagerGestureLocked by remember { mutableStateOf(false) }
    var hasPlayedEnterAnimation by remember(request.launchSource, initialPage) {
        mutableStateOf(request.launchSource == null)
    }
    val dismissDistancePx = remember(density, viewerWindowSize) {
        val screenBased = viewerWindowSize.height / 6f
        val minPx = with(density) { MEDIA_VIEWER_DISMISS_DISTANCE.toPx() }
        if (screenBased > 0f) screenBased else minPx
    }
    val chromeTranslationDistancePx = remember(density) {
        with(density) { 24.dp.toPx() }
    }
    val dismissProgress = dismissProgressFraction(gestureState.dismissOffsetY, dismissDistancePx)
    val chromeVisibilityProgress by animateFloatAsState(
        targetValue = if (state.chromeState.isVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = 180,
            easing = LinearOutSlowInEasing,
        ),
        label = "mediaViewerChromeVisibility",
    )
    val chromeAlpha = chromeVisibilityProgress * enterProgress.value * (1f - dismissProgress)
    val topChromeTranslationY = -chromeTranslationDistancePx * (1f - chromeVisibilityProgress.coerceIn(0f, 1f))
    val bottomChromeTranslationY = chromeTranslationDistancePx * (1f - chromeVisibilityProgress.coerceIn(0f, 1f))
    val isEnterAnimating = request.launchSource != null && !hasPlayedEnterAnimation
    val isTransitionAnimating = isEnterAnimating || isClosing
    val activeTransitionSource = if (isClosing) closeSource else request.launchSource
    val currentPage = pagerState.currentPage
    val pageTravelDistance = remember(viewerWindowSize, density) {
        resolveMediaViewerPageTravelDistance(
            containerWidth = viewerWindowSize.width.toFloat(),
            pageGap = with(density) { MEDIA_VIEWER_PAGE_GAP.toPx() },
        )
    }
    val pagerMotion = resolveMediaViewerPagerMotion(
        settledPage = pagerState.settledPage,
        currentPage = pagerState.currentPage,
        targetPage = pagerState.targetPage,
        currentPageOffsetFraction = pagerState.currentPageOffsetFraction,
        pageCount = request.items.size,
    )
    val usePageMotion = pageMotionState.hasActiveMotion
    val displayCurrentPage = if (usePageMotion) {
        pageMotionState.currentPage
    } else {
        pagerMotion.currentPage
    }
    val switchTargetPage = if (usePageMotion) {
        pageMotionState.targetPage
    } else {
        pagerMotion.targetPage
    }
    val switchProgress = if (usePageMotion) {
        resolveMediaViewerSwitchProgress(
            translationX = pageMotionState.translationX,
            travelDistance = pageTravelDistance,
        )
    } else {
        pagerMotion.switchProgress
    }
    val transitionPage = if (isClosing) currentPage else initialPage
    val currentItem = request.items[currentPage]
    val currentVisibleUrl = remember(currentItem, baseUrl) {
        when (currentItem) {
            is MediaItem.Image -> resolvePostImageUrl(
                baseUrl = baseUrl,
                rawUrl = resolvePostImageRenderRawUrl(currentItem.toPostBodyImage()),
            )
            is MediaItem.Video -> resolvePostImageUrl(baseUrl = baseUrl, rawUrl = currentItem.url)
        }
    }

    LaunchedEffect(currentPage) {
        state.setMenuExpanded(false)
        state.showChrome()
        isPagerGestureLocked = false
        currentPageContentSize = IntSize.Zero
        currentPageBitmap = null
        gestureState.reset()
        pageMotionState.settleAt(currentPage)
    }

    LaunchedEffect(
        currentPage,
        request.items,
        request.launchSource,
        contentContainerBoundsOnScreen,
        hasPlayedEnterAnimation,
        isClosing,
    ) {
        if (isClosing) return@LaunchedEffect
        if (
            shouldDelayMediaViewerThumbnailHide(
                hasLaunchSource = request.launchSource != null,
                hasPlayedEnterAnimation = hasPlayedEnterAnimation,
                hasContentContainerBoundsOnScreen = contentContainerBoundsOnScreen != null,
                currentPage = currentPage,
                initialPage = initialPage,
            )
        ) {
            return@LaunchedEffect
        }
        onVisibleItemChanged(request.items.getOrNull(currentPage))
    }

    suspend fun requestDismiss() {
        if (isClosing) return

        state.setMenuExpanded(false)
        val currentDismissProgress = dismissProgressFraction(gestureState.dismissOffsetY, dismissDistancePx)
        val currentOpenTransform = request.launchSource
            ?.takeIf {
                currentPage == initialPage &&
                    currentPageContentSize != IntSize.Zero &&
                    contentContainerBoundsOnScreen != null
            }
            ?.let { launchSource ->
                calculateMediaViewerEnterTransform(
                    sourceBounds = launchSource.fullBoundsOnScreen,
                    targetBounds = calculateMediaViewerTargetRectInContainer(
                        containerBounds = contentContainerBoundsOnScreen!!,
                        contentSize = currentPageContentSize,
                    ),
                    progress = enterProgress.value,
                )
            }
        val currentDisplayedScale = if (isEnterAnimating) {
            1f
        } else {
            gestureState.scale * mediaViewerDismissedScale(currentDismissProgress)
        }
        val currentRenderedScale = resolveMediaViewerRenderedScale(
            displayedScale = currentDisplayedScale,
            openTransform = currentOpenTransform,
        )
        val currentRenderedTranslation = resolveMediaViewerRenderedTranslation(
            activeOffset = gestureState.offset,
            activeDismissOffsetY = gestureState.dismissOffsetY,
            openTransform = currentOpenTransform,
        )
        val currentVisibleScrimAlpha = mediaViewerOpenScrimAlpha(
            enterProgress = enterProgress.value,
            dismissProgress = currentDismissProgress,
        )
        val viewportBoundsOnScreen = viewerWindowSize
            .takeIf { it != IntSize.Zero }
            ?.let { size ->
                IntRect(0, 0, size.width, size.height)
            }
        val dismissSource = resolveMediaViewerDismissSource(
            request = request,
            currentPage = currentPage,
        )?.takeIf { source ->
            viewportBoundsOnScreen == null ||
                shouldAnimateMediaViewerDismissToSource(
                    dismissSource = source,
                    viewportBoundsOnScreen = viewportBoundsOnScreen,
                )
        }
        if (currentPageContentSize == IntSize.Zero || contentContainerBoundsOnScreen == null) {
            isClosing = true
            onVisibleItemChanged(null)
            closeScrimAlpha.snapTo(currentVisibleScrimAlpha)
            animateMediaViewerClose(enterProgress, closeScrimAlpha)
            withFrameNanos { }
            onDismiss()
            return
        }

        closeSource = dismissSource
        closeSnapshot = MediaViewerCloseSnapshot(
            page = currentPage,
            contentSize = currentPageContentSize,
            containerBoundsOnScreen = contentContainerBoundsOnScreen!!,
            bitmap = currentPageBitmap,
            scale = currentRenderedScale,
            translation = currentRenderedTranslation,
        )
        closeScrimAlpha.snapTo(currentVisibleScrimAlpha)
        onVisibleItemChanged(null)
        isClosing = true
        animateMediaViewerClose(enterProgress, closeScrimAlpha)
        withFrameNanos { }
        onDismiss()
    }

    fun launchZoomedPageSwitch(target: Int) {
        if (target !in request.items.indices) return
        val startTranslation = pageMotionState.translationX.takeIf { it != 0f } ?: if (target > currentPage) {
            -pageTravelDistance * 0.35f
        } else {
            pageTravelDistance * 0.35f
        }
        pageMotionState.beginSwitch(
            targetPage = target,
            translationX = startTranslation,
        )
        coroutineScope.launch {
            val anim = Animatable(startTranslation)
            anim.animateTo(
                targetValue = if (target > currentPage) {
                    -pageTravelDistance
                } else {
                    pageTravelDistance
                },
                animationSpec = tween(
                    durationMillis = 180,
                    easing = LinearOutSlowInEasing,
                ),
                ) {
                pageMotionState.updateTranslation(
                    translationX = value,
                    targetPage = target,
                    isSwitching = true,
                )
            }
            gestureState.reset()
            pagerState.scrollToPage(target)
            pageMotionState.finishSwitch()
        }
    }

    LaunchedEffect(
        request.launchSource,
        contentContainerBoundsOnScreen,
        currentPage,
        hasPlayedEnterAnimation,
    ) {
        if (request.launchSource == null || hasPlayedEnterAnimation) {
            if (enterProgress.value != 1f) {
                enterProgress.snapTo(1f)
            }
            return@LaunchedEffect
        }
        if (contentContainerBoundsOnScreen == null || currentPage != initialPage) {
            return@LaunchedEffect
        }

        enterProgress.snapTo(0f)
        enterProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = MEDIA_VIEWER_ENTER_DURATION_MS,
                easing = LinearOutSlowInEasing,
            ),
        )
        hasPlayedEnterAnimation = true
    }

    Dialog(
        onDismissRequest = {
            coroutineScope.launch { requestDismiss() }
        },
        properties = mediaViewerDialogProperties(),
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { window ->
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            }
        }

        val closeProgress = 1f - enterProgress.value
        val scrimAlpha = if (isClosing) {
            closeScrimAlpha.value
        } else {
            mediaViewerOpenScrimAlpha(
                enterProgress = enterProgress.value,
                dismissProgress = dismissProgress,
            )
        }
        val showCloseOverlay = shouldRenderMediaViewerCloseOverlay(
            isClosing = isClosing,
            hasSnapshotBitmap = closeSnapshot?.bitmap != null,
        )
        val liveContentAlpha = if (isClosing) {
            if (showCloseOverlay) 0f else mediaViewerCloseContentAlpha(closeProgress)
        } else {
            1f
        }
        val activeCloseSnapshot = closeSnapshot
        val closeOverlayTargetBounds = remember(activeCloseSnapshot) {
            activeCloseSnapshot?.let { snapshot ->
                calculateMediaViewerTargetRectInContainer(
                    containerBounds = snapshot.containerBoundsOnScreen,
                    contentSize = snapshot.contentSize,
                )
            }
        }
        val closeOverlayTransform = remember(
            activeCloseSnapshot,
            closeSource,
            closeOverlayTargetBounds,
            closeProgress,
        ) {
            val snapshot = activeCloseSnapshot
            val activeCloseSource = closeSource
            if (snapshot == null || activeCloseSource == null || closeOverlayTargetBounds == null) {
                null
            } else {
                calculateMediaViewerExitTransform(
                    sourceBounds = activeCloseSource.fullBoundsOnScreen,
                    targetBounds = closeOverlayTargetBounds,
                    currentScale = snapshot.scale,
                    currentTranslation = snapshot.translation,
                    progress = closeProgress,
                )
            }
        }
        val closeOverlayClipInsets = remember(
            activeCloseSnapshot,
            closeSource,
            closeOverlayTargetBounds,
            closeProgress,
        ) {
            val activeCloseSource = closeSource
            val targetBounds = closeOverlayTargetBounds
            if (activeCloseSource == null || targetBounds == null) {
                null
            } else {
                lerpMediaViewerClipInsets(
                    start = MediaViewerClipInsets(),
                    end = calculateMediaViewerSourceClipInsets(
                        sourceVisibleBounds = activeCloseSource.boundsOnScreen,
                        sourceFullBounds = activeCloseSource.fullBoundsOnScreen,
                        targetBounds = targetBounds,
                    ),
                    progress = closeProgress,
                )
            }
        }
        val closeOverlayCornerRadiusPx = remember(
            activeCloseSnapshot,
            closeSource,
            closeOverlayTargetBounds,
            closeProgress,
        ) {
            val activeCloseSource = closeSource
            val targetBounds = closeOverlayTargetBounds
            if (activeCloseSource == null || targetBounds == null) {
                null
            } else {
                lerpFloat(
                    0f,
                    calculateMediaViewerSourceCornerRadius(
                        sourceCornerRadiusPx = activeCloseSource.cornerRadiusPx,
                        sourceFullBounds = activeCloseSource.fullBoundsOnScreen,
                        targetBounds = targetBounds,
                    ),
                    closeProgress,
                )
            }
        }
        val closeWindowDismissTransform = remember(
            activeCloseSnapshot,
            closeSource,
            viewerWindowSize,
            closeProgress,
        ) {
            val snapshot = activeCloseSnapshot
            if (snapshot == null || closeSource != null || viewerWindowSize.height <= 0) {
                null
            } else {
                calculateMediaViewerWindowDismissTransform(
                    currentScale = snapshot.scale,
                    currentTranslation = snapshot.translation,
                    viewportHeight = viewerWindowSize.height,
                    progress = closeProgress,
                )
            }
        }
        val activeCloseTransform = closeOverlayTransform ?: closeWindowDismissTransform
        val closeOverlayAlpha = if (closeSource == null) {
            1f - closeProgress.coerceIn(0f, 1f)
        } else {
            1f
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isTransitionAnimating, currentPage) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val nextLocked = if (isTransitionAnimating) {
                                false
                            } else {
                                updateMediaViewerPagerGestureLock(
                                    wasLocked = isPagerGestureLocked,
                                    pressedPointerCount = event.changes.count { it.pressed },
                                )
                            }
                            if (nextLocked != isPagerGestureLocked) {
                                isPagerGestureLocked = nextLocked
                            }
                        }
                    }
                }
                .onSizeChanged { viewerWindowSize = it }
                .background(Color.Black.copy(alpha = scrimAlpha)),
        ) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                pageSpacing = MEDIA_VIEWER_PAGE_GAP,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = liveContentAlpha },
                userScrollEnabled = shouldEnableMediaViewerPagerScroll(
                    isTransitionAnimating = isTransitionAnimating,
                    scale = gestureState.scale,
                    dismissOffsetY = gestureState.dismissOffsetY,
                    isPagerGestureLocked = isPagerGestureLocked,
                ),
            ) { page ->
                val renderedCurrentPage = if (usePageMotion) {
                    pageMotionState.currentPage
                } else {
                    currentPage
                }
                val renderedTargetPage = if (usePageMotion) {
                    pageMotionState.targetPage
                } else {
                    null
                }
                val renderedSwitchProgress = if (usePageMotion) {
                    resolveMediaViewerSwitchProgress(
                        translationX = pageMotionState.translationX,
                        travelDistance = pageTravelDistance,
                    )
                } else {
                    0f
                }
                val isVisiblePage = if (usePageMotion) {
                    resolveMediaViewerPageVisibility(
                        page = page,
                        currentPage = renderedCurrentPage,
                        targetPage = renderedTargetPage,
                    )
                } else {
                    true
                }
                if (!isVisiblePage) return@HorizontalPager

                val isCurrentPage = page == renderedCurrentPage
                MediaViewerScenePage(
                    page = page,
                    request = request,
                    baseUrl = baseUrl,
                    cookieStore = cookieStore,
                    currentPage = renderedCurrentPage,
                    transitionPage = transitionPage,
                    pageTranslationX = if (usePageMotion) {
                        pageMotionState.translationX
                    } else {
                        0f
                    },
                    slotAlpha = 1f,
                    slotScale = 1f,
                    captureContentBounds = isCurrentPage,
                    publishCurrentPageContent = isCurrentPage,
                    isInteractivePage = isCurrentPage,
                    dismissProgress = dismissProgress,
                    dismissDistancePx = dismissDistancePx,
                    gestureState = gestureState,
                    contentContainerBoundsOnScreen = contentContainerBoundsOnScreen,
                    activeTransitionSource = activeTransitionSource,
                    enterProgress = enterProgress.value,
                    isEnterAnimating = isEnterAnimating,
                    isClosing = isClosing,
                    isTransitionAnimating = isTransitionAnimating,
                    closeSnapshot = closeSnapshot,
                    onContentContainerBoundsChange = { nextBounds ->
                        if (isCurrentPage && contentContainerBoundsOnScreen != nextBounds) {
                            contentContainerBoundsOnScreen = nextBounds
                        }
                    },
                    onCurrentPageContentChange = { size, bitmap ->
                        if (isCurrentPage && !isClosing) {
                            if (currentPageContentSize != size) {
                                currentPageContentSize = size
                            }
                            if (currentPageBitmap !== bitmap) {
                                currentPageBitmap = bitmap
                            }
                        }
                    },
                    onPageTranslationDelta = { translation ->
                        val target = resolveMediaViewerSwitchTargetPage(
                            currentPage = currentPage,
                            translationX = translation,
                            pageCount = request.items.size,
                        )
                        pageMotionState.updateTranslation(
                            translationX = translation,
                            targetPage = target,
                        )
                    },
                    onPageTranslationRelease = { translation, velocityX ->
                        if (
                            pageMotionState.targetPage != null &&
                            shouldCommitMediaViewerPageSwitch(
                                translationX = translation,
                                velocityX = velocityX,
                                containerWidth = viewerWindowSize.width.toFloat(),
                                density = density.density,
                            )
                        ) {
                            val target = pageMotionState.targetPage!!
                            pageMotionState.beginSwitch(
                                targetPage = target,
                                translationX = translation,
                            )
                            coroutineScope.launch {
                                val anim = Animatable(translation)
                                anim.animateTo(
                                    targetValue = if (target > currentPage) {
                                        -pageTravelDistance
                                    } else {
                                        pageTravelDistance
                                    },
                                    animationSpec = tween(
                                        durationMillis = 180,
                                        easing = LinearOutSlowInEasing,
                                    ),
                                ) {
                                    pageMotionState.updateTranslation(
                                        translationX = value,
                                        targetPage = target,
                                        isSwitching = true,
                                    )
                                }
                                gestureState.reset()
                                pagerState.scrollToPage(target)
                                pageMotionState.finishSwitch()
                            }
                        } else if (translation != 0f) {
                            val target = pageMotionState.targetPage
                            coroutineScope.launch {
                                val anim = Animatable(translation)
                                anim.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(
                                        durationMillis = 180,
                                        easing = LinearOutSlowInEasing,
                                    ),
                                ) {
                                    pageMotionState.updateTranslation(
                                        translationX = value,
                                        targetPage = target,
                                    )
                                }
                                pageMotionState.reset()
                            }
                        } else {
                            pageMotionState.reset()
                        }
                    },
                    onSwitchToPrevious = { spec ->
                        val target = spec?.targetPage ?: (currentPage - 1)
                        if (target in request.items.indices) {
                            if (gestureState.scale > 1f + MEDIA_VIEWER_REST_SCALE_EPSILON) {
                                launchZoomedPageSwitch(target)
                            } else {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(target)
                                }
                            }
                        }
                    },
                    onSwitchToNext = { spec ->
                        val target = spec?.targetPage ?: (currentPage + 1)
                        if (target in request.items.indices) {
                            if (gestureState.scale > 1f + MEDIA_VIEWER_REST_SCALE_EPSILON) {
                                launchZoomedPageSwitch(target)
                            } else {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(target)
                                }
                            }
                        }
                    },
                    onDismissRequest = {
                        coroutineScope.launch { requestDismiss() }
                    },
                    onToggleChrome = {
                        state.toggleChrome()
                    },
                    onMenuDismiss = { state.setMenuExpanded(false) },
                )
            }

            if (showCloseOverlay && activeCloseSnapshot != null && activeCloseTransform != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ViewerImageContent(
                        contentSize = activeCloseSnapshot.contentSize,
                        bitmap = activeCloseSnapshot.bitmap,
                        clipInsets = if (closeSource != null) closeOverlayClipInsets else null,
                        cornerRadiusPx = if (closeSource != null) closeOverlayCornerRadiusPx else null,
                        modifier = Modifier.graphicsLayer {
                            alpha = closeOverlayAlpha
                            scaleX = activeCloseTransform.scaleX
                            scaleY = activeCloseTransform.scaleY
                            translationX = activeCloseTransform.translationX
                            translationY = activeCloseTransform.translationY
                        },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = chromeAlpha
                        translationY = topChromeTranslationY
                    }
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ViewerChromeButton(
                    onClick = {
                        coroutineScope.launch { requestDismiss() }
                    },
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.post_image_viewer_close),
                )

                ViewerTitleBlock(
                    items = request.items,
                    currentPage = displayCurrentPage,
                    targetPage = switchTargetPage,
                    switchProgress = switchProgress,
                    alpha = 1f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                )

                Box {
                    ViewerChromeButton(
                        onClick = {
                            if (!isTransitionAnimating) {
                                state.setMenuExpanded(true)
                            }
                        },
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.post_image_viewer_more_actions),
                    )
                    ViewerActionPopup(
                        expanded = state.chromeState.isMenuExpanded,
                        actions = mediaViewerActions(currentItem),
                        onDismissRequest = { state.setMenuExpanded(false) },
                        onActionClick = { action ->
                            state.setMenuExpanded(false)
                            when (action) {
                                MediaViewerAction.Share -> {
                                    context.startMediaViewerIntent(
                                        Intent.createChooser(
                                            Intent(Intent.ACTION_SEND)
                                                .putExtra(Intent.EXTRA_TEXT, currentVisibleUrl)
                                                .setType("text/plain"),
                                            context.getString(R.string.post_image_viewer_action_share),
                                        ),
                                    ) {
                                        bulletinHostState?.show(
                                            BulletinMessage(
                                                title = context.getString(R.string.post_image_viewer_action_failed),
                                                tone = BulletinTone.Error,
                                            ),
                                        )
                                    }
                                }

                                MediaViewerAction.OpenExternal -> {
                                    if (onOpenLink != null) {
                                        onOpenLink(currentVisibleUrl)
                                    } else {
                                        context.startMediaViewerIntent(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(currentVisibleUrl)),
                                        ) {
                                            bulletinHostState?.show(
                                                BulletinMessage(
                                                    title = context.getString(R.string.post_image_viewer_action_failed),
                                                    tone = BulletinTone.Error,
                                                ),
                                            )
                                        }
                                    }
                                }

                                MediaViewerAction.SaveImage -> {
                                    val enqueued = enqueueMediaViewerDownload(
                                        context = context,
                                        resolvedUrl = currentVisibleUrl,
                                        cookieStore = cookieStore,
                                        baseUrl = baseUrl,
                                    )
                                    bulletinHostState?.show(
                                        BulletinMessage(
                                            title = context.getString(
                                                if (enqueued) {
                                                    R.string.post_image_viewer_download_started
                                                } else {
                                                    R.string.post_image_viewer_action_failed
                                                },
                                            ),
                                            tone = if (enqueued) {
                                                BulletinTone.Info
                                            } else {
                                                BulletinTone.Error
                                            },
                                        ),
                                    )
                                }
                            }
                        },
                    )
                }
            }

            MediaViewerThumbnailStrip(
                items = request.items,
                currentPage = displayCurrentPage,
                targetPage = switchTargetPage,
                switchProgress = switchProgress,
                cookieStore = cookieStore,
                baseUrl = baseUrl,
                alpha = 1f,
                onItemClick = { page ->
                    if (page != currentPage && !isTransitionAnimating) {
                        coroutineScope.launch {
                            if (shouldAnimateMediaViewerPageJump(
                                    currentPage = currentPage,
                                    targetPage = page,
                                )
                            ) {
                                pagerState.animateScrollToPage(page)
                            } else {
                                pagerState.scrollToPage(page)
                            }
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        alpha = chromeAlpha
                        translationY = bottomChromeTranslationY
                    }
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            )

        }
    }
}
