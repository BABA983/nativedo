package io.github.baba983.nativedo.feature.browser

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppBrowserChromeMetricsTest {
    @Test
    fun browserProgressMetrics_useMoreTelegramLikeThinLine() {
        val metrics = appBrowserProgressMetrics()

        assertThat(metrics.height).isEqualTo(2.dp)
        assertThat(metrics.trackAlpha).isEqualTo(0.12f)
    }
}
