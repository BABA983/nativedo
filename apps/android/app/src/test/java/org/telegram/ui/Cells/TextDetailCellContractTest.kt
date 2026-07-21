package org.telegram.ui.Cells

import android.view.View
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.Components.LinkSpanDrawable

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TextDetailCellContractTest {
    private lateinit var cell: TextDetailCell

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
        cell = TextDetailCell(RuntimeEnvironment.getApplication())
    }

    @Test
    fun profileRowsUseTelegramLinkAwareTextViews() {
        cell.setTextAndValue("Username", "@alice", false)

        assertThat(cell.getChildAt(0)).isInstanceOf(LinkSpanDrawable.LinksTextView::class.java)
        assertThat(cell.getChildAt(1)).isInstanceOf(LinkSpanDrawable.LinksTextView::class.java)
        assertThat((cell.getChildAt(0) as LinkSpanDrawable.LinksTextView).text.toString())
            .isEqualTo("Username")
    }

    @Test
    fun rowsKeepPinnedFixedHeightAndAccessibleCombinedText() {
        cell.setTextAndValue("Trust level", "Member", true)
        cell.measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.AT_MOST),
        )

        assertThat(cell.measuredHeight).isEqualTo(AndroidUtilities.dp(60f) + 1)
    }

    @Test
    fun factoryUsesPinnedTitleAndValueInsets() {
        val factoryCell = TextDetailCell(RuntimeEnvironment.getApplication(), 18, null)
        val titleParams = factoryCell.getChildAt(0).layoutParams as android.widget.FrameLayout.LayoutParams
        val valueParams = factoryCell.getChildAt(1).layoutParams as android.widget.FrameLayout.LayoutParams

        assertThat(titleParams.leftMargin).isEqualTo(AndroidUtilities.dp(12f))
        assertThat(valueParams.leftMargin).isEqualTo(AndroidUtilities.dp(18f))
    }
}
