package io.github.baba983.nativedo.core.session

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
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
}

private class FakeSessionApiClient(
    private val currentUsername: String,
) : SessionBootstrapClient {
    override suspend fun fetchBootstrap(): ParsedBootstrap = ParsedBootstrap(
        currentUsername = currentUsername,
        csrfToken = "",
    )
}

private class FakeCsrfTokenProvider(
    private val token: String,
) : CsrfTokenProvider() {
    override suspend fun get(forceRefresh: Boolean): String = token
}
