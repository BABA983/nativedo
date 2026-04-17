package io.github.baba983.nativedo.feature.browser

import android.graphics.Bitmap
import io.github.baba983.nativedo.core.storage.AppBrowserBookmarkSnapshot
import io.github.baba983.nativedo.core.storage.AppBrowserBookmarkStorePersistence
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class AppBrowserBookmarkRecord(
    val title: String,
    val url: String,
    val host: String,
    val savedAtMillis: Long,
    val faviconBase64: String? = null,
)

class AppBrowserBookmarkStore(
    private val persistence: AppBrowserBookmarkStorePersistence = NoOpAppBrowserBookmarkStorePersistence,
    private val persistenceDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _bookmarks = MutableStateFlow(
        persistence.load().map { snapshot ->
            AppBrowserBookmarkRecord(
                title = snapshot.title,
                url = snapshot.url,
                host = snapshot.host,
                savedAtMillis = snapshot.savedAtMillis,
                faviconBase64 = snapshot.faviconBase64,
            )
        },
    )
    val bookmarks: StateFlow<List<AppBrowserBookmarkRecord>> = _bookmarks.asStateFlow()
    private val persistenceScope = CoroutineScope(SupervisorJob() + persistenceDispatcher)

    fun upsert(
        url: String,
        title: String?,
        savedAtMillis: Long = System.currentTimeMillis(),
        favicon: Bitmap? = null,
    ) {
        val resolvedUrl = normalizeAppBrowserBookmarkUrl(url) ?: return
        val host = resolvedUrl.toHttpUrlOrNull()?.host ?: resolvedUrl
        val resolvedTitle = title?.trim().takeUnless { it.isNullOrBlank() } ?: host
        val existingEntry = _bookmarks.value.firstOrNull { it.url == resolvedUrl }
        _bookmarks.value = (
            listOf(
                AppBrowserBookmarkRecord(
                    title = resolvedTitle,
                    url = resolvedUrl,
                    host = host,
                    savedAtMillis = savedAtMillis,
                    faviconBase64 = favicon?.toHistoryFaviconBase64() ?: existingEntry?.faviconBase64,
                ),
            ) + _bookmarks.value.filterNot { it.url == resolvedUrl }
        ).take(appBrowserBookmarkMaxRecordCount)
        persistBookmarks()
    }

    fun remove(url: String) {
        val resolvedUrl = normalizeAppBrowserBookmarkUrl(url) ?: return
        _bookmarks.value = _bookmarks.value.filterNot { it.url == resolvedUrl }
        persistBookmarks()
    }

    fun toggle(
        url: String,
        title: String?,
        savedAtMillis: Long = System.currentTimeMillis(),
        favicon: Bitmap? = null,
    ): Boolean {
        val resolvedUrl = normalizeAppBrowserBookmarkUrl(url) ?: return false
        return if (_bookmarks.value.any { it.url == resolvedUrl }) {
            remove(resolvedUrl)
            false
        } else {
            upsert(
                url = resolvedUrl,
                title = title,
                savedAtMillis = savedAtMillis,
                favicon = favicon,
            )
            true
        }
    }

    private object NoOpAppBrowserBookmarkStorePersistence : AppBrowserBookmarkStorePersistence {
        override fun load(): List<AppBrowserBookmarkSnapshot> = emptyList()

        override fun save(records: List<AppBrowserBookmarkSnapshot>) = Unit
    }

    private fun persistBookmarks() {
        val snapshots = _bookmarks.value.toSnapshots()
        persistenceScope.launch {
            persistence.save(snapshots)
        }
    }
}

internal fun normalizeAppBrowserBookmarkUrl(url: String): String? {
    val trimmedUrl = url.trim()
    if (trimmedUrl.isBlank()) {
        return null
    }
    return trimmedUrl
        .toHttpUrlOrNull()
        ?.newBuilder()
        ?.fragment(null)
        ?.build()
        ?.toString()
        ?: trimmedUrl
}

private fun List<AppBrowserBookmarkRecord>.toSnapshots(): List<AppBrowserBookmarkSnapshot> {
    return map { record ->
        AppBrowserBookmarkSnapshot(
            title = record.title,
            url = record.url,
            host = record.host,
            savedAtMillis = record.savedAtMillis,
            faviconBase64 = record.faviconBase64,
        )
    }
}

private const val appBrowserBookmarkMaxRecordCount = 200
