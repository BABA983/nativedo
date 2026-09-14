package io.github.baba983.nativedo.core.session

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.network.ParsedBootstrap
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.core.storage.SessionStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test
class SessionCoordinatorTest {
    private val baseUrl = "https://linux.do/"

    @Test
    fun startupRecoversBrowserTokenAndVerifiesItsAccount() = runTest {
        val cookies = CookieStore()
        val sessions = SessionStore().apply {
            save(SessionSnapshot("old-account", "Old", "", 1L))
        }
        val bridge = CookieSyncBridge(baseUrl, cookies,
            readCookieHeader = { "_t=browser-token" }, flushCookies = {})
        val coordinator = SessionCoordinator(cookies, sessions,
            FakeSessionBootstrapClient(currentUsername = "browser-account"), bridge)

        coordinator.start()

        assertThat(cookies.hasAuthenticatedCookies()).isTrue()
        val state = coordinator.sessionState.value as AppSessionState.Authenticated
        assertThat(state.snapshot.username).isEqualTo("browser-account")
        assertThat(state.freshness).isEqualTo(SessionFreshness.Verified)
    }

    @Test
    fun startupAfterLogoutDoesNotImportBrowserToken() = runTest {
        val cookies = CookieStore()
        var browserReads = 0
        val bridge = CookieSyncBridge(baseUrl, cookies,
            readCookieHeader = { browserReads++; "_t=stale-browser-token" }, flushCookies = {})
        val coordinator = SessionCoordinator(cookies, SessionStore(),
            FakeSessionBootstrapClient(currentUsername = "baba"), bridge)

        coordinator.start()

        assertThat(browserReads).isEqualTo(0)
        assertThat(coordinator.sessionState.value).isEqualTo(AppSessionState.Anonymous)
        assertThat(cookies.hasAuthenticatedCookies()).isFalse()
    }

    @Test
    fun startupDoesNotReplaceNativeRotationWithStaleBrowserToken() = runTest {
        val cookies = CookieStore().apply {
            save(baseUrl.toHttpUrl(), listOf(Cookie.Builder().name("_t").value("native-new")
                .hostOnlyDomain("linux.do").path("/").build()))
        }
        var browserReads = 0
        val bridge = CookieSyncBridge(baseUrl, cookies,
            readCookieHeader = { browserReads++; "_t=browser-old" }, flushCookies = {})
        val coordinator = SessionCoordinator(cookies, SessionStore(),
            FakeSessionBootstrapClient(currentUsername = "baba"), bridge)

        coordinator.start()

        assertThat(browserReads).isEqualTo(0)
        assertThat(cookies.load(baseUrl.toHttpUrl()).single().value).isEqualTo("native-new")
    }

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
    fun newerKnownWebViewLoginWinsOverLateStartupRestore() = runTest {
        val cookieStore = CookieStore().apply {
            save(
                baseUrl.toHttpUrl(),
                listOf(Cookie.Builder().name("_t").value("token").domain("linux.do").path("/").build()),
            )
        }
        val startupUser = CompletableDeferred<SessionUserSummary?>()
        val bridge = CookieSyncBridge(
            baseUrl = baseUrl,
            cookieStore = cookieStore,
            readCookieHeader = { "_t=token" },
            writeCookieToWebView = { _, _ -> },
            flushCookies = {},
            clearCookies = { onComplete -> onComplete?.invoke() },
        )
        val coordinator = SessionCoordinator(
            cookieStore = cookieStore,
            sessionStore = SessionStore(),
            apiClient = object : SessionBootstrapClient {
                override suspend fun fetchBootstrap(): ParsedBootstrap = ParsedBootstrap(null)
                override suspend fun fetchCurrentUser(): SessionUserSummary? = startupUser.await()
            },
            cookieSyncBridge = bridge,
            now = { 9L },
        )

        val startup = launch { coordinator.start() }
        runCurrent()
        coordinator.completeLoginFromWebViewStaged("new-user")
        startupUser.complete(SessionUserSummary(username = "stale-user"))
        startup.join()

        val state = coordinator.sessionState.value as AppSessionState.Authenticated
        assertThat(state.snapshot.username).isEqualTo("new-user")
    }

    @Test
    fun start_restoresCurrentUserAvatarFromSessionEndpoint() = runTest {
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
        val sessionStore = SessionStore()
        val coordinator = SessionCoordinator(
            cookieStore = cookieStore,
            sessionStore = sessionStore,
            apiClient = FakeSessionBootstrapClient(
                currentUser = SessionUserSummary(
                    username = "baba",
                    displayName = "Baba",
                    avatarTemplate = "/user_avatar/linux.do/baba/{size}/1.png",
                ),
            ),
            now = { 88L },
        )

        coordinator.start()

        val snapshot = sessionStore.snapshot()
        assertThat(snapshot).isEqualTo(
            SessionSnapshot(
                username = "baba",
                displayName = "Baba",
                avatarTemplate = "/user_avatar/linux.do/baba/{size}/1.png",
                lastValidatedAt = 88L,
            ),
        )
        assertThat(coordinator.sessionState.value).isEqualTo(
            AppSessionState.Authenticated(
                snapshot = snapshot!!,
                freshness = SessionFreshness.Verified,
            ),
        )
    }

