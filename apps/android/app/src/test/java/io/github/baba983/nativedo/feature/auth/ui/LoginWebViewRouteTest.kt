package io.github.baba983.nativedo.feature.auth.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.session.SessionFreshness
import io.github.baba983.nativedo.core.session.SessionSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LoginWebViewRouteTest {
    @Test
    fun shouldAttemptLoginSessionRestore_requiresLinuxDoNonLoginPageAndIdleState() {
        assertThat(
            shouldAttemptLoginSessionRestore(
                currentUrl = "https://linux.do/t/topic/1",
                hasFinished = false,
                isRestoringSession = false,
            ),
        ).isTrue()

        assertThat(
            shouldAttemptLoginSessionRestore(
                currentUrl = "https://linux.do/login",
                hasFinished = false,
                isRestoringSession = false,
            ),
        ).isFalse()
        assertThat(
            shouldAttemptLoginSessionRestore(
                currentUrl = "https://example.com/",
                hasFinished = false,
                isRestoringSession = false,
            ),
        ).isFalse()
        assertThat(
            shouldAttemptLoginSessionRestore(
                currentUrl = "https://linux.do/",
                hasFinished = true,
                isRestoringSession = false,
            ),
        ).isFalse()
        assertThat(
            shouldAttemptLoginSessionRestore(
                currentUrl = "https://linux.do/",
                hasFinished = false,
                isRestoringSession = true,
            ),
        ).isFalse()
    }

    @Test
    fun awaitAuthenticatedSession_retriesUntilCookiesAppearAndCompletesLoginFromWebView() = runTest {
        val cookieStates = ArrayDeque(listOf(false, false, true))
        var completeLoginFromWebViewCalls = 0

        val authenticated = awaitAuthenticatedSession(
            hasAuthenticatedCookies = {
                cookieStates.removeFirstOrNull() ?: true
            },
            completeLoginFromWebView = {
                completeLoginFromWebViewCalls += 1
                AppSessionState.Authenticated(
                    snapshot = SessionSnapshot(
                        username = "baba",
                        displayName = "Baba",
                        avatarTemplate = "",
                        lastValidatedAt = 1L,
                    ),
                    freshness = SessionFreshness.Verified,
                )
            },
            maxAttempts = 4,
            delayMillis = 0L,
        )

        assertThat(authenticated).isTrue()
        assertThat(completeLoginFromWebViewCalls).isEqualTo(1)
    }

    @Test
    fun awaitAuthenticatedSession_returnsFalseWhenCookiesNeverAppear() = runTest {
        var completeLoginFromWebViewCalls = 0

        val authenticated = awaitAuthenticatedSession(
            hasAuthenticatedCookies = { false },
            completeLoginFromWebView = {
                completeLoginFromWebViewCalls += 1
                AppSessionState.Authenticated(
                    snapshot = SessionSnapshot(
                        username = "baba",
                        displayName = "Baba",
                        avatarTemplate = "",
                        lastValidatedAt = 1L,
                    ),
                    freshness = SessionFreshness.Verified,
                )
            },
            maxAttempts = 3,
            delayMillis = 0L,
        )

        assertThat(authenticated).isFalse()
        assertThat(completeLoginFromWebViewCalls).isEqualTo(0)
    }

    @Test
    fun awaitAuthenticatedSession_callsLoginCompletionImmediatelyWhenCookiesAreReady() = runTest {
        var completeLoginFromWebViewCalls = 0

        val authenticated = awaitAuthenticatedSession(
            hasAuthenticatedCookies = { true },
            completeLoginFromWebView = {
                completeLoginFromWebViewCalls += 1
                AppSessionState.Authenticated(
                    snapshot = SessionSnapshot(
                        username = "baba",
                        displayName = "Baba",
                        avatarTemplate = "",
                        lastValidatedAt = 1L,
                    ),
                    freshness = SessionFreshness.Verified,
                )
            },
            maxAttempts = 3,
            delayMillis = 0L,
        )

        assertThat(authenticated).isTrue()
        assertThat(completeLoginFromWebViewCalls).isEqualTo(1)
    }

    @Test
    fun awaitAuthenticatedSession_stopsRetryingAfterFirstLoginCompletionFailureWhenCookiesAreReady() = runTest {
        var completeLoginFromWebViewCalls = 0

        val authenticated = awaitAuthenticatedSession(
            hasAuthenticatedCookies = { true },
            completeLoginFromWebView = {
                completeLoginFromWebViewCalls += 1
                AppSessionState.Expired(
                    reason = io.github.baba983.nativedo.core.session.SessionExpiredReason.RecoveryFailed,
                )
            },
            maxAttempts = 3,
            delayMillis = 0L,
        )

        assertThat(authenticated).isFalse()
        assertThat(completeLoginFromWebViewCalls).isEqualTo(1)
    }
}
