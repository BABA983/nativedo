package io.github.baba983.nativedo.core.session

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.UserProfile
import io.github.baba983.nativedo.core.network.ParsedBootstrap
import io.github.baba983.nativedo.core.network.SessionRecoveryRequiredException
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.core.storage.SessionStore
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

class SessionCoordinatorTest {
    @Test
    fun start_withCookiesAndStoredSnapshot_emitsRestoringThenVerifiedAuthentication() = runTest {
        val cookieStore = cookieStoreWithAuthenticatedSession()
        val cachedSnapshot = sessionSnapshot(
            displayName = "Cached Baba",
            avatarTemplate = "/user_avatar/linux.do/baba/{size}/cached.png",
        )
        val sessionStore = SessionStore().apply {
            save(cachedSnapshot)
        }
        val bridge = FakeCookieSyncBridge(cookieStore = cookieStore)
        val expectedSnapshot = sessionSnapshot(
            displayName = "Baba",
            avatarTemplate = "/user_avatar/linux.do/baba/120/verified.gif",
            lastValidatedAt = 42L,
        )
        val coordinator = SessionCoordinator(
            cookieStore = cookieStore,
            sessionStore = sessionStore,
            apiClient = FakeSessionApiClient(
                currentUsername = null,
                currentSessionUsername = "baba",
                profile = UserProfile(
                    username = "baba",
                    displayName = "Baba",
                    bio = "",
                    avatarUrl = "/user_avatar/linux.do/baba/{size}/verified.png",
                    animatedAvatarUrl = "/user_avatar/linux.do/baba/120/verified.gif",
                ),
            ),
            cookieSyncBridge = bridge,
            now = { 42L },
        )

        coordinator.sessionState.test {
            assertThat(awaitItem()).isEqualTo(AppSessionState.Unknown)

            coordinator.events.test {
                backgroundScope.launch {
                    coordinator.start()
                }

                assertThat(awaitItem()).isEqualTo(SessionEvent.SessionVerified(expectedSnapshot))
                cancelAndIgnoreRemainingEvents()
            }

            assertThat(awaitItem()).isEqualTo(AppSessionState.Restoring(snapshot = cachedSnapshot))
            assertAuthenticated(awaitItem(), expectedSnapshot, freshness = SessionFreshness.Verified)
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(bridge.syncFromWebViewCalls).isEqualTo(0)
        assertThat(coordinator.authVersion.value).isEqualTo(1L)
        assertThat(sessionStore.snapshot()).isEqualTo(expectedSnapshot)
        assertThat(coordinator.currentSnapshot()).isEqualTo(expectedSnapshot)
    }

    @Test
    fun completeLoginFromWebView_syncsCookiesAndEmitsLoginCompleted() = runTest {
        val cookieStore = CookieStore()
        val bridge = FakeCookieSyncBridge(
            cookieStore = cookieStore,
            cookies = authenticatedCookies(),
        )
        val expectedSnapshot = sessionSnapshot(
            displayName = "Baba",
            avatarTemplate = "/user_avatar/linux.do/baba/{size}/1.png",
            lastValidatedAt = 42L,
        )
        val coordinator = SessionCoordinator(
            cookieStore = cookieStore,
            sessionStore = SessionStore(),
            apiClient = FakeSessionApiClient(
                currentUsername = "baba",
                profile = UserProfile(
                    username = "baba",
                    displayName = "Baba",
                    bio = "",
                    avatarUrl = "/user_avatar/linux.do/baba/{size}/1.png",
                ),
            ),
            cookieSyncBridge = bridge,
            now = { 42L },
        )

        coordinator.events.test {
            backgroundScope.launch {
                coordinator.completeLoginFromWebView()
            }

            assertThat(awaitItem()).isEqualTo(SessionEvent.LoginCompleted(expectedSnapshot))
            cancelAndIgnoreRemainingEvents()
        }

        assertAuthenticated(
            coordinator.sessionState.value,
            expectedSnapshot,
            freshness = SessionFreshness.Verified,
        )
        assertThat(bridge.syncFromWebViewCalls).isEqualTo(1)
        assertThat(coordinator.authVersion.value).isEqualTo(1L)
        assertThat(coordinator.currentSnapshot()).isEqualTo(expectedSnapshot)
    }

    @Test
    fun markExpired_clearsSnapshotAndCookies_emitsExpiredEventAndBumpsAuthVersion() = runTest {
        val cookieStore = cookieStoreWithAuthenticatedSession()
        val cachedSnapshot = sessionSnapshot()
        val sessionStore = SessionStore().apply {
            save(cachedSnapshot)
        }
        val bridge = FakeCookieSyncBridge(cookieStore = cookieStore)
        val coordinator = SessionCoordinator(
            cookieStore = cookieStore,
            sessionStore = sessionStore,
            apiClient = FakeSessionApiClient(currentUsername = "baba"),
            cookieSyncBridge = bridge,
        )

        coordinator.events.test {
            coordinator.markExpired(SessionExpiredReason.ChallengeRequired)

            assertThat(awaitItem()).isEqualTo(
                SessionEvent.SessionExpired(SessionExpiredReason.ChallengeRequired),
            )
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(bridge.clearCalls).isEqualTo(1)
        assertThat(cookieStore.hasSessionCookies()).isFalse()
        assertThat(sessionStore.snapshot()).isNull()
        assertThat(coordinator.authVersion.value).isEqualTo(1L)
        assertThat(coordinator.sessionState.value).isEqualTo(
            AppSessionState.Expired(
                reason = SessionExpiredReason.ChallengeRequired,
                snapshot = cachedSnapshot,
            ),
        )
    }

    @Test
    fun onLogout_clearsSnapshotAndCookies_emitsLogoutEventAndBumpsAuthVersion() = runTest {
        val cookieStore = cookieStoreWithAuthenticatedSession()
        val sessionStore = SessionStore().apply {
            save(sessionSnapshot())
        }
        val bridge = FakeCookieSyncBridge(cookieStore = cookieStore)
        val coordinator = SessionCoordinator(
            cookieStore = cookieStore,
            sessionStore = sessionStore,
            apiClient = FakeSessionApiClient(currentUsername = "baba"),
            cookieSyncBridge = bridge,
        )

        coordinator.events.test {
            coordinator.onLogout()

            assertThat(awaitItem()).isEqualTo(SessionEvent.LogoutCompleted)
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(bridge.clearCalls).isEqualTo(1)
        assertThat(cookieStore.hasSessionCookies()).isFalse()
        assertThat(sessionStore.snapshot()).isNull()
        assertThat(coordinator.authVersion.value).isEqualTo(1L)
        assertThat(coordinator.sessionState.value).isEqualTo(AppSessionState.Anonymous)
    }

    @Test
    fun start_withoutCookies_clearsSnapshotAndBecomesAnonymous() = runTest {
        val sessionStore = SessionStore().apply {
            save(sessionSnapshot())
        }
        val coordinator = SessionCoordinator(
            cookieStore = CookieStore(),
            sessionStore = sessionStore,
            apiClient = FakeSessionApiClient(currentUsername = "baba"),
        )

        coordinator.start()

        assertThat(coordinator.sessionState.value).isEqualTo(AppSessionState.Anonymous)
        assertThat(sessionStore.snapshot()).isNull()
        assertThat(coordinator.authVersion.value).isEqualTo(1L)
    }

    @Test
    fun start_withRecoveryRequiredAndNoCachedSnapshot_marksExpiredChallengeRequired() = runTest {
        val cookieStore = cookieStoreWithAuthenticatedSession()
        val coordinator = SessionCoordinator(
            cookieStore = cookieStore,
            sessionStore = SessionStore(),
            apiClient = FakeSessionApiClient(
                currentUsername = "baba",
                bootstrapError = SessionRecoveryRequiredException(),
            ),
        )

        coordinator.events.test {
            coordinator.start()

            assertThat(awaitItem()).isEqualTo(
                SessionEvent.SessionExpired(SessionExpiredReason.ChallengeRequired),
            )
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(cookieStore.hasSessionCookies()).isFalse()
        assertThat(coordinator.authVersion.value).isEqualTo(1L)
        assertThat(coordinator.sessionState.value).isEqualTo(
            AppSessionState.Expired(
                reason = SessionExpiredReason.ChallengeRequired,
                snapshot = null,
            ),
        )
    }

    @Test
    fun start_withStoredSnapshotAndIoException_keepsCachedAuthentication() = runTest {
        val cookieStore = cookieStoreWithAuthenticatedSession()
        val cachedSnapshot = sessionSnapshot(
            displayName = "Cached Baba",
            avatarTemplate = "/user_avatar/linux.do/baba/{size}/cached.png",
        )
        val sessionStore = SessionStore().apply {
            save(cachedSnapshot)
        }
        val coordinator = SessionCoordinator(
            cookieStore = cookieStore,
            sessionStore = sessionStore,
            apiClient = FakeSessionApiClient(
                currentUsername = "baba",
                bootstrapError = IOException("network down"),
            ),
        )

        coordinator.start()

        assertAuthenticated(
            coordinator.sessionState.value,
            cachedSnapshot,
            freshness = SessionFreshness.Cached,
        )
        assertThat(sessionStore.snapshot()).isEqualTo(cachedSnapshot)
        assertThat(coordinator.authVersion.value).isEqualTo(0L)
    }

    @Test
    fun start_withIoExceptionAndNoCachedSnapshot_marksExpiredAndBumpsAuthVersion() = runTest {
        val coordinator = SessionCoordinator(
            cookieStore = cookieStoreWithAuthenticatedSession(),
            sessionStore = SessionStore(),
            apiClient = FakeSessionApiClient(
                currentUsername = "baba",
                bootstrapError = IOException("network down"),
            ),
        )

        coordinator.start()

        assertThat(coordinator.sessionState.value).isEqualTo(
            AppSessionState.Expired(
                reason = SessionExpiredReason.RecoveryFailed,
                snapshot = null,
            ),
        )
        assertThat(coordinator.authVersion.value).isEqualTo(1L)
    }

    @Test
    fun start_whenLogoutHappensDuringRestore_doesNotOverwriteWithAuthenticatedOrEmitSuccessEvent() = runTest {
        val cookieStore = cookieStoreWithAuthenticatedSession()
        val cachedSnapshot = sessionSnapshot()
        val sessionStore = SessionStore().apply {
            save(cachedSnapshot)
        }
        val unblockBootstrap = CompletableDeferred<Unit>()
        val apiClient = FakeSessionApiClient(
            currentUsername = "baba",
            awaitBootstrap = unblockBootstrap,
        )
        val coordinator = SessionCoordinator(
            cookieStore = cookieStore,
            sessionStore = sessionStore,
            apiClient = apiClient,
        )

        coordinator.events.test {
            val restoreJob = backgroundScope.launch {
                coordinator.start()
            }
            runCurrent()

            assertThat(coordinator.sessionState.value)
                .isEqualTo(AppSessionState.Restoring(snapshot = cachedSnapshot))

            coordinator.onLogout()
            unblockBootstrap.complete(Unit)
            restoreJob.join()

            assertThat(coordinator.sessionState.value).isEqualTo(AppSessionState.Anonymous)
            assertThat(sessionStore.snapshot()).isNull()
            assertThat(awaitItem()).isEqualTo(SessionEvent.LogoutCompleted)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(coordinator.authVersion.value).isEqualTo(1L)
    }

    @Test
    fun start_whenExpiryHappensDuringRestore_doesNotOverwriteWithAuthenticatedOrEmitSuccessEvent() = runTest {
        val cookieStore = cookieStoreWithAuthenticatedSession()
        val cachedSnapshot = sessionSnapshot()
        val sessionStore = SessionStore().apply {
            save(cachedSnapshot)
        }
        val unblockBootstrap = CompletableDeferred<Unit>()
        val apiClient = FakeSessionApiClient(
            currentUsername = "baba",
            awaitBootstrap = unblockBootstrap,
        )
        val coordinator = SessionCoordinator(
            cookieStore = cookieStore,
            sessionStore = sessionStore,
            apiClient = apiClient,
        )

        coordinator.events.test {
            val restoreJob = backgroundScope.launch {
                coordinator.start()
            }
            runCurrent()

            assertThat(coordinator.sessionState.value)
                .isEqualTo(AppSessionState.Restoring(snapshot = cachedSnapshot))

            coordinator.markExpired(SessionExpiredReason.ServerLoggedOut)
            unblockBootstrap.complete(Unit)
            restoreJob.join()

            assertThat(coordinator.sessionState.value).isEqualTo(
                AppSessionState.Expired(
                    reason = SessionExpiredReason.ServerLoggedOut,
                    snapshot = cachedSnapshot,
                ),
            )
            assertThat(sessionStore.snapshot()).isNull()
            assertThat(awaitItem()).isEqualTo(
                SessionEvent.SessionExpired(SessionExpiredReason.ServerLoggedOut),
            )
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(coordinator.authVersion.value).isEqualTo(1L)
    }

    @Test
    fun start_usesCachedProfileDetailsWhenProfileLookupReturnsNull() = runTest {
        val cookieStore = cookieStoreWithAuthenticatedSession()
        val cachedSnapshot = sessionSnapshot(
            displayName = "Cached Baba",
            avatarTemplate = "/user_avatar/linux.do/baba/{size}/cached.png",
        )
        val sessionStore = SessionStore().apply {
            save(cachedSnapshot)
        }
        val coordinator = SessionCoordinator(
            cookieStore = cookieStore,
            sessionStore = sessionStore,
            apiClient = FakeSessionApiClient(
                currentUsername = null,
                currentSessionUsername = "baba",
                profile = null,
            ),
            now = { 42L },
        )

        coordinator.start()

        assertAuthenticated(
            coordinator.sessionState.value,
            sessionSnapshot(
                displayName = "Cached Baba",
                avatarTemplate = "/user_avatar/linux.do/baba/{size}/cached.png",
                lastValidatedAt = 42L,
            ),
            freshness = SessionFreshness.Verified,
        )
        assertThat(sessionStore.snapshot()?.displayName).isEqualTo("Cached Baba")
        assertThat(sessionStore.snapshot()?.avatarTemplate)
            .isEqualTo("/user_avatar/linux.do/baba/{size}/cached.png")
    }

    @Test
    fun start_withOnlyForumSessionCookie_becomesAnonymousAndClearsSnapshot() = runTest {
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
        val sessionStore = SessionStore().apply {
            save(sessionSnapshot())
        }
        val coordinator = SessionCoordinator(
            cookieStore = cookieStore,
            sessionStore = sessionStore,
            apiClient = FakeSessionApiClient(currentUsername = "baba"),
        )

        coordinator.start()

        assertThat(coordinator.sessionState.value).isEqualTo(AppSessionState.Anonymous)
        assertThat(sessionStore.snapshot()).isNull()
    }
}

private fun cookieStoreWithAuthenticatedSession(): CookieStore = CookieStore().apply {
    save("https://linux.do".toHttpUrl(), authenticatedCookies())
}

private fun authenticatedCookies(): List<Cookie> = listOf(
    Cookie.Builder()
        .name("_t")
        .value("auth")
        .domain("linux.do")
        .path("/")
        .build(),
)

private fun sessionSnapshot(
    username: String = "baba",
    displayName: String = "Baba",
    avatarTemplate: String = "",
    lastValidatedAt: Long = 1L,
): SessionSnapshot = SessionSnapshot(
    username = username,
    displayName = displayName,
    avatarTemplate = avatarTemplate,
    lastValidatedAt = lastValidatedAt,
)

private fun assertAuthenticated(
    state: AppSessionState,
    expectedSnapshot: SessionSnapshot,
    freshness: SessionFreshness,
) {
    val authenticated = state as AppSessionState.Authenticated
    assertThat(authenticated.snapshot).isEqualTo(expectedSnapshot)
    assertThat(authenticated.freshness).isEqualTo(freshness)
}

private class FakeSessionApiClient(
    private val currentUsername: String?,
    private val currentSessionUsername: String? = null,
    private val profile: UserProfile? = null,
    private val bootstrapError: Throwable? = null,
    private val awaitBootstrap: CompletableDeferred<Unit>? = null,
    private val currentSessionError: Throwable? = null,
    private val profileError: Throwable? = null,
) : SessionBootstrapClient {
    override suspend fun fetchBootstrap(): ParsedBootstrap {
        awaitBootstrap?.await()
        bootstrapError?.let { throw it }
        return ParsedBootstrap(
            currentUsername = currentUsername,
            csrfToken = "",
        )
    }

    override suspend fun fetchCurrentUsername(): String? {
        currentSessionError?.let { throw it }
        return currentSessionUsername
    }

    override suspend fun fetchProfile(username: String): UserProfile? {
        profileError?.let { throw it }
        return profile
    }
}

private class FakeCookieSyncBridge(
    private val cookieStore: CookieStore,
    private val cookies: List<Cookie> = emptyList(),
) : CookieSyncBridge(baseUrl = "https://linux.do/", cookieStore = cookieStore) {
    var syncFromWebViewCalls: Int = 0
        private set
    var clearCalls: Int = 0
        private set

    override fun syncFromWebView(url: String): Boolean {
        syncFromWebViewCalls += 1
        if (cookies.isEmpty()) {
            return false
        }
        sync(url = url, cookies = cookies)
        return true
    }

    override fun clear() {
        clearCalls += 1
        cookieStore.clear()
    }
}
