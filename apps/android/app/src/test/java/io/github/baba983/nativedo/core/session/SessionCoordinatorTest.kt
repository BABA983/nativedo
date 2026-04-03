package io.github.baba983.nativedo.core.session

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.UserProfile
import io.github.baba983.nativedo.core.network.CsrfTokenProvider
import io.github.baba983.nativedo.core.network.ParsedBootstrap
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.core.storage.SessionStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

class SessionCoordinatorTest {
    @Test
    fun restoreSession_emitsAuthenticatedWhenCookiesAndBootstrapUserExist() = runTest {
        val cookieStore = CookieStore().apply {
            save(
                "https://linux.do".toHttpUrl(),
                listOf(
                    Cookie.Builder()
                        .name("_forum_session")
                        .value("session")
                        .domain("linux.do")
                        .path("/")
                        .build(),
                ),
            )
        }
        val coordinator = SessionCoordinator(
            cookieStore = cookieStore,
            sessionStore = SessionStore(),
            apiClient = FakeSessionApiClient(currentUsername = "baba"),
            csrfTokenProvider = FakeCsrfTokenProvider(token = "csrf-token"),
        )

        coordinator.sessionState.test {
            assertThat(awaitItem()).isEqualTo(AppSessionState.Anonymous)

            backgroundScope.launch {
                coordinator.restoreSession()
            }

            assertThat(awaitItem()).isEqualTo(AppSessionState.Authenticating)
            assertThat(awaitItem()).isEqualTo(
                AppSessionState.Authenticated(
                    username = "baba",
                    csrfToken = "csrf-token",
                ),
            )
        }
    }

    @Test
    fun restoreSession_syncsPersistentCookiesBeforeCheckingSession() = runTest {
        val cookieStore = CookieStore()
        val bridge = FakeCookieSyncBridge(
            cookieStore = cookieStore,
            cookies = listOf(
                Cookie.Builder()
                    .name("_t")
                    .value("auth")
                    .domain("linux.do")
                    .path("/")
                    .build(),
            ),
        )
        val coordinator = SessionCoordinator(
            cookieStore = cookieStore,
            sessionStore = SessionStore(),
            apiClient = FakeSessionApiClient(currentUsername = "baba"),
            csrfTokenProvider = FakeCsrfTokenProvider(token = "csrf-token"),
            cookieSyncBridge = bridge,
        )

        coordinator.restoreSession()

        assertThat(bridge.syncFromWebViewCalls).isEqualTo(1)
        assertThat(coordinator.sessionState.value).isEqualTo(
            AppSessionState.Authenticated(
                username = "baba",
                csrfToken = "csrf-token",
            ),
        )
    }

    @Test
    fun restoreSession_preservesCookiesWhenBootstrapLookupFails() = runTest {
        val cookieStore = CookieStore()
        val bridge = FakeCookieSyncBridge(
            cookieStore = cookieStore,
            cookies = listOf(
                Cookie.Builder()
                    .name("_t")
                    .value("auth")
                    .domain("linux.do")
                    .path("/")
                    .build(),
            ),
        )
        val coordinator = SessionCoordinator(
            cookieStore = cookieStore,
            sessionStore = SessionStore(),
            apiClient = FakeSessionApiClient(currentUsername = null),
            csrfTokenProvider = FakeCsrfTokenProvider(token = "csrf-token"),
            cookieSyncBridge = bridge,
        )

        coordinator.restoreSession()

        assertThat(bridge.clearCalls).isEqualTo(0)
        assertThat(cookieStore.hasSessionCookies()).isTrue()
        assertThat(coordinator.sessionState.value).isEqualTo(AppSessionState.Expired)
    }

    @Test
    fun restoreSession_usesBootstrapCsrfWhenCsrfRefreshFails() = runTest {
        val cookieStore = CookieStore().apply {
            save(
                "https://linux.do".toHttpUrl(),
                listOf(
                    Cookie.Builder()
                        .name("_t")
                        .value("auth")
                        .domain("linux.do")
                        .path("/")
                        .build(),
                ),
            )
        }
        val coordinator = SessionCoordinator(
            cookieStore = cookieStore,
            sessionStore = SessionStore(),
            apiClient = FakeSessionApiClient(
                currentUsername = "baba",
                csrfToken = "bootstrap-csrf",
            ),
            csrfTokenProvider = ThrowingCsrfTokenProvider(),
        )

        coordinator.restoreSession()

        assertThat(coordinator.sessionState.value).isEqualTo(
            AppSessionState.Authenticated(
                username = "baba",
                csrfToken = "bootstrap-csrf",
            ),
        )
    }

