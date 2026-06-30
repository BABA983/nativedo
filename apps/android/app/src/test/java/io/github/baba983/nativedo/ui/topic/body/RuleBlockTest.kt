package io.github.baba983.nativedo.ui.topic.body

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.telegram.messenger.AndroidUtilities

@RunWith(RobolectricTestRunner::class)
class RuleBlockTest {
    @Test
    fun dividerGeometry_matchesTelegramBlockDividerCell() {
        val parentWidth = AndroidUtilities.dp(300f)

        assertThat(RuleBlock.measuredHeightPx()).isEqualTo(AndroidUtilities.dp(18f))
        assertThat(RuleBlock.barTopPx()).isEqualTo(AndroidUtilities.dp(8f))
        assertThat(RuleBlock.barBottomPx()).isEqualTo(AndroidUtilities.dp(10f))
        assertThat(RuleBlock.barRadiusPx()).isEqualTo(AndroidUtilities.dp(1f))
        assertThat(RuleBlock.barLeftPx(parentWidth)).isEqualTo(parentWidth / 3)
        assertThat(RuleBlock.barRightPx(parentWidth)).isEqualTo(parentWidth / 3 * 2)
    }
}
