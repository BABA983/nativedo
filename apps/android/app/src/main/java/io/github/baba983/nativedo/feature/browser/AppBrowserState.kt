package io.github.baba983.nativedo.feature.browser

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import io.github.baba983.nativedo.core.storage.AppBrowserHistorySnapshot
import io.github.baba983.nativedo.core.storage.AppBrowserHistoryStorePersistence
import java.io.ByteArrayOutputStream
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

    init {
        persistenceScope.launch {
            for (mutation in mutations) {
                when (mutation) {
                    is HistoryMutation.RecordVisit -> {
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

                    HistoryMutation.Clear -> {
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
                url = url,
                title = title,
                visitedAtMillis = visitedAtMillis,
                favicon = favicon,
            ),
        )
    }

    fun clear() {
        mutations.trySend(HistoryMutation.Clear)
    }

    private object NoOpAppBrowserHistoryStorePersistence : AppBrowserHistoryStorePersistence {
        override fun load(): List<AppBrowserHistorySnapshot> = emptyList()

        override fun save(records: List<AppBrowserHistorySnapshot>) = Unit
    }
}

private sealed interface HistoryMutation {
    data class RecordVisit(
        val url: String,
        val title: String?,
        val visitedAtMillis: Long,
        val favicon: Bitmap?,
    ) : HistoryMutation

    data object Clear : HistoryMutation
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

private const val appBrowserHistoryFaviconMaxEdgePx = 48
private const val appBrowserHistoryFaviconQuality = 80
private const val appBrowserHistoryMaxRecordCount = 100
