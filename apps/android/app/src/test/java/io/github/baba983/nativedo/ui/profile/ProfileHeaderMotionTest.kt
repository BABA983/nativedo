package io.github.baba983.nativedo.ui.profile

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.session.SessionExpiredReason
import io.github.baba983.nativedo.core.session.SessionFreshness
import io.github.baba983.nativedo.core.session.SessionSnapshot
import org.junit.Test

class ProfileHeaderMotionTest {
    @Test
    fun releaseKeepsPinnedThreeStageSnapTargets() {
        assertThat(profileHeaderReleaseTarget(20f, 226, 74, 0f))
            .isEqualTo(ProfileHeaderReleaseTarget.COLLAPSED)
        assertThat(profileHeaderReleaseTarget(80f, 226, 74, 0f))
            .isEqualTo(ProfileHeaderReleaseTarget.ACTIONS)
        assertThat(profileHeaderReleaseTarget(180f, 226, 74, 0f))
            .isEqualTo(ProfileHeaderReleaseTarget.HEADER)
        assertThat(profileHeaderReleaseTarget(180f, 226, 74, -1200f))
            .isEqualTo(ProfileHeaderReleaseTarget.ACTIONS)
        assertThat(profileHeaderReleaseTarget(0f, 226, 74, -1200f))
            .isEqualTo(ProfileHeaderReleaseTarget.NONE)
    }

    @Test
    fun expandedAvatarZoneEndsAtTelegramPullThreshold() {
        assertThat(profileKeepsAvatarExpanded(true, openingAvatar = false, expandProgress = 0.33f)).isTrue()
        assertThat(profileKeepsAvatarExpanded(true, openingAvatar = false, expandProgress = 0.329f)).isFalse()
        assertThat(profileKeepsAvatarExpanded(false, openingAvatar = false, expandProgress = 1f)).isFalse()
    }

    @Test
    fun clickOpeningEntersExpandedZoneBeforePullThreshold() {
        assertThat(profileKeepsAvatarExpanded(true, openingAvatar = true, expandProgress = 0f)).isTrue()
        assertThat(profileKeepsAvatarExpanded(false, openingAvatar = true, expandProgress = 1f)).isFalse()
    }

    @Test
    fun ordinaryPullKeepsCircularAvatarUntilGalleryMorphCompletes() {
        assertThat(profileUsesFullAvatarGeometry(isPulledDown = false, expandAnimatorValue = 1f)).isFalse()
        assertThat(profileUsesFullAvatarGeometry(isPulledDown = true, expandAnimatorValue = 0.998f)).isFalse()
        assertThat(profileUsesFullAvatarGeometry(isPulledDown = true, expandAnimatorValue = 0.999f)).isTrue()
    }

    @Test
    fun expandedAvatarCloseUsesPhotoViewerFlyOutPath() {
        assertThat(profileAvatarCloseUsesFlyOut(closing = true, avatarScale = 0.96f)).isFalse()
        assertThat(profileAvatarCloseUsesFlyOut(closing = true, avatarScale = 0.9601f)).isTrue()
        assertThat(profileAvatarCloseUsesFlyOut(closing = false, avatarScale = 1f)).isFalse()
    }

    @Test
    fun sharedContentOpensMediaHeaderOnlyAtPinLine() {
        assertThat(profileMediaHeaderShouldOpen(contentTop = 121, pinTop = 120)).isFalse()
        assertThat(profileMediaHeaderShouldOpen(contentTop = 120, pinTop = 120)).isTrue()
    }

    @Test
    fun outerRemainderMovesInnerOnlyAfterContentReachesPinLine() {
        assertThat(
            profileInnerScrollFromOuter(
                dyUnconsumed = 40,
                contentTop = 181,
                pinTop = 180,
            ),
        ).isEqualTo(0)
        assertThat(
            profileInnerScrollFromOuter(
                dyUnconsumed = 40,
                contentTop = 180,
                pinTop = 180,
            ),
        ).isEqualTo(40)
    }

    @Test
    fun downwardScrollMovesInnerPreciselyBackToItsFirstRow() {
        assertThat(
            profileInnerScrollBackToTopRequest(
                dy = -48,
                firstPosition = 3,
                firstTop = -12,
                paddingTop = 0,
            ),
        ).isEqualTo(-48)
        assertThat(
            profileInnerScrollBackToTopRequest(
                dy = -48,
                firstPosition = 0,
                firstTop = -20,
                paddingTop = 0,
            ),
        ).isEqualTo(-20)
        assertThat(
            profileInnerScrollBackToTopRequest(
                dy = -48,
                firstPosition = 0,
                firstTop = 0,
                paddingTop = 0,
            ),
        ).isEqualTo(0)
    }

    @Test
    fun identityChromeRequiresCurrentOrRestoringAccount() {
        val snapshot = SessionSnapshot(
            username = "baba",
            displayName = "Baba",
            avatarTemplate = "",
            lastValidatedAt = 1L,
        )

        assertThat(profileShowsIdentityChrome(AppSessionState.Unknown)).isFalse()
        assertThat(profileShowsIdentityChrome(AppSessionState.Anonymous)).isFalse()
        assertThat(
            profileShowsIdentityChrome(
                AppSessionState.Expired(SessionExpiredReason.ServerLoggedOut, snapshot),
            ),
        ).isFalse()
        assertThat(profileShowsIdentityChrome(AppSessionState.Restoring(null))).isFalse()
        assertThat(profileShowsIdentityChrome(AppSessionState.Restoring(snapshot))).isTrue()
        assertThat(
            profileShowsIdentityChrome(
                AppSessionState.Authenticated(snapshot, SessionFreshness.Verified),
            ),
        ).isTrue()
    }
}