    @Test
    fun restoreSession_includesAvatarTemplateWhenProfileLookupSucceeds() = runTest {
        val cookieStore = CookieStore().apply {
            save(
                "https://linux.do".toHttpUrl(),
                listOf(
                    Cookie.Builder()
                        .name("_t")
                        .value("auth")
                        .domain("linux.do")
                        .path("/")
                        .build(),
                ),
            )
        }
        val coordinator = SessionCoordinator(
            cookieStore = cookieStore,
            sessionStore = SessionStore(),
            apiClient = FakeSessionApiClient(
                currentUsername = null,
                currentSessionUsername = "baba",
                profile = UserProfile(
                    username = "baba",
                    displayName = "Baba",
                    bio = "",
                    avatarUrl = "/user_avatar/linux.do/baba/{size}/1.png",
                ),
            ),
            csrfTokenProvider = FakeCsrfTokenProvider(token = "csrf-token"),
        )

        coordinator.restoreSession()

        assertThat(coordinator.sessionState.value).isEqualTo(
            AppSessionState.Authenticated(
                username = "baba",
                csrfToken = "csrf-token",
                avatarTemplate = "/user_avatar/linux.do/baba/{size}/1.png",
            ),
        )
    }

    @Test
    fun restoreSession_usesCurrentSessionUsernameWhenBootstrapUserMissing() = runTest {
        val cookieStore = CookieStore().apply {
            save(
                "https://linux.do".toHttpUrl(),
                listOf(
                    Cookie.Builder()
                        .name("_t")
                        .value("auth")
                        .domain("linux.do")
                        .path("/")
                        .build(),
                ),
            )
        }
        val coordinator = SessionCoordinator(
            cookieStore = cookieStore,
            sessionStore = SessionStore(),
            apiClient = FakeSessionApiClient(
                currentUsername = null,
                currentSessionUsername = "baba",
            ),
            csrfTokenProvider = FakeCsrfTokenProvider(token = "csrf-token"),
        )

        coordinator.restoreSession()

        assertThat(coordinator.sessionState.value).isEqualTo(
            AppSessionState.Authenticated(
                username = "baba",
                csrfToken = "csrf-token",
            ),
        )
    }

    @Test
    fun restoreSession_fallsBackToBootstrapWhenCurrentSessionLookupFails() = runTest {
        val cookieStore = CookieStore().apply {
            save(
                "https://linux.do".toHttpUrl(),
                listOf(
                    Cookie.Builder()
                        .name("_t")
                        .value("auth")
                        .domain("linux.do")
                        .path("/")
                        .build(),
                ),
            )
        }
        val coordinator = SessionCoordinator(
            cookieStore = cookieStore,
            sessionStore = SessionStore(),
            apiClient = FakeSessionApiClient(
                currentUsername = "baba",
                throwOnFetchCurrentUsername = true,
            ),
            csrfTokenProvider = FakeCsrfTokenProvider(token = "csrf-token"),
        )

        coordinator.restoreSession()

        assertThat(coordinator.sessionState.value).isEqualTo(
            AppSessionState.Authenticated(
                username = "baba",
                csrfToken = "csrf-token",
            ),
        )
    }
}

private class FakeSessionApiClient(
    private val currentUsername: String?,
    private val currentSessionUsername: String? = null,
    private val csrfToken: String = "",
    private val profile: UserProfile? = null,
    private val throwOnFetchCurrentUsername: Boolean = false,
) : SessionBootstrapClient {
    override suspend fun fetchBootstrap(): ParsedBootstrap = ParsedBootstrap(
        currentUsername = currentUsername,
        csrfToken = csrfToken,
    )

    override suspend fun fetchCurrentUsername(): String? {
        if (throwOnFetchCurrentUsername) {
            throw IllegalStateException("current session lookup failed")
        }
        return currentSessionUsername
    }

    override suspend fun fetchProfile(username: String): UserProfile? = profile
}

private class FakeCsrfTokenProvider(
    private val token: String,
) : CsrfTokenProvider() {
    override suspend fun get(forceRefresh: Boolean): String = token
}

private class ThrowingCsrfTokenProvider : CsrfTokenProvider() {
    override suspend fun get(forceRefresh: Boolean): String {
        throw IllegalStateException("csrf refresh failed")
    }
}

private class FakeCookieSyncBridge(
    private val cookieStore: CookieStore,
    private val cookies: List<Cookie>,
) : CookieSyncBridge(baseUrl = "https://linux.do/", cookieStore = cookieStore) {
    var syncFromWebViewCalls: Int = 0
        private set
    var clearCalls: Int = 0
        private set

    override fun syncFromWebView(): Boolean {
        sync(url = "https://linux.do/", cookies = cookies)
        syncFromWebViewCalls += 1
        return true
    }

    override fun clear() {
        clearCalls += 1
        cookieStore.clear()
    }
}
