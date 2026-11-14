package io.github.baba983.nativedo.core.session

import android.content.Context
import android.webkit.CookieManager
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.harness.FakeDiscourseSite
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The one shared jar, pinned by the failure modes the two-jar design produced.
 *
 * <p>Every test here drives the real `DiscourseApiClient` over a mock site against the real
 * CookieManager, because the defects live in the interactions between them: a token written by
 * the browser must be the token the next native request sends, a rotation made while a request
 * is in flight must not be overwritten by that request's stale response, and a logout answer must
 * clear exactly the site's own cookies.</p>
 */
@RunWith(RobolectricTestRunner::class)
internal class SharedCookieJarTest {

    private val site = FakeDiscourseSite()

    @Before
    fun setUp() {
        site.start()
        CookieManager.getInstance().removeAllCookies(null)
    }

    @After
    fun tearDown() {
        site.stop()
        CookieManager.getInstance().removeAllCookies(null)
    }

    /**
     * The device state this design must recover: the owner logged in through the in-app browser
     * (fresh `_t` in the shared jar), the app's native identity store is empty. A cold start must
     * verify that token and come up signed in, not logged out.
     */
    @Test
    fun aColdStartRecoversTheSessionTheBrowserLeftInTheSharedJar() {
        val container = container()
        jarCookie("_t=browser-token; Path=/; HttpOnly")
        assertNull("the native identity store starts empty", container.sessionStore.snapshot())
        site.serveJson("session/current.json", currentUser("baba", "Baba"))

        runBlocking { container.sessionCoordinator.start() }

        val state = container.sessionCoordinator.sessionState.value
        assertTrue("a browser-owned session is the app's session: $state", state is AppSessionState.Authenticated)
        assertEquals("baba", (state as AppSessionState.Authenticated).snapshot.username)
        assertEquals(SessionFreshness.Verified, state.freshness)
        assertEquals("the verified identity is persisted", "baba", container.sessionStore.snapshot()?.username)
        assertTrue(
            "the first native call carried the browser's own token",
            site.lastRequestCookieHeader?.contains("_t=browser-token") == true,
        )
    }

    /**
     * A rotation another participant performs while a native request is in flight: the response
     * was produced from the older token, so its `Set-Cookie` is not the newest rotation and must
     * not replace the one the jar already holds. The request's payload is still good.
     */
    @Test
    fun aStaleInFlightResponseDoesNotOverwriteTheRotationAnotherParticipantWrote() {
        val container = container()
        jarCookie("_t=first; Path=/; HttpOnly")
        site.serveJson("session/current.json", currentUser("baba", "Baba"))
        runBlocking { container.sessionCoordinator.start() }
        site.serveResponse("session/current.json") {
            // The browser (or a parallel response) rotated `_t` after this request went out.
            CookieManager.getInstance().setCookie(site.baseUrl, "_t=second; Path=/; HttpOnly")
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setHeader("Set-Cookie", "_t=older-rotation; Path=/; HttpOnly")
                .setBody(currentUser("baba", "Baba"))
        }

        val user = runBlocking { container.discourseApiClient.fetchCurrentUser() }

        assertNotNull("the read itself still answers", user)
        assertEquals("baba", user!!.username)
        assertEquals(
            "the newer rotation survives the stale response",
            "second",
            container.cookieJar.authToken(),
        )
    }

    /**
     * The same in-flight rotation, but the stale token now draws logout evidence. That answer
     * belongs to a token the jar no longer holds, so it must fail this one request (retryable
     * IOException) instead of ending a live session - the exact shape of the earlier
     * `expired_ServerLoggedOut`.
     */
    @Test
    fun aStaleLogoutAnswerFailsTheRequestInsteadOfEndingTheSession() {
        val container = container()
        jarCookie("_t=first; Path=/; HttpOnly")
        site.serveJson("session/current.json", currentUser("baba", "Baba"))
        runBlocking { container.sessionCoordinator.start() }
        site.serveResponse("latest.json") {
            CookieManager.getInstance().setCookie(site.baseUrl, "_t=second; Path=/; HttpOnly")
            MockResponse()
                .setResponseCode(403)
                .setHeader("discourse-logged-out", "true")
                .setHeader("Set-Cookie", "_t=; Path=/; Max-Age=0")
                .setBody("{\"error_type\":\"not_logged_in\"}")
        }

        val failure = runCatching { runBlocking { container.discourseApiClient.fetchLatestPage(0) } }
            .exceptionOrNull()

        assertTrue("a fenced logout answer is a retryable failure: $failure", failure is IOException)
        assertTrue(
            "the session is alive",
            container.sessionCoordinator.sessionState.value is AppSessionState.Authenticated,
        )
        assertEquals(
            "the stale deletion was dropped too",
            "second",
            container.cookieJar.authToken(),
        )
    }

