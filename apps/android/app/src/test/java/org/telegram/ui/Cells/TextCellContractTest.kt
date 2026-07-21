package org.telegram.ui.Cells

import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.LocaleController

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TextCellContractTest {
    private var originalRtl = false

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
        originalRtl = LocaleController.isRTL
    }

    @After
    fun tearDown() {
        LocaleController.isRTL = originalRtl
    }

    @Test
    fun rtlBrowserSettingRowMirrorsTitleAndIcon() {
        LocaleController.isRTL = true
        val cell = TextCell(RuntimeEnvironment.getApplication())
        cell.setTextAndValueAndIcon("Search engine", "Google", ColorDrawable(), false)
        val width = AndroidUtilities.dp(360f)

        cell.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        cell.layout(0, 0, width, cell.measuredHeight)

        assertThat(cell.textView.gravity and Gravity.HORIZONTAL_GRAVITY_MASK).isEqualTo(Gravity.RIGHT)
        assertThat(cell.imageView.left).isGreaterThan(width / 2)
    }
}
