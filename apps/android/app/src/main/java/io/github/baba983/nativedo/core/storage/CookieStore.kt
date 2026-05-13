package io.github.baba983.nativedo.core.storage

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class CookieStore(
    private val persistence: CookieStorePersistence,
) {
    constructor() : this(persistence = NoOpCookieStorePersistence())

    private var cookies: Map<String, List<Cookie>> = loadPersistedCookies()

    fun hasAuthenticatedCookies(): Boolean = allCookies()
        .filterNotExpired()
        .any { it.name == "_t" && it.value.isNotBlank() }

    fun load(url: HttpUrl): List<Cookie> {
        val compactedCookies = compactCookies()
        val validCookies = compactedCookies.filter { cookie -> cookie.matches(url) }
        if (compactedCookies.size != allCookies().size) {
            cookies = rebuildBuckets(compactedCookies)
            persistCookies()
        }
        return validCookies
    }

    fun save(url: HttpUrl, cookies: List<Cookie>) {
        val mergedCookies = LinkedHashMap<String, Cookie>()
        compactCookies().forEach { cookie ->
            mergedCookies[cookie.storageKey()] = cookie
        }
        cookies.filterNotExpired().forEach { cookie ->
            mergedCookies[cookie.storageKey()] = cookie
        }
        this.cookies = rebuildBuckets(mergedCookies.values.toList())
        persistCookies()
    }

    fun replace(url: HttpUrl, cookies: List<Cookie>) {
        val retainedCookies = compactCookies().filterNot { cookie ->
            cookie.domain.removePrefix(".") == url.host
        }
        this.cookies = rebuildBuckets(retainedCookies + cookies.filterNotExpired())
        persistCookies()
    }

    fun clear() {
        cookies = emptyMap()
        persistCookies()
    }

    private fun loadPersistedCookies(): Map<String, List<Cookie>> {
        return persistence.load()
            .mapValues { (host, rawCookies) ->
                val hostUrl = "https://$host/".toHttpUrl()
                rawCookies.mapNotNull { rawCookie ->
                    Cookie.parse(hostUrl, rawCookie)
                }.filterNotExpired()
            }
            .filterValues { cookies -> cookies.isNotEmpty() }
    }

    private fun persistCookies() {
        persistence.save(
            snapshot = cookies
                .mapValues { (_, cookies) ->
                    cookies.filterNotExpired().map(Cookie::toString)
                }
                .filterValues { cookies -> cookies.isNotEmpty() },
        )
    }

    private fun compactCookies(): List<Cookie> {
        val compactedCookies = LinkedHashMap<String, Cookie>()
        allCookies()
            .filterNotExpired()
            .forEach { cookie ->
                compactedCookies[cookie.storageKey()] = cookie
            }
        return compactedCookies.values.toList()
    }

    private fun allCookies(): List<Cookie> = cookies.values.flatten()

    private fun rebuildBuckets(cookies: List<Cookie>): Map<String, List<Cookie>> {
        return cookies.groupBy { cookie -> cookie.domain.removePrefix(".") }
    }

    private fun List<Cookie>.filterNotExpired(): List<Cookie> {
        val now = System.currentTimeMillis()
        return filter { cookie -> cookie.expiresAt > now }
    }

    private fun Cookie.storageKey(): String = "$name|$domain|$path"
}

interface CookieStorePersistence {
    fun load(): Map<String, List<String>>

    fun save(snapshot: Map<String, List<String>>)
}

class SharedPreferencesCookieStorePersistence(
    private val sharedPreferences: SharedPreferences,
) : CookieStorePersistence {
    override fun load(): Map<String, List<String>> {
        val snapshot = sharedPreferences.getString(KEY_SNAPSHOT, null).orEmpty()
        if (snapshot.isBlank()) {
            return emptyMap()
        }
        return runCatching {
            json.decodeFromString<CookieStoreSnapshot>(snapshot).cookies
        }.getOrDefault(emptyMap())
    }

    override fun save(snapshot: Map<String, List<String>>) {
        val encoded = json.encodeToString(CookieStoreSnapshot(cookies = snapshot))
        sharedPreferences.edit().putString(KEY_SNAPSHOT, encoded).apply()
    }

    @Serializable
    private data class CookieStoreSnapshot(
        val cookies: Map<String, List<String>> = emptyMap(),
    )

    private companion object {
        const val KEY_SNAPSHOT: String = "cookie_snapshot"
        val json: Json = Json
    }
}

private class NoOpCookieStorePersistence : CookieStorePersistence {
    override fun load(): Map<String, List<String>> = emptyMap()

    override fun save(snapshot: Map<String, List<String>>) = Unit
}
