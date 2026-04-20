package io.github.baba983.nativedo.feature.browser

import com.google.common.truth.Truth.assertThat
import androidx.compose.ui.graphics.Color
import org.junit.Test

class BrowserUiStateTest {

    @Test
    fun resolveBrowserChromeScene_returnsBrowseWhenNoTransientStateIsActive() {
        val scene = resolveBrowserChromeScene(
            isSearching = false,
            isAddressEditing = false,
            isHistoryPopupVisible = false,
            isOverflowMenuVisible = false,
            isInSubPanel = false,
        )

        assertThat(scene).isEqualTo(BrowserChromeScene.Browse)
    }

    @Test
    fun resolveBrowserChromeScene_prioritizesAddressEditingOverOtherScenes() {
        val scene = resolveBrowserChromeScene(
            isSearching = true,
            isAddressEditing = true,
            isHistoryPopupVisible = true,
            isOverflowMenuVisible = true,
            isInSubPanel = true,
        )

        assertThat(scene).isEqualTo(BrowserChromeScene.AddressEditing)
    }

    @Test
    fun resolveBrowserChromeScene_reportsOverflowMenuWhenOnlyMenuIsExpanded() {
        val scene = resolveBrowserChromeScene(
            isSearching = false,
            isAddressEditing = false,
            isHistoryPopupVisible = false,
            isOverflowMenuVisible = true,
            isInSubPanel = false,
        )

        assertThat(scene).isEqualTo(BrowserChromeScene.OverflowMenu)
    }

    @Test
    fun isBrowserChromeDark_returnsTrueForDarkColors() {
        assertThat(isBrowserChromeDark(Color(0xFF202124))).isTrue()
    }

    @Test
    fun isBrowserChromeDark_returnsFalseForLightColors() {
        assertThat(isBrowserChromeDark(Color(0xFFF5F5F5))).isFalse()
    }
}
