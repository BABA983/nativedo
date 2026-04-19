package io.github.baba983.nativedo.ui

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BrowserTabsShellMotionModelTest {
    @Test
    fun browserTabsManagerPresentationState_startsCloserToDockWithHigherInitialPresence() {
        val state = buildBrowserTabsManagerPresentationState(openProgress = 0f)

        assertThat(state.translationY).isEqualTo(56.dp)
        assertThat(state.listAlpha).isWithin(0.0001f).of(0.72f)
    }

    @Test
    fun browserTabsManagerPresentationState_reachesRestingStateWhenFullyOpen() {
        val state = buildBrowserTabsManagerPresentationState(openProgress = 1f)

        assertThat(state.translationY).isEqualTo(0.dp)
        assertThat(state.listAlpha).isWithin(0.0001f).of(1f)
    }

    @Test
    fun browserTabsManagerCloseDuration_matchesTelegramOverlayCadence() {
        assertThat(browserTabsManagerCloseDurationMillis()).isEqualTo(320)
    }
}
