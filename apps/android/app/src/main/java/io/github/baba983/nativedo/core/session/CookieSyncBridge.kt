package io.github.baba983.nativedo.core.session

import android.webkit.CookieManager
import io.github.baba983.nativedo.core.storage.CookieStore
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class CookieSyncBridge(
    private val baseUrl: String,
    private val cookieStore: CookieStore,
    private val readCookieHeader: (String) -> String? = { url ->
        CookieManager.getInstance().getCookie(url)
    },
    private val flushCookies: () -> Unit = {
        CookieManager.getInstance().flush()
    },
    private val writeCookieToWebView: (String, String) -> Unit = { url, cookie ->
        CookieManager.getInstance().setCookie(url, cookie)
    },
    private val clearCookies: ((() -> Unit)?) -> Unit = { onComplete ->
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            onComplete?.invoke()
        }
    },
) {
    fun syncFromWebView(url: String = baseUrl): Boolean {
        val cookies = parseCookieHeader(
            url = url.toHttpUrl(),
            cookieHeader = readCookieHeader(url),
        )
        cookieStore.replace(url.toHttpUrl(), cookies)
        if (cookies.isNotEmpty()) {
            flushCookies()
        }
        return cookieStore.hasAuthenticatedCookies()
    }

    fun hasAuthenticatedCookiesInWebView(url: String = baseUrl): Boolean {
        return parseCookieHeader(
            url = url.toHttpUrl(),
            cookieHeader = readCookieHeader(url),
        ).any { cookie -> cookie.name == "_t" && cookie.value.isNotBlank() }
    }

    fun syncToWebView(url: String = baseUrl) {
        val cookies = cookieStore.load(url.toHttpUrl())
        if (cookies.isEmpty()) {
            return
        }
        cookies.forEach { cookie ->
            writeCookieToWebView(url, cookie.toString())
        }
        flushCookies()
    }

    fun clear() {
        cookieStore.clear()
        clearCookies(null)
    }

    fun clear(onComplete: () -> Unit) {
        cookieStore.clear()
        clearCookies(onComplete)
    }

    internal fun parseCookieHeader(url: HttpUrl, cookieHeader: String?): List<Cookie> =
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
