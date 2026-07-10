package io.github.baba983.nativedo.ui.topic

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StableUserListSheetSizePolicyTest {
    @Test
    fun singleUserKeepsMinimumFiveRowViewport() {
        val policy = StableUserListSheetSizePolicy(rowHeightPx = 56)

        assertThat(policy.rowsHeight(userCount = 1)).isEqualTo(280)
        assertThat(policy.skeletonRows(predictedCount = 1)).isEqualTo(5)
    }

    @Test
    fun knownCountSizesLoadingBeforeResultsArrive() {
        val policy = StableUserListSheetSizePolicy(rowHeightPx = 56)

        assertThat(policy.rowsHeight(userCount = 8)).isEqualTo(448)
        assertThat(policy.skeletonRows(predictedCount = 8)).isEqualTo(8)
    }

    @Test
    fun loadedCountCanReturnToMinimumViewport() {
        val policy = StableUserListSheetSizePolicy(rowHeightPx = 56)

        assertThat(policy.rowsHeight(userCount = 12)).isEqualTo(672)
        assertThat(policy.rowsHeight(userCount = 1)).isEqualTo(280)
    }

    @Test
    fun extremeCountsAreCappedAfterSheetIsAlreadyFullHeight() {
        val policy = StableUserListSheetSizePolicy(rowHeightPx = 56)

        assertThat(policy.rowsHeight(userCount = 10_000)).isEqualTo(5_600)
        assertThat(policy.skeletonRows(predictedCount = 10_000)).isEqualTo(100)
    }
}
