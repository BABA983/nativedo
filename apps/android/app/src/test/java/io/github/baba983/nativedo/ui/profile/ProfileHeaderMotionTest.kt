package io.github.baba983.nativedo.ui.profile

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProfileHeaderMotionTest {
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
}
