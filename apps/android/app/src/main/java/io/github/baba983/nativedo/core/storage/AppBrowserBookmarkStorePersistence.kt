package io.github.baba983.nativedo.core.storage

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class AppBrowserBookmarkSnapshot(
    val title: String,
    val url: String,
    val host: String,
    val savedAtMillis: Long,
    val faviconBase64: String? = null,
)

interface AppBrowserBookmarkStorePersistence {
    fun load(): List<AppBrowserBookmarkSnapshot>

    fun save(records: List<AppBrowserBookmarkSnapshot>)
}

class SharedPreferencesAppBrowserBookmarkStorePersistence(
    private val sharedPreferences: SharedPreferences,
) : AppBrowserBookmarkStorePersistence {
    override fun load(): List<AppBrowserBookmarkSnapshot> {
        val raw = sharedPreferences.getString(KEY_BOOKMARKS, null).orEmpty()
        if (raw.isBlank()) {
            return emptyList()
        }
        return runCatching {
            json.decodeFromString(ListSerializer(AppBrowserBookmarkSnapshot.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    override fun save(records: List<AppBrowserBookmarkSnapshot>) {
        sharedPreferences.edit()
            .putString(
                KEY_BOOKMARKS,
                json.encodeToString(ListSerializer(AppBrowserBookmarkSnapshot.serializer()), records),
            )
            .apply()
    }

    private companion object {
        const val KEY_BOOKMARKS: String = "app_browser_bookmarks"
        val json: Json = Json
    }
}
