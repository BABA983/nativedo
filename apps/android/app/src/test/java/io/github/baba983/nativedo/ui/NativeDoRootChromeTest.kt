package io.github.baba983.nativedo.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.navigation.RootDestination
import org.junit.Test

class NativeDoRootChromeTest {
    @Test
    fun resolveNativeDoRootChromeScene_returnsSearchWhenHomeSearchModeIsActive() {
        assertThat(
            resolveNativeDoRootChromeScene(
                isSearchMode = true,
                actionModeSelectionCount = 0,
                isContextualOptionsExpanded = false,
            ),
        ).isEqualTo(NativeDoRootChromeScene.Search)
    }

    @Test
    fun resolveNativeDoRootChromeScene_prioritizesContextualOptionsOverlay() {
        assertThat(
            resolveNativeDoRootChromeScene(
                isSearchMode = false,
                actionModeSelectionCount = 2,
                isContextualOptionsExpanded = true,
            ),
        ).isEqualTo(NativeDoRootChromeScene.ContextualOptions)
    }

    @Test
    fun buildNativeDoRootChromeModel_hidesRootActionsDuringSearch() {
        val model = buildNativeDoRootChromeModel(
            isRootRoute = true,
            activeRoute = RootDestination.Home.route,
            homeHeaderSearchVisible = true,
            isHomeSearchMode = true,
            actionModeSelectionCount = 0,
            isContextualOptionsExpanded = false,
        )

        assertThat(model.scene).isEqualTo(NativeDoRootChromeScene.Search)
        assertThat(model.showHomeSearchAction).isFalse()
        assertThat(model.showContextualOptionsAction).isFalse()
    }

    @Test
    fun buildNativeDoRootChromeModel_showsContextualOptionsOnRootHome() {
        val model = buildNativeDoRootChromeModel(
            isRootRoute = true,
            activeRoute = RootDestination.Home.route,
            homeHeaderSearchVisible = true,
            isHomeSearchMode = false,
            actionModeSelectionCount = 0,
            isContextualOptionsExpanded = true,
        )

        assertThat(model.scene).isEqualTo(NativeDoRootChromeScene.ContextualOptions)
        assertThat(model.reserveHomeSearchSlot).isTrue()
        assertThat(model.showContextualOptionsAction).isTrue()
        assertThat(model.isContextualOptionsExpanded).isTrue()
    }
}
