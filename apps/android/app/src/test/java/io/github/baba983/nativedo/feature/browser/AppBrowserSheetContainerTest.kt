package io.github.baba983.nativedo.feature.browser

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppBrowserSheetContainerTest {
    @Test
    fun appBrowserSheetMotionState_closedKeepsNearlyAttachedScaleAndNoScrim() {
        assertThat(
            buildAppBrowserSheetMotionState(openProgress = 0f),
        ).isEqualTo(
            AppBrowserSheetMotionState(
                scale = 0.992f,
                alpha = 0.97f,
                scrimAlpha = 0f,
            ),
        )
    }

    @Test
    fun appBrowserSheetMotionState_openRestoresFullScaleAndUsesLighterScrim() {
        assertThat(
            buildAppBrowserSheetMotionState(openProgress = 1f),
        ).isEqualTo(
            AppBrowserSheetMotionState(
                scale = 1f,
                alpha = 1f,
                scrimAlpha = 0.10f,
            ),
        )
    }

    @Test
    fun appBrowserSheetTopInset_usesStatusBarInsetWhenFullscreen() {
        assertThat(
            buildAppBrowserSheetTopInset(
                openProgress = 1f,
                statusBarInset = 30.dp,
            ),
        ).isEqualTo(30.dp)
    }

    @Test
    fun appBrowserSheetTopInset_interpolatesFromHandleAreaToStatusBarInset() {
        assertThat(
            buildAppBrowserSheetTopInset(
                openProgress = 0f,
                statusBarInset = 30.dp,
            ),
        ).isEqualTo(24.dp)
        assertThat(
            buildAppBrowserSheetTopInset(
                openProgress = 0.5f,
                statusBarInset = 30.dp,
            ),
        ).isEqualTo(27.dp)
    }
}