    /**
     * A real logout: the token the jar itself holds was rejected, so the server's answer is the
     * last word. The site's cookies go, the stored identity goes, and another site's cookies the
     * in-app browser accumulated stay untouched.
     */
    @Test
    fun aRealLogoutClearsTheSitesCookiesAndLeavesOtherHostsAlone() {
        CookieManager.getInstance().setCookie("https://m.stripe.com/", "m=kept; Path=/")
        val container = container()
        jarCookie("_t=dead; Path=/; HttpOnly")
        container.sessionStore.save(SessionSnapshot("baba", "Baba", "", 1L))
        site.serveResponse("session/current.json") {
            MockResponse()
                .setResponseCode(403)
                .setHeader("discourse-logged-out", "true")
                .setBody("{\"error_type\":\"not_logged_in\"}")
        }

        runBlocking { container.sessionCoordinator.start() }

        val state = container.sessionCoordinator.sessionState.value
        assertTrue(
            "the server's own rejection ends the session: $state",
            state is AppSessionState.Expired && state.reason == SessionExpiredReason.ServerLoggedOut,
        )
        assertNull("the signed-out identity is not remembered", container.sessionStore.snapshot())
        assertFalse("the dead token cannot be sent again", container.cookieJar.hasAuthenticatedCookies())
        assertEquals(
            "another site's cookies are not this client's to clear",
            "kept",
            SharedCookieJar.cookieValue(
                CookieManager.getInstance().getCookie("https://m.stripe.com/"),
                "m",
            ),
        )
    }

    /**
     * The retired native snapshot is a one-time import source. It may fill an empty jar; it must
     * never overwrite cookies the browser already wrote (its `_t` is the newer copy), and it is
     * deleted either way.
     */
    @Test
    fun theRetiredNativeSnapshotIsImportedIntoAnEmptyJarAndThenDeleted() {
        legacySnapshot("_t=legacy-token; Path=/")

        val container = container()

        assertEquals(
            "the legacy token was imported into the shared jar",
            "legacy-token",
            container.cookieJar.authToken(),
        )
        assertNull(
            "the retired snapshot is deleted after its single read",
            legacyPreferences().getString("cookie_snapshot", null),
        )
    }

    @Test
    fun theRetiredNativeSnapshotNeverOverwritesAJarTheBrowserAlreadyFilled() {
        jarCookie("_t=fresh-browser-token; Path=/; HttpOnly")
        legacySnapshot("_t=stale-legacy-token; Path=/")

        val container = container()

        assertEquals(
            "the browser's token wins",
            "fresh-browser-token",
            container.cookieJar.authToken(),
        )
        assertNull(
            "the retired snapshot is deleted without being imported",
            legacyPreferences().getString("cookie_snapshot", null),
        )
    }

    /** A linux.do cookie must never be read for, or written under, another host. */
    @Test
    fun theJarNeverReadsOrWritesAnotherSitesCookies() {
        val jar = SharedCookieJar("https://linux.do/")
        CookieManager.getInstance().setCookie("https://example.com/", "_t=other-site; Path=/")

        assertEquals("", jar.requestCookieHeader("https://example.com/".toHttpUrl()))
        assertFalse(jar.saveSetCookie("https://example.com/".toHttpUrl(), "_t=written; Path=/"))
        assertFalse(jar.hasAuthenticatedCookies("https://example.com/".toHttpUrl()))
        assertEquals(
            "the other site's cookie is untouched",
            "other-site",
            SharedCookieJar.cookieValue(
                CookieManager.getInstance().getCookie("https://example.com/"),
                "_t",
            ),
        )
    }

    /**
     * The retired login autofill store must not survive an upgrade: a previous build saved the
     * WebView form's username and password there, and the container clears it before any screen
     * runs.
     */
    @Test
    fun aRetiredLoginAutofillStoreIsClearedWhenTheContainerIsBuilt() {
        val retired = RuntimeEnvironment.getApplication()
            .getSharedPreferences("nativedo.credentials", Context.MODE_PRIVATE)
        retired.edit()
            .putString("username", "reader")
            .putString("password", "v1:sealed")
            .commit()

        container()

        assertTrue("the retired login autofill store is empty", retired.all.isEmpty())
    }

    // ------------------------------------------------------------------ fixtures

    private fun container(): AppContainer =
        AppContainer(RuntimeEnvironment.getApplication(), site.baseUrl, null, null)

    private fun jarCookie(setCookieHeader: String) {
        CookieManager.getInstance().setCookie(site.baseUrl, setCookieHeader)
    }

    private fun currentUser(username: String, name: String): String =
        "{\"current_user\":{\"username\":\"$username\",\"name\":\"$name\",\"avatar_template\":\"\"}}"

    private fun legacySnapshot(snapshotCookie: String) {
        val host = site.baseUrl.toHttpUrl().host
        legacyPreferences().edit()
            .putString("cookie_snapshot", "{\"cookies\":{\"$host\":[\"$snapshotCookie\"]}}")
            .commit()
    }

    private fun legacyPreferences() = RuntimeEnvironment.getApplication()
        .getSharedPreferences("nativedo.cookie_store", Context.MODE_PRIVATE)
}
