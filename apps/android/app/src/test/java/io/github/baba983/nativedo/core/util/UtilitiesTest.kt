package io.github.baba983.nativedo.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UtilitiesTest {
    @Test
    fun clamp_usesTelegramMaxThenMinOrder() {
        assertThat(Utilities.clamp(300, 255, 0)).isEqualTo(255)
        assertThat(Utilities.clamp(-10, 255, 0)).isEqualTo(0)
    }

    @Test
    fun clampFloat_handlesSpecialValuesLikeTelegram() {
        assertThat(Utilities.clamp(Float.NaN, 1f, 0f)).isEqualTo(0f)
        assertThat(Utilities.clamp(Float.POSITIVE_INFINITY, 1f, 0f)).isEqualTo(1f)
        assertThat(Utilities.clamp(Float.NEGATIVE_INFINITY, 1f, 0f)).isEqualTo(1f)
    }
}
