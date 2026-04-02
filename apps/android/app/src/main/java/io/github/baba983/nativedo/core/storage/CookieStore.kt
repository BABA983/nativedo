package io.github.baba983.nativedo.core.storage

import okhttp3.Cookie
import okhttp3.HttpUrl

class CookieStore {
    private val sessionCookieNames = setOf("_t", "_forum_session")
    private var cookies: Map<String, List<Cookie>> = emptyMap()

    fun hasSessionCookies(): Boolean = cookies.values
        .flatten()
        .filterNotExpired()
        .any { it.name in sessionCookieNames }

    fun load(url: HttpUrl): List<Cookie> {
        val validCookies = cookies[url.host]
            .orEmpty()
            .filterNotExpired()
            .filter { cookie -> cookie.matches(url) }
        if (validCookies.size != cookies[url.host].orEmpty().size) {
            cookies = cookies + (url.host to validCookies)
        }
        return validCookies
    }

    fun save(url: HttpUrl, cookies: List<Cookie>) {
        val mergedCookies = LinkedHashMap<String, Cookie>()
        load(url).forEach { cookie ->
            mergedCookies[cookie.storageKey()] = cookie
        }
        cookies.filterNotExpired().forEach { cookie ->
            mergedCookies[cookie.storageKey()] = cookie
        }
        this.cookies = this.cookies + (url.host to mergedCookies.values.toList())
    }

    fun clear() {
        cookies = emptyMap()
    }

    private fun List<Cookie>.filterNotExpired(): List<Cookie> {
        val now = System.currentTimeMillis()
        return filter { cookie -> cookie.expiresAt > now }
    }

    private fun Cookie.storageKey(): String = "$name|$domain|$path"
}
