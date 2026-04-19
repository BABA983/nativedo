package io.github.baba983.nativedo.feature.browser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppBrowserSheetMotionModelTest {
    @Test
    fun appBrowserSheetPresentationState_withoutPartialAnchorKeepsFullscreenProgressInSyncWithOpenProgress() {
        val state = buildAppBrowserSheetPresentationState(
            topOffsetPx = 400f,
            partialTopOffsetPx = 0f,
            closedTopOffsetPx = 800f,
        )

        assertThat(state.openedProgress).isWithin(0.0001f).of(0.5f)
        assertThat(state.fullscreenProgress).isWithin(0.0001f).of(0.5f)
    }
}
