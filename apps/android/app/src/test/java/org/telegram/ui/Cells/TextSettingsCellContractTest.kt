package org.telegram.ui.Cells

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.ui.Components.RLottieImageView

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TextSettingsCellContractTest {
    private lateinit var cell: TextSettingsCell

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
        cell = TextSettingsCell(RuntimeEnvironment.getApplication())
    }

    @Test
    fun settingsIconUsesTelegramLottieImageView() {
        assertThat(cell.childCount).isAtLeast(4)
        assertThat(cell.getChildAt(2)).isInstanceOf(RLottieImageView::class.java)
    }
}
