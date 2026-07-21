package org.telegram.ui.ActionBar

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
class ActionBarMenuSubItemContractTest {
    private var originalRtl = false

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
        originalRtl = LocaleController.isRTL
        LocaleController.isRTL = true
    }

    @After
    fun tearDown() {
        LocaleController.isRTL = originalRtl
    }

    @Test
    fun rtlMenuItemMirrorsIconAndText() {
        val item = ActionBarMenuSubItem(RuntimeEnvironment.getApplication(), false, false, null)
        item.setTextAndIcon("Open externally", 0, ColorDrawable())
        val width = AndroidUtilities.dp(320f)
        item.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        item.layout(0, 0, width, item.measuredHeight)

        assertThat(item.textView.gravity and Gravity.HORIZONTAL_GRAVITY_MASK).isEqualTo(Gravity.RIGHT)
        assertThat(item.imageView.left).isGreaterThan(width / 2)
    }
}
