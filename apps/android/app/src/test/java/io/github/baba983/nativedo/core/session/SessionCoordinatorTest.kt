package io.github.baba983.nativedo.core.session

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.network.ParsedBootstrap
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.core.storage.SessionStore
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

class SessionCoordinatorTest {
    private val baseUrl = "https://linux.do/"

    @Test
    fun completeLoginFromWebView_syncsWebViewCookiesBeforeRestore() = runTest {
        val cookieStore = CookieStore()
        val bridge = CookieSyncBridge(
            baseUrl = baseUrl,
            cookieStore = cookieStore,
            readCookieHeader = { "_t=auth_token" },
            writeCookieToWebView = { _, _ -> },
            flushCookies = {},
            clearCookies = { onComplete -> onComplete?.invoke() },
        )
        val coordinator = SessionCoordinator(
            cookieStore = cookieStore,
            sessionStore = SessionStore(),
            apiClient = FakeSessionBootstrapClient(currentUsername = "baba"),
            cookieSyncBridge = bridge,
            now = { 42L },
        )

        coordinator.completeLoginFromWebView()

        assertThat(cookieStore.hasAuthenticatedCookies()).isTrue()
        assertThat(coordinator.sessionState.value).isEqualTo(
            AppSessionState.Authenticated(
                snapshot = SessionSnapshot(
                    username = "baba",
                    displayName = "baba",
                    avatarTemplate = "",
                    lastValidatedAt = 42L,
                ),
                freshness = SessionFreshness.Verified,
            ),
        )
    }

    @Test
    fun startupWithStaleCookiesAndNoCache_marksRecoveryFailedAndClearsCookies() = runTest {
        // A bootstrap response with no current-user could be a Cloudflare
        // challenge or a transient parse miss; on startup we still wipe the
        // stale cookies (clearInvalidWebViewCookies = true) but the reason is
        // RecoveryFailed because the server never explicitly said logged-out.
        val cookieStore = CookieStore()
        cookieStore.save(
            baseUrl.toHttpUrl(),
            listOf(
                Cookie.Builder()
                    .name("_t")
                    .value("stale_auth_token")
                    .domain("linux.do")
                    .path("/")
                    .build(),
            ),
        )
        var webViewCookiesCleared = false
        val bridge = CookieSyncBridge(
            baseUrl = baseUrl,
            cookieStore = cookieStore,
            readCookieHeader = { null },
            writeCookieToWebView = { _, _ -> },
            flushCookies = {},
            clearCookies = { onComplete ->
                webViewCookiesCleared = true
                onComplete?.invoke()
            },
        )
        val coordinator = SessionCoordinator(
            cookieStore = cookieStore,
            sessionStore = SessionStore(),
            apiClient = FakeSessionBootstrapClient(),
            cookieSyncBridge = bridge,
        )

        coordinator.start()

        assertThat(cookieStore.hasAuthenticatedCookies()).isFalse()
        assertThat(webViewCookiesCleared).isTrue()
        assertThat(coordinator.sessionState.value).isEqualTo(
            AppSessionState.Expired(SessionExpiredReason.RecoveryFailed),
        )
    }

    @Test
    fun startupWithCachedSnapshot_fallsBackToCachedAuthenticatedOnTransientBootstrapMiss() = runTest {
        // Bootstrap returning no username may just be a Cloudflare challenge
        // — wiping WebView cookies and cached snapshot would be hostile.
        // Instead we keep the user signed-in via the cached snapshot
        // (Authenticated/Cached) and revisit the next time we get a chance.
        val cookieStore = CookieStore()
        cookieStore.save(
            baseUrl.toHttpUrl(),
            listOf(
                Cookie.Builder()
                    .name("_t")
                    .value("auth_token")
                    .domain("linux.do")
                    .path("/")
                    .build(),
            ),
        )
        val snapshot = SessionSnapshot(
            username = "baba",
            displayName = "Baba",
            avatarTemplate = "",
            lastValidatedAt = 1L,
        )
        val sessionStore = SessionStore().apply {
            save(snapshot)
        }
        var webViewCookiesCleared = false
        val bridge = CookieSyncBridge(
            baseUrl = baseUrl,
            cookieStore = cookieStore,
            readCookieHeader = { null },
            writeCookieToWebView = { _, _ -> },
            flushCookies = {},
            clearCookies = { onComplete ->
                webViewCookiesCleared = true
                onComplete?.invoke()
            },
        )
        val coordinator = SessionCoordinator(
            cookieStore = cookieStore,
            sessionStore = sessionStore,
            apiClient = FakeSessionBootstrapClient(),
            cookieSyncBridge = bridge,
        )

        coordinator.start()

        assertThat(cookieStore.hasAuthenticatedCookies()).isTrue()
        assertThat(sessionStore.snapshot()).isEqualTo(snapshot)
        assertThat(webViewCookiesCleared).isFalse()
        assertThat(coordinator.sessionState.value).isEqualTo(
            AppSessionState.Authenticated(
                snapshot = snapshot,
                freshness = SessionFreshness.Cached,
            ),
        )
    }

