package io.github.baba983.nativedo.ui.topic

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StableUserListSheetSizePolicyTest {
    @Test
    fun singleUserKeepsTelegramSixRowViewport() {
        val policy = StableUserListSheetSizePolicy(rowHeightPx = 50)

        assertThat(policy.minimumHeight()).isEqualTo(300)
        assertThat(policy.rowsHeight()).isEqualTo(300)
        assertThat(policy.rowsHeight(userCount = 1)).isEqualTo(300)
        assertThat(policy.skeletonRows(predictedCount = 1)).isEqualTo(6)
    }

    @Test
    fun knownCountKeepsTelegramSixRowViewport() {
        val policy = StableUserListSheetSizePolicy(rowHeightPx = 50)

        assertThat(policy.rowsHeight(userCount = 8)).isEqualTo(300)
        assertThat(policy.skeletonRows(predictedCount = 8)).isEqualTo(6)
    }

    @Test
    fun loadedCountCanReturnToMinimumViewport() {
        val policy = StableUserListSheetSizePolicy(rowHeightPx = 50)

        assertThat(policy.rowsHeight(userCount = 12)).isEqualTo(300)
        assertThat(policy.rowsHeight(userCount = 1)).isEqualTo(300)
    }

    @Test
    fun extremeCountsRemainScrollableInsideSixRowViewport() {
        val policy = StableUserListSheetSizePolicy(rowHeightPx = 50)

        assertThat(policy.rowsHeight(userCount = 10_000)).isEqualTo(300)
        assertThat(policy.skeletonRows(predictedCount = 10_000)).isEqualTo(6)
    }
}
