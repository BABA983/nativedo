package io.github.baba983.nativedo.feature.profile.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.session.SessionFreshness
import io.github.baba983.nativedo.core.session.SessionSnapshot
import org.junit.Test

class ProfileRouteStateTest {
    @Test
    fun resolveProfileViewer_selfWithVerifiedSession_usesSnapshotUsername() {
        val resolvedViewer = resolveProfileViewer(
            viewer = ProfileViewer.Self,
            sessionState = AppSessionState.Authenticated(
                snapshot = SessionSnapshot(
                    username = "baba",
                    displayName = "Baba",
                    avatarTemplate = "",
                    lastValidatedAt = 1L,
                ),
                freshness = SessionFreshness.Verified,
            ),
        )

        assertThat(resolvedViewer).isEqualTo(ProfileViewer.Other("baba"))
    }

    @Test
    fun shouldShowProfileAuthLoading_selfDuringUnknown_returnsTrue() {
        val shouldShow = shouldShowProfileAuthLoading(
            viewer = ProfileViewer.Self,
            sessionState = AppSessionState.Unknown,
        )

        assertThat(shouldShow).isTrue()
    }

    @Test
    fun shouldShowProfileAuthLoading_selfDuringRestoring_returnsTrue() {
        val shouldShow = shouldShowProfileAuthLoading(
            viewer = ProfileViewer.Self,
            sessionState = AppSessionState.Restoring(
                snapshot = SessionSnapshot(
                    username = "baba",
                    displayName = "Baba",
                    avatarTemplate = "",
                    lastValidatedAt = 1L,
                ),
            ),
        )

        assertThat(shouldShow).isTrue()
    }

    @Test
    fun shouldShowProfileAuthLoading_otherViewerDuringRestoring_returnsFalse() {
        val shouldShow = shouldShowProfileAuthLoading(
            viewer = ProfileViewer.Other("baba"),
            sessionState = AppSessionState.Restoring(
                snapshot = SessionSnapshot(
                    username = "baba",
                    displayName = "Baba",
                    avatarTemplate = "",
                    lastValidatedAt = 1L,
                ),
            ),
        )

        assertThat(shouldShow).isFalse()
    }

    @Test
    fun shouldRefreshProfileForHandledAuthVersion_otherViewerWithNewAuthenticatedVersion_returnsTrue() {
        val shouldRefresh = shouldRefreshProfileForHandledAuthVersion(
            viewer = ProfileViewer.Other("baba"),
            sessionState = AppSessionState.Authenticated(
                snapshot = SessionSnapshot(
                    username = "baba",
                    displayName = "Baba",
                    avatarTemplate = "",
                    lastValidatedAt = 1L,
                ),
                freshness = SessionFreshness.Verified,
            ),
            handledAuthVersion = 1L,
            currentAuthVersion = 2L,
        )

        assertThat(shouldRefresh).isTrue()
    }

    @Test
    fun shouldRefreshProfileForHandledAuthVersion_selfViewer_returnsFalse() {
        val shouldRefresh = shouldRefreshProfileForHandledAuthVersion(
            viewer = ProfileViewer.Self,
            sessionState = AppSessionState.Authenticated(
                snapshot = SessionSnapshot(
                    username = "baba",
                    displayName = "Baba",
                    avatarTemplate = "",
                    lastValidatedAt = 1L,
                ),
                freshness = SessionFreshness.Verified,
            ),
            handledAuthVersion = 1L,
            currentAuthVersion = 2L,
        )

        assertThat(shouldRefresh).isFalse()
    }
}
