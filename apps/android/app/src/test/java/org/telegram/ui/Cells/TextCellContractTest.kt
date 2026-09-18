package org.telegram.ui.Cells

import android.graphics.drawable.ColorDrawable
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

/**
 * Pins the pin's TextCell layout contract (the file is the pin's again): the row
 * mirrors its icon for RTL, which is what the browser-settings rows depend on.
 * The value side is deliberately not asserted here — its measured width depends
 * on the row's text metrics, not on the direction.
 */
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

    private fun browserSettingRow(): TextCell =
        TextCell(RuntimeEnvironment.getApplication()).apply {
            setTextAndValueAndIcon("Search engine", "Google", ColorDrawable(), false)
            val width = AndroidUtilities.dp(360f)
            measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            layout(0, 0, width, measuredHeight)
        }

    @Test
    fun rtlBrowserSettingRowMirrorsTheIcon() {
        LocaleController.isRTL = true
        val width = AndroidUtilities.dp(360f)

        assertThat(browserSettingRow().imageView.left).isGreaterThan(width / 2)
    }

    @Test
    fun ltrBrowserSettingRowKeepsTheIconOnTheLeft() {
        LocaleController.isRTL = false
        val width = AndroidUtilities.dp(360f)

        assertThat(browserSettingRow().imageView.left).isLessThan(width / 2)
    }
}
