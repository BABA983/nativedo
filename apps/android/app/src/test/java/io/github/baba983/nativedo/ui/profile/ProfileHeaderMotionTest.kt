package io.github.baba983.nativedo.ui.profile

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.session.SessionExpiredReason
import io.github.baba983.nativedo.core.session.SessionFreshness
import io.github.baba983.nativedo.core.session.SessionSnapshot
import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.feature.profile.ui.ProfileFeedUiState
import io.github.baba983.nativedo.ui.settings.profileVersionFooterReplacesTerminalShadow
import org.junit.Test
import org.telegram.ui.Components.UniversalAdapter

class ProfileHeaderMotionTest {
    @Test
    fun initialAvatarLayoutFallsBackFromUnmeasuredFragmentWidth() {
        assertThat(profileViewportWidth(fragmentWidth = 0, listWidth = 0, displayWidth = 1080))
            .isEqualTo(1080)
        assertThat(profileViewportWidth(fragmentWidth = 0, listWidth = 960, displayWidth = 1080))
            .isEqualTo(960)
        assertThat(profileViewportWidth(fragmentWidth = 900, listWidth = 960, displayWidth = 1080))
            .isEqualTo(900)
    }

    @Test
    fun actionsSegmentDoesNotAdvanceAvatarHeaderProgress() {
        assertThat(profileHeaderExtraDiff(extraHeight = 0f, actionsExtraHeight = 74, headerOnlyExtraHeight = 152))
            .isEqualTo(0f)
        assertThat(profileHeaderExtraDiff(extraHeight = 74f, actionsExtraHeight = 74, headerOnlyExtraHeight = 152))
            .isEqualTo(0f)
        assertThat(profileHeaderExtraDiff(extraHeight = 150f, actionsExtraHeight = 74, headerOnlyExtraHeight = 152))
            .isWithin(0.0001f)
            .of(0.5f)
        assertThat(profileHeaderExtraDiff(extraHeight = 226f, actionsExtraHeight = 74, headerOnlyExtraHeight = 152))
            .isEqualTo(1f)
    }

    @Test
    fun listKeepsTelegramFullPortraitScrollRange() {
        assertThat(
            profileListTopPadding(
                viewportWidth = 360,
                actionsExtraHeight = 74,
            ),
        ).isEqualTo(434)
        assertThat(
            profileInitialRowOffset(
                headerExtraHeight = 226,
                listTopPadding = 434,
            ),
        ).isEqualTo(-208)
    }

    @Test
    fun sessionOnlyPageDoesNotReserveProfileHeaderSpace() {
        assertThat(profileSessionListTopPadding(profileChromeVisible = false, profileTopPadding = 434))
            .isEqualTo(0)
        assertThat(profileSessionListTopPadding(profileChromeVisible = true, profileTopPadding = 434))
            .isEqualTo(434)
    }

    @Test
    fun shortInitialContentGetsEnoughBottomPaddingToPreserveHeaderAnchor() {
        assertThat(
            profileOuterListBottomPadding(
                baseInset = 96,
                visibleHeight = 900,
                headerExtraHeight = 226,
                contentHeight = 140,
            ),
        ).isEqualTo(534)
        assertThat(
            profileOuterListBottomPadding(
                baseInset = 96,
                visibleHeight = 900,
                headerExtraHeight = 226,
                contentHeight = 800,
            ),
        ).isEqualTo(96)
    }

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
    fun fullExpandPositionsFirstRowAtViewportAndActionsBoundary() {
        assertThat(
            profileHeaderExpandedTargetTop(
                listWidth = 1080,
                actionsExtraHeight = 74,
                actionBarHeight = 120,
            ),
        ).isEqualTo(1034)
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
    fun normalScrollCrossfadesOnlineSubtitleAndMediaCounter() {
        assertThat(profileHeaderTitleLayerAlpha(0)).isEqualTo(0f)
        assertThat(profileHeaderTitleLayerAlpha(1)).isEqualTo(1f)
        assertThat(profileHeaderSubtitleLayerAlpha(0, 0.5f)).isEqualTo(0f)
        assertThat(profileHeaderSubtitleLayerAlpha(1, 0.5f)).isEqualTo(0.5f)
        assertThat(profileMediaCounterAlpha(0.5f)).isEqualTo(0.5f)
        assertThat(profileMediaCounterAlpha(2f)).isEqualTo(1f)
    }

    @Test
    fun mediaCounterDistinguishesUnknownEmptyAndLoadedFeedCounts() {
        assertThat(profileFeedSubtitleCount(ProfileFeedUiState(isLoading = true))).isNull()
        assertThat(
            profileFeedSubtitleCount(
                ProfileFeedUiState(isLoading = false, errorMessage = "failed"),
            ),
        ).isNull()
        assertThat(profileFeedSubtitleCount(ProfileFeedUiState(isLoading = false))).isEqualTo(0)
        assertThat(
            profileFeedSubtitleCount(
                ProfileFeedUiState(
                    items = listOf(TopicSummary(id = 1L, title = "Topic", authorUsername = "user")),
                    isLoading = false,
                    hasMore = true,
                ),
            ),
        ).isEqualTo(1)
    }

    @Test
    fun sharedContentOpensMediaHeaderOnlyAtPinLine() {
        assertThat(profileMediaHeaderShouldOpen(contentTop = 121, pinTop = 120)).isFalse()
        assertThat(profileMediaHeaderShouldOpen(contentTop = 120, pinTop = 120)).isTrue()
    }

    @Test
    fun mediaHeaderRevealsWhiteSurfaceFromBottomToTop() {
        assertThat(profileMediaHeaderTopBoundary(height = 200, progress = 0f)).isEqualTo(200)
        assertThat(profileMediaHeaderTopBoundary(height = 200, progress = 0.5f)).isEqualTo(100)
        assertThat(profileMediaHeaderTopBoundary(height = 200, progress = 1f)).isEqualTo(0)
    }

    @Test
    fun versionFooterReplacesTerminalShadowBeforeBinding() {
        assertThat(profileVersionFooterReplacesTerminalShadow(UniversalAdapter.VIEW_TYPE_SHADOW)).isTrue()
        assertThat(profileVersionFooterReplacesTerminalShadow(UniversalAdapter.VIEW_TYPE_TEXT)).isFalse()
        assertThat(profileVersionFooterReplacesTerminalShadow(null)).isFalse()
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
    fun sharedContentFillsTheViewportBelowTheActionBarPinLine() {
        assertThat(profileContentViewportHeight(outerHeight = 800, pinTop = 120)).isEqualTo(680)
        assertThat(profileContentViewportHeight(outerHeight = 100, pinTop = 120)).isEqualTo(0)
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