    @Test
    fun loggedOutResponseWithCachedSnapshot_clearsStaleCookiesAndExpiresSession() = runTest {
        val cookieStore = CookieStore()
        cookieStore.save(
            baseUrl.toHttpUrl(),
            listOf(
                Cookie.Builder()
                    .name("_t")
                    .value("stale_auth_token")
                    .domain("linux.do")
                    .path("/")
                    .build(),
            ),
        )
        val snapshot = SessionSnapshot(
            username = "baba",
            displayName = "Baba",
            avatarTemplate = "",
            lastValidatedAt = 1L,
        )
        val sessionStore = SessionStore().apply {
            save(snapshot)
        }
        val coordinator = SessionCoordinator(
            cookieStore = cookieStore,
            sessionStore = sessionStore,
            apiClient = FakeSessionBootstrapClient(currentUsernameError = SessionLoggedOutException()),
            cookieSyncBridge = CookieSyncBridge(
                baseUrl = baseUrl,
                cookieStore = cookieStore,
                readCookieHeader = { null },
                writeCookieToWebView = { _, _ -> },
                flushCookies = {},
                clearCookies = { onComplete -> onComplete?.invoke() },
            ),
        )

        coordinator.start()

        assertThat(cookieStore.hasAuthenticatedCookies()).isFalse()
        assertThat(sessionStore.snapshot()).isNull()
        assertThat(coordinator.sessionState.value).isEqualTo(
            AppSessionState.Expired(
                reason = SessionExpiredReason.ServerLoggedOut,
                snapshot = snapshot,
            ),
        )
    }

    @Test
    fun completeLoginFromWebView_failureDoesNotClearWebViewCookiesWhileUserIsLoggingIn() = runTest {
        val cookieStore = CookieStore()
        val sessionStore = SessionStore().apply {
            save(
                SessionSnapshot(
                    username = "baba",
                    displayName = "Baba",
                    avatarTemplate = "",
                    lastValidatedAt = 1L,
                ),
            )
        }
        var webViewCookiesCleared = false
        val coordinator = SessionCoordinator(
            cookieStore = cookieStore,
            sessionStore = sessionStore,
            apiClient = FakeSessionBootstrapClient(currentUsernameError = RuntimeException("temporary failure")),
            cookieSyncBridge = CookieSyncBridge(
                baseUrl = baseUrl,
                cookieStore = cookieStore,
                readCookieHeader = { "_t=auth_token" },
                writeCookieToWebView = { _, _ -> },
                flushCookies = {},
                clearCookies = { onComplete ->
                    webViewCookiesCleared = true
                    onComplete?.invoke()
                },
            ),
        )

        coordinator.completeLoginFromWebView()

        assertThat(cookieStore.hasAuthenticatedCookies()).isFalse()
        assertThat(sessionStore.snapshot()).isNull()
        assertThat(webViewCookiesCleared).isFalse()
        assertThat(coordinator.sessionState.value).isEqualTo(
            AppSessionState.Expired(
                reason = SessionExpiredReason.RecoveryFailed,
                snapshot = SessionSnapshot(
                    username = "baba",
                    displayName = "Baba",
                    avatarTemplate = "",
                    lastValidatedAt = 1L,
                ),
            ),
        )
    }

    @Test
    fun completeLoginFromWebView_loggedOutResponseClearsInvalidWebViewCookies() = runTest {
        val cookieStore = CookieStore()
        var webViewCookiesCleared = false
        val coordinator = SessionCoordinator(
            cookieStore = cookieStore,
            sessionStore = SessionStore(),
            apiClient = FakeSessionBootstrapClient(currentUsernameError = SessionLoggedOutException()),
            cookieSyncBridge = CookieSyncBridge(
                baseUrl = baseUrl,
                cookieStore = cookieStore,
                readCookieHeader = { "_t=stale_auth_token" },
                writeCookieToWebView = { _, _ -> },
                flushCookies = {},
                clearCookies = { onComplete ->
                    webViewCookiesCleared = true
                    onComplete?.invoke()
                },
            ),
        )

        coordinator.completeLoginFromWebView()

        assertThat(cookieStore.hasAuthenticatedCookies()).isFalse()
        assertThat(webViewCookiesCleared).isTrue()
        assertThat(coordinator.sessionState.value).isEqualTo(
            AppSessionState.Expired(reason = SessionExpiredReason.ServerLoggedOut),
        )
    }

