package io.github.baba983.nativedo.core.session

import android.webkit.CookieManager
import io.github.baba983.nativedo.core.storage.CookieStore
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
    private val writeCookieToWebViewAsync: (String, String, (Boolean) -> Unit) -> Unit = { url, cookie, onComplete ->
        CookieManager.getInstance().setCookie(url, cookie, onComplete)
    },
) {
    private val operationMutex = Mutex()
    private val pendingClear = AtomicReference<CompletableDeferred<Unit>?>(null)

    suspend fun syncFromWebView(url: String = baseUrl): Boolean = operationMutex.withLock {
        awaitPendingClear()
        syncFromWebViewFiltered(url) { true }
    }

    suspend fun syncFromWebViewByName(
        url: String = baseUrl,
        names: Set<String>,
    ): Boolean = operationMutex.withLock {
        awaitPendingClear()
        syncFromWebViewFiltered(url) { it.name in names }
    }

    suspend fun syncFromWebViewExcluding(
        url: String = baseUrl,
        excludeNames: Set<String>,
    ): Boolean = operationMutex.withLock {
        awaitPendingClear()
        syncFromWebViewFiltered(url) { it.name !in excludeNames }
    }

    private fun syncFromWebViewFiltered(url: String, predicate: (Cookie) -> Boolean): Boolean {
        val cookieUrl = url.toHttpUrl()
        val cookieHeader = readCookieHeader(url)
        val baseHeader = if (url == baseUrl) null else readCookieHeader(baseUrl)
        // CookieManager can briefly return null/blank while its Chromium store is
        // being opened/flushed. Treat that as unavailable, not as logout.
        val cookies = (parseCookieHeader(cookieUrl, cookieHeader) +
            parseCookieHeader(baseUrl.toHttpUrl(), baseHeader))
            .distinctBy { cookie -> "${cookie.name}|${cookie.domain}|${cookie.path}" }
            .filter(predicate)
        if (cookies.isEmpty()) return cookieStore.hasAuthenticatedCookies()
        cookieStore.save(baseUrl.toHttpUrl(), cookies)
        flushCookies()
        return cookieStore.hasAuthenticatedCookies()
    }

    fun hasAuthenticatedCookiesInWebView(url: String = baseUrl): Boolean =
        parseCookieHeader(
            url = url.toHttpUrl(),
            cookieHeader = readCookieHeader(url),
        ).any { cookie -> cookie.name == "_t" && cookie.value.isNotBlank() }

    suspend fun syncToWebView(url: String = baseUrl) = operationMutex.withLock {
        awaitPendingClear()
        syncToWebViewNow(url)
    }

    private fun syncToWebViewNow(url: String) {
        val cookies = cookieStore.load(url.toHttpUrl())
        if (cookies.isEmpty()) return
        cookies.forEach { cookie -> writeCookieToWebView(url, cookie.toString()) }
        flushCookies()
    }

    suspend fun syncToWebViewAsync(
        url: String = baseUrl,
        timeoutMillis: Long = DEFAULT_ASYNC_COOKIE_TIMEOUT_MILLIS,
    ): Boolean = operationMutex.withLock {
        awaitPendingClear()
        val cookies = cookieStore.load(url.toHttpUrl())
        if (cookies.isEmpty()) return@withLock true
        withTimeoutOrNull(timeoutMillis) {
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
                            if (allSucceeded) flushCookies()
                            if (continuation.isActive) continuation.resume(allSucceeded)
                        }
                    }
                }
            }
        } ?: false
    }
    suspend fun clearAsync(timeoutMillis: Long = DEFAULT_ASYNC_COOKIE_TIMEOUT_MILLIS) {
        withContext(NonCancellable) {
            operationMutex.withLock {
                awaitPendingClear()
                cookieStore.clear()
                val completion = CompletableDeferred<Unit>()
                pendingClear.set(completion)
                clearCookies { completion.complete(Unit) }
                withTimeoutOrNull(timeoutMillis) { completion.await() }
                // If the WebView callback timed out, retain the deferred as a
                // barrier. Future login/recovery operations wait for the old
                // deletion instead of racing it with a new cookie write.
                if (completion.isCompleted) {
                    pendingClear.compareAndSet(completion, null)
                }
            }
        }
    }

    private suspend fun awaitPendingClear() {
        val completion = pendingClear.get() ?: return
        // The deletion callback normally completes the barrier. If it never
        // fires, drop the barrier after the timeout instead of wedging every
        // future cookie operation on a callback that may never arrive.
        withTimeoutOrNull(DEFAULT_ASYNC_COOKIE_TIMEOUT_MILLIS) { completion.await() }
        pendingClear.compareAndSet(completion, null)
    }

    internal fun parseCookieHeader(url: HttpUrl, cookieHeader: String?): List<Cookie> =
        cookieHeader
            .orEmpty()
            .split(';')
            .map { segment -> segment.trim() }
            .filter { segment -> segment.contains('=') }
            .mapNotNull { segment ->
                val name = segment.substringBefore('=').trim()
                val value = segment.substringAfter('=').trim()
                runCatching {
                    Cookie.Builder()
                        .name(name)
                        .value(value)
                        .domain(url.host)
                        .path("/")
                        .apply { if (url.isHttps) secure() }
                        .build()
                }.getOrNull()
            }

    companion object {
        val AUTH_COOKIE_NAMES = setOf("_t", "_forum_session")
        val RUNTIME_COOKIE_NAMES = setOf("_rt")
        private const val DEFAULT_ASYNC_COOKIE_TIMEOUT_MILLIS = 5_000L
    }
}
