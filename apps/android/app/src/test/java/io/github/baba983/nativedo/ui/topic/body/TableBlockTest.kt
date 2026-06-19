package io.github.baba983.nativedo.ui.topic.body

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader

@RunWith(RobolectricTestRunner::class)
class TableBlockTest {
    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun tableOuterGeometry_matchesTelegramBlockTableCell() {
        assertThat(TableBlock.listTopPaddingPx()).isEqualTo(AndroidUtilities.dp(8f))
        assertThat(TableBlock.listBottomPaddingPx()).isEqualTo(AndroidUtilities.dp(8f))
        assertThat(TableBlock.scrollHorizontalPaddingPx()).isEqualTo(AndroidUtilities.dp(18f))
        assertThat(TableBlock.scrollOverflowThresholdInsetPx()).isEqualTo(AndroidUtilities.dp(36f))
    }

    @Test
    fun tableCellGeometry_matchesTelegramTableLayout() {
        assertThat(TableBlock.cellHorizontalPaddingPx()).isEqualTo(AndroidUtilities.dp(8f))
        assertThat(TableBlock.cellVerticalPaddingPx()).isEqualTo(AndroidUtilities.dp(7f))
        assertThat(TableBlock.tableCornerRadiusPx()).isEqualTo(AndroidUtilities.dp(3f))
    }

    @Test
    fun nativeFallbackGeometry_isExplicit() {
        assertThat(TableBlock.minRowHeightPx()).isEqualTo(AndroidUtilities.dp(36f))
        assertThat(TableBlock.minCellTextWidthPx()).isEqualTo(AndroidUtilities.dp(40f))
        assertThat(TableBlock.nestedBlockHorizontalInsetPx()).isEqualTo(AndroidUtilities.dp(16f))
        assertThat(TableBlock.nestedBlockSpacingPx()).isEqualTo(0)
    }
}
