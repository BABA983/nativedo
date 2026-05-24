package io.github.baba983.nativedo.feature.topic.ui

import android.graphics.RectF
import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.PostReaction
import io.github.baba983.nativedo.core.model.PostReactionUsersGroup
import io.github.baba983.nativedo.core.model.toggledReaction
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.core.ui.GalleryImage
import io.github.baba983.nativedo.core.ui.UserAvatar
import io.github.baba983.nativedo.core.ui.htmlToPreviewText
import org.telegram.ui.ActionBar.Theme
import io.github.baba983.nativedo.ui.actionbar.rememberThemeRevision
import io.github.baba983.nativedo.ui.actionbar.themedColor
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TOPIC_HEADER_ITEM_INDEX = 0
private const val TOPIC_LOAD_MORE_THRESHOLD = 4
private val topicHeaderDateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

private data class TopicJumpHighlight(
    val postId: Long,
    val nonce: Int,
)

private data class TopicReactionSheetState(
    val postId: Long,
    val reactions: List<PostReaction>,
    val initialReactionId: String? = null,
)

private data class TopicReactionUsersUiState(
    val groups: List<PostReactionUsersGroup> = emptyList(),
    val selectedReactionId: String? = null,
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicDetailContent(
    topicId: Long,
    uiState: TopicUiState,
    baseUrl: String,
    cookieStore: CookieStore,
    svgSpriteMarkup: String,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onPostUpdated: (Post) -> Unit,
    fetchPostByNumber: suspend (Long, Int) -> String,
    fetchReactionUsers: suspend (Long) -> List<PostReactionUsersGroup>,
    toggleReaction: suspend (Long, String) -> Unit,
    onCollapsedTitleChanged: (String) -> Unit,
    onTopicClick: (Long) -> Unit,
    onOpenLink: (String) -> Unit,
    hiddenImageUrl: String?,
    onImageBoundsChanged: (String, RectF?) -> Unit,
    onImageViewChanged: (String, View?) -> Unit,
    onOpenImage: (String, List<GalleryImage>, RectF?, Bitmap?, View?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    val colors = rememberTopicDetailColors()
    val coroutineScope = rememberCoroutineScope()
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 20.dp
    val replyTargetsByPostNumber = remember(uiState.posts) {
        topicReplyTargetsByPostNumber(uiState.posts)
    }
    val replySheetEntriesByPostNumber = remember(uiState.posts) {
        topicReplySheetEntriesByPostNumber(uiState.posts)
    }
    val localPostsByPostNumber = remember(uiState.posts) {
        uiState.posts.mapNotNull { post ->
            post.postNumber?.let { postNumber -> postNumber to post.cookedHtml }
        }.toMap()
    }
    var activeReplySheetPostNumber by remember(topicId) { mutableStateOf<Int?>(null) }
    val activeReplySheetItems = activeReplySheetPostNumber?.let(replySheetEntriesByPostNumber::get).orEmpty()
    val replySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var activeReactionSheetState by remember(topicId) { mutableStateOf<TopicReactionSheetState?>(null) }
    val reactionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var reactionUsersUiState by remember(topicId) { mutableStateOf(TopicReactionUsersUiState()) }
    var jumpHighlightNonce by remember(topicId) { mutableStateOf(0) }
    var jumpHighlight by remember { mutableStateOf<TopicJumpHighlight?>(null) }
    val showCollapsedTitle by remember(listState, uiState.title) {
        derivedStateOf {
            uiState.title.isNotBlank() &&
                listState.layoutInfo.visibleItemsInfo.none { it.index == TOPIC_HEADER_ITEM_INDEX }
        }
    }

    fun jumpToLoadedPost(target: TopicReplyTarget) {
        coroutineScope.launch {
            listState.animateScrollToItem(target.itemIndex)
            jumpHighlightNonce += 1
            jumpHighlight = TopicJumpHighlight(
                postId = target.postId,
                nonce = jumpHighlightNonce,
            )
        }
    }

    fun togglePostReaction(post: Post, reaction: PostReaction) {
        coroutineScope.launch {
            val currentPost = uiState.posts.firstOrNull { candidate -> candidate.id == post.id } ?: return@launch
            val optimisticPost = currentPost.toggledReaction(
                reactionId = reaction.id,
                emojiCatalog = uiState.emojiCatalog,
            )
            onPostUpdated(optimisticPost)
            val result = runCatching {
                toggleReaction(post.id, reaction.id)
            }
            if (result.isFailure) {
                onPostUpdated(currentPost)
            }
        }
    }

    LaunchedEffect(showCollapsedTitle, uiState.title) {
        onCollapsedTitleChanged(if (showCollapsedTitle) uiState.title else "")
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
        val groups = runCatching { fetchReactionUsers(reactionSheet.postId) }.getOrDefault(emptyList())
        reactionUsersUiState = TopicReactionUsersUiState(
            groups = groups,
            selectedReactionId = reactionUsersUiState.selectedReactionId ?: groups.firstOrNull()?.id,
            isLoading = false,
            hasLoaded = true,
        )
    }

    LaunchedEffect(
        listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index,
        listState.layoutInfo.totalItemsCount,
        uiState.hasMore,
        uiState.isLoadingMore,
    ) {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@LaunchedEffect
        if (
            uiState.hasMore &&
            !uiState.isLoadingMore &&
            !uiState.isLoading &&
            lastVisible >= listState.layoutInfo.totalItemsCount - TOPIC_LOAD_MORE_THRESHOLD
        ) {
            onLoadMore()
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = colors.background) {
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            state = pullToRefreshState,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomPadding),
            ) {
                when {
                    uiState.isLoading -> {
                        item { TopicHeaderSkeleton(colors) }
                        items(TOPIC_DETAIL_SKELETON_POSTS.size) { index ->
                            TopicPostSkeleton(
                                template = TOPIC_DETAIL_SKELETON_POSTS[index],
                                colors = colors,
                            )
                        }
                    }

                    uiState.errorMessage != null && uiState.posts.isEmpty() -> item {
                        TopicDetailErrorState(
                            message = uiState.errorMessage.orEmpty(),
                            onRetry = onRefresh,
                            colors = colors,
                        )
                    }

                    else -> {
                        item {
                            TopicHeaderBlock(
                                title = uiState.title,
                                createdAt = uiState.createdAt,
                                lastPostedAt = uiState.lastPostedAt,
                                tags = uiState.tags,
                                totalPosts = uiState.totalPosts,
                                loadedPosts = uiState.posts.size,
                                viewCount = uiState.viewCount,
                                colors = colors,
                            )
                        }
                        itemsIndexed(uiState.posts, key = { _, post -> post.id }) { index, post ->
                            val replyTarget = post.replyToPostNumber?.let(replyTargetsByPostNumber::get)
                            val replySheetItems = post.postNumber?.let(replySheetEntriesByPostNumber::get).orEmpty()
                            TopicPostCell(
                                post = post,
                                index = index,
                                colors = colors,
                                baseUrl = baseUrl,
                                cookieStore = cookieStore,
                                svgSpriteMarkup = svgSpriteMarkup,
                                validReactionIds = uiState.validReactions,
                                emojiCatalog = uiState.emojiCatalog,
                                currentTopicId = topicId,
                                localPostsByPostNumber = localPostsByPostNumber,
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
                                onExpandQuote = { sourceTopicId, sourcePostNumber ->
                                    runCatching { fetchPostByNumber(sourceTopicId, sourcePostNumber) }
                                },
                                onRepliesClick = post.postNumber?.let { postNumber ->
                                    if (replySheetItems.isNotEmpty()) {
                                        { activeReplySheetPostNumber = postNumber }
                                    } else {
                                        null
                                    }
                                },
                                onReplyTargetClick = replyTarget?.let { target ->
                                    { jumpToLoadedPost(target) }
                                },
                                onReactionSelected = { reaction ->
                                    togglePostReaction(post, reaction)
                                },
                                onMoreReactionsClick = { reactions, initialReactionId ->
                                    activeReactionSheetState = TopicReactionSheetState(
                                        postId = post.id,
                                        reactions = reactions,
                                        initialReactionId = initialReactionId,
                                    )
                                },
                                onReactionPickerSelect = { reaction ->
                                    togglePostReaction(post, reaction)
                                },
                                onTopicClick = onTopicClick,
                                onOpenLink = onOpenLink,
                                hiddenImageUrl = hiddenImageUrl,
                                onImageBoundsChanged = onImageBoundsChanged,
                                onImageViewChanged = onImageViewChanged,
                                onOpenImage = onOpenImage,
                                onOpenAuthor = {
                                    if (post.authorUsername.isNotBlank()) {
                                        onOpenLink("$baseUrl/u/${post.authorUsername}")
                                    }
                                },
                                isJumpHighlighted = jumpHighlight?.postId == post.id,
                            )
                        }
                        if (uiState.isLoadingMore) {
                            item { TopicDetailCenterState("加载更多…", colors, showProgress = true) }
                        }
                        uiState.loadMoreErrorMessage?.let { message ->
                            item {
                                TopicDetailLoadMoreErrorState(
                                    message = message,
                                    onRetry = onLoadMore,
                                    colors = colors,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (activeReplySheetPostNumber != null) {
        TopicRepliesSheet(
            replies = activeReplySheetItems,
            baseUrl = baseUrl,
            colors = colors,
            sheetState = replySheetState,
            onDismissRequest = {
                activeReplySheetPostNumber = null
            },
            onReplyClick = { reply ->
                activeReplySheetPostNumber = null
                reply.postNumber?.let(replyTargetsByPostNumber::get)?.let(::jumpToLoadedPost)
            },
        )
    }

    activeReactionSheetState?.let { reactionSheet ->
        TopicReactionsSheet(
            reactions = reactionSheet.reactions,
            baseUrl = baseUrl,
            colors = colors,
            usersUiState = reactionUsersUiState,
            sheetState = reactionSheetState,
            onReactionSelected = { reactionId ->
                reactionUsersUiState = reactionUsersUiState.copy(selectedReactionId = reactionId)
            },
            onUserClick = { username ->
                activeReactionSheetState = null
                reactionUsersUiState = TopicReactionUsersUiState()
                onOpenLink("$baseUrl/u/$username")
            },
            onDismissRequest = {
                activeReactionSheetState = null
                reactionUsersUiState = TopicReactionUsersUiState()
            },
        )
    }
}

@Composable
private fun TopicHeaderBlock(
    title: String,
    createdAt: String,
    lastPostedAt: String,
    tags: List<String>,
    totalPosts: Int,
    loadedPosts: Int,
    viewCount: Int,
    colors: TopicDetailColors,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.rowBackground)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title.ifBlank { "话题" },
                style = MaterialTheme.typography.titleLarge,
                color = colors.title,
                fontWeight = FontWeight.SemiBold,
            )
            topicHeaderDateLine(createdAt = createdAt, lastPostedAt = lastPostedAt).takeIf { it.isNotBlank() }?.let { dateLine ->
                Text(
                    text = dateLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "已加载 $loadedPosts / $totalPosts 楼 · $viewCount 浏览",
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondary,
            )
            if (tags.isNotEmpty()) {
                Text(
                    text = tags.joinToString(prefix = "#", separator = " #"),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        HorizontalDivider(color = colors.divider)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicRepliesSheet(
    replies: List<TopicReplySheetItem>,
    baseUrl: String,
    colors: TopicDetailColors,
    sheetState: androidx.compose.material3.SheetState,
    onDismissRequest: () -> Unit,
    onReplyClick: (TopicReplySheetItem) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = colors.rowBackground,
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
                color = colors.title,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = replies,
                    key = { reply -> reply.postId },
                ) { reply ->
                    ReplySheetItem(
                        reply = reply,
                        baseUrl = baseUrl,
                        colors = colors,
                        onClick = { onReplyClick(reply) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReplySheetItem(
    reply: TopicReplySheetItem,
    baseUrl: String,
    colors: TopicDetailColors,
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
                    color = colors.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = topicPostTimeLabel(reply.createdAt).orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.secondary,
                )
            }
            Text(
                text = topicPostUsernameLabel(reply.authorUsername),
                style = MaterialTheme.typography.labelMedium,
                color = colors.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = reply.previewText.ifBlank { "View original reply" },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onClick) {
                Text(text = "View original reply")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicReactionsSheet(
    reactions: List<PostReaction>,
    baseUrl: String,
    colors: TopicDetailColors,
    usersUiState: TopicReactionUsersUiState,
    sheetState: androidx.compose.material3.SheetState,
    onReactionSelected: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = colors.rowBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = 560.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Reactions",
                style = MaterialTheme.typography.titleMedium,
                color = colors.title,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                reactions.forEach { reaction ->
                    TopicReactionChip(
                        reaction = reaction,
                        baseUrl = baseUrl,
                        colors = colors,
                        selected = reaction.id == usersUiState.selectedReactionId,
                        compact = false,
                        modifier = Modifier.clickable { onReactionSelected(reaction.id) },
                    )
                }
            }
            TopicReactionUsersSection(
                usersUiState = usersUiState,
                baseUrl = baseUrl,
                colors = colors,
                onUserClick = onUserClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

@Composable
private fun TopicReactionUsersSection(
    usersUiState: TopicReactionUsersUiState,
    baseUrl: String,
    colors: TopicDetailColors,
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
            color = colors.title,
        )
        when {
            usersUiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
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
                        color = colors.secondary,
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onUserClick(user.username) },
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UserAvatar(
                                label = user.username,
                                avatarTemplate = user.avatarTemplate,
                                baseUrl = baseUrl,
                                size = 24.dp,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = user.displayName.ifBlank { user.username },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "@${user.username}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicDetailCenterState(
    text: String,
    colors: TopicDetailColors,
    showProgress: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = 18.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showProgress) {
            CircularProgressIndicator()
        }
        Text(text = text, color = colors.secondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TopicDetailErrorState(
    message: String,
    onRetry: () -> Unit,
    colors: TopicDetailColors,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = 18.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "加载失败",
            color = colors.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(message, color = colors.secondary, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onRetry) { Text("重试") }
    }
}

@Composable
private fun TopicDetailLoadMoreErrorState(
    message: String,
    onRetry: () -> Unit,
    colors: TopicDetailColors,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            message,
            color = colors.secondary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) { Text("重试") }
    }
}

internal data class TopicDetailColors(
    val background: Color,
    val rowBackground: Color,
    val title: Color,
    val body: Color,
    val secondary: Color,
    val accent: Color,
    val divider: Color,
)

@Composable
internal fun rememberTopicDetailColors(): TopicDetailColors {
    rememberThemeRevision()
    return TopicDetailColors(
        background = themedColor(Theme.key_iv_background),
        rowBackground = themedColor(Theme.key_windowBackgroundWhite),
        title = themedColor(Theme.key_windowBackgroundWhiteBlackText),
        body = themedColor(Theme.key_windowBackgroundWhiteBlackText),
        secondary = themedColor(Theme.key_windowBackgroundWhiteGrayText),
        accent = themedColor(Theme.key_windowBackgroundWhiteBlueText),
        divider = themedColor(Theme.key_divider),
    )
}

private fun topicHeaderDateLine(
    createdAt: String,
    lastPostedAt: String,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String = buildList {
    topicHeaderDateLabel("Started", createdAt, zoneId)?.let(::add)
    topicHeaderDateLabel("Active", lastPostedAt, zoneId)?.let(::add)
}.joinToString(separator = " · ")

private fun topicHeaderDateLabel(
    label: String,
    rawValue: String,
    zoneId: ZoneId,
): String? {
    if (rawValue.isBlank()) return null
    val dateLabel = runCatching {
        OffsetDateTime.parse(rawValue)
            .atZoneSameInstant(zoneId)
            .format(topicHeaderDateFormatter)
    }.getOrNull() ?: return null
    return "$label $dateLabel"
}
