package io.github.baba983.nativedo.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.session.SessionExpiredReason
import io.github.baba983.nativedo.core.session.SessionFreshness
import io.github.baba983.nativedo.core.session.SessionSnapshot
import org.junit.Test

class NativeDoChromeStateTest {

    @Test
    fun anonymousSession_usesDefaultTitle() {
        assertThat(NativeDoChromeState.from(AppSessionState.Anonymous))
            .isEqualTo(NativeDoChromeState())
    }

    @Test
    fun restoringSession_usesCachedSnapshotTitle() {
        assertThat(
            NativeDoChromeState.from(
                AppSessionState.Restoring(
                    snapshot = SessionSnapshot(
                        username = "baba",
                        displayName = "Baba",
                        avatarTemplate = "",
                        lastValidatedAt = 1L,
                    ),
                ),
            ),
        )
            .isEqualTo(NativeDoChromeState(title = "Baba"))
    }

    @Test
    fun authenticatedSession_showsSnapshotChromeTitle() {
        assertThat(
            NativeDoChromeState.from(
                AppSessionState.Authenticated(
                    snapshot = SessionSnapshot(
                        username = "baba",
                        displayName = "Baba",
                        avatarTemplate = "",
                        lastValidatedAt = 1L,
                    ),
                    freshness = SessionFreshness.Verified,
                ),
            ),
        ).isEqualTo(NativeDoChromeState(title = "Baba"))
    }

    @Test
    fun expiredSessionWithSnapshot_usesCachedSnapshotTitle() {
        assertThat(
            NativeDoChromeState.from(
                AppSessionState.Expired(
                    reason = SessionExpiredReason.RecoveryFailed,
                    snapshot = SessionSnapshot(
                        username = "baba",
                        displayName = "Baba",
                        avatarTemplate = "",
                        lastValidatedAt = 1L,
                    ),
                ),
            ),
        ).isEqualTo(NativeDoChromeState(title = "Baba"))
    }

    @Test
    fun unknownSessionWithCachedSnapshot_usesCachedSnapshotTitle() {
        assertThat(
            NativeDoChromeState.from(
                sessionState = AppSessionState.Unknown,
                cachedSnapshot = SessionSnapshot(
                    username = "baba",
                    displayName = "Baba",
                    avatarTemplate = "",
                    lastValidatedAt = 1L,
                ),
            ),
        ).isEqualTo(NativeDoChromeState(title = "Baba"))
    }

    @Test
    fun anonymousSessionWithCachedSnapshot_usesDefaultTitle() {
        assertThat(
            NativeDoChromeState.from(
                sessionState = AppSessionState.Anonymous,
                cachedSnapshot = SessionSnapshot(
                    username = "baba",
                    displayName = "Baba",
                    avatarTemplate = "",
                    lastValidatedAt = 1L,
                ),
            ),
        ).isEqualTo(NativeDoChromeState())
    }
}
