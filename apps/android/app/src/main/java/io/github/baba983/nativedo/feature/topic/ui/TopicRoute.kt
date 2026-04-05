package io.github.baba983.nativedo.feature.topic.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.session.SessionEvent
import io.github.baba983.nativedo.core.ui.BulletinMessage
import io.github.baba983.nativedo.core.ui.LocalBulletinHostState
import io.github.baba983.nativedo.core.ui.RequestErrorCard
import io.github.baba983.nativedo.core.ui.requestErrorBulletin
import io.github.baba983.nativedo.core.ui.requestErrorPresentation
import io.github.baba983.nativedo.feature.topic.data.TopicRepository
import io.github.baba983.nativedo.feature.topic.domain.LoadTopicUseCase
import io.github.baba983.nativedo.ui.NativeDoPullToRefreshIndicator
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TOPIC_LOAD_MORE_THRESHOLD = 5
private const val TOPIC_BOTTOM_SHELF_BUFFER_ITEMS = 2
private const val TOPIC_STATIC_ITEMS_BEFORE_POSTS = 1
private val topicPostTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal enum class TopicPostRole {
    OriginalPoster,
    Reply,
}

internal data class TopicScrollSnapshot(
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
)

internal data class TopicReplyTarget(
    val itemIndex: Int,
    val postId: Long,
    val authorUsername: String,
    val authorAvatarTemplate: String,
)

