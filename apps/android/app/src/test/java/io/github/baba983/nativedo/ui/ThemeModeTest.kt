package io.github.baba983.nativedo.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.ui.theme.ThemeMode
import org.junit.Test

class ThemeModeTest {

    @Test
    fun next_cyclesThroughAllModes() {
        assertThat(ThemeMode.System.next()).isEqualTo(ThemeMode.Light)
        assertThat(ThemeMode.Light.next()).isEqualTo(ThemeMode.Dark)
        assertThat(ThemeMode.Dark.next()).isEqualTo(ThemeMode.System)
    }
}
