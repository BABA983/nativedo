package io.github.baba983.nativedo.feature.home.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.baba983.nativedo.NativeDoApplication
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.ui.LocalBulletinHostState
import io.github.baba983.nativedo.core.ui.NativeDoSearchField
import io.github.baba983.nativedo.core.ui.RequestErrorCard
import io.github.baba983.nativedo.core.ui.requestErrorBulletin
import io.github.baba983.nativedo.core.ui.requestErrorPresentation
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.session.SessionEvent
import io.github.baba983.nativedo.core.storage.RecentSearchStore
import io.github.baba983.nativedo.feature.home.data.HomeRepository
import io.github.baba983.nativedo.feature.home.domain.LoadHomeFeedUseCase
import io.github.baba983.nativedo.feature.search.ui.SearchResultsContent
import io.github.baba983.nativedo.feature.search.ui.SearchSceneTopBar
import io.github.baba983.nativedo.feature.search.ui.rememberSearchViewModel
import io.github.baba983.nativedo.navigation.RootDestination
import io.github.baba983.nativedo.ui.NativeDoReaderScaffold
import io.github.baba983.nativedo.ui.NativeDoPullToRefreshIndicator
import io.github.baba983.nativedo.ui.NativeDoTopBar
import io.github.baba983.nativedo.ui.buildNativeDoRootChromeModel
import io.github.baba983.nativedo.ui.theme.NativeDoCardShape
import io.github.baba983.nativedo.ui.theme.NativeDoEasing
import io.github.baba983.nativedo.ui.theme.NativeDoGlassRole
import io.github.baba983.nativedo.ui.theme.NativeDoMotionTheme
import io.github.baba983.nativedo.ui.theme.NativeDoPillShape
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import io.github.baba983.nativedo.ui.theme.ThemeMode
import io.github.baba983.nativedo.ui.theme.nativeDoMotionProgressValue
import kotlinx.coroutines.launch

private const val HOME_HEADER_SEARCH_THRESHOLD = 0.98f
private const val HOME_LOAD_MORE_THRESHOLD = 5
private val HOME_SEARCH_CONTAINER_HEIGHT = 64.dp
private val HOME_TABS_CONTAINER_HEIGHT = 52.dp

internal data class HomeSearchChromeState(
    val collapseProgress: Float,
    val tabsOffsetProgress: Float,
    val entryAlpha: Float,
    val entryOffsetProgress: Float,
    val showHeaderSearch: Boolean,
)

