package io.github.baba983.nativedo.feature.browser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BrowserSheetStateTest {

    @Test
    fun buildBrowserSheetPresentationState_derivesOpenFullscreenAttachedAndScrimProgress() {
        val state = buildBrowserSheetPresentationState(
            topOffsetPx = 80f,
            partialTopOffsetPx = 160f,
            closedTopOffsetPx = 800f,
        )

        assertThat(state.openProgress).isEqualTo(1f)
        assertThat(state.fullscreenProgress).isWithin(0.0001f).of(0.5f)
        assertThat(state.attachedProgress).isEqualTo(0f)
        assertThat(state.scrimAlpha).isWithin(0.0001f).of(0.10f)
    }

    @Test
    fun buildBrowserSheetAttachedProgress_staysZeroUntilThresholdThenScalesToOne() {
        assertThat(buildBrowserSheetAttachedProgress(0.85f)).isEqualTo(0f)
        assertThat(buildBrowserSheetAttachedProgress(0.925f)).isWithin(0.0001f).of(0.5f)
        assertThat(buildBrowserSheetAttachedProgress(1f)).isEqualTo(1f)
    }

    @Test
    fun buildBrowserSheetSettle_dismissesWhenDraggedFarEnoughDown() {
        val settle = buildBrowserSheetSettle(
            currentTopOffsetPx = 440f,
            velocityPxPerSecond = 120f,
            partialTopOffsetPx = 160f,
            closedTopOffsetPx = 800f,
        )

        assertThat(settle.target).isEqualTo(BrowserSheetAnchor.Closed)
        assertThat(settle.durationMillis).isAtLeast(180)
    }

    @Test
    fun buildBrowserSheetSettle_promotesToFullscreenOnFastUpwardDrag() {
        val settle = buildBrowserSheetSettle(
            currentTopOffsetPx = 120f,
            velocityPxPerSecond = -1200f,
            partialTopOffsetPx = 160f,
            closedTopOffsetPx = 800f,
        )

        assertThat(settle.target).isEqualTo(BrowserSheetAnchor.Fullscreen)
    }

    @Test
    fun buildBrowserSheetSettle_picksNearestAnchorWhenWithinKeepZone() {
        val settle = buildBrowserSheetSettle(
            currentTopOffsetPx = 220f,
            velocityPxPerSecond = 100f,
            partialTopOffsetPx = 160f,
            closedTopOffsetPx = 800f,
        )

        assertThat(settle.target).isEqualTo(BrowserSheetAnchor.Partial)
    }
}
