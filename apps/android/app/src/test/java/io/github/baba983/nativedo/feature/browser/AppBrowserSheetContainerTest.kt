package io.github.baba983.nativedo.feature.browser

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppBrowserSheetContainerTest {
    @Test
    fun appBrowserSheetRestoreMotionState_keepsForegroundWithoutReplayingBottomUpEntry() {
        assertThat(
            buildAppBrowserSheetRestoreMotionState(restoredFromDock = true),
        ).isEqualTo(
            BrowserSheetMotionState(
                scale = 1f,
                alpha = 1f,
                scrimAlpha = 0f,
            ),
        )
        assertThat(
            buildAppBrowserSheetRestoreMotionState(restoredFromDock = false),
        ).isNull()
    }

    @Test
    fun appBrowserSheetRestoreSettleMotionState_addsTinyInPlaceSettle() {
        assertThat(
            buildAppBrowserSheetRestoreSettleMotionState(progress = 0f),
        ).isEqualTo(
            BrowserSheetMotionState(
                scale = 0.996f,
                alpha = 0.985f,
                scrimAlpha = 0f,
            ),
        )
        assertThat(
            buildAppBrowserSheetRestoreSettleMotionState(progress = 1f),
        ).isEqualTo(
            BrowserSheetMotionState(
                scale = 1f,
                alpha = 1f,
                scrimAlpha = 0f,
            ),
        )
    }

    @Test
    fun appBrowserSheetMotionState_closedKeepsNearlyAttachedScaleAndNoScrim() {
        assertThat(
            buildBrowserSheetMotionState(openProgress = 0f),
        ).isEqualTo(
            BrowserSheetMotionState(
                scale = 0.992f,
                alpha = 0.97f,
                scrimAlpha = 0f,
            ),
        )
    }

    @Test
    fun appBrowserSheetMotionState_openRestoresFullScaleAndUsesLighterScrim() {
        assertThat(
            buildBrowserSheetMotionState(openProgress = 1f),
        ).isEqualTo(
            BrowserSheetMotionState(
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
