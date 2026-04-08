package io.github.baba983.nativedo.core.storage

import android.content.SharedPreferences
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AppBrowserHistorySnapshot(
    val title: String,
    val url: String,
    val host: String,
    val visitedAtMillis: Long,
)

interface AppBrowserHistoryStorePersistence {
    fun load(): List<AppBrowserHistorySnapshot>

    fun save(records: List<AppBrowserHistorySnapshot>)
}

class SharedPreferencesAppBrowserHistoryStorePersistence(
    private val sharedPreferences: SharedPreferences,
) : AppBrowserHistoryStorePersistence {
    override fun load(): List<AppBrowserHistorySnapshot> {
        val raw = sharedPreferences.getString(KEY_HISTORY, null).orEmpty()
        if (raw.isBlank()) {
            return emptyList()
        }
        return runCatching {
            json.decodeFromString(ListSerializer(AppBrowserHistorySnapshot.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    override fun save(records: List<AppBrowserHistorySnapshot>) {
        sharedPreferences.edit()
            .putString(
                KEY_HISTORY,
                json.encodeToString(ListSerializer(AppBrowserHistorySnapshot.serializer()), records),
            )
            .apply()
    }

    private companion object {
        const val KEY_HISTORY: String = "app_browser_history"
        val json: Json = Json
    }
}
