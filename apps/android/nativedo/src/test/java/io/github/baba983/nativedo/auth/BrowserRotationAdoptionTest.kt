package io.github.baba983.nativedo.auth

import android.webkit.CookieManager
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.session.SessionSnapshot
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * A stale native `_t` next to a browser jar that holds the rotation.
 *
 * <p>The failure this pins: an in-app browser visit (or any other WebView page) can rotate
 * `_t` while the native jar was idle. The server rejects the native token, and the rejection is
 * the one signal that must reconcile with the browser jar before anything is cleared - otherwise
 * the reader is logged out while a live token sits two directories away in `CookieManager`. The
 * coordinator's rejection path has that adoption; this test drives it through the real
 * `DiscourseApiClient` and the real cookie store (the old unit test used a fake client, which never
 * exercised the client's own logout reporting).</p>
 */
@RunWith(RobolectricTestRunner::class)
internal class BrowserRotationAdoptionTest {

    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
    }

    @After
    fun tearDown() {
        site.stop()
        CookieManager.getInstance().removeAllCookies(null)
    }

    @Test
    fun aRejectedNativeTokenAdoptsTheBrowsersRotationInsteadOfLoggingOut() {
        val baseUrl = site.baseUrl
        val host = baseUrl.toHttpUrl().host
        val container = AppContainer(RuntimeEnvironment.getApplication(), baseUrl, null, null)
        container.cookieStore.save(
            baseUrl.toHttpUrl(),
            listOf(cookie(host, "_t", "native-stale"), cookie(host, "_forum_session", "native-session")),
        )
        container.sessionStore.save(SessionSnapshot("baba", "Baba", "", 1L))
        // The browser made a request after the native jar's last rotation and was handed the new
        // value; the native jar never saw it.
        CookieManager.getInstance().setCookie(baseUrl, "_t=browser-rotated; Path=/; HttpOnly")

        val attempts = AtomicInteger()
        site.serveResponse("session/current.json") {
            if (attempts.getAndIncrement() == 0) {
                // The rejected token is deleted by the same answer, exactly as `refresh_session`
                // does when the lookup resolves to no user.
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"current_user\":null}")
                    .addHeader(
                        "Set-Cookie",
                        "_t=; Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT",
                    )
            } else {
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        "{\"current_user\":{\"username\":\"baba\",\"name\":\"Baba\",\"avatar_template\":\"\"}}",
                    )
            }
        }

        runBlocking { container.sessionCoordinator.start() }

        val state = container.sessionCoordinator.sessionState.value
        assertTrue("a rejected token must not log the reader out: $state", state is AppSessionState.Authenticated)
        assertEquals("baba", (state as AppSessionState.Authenticated).snapshot.username)
        assertEquals(
            "the native jar must end up with the rotation the browser held",
            "browser-rotated",
            container.cookieStore.load(baseUrl.toHttpUrl()).first { it.name == "_t" }.value,
        )
        assertTrue(
            "the browser jar must survive the reconciliation",
            CookieManager.getInstance().getCookie(baseUrl)?.contains("_t=browser-rotated") == true,
        )
        assertEquals("the retried verify is what proves the adopted token", 2, attempts.get())
    }

    /**
     * The same rotation, arriving on an ordinary request while the app is in use - the device pass
     * hit this within two minutes of browsing in the seeded in-app browser. The browser and the
     * native client share one `_t`, so each rotates the token for the other; a native request that
     * misses the rotation must adopt the browser's and fail alone, not sign the reader out.
     */
    @Test
    fun aMidSessionRejectionAdoptsTheBrowserRotationInsteadOfLoggingOut() {
        val baseUrl = site.baseUrl
        val host = baseUrl.toHttpUrl().host
        val container = AppContainer(RuntimeEnvironment.getApplication(), baseUrl, null, null)
        container.cookieStore.save(
            baseUrl.toHttpUrl(),
            listOf(cookie(host, "_t", "native-old"), cookie(host, "_forum_session", "native-session")),
        )
        container.sessionStore.save(SessionSnapshot("baba", "Baba", "", 1L))
        CookieManager.getInstance().setCookie(baseUrl, "_t=browser-rotated; Path=/; HttpOnly")
        site.serveJson(
            "session/current.json",
            "{\"current_user\":{\"username\":\"baba\",\"name\":\"Baba\",\"avatar_template\":\"\"}}",
        )
        site.serveResponse("latest.json") {
            MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/json")
                .setHeader("discourse-logged-out", "true")
                .setBody("{\"error_type\":\"not_logged_in\"}")
        }

        runBlocking {
            container.sessionCoordinator.start()
            assertTrue(container.sessionCoordinator.sessionState.value is AppSessionState.Authenticated)

            val failure = runCatching { container.discourseApiClient.fetchLatestPage(0) }.exceptionOrNull()

            assertTrue(
                "a rotation must fail like a retryable read, not like a logout: $failure",
                failure is java.io.IOException,
            )
        }

        assertTrue(
            "the session must survive the rejection",
            container.sessionCoordinator.sessionState.value is AppSessionState.Authenticated,
        )
        assertEquals(
            "the browser rotation is what the native jar now holds",
            "browser-rotated",
            container.cookieStore.load(baseUrl.toHttpUrl()).first { it.name == "_t" }.value,
        )
        assertTrue(
            "the browser jar must not be cleared",
            CookieManager.getInstance().getCookie(baseUrl)?.contains("_t=browser-rotated") == true,
        )
    }

    private fun cookie(host: String, name: String, value: String): Cookie =
        Cookie.Builder().name(name).value(value).hostOnlyDomain(host).path("/").build()
}
