package io.github.baba983.nativedo.core.session

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The one cookie jar: [android.webkit.CookieManager], the process-wide store the login WebView,
 * upstream's in-app browser and the native client all share.
 *
 * <p>There is deliberately no second copy. The earlier design kept a native snapshot and copied it
 * into the WebView, but `_t` rotates; two copies of a rotating token cannot both stay valid, and
 * the stale reuse logged the reader out (`expired_ServerLoggedOut`). Every participant now reads
 * and writes this one store:</p>
 *
 * <ul>
 *   <li>OkHttp's requests read it ([requestCookieHeader]) and write each response `Set-Cookie`
 *   back verbatim ([saveSetCookie]), so the rotation any side performs is the rotation every side
 *   sees;</li>
 *   <li>the login WebView and the in-app browser read and write it themselves through Chromium;
 *   nothing has to be seeded or imported between visits;</li>
 *   <li>reads and writes are restricted to the site's own hosts ([isSessionHost]): a linux.do
 *   cookie is never sent to, or written under, another domain, and other sites the in-app browser
 *   visits keep their own jars untouched.</li>
 * </ul>
 *
 * <p>Attribute fidelity: [CookieManager.getCookie] exposes only name/value pairs, and
 * [saveSetCookie] takes the response's raw `Set-Cookie` value, so attributes OkHttp's cookie model
 * does not carry (SameSite, Partitioned) survive the native round trip.</p>
 */
