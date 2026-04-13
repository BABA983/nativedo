package io.github.baba983.nativedo.feature.browser

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import io.github.baba983.nativedo.core.storage.AppBrowserHistorySnapshot
import io.github.baba983.nativedo.core.storage.AppBrowserHistoryStorePersistence
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

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
    val alpha: Float,
    val backdropAlpha: Float,
)

internal data class AppBrowserSwipeSettle(
    val shouldDismiss: Boolean,
    val targetOffsetPx: Float,
    val durationMillis: Int,
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

    fun advanceFakeTick(): AppBrowserLoadProgressState {
        if (!isLoading) return this
        val cap = maxOf(actualProgress, appBrowserLoadProgressFakeCap)
        if (displayedProgress >= cap) return this
        val tick = if (cap - displayedProgress < 0.25f) {
            appBrowserLoadProgressSlowTick
        } else {
            appBrowserLoadProgressFastTick
        }
        return copy(displayedProgress = minOf(displayedProgress + tick, cap))
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

internal fun buildAppBrowserRoutePresentationState(
    offsetPx: Float,
    screenWidthPx: Float,
): AppBrowserRoutePresentationState {
    val safeScreenWidthPx = screenWidthPx.takeIf { it > 0f } ?: 1f
    val clampedOffset = offsetPx.coerceIn(0f, safeScreenWidthPx)
    val progress = 1f - (clampedOffset / safeScreenWidthPx)
    return AppBrowserRoutePresentationState(
        progress = progress,
        alpha = 0.92f + (progress * 0.08f),
        backdropAlpha = (1f - progress) * 0.32f,
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
): List<AppBrowserMenuEntry> {
    return listOf(
        AppBrowserMenuEntry.Item(
            AppBrowserMenuItem(
                action = AppBrowserMenuAction.Forward,
                enabled = canGoForward,
            ),
        ),
        AppBrowserMenuEntry.Item(AppBrowserMenuItem(action = AppBrowserMenuAction.Refresh)),
        AppBrowserMenuEntry.Divider,
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
    )
}

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
): AppBrowserSearchPanelState {
    if (!isSearching || query.isBlank()) {
        return AppBrowserSearchPanelState(
            visible = false,
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
            dividerAlpha = 0.16f,
        )
    }

    val progress = scrollProgress.coerceIn(0f, 1f)
    return AppBrowserScrollChromeBackgroundState(
        fillFraction = progress,
        fillAlpha = lerpAppBrowserVisual(0f, 0.18f, progress),
        dividerAlpha = lerpAppBrowserVisual(0.16f, 0.24f, progress),
    )
}

private fun lerpAppBrowserVisual(start: Float, end: Float, progress: Float): Float {
    return start + (end - start) * progress.coerceIn(0f, 1f)
}

internal fun AppBrowserHistoryRecord.historyFavicon(): Bitmap? = faviconBase64?.historyFaviconBitmap()

internal fun String.historyFaviconBitmap(): Bitmap? {
    return runCatching {
        val bytes = Base64.decode(this, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

private fun Bitmap.toHistoryFaviconBase64(): String? {
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
private const val appBrowserLoadProgressStart = 0.3f
private const val appBrowserLoadProgressFakeCap = 0.7f
private const val appBrowserLoadProgressFastTick = 0.02f
private const val appBrowserLoadProgressSlowTick = 0.01f
