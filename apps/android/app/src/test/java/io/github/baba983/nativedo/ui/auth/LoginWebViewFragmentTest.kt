package io.github.baba983.nativedo.ui.auth

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LoginWebViewFragmentTest {
    @Test
    fun shouldAttemptLoginSessionRestore_requiresLinuxDoPageOutsideLogin() {
        assertThat(
            shouldAttemptLoginSessionRestore(
                currentUrl = "https://linux.do/",
                baseUrl = "https://linux.do/",
                hasFinished = false,
                isRestoringSession = false,
            ),
        ).isTrue()
        assertThat(
            shouldAttemptLoginSessionRestore(
                currentUrl = "https://linux.do/login",
                baseUrl = "https://linux.do/",
                hasFinished = false,
                isRestoringSession = false,
            ),
        ).isFalse()
        assertThat(
            shouldAttemptLoginSessionRestore(
                currentUrl = "https://example.com/",
                baseUrl = "https://linux.do/",
                hasFinished = false,
                isRestoringSession = false,
            ),
        ).isFalse()
        assertThat(
            shouldAttemptLoginSessionRestore(
                currentUrl = "https://linux.do.evil.com/",
                baseUrl = "https://linux.do/",
                hasFinished = false,
                isRestoringSession = false,
            ),
        ).isFalse()
        assertThat(
            shouldAttemptLoginSessionRestore(
                currentUrl = "https://linux.do@evil.com/",
                baseUrl = "https://linux.do/",
                hasFinished = false,
                isRestoringSession = false,
            ),
        ).isFalse()
        assertThat(
            shouldAttemptLoginSessionRestore(
                currentUrl = "https://linux.do:8443/",
                baseUrl = "https://linux.do/",
                hasFinished = false,
                isRestoringSession = false,
            ),
        ).isFalse()
        assertThat(
            shouldAttemptLoginSessionRestore(
                currentUrl = "https://linux.do/?next=/login",
                baseUrl = "https://linux.do/",
                hasFinished = false,
                isRestoringSession = false,
            ),
        ).isTrue()
    }

    @Test
    fun awaitAuthenticatedSession_waitsForCookiesBeforeCompletingLogin() = runTest {
        var attempts = 0
        var completed = false

        val authenticated = awaitAuthenticatedSession(
            hasAuthenticatedCookies = {
                attempts += 1
                attempts == 3
            },
            completeLogin = {
                completed = true
                true
            },
            maxAttempts = 5,
            delayMillis = 1,
        )

        assertThat(authenticated).isTrue()
        assertThat(completed).isTrue()
        assertThat(attempts).isEqualTo(3)
    }

    @Test
    fun decodeJavascriptString_decodesEvaluateJavascriptStringResult() {
        assertThat(decodeJavascriptString("\"baba\"")).isEqualTo("baba")
        assertThat(decodeJavascriptString("null")).isEmpty()
        assertThat(decodeJavascriptString("")).isEmpty()
    }
}
