package io.github.baba983.nativedo.core.ui

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.ui.theme.NativeDoExtendedColors
import org.junit.Test

class NativeDoChromeIconButtonTest {
    @Test
    fun contentAlpha_disablesWhenButtonIsDisabled() {
        assertThat(
            nativeDoChromeIconButtonContentAlpha(
                enabled = false,
                emphasized = true,
            ),
        ).isEqualTo(0.38f)
    }

    @Test
    fun contentAlpha_dimsWhenNotEmphasized() {
        assertThat(
            nativeDoChromeIconButtonContentAlpha(
                enabled = true,
                emphasized = false,
            ),
        ).isEqualTo(0.62f)
    }

    @Test
    fun contentAlpha_isFullWhenEnabledAndEmphasized() {
        assertThat(
            nativeDoChromeIconButtonContentAlpha(
                enabled = true,
                emphasized = true,
            ),
        ).isEqualTo(1f)
    }

    @Test
    fun containerColor_prefersOverrideWhenProvided() {
        val override = Color(0xFF123456)
        val resolved = nativeDoChromeIconButtonContainerColor(
            colors = sampleColors(),
            enabled = true,
            emphasized = true,
            pressed = false,
            containerColorOverride = override,
        )

        assertThat(resolved).isEqualTo(override)
    }
}

private fun sampleColors() = NativeDoExtendedColors(
    backgroundBase = Color(0xFFFFFFFF),
    backgroundElevated = Color(0xFFF6F6F6),
    glassBackground = Color(0xFFEEEEEE),
    glassBackgroundStrong = Color(0xFFDDDDDD),
    glassStroke = Color(0xFFCCCCCC),
    glassStrokeStrong = Color(0xFFBBBBBB),
    glassShadow = Color(0x11000000),
    accent = Color(0xFF00AA88),
    accentMuted = Color(0x2200AA88),
    textPrimary = Color(0xFF111111),
    textSecondary = Color(0xFF666666),
    glowPrimary = Color(0x2200AA88),
    glowSecondary = Color(0x220088AA),
)
