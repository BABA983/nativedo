package org.telegram.ui.Cells

import android.view.accessibility.AccessibilityNodeInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Components.Switch

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TextCheckCellContractTest {
    private lateinit var cell: TextCheckCell
    private lateinit var switchView: Switch

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
        cell = TextCheckCell(RuntimeEnvironment.getApplication())
        switchView = cell.getChildAt(1) as Switch
    }

    @Test
    fun enabledStatePropagatesToTelegramSwitch() {
        cell.setEnabled(false)
        assertThat(switchView.isEnabled).isFalse()

        cell.setEnabled(true)
        assertThat(switchView.isEnabled).isTrue()
    }

    @Test
    fun accessibilityNodeDescribesToggleAndState() {
        cell.setTextAndCheck("Use in-app browser", true, false)
        val info = AccessibilityNodeInfo.obtain()

        cell.onInitializeAccessibilityNodeInfo(info)

        assertThat(info.className).isEqualTo("android.widget.Switch")
        assertThat(info.isCheckable).isTrue()
        assertThat(info.isChecked).isTrue()
        assertThat(info.contentDescription).isEqualTo("Use in-app browser")

        info.recycle()
    }
}
