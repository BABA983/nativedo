package io.github.baba983.nativedo.ui.settings

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.model.UserProfile
import io.github.baba983.nativedo.ui.profile.profileFormatDate
import io.github.baba983.nativedo.ui.profile.profileFormatTimeRead
import org.junit.Test

class AccountDetailsFragmentTest {
    @Test
    fun administratorRoleTakesPriorityOverModeratorRole() {
        val profile = UserProfile(
            username = "member",
            displayName = "Member",
            bio = "",
            admin = true,
            moderator = true,
        )

        assertThat(profileRoleLabelRes(profile)).isEqualTo(R.string.profile_role_admin)
    }

    @Test
    fun regularMemberHasNoSyntheticRoleRow() {
        val profile = UserProfile(
            username = "member",
            displayName = "Member",
            bio = "",
        )

        assertThat(profileRoleLabelRes(profile)).isNull()
    }

    @Test
    fun accountValuesUseCompactTelegramStyleFormatting() {
        assertThat(profileTrustLevelLabelRes(3)).isEqualTo(R.string.profile_trust_level_3)
        assertThat(profileTrustLevelLabelRes(5)).isNull()
        assertThat(profileFormatTimeRead(90 * 60)).isEqualTo("1h 30m")
        assertThat(profileFormatDate("2026-07-26T08:00:00Z")).isEqualTo("2026-07-26")
    }
}
