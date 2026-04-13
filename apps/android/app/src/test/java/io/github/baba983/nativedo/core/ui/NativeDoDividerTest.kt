package io.github.baba983.nativedo.core.ui

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NativeDoDividerTest {
    @Test
    fun dividerAlpha_clampsIntoVisibleRange() {
        assertThat(nativeDoDividerResolvedAlpha(-1f)).isEqualTo(0f)
        assertThat(nativeDoDividerResolvedAlpha(0.5f)).isEqualTo(0.5f)
        assertThat(nativeDoDividerResolvedAlpha(2f)).isEqualTo(1f)
    }

    @Test
    fun dividerThickness_fallsBackToOneDpForNonPositiveValues() {
        assertThat(nativeDoDividerResolvedThickness(0.dp)).isEqualTo(1.dp)
        assertThat(nativeDoDividerResolvedThickness((-2).dp)).isEqualTo(1.dp)
        assertThat(nativeDoDividerResolvedThickness(2.dp)).isEqualTo(2.dp)
    }
}
