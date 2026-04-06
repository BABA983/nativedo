package io.github.baba983.nativedo.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CompactCountTest {

    @Test
    fun compactCount_formatsSmallNumbers() {
        assertThat(compactCount(987)).isEqualTo("987")
    }

    @Test
    fun compactCount_formatsThousands() {
        assertThat(compactCount(1_250)).isEqualTo("1.3K")
        assertThat(compactCount(18_400)).isEqualTo("18K")
    }

    @Test
    fun compactCount_promotesRoundedThousandsIntoMillions() {
        assertThat(compactCount(999_500)).isEqualTo("1M")
    }

    @Test
    fun compactCount_formatsMillions() {
        assertThat(compactCount(1_250_000)).isEqualTo("1.3M")
    }
}