    @Test
    fun completeLoginFromWebViewWithKnownUsername_marksAuthenticatedWithoutHittingApi() = runTest {
        val cookieStore = CookieStore()
        val bridge = CookieSyncBridge(
            baseUrl = baseUrl,
            cookieStore = cookieStore,
            readCookieHeader = { "_t=auth_token" },
            writeCookieToWebView = { _, _ -> },
            flushCookies = {},
            clearCookies = { onComplete -> onComplete?.invoke() },
        )
        // Bootstrap deliberately throws to prove the API path is *not* used.
        val coordinator = SessionCoordinator(
            cookieStore = cookieStore,
            sessionStore = SessionStore(),
            apiClient = FakeSessionBootstrapClient(currentUsernameError = SessionLoggedOutException()),
            cookieSyncBridge = bridge,
            now = { 99L },
        )

        coordinator.completeLoginFromWebViewWithKnownUsername(username = "baba")

        assertThat(cookieStore.hasAuthenticatedCookies()).isTrue()
        assertThat(coordinator.sessionState.value).isEqualTo(
            AppSessionState.Authenticated(
                snapshot = SessionSnapshot(
                    username = "baba",
                    displayName = "baba",
                    avatarTemplate = "",
                    lastValidatedAt = 99L,
                ),
                freshness = SessionFreshness.Verified,
            ),
        )
    }

    @Test
    fun completeLoginFromWebViewWithKnownUsername_keepsCachedDisplayNameAndAvatarWhenUsernameMatches() = runTest {
        val cookieStore = CookieStore()
        val cachedSnapshot = SessionSnapshot(
            username = "baba",
            displayName = "Baba The Goat",
            avatarTemplate = "https://example/avatar/{size}.png",
            lastValidatedAt = 1L,
        )
        val sessionStore = SessionStore().apply { save(cachedSnapshot) }
        val bridge = CookieSyncBridge(
            baseUrl = baseUrl,
            cookieStore = cookieStore,
            readCookieHeader = { "_t=auth_token" },
            writeCookieToWebView = { _, _ -> },
            flushCookies = {},
            clearCookies = { onComplete -> onComplete?.invoke() },
        )
        val coordinator = SessionCoordinator(
            cookieStore = cookieStore,
            sessionStore = sessionStore,
            apiClient = FakeSessionBootstrapClient(),
            cookieSyncBridge = bridge,
            now = { 200L },
        )

        coordinator.completeLoginFromWebViewWithKnownUsername(username = "baba")

        val state = coordinator.sessionState.value
        check(state is AppSessionState.Authenticated) { "expected Authenticated, got $state" }
        assertThat(state.snapshot.displayName).isEqualTo("Baba The Goat")
        assertThat(state.snapshot.avatarTemplate).isEqualTo("https://example/avatar/{size}.png")
        assertThat(state.snapshot.lastValidatedAt).isEqualTo(200L)
    }

    @Test
    fun completeLoginFromWebViewWithKnownUsername_fallsBackToAnonymousWhenCookiesMissing() = runTest {
        val cookieStore = CookieStore()
        val bridge = CookieSyncBridge(
            baseUrl = baseUrl,
            cookieStore = cookieStore,
            readCookieHeader = { null },
            writeCookieToWebView = { _, _ -> },
            flushCookies = {},
            clearCookies = { onComplete -> onComplete?.invoke() },
        )
        val coordinator = SessionCoordinator(
            cookieStore = cookieStore,
            sessionStore = SessionStore(),
            apiClient = FakeSessionBootstrapClient(),
            cookieSyncBridge = bridge,
        )

        coordinator.completeLoginFromWebViewWithKnownUsername(username = "baba")

        assertThat(coordinator.sessionState.value).isEqualTo(AppSessionState.Anonymous)
    }

    private class FakeSessionBootstrapClient(
        private val currentUsername: String? = null,
        private val bootstrapUsername: String? = null,
        private val currentUsernameError: Throwable? = null,
    ) : SessionBootstrapClient {
        override suspend fun fetchBootstrap(): ParsedBootstrap =
            ParsedBootstrap(currentUsername = bootstrapUsername)

        override suspend fun fetchCurrentUsername(): String? {
            currentUsernameError?.let { throw it }
            return currentUsername
        }
    }
}
