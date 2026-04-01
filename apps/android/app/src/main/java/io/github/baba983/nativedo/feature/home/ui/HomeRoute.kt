package io.github.baba983.nativedo.feature.home.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.baba983.nativedo.NativeDoApplication
import io.github.baba983.nativedo.core.storage.RecentSearchStore
import io.github.baba983.nativedo.feature.home.data.HomeRepository
import io.github.baba983.nativedo.feature.home.domain.LoadHomeFeedUseCase
import io.github.baba983.nativedo.feature.search.ui.SearchScreen
import io.github.baba983.nativedo.feature.search.ui.rememberSearchViewModel
import io.github.baba983.nativedo.ui.theme.NativeDoChromeShape
import io.github.baba983.nativedo.ui.theme.NativeDoPillShape
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras

private const val HOME_HEADER_SEARCH_THRESHOLD = 0.98f
private val HOME_SEARCH_FIELD_HEIGHT = 48.dp
private val HOME_SEARCH_CONTAINER_HEIGHT = 64.dp
private val HOME_TABS_CONTAINER_HEIGHT = 52.dp

@Composable
fun HomeRoute(
    onTopicClick: (Long) -> Unit,
    onUserClick: (String) -> Unit,
    headerSearchRequestToken: Int = 0,
    onHeaderSearchVisibilityChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContainer = remember(context) {
        (context.applicationContext as NativeDoApplication).appContainer
    }
    val viewModel: HomeViewModel = viewModel(
        factory = remember(appContainer) {
            HomeViewModelFactory(
                loadHomeFeed = LoadHomeFeedUseCase(
                    repository = HomeRepository(appContainer.discourseApiClient),
                ),
            )
        },
    )
    val recentSearchStore = remember { RecentSearchStore() }
    val searchViewModel = rememberSearchViewModel(
        appContainer = appContainer,
        recentSearchStore = recentSearchStore,
        key = "home-search",
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchUiState by searchViewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var isSearchMode by rememberSaveable { mutableStateOf(false) }
    var searchOpenedFromHeader by rememberSaveable { mutableStateOf(false) }
    var forceShowSearchField by rememberSaveable { mutableStateOf(false) }
    var handledHeaderSearchRequestToken by rememberSaveable { mutableIntStateOf(0) }

    fun closeSearch() {
        forceShowSearchField = searchOpenedFromHeader
        searchOpenedFromHeader = false
        isSearchMode = false
    }

    BackHandler(enabled = isSearchMode) {
        closeSearch()
    }

    LaunchedEffect(headerSearchRequestToken) {
        if (headerSearchRequestToken > handledHeaderSearchRequestToken) {
            handledHeaderSearchRequestToken = headerSearchRequestToken
            searchOpenedFromHeader = true
            isSearchMode = true
        }
    }

    LaunchedEffect(forceShowSearchField, listState.isScrollInProgress) {
        if (forceShowSearchField && listState.isScrollInProgress) {
            forceShowSearchField = false
        }
    }

    LaunchedEffect(isSearchMode) {
        if (isSearchMode) {
            onHeaderSearchVisibilityChanged(false)
        }
    }

    DisposableEffect(onHeaderSearchVisibilityChanged) {
        onDispose {
            onHeaderSearchVisibilityChanged(false)
        }
    }

    AnimatedContent(
        targetState = isSearchMode,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            (
                fadeIn(animationSpec = tween(durationMillis = 220)) +
                    scaleIn(
                        animationSpec = tween(durationMillis = 220),
                        initialScale = 0.985f,
                    )
                ) togetherWith (
                fadeOut(animationSpec = tween(durationMillis = 180)) +
                    scaleOut(
                        animationSpec = tween(durationMillis = 180),
                        targetScale = 0.985f,
                    )
                )
        },
        label = "home_search_mode",
    ) { searchMode ->
        if (searchMode) {
            SearchScreen(
                uiState = searchUiState,
                onQueryChanged = searchViewModel::onQueryChanged,
                onQuerySubmitted = searchViewModel::onQuerySubmitted,
                onTopicClick = onTopicClick,
                onUserClick = onUserClick,
                modifier = Modifier.fillMaxSize(),
                onCloseRequest = ::closeSearch,
                autoFocus = true,
            )
        } else {
            HomeFeedContent(
                uiState = uiState,
                listState = listState,
                forceShowSearchField = forceShowSearchField,
                onTopicClick = onTopicClick,
                onFeedSelected = viewModel::onFeedSelected,
                onRefresh = viewModel::onRefresh,
                onSearchClick = {
                    searchOpenedFromHeader = false
                    forceShowSearchField = false
                    isSearchMode = true
                },
                onHeaderSearchVisibilityChanged = onHeaderSearchVisibilityChanged,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun HomeFeedContent(
    uiState: HomeUiState,
    listState: LazyListState,
    forceShowSearchField: Boolean,
    onTopicClick: (Long) -> Unit,
    onFeedSelected: (HomeFeedTab) -> Unit,
    onRefresh: () -> Unit,
    onSearchClick: () -> Unit,
    onHeaderSearchVisibilityChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val errorMessage = uiState.errorMessage
    val density = LocalDensity.current
    val searchFieldTravelPx = remember(density) {
        with(density) { HOME_SEARCH_CONTAINER_HEIGHT.roundToPx() }
    }
    val rawSearchCollapseProgress by remember(listState, searchFieldTravelPx) {
        derivedStateOf {
            homeSearchCollapseProgress(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                collapseDistancePx = searchFieldTravelPx,
            )
        }
    }
    val searchCollapseProgress = if (forceShowSearchField) 0f else rawSearchCollapseProgress
    val showHeaderSearch by remember(searchCollapseProgress, forceShowSearchField) {
        derivedStateOf {
            shouldShowHomeHeaderSearch(
                collapseProgress = searchCollapseProgress,
                forceShowSearchField = forceShowSearchField,
            )
        }
    }
    val stickyChromeColor = lerp(
        start = colors.backgroundBase,
        stop = colors.backgroundElevated,
        fraction = 0.34f,
    ).copy(alpha = 0.97f)

    LaunchedEffect(showHeaderSearch, onHeaderSearchVisibilityChanged) {
        onHeaderSearchVisibilityChanged(showHeaderSearch)
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = HOME_SEARCH_CONTAINER_HEIGHT + HOME_TABS_CONTAINER_HEIGHT,
                bottom = 12.dp,
            ),
        ) {
            if (uiState.isRefreshing) {
                item(key = "refresh-indicator") {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = colors.accentMuted,
                    )
                }
            }

            when {
                uiState.isLoading -> {
                    item(key = "loading") {
                        HomeFeedbackCard {
                            CircularProgressIndicator()
                        }
                    }
                }

                errorMessage != null && uiState.items.isEmpty() -> {
                    item(key = "error") {
                        HomeFeedbackCard {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = errorMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                TextButton(onClick = onRefresh) {
                                    Text(text = "Retry")
                                }
                            }
                        }
                    }
                }

                uiState.isEmpty -> {
                    item(key = "empty") {
                        HomeFeedbackCard {
                            Text(
                                text = "No topics yet",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                else -> {
                    itemsIndexed(
                        items = uiState.items,
                        key = { _, topic -> topic.id },
                    ) { index, topic ->
                        TopicListItem(
                            topic = topic,
                            onClick = { onTopicClick(topic.id) },
                            modifier = Modifier.fillMaxWidth(),
                            showDivider = index != uiState.items.lastIndex,
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(HOME_TABS_CONTAINER_HEIGHT)
                .graphicsLayer {
                    translationY = searchFieldTravelPx * (1f - searchCollapseProgress)
                }
                .background(stickyChromeColor),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeFeedTab.entries.forEach { tab ->
                    FilterChip(
                        selected = uiState.selectedTab == tab,
                        onClick = { onFeedSelected(tab) },
                        label = { Text(text = tab.label()) },
                        shape = NativeDoPillShape,
                        border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = colors.backgroundElevated.copy(alpha = 0.82f),
                            labelColor = colors.textSecondary,
                            selectedContainerColor = colors.accentMuted,
                            selectedLabelColor = colors.textPrimary,
                        ),
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f),
            )
        }

        HomeSearchEntry(
            onClick = onSearchClick,
            collapseProgress = searchCollapseProgress,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    translationY = -searchFieldTravelPx * searchCollapseProgress
                },
        )
    }
}

@Composable
private fun HomeSearchEntry(
    onClick: () -> Unit,
    collapseProgress: Float,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    val stickyBackgroundColor = lerp(
        start = colors.backgroundBase,
        stop = colors.backgroundElevated,
        fraction = 0.34f,
    ).copy(alpha = 0.96f)
    val fieldColor = colors.backgroundElevated.copy(alpha = 0.98f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HOME_SEARCH_CONTAINER_HEIGHT)
            .graphicsLayer {
                alpha = 1f - collapseProgress
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(stickyBackgroundColor),
        )
        HorizontalDivider(
            modifier = Modifier.align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f),
        )

        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .height(HOME_SEARCH_FIELD_HEIGHT)
                .clickable(onClick = onClick),
            shape = NativeDoChromeShape,
            color = fieldColor,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            ),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colors.textSecondary,
                )
                Text(
                    text = "Search linux.do",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun HomeFeedbackCard(
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

internal fun homeSearchCollapseProgress(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    collapseDistancePx: Int,
): Float = when {
    firstVisibleItemIndex > 0 -> 1f
    collapseDistancePx <= 0 -> 0f
    else -> (firstVisibleItemScrollOffset / collapseDistancePx.toFloat()).coerceIn(0f, 1f)
}

internal fun shouldShowHomeHeaderSearch(
    collapseProgress: Float,
    forceShowSearchField: Boolean,
): Boolean = !forceShowSearchField && collapseProgress >= HOME_HEADER_SEARCH_THRESHOLD

private class HomeViewModelFactory(
    private val loadHomeFeed: LoadHomeFeedUseCase,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(loadHomeFeed = loadHomeFeed) as T
    }
}

private fun HomeFeedTab.label(): String = when (this) {
    HomeFeedTab.Latest -> "Latest"
    HomeFeedTab.New -> "New"
    HomeFeedTab.Top -> "Top"
}
