package io.github.baba983.nativedo.feature.browser

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderBlock
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderContent
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderDocument
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderListItem
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderTableCell
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderTableCellAlignment
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderTableRow
import io.github.baba983.nativedo.core.navigation.flattenBodyText
import io.github.baba983.nativedo.core.storage.AppBrowserHistorySnapshot
import io.github.baba983.nativedo.core.storage.AppBrowserHistoryStorePersistence
import io.github.baba983.nativedo.core.ui.PostBodyImage
import io.github.baba983.nativedo.core.ui.TextDocument
import io.github.baba983.nativedo.core.ui.TextEntity
import io.github.baba983.nativedo.core.ui.buildTextDocumentFromNodes
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import kotlin.math.abs

@Serializable
data class AppBrowserHistoryRecord(
    val title: String,
    val url: String,
    val host: String,
    val visitedAtMillis: Long,
    val faviconBase64: String? = null,
)

sealed interface AppBrowserPanel {
    data object Page : AppBrowserPanel

    data object History : AppBrowserPanel

    data object Bookmarks : AppBrowserPanel

    data object Settings : AppBrowserPanel
}

internal data class AppBrowserSearchPanelState(
    val visible: Boolean,
    val matchText: String,
    val canNavigate: Boolean,
    val showsNoResults: Boolean = false,
)

internal data class AppBrowserScrollChromeBackgroundState(
    val fillFraction: Float,
    val fillAlpha: Float,
    val dividerAlpha: Float,
)

internal data class AppBrowserRoutePresentationState(
    val progress: Float,
    val translationX: Float,
    val alpha: Float,
    val backdropAlpha: Float,
)

internal enum class AppBrowserContainerMode {
    FullRoute,
    Stack,
    Sheet,
}

internal enum class AppBrowserSheetAnchor {
    Closed,
    Partial,
    Fullscreen,
}

internal data class AppBrowserSheetPresentationState(
    val openedProgress: Float,
    val fullscreenProgress: Float,
    val topOffsetPx: Float,
    val scrimAlpha: Float,
)

internal data class AppBrowserSheetSettle(
    val target: AppBrowserSheetAnchor,
    val durationMillis: Int,
)

internal data class AppBrowserTopBarPresentationState(
    val alpha: Float,
    val translationX: Float,
)

internal data class AppBrowserSwipeSettle(
    val shouldDismiss: Boolean,
    val targetOffsetPx: Float,
    val durationMillis: Int,
)

internal data class AppBrowserScrollSnapshot(
    val scrollY: Int,
    val deltaY: Int,
    val progress: Float,
)

internal data class AppBrowserHeaderCollapseState(
    val offsetPx: Float = 0f,
    val progress: Float = 0f,
)

internal data class AppBrowserLoadProgressState(
    val displayedProgress: Float = 0f,
    val actualProgress: Float = 0f,
    val isLoading: Boolean = false,
    val isVisible: Boolean = false,
) {
    fun start(): AppBrowserLoadProgressState {
        return copy(
            displayedProgress = appBrowserLoadProgressStart,
            actualProgress = 0f,
            isLoading = true,
            isVisible = true,
        )
    }

    fun onActualProgressChanged(progress: Float): AppBrowserLoadProgressState {
        val normalized = progress.coerceIn(0f, 1f)
        return copy(
            actualProgress = normalized,
            displayedProgress = maxOf(displayedProgress, normalized),
            isLoading = normalized < 1f,
            isVisible = isVisible || normalized > 0f,
        )
    }

    fun finish(): AppBrowserLoadProgressState {
        return copy(
            displayedProgress = 1f,
            actualProgress = 1f,
            isLoading = false,
            isVisible = true,
        )
    }

    fun dismiss(): AppBrowserLoadProgressState {
        return AppBrowserLoadProgressState()
    }
}

internal data class AppBrowserStorageUsage(
    val cookiesSizeBytes: Long,
    val cacheSizeBytes: Long,
)

internal data class AppBrowserInstantViewLoaderState(
    val pageUrl: String? = null,
    val pageProgress: Float = 0f,
    val pageLoaded: Boolean = false,
    val phase: AppBrowserInstantViewPhase = AppBrowserInstantViewPhase.Hidden,
    val document: AppBrowserReaderDocument? = null,
    val pendingOpenRequested: Boolean = false,
    val resolveInFlight: Boolean = false,
) {
    val supportsInstantView: Boolean
        get() = document != null

    val actionAvailability: AppBrowserInstantViewActionAvailability
        get() = when {
            phase == AppBrowserInstantViewPhase.Hidden ||
                phase == AppBrowserInstantViewPhase.Unsupported -> AppBrowserInstantViewActionAvailability.Hidden
            phase == AppBrowserInstantViewPhase.Ready && document != null -> AppBrowserInstantViewActionAvailability.Enabled
            else -> AppBrowserInstantViewActionAvailability.Disabled
        }

    fun onPageStarted(pageUrl: String?): AppBrowserInstantViewLoaderState {
        return copy(
            pageUrl = pageUrl?.takeIf(String::isNotBlank),
            pageProgress = 0f,
            pageLoaded = false,
            phase = AppBrowserInstantViewPhase.Probing,
            document = null,
            pendingOpenRequested = false,
            resolveInFlight = false,
        )
    }

    fun onResolveStarted(
        pageUrl: String,
        openWhenResolved: Boolean,
    ): AppBrowserInstantViewLoaderState {
        return copy(
            pageUrl = pageUrl,
            phase = AppBrowserInstantViewPhase.Probing,
            pendingOpenRequested = pendingOpenRequested || openWhenResolved,
            resolveInFlight = true,
        )
    }

    fun onPageFinished(pageUrl: String?): AppBrowserInstantViewLoaderState {
        val normalizedUrl = pageUrl?.takeIf(String::isNotBlank) ?: this.pageUrl
        return copy(
            pageUrl = normalizedUrl,
            pageProgress = 1f,
            pageLoaded = true,
        )
    }

    fun onResolved(
        pageUrl: String,
        document: AppBrowserReaderDocument?,
    ): AppBrowserInstantViewLoaderState {
        if (this.pageUrl != null && this.pageUrl != pageUrl) {
            return this
        }
        return copy(
            pageUrl = pageUrl,
            phase = if (document != null) {
                AppBrowserInstantViewPhase.Ready
            } else {
                AppBrowserInstantViewPhase.Unsupported
            },
            document = document,
            resolveInFlight = false,
        )
    }

    fun isStaleForSnapshot(
        pageUrl: String?,
        pageProgress: Float,
        pageLoaded: Boolean,
    ): Boolean {
        val normalizedUrl = pageUrl?.takeIf(String::isNotBlank)
        val normalizedProgress = pageProgress.coerceIn(0f, 1f)
        return this.pageUrl != normalizedUrl ||
            this.pageProgress != normalizedProgress ||
            this.pageLoaded != pageLoaded
    }

    fun onPageSnapshotChanged(
        pageUrl: String?,
        pageProgress: Float,
    ): AppBrowserInstantViewLoaderState {
        val normalizedUrl = pageUrl?.takeIf(String::isNotBlank)
        val normalizedProgress = pageProgress.coerceIn(0f, 1f)
        val preservesLoadedSnapshot = pageLoaded &&
            pageUrl != null &&
            pageUrl == this.pageUrl
        val effectiveProgress = if (preservesLoadedSnapshot) {
            maxOf(this.pageProgress, normalizedProgress)
        } else {
            normalizedProgress
        }
        val effectiveLoaded = preservesLoadedSnapshot
        val isStale = isStaleForSnapshot(
            pageUrl = normalizedUrl,
            pageProgress = effectiveProgress,
            pageLoaded = effectiveLoaded,
        )
        return copy(
            pageUrl = normalizedUrl,
            pageProgress = effectiveProgress,
            pageLoaded = effectiveLoaded,
            phase = if (document != null && isStale) {
                AppBrowserInstantViewPhase.Probing
            } else {
                phase
            },
            document = if (document != null && isStale) null else document,
            resolveInFlight = if (document != null && isStale) {
                false
            } else {
                resolveInFlight
            },
        )
    }

    fun clearPendingOpen(): AppBrowserInstantViewLoaderState {
        return copy(pendingOpenRequested = false)
    }
}

internal data class AppBrowserWebPermissionRequestData(
    val grantableResources: List<String>,
    val requiredAndroidPermissions: List<String>,
)

internal data class AppBrowserPopupRequest(
    val requestedUrl: String?,
    val fallbackUrl: String?,
    val isDialog: Boolean,
    val isUserGesture: Boolean,
) {
    val resolvedUrl: String?
        get() = resolveAppBrowserPopupUrl(
            requestedUrl = requestedUrl,
            fallbackUrl = fallbackUrl,
        )
}

internal data class AppBrowserDownloadRequest(
    val url: String,
    val userAgent: String?,
    val contentDisposition: String?,
    val mimeType: String?,
    val contentLength: Long,
)

internal enum class AppBrowserPageLoadErrorKind {
    Network,
    Http,
}

internal data class AppBrowserPageLoadError(
    val kind: AppBrowserPageLoadErrorKind,
    val url: String,
    val rawMessage: String? = null,
    val statusCode: Int? = null,
    val reasonPhrase: String? = null,
)

