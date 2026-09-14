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

    fun flush() = persistence.flush()

    private var cookies: Map<String, List<Cookie>> = loadPersistedCookies()

    private var revision = 0L

    @Synchronized
    fun revision(): Long = revision

    @Synchronized
    fun requestSnapshot(url: HttpUrl): Pair<Long, List<Cookie>> = revision to load(url)

    @Synchronized
    fun saveIfCurrent(url: HttpUrl, cookies: List<Cookie>, expectedRevision: Long): Boolean {
        if (revision != expectedRevision) return false
        save(url, cookies)
        return true
    }

    // Login/logout must fence in-flight responses even if the token did not change.
    @Synchronized
    fun advanceRevision() { revision += 1 }

    @Synchronized
    fun hasAuthenticatedCookies(): Boolean = allCookies()
        .filterNotExpired()
        .any { it.name == "_t" && it.value.isNotBlank() }

    @Synchronized
    fun load(url: HttpUrl): List<Cookie> {
        val compactedCookies = compactCookies()
        val validCookies = compactedCookies.filter { cookie -> cookie.matches(url) }
        if (compactedCookies.size != allCookies().size) {
            cookies = rebuildBuckets(compactedCookies)
            persistCookies()
        }
        return validCookies
    }

    @Synchronized
    fun save(url: HttpUrl, cookies: List<Cookie>) {
        val previousAuth = authCookies()
        val mergedCookies = LinkedHashMap<String, Cookie>()
        compactCookies().forEach { cookie ->
            mergedCookies[cookie.storageKey()] = cookie
        }
        val now = System.currentTimeMillis()
        cookies.forEach { cookie ->
            val key = cookie.storageKey()
            if (cookie.expiresAt <= now) {
                // OkHttp passes Max-Age=0/expired Set-Cookie values here.
                // Keeping the previous value would resurrect a revoked _t
                // token on the next request.
                mergedCookies.remove(key)
            } else {
                mergedCookies[key] = cookie
            }
        }
        this.cookies = rebuildBuckets(mergedCookies.values.toList())
        if (previousAuth != authCookies()) revision += 1
        persistCookies()
    }

    private fun authCookies() = allCookies().filter { it.name == "_t" }
        .associate { it.storageKey() to it.value }

    @Synchronized
    fun clear() {
        revision += 1
        cookies = emptyMap()
        persistCookies()
    }

    private fun loadPersistedCookies(): Map<String, List<Cookie>> {
        return persistence.load()
            .mapValues { (host, rawCookies) ->
                val hostUrl = "https://${host.removePrefix(".")}/".toHttpUrl()
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

    private fun Cookie.storageKey(): String = "$name|${domain.removePrefix(".")}|$path"
}

interface CookieStorePersistence {
    fun flush() = Unit

    fun load(): Map<String, List<String>>

    fun save(snapshot: Map<String, List<String>>)
}

class SharedPreferencesCookieStorePersistence(
    private val sharedPreferences: SharedPreferences,
) : CookieStorePersistence {
    override fun flush() {
        check(sharedPreferences.edit().commit()) { "Unable to persist session" }
    }

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
