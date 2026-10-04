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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

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
    fun revision(): Long = cookieStore.revision()

    /**
     * Whether [url] is the site's own host (or one of its subdomains). The WebView jar is written
     * only for those hosts: a linux.do cookie must never be seeded under another domain.
     */
    fun isSessionHost(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        val base = baseUrl.toHttpUrlOrNull() ?: return false
        return parsed.host == base.host || parsed.host.endsWith("." + base.host)
    }

    /**
     * The `_t` the browser jar presents for [url], or null when it has none. Throws when the
     * browser's own header carries a conflicting auth pair: which of the two the server last
     * handed out cannot be told from here, so the caller must not write over either.
     */
    suspend fun authTokenInWebView(url: String = baseUrl): String? = operationMutex.withLock {
        parseCookieHeader(url.toHttpUrl(), readCookieHeader(url))
            .firstOrNull { it.name == "_t" && it.value.isNotBlank() }
            ?.value
    }

    suspend fun syncFromWebViewIfCurrent(expectedRevision: Long): Boolean = operationMutex.withLock {
        awaitPendingClear()
        syncFromWebViewFiltered(baseUrl, expectedRevision) { true }
    }

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

    private fun syncFromWebViewFiltered(url: String, expectedRevision: Long? = null, predicate: (Cookie) -> Boolean): Boolean {
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
        if (expectedRevision == null) {
            cookieStore.save(baseUrl.toHttpUrl(), cookies)
        } else if (!cookieStore.saveIfCurrent(baseUrl.toHttpUrl(), cookies, expectedRevision)) {
            SessionDiagnostics.record("webview_import_stale", expectedRevision, accepted = false)
            return false
        }
        SessionDiagnostics.record("webview_import", cookieStore.revision())
        flushCookies()
        return cookieStore.hasAuthenticatedCookies()
    }

    fun hasAuthenticatedCookiesInWebView(url: String = baseUrl): Boolean =
        parseCookieHeader(
            url = url.toHttpUrl(),
            cookieHeader = readCookieHeader(url),
        ).any { cookie -> cookie.name == "_t" && cookie.value.isNotBlank() }

    // getCookie omits attributes. Do not round-trip unchanged browser cookies:
    // that would turn persistent/HttpOnly cookies into synthesized session cookies.
    private fun cookiesToExport(url: String): List<Cookie> {
        val visible = readCookieHeader(url).orEmpty().split(';').map { it.trim() }
        return cookieStore.load(url.toHttpUrl()).filter { "${it.name}=${it.value}" !in visible }
    }

    suspend fun syncToWebView(url: String = baseUrl) = operationMutex.withLock {
        awaitPendingClear()
        syncToWebViewNow(url)
    }

    private fun syncToWebViewNow(url: String) {
        if (!isSessionHost(url)) return
        val cookies = cookiesToExport(url)
        if (cookies.isEmpty()) return
        cookies.forEach { cookie -> writeCookieToWebView(url, cookie.toString()) }
        flushCookies()
    }

    suspend fun syncToWebViewAsync(
        url: String = baseUrl,
        timeoutMillis: Long = DEFAULT_ASYNC_COOKIE_TIMEOUT_MILLIS,
    ): Boolean = operationMutex.withLock {
        if (!isSessionHost(url)) return@withLock true
        awaitPendingClear()
        writeCookiesAsync(url, cookiesToExport(url), timeoutMillis)
    }

    /**
     * Seed the browser jar for a linux.do page from the native jar.
     *
     * A cookie the browser already presents is left alone: its copy may be the newer one, and
     * `getCookie` cannot carry the attributes a rewrite would need. The exception is
     * [overwriteNames]: the caller may name the cookies whose native values a server answer has
     * just accepted ([AUTH_COOKIE_NAMES] for a session seed), and only those are replaced.
     *
     * Returns false when a write did not confirm before [timeoutMillis]; the page still opens,
     * without the guarantee that a named cookie landed.
     */
    suspend fun seedBrowserFromNative(
        url: String = baseUrl,
        overwriteNames: Set<String> = emptySet(),
        timeoutMillis: Long = DEFAULT_ASYNC_COOKIE_TIMEOUT_MILLIS,
    ): Boolean = operationMutex.withLock {
        if (!isSessionHost(url)) return@withLock true
        awaitPendingClear()
        val presentedNames = readCookieHeader(url).orEmpty()
            .split(';')
            .map { entry -> entry.substringBefore('=').trim() }
            .filter(String::isNotEmpty)
            .toSet()
        val cookies = cookieStore.load(url.toHttpUrl())
            .filter { cookie -> cookie.name !in presentedNames || cookie.name in overwriteNames }
        writeCookiesAsync(url, cookies, timeoutMillis)
    }

    private suspend fun writeCookiesAsync(url: String, cookies: List<Cookie>, timeoutMillis: Long): Boolean {
        if (cookies.isEmpty()) return true
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

    internal fun parseCookieHeader(url: HttpUrl, cookieHeader: String?): List<Cookie> {
        val pairs = cookieHeader.orEmpty().split(';').map { it.trim() }
            .filter { it.contains('=') }.map { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
        if (pairs.filter { it.first in AUTH_COOKIE_NAMES }.groupBy { it.first }
                .any { (_, values) -> values.map { it.second }.distinct().size > 1 }) {
            throw io.github.baba983.nativedo.core.network.SessionRecoveryRequiredException()
        }
        val existing = cookieStore.load(url)
        return pairs.mapNotNull { (name, value) ->
            val known = existing.firstOrNull { it.name == name }
            runCatching {
                Cookie.Builder().name(name).value(value).apply {
                    if (known != null) {
                        if (known.hostOnly) hostOnlyDomain(known.domain) else domain(known.domain)
                        path(known.path)
                        if (known.secure) secure()
                        if (known.httpOnly) httpOnly()
                        if (known.value == value && known.persistent) expiresAt(known.expiresAt)
                    } else {
                        hostOnlyDomain(url.host)
                        path("/")
                        if (url.isHttps) secure()
                        if (name in AUTH_COOKIE_NAMES) httpOnly()
                    }
                }.build()
            }.getOrNull()
        }
    }

    companion object {
        val AUTH_COOKIE_NAMES = setOf("_t", "_forum_session")
        val RUNTIME_COOKIE_NAMES = setOf("_rt")
        private const val DEFAULT_ASYNC_COOKIE_TIMEOUT_MILLIS = 5_000L
    }
}
