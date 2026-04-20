package io.github.baba983.nativedo.feature.browser

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.storage.AppBrowserExternalOpenMode
import io.github.baba983.nativedo.core.storage.AppBrowserSearchEngine
import org.junit.Test

class AppBrowserSettingsPanelStateTest {
    @Test
    fun historySubtitleCount_returnsNullWhenThereIsNoHistory() {
        assertThat(browserSettingsHistorySubtitleCount(0)).isNull()
    }

    @Test
    fun historySubtitleCount_returnsCountWhenHistoryExists() {
        assertThat(browserSettingsHistorySubtitleCount(7)).isEqualTo(7)
    }

    @Test
    fun dataSizeSubtitle_returnsNullWhenSizeIsZero() {
        assertThat(browserSettingsDataSizeSubtitle(0L)).isNull()
    }

    @Test
    fun dataSizeSubtitle_formatsPositiveSizes() {
        assertThat(browserSettingsDataSizeSubtitle(1_536L)).isEqualTo("1.5 KB")
    }

    @Test
    fun externalOpenModeLabel_formatsInAppBrowser() {
        assertThat(browserExternalOpenModeLabelText(AppBrowserExternalOpenMode.InAppBrowser)).isEqualTo("In-app browser")
    }

    @Test
    fun searchEngineLabel_formatsGoogle() {
        assertThat(browserSearchEngineLabelText(AppBrowserSearchEngine.Google)).isEqualTo("Google")
    }
}
