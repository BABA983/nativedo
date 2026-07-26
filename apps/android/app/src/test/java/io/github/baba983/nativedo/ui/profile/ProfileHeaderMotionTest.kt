package io.github.baba983.nativedo.ui.profile

import android.view.View
import com.google.common.truth.Truth.assertThat
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
    fun releaseKeepsPinnedThreeStageSnapTargets() {
        assertThat(profileHeaderReleaseTarget(20f, 226, 74, 0f))
            .isEqualTo(ProfileHeaderReleaseTarget.COLLAPSED)
        assertThat(profileHeaderReleaseTarget(80f, 226, 74, 0f))
            .isEqualTo(ProfileHeaderReleaseTarget.ACTIONS)
        assertThat(profileHeaderReleaseTarget(180f, 226, 74, 0f))
            .isEqualTo(ProfileHeaderReleaseTarget.HEADER)
        assertThat(profileHeaderReleaseTarget(180f, 226, 74, -1200f))
            .isEqualTo(ProfileHeaderReleaseTarget.ACTIONS)
    }

    @Test
    fun fullExpandPositionsFirstRowBelowActionBarAndActions() {
        assertThat(
            profileHeaderExpandedTargetTop(
                listWidth = 1080,
                actionsExtraHeight = 74,
                actionBarHeight = 120,
            ),
        ).isEqualTo(1034)
    }

    @Test
    fun fullExpandKeepsSingleDiscourseAvatarVisibleWithoutGallerySuccessor() {
        assertThat(
            profileAvatarContainerVisibility(
                expandValue = 1f,
                hasExpandedMediaSuccessor = false,
            ),
        ).isEqualTo(View.VISIBLE)
        assertThat(
            profileAvatarContainerVisibility(
                expandValue = 1f,
                hasExpandedMediaSuccessor = true,
            ),
        ).isEqualTo(View.GONE)
    }

    @Test
    fun partialPullSettlesAgainstHeaderIncludingActionBarOffset() {
        assertThat(
            profileHeaderSettleDy(
                viewTop = 430,
                headerTopOffset = 120,
                headerExtraHeight = 226,
            ),
        ).isEqualTo(84)
    }

    @Test
    fun normalScrollUsesOneTitleLayerAndMediaPinFadesOnlySubtitle() {
        assertThat(profileHeaderTitleLayerAlpha(0)).isEqualTo(0f)
        assertThat(profileHeaderTitleLayerAlpha(1)).isEqualTo(1f)
        assertThat(profileHeaderSubtitleLayerAlpha(0, 0.5f)).isEqualTo(0f)
        assertThat(profileHeaderSubtitleLayerAlpha(1, 0.5f)).isEqualTo(0.5f)
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
    fun nestedContentConsumesOuterCollapseBeforeInnerScroll() {
        assertThat(
            profileOuterScrollRequest(
                dy = 80,
                contentTop = 220,
                pinTop = 180,
                innerCanScrollUp = false,
            ),
        ).isEqualTo(40)
        assertThat(
            profileOuterScrollRequest(
                dy = 80,
                contentTop = 180,
                pinTop = 180,
                innerCanScrollUp = false,
            ),
        ).isEqualTo(0)
    }

    @Test
    fun nestedContentReturnsDownwardScrollOnlyAfterInnerReachesTop() {
        assertThat(
            profileOuterScrollRequest(
                dy = -48,
                contentTop = 180,
                pinTop = 180,
                innerCanScrollUp = true,
            ),
        ).isEqualTo(0)
        assertThat(
            profileOuterScrollRequest(
                dy = -48,
                contentTop = 180,
                pinTop = 180,
                innerCanScrollUp = false,
            ),
        ).isEqualTo(-48)
    }
}
