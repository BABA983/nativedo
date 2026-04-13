package io.github.baba983.nativedo.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NativeDoSectionHeaderTest {
    @Test
    fun actionLabel_returnsNullForBlankValues() {
        assertThat(nativeDoSectionHeaderActionLabel(null)).isNull()
        assertThat(nativeDoSectionHeaderActionLabel("")).isNull()
        assertThat(nativeDoSectionHeaderActionLabel("   ")).isNull()
    }

    @Test
    fun actionLabel_preservesNonBlankValues() {
        assertThat(nativeDoSectionHeaderActionLabel("Clear"))
            .isEqualTo("Clear")
    }
}