class SharedCookieJar(
    baseUrl: String,
    private val cookieManager: () -> CookieManager = { webViewCookieManager() },
) {
    private val siteUrl: HttpUrl = baseUrl.toHttpUrl()
    private val baseHost: String = siteUrl.host
    private val sessionUrls: List<String> = listOf("https://$baseHost/", "https://www.$baseHost/")
    private val writeCount = AtomicLong(0)

    /**
     * A monotonic count of the writes this facade performed, for the session diagnostics log.
     * WebView-side writes are not counted, so this is never a security or fencing boundary.
     */
    fun revision(): Long = writeCount.get()

    fun isSessionHost(url: HttpUrl): Boolean =
        url.host == baseHost || url.host.endsWith(".".plus(baseHost))

    fun isSessionHost(url: String): Boolean =
        url.toHttpUrlOrNull()?.let(::isSessionHost) ?: false

    /**
     * The `Cookie` request header for [url], exactly as the shared jar presents it. Blank when the
     * URL is not the site's own or the jar holds nothing for it.
     */
    fun requestCookieHeader(url: HttpUrl): String {
        if (!isSessionHost(url)) return ""
        return runCatching { cookieManager().getCookie(url.toString()) }
            .getOrNull()
            .orEmpty()
    }

    fun requestCookieHeader(url: String): String =
        url.toHttpUrlOrNull()?.let(::requestCookieHeader).orEmpty()

    fun authToken(url: HttpUrl = siteUrl): String? =
        cookieValue(requestCookieHeader(url), TOKEN_COOKIE)?.takeIf(String::isNotBlank)

    fun hasAuthenticatedCookies(url: HttpUrl = siteUrl): Boolean = authToken(url) != null

    /** Whether the shared jar presents anything at all for the site's base URL. */
    fun hasCookiesForSite(): Boolean = requestCookieHeader(siteUrl).isNotBlank()

    /**
     * Writes one response `Set-Cookie` value, attributes and all, into the shared jar. Expired
     * values delete the cookie, exactly as the browser would apply them.
     *
     * @return true when the value was handed to CookieManager; a false answer is not a session
     * signal, only a dropped write (the page still loads, the next request re-reads the jar).
     */
    fun saveSetCookie(url: HttpUrl, setCookieHeader: String): Boolean {
        if (setCookieHeader.isBlank() || !isSessionHost(url)) return false
        val applied = runCatching { cookieManager().setCookie(url.toString(), setCookieHeader) }
            .isSuccess
        if (applied) writeCount.incrementAndGet()
        return applied
    }

    /**
     * Deletes every cookie the site's own hosts present, leaving cookies of other hosts in the
     * same jar alone. CookieManager has no domain-scoped removal API, so the names are read first
     * and each is expired; a path-scoped cookie the site never shows for `/` can survive, which is
     * why the server's own logout (in the browser) remains the thorough path.
     */
    fun clearSessionCookies() {
        val manager = cookieManager()
        val names = LinkedHashSet<String>()
        sessionUrls.forEach { url -> names += cookieNames(manager.getCookie(url)) }
        names += AUTH_COOKIE_NAMES
        names += RUNTIME_COOKIE_NAMES
        names += CF_CLEARANCE_COOKIE
        sessionUrls.forEach { url ->
            names.forEach { name -> manager.setCookie(url, "$name=; Max-Age=0; Path=/") }
        }
        // Domain cookies are stored under `.host`; one deletion from the base host covers them.
        names.forEach { name ->
            manager.setCookie(sessionUrls.first(), "$name=; Max-Age=0; Path=/; Domain=.$baseHost")
        }
        writeCount.incrementAndGet()
        flush()
    }

    fun flush() {
        runCatching { cookieManager().flush() }
    }

    companion object {
        const val TOKEN_COOKIE: String = "_t"
        val AUTH_COOKIE_NAMES: Set<String> = setOf("_t", "_forum_session")
        val RUNTIME_COOKIE_NAMES: Set<String> = setOf("_rt")
        const val CF_CLEARANCE_COOKIE: String = "cf_clearance"

        /** The value of [name] in a `Cookie` request header, or null. */
        fun cookieValue(header: String?, name: String): String? {
            header.orEmpty().split(';').forEach { part ->
                val entry = part.trim()
                val separator = entry.indexOf('=')
                if (separator > 0 && entry.substring(0, separator).trim() == name) {
                    return entry.substring(separator + 1).trim()
                }
            }
            return null
        }

        /** The cookie name of a `Set-Cookie` value: its first pair. */
        fun setCookieName(setCookieHeader: String): String? =
            setCookieHeader.substringBefore(';')
                .substringBefore('=')
                .trim()
                .takeIf(String::isNotEmpty)

        fun cookieNames(header: String?): Set<String> =
            header.orEmpty().split(';')
                .map { it.trim() }
                .mapNotNull { entry ->
                    val separator = entry.indexOf('=')
                    if (separator <= 0) {
                        null
                    } else {
                        entry.substring(0, separator).trim().takeIf(String::isNotEmpty)
                    }
                }
                .toSet()
    }
}

/**
 * CookieManager.getInstance() before any WebView exists is a supported cold-start path, but the
 * provider load should not be the first thing a network thread does (older providers expected the
 * main thread, and the first API call of a cold start runs on an OkHttp thread). Initialization
 * happens once, on the main thread when the caller is not already there.
 */
private fun webViewCookieManager(): CookieManager {
    WebViewCookieManagerInitializer.ensureInitialized()
    return CookieManager.getInstance()
}

private object WebViewCookieManagerInitializer {
    @Volatile
    private var initialized = false

    fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val mainLooper = Looper.getMainLooper()
            if (mainLooper == null || Looper.myLooper() == mainLooper) {
                CookieManager.getInstance()
            } else {
                val latch = CountDownLatch(1)
                try {
                    Handler(mainLooper).post {
                        runCatching { CookieManager.getInstance() }
                            .onFailure { error ->
                                Log.w("NativeDoSession", "webview_provider_init_failed", error)
                            }
                        latch.countDown()
                    }
                    latch.await(INIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                CookieManager.getInstance()
            }
            initialized = true
        }
    }

    /** Bounded: a wedged main thread is not a reason to fail the first cold-start request. */
    private const val INIT_TIMEOUT_MILLIS = 2_000L
}