internal fun buildAppBrowserWebPermissionRequestData(
    requestedResources: Array<String>,
): AppBrowserWebPermissionRequestData? {
    val grantableResources = buildList {
        if (requestedResources.contains(appBrowserVideoCaptureResource)) {
            add(appBrowserVideoCaptureResource)
        }
        if (requestedResources.contains(appBrowserAudioCaptureResource)) {
            add(appBrowserAudioCaptureResource)
        }
    }
    if (grantableResources.isEmpty()) {
        return null
    }
    val requiredAndroidPermissions = buildList {
        if (grantableResources.contains(appBrowserVideoCaptureResource)) {
            add(Manifest.permission.CAMERA)
        }
        if (grantableResources.contains(appBrowserAudioCaptureResource)) {
            add(Manifest.permission.RECORD_AUDIO)
        }
    }
    return AppBrowserWebPermissionRequestData(
        grantableResources = grantableResources,
        requiredAndroidPermissions = requiredAndroidPermissions,
    )
}

internal fun buildAppBrowserDownloadHeaders(
    cookie: String?,
    userAgent: String?,
): Map<String, String> {
    return buildMap {
        cookie
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { put("Cookie", it) }
        userAgent
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { put("User-Agent", it) }
    }
}

internal fun buildAppBrowserPageLoadError(
    failingUrl: String,
    isForMainFrame: Boolean,
    description: String?,
    statusCode: Int? = null,
    reasonPhrase: String? = null,
): AppBrowserPageLoadError? {
    if (!isForMainFrame || failingUrl.isBlank()) {
        return null
    }
    return if (statusCode != null) {
        AppBrowserPageLoadError(
            kind = AppBrowserPageLoadErrorKind.Http,
            url = failingUrl,
            rawMessage = description?.trim()?.takeIf(String::isNotBlank),
            statusCode = statusCode,
            reasonPhrase = reasonPhrase?.trim()?.takeIf(String::isNotBlank),
        )
    } else {
        AppBrowserPageLoadError(
            kind = AppBrowserPageLoadErrorKind.Network,
            url = failingUrl,
            rawMessage = description?.trim()?.takeIf(String::isNotBlank),
        )
    }
}

internal fun resolveAppBrowserPopupUrl(
    requestedUrl: String?,
    fallbackUrl: String?,
): String? {
    return requestedUrl
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: fallbackUrl
            ?.trim()
            ?.takeIf(String::isNotBlank)
}

internal fun buildAppBrowserRoutePresentationState(
    offsetPx: Float,
    screenWidthPx: Float,
): AppBrowserRoutePresentationState {
    val safeScreenWidthPx = screenWidthPx.takeIf { it > 0f } ?: 1f
    val clampedOffset = offsetPx.coerceIn(0f, safeScreenWidthPx)
    val progress = 1f - (clampedOffset / safeScreenWidthPx)
    return AppBrowserRoutePresentationState(
        progress = progress,
        translationX = clampedOffset,
        alpha = lerpAppBrowserVisual(0.84f, 1f, progress),
        backdropAlpha = (1f - progress) * 0.32f,
    )
}

internal fun buildAppBrowserTopBarPresentationState(
    offsetPx: Float,
    screenWidthPx: Float,
    maxTranslationPx: Float,
): AppBrowserTopBarPresentationState {
    val safeScreenWidthPx = screenWidthPx.takeIf { it > 0f } ?: 1f
    val clampedOffset = offsetPx.coerceIn(0f, safeScreenWidthPx)
    val progress = 1f - (clampedOffset / safeScreenWidthPx)
    val clampedMaxTranslationPx = maxTranslationPx.coerceAtLeast(0f)
    return AppBrowserTopBarPresentationState(
        alpha = lerpAppBrowserVisual(0.90f, 1f, progress),
        translationX = clampedMaxTranslationPx * (1f - progress),
    )
}

internal fun buildAppBrowserSheetPresentationState(
    topOffsetPx: Float,
    partialTopOffsetPx: Float,
    closedTopOffsetPx: Float,
): AppBrowserSheetPresentationState {
    val safeClosedTopOffsetPx = closedTopOffsetPx.coerceAtLeast(1f)
    val safePartialTopOffsetPx = partialTopOffsetPx.coerceIn(0f, safeClosedTopOffsetPx)
    val clampedTopOffsetPx = topOffsetPx.coerceIn(0f, safeClosedTopOffsetPx)
    val openedTravel = (safeClosedTopOffsetPx - safePartialTopOffsetPx).coerceAtLeast(1f)
    val openedProgress = ((safeClosedTopOffsetPx - clampedTopOffsetPx) / openedTravel)
        .coerceIn(0f, 1f)
    val fullscreenProgress = if (safePartialTopOffsetPx == 0f) {
        1f
    } else {
        ((safePartialTopOffsetPx - clampedTopOffsetPx) / safePartialTopOffsetPx)
            .coerceIn(0f, 1f)
    }
    val scrimAlpha = (
        lerpAppBrowserVisual(0f, 0.22f, openedProgress) +
            lerpAppBrowserVisual(0f, 0.16f, fullscreenProgress)
        ).coerceIn(0f, 0.38f)
    return AppBrowserSheetPresentationState(
        openedProgress = openedProgress,
        fullscreenProgress = fullscreenProgress,
        topOffsetPx = clampedTopOffsetPx,
        scrimAlpha = scrimAlpha,
    )
}

internal fun buildAppBrowserSheetSettle(
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
    val travelDistancePx = abs(targetOffsetPx - clampedTopOffsetPx)
    val durationMillis = maxOf(((360f / safeClosedTopOffsetPx) * travelDistancePx).toInt(), 220)
    return AppBrowserSheetSettle(
        target = target,
        durationMillis = durationMillis,
    )
}

internal fun buildAppBrowserSwipeSettle(
    currentOffsetPx: Float,
    velocityPxPerSecond: Float,
    screenWidthPx: Float,
    dismissThresholdFraction: Float = 0.35f,
    flingVelocityThresholdPxPerSecond: Float = 800f,
): AppBrowserSwipeSettle {
    val safeScreenWidthPx = screenWidthPx.takeIf { it > 0f } ?: 1f
    val clampedOffset = currentOffsetPx.coerceIn(0f, safeScreenWidthPx)
    val shouldDismiss = clampedOffset > (safeScreenWidthPx * dismissThresholdFraction) ||
        velocityPxPerSecond > flingVelocityThresholdPxPerSecond
    val remainingDistance = if (shouldDismiss) {
        safeScreenWidthPx - clampedOffset
    } else {
        clampedOffset
    }
    val durationMillis = maxOf(((420f / safeScreenWidthPx) * remainingDistance).toInt(), 250)
    return AppBrowserSwipeSettle(
        shouldDismiss = shouldDismiss,
        targetOffsetPx = if (shouldDismiss) safeScreenWidthPx else 0f,
        durationMillis = durationMillis,
    )
}

internal fun buildAppBrowserScrollSnapshot(
    scrollY: Int,
    previousScrollY: Int,
    contentHeightPx: Int,
    viewportHeightPx: Int,
): AppBrowserScrollSnapshot {
    return AppBrowserScrollSnapshot(
        scrollY = scrollY.coerceAtLeast(0),
        deltaY = scrollY - previousScrollY,
        progress = buildAppBrowserScrollProgress(
            scrollY = scrollY,
            contentHeightPx = contentHeightPx,
            viewportHeightPx = viewportHeightPx,
        ),
    )
}

internal fun buildAppBrowserHeaderCollapseState(
    currentOffsetPx: Float,
    scrollY: Int,
    deltaY: Int,
    maxCollapseOffsetPx: Float,
    isEnabled: Boolean,
): AppBrowserHeaderCollapseState {
    val safeMaxCollapseOffsetPx = maxCollapseOffsetPx.coerceAtLeast(0f)
    if (!isEnabled || safeMaxCollapseOffsetPx == 0f) {
        return AppBrowserHeaderCollapseState()
    }
    if (scrollY <= appBrowserHeaderRestoreThresholdPx) {
        return AppBrowserHeaderCollapseState()
    }
    val filteredDeltaPx = if (abs(deltaY) <= appBrowserHeaderDeltaThresholdPx) {
        0f
    } else {
        deltaY
            .coerceIn(-appBrowserHeaderMaxStepPx, appBrowserHeaderMaxStepPx)
            .toFloat()
    }
    val resolvedOffsetPx = (currentOffsetPx + filteredDeltaPx)
        .coerceIn(0f, safeMaxCollapseOffsetPx)
    return AppBrowserHeaderCollapseState(
        offsetPx = resolvedOffsetPx,
        progress = (resolvedOffsetPx / safeMaxCollapseOffsetPx).coerceIn(0f, 1f),
    )
}

internal fun shouldApplyAppBrowserFindResult(
    isSearching: Boolean,
    visibleQuery: String,
    dispatchedQuery: String?,
): Boolean {
    return isSearching &&
        visibleQuery.isNotBlank() &&
        visibleQuery == dispatchedQuery
}