@Composable
fun HomeRoute(
    onTopicClick: (Long) -> Unit,
    onUserClick: (String) -> Unit,
    refreshRequestToken: Int = 0,
    themeMode: ThemeMode = ThemeMode.System,
    onThemeModeSelected: (ThemeMode) -> Unit = {},
    onSearchModeChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContainer = remember(context) {
        (context.applicationContext as NativeDoApplication).appContainer
    }
    val sessionState by appContainer.sessionCoordinator.sessionState.collectAsStateWithLifecycle()
    val authVersion by appContainer.sessionCoordinator.authVersion.collectAsStateWithLifecycle()
    val categories by appContainer.categoryStore.categories.collectAsStateWithLifecycle()
    var handledAuthVersion by rememberSaveable { mutableStateOf(UNSET_HOME_AUTH_VERSION) }
    val homeViewModel: HomeViewModel = viewModel(
        factory = remember(appContainer) {
            HomeViewModelFactory(
                loadHomeFeed = LoadHomeFeedUseCase(
                    repository = HomeRepository(appContainer.discourseApiClient),
                ),
                currentSessionVersion = { appContainer.sessionCoordinator.authVersion.value },
            )
        },
    )
    val recentSearchStore = remember { RecentSearchStore() }
    val searchViewModel = rememberSearchViewModel(
        appContainer = appContainer,
        recentSearchStore = recentSearchStore,
        key = "home-search",
    )
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val searchUiState by searchViewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var isSearchMode by rememberSaveable { mutableStateOf(false) }
    var searchOpenedFromHeader by rememberSaveable { mutableStateOf(false) }
    var forceShowSearchField by rememberSaveable { mutableStateOf(false) }
    var handledRefreshRequestToken by rememberSaveable { mutableIntStateOf(0) }
    var contextualOptionsExpanded by rememberSaveable { mutableStateOf(false) }
    val currentSessionState by rememberUpdatedState(sessionState)
    val currentAuthVersion by rememberUpdatedState(authVersion)
    val currentOnSearchModeChanged by rememberUpdatedState(onSearchModeChanged)
    val contentMotion = NativeDoMotionTheme.scheme.content
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
    val searchChromeState = remember(rawSearchCollapseProgress, forceShowSearchField) {
        buildHomeSearchChromeState(
            collapseProgress = rawSearchCollapseProgress,
            forceShowSearchField = forceShowSearchField,
        )
    }
    val rootChromeModel = remember(
        searchChromeState.showHeaderSearch,
        isSearchMode,
        contextualOptionsExpanded,
    ) {
        buildNativeDoRootChromeModel(
            isRootRoute = true,
            activeRoute = RootDestination.Home.route,
            homeHeaderSearchVisible = searchChromeState.showHeaderSearch,
            isHomeSearchMode = isSearchMode,
            actionModeSelectionCount = 0,
            isContextualOptionsExpanded = contextualOptionsExpanded,
        )
    }

    fun closeSearch() {
        forceShowSearchField = searchOpenedFromHeader
        searchOpenedFromHeader = false
        isSearchMode = false
    }

    fun refreshFromHeader() {
        coroutineScope.launch {
            if (
                shouldScrollHomeFeedToTopOnRefreshRequest(
                    firstVisibleItemIndex = listState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                )
            ) {
                listState.scrollToItem(0)
            }
            homeViewModel.onRefresh()
        }
    }

    BackHandler(enabled = isSearchMode) {
        closeSearch()
    }

    LaunchedEffect(refreshRequestToken) {
        if (refreshRequestToken > handledRefreshRequestToken) {
            handledRefreshRequestToken = refreshRequestToken
            if (
                shouldScrollHomeFeedToTopOnRefreshRequest(
                    firstVisibleItemIndex = listState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                )
            ) {
                listState.scrollToItem(0)
            }
            homeViewModel.onRefresh()
        }
    }

    val refreshHomeIfNeeded: () -> Unit = {
        val latestAuthVersion = currentAuthVersion
        if (
            shouldRefreshHomeForHandledAuthVersion(
                sessionState = currentSessionState,
                handledAuthVersion = handledAuthVersion,
                currentAuthVersion = latestAuthVersion,
            )
        ) {
            handledAuthVersion = latestAuthVersion
            homeViewModel.onRefresh()
        }
    }

    LaunchedEffect(authVersion, sessionState) {
        if (handledAuthVersion == UNSET_HOME_AUTH_VERSION) {
            handledAuthVersion = authVersion
        } else {
            refreshHomeIfNeeded()
        }
    }

    LaunchedEffect(appContainer.sessionCoordinator) {
        appContainer.sessionCoordinator.events.collect { event ->
            if (
                event is SessionEvent.LoginCompleted ||
                event is SessionEvent.SessionVerified ||
                event is SessionEvent.LogoutCompleted ||
                event is SessionEvent.SessionExpired
            ) {
                refreshHomeIfNeeded()
            }
        }
    }

    LaunchedEffect(appContainer.categoryStore) {
        appContainer.categoryStore.loadIfNeeded()
    }

    LaunchedEffect(forceShowSearchField, listState.isScrollInProgress) {
        if (forceShowSearchField && listState.isScrollInProgress) {
            forceShowSearchField = false
        }
    }

    LaunchedEffect(isSearchMode, rootChromeModel.showContextualOptionsAction) {
        if (isSearchMode || !rootChromeModel.showContextualOptionsAction) {
            contextualOptionsExpanded = false
        }
    }

    LaunchedEffect(isSearchMode) {
        currentOnSearchModeChanged(isSearchMode)
    }

    DisposableEffect(currentOnSearchModeChanged) {
        onDispose {
            currentOnSearchModeChanged(false)
        }
    }

    val searchTransitionProgress by animateFloatAsState(
        targetValue = if (isSearchMode) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isSearchMode) 200 else 180,
            delayMillis = if (isSearchMode) 0 else 20,
            easing = NativeDoEasing.Standard,
        ),
        label = "home_search_transition",
    )
    val showSearchTopBar = isSearchMode || searchTransitionProgress > 0f

    NativeDoReaderScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                // NativeDoTopBar: title/icons fade + scale 0.95 in place (no translation)
                Box(
                    modifier = Modifier.graphicsLayer {
                        alpha = 1f - searchTransitionProgress
                        scaleX = 1f - 0.05f * searchTransitionProgress
                        scaleY = 1f - 0.05f * searchTransitionProgress
                    },
                ) {
                    NativeDoTopBar(
                        currentRoute = RootDestination.Home.route,
                        rootChrome = rootChromeModel,
                        titleOverride = null,
                        showBack = false,
                        themeMode = themeMode,
                        onHomeRefreshClick = ::refreshFromHeader,
                        onHomeSearchClick = {
                            searchOpenedFromHeader = true
                            forceShowSearchField = false
                            isSearchMode = true
                        },
                        onThemeModeSelected = {
                            contextualOptionsExpanded = false
                            onThemeModeSelected(it)
                        },
                        onToggleContextualOptions = {
                            contextualOptionsExpanded = !contextualOptionsExpanded
                        },
                        onDismissContextualOptions = {
                            contextualOptionsExpanded = false
                        },
                        onExitActionMode = {
                            contextualOptionsExpanded = false
                        },
                        onBackClick = {},
                    )
                }

                // SearchSceneTopBar: just fade in place (no translation)
                if (showSearchTopBar) {
                    Box(
                        modifier = Modifier.graphicsLayer {
                            alpha = searchTransitionProgress
                        },
                    ) {
                        SearchSceneTopBar(
                            query = searchUiState.query,
                            onQueryChanged = searchViewModel::onQueryChanged,
                            onQuerySubmitted = searchViewModel::onQuerySubmitted,
                            onCloseRequest = ::closeSearch,
                            autoFocus = isSearchMode,
                        )
                    }
                }
            }
        },
    ) { paddingValues ->
        // Content area: list fades + scales 0.95, search results fade + scale 1.05→1.0
        AnimatedContent(
            targetState = isSearchMode,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            transitionSpec = {
                if (targetState) {
                    // Opening: search results fade in, list scales down
                    (
                        fadeIn(animationSpec = tween(200, easing = NativeDoEasing.Standard)) +
                            scaleIn(
                                animationSpec = tween(200, easing = NativeDoEasing.Standard),
                                initialScale = 1.05f,
                            )
                        ) togetherWith (
                        fadeOut(animationSpec = tween(200, easing = NativeDoEasing.Standard)) +
                            scaleOut(
                                animationSpec = tween(200, easing = NativeDoEasing.Standard),
                                targetScale = 0.95f,
                            )
                        )
                } else {
                    // Closing: list fades in, search results scale up
                    (
                        fadeIn(animationSpec = tween(180, delayMillis = 20, easing = NativeDoEasing.Standard)) +
                            scaleIn(
                                animationSpec = tween(180, delayMillis = 20, easing = NativeDoEasing.Standard),
                                initialScale = 0.95f,
                            )
                        ) togetherWith (
                        fadeOut(animationSpec = tween(180, easing = NativeDoEasing.Standard)) +
                            scaleOut(
                                animationSpec = tween(180, easing = NativeDoEasing.Standard),
                                targetScale = 1.05f,
                            )
                        )
                }
            },
            label = "home_search_mode",
        ) { searchActive ->
            if (searchActive) {
                SearchResultsContent(
                    uiState = searchUiState,
                    categories = categories,
                    baseUrl = appContainer.baseUrl,
                    onQuerySubmitted = searchViewModel::onQuerySubmitted,
                    onTopicClick = onTopicClick,
                    onUserClick = onUserClick,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                HomeFeedContent(
                    uiState = uiState,
                    categories = categories,
                    listState = listState,
                    searchChromeState = searchChromeState,
                    baseUrl = appContainer.baseUrl,
                    onTopicClick = { topicId ->
                        homeViewModel.markTopicSeen(topicId)
                        onTopicClick(topicId)
                    },
                    onFeedSelected = homeViewModel::onFeedSelected,
                    onRefresh = homeViewModel::onRefresh,
                    onLoadMore = homeViewModel::loadNextPage,
                    onSearchClick = {
                        searchOpenedFromHeader = false
                        forceShowSearchField = false
                        isSearchMode = true
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeFeedContent(
    uiState: HomeUiState,
    categories: Map<Int, io.github.baba983.nativedo.core.model.CategorySummary>,
    listState: LazyListState,
    searchChromeState: HomeSearchChromeState,
    baseUrl: String,
    onTopicClick: (Long) -> Unit,
    onFeedSelected: (HomeFeedTab) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pullToRefreshState = rememberPullToRefreshState()
    val bulletinHostState = LocalBulletinHostState.current
    val colors = NativeDoThemeExtras.colors
    val chromeGlass = NativeDoThemeExtras.glass(NativeDoGlassRole.Chrome)
    val errorMessage = uiState.errorMessage
    val initialLoadErrorPresentation = errorMessage?.let {
        requestErrorPresentation(
            rawMessage = it,
            genericTitle = "Couldn't load topics",
            genericDescription = "Pull to refresh or try again.",
        )
    }
    val loadMoreErrorPresentation = uiState.loadMoreErrorMessage?.let {
        requestErrorPresentation(
            rawMessage = it,
            genericTitle = "Couldn't load more topics",
            genericDescription = "Please try again in a moment.",
        )
    }
    val density = LocalDensity.current
    val searchFieldTravelPx = remember(density) {
        with(density) { HOME_SEARCH_CONTAINER_HEIGHT.roundToPx() }
    }
    var shownRefreshError by rememberSaveable(uiState.selectedTab) { mutableStateOf<String?>(null) }
    val stickyChromeColor = chromeGlass.containerColor.copy(alpha = 0.97f)

    LaunchedEffect(errorMessage, uiState.items.size) {
        when {
            errorMessage.isNullOrBlank() -> shownRefreshError = null
            uiState.items.isNotEmpty() && errorMessage != shownRefreshError -> {
                shownRefreshError = errorMessage
                bulletinHostState?.show(
                    requestErrorBulletin(
                        rawMessage = errorMessage,
                        genericTitle = "Couldn't refresh topics",
                        genericDescription = "Please try again in a moment.",
                    ),
                )
            }
        }
    }

    val shouldLoadMore by remember(
        listState,
        uiState.items.size,
        uiState.hasMore,
        uiState.isLoadingMore,
        uiState.loadMoreErrorMessage,
    ) {
        derivedStateOf {
            shouldLoadMoreHomeFeed(
                lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1,
                totalItemsCount = uiState.items.size,
                hasMore = uiState.hasMore,
                isLoadingMore = uiState.isLoadingMore,
            ) && uiState.loadMoreErrorMessage == null
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMore()
        }
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        modifier = modifier.fillMaxSize(),
        indicator = {
            NativeDoPullToRefreshIndicator(
                state = pullToRefreshState,
                isRefreshing = uiState.isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = HOME_SEARCH_CONTAINER_HEIGHT + HOME_TABS_CONTAINER_HEIGHT,
                    bottom = 12.dp,
                ),
            ) {
                when {
                    uiState.isLoading -> {
                        items(
                            count = HOME_SKELETON_ITEM_COUNT,
                            key = { index -> "loading-$index" },
                        ) { index ->
                            HomeTopicSkeletonItem(
                                index = index,
                                modifier = Modifier.fillMaxWidth(),
                                showDivider = index != HOME_SKELETON_ITEM_COUNT - 1,
                            )
                        }
                    }

                    errorMessage != null && uiState.items.isEmpty() -> {
                        item(key = "error") {
                            HomeFeedbackCard {
                                RequestErrorCard(
                                    presentation = requireNotNull(initialLoadErrorPresentation),
                                    onRetry = onRefresh,
                                    modifier = Modifier.fillMaxWidth(),
                                )
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
                                category = topic.categoryId?.let(categories::get),
                                baseUrl = baseUrl,
                                onClick = { onTopicClick(topic.id) },
                                modifier = Modifier.fillMaxWidth(),
                                showDivider = index != uiState.items.lastIndex,
                            )
                        }

                        if (uiState.isLoadingMore) {
                            item(key = "loading-more") {
                                HomeFeedbackCard {
                                    CircularProgressIndicator()
                                }
                            }
                        } else if (uiState.loadMoreErrorMessage != null) {
                            item(key = "load-more-error") {
                                HomeFeedbackCard {
                                    RequestErrorCard(
                                        presentation = requireNotNull(loadMoreErrorPresentation),
                                        onRetry = onLoadMore,
                                        modifier = Modifier.fillMaxWidth(),
                                        compact = true,
                                    )
                                }
                            }
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
                        translationY = searchFieldTravelPx * searchChromeState.tabsOffsetProgress
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
                    color = chromeGlass.borderColor.copy(alpha = 0.64f),
                )
            }

            HomeSearchEntry(
                onClick = onSearchClick,
                chromeState = searchChromeState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        translationY = -searchFieldTravelPx * searchChromeState.entryOffsetProgress
                    },
            )
        }
    }
}

@Composable
private fun HomeSearchEntry(
    onClick: () -> Unit,
    chromeState: HomeSearchChromeState,
    modifier: Modifier = Modifier,
) {
    val chromeGlass = NativeDoThemeExtras.glass(NativeDoGlassRole.Chrome)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HOME_SEARCH_CONTAINER_HEIGHT)
            .graphicsLayer {
                alpha = chromeState.entryAlpha
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(chromeGlass.containerColor.copy(alpha = 0.96f)),
        )
        HorizontalDivider(
            modifier = Modifier.align(Alignment.BottomCenter),
            color = chromeGlass.borderColor.copy(alpha = 0.64f),
        )

        NativeDoSearchField(
            value = "",
            onValueChange = {},
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            placeholder = stringResource(R.string.app_search_placeholder),
            readOnly = true,
            onClick = onClick,
        )
    }
}

@Composable
private fun HomeFeedbackCard(
    content: @Composable () -> Unit,
) {
    val colors = NativeDoThemeExtras.colors

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        shape = NativeDoCardShape,
        color = colors.backgroundElevated.copy(alpha = 0.9f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f),
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
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

internal fun buildHomeSearchChromeState(
    collapseProgress: Float,
    forceShowSearchField: Boolean,
): HomeSearchChromeState {
    val resolvedCollapseProgress = if (forceShowSearchField) {
        0f
    } else {
        collapseProgress.coerceIn(0f, 1f)
    }
    return HomeSearchChromeState(
        collapseProgress = resolvedCollapseProgress,
        tabsOffsetProgress = nativeDoMotionProgressValue(
            progress = resolvedCollapseProgress,
            start = 1f,
            end = 0f,
        ),
        entryAlpha = nativeDoMotionProgressValue(
            progress = resolvedCollapseProgress,
            start = 1f,
            end = 0f,
        ),
        entryOffsetProgress = resolvedCollapseProgress,
        showHeaderSearch = shouldShowHomeHeaderSearch(
            collapseProgress = resolvedCollapseProgress,
            forceShowSearchField = forceShowSearchField,
        ),
    )
}

internal fun shouldLoadMoreHomeFeed(
    lastVisibleIndex: Int,
    totalItemsCount: Int,
    hasMore: Boolean,
    isLoadingMore: Boolean,
): Boolean {
    if (!hasMore || isLoadingMore || totalItemsCount == 0 || lastVisibleIndex < 0) {
        return false
    }
    return lastVisibleIndex >= totalItemsCount - HOME_LOAD_MORE_THRESHOLD
}

internal fun shouldScrollHomeFeedToTopOnRefreshRequest(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
): Boolean = firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0

private class HomeViewModelFactory(
    private val loadHomeFeed: LoadHomeFeedUseCase,
    private val currentSessionVersion: () -> Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(
            loadHomeFeed = loadHomeFeed,
            currentSessionVersion = currentSessionVersion,
        ) as T
    }
}

internal fun shouldRefreshHomeForHandledAuthVersion(
    sessionState: AppSessionState,
    handledAuthVersion: Long,
    currentAuthVersion: Long,
): Boolean {
    if (currentAuthVersion <= handledAuthVersion) {
        return false
    }
    return sessionState != AppSessionState.Unknown && sessionState !is AppSessionState.Restoring
}

private const val UNSET_HOME_AUTH_VERSION = Long.MIN_VALUE

private fun HomeFeedTab.label(): String = when (this) {
    HomeFeedTab.Latest -> "Latest"
    HomeFeedTab.New -> "New"
    HomeFeedTab.Top -> "Top"
}
