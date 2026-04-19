package io.github.baba983.nativedo.feature.browser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppBrowserReaderSheetSettleTest {

    @Test
    fun buildAppBrowserReaderSheetSettle_keepsPartialForMidRelease() {
        val settle = buildAppBrowserReaderSheetSettle(
            currentTopOffsetPx = 220f,
            velocityPxPerSecond = 100f,
            partialTopOffsetPx = 160f,
            closedTopOffsetPx = 800f,
        )

        assertThat(settle.target).isEqualTo(AppBrowserSheetAnchor.Partial)
        assertThat(settle.durationMillis).isAtLeast(220)
    }

    @Test
    fun buildAppBrowserReaderSheetSettle_dismissesWhenDraggedFarEnoughDown() {
        val settle = buildAppBrowserReaderSheetSettle(
            currentTopOffsetPx = 440f,
            velocityPxPerSecond = 120f,
            partialTopOffsetPx = 160f,
            closedTopOffsetPx = 800f,
        )

        assertThat(settle.target).isEqualTo(AppBrowserSheetAnchor.Closed)
    }
}
