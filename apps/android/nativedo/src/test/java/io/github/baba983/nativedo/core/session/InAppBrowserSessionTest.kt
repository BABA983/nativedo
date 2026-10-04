package io.github.baba983.nativedo.core.session

import io.github.baba983.nativedo.core.storage.CookieStore
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The policy that puts the session into the in-app browser jar.
 *
 * <p>The failure modes under test are the two ways a cookie hand-off goes wrong: writing linux.do
 * cookies under a foreign host, and letting the native jar overwrite a browser rotation the server
 * has not confirmed (or the other way around - keeping a stale browser token and showing the reader
 * an anonymous page). The browser here is a cookie header on a fake jar, because the question is
 * the decision, not Chromium's storage.</p>
 */
internal class InAppBrowserSessionTest {

    private val baseUrl = "https://linux.do/"

    @Test
    fun aForeignHostNeverGetsASessionCookie() = runBlocking {
        val store = cookieStoreWith(sessionCookie())
        val browser = BrowserJar()
        val handoff = handoff(store, browser) { SessionProbeResult.Accepted }

        handoff.prepare("https://example.com/page")
        // Defense in depth at the bridge: even a caller that skips the handoff cannot seed a
        // foreign host.
        handoffBridge.syncToWebViewAsync("https://example.com/")

        assertTrue("a linux.do cookie must never be written under another host: ${browser.writes}", browser.writes.isEmpty())
    }

    @Test
    fun theBrowserIsFilledWhenItHasNoSessionOfItsOwn() = runBlocking {
        val store = cookieStoreWith(
            sessionCookie(),
            cookie("_forum_session", "forum-session"),
            cookie("cf_clearance", "clearance"),
        )
        val browser = BrowserJar()
        val handoff = handoff(store, browser) { SessionProbeResult.Unavailable }

        handoff.prepare(baseUrl)

        val names = browser.writes.map { it.substringBefore('=') }
        assertEquals(listOf("_t", "_forum_session", "cf_clearance"), names)
        assertTrue("the browser gets the session token", browser.writes.first().startsWith("_t=session-token"))
        assertTrue("attributes survive the write", browser.writes.first().contains("httponly"))
        assertTrue("attributes survive the write", browser.writes.first().contains("secure"))
        assertTrue("a host-only cookie stays host-only", !browser.writes.first().contains("domain="))
    }

    @Test
    fun aServerConfirmedNativeTokenReplacesTheBrowsersStaleOne() = runBlocking {
        val store = cookieStoreWith(sessionCookie("native-current"))
        val browser = BrowserJar("_t=browser-stale; _forum_session=forum-stale")
        val handoff = handoff(store, browser) { SessionProbeResult.Accepted }

        handoff.prepare(baseUrl)

        val token = browser.writes.single { it.startsWith("_t=") }
        assertTrue("the accepted native token is what the page must carry: $token", token.startsWith("_t=native-current"))
        assertTrue("the browser's stale token was replaced", browser.writes.none { it.startsWith("_t=browser-stale") })
    }

    @Test
    fun anUnconfirmedDivergenceLeavesTheBrowsersCookiesAlone() = runBlocking {
        val store = cookieStoreWith(sessionCookie("native-maybe-stale"))
        val browser = BrowserJar("_t=browser-maybe-stale")
        val handoff = handoff(store, browser) { SessionProbeResult.Unavailable }

        handoff.prepare(baseUrl)

        assertTrue(
            "an unanswered probe is not a licence to overwrite the possibly-live browser token: ${browser.writes}",
            browser.writes.isEmpty(),
        )
        assertEquals("native-maybe-stale", store.authenticatedToken())
    }

    @Test
    fun aRejectedNativeTokenAdoptsTheBrowserRotation() = runBlocking {
        val store = cookieStoreWith(sessionCookie("native-stale"))
        val browser = BrowserJar("_t=browser-rotated; _forum_session=forum-rotated")
        val handoff = handoff(store, browser) { SessionProbeResult.Rejected }

        handoff.prepare(baseUrl)

        assertEquals("the rotation is adopted into the native jar", "browser-rotated", store.authenticatedToken())
        assertTrue(
            "the stale native token is not written anywhere: ${browser.writes}",
            browser.writes.none { it.startsWith("_t=native-stale") },
        )
    }

    @Test
    fun aMatchingTokenOnlyFillsWhatTheBrowserLacks() = runBlocking {
        val store = cookieStoreWith(
            sessionCookie("shared-token"),
            cookie("cf_clearance", "clearance"),
        )
        val browser = BrowserJar("_t=shared-token")
        val handoff = handoff(store, browser) { error("a matching token needs no probe") }

        handoff.prepare(baseUrl)

        assertEquals(listOf("cf_clearance"), browser.writes.map { it.substringBefore('=') })
    }

    // ------------------------------------------------------------------ fixtures

    private lateinit var handoffBridge: CookieSyncBridge

    private fun handoff(
        store: CookieStore,
        browser: BrowserJar,
        probe: suspend () -> SessionProbeResult,
    ): InAppBrowserSession {
        handoffBridge = CookieSyncBridge(
            baseUrl = baseUrl,
            cookieStore = store,
            readCookieHeader = { browser.header },
            writeCookieToWebView = { _, cookie -> browser.writes += cookie },
            writeCookieToWebViewAsync = { _, cookie, onComplete ->
                browser.writes += cookie
                onComplete(true)
            },
            flushCookies = {},
        )
        return InAppBrowserSession(
            cookieStore = store,
            bridge = handoffBridge,
            probeSession = probe,
            openInAppBrowser = { _, _ -> },
            scope = kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined,
            ),
        )
    }

    private class BrowserJar(var header: String? = null) {
        val writes = mutableListOf<String>()
    }

    private fun cookieStoreWith(vararg cookies: Cookie): CookieStore = CookieStore().apply {
        save(baseUrl.toHttpUrl(), cookies.toList())
    }

    private fun sessionCookie(value: String = "session-token"): Cookie = Cookie.Builder()
        .name("_t")
        .value(value)
        .hostOnlyDomain("linux.do")
        .path("/")
        .secure()
        .httpOnly()
        .build()

    private fun cookie(name: String, value: String): Cookie = Cookie.Builder()
        .name(name)
        .value(value)
        .hostOnlyDomain("linux.do")
        .path("/")
        .secure()
        .build()
}
