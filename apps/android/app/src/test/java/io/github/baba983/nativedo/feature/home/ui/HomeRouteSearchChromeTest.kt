package io.github.baba983.nativedo.feature.home.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.ui.theme.NativeDoExtendedColors
import org.junit.Test

class HomeRouteSearchChromeTest {
    @Test
    fun skeletonPalette_usesMutedGrayStopsInsteadOfGlassHighlight() {
        val palette = homeSkeletonPalette(
            colors = NativeDoExtendedColors(
                backgroundBase = Color(0xFFFFFFFF),
                backgroundElevated = Color(0xFFFFFFFF),
                glassBackground = Color(0xFF123456),
                glassBackgroundStrong = Color(0xFF234567),
                glassStroke = Color(0xFF345678),
                glassStrokeStrong = Color(0xFFFF0000),
                glassShadow = Color(0xFF456789),
                accent = Color(0xFF56789A),
                accentMuted = Color(0xFF6789AB),
                textPrimary = Color(0xFF111111),
                textSecondary = Color(0xFF000000),
                glowPrimary = Color(0xFF789ABC),
                glowSecondary = Color(0xFF89ABCD),
            ),
        )
        val baseArgb = palette.base.toArgb()
        val highlightArgb = palette.highlight.toArgb()
        val baseChannel = (baseArgb ushr 16) and 0xFF
        val highlightChannel = (highlightArgb ushr 16) and 0xFF

        assertThat(baseArgb ushr 24).isEqualTo(0xF0)
        assertThat(highlightArgb ushr 24).isEqualTo(0xB8)
        assertThat(baseChannel).isEqualTo((baseArgb ushr 8) and 0xFF)
        assertThat(baseChannel).isEqualTo(baseArgb and 0xFF)
        assertThat(highlightChannel).isEqualTo((highlightArgb ushr 8) and 0xFF)
        assertThat(highlightChannel).isEqualTo(highlightArgb and 0xFF)
        assertThat(highlightChannel).isLessThan(baseChannel)
        assertThat(highlightArgb and 0x00FFFFFF).isNotEqualTo(0x00FF0000)
    }

    @Test
    fun skeletonSpec_firstRowUsesWideTitleAndReplyBadge() {
        val spec = homeTopicSkeletonSpec(index = 0)

        assertThat(spec.titleWidthFraction).isEqualTo(0.82f)
        assertThat(spec.metaWidthFraction).isEqualTo(0.38f)
        assertThat(spec.showReplyBadge).isTrue()
    }

    @Test
    fun skeletonSpec_secondRowUsesShorterTitleWithoutReplyBadge() {
        val spec = homeTopicSkeletonSpec(index = 1)

        assertThat(spec.titleWidthFraction).isEqualTo(0.68f)
        assertThat(spec.metaWidthFraction).isEqualTo(0.46f)
        assertThat(spec.showReplyBadge).isFalse()
    }

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

    @Test
    fun chromeState_resetsCollapseWhenSearchFieldIsForcedVisible() {
        val state = buildHomeSearchChromeState(
            collapseProgress = 1f,
            forceShowSearchField = true,
        )

        assertThat(state.collapseProgress).isEqualTo(0f)
        assertThat(state.tabsOffsetProgress).isEqualTo(1f)
        assertThat(state.entryAlpha).isEqualTo(1f)
        assertThat(state.entryOffsetProgress).isEqualTo(0f)
        assertThat(state.showHeaderSearch).isFalse()
    }

    @Test
    fun chromeState_usesCollapsedProgressForHeaderChrome() {
        val state = buildHomeSearchChromeState(
            collapseProgress = 1f,
            forceShowSearchField = false,
        )

        assertThat(state.collapseProgress).isEqualTo(1f)
        assertThat(state.tabsOffsetProgress).isEqualTo(0f)
        assertThat(state.entryAlpha).isEqualTo(0f)
        assertThat(state.entryOffsetProgress).isEqualTo(1f)
        assertThat(state.showHeaderSearch).isTrue()
    }

    @Test
    fun loadMore_requestedWhenLastVisibleItemIsInsideThreshold() {
        assertThat(
            shouldLoadMoreHomeFeed(
                lastVisibleIndex = 17,
                totalItemsCount = 20,
                hasMore = true,
                isLoadingMore = false,
            ),
        ).isTrue()
    }

    @Test
    fun loadMore_notRequestedWhenNoMorePagesRemain() {
        assertThat(
            shouldLoadMoreHomeFeed(
                lastVisibleIndex = 17,
                totalItemsCount = 20,
                hasMore = false,
                isLoadingMore = false,
            ),
        ).isFalse()
    }

    @Test
    fun refreshRequest_scrollsToTopWhenFirstItemIsOffscreen() {
        assertThat(
            shouldScrollHomeFeedToTopOnRefreshRequest(
                firstVisibleItemIndex = 2,
                firstVisibleItemScrollOffset = 0,
            ),
        ).isTrue()
    }

    @Test
    fun refreshRequest_doesNotScrollWhenAlreadyAtTop() {
        assertThat(
            shouldScrollHomeFeedToTopOnRefreshRequest(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0,
            ),
        ).isFalse()
    }
}
