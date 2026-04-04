package io.github.baba983.nativedo.core.session

import android.webkit.CookieManager
import io.github.baba983.nativedo.core.log.NativeDoLogTag
import io.github.baba983.nativedo.core.log.debugLog
import io.github.baba983.nativedo.core.storage.CookieStore
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl

open class CookieSyncBridge(
    private val baseUrl: String,
    private val cookieStore: CookieStore,
    private val readCookieHeader: (String) -> String? = { url ->
        CookieManager.getInstance().getCookie(url)
    },
    private val flushCookies: () -> Unit = {
        CookieManager.getInstance().flush()
    },
    private val clearCookies: () -> Unit = {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    },
) {
    private val authenticatedCookieNames = setOf("_t")

    fun sync(url: String, cookies: List<Cookie>) {
        cookieStore.save(url.toHttpUrl(), cookies)
    }

    open fun hasAuthenticatedCookiesInWebView(url: String = baseUrl): Boolean {
        val cookies = parseCookieHeader(
            url = url.toHttpUrl(),
            cookieHeader = readCookieHeader(url),
        )
        val hasAuthenticatedCookies = cookies.any { cookie ->
            cookie.name in authenticatedCookieNames
        }
        debugLog(
            TAG,
            "hasAuthenticatedCookiesInWebView names=${cookies.map { cookie -> cookie.name }.distinct()} result=$hasAuthenticatedCookies",
        )
        return hasAuthenticatedCookies
    }

    open fun syncFromWebView(url: String = baseUrl): Boolean {
        val cookieHeader = readCookieHeader(url)
        debugLog(
            TAG,
            "syncFromWebView url=$url cookies=${cookieHeader.cookieNamesSummary()}",
        )
        val cookies = parseCookieHeader(
            url = url.toHttpUrl(),
            cookieHeader = cookieHeader,
        )
        if (cookies.isEmpty()) {
            debugLog(TAG, "syncFromWebView skipped: no cookies available")
            return false
        }
        sync(url = url, cookies = cookies)
        flushCookies()
        val hasAuthenticatedCookies = cookieStore.hasAuthenticatedCookies()
        debugLog(
            TAG,
            "syncFromWebView synced=${cookies.map { cookie -> cookie.name }.distinct()} hasAuthenticatedCookies=$hasAuthenticatedCookies",
        )
        return hasAuthenticatedCookies
    }

    open fun clear() {
        cookieStore.clear()
        clearCookies()
    }

    internal fun parseCookieHeader(url: okhttp3.HttpUrl, cookieHeader: String?): List<Cookie> =
        cookieHeader
            .orEmpty()
            .split(';')
            .map { segment -> segment.trim() }
            .filter { segment -> segment.contains('=') }
            .map { segment ->
                val name = segment.substringBefore('=').trim()
                val value = segment.substringAfter('=').trim()
                Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain(url.host)
                    .path("/")
                    .apply {
                        if (url.isHttps) {
                            secure()
                        }
                    }
                    .build()
            }

    private fun String?.cookieNamesSummary(): String {
        return this
            .orEmpty()
            .split(';')
            .mapNotNull { segment ->
                segment.substringBefore('=').trim().takeIf { name -> name.isNotBlank() }
            }
            .distinct()
            .takeIf { names -> names.isNotEmpty() }
            ?.joinToString(prefix = "[", postfix = "]")
            ?: "[]"
    }

    private companion object {
        val TAG: NativeDoLogTag = NativeDoLogTag.Session
    }
}
