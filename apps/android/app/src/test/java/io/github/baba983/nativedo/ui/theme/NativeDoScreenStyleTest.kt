package io.github.baba983.nativedo.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NativeDoScreenStyleTest {
    @Test
    fun nativeDoBrowserScreenStyle_returnsBaseStyleWhenPresentationIsClosed() {
        val baseStyle = nativeDoRootScreenStyle(
            colors = NativeDoLightExtendedColors,
            darkTheme = false,
        )

        val resolved = nativeDoBrowserScreenStyle(
            baseStyle = baseStyle,
            browserChromeColor = Color.Black,
            browserNavigationBarColor = Color.Black,
            presentationProgress = 0f,
        )

        assertThat(resolved).isEqualTo(baseStyle)
    }

    @Test
    fun nativeDoBrowserScreenStyle_usesResolvedColorLuminanceForIconAppearance() {
        val baseStyle = nativeDoRootScreenStyle(
            colors = NativeDoDarkExtendedColors,
            darkTheme = true,
        )

        val resolved = nativeDoBrowserScreenStyle(
            baseStyle = baseStyle,
            browserChromeColor = Color(0xFFF7F4EC),
            browserNavigationBarColor = Color(0xFFF0ECE4),
            presentationProgress = 1f,
        )

        assertThat(resolved.systemBarStyle.backgroundPolicy)
            .isEqualTo(NativeDoSystemBarBackgroundPolicy.Explicit)
        assertThat(resolved.systemBarStyle.useDarkStatusBarIcons).isTrue()
        assertThat(resolved.systemBarStyle.useDarkNavigationBarIcons).isTrue()
    }
}
