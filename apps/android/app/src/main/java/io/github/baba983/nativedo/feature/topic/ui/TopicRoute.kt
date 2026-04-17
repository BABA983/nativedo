package io.github.baba983.nativedo.feature.topic.ui
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.baba983.nativedo.NativeDoApplication
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.defaultReactionFallback
import io.github.baba983.nativedo.core.model.toggledReaction
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.session.SessionEvent
import io.github.baba983.nativedo.core.ui.BulletinMessage
import io.github.baba983.nativedo.core.ui.BulletinTone
import io.github.baba983.nativedo.core.ui.LocalBulletinHostState
import io.github.baba983.nativedo.core.ui.RequestErrorCard
import io.github.baba983.nativedo.core.ui.UserAvatar
import io.github.baba983.nativedo.core.ui.htmlToPreviewText
import io.github.baba983.nativedo.core.ui.requestErrorBulletin
import io.github.baba983.nativedo.core.ui.requestErrorPresentation
import io.github.baba983.nativedo.feature.topic.data.TopicRepository
import io.github.baba983.nativedo.feature.topic.domain.LoadTopicUseCase
import io.github.baba983.nativedo.ui.NativeDoGlassSurface
import io.github.baba983.nativedo.ui.NativeDoPullToRefreshIndicator
import io.github.baba983.nativedo.ui.theme.NativeDoGlassRole
import io.github.baba983.nativedo.ui.theme.NativeDoMotionTheme
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TOPIC_LOAD_MORE_THRESHOLD = 5
private const val TOPIC_HEADER_ITEM_INDEX = 0
private const val TOPIC_BOTTOM_SHELF_BUFFER_ITEMS = 2
private const val TOPIC_STATIC_ITEMS_BEFORE_POSTS = 1
private val topicPostTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val topicPostDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
private val topicPostUtcFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")

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
    val postNumber: Int?,
    val authorUsername: String,
    val authorDisplayName: String,
    val authorAvatarTemplate: String,
    val authorAnimatedAvatarUrl: String,
    val createdAt: String,
    val cookedHtml: String,
)

internal data class TopicJumpHighlight(
    val postId: Long,
    val nonce: Int,
)

internal data class TopicReplySheetItem(
    val postId: Long,
    val postNumber: Int?,
    val authorUsername: String,
    val authorDisplayName: String,
    val authorAvatarTemplate: String,
    val authorAnimatedAvatarUrl: String,
    val createdAt: String,
    val previewText: String,
)

internal data class TopicReactionSheetState(
    val postId: Long,
    val reactions: List<io.github.baba983.nativedo.core.model.PostReaction>,
    val initialReactionId: String? = null,
)

