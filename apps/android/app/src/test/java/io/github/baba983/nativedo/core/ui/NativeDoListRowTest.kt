package io.github.baba983.nativedo.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NativeDoListRowTest {
    @Test
    fun subtitleText_returnsNullForBlankValues() {
        assertThat(nativeDoListRowSubtitleText(null)).isNull()
        assertThat(nativeDoListRowSubtitleText("")).isNull()
        assertThat(nativeDoListRowSubtitleText("   ")).isNull()
    }

    @Test
    fun subtitleText_preservesNonBlankValues() {
        assertThat(nativeDoListRowSubtitleText("Linux.do user"))
            .isEqualTo("Linux.do user")
    }
}
