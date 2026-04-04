package io.github.baba983.nativedo.feature.home.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.session.SessionExpiredReason
import org.junit.Test

class HomeRouteStateTest {
    @Test
    fun shouldRefreshHomeForHandledAuthVersion_authenticatedWithNewVersion_returnsTrue() {
        val shouldRefresh = shouldRefreshHomeForHandledAuthVersion(
            sessionState = AppSessionState.Authenticated(
                snapshot = homeSessionSnapshot(),
                freshness = io.github.baba983.nativedo.core.session.SessionFreshness.Verified,
            ),
            handledAuthVersion = 1L,
            currentAuthVersion = 2L,
        )

        assertThat(shouldRefresh).isTrue()
    }

    @Test
    fun shouldRefreshHomeForHandledAuthVersion_unknown_returnsFalse() {
        val shouldRefresh = shouldRefreshHomeForHandledAuthVersion(
            sessionState = AppSessionState.Unknown,
            handledAuthVersion = 1L,
            currentAuthVersion = 2L,
        )

        assertThat(shouldRefresh).isFalse()
    }

    @Test
    fun shouldRefreshHomeForHandledAuthVersion_expiredWithNewVersion_returnsTrue() {
        val shouldRefresh = shouldRefreshHomeForHandledAuthVersion(
            sessionState = AppSessionState.Expired(
                reason = SessionExpiredReason.ServerLoggedOut,
            ),
            handledAuthVersion = 1L,
            currentAuthVersion = 2L,
        )

        assertThat(shouldRefresh).isTrue()
    }
}

private fun homeSessionSnapshot() = io.github.baba983.nativedo.core.session.SessionSnapshot(
    username = "baba",
    displayName = "Baba",
    avatarTemplate = "",
    lastValidatedAt = 1L,
)