    @Test
    fun startupWithStaleCookiesAndNoCache_requiresRecoveryWithoutClearingCookies() = runTest {
        // A bootstrap response with no current-user could be a Cloudflare
        // challenge or a transient parse miss. Keep the cookies so the next
        // WebView attempt can complete the challenge.
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

        assertThat(cookieStore.hasAuthenticatedCookies()).isTrue()
        assertThat(webViewCookiesCleared).isFalse()
        assertThat(coordinator.sessionState.value).isEqualTo(
            AppSessionState.Expired(SessionExpiredReason.ChallengeRequired),
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

        assertThat(cookieStore.hasAuthenticatedCookies()).isTrue()
        assertThat(sessionStore.snapshot()).isEqualTo(
            SessionSnapshot(
                username = "baba",
                displayName = "Baba",
                avatarTemplate = "",
                lastValidatedAt = 1L,
            ),
        )
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
    fun repeatedServerLogoutKeepsTheOriginalExpiredSnapshot() = runTest {
        val snapshot = SessionSnapshot(
            username = "baba",
            displayName = "Baba",
            avatarTemplate = "/user_avatar/linux.do/baba/{size}/1.png",
            lastValidatedAt = 1L,
        )
        val sessionStore = SessionStore().apply { save(snapshot) }
        val coordinator = SessionCoordinator(
            cookieStore = CookieStore(),
            sessionStore = sessionStore,
            apiClient = FakeSessionBootstrapClient(),
        )

        coordinator.markExpired(SessionExpiredReason.ServerLoggedOut)
        coordinator.markExpired(SessionExpiredReason.ServerLoggedOut)

        assertThat(coordinator.sessionState.value).isEqualTo(
            AppSessionState.Expired(
                reason = SessionExpiredReason.ServerLoggedOut,
                snapshot = snapshot,
            ),
        )
    }

    @Test
    fun recoveryFailureDoesNotClearCookiesOrCachedIdentity() = runTest {
        val cookieStore = CookieStore().apply {
            save(
                baseUrl.toHttpUrl(),
                listOf(Cookie.Builder().name("_t").value("token").domain("linux.do").path("/").build()),
            )
        }
        val snapshot = SessionSnapshot(
            username = "baba",
            displayName = "Baba",
            avatarTemplate = "",
            lastValidatedAt = 1L,
        )
        val sessionStore = SessionStore().apply { save(snapshot) }
        val coordinator = SessionCoordinator(
            cookieStore = cookieStore,
            sessionStore = sessionStore,
            apiClient = FakeSessionBootstrapClient(),
        )

        coordinator.markExpired(SessionExpiredReason.RecoveryFailed)

        assertThat(cookieStore.hasAuthenticatedCookies()).isTrue()
        assertThat(sessionStore.snapshot()).isEqualTo(snapshot)
        assertThat(coordinator.sessionState.value).isEqualTo(
            AppSessionState.Expired(
                reason = SessionExpiredReason.RecoveryFailed,
                snapshot = snapshot,
            ),
        )
    }

    @Test
    fun staleRequestLogoutCannotExpireANewerWebViewLogin() = runTest {
        val cookieStore = CookieStore().apply {
            save(
                baseUrl.toHttpUrl(),
                listOf(Cookie.Builder().name("_t").value("old-token").domain("linux.do").path("/").build()),
            )
        }
        val coordinator = SessionCoordinator(
            cookieStore = cookieStore,
            sessionStore = SessionStore(),
            apiClient = FakeSessionBootstrapClient(),
            now = { 42L },
            cookieSyncBridge = CookieSyncBridge(
                baseUrl = baseUrl,
                cookieStore = cookieStore,
                readCookieHeader = { "_t=new-token" },
                writeCookieToWebView = { _, _ -> },
                flushCookies = {},
                clearCookies = { onComplete -> onComplete?.invoke() },
            ),
        )
        val staleRequestGeneration = coordinator.currentOperationGeneration()

        coordinator.completeLoginFromWebViewStaged("new-user")
        coordinator.markExpiredIfCurrent(
            reason = SessionExpiredReason.ServerLoggedOut,
            expectedGeneration = staleRequestGeneration,
        )

        assertThat(cookieStore.hasAuthenticatedCookies()).isTrue()
        assertThat(coordinator.sessionState.value).isEqualTo(
            AppSessionState.Authenticated(
                snapshot = SessionSnapshot(
                    username = "new-user",
                    displayName = "new-user",
                    avatarTemplate = "",
                    lastValidatedAt = 42L,
                ),
                freshness = SessionFreshness.WebView,
            ),
        )
    }

    @Test
    fun completeLoginFromWebViewStaged_marksAuthenticatedWithoutHittingApi() = runTest {
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

        coordinator.completeLoginFromWebViewStaged(username = "baba")

        assertThat(cookieStore.hasAuthenticatedCookies()).isTrue()
        assertThat(coordinator.sessionState.value).isEqualTo(
            AppSessionState.Authenticated(
                snapshot = SessionSnapshot(
                    username = "baba",
                    displayName = "baba",
                    avatarTemplate = "",
                    lastValidatedAt = 99L,
                ),
                freshness = SessionFreshness.WebView,
            ),
        )
    }

    @Test
    fun completeLoginFromWebViewStaged_usesWebViewProfilePayload() = runTest {
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
            apiClient = FakeSessionBootstrapClient(currentUsernameError = SessionLoggedOutException()),
            cookieSyncBridge = bridge,
            now = { 100L },
        )

        coordinator.completeLoginFromWebViewStaged(
            username = "baba",
            displayName = "Baba",
            avatarTemplate = "/user_avatar/linux.do/baba/{size}/1.png",
        )

        assertThat(coordinator.sessionState.value).isEqualTo(
            AppSessionState.Authenticated(
                snapshot = SessionSnapshot(
                    username = "baba",
                    displayName = "Baba",
                    avatarTemplate = "/user_avatar/linux.do/baba/{size}/1.png",
                    lastValidatedAt = 100L,
                ),
                freshness = SessionFreshness.WebView,
            ),
        )
    }

    @Test
    fun completeLoginFromWebViewStaged_keepsCachedDisplayNameAndAvatarWhenUsernameMatches() = runTest {
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

        coordinator.completeLoginFromWebViewStaged(username = "baba")

        val state = coordinator.sessionState.value
        check(state is AppSessionState.Authenticated) { "expected Authenticated, got $state" }
        assertThat(state.snapshot.displayName).isEqualTo("Baba The Goat")
        assertThat(state.snapshot.avatarTemplate).isEqualTo("https://example/avatar/{size}.png")
        assertThat(state.snapshot.lastValidatedAt).isEqualTo(200L)
    }

    @Test
    fun completeLoginFromWebViewStaged_fallsBackToAnonymousWhenCookiesMissing() = runTest {
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

        coordinator.completeLoginFromWebViewStaged(username = "baba")

        assertThat(coordinator.sessionState.value).isEqualTo(AppSessionState.Anonymous)
    }

    @Test
    fun logoutClearsDurableIdentityBeforeWebViewCallbackAndSurvivesCallerCancellation() = runTest {
        val cookieStore = CookieStore().apply {
            save(
                baseUrl.toHttpUrl(),
                listOf(Cookie.Builder().name("_t").value("token").domain("linux.do").path("/").build()),
            )
        }
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
        var clearCallback: (() -> Unit)? = null
        val bridge = CookieSyncBridge(
            baseUrl = baseUrl,
            cookieStore = cookieStore,
            readCookieHeader = { "_t=token" },
            writeCookieToWebView = { _, _ -> },
            flushCookies = {},
            clearCookies = { callback -> clearCallback = callback },
        )
        val applicationScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val coordinator = SessionCoordinator(
            cookieStore = cookieStore,
            sessionStore = sessionStore,
            apiClient = FakeSessionBootstrapClient(),
            cookieSyncBridge = bridge,
            applicationScope = applicationScope,
            persistenceDispatcher = StandardTestDispatcher(testScheduler),
        )

        val logoutJob = coordinator.onLogout()
        runCurrent()

        assertThat(coordinator.sessionState.value).isEqualTo(AppSessionState.Anonymous)
        assertThat(sessionStore.snapshot()).isNull()
        assertThat(cookieStore.hasAuthenticatedCookies()).isFalse()

        val caller = launch { logoutJob.join() }
        caller.cancel()
        clearCallback?.invoke()
        logoutJob.join()
    }
    private class FakeSessionBootstrapClient(
        private val currentUser: SessionUserSummary? = null,
        private val currentUsername: String? = null,
        private val bootstrapUsername: String? = null,
        private val currentUsernameError: Throwable? = null,
    ) : SessionBootstrapClient {
        override suspend fun fetchBootstrap(): ParsedBootstrap =
            ParsedBootstrap(currentUsername = bootstrapUsername)

        override suspend fun fetchCurrentUser(): SessionUserSummary? {
            currentUsernameError?.let { throw it }
            return currentUser ?: currentUsername?.let { username ->
                SessionUserSummary(username = username)
            }
        }

        override suspend fun fetchCurrentUsername(): String? {
            currentUsernameError?.let { throw it }
            return currentUsername
        }
    }
}