class AppBrowserHistoryStore(
    private val persistence: AppBrowserHistoryStorePersistence = NoOpAppBrowserHistoryStorePersistence,
    private val persistenceDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _history = MutableStateFlow(
        persistence.load().map { snapshot ->
            AppBrowserHistoryRecord(
                title = snapshot.title,
                url = snapshot.url,
                host = snapshot.host,
                visitedAtMillis = snapshot.visitedAtMillis,
                faviconBase64 = snapshot.faviconBase64,
            )
        },
    )
    val history: StateFlow<List<AppBrowserHistoryRecord>> = _history.asStateFlow()
    private val persistenceScope = CoroutineScope(SupervisorJob() + persistenceDispatcher)
    private val mutations = Channel<HistoryMutation>(capacity = Channel.UNLIMITED)
    private val mutationGeneration = AtomicLong(0L)

    init {
        persistenceScope.launch {
            for (mutation in mutations) {
                when (mutation) {
                    is HistoryMutation.RecordVisit -> {
                        if (mutation.generation != mutationGeneration.get()) {
                            continue
                        }
                        val host = mutation.url.toHttpUrlOrNull()?.host ?: mutation.url
                        val resolvedTitle = mutation.title?.trim().takeUnless { it.isNullOrBlank() } ?: host
                        val existingEntry = _history.value.firstOrNull { it.url == mutation.url }
                        val entry = AppBrowserHistoryRecord(
                            title = resolvedTitle,
                            url = mutation.url,
                            host = host,
                            visitedAtMillis = mutation.visitedAtMillis,
                            faviconBase64 = mutation.favicon?.toHistoryFaviconBase64() ?: existingEntry?.faviconBase64,
                        )
                        val updatedHistory = (listOf(entry) + _history.value.filterNot { it.url == mutation.url })
                            .take(appBrowserHistoryMaxRecordCount)
                        _history.value = updatedHistory
                        persistence.save(updatedHistory.toSnapshots())
                    }

                    is HistoryMutation.Clear -> {
                        if (mutation.generation != mutationGeneration.get()) {
                            continue
                        }
                        _history.value = emptyList()
                        persistence.save(emptyList())
                    }
                }
            }
        }
    }

    fun recordVisit(
        url: String,
        title: String?,
        visitedAtMillis: Long = System.currentTimeMillis(),
        favicon: Bitmap? = null,
    ) {
        if (url.isBlank()) {
            return
        }
        mutations.trySend(
            HistoryMutation.RecordVisit(
                generation = mutationGeneration.get(),
                url = url,
                title = title,
                visitedAtMillis = visitedAtMillis,
                favicon = favicon,
            ),
        )
    }

    fun clear() {
        val generation = mutationGeneration.incrementAndGet()
        _history.value = emptyList()
        mutations.trySend(HistoryMutation.Clear(generation = generation))
    }

    fun clearImmediate() {
        val generation = mutationGeneration.incrementAndGet()
        _history.value = emptyList()
        while (mutations.tryReceive().isSuccess) {
            // Drop stale queued writes so UI does not flicker with pre-clear history.
        }
        mutations.trySend(HistoryMutation.Clear(generation = generation))
    }

    private object NoOpAppBrowserHistoryStorePersistence : AppBrowserHistoryStorePersistence {
        override fun load(): List<AppBrowserHistorySnapshot> = emptyList()

        override fun save(records: List<AppBrowserHistorySnapshot>) = Unit
    }
}

private sealed interface HistoryMutation {
    data class RecordVisit(
        val generation: Long,
        val url: String,
        val title: String?,
        val visitedAtMillis: Long,
        val favicon: Bitmap?,
    ) : HistoryMutation

    data class Clear(
        val generation: Long,
    ) : HistoryMutation
}

private fun List<AppBrowserHistoryRecord>.toSnapshots(): List<AppBrowserHistorySnapshot> {
    return map { record ->
        AppBrowserHistorySnapshot(
            title = record.title,
            url = record.url,
            host = record.host,
            visitedAtMillis = record.visitedAtMillis,
            faviconBase64 = record.faviconBase64,
        )
    }
}

internal fun buildAppBrowserMenuEntries(
    canGoForward: Boolean,
    hasBrowserHistory: Boolean,
    isBookmarked: Boolean,
    instantViewActionAvailability: AppBrowserInstantViewActionAvailability =
        AppBrowserInstantViewActionAvailability.Hidden,
): List<AppBrowserMenuEntry> {
    return listOf(
        AppBrowserMenuEntry.Item(
            AppBrowserMenuItem(
                action = AppBrowserMenuAction.Forward,
                enabled = canGoForward,
            ),
        ),
        AppBrowserMenuEntry.Item(AppBrowserMenuItem(action = AppBrowserMenuAction.Refresh)),
        AppBrowserMenuEntry.Item(
            AppBrowserMenuItem(
                action = if (isBookmarked) {
                    AppBrowserMenuAction.RemoveBookmark
                } else {
                    AppBrowserMenuAction.AddBookmark
                },
            ),
        ),
        AppBrowserMenuEntry.Divider,
        instantViewActionAvailability
            .takeUnless { it == AppBrowserInstantViewActionAvailability.Hidden }
            ?.let {
            AppBrowserMenuEntry.Item(
                AppBrowserMenuItem(
                    action = AppBrowserMenuAction.InstantView,
                    enabled = instantViewActionAvailability == AppBrowserInstantViewActionAvailability.Enabled,
                ),
            )
        },
        AppBrowserMenuEntry.Item(AppBrowserMenuItem(action = AppBrowserMenuAction.OpenInBrowser)),
        AppBrowserMenuEntry.Item(AppBrowserMenuItem(action = AppBrowserMenuAction.Share)),
        AppBrowserMenuEntry.Item(AppBrowserMenuItem(action = AppBrowserMenuAction.CopyLink)),
        AppBrowserMenuEntry.Divider,
        AppBrowserMenuEntry.Item(AppBrowserMenuItem(action = AppBrowserMenuAction.Search)),
        AppBrowserMenuEntry.Item(
            AppBrowserMenuItem(
                action = AppBrowserMenuAction.History,
                enabled = hasBrowserHistory,
            ),
        ),
        AppBrowserMenuEntry.Item(AppBrowserMenuItem(action = AppBrowserMenuAction.Settings)),
        AppBrowserMenuEntry.Item(AppBrowserMenuItem(action = AppBrowserMenuAction.Close)),
    ).filterNotNull()
}

internal fun buildAppBrowserReaderContent(
    url: String,
    title: String?,
    bodyText: String?,
    sourceName: String? = null,
    excerpt: String? = null,
    byline: String? = null,
    publishedTime: String? = null,
    heroImageUrl: String? = null,
): AppBrowserReaderContent? {
    val resolvedUrl = url.toHttpUrlOrNull() ?: return null
    val normalizedTitle = resolveAppBrowserReaderDocumentTitle(
        rawTitle = title,
        fallbackHost = resolvedUrl.host,
    )
    val normalizedBody = normalizeAppBrowserReaderBodyText(bodyText)
    if (!supportsAppBrowserInstantView(url = resolvedUrl.toString(), title = normalizedTitle, bodyText = normalizedBody)) {
        return null
    }
    return AppBrowserReaderContent(
        document = AppBrowserReaderDocument(
            url = resolvedUrl.toString(),
            title = normalizedTitle,
            sourceName = sourceName?.trim()?.takeIf(String::isNotBlank),
            excerpt = excerpt?.trim()?.takeIf(String::isNotBlank),
            byline = byline?.trim()?.takeIf(String::isNotBlank),
            publishedTime = publishedTime?.trim()?.takeIf(String::isNotBlank),
            heroImageUrl = heroImageUrl?.trim()?.takeIf(String::isNotBlank),
            blocks = normalizedBody
                .split(Regex("""\n\s*\n"""))
                .map(String::trim)
                .filter(String::isNotBlank)
                .map { paragraph ->
                    AppBrowserReaderBlock.Paragraph(
                        content = TextDocument(
                            text = paragraph,
                            entities = emptyList(),
                        ),
                    )
                },
        ),
    )
}

internal fun buildAppBrowserReaderContent(
    document: AppBrowserReaderDocument?,
): AppBrowserReaderContent? {
    val readerDocument = document ?: return null
    val fallbackHost = readerDocument.url.toHttpUrlOrNull()?.host ?: readerDocument.url
    val resolvedTitle = resolveAppBrowserReaderDocumentTitle(
        rawTitle = readerDocument.title,
        fallbackHost = fallbackHost,
        headingTitle = readerDocument.blocks.leadingReaderHeadingText(),
    )
    val normalizedDocument = readerDocument.copy(
        title = resolvedTitle,
        blocks = readerDocument.blocks.dropLeadingDuplicateReaderHeading(resolvedTitle),
    )
    val normalizedBody = normalizeAppBrowserReaderBodyText(normalizedDocument.flattenBodyText())
    if (!supportsAppBrowserInstantView(normalizedDocument.url, resolvedTitle, normalizedBody)) {
        return null
    }
    return AppBrowserReaderContent(
        document = normalizedDocument,
    )
}

internal fun supportsAppBrowserInstantView(
    url: String,
    title: String?,
    bodyText: String?,
): Boolean {
    val resolvedUrl = url.toHttpUrlOrNull() ?: return false
    val normalizedTitle = title
        ?.trim()
        .orEmpty()
    val normalizedBody = normalizeAppBrowserReaderBodyText(bodyText)
    if (normalizedTitle.isBlank() || normalizedBody.length < appBrowserReaderMinBodyLength) {
        return false
    }

    val substantialLineCount = normalizedBody
        .lineSequence()
        .map(String::trim)
        .count { line -> line.length >= appBrowserReaderMinLineLength }
    if (substantialLineCount < appBrowserReaderMinLineCount) {
        return false
    }

    val segments = resolvedUrl.pathSegments
        .filter { it.isNotBlank() }
        .map(String::lowercase)
    val host = resolvedUrl.host.lowercase()
    if (host == "linux.do") {
        return segments.firstOrNull() == "t"
    }

    val disallowedSegments = setOf(
        "login",
        "signin",
        "signup",
        "session",
        "search",
        "settings",
        "account",
    )
    return resolvedUrl.scheme in setOf("http", "https") &&
        segments.isNotEmpty() &&
        segments.none { segment -> segment in disallowedSegments }
}

