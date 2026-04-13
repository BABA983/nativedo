package io.github.baba983.nativedo.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NativeDoSettingsRowTest {
    @Test
    fun subtitleText_returnsNullForBlankValues() {
        assertThat(nativeDoSettingsRowSubtitleText(null)).isNull()
        assertThat(nativeDoSettingsRowSubtitleText("")).isNull()
        assertThat(nativeDoSettingsRowSubtitleText("   ")).isNull()
    }

    @Test
    fun subtitleText_preservesNonBlankValues() {
        assertThat(nativeDoSettingsRowSubtitleText("Open the full browser history panel."))
            .isEqualTo("Open the full browser history panel.")
    }

    @Test
    fun trailingValueText_returnsNullForBlankValues() {
        assertThat(nativeDoSettingsRowTrailingValueText(null)).isNull()
        assertThat(nativeDoSettingsRowTrailingValueText("")).isNull()
        assertThat(nativeDoSettingsRowTrailingValueText("   ")).isNull()
    }

    @Test
    fun trailingValueText_preservesNonBlankValues() {
        assertThat(nativeDoSettingsRowTrailingValueText("2.5 MB")).isEqualTo("2.5 MB")
    }
}
