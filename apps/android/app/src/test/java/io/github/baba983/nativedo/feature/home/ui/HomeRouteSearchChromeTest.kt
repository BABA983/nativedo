package io.github.baba983.nativedo.feature.home.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomeRouteSearchChromeTest {

    @Test
    fun collapseProgress_staysAtZeroAtTop() {
        assertThat(
            homeSearchCollapseProgress(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0,
                collapseDistancePx = 64,
            ),
        ).isEqualTo(0f)
    }

    @Test
    fun collapseProgress_reachesOneWhenFirstItemIsGone() {
        assertThat(
            homeSearchCollapseProgress(
                firstVisibleItemIndex = 1,
                firstVisibleItemScrollOffset = 0,
                collapseDistancePx = 64,
            ),
        ).isEqualTo(1f)
    }

    @Test
    fun headerSearch_hiddenWhileForcedSearchFieldIsVisible() {
        assertThat(
            shouldShowHomeHeaderSearch(
                collapseProgress = 1f,
                forceShowSearchField = true,
            ),
        ).isFalse()
    }

    @Test
    fun headerSearch_visibleWhenSearchFieldIsFullyCollapsed() {
        assertThat(
            shouldShowHomeHeaderSearch(
                collapseProgress = 1f,
                forceShowSearchField = false,
            ),
        ).isTrue()
    }
}