internal fun normalizeAppBrowserReaderBodyText(
    bodyText: String?,
): String {
    return bodyText
        .orEmpty()
        .replace('\u00A0', ' ')
        .lineSequence()
        .map { line -> line.trimEnd() }
        .joinToString(separator = "\n")
        .replace(Regex("""[ \t]{2,}"""), " ")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()
}

internal fun extractAppBrowserReaderBodyFromHtml(
    html: String?,
    pageUrl: String,
): String? {
    return extractAppBrowserReaderDocumentFromHtml(
        html = html,
        pageUrl = pageUrl,
    )?.flattenBodyText()
        ?.let(::normalizeAppBrowserReaderBodyText)
        ?.takeIf(String::isNotBlank)
}

@Serializable
private data class AppBrowserInstantViewJsonDocument(
    val title: String? = null,
    val sourceName: String? = null,
    val excerpt: String? = null,
    val byline: String? = null,
    val publishedTime: String? = null,
    val heroImageUrl: String? = null,
    val blocks: List<AppBrowserInstantViewJsonBlock> = emptyList(),
)

@Serializable
private data class AppBrowserInstantViewJsonBlock(
    val type: String,
    val text: String? = null,
    val content: AppBrowserInstantViewJsonTextContent? = null,
    val attribution: AppBrowserInstantViewJsonTextContent? = null,
    val summary: AppBrowserInstantViewJsonTextContent? = null,
    val titleContent: AppBrowserInstantViewJsonTextContent? = null,
    val level: Int? = null,
    val ordered: Boolean? = null,
    val expanded: Boolean? = null,
    val striped: Boolean? = null,
    val bordered: Boolean? = null,
    val items: List<AppBrowserInstantViewJsonListItem> = emptyList(),
    val blocks: List<AppBrowserInstantViewJsonBlock> = emptyList(),
    val rows: List<AppBrowserInstantViewJsonTableRow> = emptyList(),
    val code: String? = null,
    val language: String? = null,
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val alt: String? = null,
    val caption: AppBrowserInstantViewJsonTextContent? = null,
    val credit: AppBrowserInstantViewJsonTextContent? = null,
)

@Serializable
private data class AppBrowserInstantViewJsonListItem(
    val blocks: List<AppBrowserInstantViewJsonBlock> = emptyList(),
)

@Serializable
private data class AppBrowserInstantViewJsonTableRow(
    val cells: List<AppBrowserInstantViewJsonTableCell> = emptyList(),
)

@Serializable
private data class AppBrowserInstantViewJsonTableCell(
    val content: AppBrowserInstantViewJsonTextContent? = null,
    val text: String? = null,
    val header: Boolean? = null,
    val colspan: Int? = null,
    val rowspan: Int? = null,
    val align: String? = null,
)

@Serializable
private data class AppBrowserInstantViewJsonTextContent(
    val text: String? = null,
    val entities: List<AppBrowserInstantViewJsonTextEntity> = emptyList(),
)

@Serializable
private data class AppBrowserInstantViewJsonTextEntity(
    val type: String,
    val start: Int,
    val end: Int,
    val url: String? = null,
    val username: String? = null,
    val fallback: String? = null,
)

private val appBrowserInstantViewJson = Json {
    ignoreUnknownKeys = true
}

internal fun parseAppBrowserInstantViewJsonDocument(
    pageUrl: String,
    json: String?,
): AppBrowserReaderDocument? {
    val resolvedUrl = pageUrl.toHttpUrlOrNull() ?: return null
    val payload = json
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: return null
    val parsed = runCatching {
        appBrowserInstantViewJson.decodeFromString<AppBrowserInstantViewJsonDocument>(payload)
    }.getOrNull() ?: return null
    val parsedBlocks = parsed.blocks
        .mapNotNull(::toAppBrowserReaderBlock)
    val resolvedTitle = resolveAppBrowserReaderDocumentTitle(
        rawTitle = parsed.title,
        fallbackHost = resolvedUrl.host,
        headingTitle = parsedBlocks.leadingReaderHeadingText(),
    )
    val blocks = parsedBlocks
        .dropLeadingDuplicateReaderHeading(resolvedTitle)
    if (blocks.isEmpty()) {
        return null
    }
    return AppBrowserReaderDocument(
        url = resolvedUrl.toString(),
        title = resolvedTitle,
        sourceName = parsed.sourceName?.trim()?.takeIf(String::isNotBlank),
        excerpt = parsed.excerpt?.trim()?.takeIf(String::isNotBlank),
        byline = parsed.byline?.trim()?.takeIf(String::isNotBlank),
        publishedTime = parsed.publishedTime?.trim()?.takeIf(String::isNotBlank),
        heroImageUrl = parsed.heroImageUrl?.trim()?.takeIf(String::isNotBlank),
        blocks = blocks,
    )
}

internal fun sanitizeAppBrowserInstantViewHtmlSnapshot(
    html: String,
): String {
    val document = Jsoup.parse(html)
    document.select(
        "script,link,iframe,frame,object,embed,canvas,video,audio",
    ).remove()
    document.outputSettings().prettyPrint(false)
    return document.outerHtml()
}

private fun toAppBrowserReaderBlock(
    block: AppBrowserInstantViewJsonBlock,
): AppBrowserReaderBlock? {
    return when (block.type.lowercase()) {
        "paragraph" -> block.toAppBrowserTextDocument()?.let(AppBrowserReaderBlock::Paragraph)
        "heading" -> block.toAppBrowserTextDocument()?.let { content ->
            AppBrowserReaderBlock.Heading(
                level = block.level?.coerceIn(1, 6) ?: 1,
                content = content,
            )
        }
        "quote" -> block.blocks
            .mapNotNull(::toAppBrowserReaderBlock)
            .takeIf(List<*>::isNotEmpty)
            ?.let(AppBrowserReaderBlock::Quote)
        "pullquote" -> block.toAppBrowserTextDocument()?.let { content ->
            AppBrowserReaderBlock.PullQuote(
                content = content,
                attribution = block.attribution.toAppBrowserTextDocument(),
            )
        }
        "details" -> block.summary.toAppBrowserTextDocument()?.let { summary ->
            val children = block.blocks.mapNotNull(::toAppBrowserReaderBlock)
            AppBrowserReaderBlock.Details(
                summary = summary,
                children = children,
                expandedByDefault = block.expanded == true,
            )
        }
        "list" -> block.items
            .mapNotNull { item ->
                item.blocks
                    .mapNotNull(::toAppBrowserReaderBlock)
                    .takeIf(List<*>::isNotEmpty)
                    ?.let(::AppBrowserReaderListItem)
            }
            .takeIf(List<*>::isNotEmpty)
            ?.let { items ->
                AppBrowserReaderBlock.ListBlock(
                    ordered = block.ordered == true,
                    items = items,
                )
            }
        "code" -> block.code
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { code ->
                AppBrowserReaderBlock.CodeBlock(
                    code = code,
                    languageHint = block.language?.trim()?.takeIf(String::isNotBlank),
                )
            }
        "image",
        "figure" -> block.toAppBrowserImageBlock()
        "table" -> block.toAppBrowserTableBlock()
        "divider" -> AppBrowserReaderBlock.Divider
        else -> null
    }
}

private fun AppBrowserInstantViewJsonBlock.toAppBrowserTextDocument(): TextDocument? {
    return content.toAppBrowserTextDocument()
        ?: text.toAppBrowserTextDocument()
}

private fun AppBrowserInstantViewJsonTextContent?.toAppBrowserTextDocument(): TextDocument? {
    val text = this
        ?.text
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: return null
    val length = text.length
    val entities = this.entities.mapNotNull { entity ->
        entity.toTextEntity(length)
    }
    return TextDocument(
        text = text,
        entities = entities,
    )
}

private fun String?.toAppBrowserTextDocument(): TextDocument? {
    val text = this
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: return null
    return TextDocument(
        text = text,
        entities = emptyList(),
    )
}

private fun AppBrowserInstantViewJsonTextEntity.toTextEntity(
    textLength: Int,
): TextEntity? {
    val range = normalizeAppBrowserTextEntityRange(textLength) ?: return null
    return when (type.lowercase()) {
        "bold" -> TextEntity.Bold(range.start, range.end)
        "italic" -> TextEntity.Italic(range.start, range.end)
        "strike" -> TextEntity.Strike(range.start, range.end)
        "link" -> url?.trim()?.takeIf(String::isNotBlank)?.let { rawUrl ->
            TextEntity.Link(range.start, range.end, rawUrl = rawUrl)
        }
        "mention" -> username?.trim()?.takeIf(String::isNotBlank)?.let { name ->
            TextEntity.Mention(range.start, range.end, username = name)
        }
        "inline_code" -> TextEntity.InlineCode(range.start, range.end)
        "underline" -> TextEntity.Underline(range.start, range.end)
        "highlight" -> TextEntity.Highlight(range.start, range.end)
        "superscript" -> TextEntity.Superscript(range.start, range.end)
        "subscript" -> TextEntity.Subscript(range.start, range.end)
        "small" -> TextEntity.Small(range.start, range.end)
        "kbd" -> TextEntity.Kbd(range.start, range.end)
        else -> null
    }
}