internal data class TopicJumpHighlight(
    val postId: Long,
    val nonce: Int,
)

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
    val coroutineScope = rememberCoroutineScope()
    var previousScrollSnapshot by remember { mutableStateOf<TopicScrollSnapshot?>(null) }
    var bottomShelfArmed by rememberSaveable(topicId) { mutableStateOf(false) }
    var jumpHighlightNonce by rememberSaveable(topicId) { mutableStateOf(0) }
    var jumpHighlight by remember { mutableStateOf<TopicJumpHighlight?>(null) }
    val currentSessionState by rememberUpdatedState(sessionState)
    val currentAuthVersion by rememberUpdatedState(authVersion)
    val replyTargetsByPostNumber = remember(uiState.posts) {
        topicReplyTargetsByPostNumber(uiState.posts)
    }
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

    val isAtBottom by remember(listState, uiState.posts.size, uiState.isLoadingMore, uiState.loadMoreErrorMessage) {
        derivedStateOf {
            isTopicListAtBottom(
                lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0,
                totalItemsCount = listState.layoutInfo.totalItemsCount,
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

    LaunchedEffect(jumpHighlight) {
        val currentHighlight = jumpHighlight ?: return@LaunchedEffect
        delay(600L)
        if (jumpHighlight == currentHighlight) {
            jumpHighlight = null
        }
    }

    LaunchedEffect(
        listState.firstVisibleItemIndex,
        listState.firstVisibleItemScrollOffset,
        listState.isScrollInProgress,
        uiState.posts.size,
        isAtBottom,
    ) {
        val currentScrollSnapshot = TopicScrollSnapshot(
            firstVisibleItemIndex = listState.firstVisibleItemIndex,
            firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
        )
        val isScrollingUp = isTopicScrollMovingUp(
            previous = previousScrollSnapshot,
            current = currentScrollSnapshot,
        )

        when {
            isAtBottom -> bottomShelfArmed = false
            shouldArmTopicBottomShelf(
                hasPosts = uiState.posts.isNotEmpty(),
                isAtBottom = isAtBottom,
                isScrollingUp = listState.isScrollInProgress && isScrollingUp,
            ) -> bottomShelfArmed = true
        }

        previousScrollSnapshot = currentScrollSnapshot
    }

    val showBottomShelf = uiState.posts.isNotEmpty() && bottomShelfArmed

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
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "topic-header") {
                        TopicHeader(
                            title = uiState.title,
                            createdAt = uiState.createdAt,
                            lastPostedAt = uiState.lastPostedAt,
                            viewCount = uiState.viewCount,
                            tags = uiState.tags,
                            totalPosts = uiState.totalPosts,
                            loadedPosts = uiState.posts.size,
                        )
                    }
                    itemsIndexed(
                        items = uiState.posts,
                        key = { _, post -> post.id },
                    ) { index, post ->
                        val replyTarget = post.replyToPostNumber?.let(replyTargetsByPostNumber::get)
                        TopicPostCard(
                            post = post,
                            postIndex = index,
                            role = topicPostRole(index),
                            cookieStore = appContainer.cookieStore,
                            baseUrl = appContainer.baseUrl,
                            onTopicClick = onTopicClick,
                            onUserClick = onUserClick,
                            replyTargetFloorLabel = post.replyToPostNumber?.let(::topicFloorLabelForPostNumber),
                            replyTargetAvatarLabel = replyTarget?.authorUsername.orEmpty(),
                            replyTargetAvatarTemplate = replyTarget?.authorAvatarTemplate.orEmpty(),
                            onReplyTargetClick = post.replyToPostNumber?.let { replyToPostNumber ->
                                {
                                    val target = replyTargetsByPostNumber[replyToPostNumber]
                                    if (target == null) {
                                        bulletinHostState?.show(
                                            BulletinMessage(
                                                title = context.getString(R.string.topic_reply_jump_unavailable_title),
                                                subtitle = context.getString(R.string.topic_reply_jump_unavailable_subtitle),
                                            ),
                                        )
                                    } else {
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(target.itemIndex)
                                            jumpHighlightNonce += 1
                                            jumpHighlight = TopicJumpHighlight(
                                                postId = target.postId,
                                                nonce = jumpHighlightNonce,
                                            )
                                        }
                                    }
                                }
                            },
                            isJumpHighlighted = jumpHighlight?.postId == post.id,
                        )
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

                AnimatedVisibility(
                    visible = showBottomShelf,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                ) {
                    BottomUtilityShelf(
                        onReplyClick = {
                            bulletinHostState?.show(
                                BulletinMessage(
                                    title = context.getString(R.string.topic_reader_reply_unavailable_title),
                                    subtitle = context.getString(R.string.topic_reader_reply_unavailable_subtitle),
                                ),
                            )
                        },
                        onMoreClick = {
                            bulletinHostState?.show(
                                BulletinMessage(
                                    title = context.getString(R.string.topic_reader_more_unavailable_title),
                                    subtitle = context.getString(R.string.topic_reader_more_unavailable_subtitle),
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}

internal fun topicPostRole(index: Int): TopicPostRole =
    if (index == 0) TopicPostRole.OriginalPoster else TopicPostRole.Reply

internal fun topicFloorLabel(index: Int): String = "${index + 1}F"

internal fun topicFloorLabelForPostNumber(postNumber: Int): String = "${postNumber}F"

internal fun topicPostDisplayName(
    displayName: String,
    username: String,
): String = displayName.ifBlank { username }

internal fun topicPostUsernameLabel(username: String): String = "@$username"

internal fun topicReplyTargetsByPostNumber(
    posts: List<Post>,
    leadingItemsCount: Int = TOPIC_STATIC_ITEMS_BEFORE_POSTS,
): Map<Int, TopicReplyTarget> = buildMap {
    posts.forEachIndexed { index, post ->
        post.postNumber?.let { postNumber ->
            put(
                postNumber,
                TopicReplyTarget(
                    itemIndex = index + leadingItemsCount,
                    postId = post.id,
                    authorUsername = post.authorUsername,
                    authorAvatarTemplate = post.authorAvatarTemplate,
                ),
            )
        }
    }
}

internal fun topicPostMetaLabel(
    index: Int,
    createdAt: String,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    val floorLabel = topicFloorLabel(index)
    val timeLabel = createdAt
        .takeIf { it.isNotBlank() }
        ?.let { rawCreatedAt ->
            runCatching {
                OffsetDateTime.parse(rawCreatedAt)
                    .atZoneSameInstant(zoneId)
                    .format(topicPostTimeFormatter)
            }.getOrNull()
        }

    return if (timeLabel == null) {
        floorLabel
    } else {
        "$floorLabel · $timeLabel"
    }
}

internal fun isTopicListAtBottom(
    lastVisibleIndex: Int,
    totalItemsCount: Int,
    bufferItems: Int = TOPIC_BOTTOM_SHELF_BUFFER_ITEMS,
): Boolean {
    if (totalItemsCount == 0) return true
    val lastBottomIndex = (totalItemsCount - 1 - bufferItems).coerceAtLeast(0)
    return lastVisibleIndex >= lastBottomIndex
}

internal fun isTopicScrollMovingUp(
    previous: TopicScrollSnapshot?,
    current: TopicScrollSnapshot,
): Boolean {
    if (previous == null) return false

    return current.firstVisibleItemIndex < previous.firstVisibleItemIndex ||
        (
            current.firstVisibleItemIndex == previous.firstVisibleItemIndex &&
                current.firstVisibleItemScrollOffset < previous.firstVisibleItemScrollOffset
            )
}

internal fun shouldArmTopicBottomShelf(
    hasPosts: Boolean,
    isAtBottom: Boolean,
    isScrollingUp: Boolean,
): Boolean = hasPosts && !isAtBottom && isScrollingUp

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
