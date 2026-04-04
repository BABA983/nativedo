package io.github.baba983.nativedo.feature.auth.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.session.SessionExpiredReason
import io.github.baba983.nativedo.core.session.SessionFreshness
import io.github.baba983.nativedo.core.session.SessionSnapshot
import org.junit.Test

class AuthRestoreGateStateTest {
    @Test
    fun unknownSession_showsGate() {
        assertThat(shouldShowAuthRestoreGate(AppSessionState.Unknown)).isTrue()
    }

    @Test
    fun restoringSession_showsGate() {
        assertThat(
            shouldShowAuthRestoreGate(
                AppSessionState.Restoring(
                    snapshot = SessionSnapshot(
                        username = "baba",
                        displayName = "Baba",
                        avatarTemplate = "",
                        lastValidatedAt = 1L,
                    ),
                ),
            ),
        ).isTrue()
    }

    @Test
    fun resolvedSession_hidesGate() {
        val authenticated = AppSessionState.Authenticated(
            snapshot = SessionSnapshot(
                username = "baba",
                displayName = "Baba",
                avatarTemplate = "",
                lastValidatedAt = 1L,
            ),
            freshness = SessionFreshness.Verified,
        )
        val expired = AppSessionState.Expired(
            reason = SessionExpiredReason.RecoveryFailed,
        )

        assertThat(shouldShowAuthRestoreGate(authenticated)).isFalse()
        assertThat(shouldShowAuthRestoreGate(AppSessionState.Anonymous)).isFalse()
        assertThat(shouldShowAuthRestoreGate(expired)).isFalse()
    }
}
