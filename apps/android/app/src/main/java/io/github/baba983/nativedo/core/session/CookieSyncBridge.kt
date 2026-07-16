package io.github.baba983.nativedo.core.session

import android.webkit.CookieManager
import io.github.baba983.nativedo.core.storage.CookieStore
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

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
    private val writeCookieToWebViewAsync: (String, String, (Boolean) -> Unit) -> Unit = { url, cookie, onComplete ->
        CookieManager.getInstance().setCookie(url, cookie, onComplete)
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

    suspend fun syncToWebViewAsync(
        url: String = baseUrl,
        timeoutMillis: Long = DEFAULT_ASYNC_COOKIE_TIMEOUT_MILLIS,
    ): Boolean {
        val cookies = cookieStore.load(url.toHttpUrl())
        if (cookies.isEmpty()) {
            return true
        }
        return withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                var remaining = cookies.size
                var allSucceeded = true
                var resumed = false
                cookies.forEach { cookie ->
                    writeCookieToWebViewAsync(url, cookie.toString()) { succeeded ->
                        allSucceeded = allSucceeded && succeeded
                        remaining -= 1
                        if (!resumed && remaining == 0) {
                            resumed = true
                            if (allSucceeded) {
                                flushCookies()
                            }
                            if (continuation.isActive) continuation.resume(allSucceeded)
                        }
                    }
                }
            }
        } ?: false
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

    private companion object {
        private const val DEFAULT_ASYNC_COOKIE_TIMEOUT_MILLIS = 5_000L
    }
}
