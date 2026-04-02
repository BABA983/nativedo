package io.github.baba983.nativedo.core.session

import android.webkit.CookieManager
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
    fun sync(url: String, cookies: List<Cookie>) {
        cookieStore.save(url.toHttpUrl(), cookies)
    }

    open fun syncFromWebView(): Boolean {
        val cookies = parseCookieHeader(
            url = baseUrl.toHttpUrl(),
            cookieHeader = readCookieHeader(baseUrl),
        )
        if (cookies.isEmpty()) {
            return false
        }
        sync(url = baseUrl, cookies = cookies)
        flushCookies()
        return cookieStore.hasSessionCookies()
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
}
