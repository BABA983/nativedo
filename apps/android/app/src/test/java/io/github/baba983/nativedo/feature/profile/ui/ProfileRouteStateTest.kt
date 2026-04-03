package io.github.baba983.nativedo.feature.profile.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.session.AppSessionState
import org.junit.Test

class ProfileRouteStateTest {
    @Test
    fun profileSessionRestoreKey_changesWhenLoginReturnTriggerChanges() {
        val initialKey = profileSessionRestoreKey(
            viewer = ProfileViewer.Self,
            sessionRestoreTrigger = 0,
        )
        val retriedKey = profileSessionRestoreKey(
            viewer = ProfileViewer.Self,
            sessionRestoreTrigger = 1,
        )

        assertThat(retriedKey).isNotEqualTo(initialKey)
    }

    @Test
    fun resolveProfileViewer_selfWithAuthenticatedSession_usesAuthenticatedUsername() {
        val resolvedViewer = resolveProfileViewer(
            viewer = ProfileViewer.Self,
            sessionState = AppSessionState.Authenticated(
                username = "baba",
                csrfToken = "csrf-token",
            ),
        )

        assertThat(resolvedViewer).isEqualTo(ProfileViewer.Other("baba"))
    }

    @Test
    fun shouldShowProfileAuthLoading_selfDuringAuthentication_returnsTrue() {
        val shouldShow = shouldShowProfileAuthLoading(
            viewer = ProfileViewer.Self,
            sessionState = AppSessionState.Authenticating,
        )

        assertThat(shouldShow).isTrue()
    }

    @Test
    fun shouldRequestProfileSessionRestore_selfAnonymousWithoutAttempt_returnsTrue() {
        val shouldRequest = shouldRequestProfileSessionRestore(
            viewer = ProfileViewer.Self,
            sessionState = AppSessionState.Anonymous,
            restoreAttempted = false,
        )

        assertThat(shouldRequest).isTrue()
    }

    @Test
    fun shouldRequestProfileSessionRestore_selfExpiredAfterAttempt_returnsFalse() {
        val shouldRequest = shouldRequestProfileSessionRestore(
            viewer = ProfileViewer.Self,
            sessionState = AppSessionState.Expired,
            restoreAttempted = true,
        )

        assertThat(shouldRequest).isFalse()
    }
}
