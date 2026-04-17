package io.github.baba983.nativedo.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import io.github.baba983.nativedo.feature.home.ui.HomeRoute
import io.github.baba983.nativedo.feature.profile.ui.ProfileRoute
import io.github.baba983.nativedo.feature.profile.ui.ProfileViewer
import io.github.baba983.nativedo.navigation.RootDestination
import io.github.baba983.nativedo.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Stable
internal class RootTabsHostState internal constructor(
    internal val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
) {
    val selectedRoute: String
        get() = RootDestination.bottomTabs
            .getOrNull(pagerState.currentPage)
            ?.route
            ?: RootDestination.start.route

    fun navigateTo(route: String) {
        val page = RootDestination.bottomTabs.indexOfFirst { it.route == route }
        if (page == -1 || page == pagerState.currentPage) {
            return
        }
        coroutineScope.launch {
            pagerState.animateScrollToPage(page)
        }
    }
}

@Composable
internal fun rememberRootTabsHostState(): RootTabsHostState {
    val pagerState = rememberPagerState(
        initialPage = RootDestination.bottomTabs.indexOf(RootDestination.start),
    ) { RootDestination.bottomTabs.size }
    val coroutineScope = rememberCoroutineScope()
    return remember(pagerState, coroutineScope) {
        RootTabsHostState(
            pagerState = pagerState,
            coroutineScope = coroutineScope,
        )
    }
}

@Composable
internal fun RootTabsHost(
    state: RootTabsHostState,
    homeRefreshRequestToken: Int,
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onHomeSearchModeChanged: (Boolean) -> Unit,
    onOpenLogin: () -> Unit,
    onOpenTopic: (Long) -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalPager(
        state = state.pagerState,
        modifier = modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        key = { page -> RootDestination.bottomTabs[page].route },
    ) { page ->
        when (RootDestination.bottomTabs[page]) {
            RootDestination.Home -> {
                HomeRoute(
                    onTopicClick = onOpenTopic,
                    onUserClick = onOpenUser,
                    refreshRequestToken = homeRefreshRequestToken,
                    themeMode = themeMode,
                    onThemeModeSelected = onThemeModeSelected,
                    onSearchModeChanged = onHomeSearchModeChanged,
                )
            }

            RootDestination.Profile -> {
                ProfileRoute(
                    viewer = ProfileViewer.Self,
                    themeMode = themeMode,
                    onThemeModeSelected = onThemeModeSelected,
                    onLoginClick = onOpenLogin,
                    onSessionRecoveryRequired = onOpenLogin,
                    onTopicClick = onOpenTopic,
                    onUserClick = onOpenUser,
                    onOpenLink = onOpenLink,
                )
            }

            RootDestination.Search -> Unit
        }
    }
}
