package io.github.baba983.nativedo.feature.browser

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.sqrt

class AppBrowserChromeMetricsTest {
    @Test
    fun browserProgressMetrics_useMoreTelegramLikeThinLine() {
        val metrics = appBrowserProgressMetrics()

        assertThat(metrics.height).isEqualTo(2.dp)
        assertThat(metrics.trackAlpha).isEqualTo(0.12f)
    }

    @Test
    fun browserTopBarCollapseMetrics_startExpanded() {
        val metrics = buildAppBrowserTopBarCollapseMetrics(
            headerCollapseProgress = 0f,
            scrollProgress = 0f,
        )

        assertThat(metrics.menuScale).isEqualTo(1f)
        assertThat(metrics.menuTranslationProgress).isEqualTo(0f)
        assertThat(metrics.titleScale).isEqualTo(1f)
        assertThat(metrics.titleTranslationProgress).isEqualTo(0f)
    }

    @Test
    fun browserTopBarCollapseMetrics_followTelegramLikeCollapseDirection() {
        val metrics = buildAppBrowserTopBarCollapseMetrics(
            headerCollapseProgress = 1f,
            scrollProgress = 1f,
        )
        val expectedMenuScale = sqrt(
            (AppBrowserCollapsedTopBarOverlayHeight.value / AppBrowserTopBarOverlayHeight.value).toDouble(),
        ).toFloat()

        assertThat(metrics.menuScale).isWithin(0.0001f).of(expectedMenuScale)
        assertThat(metrics.menuTranslationProgress).isWithin(0.0001f).of(1f - expectedMenuScale)
        assertThat(metrics.titleScale).isWithin(0.0001f).of(expectedMenuScale * 0.97f)
        assertThat(metrics.titleTranslationProgress).isEqualTo(1f)
    }

    @Test
    fun browserScrubbedProgress_clampsToScrollableRange() {
        assertThat(
            buildAppBrowserScrubbedProgress(
                currentProgress = 0.4f,
                deltaFraction = 0.2f,
            ),
        ).isEqualTo(0.6f)
        assertThat(
            buildAppBrowserScrubbedProgress(
                currentProgress = 0.1f,
                deltaFraction = -0.4f,
            ),
        ).isEqualTo(0f)
        assertThat(
            buildAppBrowserScrubbedProgress(
                currentProgress = 0.9f,
                deltaFraction = 0.4f,
            ),
        ).isEqualTo(1f)
    }
}
