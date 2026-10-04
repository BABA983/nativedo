package io.github.baba983.nativedo.core.session

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * One-time import of the retired native cookie snapshot (`nativedo.cookie_store.xml`) into the
 * shared jar.
 *
 * <p>The snapshot was the old second copy of the session; with `CookieManager` as the single
 * store it must not keep participating. It is read exactly once, when the shared jar has nothing
 * for the site, and then deleted - whether or not anything was imported. When the jar already
 * holds cookies (the WebView's copy is the newer one by construction), the snapshot is discarded
 * rather than allowed to overwrite a live rotating `_t`.</p>
 */
internal object LegacyCookieMigration {

    @Serializable
    private data class LegacyCookieSnapshot(
        val cookies: Map<String, List<String>> = emptyMap(),
    )

    /**
     * @return true when legacy cookies were written into [jar]; the preferences are cleared either
     * way.
     */
    fun migrate(preferences: SharedPreferences, jar: SharedCookieJar): Boolean {
        val encoded = preferences.getString(KEY_SNAPSHOT, null)
        if (encoded.isNullOrBlank()) return false
        val imported = runCatching {
            if (jar.hasCookiesForSite()) {
                // The jar already has the reader's session; a snapshot from the old store would
                // be an older copy of a rotating token.
                false
            } else {
                val snapshot = json.decodeFromString<LegacyCookieSnapshot>(encoded)
                var written = 0
                snapshot.cookies.forEach { (host, rawCookies) ->
                    val url = "https://${host.removePrefix(".")}/".toHttpUrlOrNull() ?: return@forEach
                    rawCookies.forEach { rawCookie ->
                        if (jar.saveSetCookie(url, rawCookie)) written += 1
                    }
                }
                written > 0
            }
        }.getOrDefault(false)
        preferences.edit().clear().apply()
        SessionDiagnostics.record(
            event = "legacy_cookie_migration",
            revision = jar.revision(),
            accepted = imported,
        )
        return imported
    }

    private const val KEY_SNAPSHOT: String = "cookie_snapshot"
    private val json: Json = Json
}
