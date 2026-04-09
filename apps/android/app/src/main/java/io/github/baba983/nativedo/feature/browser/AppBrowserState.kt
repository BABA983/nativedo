package io.github.baba983.nativedo.feature.browser

import io.github.baba983.nativedo.core.storage.AppBrowserHistorySnapshot
import io.github.baba983.nativedo.core.storage.AppBrowserHistoryStorePersistence
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
) {
    private val _history = MutableStateFlow(
        persistence.load().map { snapshot ->
            AppBrowserHistoryRecord(
                title = snapshot.title,
                url = snapshot.url,
                host = snapshot.host,
                visitedAtMillis = snapshot.visitedAtMillis,
            )
        },
    )
    val history: StateFlow<List<AppBrowserHistoryRecord>> = _history.asStateFlow()

    fun recordVisit(
        url: String,
        title: String?,
        visitedAtMillis: Long = System.currentTimeMillis(),
    ) {
        if (url.isBlank()) {
            return
        }
        val host = url.toHttpUrlOrNull()?.host ?: url
        val resolvedTitle = title?.trim().takeUnless { it.isNullOrBlank() } ?: host
        val entry = AppBrowserHistoryRecord(
            title = resolvedTitle,
            url = url,
            host = host,
            visitedAtMillis = visitedAtMillis,
        )
        _history.value = listOf(entry) + _history.value.filterNot { it.url == url }
        persistence.save(
            _history.value.map { record ->
                AppBrowserHistorySnapshot(
                    title = record.title,
                    url = record.url,
                    host = record.host,
                    visitedAtMillis = record.visitedAtMillis,
                )
            },
        )
    }

    fun clear() {
        _history.value = emptyList()
        persistence.save(emptyList())
    }

    private object NoOpAppBrowserHistoryStorePersistence : AppBrowserHistoryStorePersistence {
        override fun load(): List<AppBrowserHistorySnapshot> = emptyList()

        override fun save(records: List<AppBrowserHistorySnapshot>) = Unit
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
