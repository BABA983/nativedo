package io.github.baba983.nativedo.ui.auth

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import io.github.baba983.nativedo.core.session.CurrentUserExtractor
import io.github.baba983.nativedo.core.session.awaitAuthenticatedSession
import io.github.baba983.nativedo.core.web.decodeJavascriptString
import org.junit.Test

class LoginWebViewFragmentTest {
    @Test
    fun backPrioritizesRestoreThenSessionThenWebHistoryThenPop() {
        assertThat(loginWebBackAction(true, true, true)).isEqualTo(LoginWebBackAction.CONSUME_RESTORE)
        assertThat(loginWebBackAction(false, true, true)).isEqualTo(LoginWebBackAction.COMPLETE_SESSION)
        assertThat(loginWebBackAction(false, false, true)).isEqualTo(LoginWebBackAction.COMPLETE_SESSION)
        assertThat(loginWebBackAction(false, true, false)).isEqualTo(LoginWebBackAction.GO_WEB_HISTORY)
        assertThat(loginWebBackAction(false, false, false)).isEqualTo(LoginWebBackAction.POP_FRAGMENT)
    }

    @Test
    fun navigationKeepsOAuthHttpPagesInsideAndRoutesExternalSchemes() {
        assertThat(loginWebNavigationDecision("https://github.com/login/oauth"))
            .isEqualTo(LoginWebNavigationDecision.LoadInWebView)
        assertThat(loginWebNavigationDecision("mailto:admin@linux.do"))
            .isEqualTo(LoginWebNavigationDecision.OpenExternal)
        assertThat(loginWebNavigationDecision("javascript:alert(1)"))
            .isEqualTo(LoginWebNavigationDecision.Block)
        assertThat(loginWebNavigationDecision("file:///data/local/tmp/token"))
            .isEqualTo(LoginWebNavigationDecision.Block)
    }

    @Test
    fun intentNavigationUsesSafeHttpsFallback() {
        val decision = loginWebNavigationDecision(
            "intent://oauth#Intent;scheme=github;S.browser_fallback_url=https%3A%2F%2Fgithub.com%2Flogin;end",
        )

        assertThat(decision).isEqualTo(
            LoginWebNavigationDecision.LoadFallback("https://github.com/login"),
        )
    }

    @Test
    fun mainFrameErrorsBlockCompletionButSubresourcesDoNot() {
        assertThat(loginWebShouldShowMainFrameError(isForMainFrame = true)).isTrue()
        assertThat(loginWebShouldShowMainFrameError(isForMainFrame = false)).isFalse()
        assertThat(loginWebShouldShowMainFrameError(isForMainFrame = true, httpStatusCode = 503)).isTrue()
        assertThat(loginWebShouldShowMainFrameError(isForMainFrame = true, httpStatusCode = 302)).isFalse()

        assertThat(loginWebCanCompleteNavigation(navigationGeneration = 4, failedNavigationGeneration = 4)).isFalse()
        assertThat(loginWebCanCompleteNavigation(navigationGeneration = 5, failedNavigationGeneration = 4)).isTrue()
    }

    @Test
    fun downloadsKeepCookiesSameOriginOnly() {
        assertThat(loginWebDownloadDecision("https://linux.do/uploads/private.pdf", "https://linux.do/"))
            .isEqualTo(LoginWebDownloadDecision.AUTHENTICATED_SAME_ORIGIN)
        assertThat(loginWebDownloadDecision("https://cdn.example.com/public.pdf", "https://linux.do/"))
            .isEqualTo(LoginWebDownloadDecision.OPEN_EXTERNAL)
        assertThat(loginWebDownloadDecision("blob:https://linux.do/uuid", "https://linux.do/"))
            .isEqualTo(LoginWebDownloadDecision.BLOCK)
        assertThat(loginWebDownloadDecision("file:///data/data/token", "https://linux.do/"))
            .isEqualTo(LoginWebDownloadDecision.BLOCK)
    }

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
    fun awaitAuthenticatedSession_retriesTransientCompletionFailure() = runTest {
        var completionAttempts = 0

        val authenticated = awaitAuthenticatedSession(
            hasAuthenticatedCookies = { true },
            completeLogin = {
                completionAttempts += 1
                completionAttempts == 3
            },
            maxAttempts = 5,
            delayMillis = 1,
        )

        assertThat(authenticated).isTrue()
        assertThat(completionAttempts).isEqualTo(3)
    }

    @Test
    fun decodeJavascriptString_decodesEvaluateJavascriptStringResult() {
        assertThat(decodeJavascriptString("\"baba\"")).isEqualTo("baba")
        assertThat(decodeJavascriptString("null")).isEmpty()
        assertThat(decodeJavascriptString("")).isEmpty()
    }

    @Test
    fun decodeCurrentUserPayload_readsAvatarTemplate() {
        val currentUser = CurrentUserExtractor.decodeCurrentUserPayload(
            "\"{\\\"username\\\":\\\"baba\\\",\\\"name\\\":\\\"Baba\\\",\\\"avatar_template\\\":\\\"/user_avatar/linux.do/baba/{size}/1.png\\\"}\"",
        )

        assertThat(currentUser?.username).isEqualTo("baba")
        assertThat(currentUser?.displayName).isEqualTo("Baba")
        assertThat(currentUser?.avatarTemplate).isEqualTo("/user_avatar/linux.do/baba/{size}/1.png")
    }
}
