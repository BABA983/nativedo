package org.telegram.ui.Components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.ActionBar.Theme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class CheckBoxSquareContractTest {
    private lateinit var checkBox: CheckBoxSquare

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
        checkBox = CheckBoxSquare(
            RuntimeEnvironment.getApplication(),
            false,
            object : Theme.ResourcesProvider {
                private fun color(key: Int): Int = when (key) {
                    Theme.key_checkboxSquareCheck -> 0xffffffff.toInt()
                    Theme.key_checkboxSquareBackground -> 0xff3390ec.toInt()
                    Theme.key_checkboxSquareDisabled -> 0xffb0b0b0.toInt()
                    else -> 0xff737373.toInt()
                }

                override fun getColor(key: Int): Int = color(key)
                override fun getColorOrNull(key: Int): Int = color(key)
            },
        )
    }

    @Test
    fun checkedStateExposesAnimatedProgressAndDrawsWithoutCrash() {
        val size = AndroidUtilities.dp(18f)
        checkBox.measure(
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
        )
        checkBox.layout(0, 0, size, size)
        checkBox.setChecked(true, false)

        assertThat(checkBox.progress).isEqualTo(1f)

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        checkBox.draw(Canvas(bitmap))
        assertThat(checkBox.progress).isEqualTo(1f)
    }

    @Test
    fun accessibilityNodeUsesCheckboxSemantics() {
        checkBox.setChecked(true, false)
        val info = AccessibilityNodeInfo.obtain()

        checkBox.onInitializeAccessibilityNodeInfo(info)

        assertThat(info.className).isEqualTo("android.widget.CheckBox")
        assertThat(info.isCheckable).isTrue()
        assertThat(info.isChecked).isTrue()
        info.recycle()
    }
}
