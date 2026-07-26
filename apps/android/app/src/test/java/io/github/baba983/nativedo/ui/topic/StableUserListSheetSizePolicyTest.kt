package io.github.baba983.nativedo.ui.topic

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StableUserListSheetSizePolicyTest {
    @Test
    fun singleUserShrinksWithinTelegramSixRowViewport() {
        val policy = StableUserListSheetSizePolicy(rowHeightPx = 50)

        assertThat(policy.minimumHeight()).isEqualTo(50)
        assertThat(policy.rowsHeight()).isEqualTo(300)
        assertThat(policy.rowsHeight(userCount = 1)).isEqualTo(50)
        assertThat(policy.skeletonRows(predictedCount = 1)).isEqualTo(1)
    }

    @Test
    fun knownCountIsCappedByTelegramSixRowViewport() {
        val policy = StableUserListSheetSizePolicy(rowHeightPx = 50)

        assertThat(policy.rowsHeight(userCount = 8)).isEqualTo(300)
        assertThat(policy.skeletonRows(predictedCount = 8)).isEqualTo(6)
    }

    @Test
    fun loadedCountCanShrinkAfterLoading() {
        val policy = StableUserListSheetSizePolicy(rowHeightPx = 50)

        assertThat(policy.rowsHeight(userCount = 12)).isEqualTo(300)
        assertThat(policy.rowsHeight(userCount = 1)).isEqualTo(50)
    }

    @Test
    fun extremeCountsRemainScrollableInsideSixRowViewport() {
        val policy = StableUserListSheetSizePolicy(rowHeightPx = 50)

        assertThat(policy.rowsHeight(userCount = 10_000)).isEqualTo(300)
        assertThat(policy.skeletonRows(predictedCount = 10_000)).isEqualTo(6)
    }
}
