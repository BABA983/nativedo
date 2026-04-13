package io.github.baba983.nativedo.feature.browser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppBrowserSettingsPanelStateTest {
    @Test
    fun historySubtitleCount_returnsNullWhenThereIsNoHistory() {
        assertThat(appBrowserSettingsHistorySubtitleCount(0)).isNull()
    }

    @Test
    fun historySubtitleCount_returnsCountWhenHistoryExists() {
        assertThat(appBrowserSettingsHistorySubtitleCount(7)).isEqualTo(7)
    }

    @Test
    fun dataSizeSubtitle_returnsNullWhenSizeIsZero() {
        assertThat(appBrowserSettingsDataSizeSubtitle(0L)).isNull()
    }

    @Test
    fun dataSizeSubtitle_formatsPositiveSizes() {
        assertThat(appBrowserSettingsDataSizeSubtitle(1_536L)).isEqualTo("1.5 KB")
    }
}
