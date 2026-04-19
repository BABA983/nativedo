package io.github.baba983.nativedo.ui

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BrowserTabsTransitionContractTest {
    @Test
    fun resolveBrowserTabsTransitionRenderSource_prefersLiveSurfaceWhenActiveSurfaceParticipates() {
        val transition = BrowserTabsTransitionState(
            transitionId = 1L,
            kind = BrowserTabsTransitionKind.RestoreFromManager,
            sessionId = 11L,
            preview = browserPreview(),
            sourceSurface = BrowserTabsTransitionSurface.Manager,
            targetSurface = BrowserTabsTransitionSurface.Active,
            sourceBounds = IntRect(80, 260, 920, 1180),
            targetBounds = IntRect(0, 0, 1080, 2200),
        )

        assertThat(
            resolveBrowserTabsTransitionRenderSource(
                transition = transition,
                hasLiveSurface = true,
            ),
        ).isEqualTo(BrowserTabsTransitionRenderSource.LiveSurface)
    }

    @Test
    fun resolveBrowserTabsTransitionRenderSource_keepsPreviewForParkedOnlyTransitions() {
        val transition = BrowserTabsTransitionState(
            transitionId = 2L,
            kind = BrowserTabsTransitionKind.OpenManager,
            sessionId = 17L,
            preview = browserPreview(),
            sourceSurface = BrowserTabsTransitionSurface.Dock,
            targetSurface = BrowserTabsTransitionSurface.Manager,
            sourceBounds = IntRect(48, 2088, 1032, 2188),
            targetBounds = IntRect(92, 320, 988, 1180),
        )

        assertThat(
            resolveBrowserTabsTransitionRenderSource(
                transition = transition,
                hasLiveSurface = true,
            ),
        ).isEqualTo(BrowserTabsTransitionRenderSource.Preview)
    }

    @Test
    fun buildBrowserTabsLiveSurfaceTransitionState_returnsInterpolatedFrameForDrawThrough() {
        val transition = BrowserTabsTransitionState(
            transitionId = 3L,
            kind = BrowserTabsTransitionKind.MinimizeToDock,
            sessionId = 23L,
            preview = browserPreview(),
            sourceSurface = BrowserTabsTransitionSurface.Active,
            targetSurface = BrowserTabsTransitionSurface.Dock,
            sourceBounds = IntRect(0, 0, 1080, 2200),
            targetBounds = IntRect(48, 2088, 1032, 2188),
        )

        val state = buildBrowserTabsLiveSurfaceTransitionState(
            transition = transition,
            hasLiveSurface = true,
            progress = 0.5f,
        )

        assertThat(state).isNotNull()
        assertThat(state?.sessionId).isEqualTo(23L)
        assertThat(state?.boundsInRoot).isEqualTo(IntRect(24, 1044, 1056, 2194))
        assertThat(state?.cornerRadius).isEqualTo(5.dp)
        assertThat(state?.alpha).isWithin(0.0001f).of(1f)
    }

    @Test
    fun browserTabsTransitionDuration_closeManager_matchesManagerCloseCadence() {
        assertThat(
            browserTabsTransitionDurationMillis(BrowserTabsTransitionKind.CloseManager),
        ).isEqualTo(320)
    }

    private fun browserPreview() = BrowserHostMinimizedPreview(
        kind = BrowserHostMinimizedKind.Browser,
        title = "linux.do",
        subtitle = "Browser",
    )
}