private fun AppBrowserInstantViewJsonTextEntity.normalizeAppBrowserTextEntityRange(
    textLength: Int,
): AppBrowserTextEntityRange? {
    val normalizedStart = start.coerceIn(0, textLength)
    val normalizedEnd = end.coerceIn(0, textLength)
    if (normalizedStart >= normalizedEnd) {
        return null
    }
    return AppBrowserTextEntityRange(
        start = normalizedStart,
        end = normalizedEnd,
    )
}

private fun AppBrowserInstantViewJsonBlock.toAppBrowserImageBlock(): AppBrowserReaderBlock.Image? {
    val resolvedUrl = url
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: return null
    return AppBrowserReaderBlock.Image(
        image = PostBodyImage(
            rawUrl = resolvedUrl,
            width = width?.takeIf { it > 0 },
            height = height?.takeIf { it > 0 },
        ),
        alt = alt?.trim()?.takeIf(String::isNotBlank),
        caption = caption.toAppBrowserTextDocument(),
        credit = credit.toAppBrowserTextDocument(),
    )
}

private fun AppBrowserInstantViewJsonBlock.toAppBrowserTableBlock(): AppBrowserReaderBlock.Table? {
    val parsedRows = rows.mapNotNull { row ->
        row.cells
            .mapNotNull { cell -> cell.toAppBrowserReaderTableCell() }
            .takeIf(List<*>::isNotEmpty)
            ?.let(::AppBrowserReaderTableRow)
    }
    if (parsedRows.isEmpty()) {
        return null
    }
    return AppBrowserReaderBlock.Table(
        title = titleContent.toAppBrowserTextDocument(),
        rows = parsedRows,
        striped = striped == true,
        bordered = bordered != false,
    )
}

private fun AppBrowserInstantViewJsonTableCell.toAppBrowserReaderTableCell(): AppBrowserReaderTableCell? {
    val document = content.toAppBrowserTextDocument()
        ?: text.toAppBrowserTextDocument()
        ?: return null
    return AppBrowserReaderTableCell(
        content = document,
        isHeader = header == true,
        columnSpan = colspan?.coerceAtLeast(1) ?: 1,
        rowSpan = rowspan?.coerceAtLeast(1) ?: 1,
        alignment = when (align?.trim()?.lowercase()) {
            "center", "middle" -> AppBrowserReaderTableCellAlignment.Center
            "right", "end" -> AppBrowserReaderTableCellAlignment.End
            else -> AppBrowserReaderTableCellAlignment.Start
        },
    )
}

private data class AppBrowserTextEntityRange(
    val start: Int,
    val end: Int,
)

private fun Element.stripAppBrowserReaderNoise() {
    select("script,style,noscript,header,footer,nav,form,button,input,textarea,select,svg").remove()
    select("*").forEach { node ->
        val classAndId = buildString {
            append(node.className())
            append(' ')
            append(node.id())
        }.lowercase()
        val text = node.text().trim().lowercase()
        val isNoiseByClass = classAndId.contains("comment") ||
            classAndId.contains("sidebar") ||
            classAndId.contains("related") ||
            classAndId.contains("promo") ||
            classAndId.contains("newsletter") ||
            classAndId.contains("signup") ||
            classAndId.contains("share") ||
            classAndId.contains("social") ||
            classAndId.contains("discussion")
        val isNoiseByText = text.startsWith("share on ") ||
            text.contains("newsletter signup")
        if (isNoiseByClass || isNoiseByText) {
            node.remove()
        }
    }
}

private fun org.jsoup.nodes.Document.selectAppBrowserReaderRoot(): Element? {
    val semanticRoot = sequenceOf(
        ".topic-post .cooked",
        "article",
        "main",
        "[role=main]",
        ".cooked",
        ".post-stream",
    ).mapNotNull(::selectFirst).firstOrNull()
    if (semanticRoot != null) {
        return semanticRoot
    }

    val scoredRoot = body()
        ?.select("*")
        ?.map { candidate -> candidate to candidate.appBrowserReaderRootScore() }
        ?.maxByOrNull { (_, score) -> score }
        ?.takeIf { (_, score) -> score > 0 }
        ?.first
    return scoredRoot ?: body()
}

private fun Element.appBrowserReaderRootScore(): Int {
    if (tagName() in setOf("aside", "nav", "header", "footer", "form")) {
        return Int.MIN_VALUE
    }

    val classAndId = buildString {
        append(className())
        append(' ')
        append(id())
    }.lowercase()

    if (
        classAndId.contains("comment") ||
        classAndId.contains("sidebar") ||
        classAndId.contains("related") ||
        classAndId.contains("footer") ||
        classAndId.contains("promo")
    ) {
        return -100
    }

    var score = 0
    score += listOf("article", "content", "entry", "post", "body")
        .count(classAndId::contains) * 40

    score += select("> p").sumOf { paragraph ->
        paragraph.text().trim().length.coerceAtMost(220)
    }
    score += select("> div > p").sumOf { paragraph ->
        (paragraph.text().trim().length / 2).coerceAtMost(110)
    }
    score += select("> h1, > h2").size * 30
    score += select("h1, h2").size * 90
    if (select("h1, h2").isNotEmpty() && select("p").size >= 2) {
        score += 180
    }
    score -= children().count { child ->
        child.tagName() in setOf("aside", "nav", "footer")
    } * 40
    return score
}

internal fun extractAppBrowserReaderDocumentFromHtml(
    html: String?,
    pageUrl: String,
): AppBrowserReaderDocument? {
    val rawHtml = html?.trim()?.takeIf { value -> value.isNotBlank() } ?: return null
    val resolvedUrl = pageUrl.toHttpUrlOrNull() ?: return null
    val document = Jsoup.parse(rawHtml, resolvedUrl.toString())
    val host = resolvedUrl.host.lowercase()
    val root = when {
        host == "news.ycombinator.com" && resolvedUrl.encodedPath == "/item" -> null
        else -> document.selectAppBrowserReaderRoot()
    }

    val title = resolveAppBrowserReaderHtmlTitle(
        document = document,
        root = root,
        fallbackHost = resolvedUrl.host,
    )
    val sourceName = document.selectFirst("meta[property=og:site_name], meta[name=application-name]")
        ?.attr("content")
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: resolvedUrl.host
    val excerpt = document.selectFirst("meta[property=og:description], meta[name=description]")
        ?.attr("content")
        ?.trim()
        ?.takeIf(String::isNotBlank)
    val byline = document.selectFirst(
        "meta[name=author], [rel=author], .byline, .author, [itemprop=author]",
    )?.let { node ->
        when {
            node.hasAttr("content") -> node.attr("content")
            else -> node.text()
        }.trim().takeIf(String::isNotBlank)
    }
    val publishedTime = extractAppBrowserReaderPublishedTime(document)
    val heroImageUrl = document.selectFirst("meta[property=og:image], meta[name=twitter:image]")
        ?.attr("content")
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.let { candidate ->
            resolvedUrl.resolve(candidate)?.toString() ?: candidate
        }

    val blocks = if (host == "news.ycombinator.com" && resolvedUrl.encodedPath == "/item") {
        document.select(".commtext")
            .mapNotNull { node ->
                val text = normalizeAppBrowserReaderBodyText(node.text())
                text.takeIf(String::isNotBlank)?.let { normalized ->
                    AppBrowserReaderBlock.Paragraph(
                        content = TextDocument(
                            text = normalized,
                            entities = emptyList(),
                        ),
                    )
                }
            }
    } else {
        root
            ?.clone()
            ?.apply { stripAppBrowserReaderNoise() }
            ?.childNodes()
            ?.let(::parseAppBrowserReaderNodes)
            .orEmpty()
    }

    val normalizedBlocks = blocks.dropLeadingDuplicateReaderHeading(title)
    return normalizedBlocks
        .takeIf(List<*>::isNotEmpty)
        ?.let { parsedBlocks ->
            AppBrowserReaderDocument(
                url = resolvedUrl.toString(),
                title = title,
                sourceName = sourceName,
                excerpt = excerpt,
                byline = byline,
                publishedTime = publishedTime,
                heroImageUrl = heroImageUrl,
                blocks = parsedBlocks,
            )
        }
}

private fun List<AppBrowserReaderBlock>.dropLeadingDuplicateReaderHeading(
    title: String,
): List<AppBrowserReaderBlock> {
    val firstBlock = firstOrNull() as? AppBrowserReaderBlock.Heading ?: return this
    return if (firstBlock.content.text.isReaderTitleDuplicateOf(title)) {
        drop(1)
    } else {
        this
    }
}

private fun List<AppBrowserReaderBlock>.leadingReaderHeadingText(): String? {
    return (firstOrNull() as? AppBrowserReaderBlock.Heading)
        ?.content
        ?.text
        ?.trim()
        ?.takeIf(String::isNotBlank)
}

private fun String.isReaderTitleDuplicateOf(
    title: String,
): Boolean {
    val normalizedText = normalizeReaderTitleDuplicateText(this)
    val normalizedTitle = normalizeReaderTitleDuplicateText(title)
    if (normalizedText.isBlank() || normalizedTitle.isBlank()) {
        return false
    }
    return normalizedText == normalizedTitle ||
        readerTitleSimilarityScore(normalizedTitle, normalizedText) > appBrowserReaderTitleDuplicateThreshold
}