internal data class TopicReactionUsersUiState(
    val groups: List<io.github.baba983.nativedo.core.model.PostReactionUsersGroup> = emptyList(),
    val selectedReactionId: String? = null,
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TopicRoute(
    topicId: Long,
    onTopicClick: (Long) -> Unit = {},
    onUserClick: (String) -> Unit = {},
    onOpenLink: (String) -> Unit = {},
    onHeaderTitleChanged: (String?) -> Unit = {},
    initialScrollSnapshot: TopicScrollSnapshot? = null,
    onScrollSnapshotChanged: (TopicScrollSnapshot) -> Unit = {},
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
    val svgSpriteMarkup by appContainer.svgSpriteStore.sprite.collectAsStateWithLifecycle()
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
    // Keep the list save key stable while this topic moves between current/previous host slots.
    val initialListScrollSnapshot = remember(topicId) {
        resolveInitialTopicScrollSnapshot(initialScrollSnapshot)
    }
    val listState = rememberSaveable(
        topicId,
        saver = LazyListState.Saver,
    ) {
        LazyListState(
            firstVisibleItemIndex = initialListScrollSnapshot.firstVisibleItemIndex,
            firstVisibleItemScrollOffset = initialListScrollSnapshot.firstVisibleItemScrollOffset,
        )
    }
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
    val replySheetEntriesByPostNumber = remember(uiState.posts) {
        topicReplySheetEntriesByPostNumber(uiState.posts)
    }
    var activeReplySheetPostNumber by rememberSaveable(topicId) { mutableStateOf<Int?>(null) }
    val activeReplySheetItems = activeReplySheetPostNumber?.let(replySheetEntriesByPostNumber::get).orEmpty()
    val replySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var activeReactionSheetState by remember(topicId) { mutableStateOf<TopicReactionSheetState?>(null) }
    val reactionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var reactionUsersUiState by remember(topicId) { mutableStateOf(TopicReactionUsersUiState()) }
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

    val showCollapsedTopicTitle by remember(listState, uiState.title) {
        derivedStateOf {
            shouldShowCollapsedTopicTitle(
                title = uiState.title,
                visibleItemIndexes = listState.layoutInfo.visibleItemsInfo.map { it.index },
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

    LaunchedEffect(activeReactionSheetState?.postId) {
        val reactionSheet = activeReactionSheetState ?: return@LaunchedEffect
        val defaultReactionId = reactionSheet.initialReactionId
            ?: reactionSheet.reactions.firstOrNull { it.reacted }?.id
            ?: reactionSheet.reactions.firstOrNull()?.id
        reactionUsersUiState = TopicReactionUsersUiState(
            selectedReactionId = defaultReactionId,
            isLoading = true,
            hasLoaded = false,
        )
        val groups = runCatching {
            appContainer.discourseApiClient.fetchReactionUsers(reactionSheet.postId)
        }.getOrDefault(emptyList())
        val resolvedSelectedReactionId = reactionUsersUiState.selectedReactionId
            ?: groups.firstOrNull()?.id
        reactionUsersUiState = TopicReactionUsersUiState(
            groups = groups,
            selectedReactionId = resolvedSelectedReactionId,
            isLoading = false,
            hasLoaded = true,
        )
    }

    LaunchedEffect(showCollapsedTopicTitle, uiState.title) {
        onHeaderTitleChanged(
            uiState.title.takeIf { showCollapsedTopicTitle },
        )
    }

    DisposableEffect(onHeaderTitleChanged) {
        onDispose {
            onHeaderTitleChanged(null)
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
        onScrollSnapshotChanged(currentScrollSnapshot)
    }

    val showBottomShelf = uiState.posts.isNotEmpty() && bottomShelfArmed

    when {
        uiState.isLoading -> {
            TopicLoadingScene(
                modifier = modifier.fillMaxSize(),
            )
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
                        val replySheetItems = post.postNumber?.let(replySheetEntriesByPostNumber::get).orEmpty()
                        TopicPostCard(
                            post = post,
                            postIndex = index,
                            role = topicPostRole(index),
                            cookieStore = appContainer.cookieStore,
                            baseUrl = appContainer.baseUrl,
                            svgSpriteMarkup = svgSpriteMarkup,
                            onTopicClick = onTopicClick,
                            onUserClick = onUserClick,
                            onOpenLink = onOpenLink,
                            validReactionIds = uiState.validReactions,
                            emojiCatalog = uiState.emojiCatalog,
                            currentTopicId = topicId,
                            localPostsByPostNumber = uiState.posts
                                .mapNotNull { loadedPost -> loadedPost.postNumber?.let { number -> number to loadedPost.cookedHtml } }
                                .toMap(),
                            onExpandQuote = { sourceTopicId, sourcePostNumber ->
                                runCatching {
                                    appContainer.discourseApiClient
                                        .fetchPostByNumber(sourceTopicId, sourcePostNumber)
                                        .cookedHtml
                                }
                            },
                            replyTargetFloorLabel = post.replyToPostNumber?.let(::topicFloorLabelForPostNumber),
                            replyTargetAuthorLabel = replyTarget?.let { target ->
                                topicPostDisplayName(
                                    displayName = target.authorDisplayName,
                                    username = target.authorUsername,
                                )
                            }.orEmpty(),
                            replyTargetSummary = replyTarget?.cookedHtml?.htmlToPreviewText().orEmpty(),
                            replyCount = replySheetItems.size,
                            recentReplyParticipantLabels = replySheetItems.map { item -> item.authorUsername },
                            onRepliesClick = post.postNumber?.let { postNumber ->
                                if (replySheetItems.isNotEmpty()) {
                                    { activeReplySheetPostNumber = postNumber }
                                } else {
                                    null
                                }
                            },
                            onMoreReactionsClick = { reactions: List<io.github.baba983.nativedo.core.model.PostReaction>, initialReactionId: String? ->
                                activeReactionSheetState = TopicReactionSheetState(
                                    postId = post.id,
                                    reactions = reactions,
                                    initialReactionId = initialReactionId,
                                )
                            },
                            onReactionSelected = { reaction ->
                                coroutineScope.launch {
                                    val currentPost = uiState.posts.firstOrNull { candidate -> candidate.id == post.id } ?: return@launch
                                    val optimisticPost = currentPost.toggledReaction(
                                        reactionId = reaction.id,
                                        emojiCatalog = uiState.emojiCatalog,
                                    )
                                    viewModel.replacePost(optimisticPost)
                                    val result = runCatching {
                                        appContainer.discourseApiClient.toggleReaction(
                                            postId = post.id,
                                            reactionId = reaction.id,
                                        )
                                    }
                                    if (result.isFailure) {
                                        viewModel.replacePost(currentPost)
                                        bulletinHostState?.show(
                                            BulletinMessage(
                                                title = "Couldn't update reaction",
                                                subtitle = "Please try again.",
                                                tone = BulletinTone.Error,
                                            ),
                                        )
                                    }
                                }
                            },
                            onReactionPickerSelect = { reaction ->
                                coroutineScope.launch {
                                    val currentPost = uiState.posts.firstOrNull { candidate -> candidate.id == post.id } ?: return@launch
                                    val optimisticPost = currentPost.toggledReaction(
                                        reactionId = reaction.id,
                                        emojiCatalog = uiState.emojiCatalog,
                                    )
                                    viewModel.replacePost(optimisticPost)
                                    val result = runCatching {
                                        appContainer.discourseApiClient.toggleReaction(
                                            postId = post.id,
                                            reactionId = reaction.id,
                                        )
                                    }
                                    if (result.isFailure) {
                                        viewModel.replacePost(currentPost)
                                        bulletinHostState?.show(
                                            BulletinMessage(
                                                title = "Couldn't update reaction",
                                                subtitle = "Please try again.",
                                                tone = BulletinTone.Error,
                                            ),
                                        )
                                    }
                                }
                            },
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

    if (activeReplySheetPostNumber != null) {
        TopicRepliesSheet(
            sheetState = replySheetState,
            replies = activeReplySheetItems,
            baseUrl = appContainer.baseUrl,
            onDismissRequest = {
                activeReplySheetPostNumber = null
            },
            onReplyClick = { reply ->
                coroutineScope.launch {
                    activeReplySheetPostNumber = null
                    val target = reply.postNumber?.let(replyTargetsByPostNumber::get)
                    if (target != null) {
                        listState.animateScrollToItem(target.itemIndex)
                        jumpHighlightNonce += 1
                        jumpHighlight = TopicJumpHighlight(
                            postId = target.postId,
                            nonce = jumpHighlightNonce,
                        )
                    }
                }
            },
        )
    }

    activeReactionSheetState?.let { reactionSheet ->
        TopicReactionsSheet(
            sheetState = reactionSheetState,
            reactions = reactionSheet.reactions,
            baseUrl = appContainer.baseUrl,
            usersUiState = reactionUsersUiState,
            onReactionSelected = { reactionId ->
                reactionUsersUiState = reactionUsersUiState.copy(
                    selectedReactionId = reactionId,
                )
            },
            onUserClick = { username ->
                activeReactionSheetState = null
                reactionUsersUiState = TopicReactionUsersUiState()
                onUserClick(username)
            },
            onDismissRequest = {
                activeReactionSheetState = null
                reactionUsersUiState = TopicReactionUsersUiState()
            },
        )
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

internal fun topicPostUsesAnimatedTitle(title: String): Boolean = title.trim() in setOf("种子用户", "破界者")

internal fun shouldShowCollapsedTopicTitle(
    title: String,
    visibleItemIndexes: List<Int>,
    headerItemIndex: Int = TOPIC_HEADER_ITEM_INDEX,
): Boolean {
    return title.isNotBlank() &&
        visibleItemIndexes.isNotEmpty() &&
        headerItemIndex !in visibleItemIndexes
}

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
                    postNumber = post.postNumber,
                    authorUsername = post.authorUsername,
                    authorDisplayName = post.authorDisplayName,
                    authorAvatarTemplate = post.authorAvatarTemplate,
                    authorAnimatedAvatarUrl = post.authorAnimatedAvatarUrl,
                    createdAt = post.createdAt,
                    cookedHtml = post.cookedHtml,
                ),
            )
        }
    }
}

internal fun topicReplySheetEntriesByPostNumber(
    posts: List<Post>,
): Map<Int, List<TopicReplySheetItem>> = posts
    .filter { it.replyToPostNumber != null }
    .groupBy { requireNotNull(it.replyToPostNumber) }
    .mapValues { (_, replies) ->
        replies.map { post ->
            TopicReplySheetItem(
                postId = post.id,
                postNumber = post.postNumber,
                authorUsername = post.authorUsername,
                authorDisplayName = post.authorDisplayName,
                authorAvatarTemplate = post.authorAvatarTemplate,
                authorAnimatedAvatarUrl = post.authorAnimatedAvatarUrl,
                createdAt = post.createdAt,
                previewText = post.cookedHtml.htmlToPreviewText(),
            )
        }
    }

internal fun topicPostMetaLabel(
    index: Int,
    createdAt: String,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    val floorLabel = topicFloorLabel(index)
    val timeLabel = topicPostTimeLabel(
        rawValue = createdAt,
        now = now,
        zoneId = zoneId,
    )

    return if (timeLabel == null) {
        floorLabel
    } else {
        "$floorLabel · $timeLabel"
    }
}

internal fun topicPostTimeLabel(
    rawValue: String,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): String? {
    if (rawValue.isBlank()) return null

    val postedInstant = runCatching { OffsetDateTime.parse(rawValue).toInstant() }
        .recoverCatching {
            java.time.LocalDateTime.parse(rawValue, topicPostUtcFormatter)
                .atOffset(ZoneOffset.UTC)
                .toInstant()
        }
        .getOrNull()
        ?: return null
    val duration = Duration.between(postedInstant, now)
    val postedLocalDateTime = postedInstant.atZone(zoneId)
    val nowLocalDate = now.atZone(zoneId).toLocalDate()

    if (duration.isNegative) {
        return postedLocalDateTime.format(topicPostDateTimeFormatter)
    }

    return when {
        duration < Duration.ofMinutes(1) -> "just now"
        duration < Duration.ofHours(1) -> "${duration.toMinutes()}m"
        postedLocalDateTime.toLocalDate() == nowLocalDate -> postedLocalDateTime.format(topicPostTimeFormatter)
        else -> postedLocalDateTime.format(topicPostDateTimeFormatter)
    }
}

internal fun topicPostEditedMetaLabel(
    editCount: Int,
): String? {
    return editCount
        .takeIf { it > 0 }
        ?.toString()
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

internal fun resolveInitialTopicScrollSnapshot(
    snapshot: TopicScrollSnapshot?,
): TopicScrollSnapshot = snapshot ?: TopicScrollSnapshot(
    firstVisibleItemIndex = 0,
    firstVisibleItemScrollOffset = 0,
)

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

@Composable
private fun TopicLoadingScene(
    modifier: Modifier = Modifier,
) {
    val shimmerBrush = rememberTopicReactionSkeletonBrush()
    Box(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NativeDoGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                strong = true,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.42f)
                                .height(12.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                .background(shimmerBrush),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.26f)
                                .height(10.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                .background(shimmerBrush),
                        )
                    }
                }
            }

            repeat(2) {
                NativeDoGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.34f)
                                .height(11.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                .background(shimmerBrush),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                .background(shimmerBrush),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.88f)
                                .height(12.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                .background(shimmerBrush),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.56f)
                                .height(12.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                .background(shimmerBrush),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicRepliesSheet(
    sheetState: androidx.compose.material3.SheetState,
    replies: List<TopicReplySheetItem>,
    baseUrl: String,
    onDismissRequest: () -> Unit,
    onReplyClick: (TopicReplySheetItem) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = NativeDoThemeExtras.colors.backgroundElevated,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = topicReplyCountLabel(replies.size),
                style = MaterialTheme.typography.titleMedium,
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = replies,
                    key = { reply -> reply.postId },
                ) { reply ->
                    ReplySheetItem(
                        reply = reply,
                        baseUrl = baseUrl,
                        onClick = { onReplyClick(reply) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicReactionsSheet(
    sheetState: androidx.compose.material3.SheetState,
    reactions: List<io.github.baba983.nativedo.core.model.PostReaction>,
    baseUrl: String,
    usersUiState: TopicReactionUsersUiState,
    onReactionSelected: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = NativeDoThemeExtras.colors.backgroundElevated,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 420.dp, max = 560.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Reactions",
                style = MaterialTheme.typography.titleMedium,
            )
            ReactionFlowLayout(
                reactions = reactions,
                baseUrl = baseUrl,
                selectedReactionId = usersUiState.selectedReactionId,
                onReactionSelected = onReactionSelected,
                modifier = Modifier.fillMaxWidth(),
            )
            TopicReactionUsersSection(
                usersUiState = usersUiState,
                baseUrl = baseUrl,
                onUserClick = onUserClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

@Composable
private fun ReactionFlowLayout(
    reactions: List<io.github.baba983.nativedo.core.model.PostReaction>,
    baseUrl: String,
    selectedReactionId: String?,
    onReactionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sortedReactions = reactions.sortedWith(
        compareByDescending<io.github.baba983.nativedo.core.model.PostReaction> { it.reacted }
            .thenByDescending { it.count }
            .thenBy { it.id },
    )
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 6,
    ) {
        sortedReactions.forEach { reaction ->
            TopicReactionChip(
                reaction = reaction,
                baseUrl = baseUrl,
                selected = reaction.id == selectedReactionId,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    onReactionSelected(reaction.id)
                },
            )
        }
    }
}

@Composable
private fun TopicReactionUsersSection(
    usersUiState: TopicReactionUsersUiState,
    baseUrl: String,
    onUserClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedGroup = usersUiState.groups.firstOrNull { it.id == usersUiState.selectedReactionId }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Users",
            style = MaterialTheme.typography.titleSmall,
        )
        when {
            usersUiState.isLoading -> {
                TopicReactionUsersSkeleton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
            selectedGroup == null || selectedGroup.users.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = "No users yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NativeDoThemeExtras.colors.textSecondary,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        items = selectedGroup.users,
                        key = { user -> user.username },
                    ) { user ->
                        val interactionSource = remember { MutableInteractionSource() }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                ) { onUserClick(user.username) },
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UserAvatar(
                                label = user.username,
                                avatarTemplate = user.avatarTemplate,
                                animatedAvatarUrl = "",
                                baseUrl = baseUrl,
                                size = 24.dp,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = topicPostDisplayName(
                                        displayName = user.displayName,
                                        username = user.username,
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = topicPostUsernameLabel(user.username),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = NativeDoThemeExtras.colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    user.createdAt.takeIf { it.isNotBlank() }?.let { createdAt ->
                                        Text(
                                            text = "· ${topicPostTimeLabel(createdAt).orEmpty()}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = NativeDoThemeExtras.colors.textSecondary,
                                        )
                                    }
                                    if (user.canUndo) {
                                        Text(
                                            text = "· can undo",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = NativeDoThemeExtras.colors.accent,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicReactionUsersSkeleton(
    modifier: Modifier = Modifier,
) {
    val shimmerBrush = rememberTopicReactionSkeletonBrush()
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(4) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(shimmerBrush),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.42f)
                            .height(12.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .background(shimmerBrush),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.58f)
                            .height(10.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .background(shimmerBrush),
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberTopicReactionSkeletonBrush(): Brush {
    val colors = NativeDoThemeExtras.colors
    val motionScheme = NativeDoMotionTheme.scheme
    val glass = NativeDoThemeExtras.glass(NativeDoGlassRole.Card)
    val durationMillis = remember(motionScheme) {
        (motionScheme.page.durationMillis * 4) + motionScheme.label.enter.durationMillis
    }
    val transition = rememberInfiniteTransition(label = "topic_reaction_users_skeleton")
    val offset by transition.animateFloat(
        initialValue = -900f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "topic_reaction_users_skeleton_offset",
    )
    val base = lerp(
        start = colors.backgroundElevated,
        stop = colors.textSecondary,
        fraction = 0.08f + (glass.borderColor.alpha * 0.02f),
    ).copy(alpha = 0.94f)
    val highlight = lerp(
        start = colors.backgroundElevated,
        stop = colors.textSecondary,
        fraction = 0.18f + (glass.borderColor.alpha * 0.04f),
    ).copy(alpha = 0.72f)
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(x = offset - 220f, y = 0f),
        end = Offset(x = offset + 220f, y = 220f),
    )
}

@Composable
private fun ReplySheetItem(
    reply: TopicReplySheetItem,
    baseUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        UserAvatar(
            label = reply.authorUsername,
            avatarTemplate = reply.authorAvatarTemplate,
            animatedAvatarUrl = reply.authorAnimatedAvatarUrl,
            baseUrl = baseUrl,
            size = 28.dp,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = topicPostDisplayName(
                        displayName = reply.authorDisplayName,
                        username = reply.authorUsername,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = topicPostTimeLabel(reply.createdAt).orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = NativeDoThemeExtras.colors.textSecondary,
                )
            }
            Text(
                text = reply.previewText.ifBlank { "View original reply" },
                style = MaterialTheme.typography.bodyMedium,
                color = NativeDoThemeExtras.colors.textSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onClick) {
                Text(text = "View original reply")
            }
        }
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
