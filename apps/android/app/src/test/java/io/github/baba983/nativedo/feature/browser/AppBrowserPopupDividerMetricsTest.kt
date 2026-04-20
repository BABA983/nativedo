package io.github.baba983.nativedo.feature.browser

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppBrowserPopupDividerMetricsTest {
    @Test
    fun popupDividerMetrics_useMoreCompactTelegramLikeGap() {
        val metrics = browserPopupDividerMetrics()

        assertThat(metrics.spacing).isEqualTo(2.dp)
        assertThat(metrics.gapHeight).isEqualTo(6.dp)
    }
}