private fun normalizeReaderTitleDuplicateText(
    value: String,
): String {
    return value
        .lowercase()
        .replace('\u00A0', ' ')
        .replace(Regex("""[“”"'"'`]+"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .trim(
            ' ', '.', ',', ':', ';', '!', '?', '-', '–', '—',
            '(', ')', '[', ']', '{', '}',
        )
}

private fun resolveAppBrowserReaderDocumentTitle(
    rawTitle: String?,
    fallbackHost: String,
    headingTitle: String? = null,
): String {
    val normalizedCandidate = rawTitle
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.let(::normalizeAppBrowserReaderDocumentTitleCandidate)
        ?.takeIf(String::isNotBlank)
    return when {
        headingTitle != null &&
            normalizedCandidate != null &&
            normalizedCandidate.equals(fallbackHost, ignoreCase = true) -> headingTitle
        headingTitle != null &&
            normalizedCandidate != null &&
            readerTitleSimilarityScore(normalizedCandidate, headingTitle) > appBrowserReaderTitleDuplicateThreshold -> headingTitle
        normalizedCandidate != null -> normalizedCandidate
        headingTitle != null -> headingTitle
        else -> fallbackHost
    }
}

private fun resolveAppBrowserReaderHtmlTitle(
    document: org.jsoup.nodes.Document,
    root: Element?,
    fallbackHost: String,
): String {
    val headingTitle = root
        ?.selectFirst("h1, h2, h3")
        ?.text()
        ?.trim()
        ?.takeIf(String::isNotBlank)
    val metaTitle = sequenceOf(
        "meta[property=og:title]",
        "meta[name=og:title]",
        "meta[name=twitter:title]",
        "meta[property=twitter:title]",
        "meta[name=title]",
    ).mapNotNull { selector ->
        document.selectFirst(selector)
            ?.attr("content")
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }.firstOrNull()
    val candidateTitle = metaTitle
        ?: document.title()
            .trim()
            .takeIf(String::isNotBlank)
    val normalizedCandidate = candidateTitle
        ?.let(::normalizeAppBrowserReaderDocumentTitleCandidate)
        ?.takeIf(String::isNotBlank)
    val headingCount = root?.select("h1")?.size ?: document.select("h1").size
    return when {
        headingTitle != null &&
            normalizedCandidate != null &&
            readerTitleSimilarityScore(normalizedCandidate, headingTitle) > appBrowserReaderTitleDuplicateThreshold -> headingTitle
        headingTitle != null &&
            metaTitle == null &&
            headingCount == 1 &&
            candidateTitle != null &&
            (candidateTitle.length > 150 || candidateTitle.length < 15) -> headingTitle
        normalizedCandidate != null -> normalizedCandidate
        headingTitle != null -> headingTitle
        else -> fallbackHost
    }
}

private fun normalizeAppBrowserReaderDocumentTitleCandidate(
    rawTitle: String,
): String {
    val normalizedTitle = rawTitle
        .replace('\u00A0', ' ')
        .replace(Regex("""\s+"""), " ")
        .trim()
    if (normalizedTitle.isBlank()) {
        return normalizedTitle
    }
    val separatorMatch = appBrowserReaderTitleSeparatorRegex.find(normalizedTitle)
    if (separatorMatch == null) {
        return normalizedTitle
    }
    val left = normalizedTitle.substring(0, separatorMatch.range.first).trim()
    val right = normalizedTitle.substring(separatorMatch.range.last + 1).trim()
    return when {
        left.readerTitleWordCount() >= 3 -> left
        right.readerTitleWordCount() >= 3 -> right
        else -> normalizedTitle
    }
}

private fun TextDocument.trimAppBrowserReaderWhitespace(): TextDocument {
    val start = text.indexOfFirst { character -> !character.isWhitespace() }
    if (start < 0) {
        return TextDocument(text = "", entities = emptyList())
    }
    val end = text.indexOfLast { character -> !character.isWhitespace() } + 1
    return sliceAppBrowserReaderTextDocument(start, end)
}

private fun TextDocument.sliceAppBrowserReaderTextDocument(
    startIndex: Int,
    endIndex: Int,
): TextDocument {
    val clampedStart = startIndex.coerceIn(0, text.length)
    val clampedEnd = endIndex.coerceIn(clampedStart, text.length)
    return TextDocument(
        text = text.substring(clampedStart, clampedEnd),
        entities = entities.mapNotNull { entity ->
            entity.sliceAppBrowserReaderTextEntity(
                rangeStart = clampedStart,
                rangeEnd = clampedEnd,
            )
        },
    )
}

private fun TextEntity.sliceAppBrowserReaderTextEntity(
    rangeStart: Int,
    rangeEnd: Int,
): TextEntity? {
    val slicedStart = start.coerceIn(rangeStart, rangeEnd)
    val slicedEnd = end.coerceIn(rangeStart, rangeEnd)
    val relativeStart = slicedStart - rangeStart
    val relativeEnd = slicedEnd - rangeStart

    if (start == end) {
        if (start < rangeStart || start > rangeEnd) {
            return null
        }
        return copyAppBrowserReaderTextEntityWithRange(relativeStart, relativeStart)
    }
    if (slicedEnd <= slicedStart) {
        return null
    }
    return copyAppBrowserReaderTextEntityWithRange(relativeStart, relativeEnd)
}

private fun TextEntity.copyAppBrowserReaderTextEntityWithRange(
    start: Int,
    end: Int,
): TextEntity {
    return when (this) {
        is TextEntity.Bold -> copy(start = start, end = end)
        is TextEntity.Italic -> copy(start = start, end = end)
        is TextEntity.Strike -> copy(start = start, end = end)
        is TextEntity.Underline -> copy(start = start, end = end)
        is TextEntity.Highlight -> copy(start = start, end = end)
        is TextEntity.Superscript -> copy(start = start, end = end)
        is TextEntity.Subscript -> copy(start = start, end = end)
        is TextEntity.Small -> copy(start = start, end = end)
        is TextEntity.Kbd -> copy(start = start, end = end)
        is TextEntity.Link -> copy(start = start, end = end)
        is TextEntity.Mention -> copy(start = start, end = end)
        is TextEntity.InlineCode -> copy(start = start, end = end)
        is TextEntity.Spoiler -> copy(start = start, end = end)
        is TextEntity.Emoji -> copy(start = start, end = end)
        is TextEntity.SiteIcon -> copy(start = start, end = end)
    }
}

private fun readerTitleSimilarityScore(
    title: String,
    candidate: String,
): Float {
    val titleTokens = tokenizeReaderTitleSimilarityText(title)
    val candidateTokens = tokenizeReaderTitleSimilarityText(candidate)
    if (titleTokens.isEmpty() || candidateTokens.isEmpty()) {
        return 0f
    }
    val candidateText = candidateTokens.joinToString(separator = " ")
    if (candidateText.isBlank()) {
        return 0f
    }
    val unmatchedLength = candidateTokens
        .filterNot(titleTokens::contains)
        .joinToString(separator = " ")
        .length
    return 1f - (unmatchedLength.toFloat() / candidateText.length.toFloat())
}

private fun tokenizeReaderTitleSimilarityText(
    value: String,
): List<String> {
    return normalizeReaderTitleDuplicateText(value)
        .split(appBrowserReaderTitleTokenizeRegex)
        .filter(String::isNotBlank)
}

private fun String.readerTitleWordCount(): Int {
    return trim()
        .split(Regex("""\s+"""))
        .count(String::isNotBlank)
}

private val appBrowserReaderTitleSeparatorRegex = Regex(""" [\|\-\\/>»] """)
private val appBrowserReaderTitleTokenizeRegex = Regex("""[\s!"#$%&'()*+,./:;<=>?@\[\]\\^_`{|}~-]+""")
private const val appBrowserReaderTitleDuplicateThreshold = 0.75f

private fun extractAppBrowserReaderPublishedTime(
    document: org.jsoup.nodes.Document,
): String? {
    return sequenceOf(
        "meta[property=article:published_time]",
        "meta[name=article:published_time]",
        "meta[property=og:published_time]",
        "meta[name=og:published_time]",
        "meta[name=pubdate]",
        "meta[name=publish-date]",
        "meta[name=publication_date]",
        "meta[name=date]",
        "time[datetime]",
        "[itemprop=datePublished]",
        "[property=datePublished]",
    ).mapNotNull { selector ->
        document.selectFirst(selector)?.let { node ->
            when {
                node.hasAttr("content") -> node.attr("content")
                node.hasAttr("datetime") -> node.attr("datetime")
                else -> node.text()
            }.trim().takeIf(String::isNotBlank)
        }
    }.firstOrNull()
}

private fun parseAppBrowserReaderNodes(
    nodes: List<Node>,
): List<AppBrowserReaderBlock> {
    val blocks = mutableListOf<AppBrowserReaderBlock>()
    val inlineBuffer = mutableListOf<Node>()

    fun flushInlineBuffer() {
        inlineBuffer.toReaderParagraphBlock()?.let(blocks::add)
        inlineBuffer.clear()
    }

    nodes.forEach { node ->
        when (node) {
            is TextNode -> inlineBuffer += node
            is Element -> if (node.isReaderBlockElement()) {
                flushInlineBuffer()
                blocks += parseAppBrowserReaderElement(node)
            } else {
                inlineBuffer += node
            }
            else -> Unit
        }
    }

    flushInlineBuffer()
    return blocks
}

private fun List<Node>.toReaderParagraphBlock(): AppBrowserReaderBlock.Paragraph? {
    if (isEmpty()) {
        return null
    }
    val content = buildTextDocumentFromNodes(this).trimAppBrowserReaderWhitespace()
    return if (content.text.isNotBlank() || content.entities.isNotEmpty()) {
        AppBrowserReaderBlock.Paragraph(content = content)
    } else {
        null
    }
}

private fun Element.isReaderBlockElement(): Boolean {
    return tagName() in setOf(
        "p",
        "h1",
        "h2",
        "h3",
        "h4",
        "h5",
        "h6",
        "blockquote",
        "ul",
        "ol",
        "details",
        "pre",
        "table",
        "img",
        "picture",
        "figure",
        "hr",
        "article",
        "main",
        "section",
        "div",
        "body",
        "aside",
    )
}

private fun parseAppBrowserReaderElement(
    element: Element,
): List<AppBrowserReaderBlock> {
    return when (element.tagName()) {
        "p" -> if (element.containsOnlyReaderInlineImage()) {
            element.toReaderImage()?.let(::listOf).orEmpty()
        } else {
            element.toReaderParagraph()?.let(::listOf).orEmpty()
        }
        "h1" -> element.toReaderHeading(level = 1)?.let(::listOf).orEmpty()
        "h2" -> element.toReaderHeading(level = 2)?.let(::listOf).orEmpty()
        "h3" -> element.toReaderHeading(level = 3)?.let(::listOf).orEmpty()
        "h4" -> element.toReaderHeading(level = 4)?.let(::listOf).orEmpty()
        "h5" -> element.toReaderHeading(level = 5)?.let(::listOf).orEmpty()
        "h6" -> element.toReaderHeading(level = 6)?.let(::listOf).orEmpty()
        "blockquote" -> if (element.isReaderPullQuoteCandidate()) {
            element.toReaderPullQuote()?.let(::listOf)
                ?: element.toReaderQuote()?.let(::listOf)
                ?: emptyList()
        } else {
            element.toReaderQuote()?.let(::listOf).orEmpty()
        }
        "ul" -> element.toReaderList(ordered = false)?.let(::listOf).orEmpty()
        "ol" -> element.toReaderList(ordered = true)?.let(::listOf).orEmpty()
        "details" -> element.toReaderDetails()?.let(::listOf).orEmpty()
        "pre" -> element.toReaderCodeBlock()?.let(::listOf).orEmpty()
        "table" -> element.toReaderTable()?.let(::listOf).orEmpty()
        "img" -> element.toReaderImage()?.let(::listOf).orEmpty()
        "picture" -> element.selectFirst("img")?.toReaderImage()?.let(::listOf).orEmpty()
        "figure" -> element.toReaderFigure()?.let(::listOf).orEmpty()
        "hr" -> listOf(AppBrowserReaderBlock.Divider)
        "article",
        "main",
        "section",
        "div",
        "body",
        "aside" -> parseAppBrowserReaderNodes(element.childNodes())
        else -> element.toReaderParagraph()?.let(::listOf).orEmpty()
    }
}

private fun Element.toReaderParagraph(): AppBrowserReaderBlock.Paragraph? {
    if (containsOnlyReaderInlineImage()) {
        return null
    }
    val content = buildTextDocumentFromNodes(childNodes()).trimAppBrowserReaderWhitespace()
    return if (content.text.isNotBlank() || content.entities.isNotEmpty()) {
        AppBrowserReaderBlock.Paragraph(content = content)
    } else {
        null
    }
}

private fun Element.toReaderHeading(
    level: Int,
): AppBrowserReaderBlock.Heading? {
    val content = buildTextDocumentFromNodes(childNodes()).trimAppBrowserReaderWhitespace()
    return if (content.text.isNotBlank() || content.entities.isNotEmpty()) {
        AppBrowserReaderBlock.Heading(level = level, content = content)
    } else {
        null
    }
}

private fun Element.toReaderQuote(): AppBrowserReaderBlock.Quote? {
    val blocks = parseAppBrowserReaderNodes(childNodes())
    return blocks.takeIf(List<*>::isNotEmpty)?.let { parsed ->
        AppBrowserReaderBlock.Quote(blocks = parsed)
    }
}

private fun Element.isReaderPullQuoteCandidate(): Boolean {
    val classAndId = buildString {
        append(className())
        append(' ')
        append(id())
    }.lowercase()
    if (classAndId.contains("pullquote")) {
        return true
    }
    val contentChildren = children().filterNot { child ->
        child.tagName() in setOf("cite", "footer")
    }
    return contentChildren.isEmpty() ||
        (contentChildren.size == 1 && contentChildren.first().tagName() == "p")
}

private fun Element.toReaderPullQuote(): AppBrowserReaderBlock.PullQuote? {
    val clone = clone()
    val attributionNode = clone.selectFirst("cite, footer")
    val attribution = attributionNode
        ?.let { node -> buildTextDocumentFromNodes(node.childNodes()).trimAppBrowserReaderWhitespace() }
        ?.takeIf { content -> content.text.isNotBlank() || content.entities.isNotEmpty() }
    attributionNode?.remove()
    val content = buildTextDocumentFromNodes(clone.childNodes())
        .trimAppBrowserReaderWhitespace()
        .takeIf { text -> text.text.isNotBlank() || text.entities.isNotEmpty() }
        ?: return null
    return AppBrowserReaderBlock.PullQuote(
        content = content,
        attribution = attribution,
    )
}

private fun Element.toReaderImage(): AppBrowserReaderBlock.Image? {
    val imageElement = when (tagName()) {
        "img" -> this
        else -> selectFirst("img, picture img, a img")
    } ?: this
    val imageUrl = imageElement.absUrl("src")
        .ifBlank { imageElement.attr("src") }
        .ifBlank { imageElement.attr("data-src") }
        .ifBlank { imageElement.attr("data-original") }
        .trim()
        .takeIf(String::isNotBlank)
        ?: return null
    return AppBrowserReaderBlock.Image(
        image = PostBodyImage(
            rawUrl = imageUrl,
            width = imageElement.attr("width").toIntOrNull()?.takeIf { it > 0 },
            height = imageElement.attr("height").toIntOrNull()?.takeIf { it > 0 },
        ),
        alt = imageElement.attr("alt").trim().takeIf(String::isNotBlank),
        caption = null,
        credit = null,
    )
}

private fun Element.containsOnlyReaderInlineImage(): Boolean {
    if (selectFirst("img, picture img, a img") == null) {
        return false
    }
    val clone = clone()
    clone.select("img, picture, source").remove()
    return clone.text().trim().isBlank()
}

private fun Element.toReaderDetails(): AppBrowserReaderBlock.Details? {
    val summaryElement = children().firstOrNull { child ->
        child.tagName() == "summary"
    } ?: return null
    val summary = buildTextDocumentFromNodes(summaryElement.childNodes())
        .trimAppBrowserReaderWhitespace()
        .takeIf { content -> content.text.isNotBlank() || content.entities.isNotEmpty() }
        ?: return null
    val children = parseAppBrowserReaderNodes(
        childNodes().filterNot { node ->
            node is Element && node.tagName() == "summary"
        },
    )
    return AppBrowserReaderBlock.Details(
        summary = summary,
        children = children,
        expandedByDefault = hasAttr("open"),
    )
}

private fun Element.toReaderFigure(): AppBrowserReaderBlock.Image? {
    val imageElement = selectFirst("img") ?: return null
    val baseImage = imageElement.toReaderImage() ?: return null
    val captionClone = selectFirst("figcaption")?.clone()
    val creditNode = captionClone?.selectFirst(".credit, .caption-credit, [data-credit], [itemprop=copyrightNotice]")
    val credit = creditNode
        ?.let { node -> buildTextDocumentFromNodes(node.childNodes()).trimAppBrowserReaderWhitespace() }
        ?.takeIf { content -> content.text.isNotBlank() || content.entities.isNotEmpty() }
    creditNode?.remove()
    val caption = captionClone
        ?.let { captionElement -> buildTextDocumentFromNodes(captionElement.childNodes()).trimAppBrowserReaderWhitespace() }
        ?.takeIf { content -> content.text.isNotBlank() || content.entities.isNotEmpty() }
    return baseImage.copy(
        caption = caption,
        credit = credit,
    )
}

private fun Element.toReaderTable(): AppBrowserReaderBlock.Table? {
    val title = selectFirst("caption")
        ?.let { caption -> buildTextDocumentFromNodes(caption.childNodes()).trimAppBrowserReaderWhitespace() }
        ?.takeIf { content -> content.text.isNotBlank() || content.entities.isNotEmpty() }
    val parsedRows = select("tr").mapNotNull { row ->
        row.children()
            .filter { cell -> cell.tagName() in setOf("th", "td") }
            .mapNotNull { cell -> cell.toReaderTableCell() }
            .takeIf(List<*>::isNotEmpty)
            ?.let(::AppBrowserReaderTableRow)
    }
    if (parsedRows.isEmpty()) {
        return null
    }
    return AppBrowserReaderBlock.Table(
        title = title,
        rows = parsedRows,
        striped = hasClass("striped"),
        bordered = !hasClass("borderless"),
    )
}

private fun Element.toReaderTableCell(): AppBrowserReaderTableCell? {
    val content = buildTextDocumentFromNodes(childNodes())
        .trimAppBrowserReaderWhitespace()
        .takeIf { document -> document.text.isNotBlank() || document.entities.isNotEmpty() }
        ?: return null
    return AppBrowserReaderTableCell(
        content = content,
        isHeader = tagName() == "th",
        columnSpan = attr("colspan").toIntOrNull()?.coerceAtLeast(1) ?: 1,
        rowSpan = attr("rowspan").toIntOrNull()?.coerceAtLeast(1) ?: 1,
        alignment = when {
            hasClass("align-center") || attr("align").equals("center", ignoreCase = true) ->
                AppBrowserReaderTableCellAlignment.Center
            hasClass("align-right") || attr("align").equals("right", ignoreCase = true) ||
                attr("align").equals("end", ignoreCase = true) ->
                AppBrowserReaderTableCellAlignment.End
            else -> AppBrowserReaderTableCellAlignment.Start
        },
    )
}

private fun Element.toReaderList(
    ordered: Boolean,
): AppBrowserReaderBlock.ListBlock? {
    val items = children()
        .filter { child -> child.tagName() == "li" }
        .mapNotNull { child ->
            val blocks = parseAppBrowserReaderNodes(child.childNodes())
                .ifEmpty {
                    child.toReaderParagraph()?.let(::listOf).orEmpty()
                }
            blocks.takeIf(List<*>::isNotEmpty)?.let { parsed ->
                AppBrowserReaderListItem(blocks = parsed)
            }
        }
    return items.takeIf(List<*>::isNotEmpty)?.let { parsedItems ->
        AppBrowserReaderBlock.ListBlock(
            ordered = ordered,
            items = parsedItems,
        )
    }
}

private fun Element.toReaderCodeBlock(): AppBrowserReaderBlock.CodeBlock? {
    val code = wholeText()
        .replace('\u00A0', ' ')
        .trim('\n')
        .trimEnd()
    if (code.isBlank()) {
        return null
    }
    return AppBrowserReaderBlock.CodeBlock(
        code = code,
        languageHint = attr("data-code-wrap").ifBlank {
            selectFirst("code")?.classNames()
                ?.firstOrNull { className -> className.startsWith("language-") }
                ?.removePrefix("language-")
        },
    )
}

private const val appBrowserReaderMinBodyLength = 160
private const val appBrowserReaderMinLineLength = 18
private const val appBrowserReaderMinLineCount = 5
private const val appBrowserVideoCaptureResource = "android.webkit.resource.VIDEO_CAPTURE"
private const val appBrowserAudioCaptureResource = "android.webkit.resource.AUDIO_CAPTURE"

internal fun mergeAppBrowserHistoryEntries(
    backHistoryEntries: List<AppBrowserHistoryEntry>,
    browserHistoryEntries: List<AppBrowserHistoryRecord>,
): List<AppBrowserHistoryEntry> {
    if (browserHistoryEntries.isEmpty()) {
        return emptyList()
    }

    val backEntriesByUrl = LinkedHashMap<String, AppBrowserHistoryEntry>()
    backHistoryEntries.forEach { entry ->
        backEntriesByUrl.putIfAbsent(entry.url, entry)
    }
    return browserHistoryEntries.map { record ->
        val backEntry = backEntriesByUrl[record.url]
        AppBrowserHistoryEntry(
            title = backEntry?.title ?: record.title,
            url = record.url,
            offset = backEntry?.offset,
            host = record.host,
            visitedAtMillis = record.visitedAtMillis,
            favicon = backEntry?.favicon ?: record.historyFavicon(),
        )
    }
}

internal fun buildAppBrowserSearchPanelState(
    isSearching: Boolean,
    query: String,
    activeMatchOrdinal: Int,
    numberOfMatches: Int,
    isCounting: Boolean,
): AppBrowserSearchPanelState {
    if (!isSearching || query.isBlank()) {
        return AppBrowserSearchPanelState(
            visible = false,
            matchText = "",
            canNavigate = false,
        )
    }

    if (isCounting) {
        return AppBrowserSearchPanelState(
            visible = true,
            matchText = "",
            canNavigate = false,
        )
    }

    return if (numberOfMatches == 0) {
        AppBrowserSearchPanelState(
            visible = true,
            matchText = "",
            canNavigate = false,
            showsNoResults = true,
        )
    } else {
        AppBrowserSearchPanelState(
            visible = true,
            matchText = "${activeMatchOrdinal + 1}/$numberOfMatches",
            canNavigate = true,
        )
    }
}

internal fun buildAppBrowserScrollProgress(
    scrollY: Int,
    contentHeightPx: Int,
    viewportHeightPx: Int,
): Float {
    val scrollableHeight = (contentHeightPx - viewportHeightPx).coerceAtLeast(0)
    if (scrollableHeight == 0) {
        return 0f
    }
    return (scrollY.toFloat() / scrollableHeight.toFloat()).coerceIn(0f, 1f)
}

internal fun buildAppBrowserScrollChromeBackgroundState(
    scrollProgress: Float,
    isLoadingVisible: Boolean,
): AppBrowserScrollChromeBackgroundState {
    if (isLoadingVisible) {
        return AppBrowserScrollChromeBackgroundState(
            fillFraction = 0f,
            fillAlpha = 0f,
            dividerAlpha = 0.06f,
        )
    }

    val progress = scrollProgress.coerceIn(0f, 1f)
    return AppBrowserScrollChromeBackgroundState(
        fillFraction = progress,
        fillAlpha = lerpAppBrowserVisual(0f, 0.18f, progress),
        dividerAlpha = lerpAppBrowserVisual(0.06f, 0.12f, progress),
    )
}

private fun lerpAppBrowserVisual(start: Float, end: Float, progress: Float): Float {
    return start + (end - start) * progress.coerceIn(0f, 1f)
}

private const val appBrowserHeaderRestoreThresholdPx = 12
private const val appBrowserHeaderDeltaThresholdPx = 1
private const val appBrowserHeaderMaxStepPx = 24

internal fun AppBrowserHistoryRecord.historyFavicon(): Bitmap? = faviconBase64?.historyFaviconBitmap()

internal fun String.historyFaviconBitmap(): Bitmap? {
    return runCatching {
        val bytes = Base64.decode(this, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

internal fun Bitmap.toHistoryFaviconBase64(): String? {
    return runCatching {
        val scaledBitmap = if (width > appBrowserHistoryFaviconMaxEdgePx || height > appBrowserHistoryFaviconMaxEdgePx) {
            val scale = minOf(
                appBrowserHistoryFaviconMaxEdgePx.toFloat() / width.toFloat(),
                appBrowserHistoryFaviconMaxEdgePx.toFloat() / height.toFloat(),
            )
            Bitmap.createScaledBitmap(
                this,
                (width * scale).toInt().coerceAtLeast(1),
                (height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            this
        }
        ByteArrayOutputStream().use { stream ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                scaledBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, appBrowserHistoryFaviconQuality, stream)
            } else {
                @Suppress("DEPRECATION")
                scaledBitmap.compress(Bitmap.CompressFormat.WEBP, appBrowserHistoryFaviconQuality, stream)
            }
            Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        }
    }.getOrNull()
}

internal fun formatAppBrowserDataSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return if (value == value.toLong().toDouble()) {
        "${value.toLong()} ${units[unitIndex]}"
    } else {
        "${"%.1f".format(value)} ${units[unitIndex]}"
    }
}

internal fun readAppBrowserStorageUsage(
    appDataDir: File,
    appCacheDir: File,
): AppBrowserStorageUsage {
    val webViewDir = File(appDataDir, "app_webview")
    val cacheDir = File(appCacheDir, "WebView")
    val cookiesSize = webViewDir.appBrowserDirectorySize(cookiesOnly = true)
    var cacheSize = webViewDir.appBrowserDirectorySize(cookiesOnly = false)
    cacheSize += cacheDir.appBrowserDirectorySize(cookiesOnly = null)
    val webviewDb = File(appDataDir, "databases/webview.db")
    val webviewCacheDb = File(appDataDir, "databases/webviewCache.db")
    if (webviewDb.exists()) cacheSize += webviewDb.length()
    if (webviewCacheDb.exists()) cacheSize += webviewCacheDb.length()
    return AppBrowserStorageUsage(
        cookiesSizeBytes = cookiesSize,
        cacheSizeBytes = cacheSize,
    )
}

internal fun clearAppBrowserCookieFiles(appDataDir: File) {
    File(appDataDir, "app_webview").deleteAppBrowserDirectory(cookiesOnly = true)
}

internal fun clearAppBrowserCacheFiles(
    appDataDir: File,
    appCacheDir: File,
) {
    File(appDataDir, "databases/webview.db").delete()
    File(appDataDir, "databases/webviewCache.db").delete()
    File(appDataDir, "app_webview").deleteAppBrowserDirectory(cookiesOnly = false)
    File(appCacheDir, "WebView").deleteAppBrowserDirectory(cookiesOnly = null)
}

private fun File.appBrowserDirectorySize(cookiesOnly: Boolean?): Long {
    if (!exists()) return 0L
    if (isDirectory) {
        return listFiles()
            ?.sumOf { child -> child.appBrowserDirectorySize(cookiesOnly) }
            ?: 0L
    }
    if (cookiesOnly != null && isCookieFile() != cookiesOnly) {
        return 0L
    }
    return length()
}

private fun File.deleteAppBrowserDirectory(cookiesOnly: Boolean?): Boolean {
    if (!exists()) return false
    return if (isDirectory) {
        listFiles()?.forEach { child ->
            child.deleteAppBrowserDirectory(cookiesOnly)
        }
        if (cookiesOnly == null || listFiles().isNullOrEmpty()) {
            delete()
        } else {
            false
        }
    } else if (cookiesOnly == null || isCookieFile() == cookiesOnly) {
        delete()
    } else {
        false
    }
}

private fun File.isCookieFile(): Boolean = name.startsWith("Cookies")

private const val appBrowserHistoryFaviconMaxEdgePx = 48
private const val appBrowserHistoryFaviconQuality = 80
private const val appBrowserHistoryMaxRecordCount = 100
private const val appBrowserLoadProgressStart = 0.08f
