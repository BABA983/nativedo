package io.github.baba983.nativedo.feature.topic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.baba983.nativedo.NativeDoApplication
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.session.SessionEvent
import io.github.baba983.nativedo.core.ui.HtmlPostBodyView
import io.github.baba983.nativedo.core.ui.LocalBulletinHostState
import io.github.baba983.nativedo.core.ui.RequestErrorCard
import io.github.baba983.nativedo.core.ui.UserAvatar
import io.github.baba983.nativedo.core.ui.requestErrorBulletin
import io.github.baba983.nativedo.core.ui.requestErrorPresentation
import io.github.baba983.nativedo.feature.topic.data.TopicRepository
import io.github.baba983.nativedo.feature.topic.domain.LoadTopicUseCase
import io.github.baba983.nativedo.ui.NativeDoPullToRefreshIndicator
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras

private const val TOPIC_LOAD_MORE_THRESHOLD = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicRoute(
    topicId: Long,
    onTopicClick: (Long) -> Unit = {},
    onUserClick: (String) -> Unit = {},
    onSessionRecoveryRequired: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContainer = remember(context) {
        (context.applicationContext as NativeDoApplication).appContainer
    }
    val bulletinHostState = LocalBulletinHostState.current
    val sessionState by appContainer.sessionCoordinator.sessionState.collectAsStateWithLifecycle()
    val authVersion by appContainer.sessionCoordinator.authVersion.collectAsStateWithLifecycle()
    var handledAuthVersion by rememberSaveable { mutableStateOf(UNSET_AUTH_VERSION) }
    val viewModel: TopicViewModel = viewModel(
        key = "topic-$topicId",
        factory = remember(appContainer, topicId) {
            TopicViewModelFactory(
                loadTopic = LoadTopicUseCase(
                    repository = TopicRepository(appContainer.discourseApiClient),
                ),
                topicId = topicId,
                currentSessionVersion = { appContainer.sessionCoordinator.authVersion.value },
            )
        },
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = NativeDoThemeExtras.colors
    val initialLoadErrorPresentation = uiState.errorMessage?.let {
        requestErrorPresentation(
            rawMessage = it,
            genericTitle = "Couldn't load topic",
            genericDescription = "Please try again in a moment.",
        )
    }
    val loadMoreErrorPresentation = uiState.loadMoreErrorMessage?.let {
        requestErrorPresentation(
            rawMessage = it,
            genericTitle = "Couldn't load more posts",
            genericDescription = "Please try again in a moment.",
        )
    }
    var shownRefreshError by rememberSaveable(topicId) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    val currentSessionState by rememberUpdatedState(sessionState)
    val currentAuthVersion by rememberUpdatedState(authVersion)
    val refreshTopicIfNeeded: () -> Unit = {
        val latestAuthVersion = currentAuthVersion
        if (
            shouldRefreshTopicForHandledAuthVersion(
                sessionState = currentSessionState,
                handledAuthVersion = handledAuthVersion,
                currentAuthVersion = latestAuthVersion,
            )
        ) {
            handledAuthVersion = latestAuthVersion
            viewModel.refresh()
        }
    }

    val shouldLoadMore by remember(listState, uiState.hasMore, uiState.isLoadingMore) {
        derivedStateOf {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            shouldLoadMoreTopicPosts(
                lastVisibleIndex = lastVisibleIndex,
                totalItemsCount = listState.layoutInfo.totalItemsCount,
                hasMore = uiState.hasMore,
                isLoadingMore = uiState.isLoadingMore,
            )
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMore()
        }
    }

    LaunchedEffect(uiState.isAuthRecoveryRequired) {
        if (uiState.isAuthRecoveryRequired) {
            onSessionRecoveryRequired()
            viewModel.onAuthRecoveryHandled()
        }
    }

    LaunchedEffect(authVersion, sessionState) {
        if (handledAuthVersion == UNSET_AUTH_VERSION) {
            handledAuthVersion = authVersion
        } else {
            refreshTopicIfNeeded()
        }
    }

    LaunchedEffect(appContainer.sessionCoordinator) {
        appContainer.sessionCoordinator.events.collect { event ->
            if (event is SessionEvent.LoginCompleted || event is SessionEvent.SessionVerified) {
                refreshTopicIfNeeded()
            }
        }
    }

    LaunchedEffect(uiState.errorMessage, uiState.posts.size) {
        when {
            uiState.errorMessage.isNullOrBlank() -> shownRefreshError = null
            uiState.posts.isNotEmpty() && uiState.errorMessage != shownRefreshError -> {
                shownRefreshError = uiState.errorMessage
                bulletinHostState?.show(
                    requestErrorBulletin(
                        rawMessage = uiState.errorMessage,
                        genericTitle = "Couldn't refresh topic",
                        genericDescription = "Please try again in a moment.",
                    ),
                )
            }
        }
    }

    when {
        uiState.isLoading -> {
            TopicFeedbackCard(
                modifier = modifier.fillMaxSize(),
            ) {
                CircularProgressIndicator()
            }
        }

        uiState.canRetry -> {
            TopicFeedbackCard(
                modifier = modifier.fillMaxSize(),
            ) {
                RequestErrorCard(
                    presentation = requireNotNull(initialLoadErrorPresentation),
                    onRetry = viewModel::refresh,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        else -> PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
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
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = uiState.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = topicPostCountLabel(
                                totalPosts = uiState.totalPosts,
                                loadedPosts = uiState.posts.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }

                    if (uiState.posts.isNotEmpty()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f),
                        )
                    }
                }
                itemsIndexed(
                    items = uiState.posts,
                    key = { _, post -> post.id },
                ) { index, post ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UserAvatar(
                                label = post.authorUsername,
                                avatarTemplate = post.authorAvatarTemplate,
                                baseUrl = appContainer.baseUrl,
                                size = 32.dp,
                            )
                            Text(
                                text = "@${post.authorUsername}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        HtmlPostBodyView(
                            html = post.cookedHtml,
                            cookieStore = appContainer.cookieStore,
                            baseUrl = appContainer.baseUrl,
                            onTopicClick = onTopicClick,
                            onUserClick = onUserClick,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (index != uiState.posts.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f),
                        )
                    }
                }

                if (uiState.isLoadingMore) {
                    item(key = "load-more") {
                        TopicFeedbackCard { CircularProgressIndicator() }
                    }
                }

                if (uiState.loadMoreErrorMessage != null) {
                    item(key = "load-more-error") {
                        TopicFeedbackCard {
                            RequestErrorCard(
                                presentation = requireNotNull(loadMoreErrorPresentation),
                                onRetry = viewModel::loadMore,
                                modifier = Modifier.fillMaxWidth(),
                                compact = true,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun shouldLoadMoreTopicPosts(
    lastVisibleIndex: Int,
    totalItemsCount: Int,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    threshold: Int = TOPIC_LOAD_MORE_THRESHOLD,
): Boolean {
    if (!hasMore || isLoadingMore || totalItemsCount == 0) return false
    return lastVisibleIndex >= totalItemsCount - threshold
}

internal fun topicPostCountLabel(totalPosts: Int, loadedPosts: Int): String {
    val count = if (totalPosts > 0) totalPosts else loadedPosts
    return "$count posts"
}

internal fun shouldRefreshTopicForHandledAuthVersion(
    sessionState: AppSessionState,
    handledAuthVersion: Long,
    currentAuthVersion: Long,
): Boolean {
    return sessionState is AppSessionState.Authenticated &&
        currentAuthVersion > handledAuthVersion
}

@Composable
private fun TopicFeedbackCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private class TopicViewModelFactory(
    private val loadTopic: LoadTopicUseCase,
    private val topicId: Long,
    private val currentSessionVersion: () -> Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return TopicViewModel(
            loadTopic = loadTopic,
            topicId = topicId,
            currentSessionVersion = currentSessionVersion,
        ) as T
    }
}

private const val UNSET_AUTH_VERSION = Long.MIN_VALUE
