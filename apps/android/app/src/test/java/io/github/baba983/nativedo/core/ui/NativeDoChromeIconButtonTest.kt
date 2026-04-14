package io.github.baba983.nativedo.core.ui

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.ui.theme.NativeDoGlassRole
import io.github.baba983.nativedo.ui.theme.NativeDoGlassSpec
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
            glassSpec = sampleGlassSpec(),
            enabled = true,
            emphasized = true,
            pressed = false,
            containerColorOverride = override,
        )

        assertThat(resolved).isEqualTo(override)
    }
}

private fun sampleGlassSpec() = NativeDoGlassSpec(
    role = NativeDoGlassRole.Chrome,
    containerColor = Color(0xFFDDDDDD),
    borderColor = Color(0xFFBBBBBB),
)
