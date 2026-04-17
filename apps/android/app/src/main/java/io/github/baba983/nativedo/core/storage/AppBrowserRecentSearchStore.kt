package io.github.baba983.nativedo.core.storage

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
internal data class AppBrowserRecentSearchSnapshot(
    val query: String,
    val searchedAtMillis: Long,
)

internal data class AppBrowserRecentSearchRecord(
    val query: String,
    val searchedAtMillis: Long,
)

internal interface AppBrowserRecentSearchStorePersistence {
    fun load(): List<AppBrowserRecentSearchSnapshot>

    fun save(records: List<AppBrowserRecentSearchSnapshot>)
}

internal class SharedPreferencesAppBrowserRecentSearchStorePersistence(
    private val sharedPreferences: SharedPreferences,
) : AppBrowserRecentSearchStorePersistence {
    override fun load(): List<AppBrowserRecentSearchSnapshot> {
        val raw = sharedPreferences.getString(KEY_RECENT_SEARCHES, null).orEmpty()
        if (raw.isBlank()) {
            return emptyList()
        }
        return runCatching {
            json.decodeFromString(ListSerializer(AppBrowserRecentSearchSnapshot.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    override fun save(records: List<AppBrowserRecentSearchSnapshot>) {
        sharedPreferences.edit()
            .putString(
                KEY_RECENT_SEARCHES,
                json.encodeToString(ListSerializer(AppBrowserRecentSearchSnapshot.serializer()), records),
            )
            .apply()
    }

    private companion object {
        const val KEY_RECENT_SEARCHES: String = "app_browser_recent_searches"
        val json: Json = Json
    }
}

internal class AppBrowserRecentSearchStore(
    private val persistence: AppBrowserRecentSearchStorePersistence = NoOpAppBrowserRecentSearchStorePersistence,
    private val persistenceDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _recentSearches = MutableStateFlow(
        persistence.load().map { snapshot ->
            AppBrowserRecentSearchRecord(
                query = snapshot.query,
                searchedAtMillis = snapshot.searchedAtMillis,
            )
        },
    )
    val recentSearches: StateFlow<List<AppBrowserRecentSearchRecord>> = _recentSearches.asStateFlow()
    private val persistenceScope = CoroutineScope(SupervisorJob() + persistenceDispatcher)

    fun recordSearch(
        query: String,
        searchedAtMillis: Long = System.currentTimeMillis(),
    ) {
        val normalizedQuery = normalizeAppBrowserRecentSearchQuery(query) ?: return
        _recentSearches.value = (
            listOf(
                AppBrowserRecentSearchRecord(
                    query = normalizedQuery,
                    searchedAtMillis = searchedAtMillis,
                ),
            ) + _recentSearches.value.filterNot { existing ->
                existing.query.equals(normalizedQuery, ignoreCase = true)
            }
        ).take(appBrowserRecentSearchMaxRecordCount)
        persist()
    }

    private fun persist() {
        val snapshots = _recentSearches.value.map { record ->
            AppBrowserRecentSearchSnapshot(
                query = record.query,
                searchedAtMillis = record.searchedAtMillis,
            )
        }
        persistenceScope.launch {
            persistence.save(snapshots)
        }
    }

    private object NoOpAppBrowserRecentSearchStorePersistence : AppBrowserRecentSearchStorePersistence {
        override fun load(): List<AppBrowserRecentSearchSnapshot> = emptyList()

        override fun save(records: List<AppBrowserRecentSearchSnapshot>) = Unit
    }
}

internal fun normalizeAppBrowserRecentSearchQuery(query: String): String? {
    return query.trim().takeIf(String::isNotBlank)
}

private const val appBrowserRecentSearchMaxRecordCount = 24
