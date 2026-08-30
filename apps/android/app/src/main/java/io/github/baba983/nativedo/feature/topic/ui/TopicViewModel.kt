package io.github.baba983.nativedo.feature.topic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.PostReaction
import io.github.baba983.nativedo.core.model.toggledReaction
import io.github.baba983.nativedo.feature.home.ui.readableMessage
import io.github.baba983.nativedo.core.model.PostReactionUsersGroup
import io.github.baba983.nativedo.feature.topic.data.TopicRepository
import io.github.baba983.nativedo.feature.topic.data.TopicReplyDraft
import io.github.baba983.nativedo.feature.topic.data.TopicReplyDraftKey
import io.github.baba983.nativedo.feature.topic.data.TopicReplyDraftStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TOPIC_POST_PAGE_SIZE = 20
private const val TOPIC_JUMP_TO_LATEST_PAGE_SIZE = 20
private const val POLL_VOTERS_PAGE_SIZE = 50

class TopicViewModel(
    private val repository: TopicRepository,
    private val topicId: Long,
    private val replyDraftStore: TopicReplyDraftStore = TopicReplyDraftStore(),
    private val replyDraftKey: TopicReplyDraftKey? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        TopicUiState(replyDraft = replyDraftKey?.let(replyDraftStore::load) ?: TopicReplyDraft()),
    )
    val uiState: StateFlow<TopicUiState> = _uiState.asStateFlow()

    private var remainingPostIds: List<Long> = emptyList()
    private var streamPostIds: List<Long> = emptyList()
    private var jumpToLatestGeneration: Int = 0
    private var jumpNonce: Int = 0
    private val pendingJumpPostNumbers = mutableSetOf<Int>()
    private val pendingJumpPostIds = mutableSetOf<Long>()
    private var reactionUsersRequestGeneration: Int = 0
    private var pollVotersRequestGeneration: Int = 0
    private var postRepliesRequestGeneration: Int = 0

    init {
        refresh()
    }

    fun refresh() {
        jumpToLatestGeneration++
        viewModelScope.launch {
            val hasExistingPosts = _uiState.value.posts.isNotEmpty()
            _uiState.value = _uiState.value.copy(
                isLoading = !hasExistingPosts,
                isRefreshing = hasExistingPosts,
                isLoadingMore = false,
                errorMessage = null,
                loadMoreErrorMessage = null,
            )
            repository.load(topicId).fold(
                onSuccess = { detail ->
                    val replyDraft = _uiState.value.replyDraft
                    streamPostIds = detail.streamPostIds
                    val loadedIds = detail.posts.map(Post::id).toSet()
                    remainingPostIds = streamPostIds.filterNot(loadedIds::contains)
                    _uiState.value = TopicUiState(
                        title = detail.title,
                        createdAt = detail.createdAt,
                        lastPostedAt = detail.lastPostedAt,
                        viewCount = detail.viewCount,
                        tags = detail.tags,
                        validReactions = detail.validReactions,
                        emojiCatalog = detail.emojiCatalog,
                        emojiCategories = detail.emojiCategories,
                        totalPosts = detail.totalPosts,
                        posts = detail.posts,
                        highestPostNumber = detail.highestPostNumber,
                        lastReadPostNumber = detail.lastReadPostNumber,
                        participants = detail.participants,
                        streamPostIds = streamPostIds,
                        isLoading = false,
                        isRefreshing = false,
                        hasMore = remainingPostIds.isNotEmpty(),
                        replyDraft = replyDraft,
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = error.readableMessage(),
                    )
                },
            )
        }
    }

    /**
     * Clears the page-down unread counter once the user reaches the bottom of
     * the loaded stream — the vendor chat clears `newUnreadMessageCount` at the
     * same boundary. Reading position is kept local; server sync (timings) is
     * a separate concern.
     */
    fun markLoadedPostsRead() {
        val state = _uiState.value
        // Anonymous sessions carry no Discourse read position — nothing to mark.
        if (state.lastReadPostNumber <= 0) return
        val highestLoaded = state.posts.maxOfOrNull { it.postNumber ?: 0 } ?: 0
        val effective = maxOf(state.lastReadPostNumber, highestLoaded)
        if (effective == state.lastReadPostNumber) return
        _uiState.value = state.copy(lastReadPostNumber = effective)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isRefreshing || state.isLoadingMore || remainingPostIds.isEmpty()) {
            return
        }

        val nextChunk = remainingPostIds.take(TOPIC_POST_PAGE_SIZE)
        viewModelScope.launch {
            _uiState.value = state.copy(
                isLoadingMore = true,
                loadMoreErrorMessage = null,
            )
            repository.loadPosts(topicId, nextChunk).fold(
                onSuccess = { posts ->
                    remainingPostIds = remainingPostIds.drop(nextChunk.size)
                    _uiState.value = _uiState.value.copy(
                        posts = mergePostsInStreamOrder(_uiState.value.posts, posts),
                        isLoadingMore = false,
                        hasMore = remainingPostIds.isNotEmpty(),
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        loadMoreErrorMessage = error.readableMessage(),
                    )
                },
            )
        }
    }

    fun replacePost(post: Post) {
        replacePostById(post)
    }

    fun onReactionSelected(postId: Long, reactionId: String) {
        val currentPost = _uiState.value.posts.firstOrNull { post -> post.id == postId } ?: return
        val optimisticPost = currentPost.toggledReaction(
            reactionId = reactionId,
            emojiCatalog = _uiState.value.emojiCatalog,
        )
        replacePostById(optimisticPost)

        viewModelScope.launch {
            repository.toggleReaction(postId = postId, reactionId = reactionId).onFailure {
                replacePostById(currentPost)
            }
        }
    }

    fun togglePostBookmark(postId: Long) {
        val currentPost = _uiState.value.posts.firstOrNull { post -> post.id == postId } ?: return
        val nextBookmarked = !currentPost.isBookmarked
        replacePostById(currentPost.copy(isBookmarked = nextBookmarked))

        viewModelScope.launch {
            repository.setPostBookmark(postId = postId, bookmarked = nextBookmarked).onFailure {
                replacePostById(currentPost)
            }
        }
    }

    fun openReactionSheet(
        postId: Long,
        reactions: List<PostReaction>,
        initialReactionId: String?,
        reactionUsersCount: Int = 0,
    ) {
        val selectedReactionId = initialReactionId
            ?: reactions.firstOrNull { reaction -> reaction.reacted }?.id
            ?: reactions.firstOrNull()?.id
        _uiState.value = _uiState.value.copy(
            reactionSheet = TopicReactionSheetUiState(
                postId = postId,
                reactions = reactions,
                reactionUsersCount = reactionUsersCount,
                initialReactionId = initialReactionId,
            ),
            reactionUsers = TopicReactionUsersUiState(
                selectedReactionId = selectedReactionId,
                isLoading = true,
                hasLoaded = false,
            ),
        )

        loadReactionUsers(postId)
    }

    fun selectReactionInSheet(reactionId: String) {
        _uiState.value = _uiState.value.copy(
            reactionUsers = _uiState.value.reactionUsers.copy(selectedReactionId = reactionId),
        )
    }

    fun retryReactionUsers() {
        val postId = _uiState.value.reactionSheet?.postId ?: return
        _uiState.value = _uiState.value.copy(
            reactionUsers = _uiState.value.reactionUsers.copy(
                groups = emptyList(),
                isLoading = true,
                hasLoaded = false,
                errorMessage = null,
            ),
        )
        loadReactionUsers(postId)
    }

    fun dismissReactionSheet() {
        reactionUsersRequestGeneration++
        _uiState.value = _uiState.value.copy(
            reactionSheet = null,
            reactionUsers = TopicReactionUsersUiState(),
        )
    }

    private fun loadReactionUsers(postId: Long) {
        val requestGeneration = ++reactionUsersRequestGeneration
        viewModelScope.launch {
            repository.loadReactionUsers(postId = postId).fold(
                onSuccess = { groups ->
                    if (requestGeneration != reactionUsersRequestGeneration || _uiState.value.reactionSheet?.postId != postId) {
                        return@fold
                    }
                    val currentSelected = _uiState.value.reactionUsers.selectedReactionId
                    _uiState.value = _uiState.value.copy(
                        reactionUsers = TopicReactionUsersUiState(
                            groups = groups,
                            selectedReactionId = currentSelected ?: groups.firstOrNull()?.id,
                            isLoading = false,
                            hasLoaded = true,
                        ),
                    )
                },
                onFailure = { throwable ->
                    if (requestGeneration != reactionUsersRequestGeneration || _uiState.value.reactionSheet?.postId != postId) {
                        return@fold
                    }
                    _uiState.value = _uiState.value.copy(
                        reactionUsers = _uiState.value.reactionUsers.copy(
                            groups = emptyList(),
                            isLoading = false,
                            hasLoaded = true,
                            errorMessage = throwable.message,
                        ),
                    )
                },
            )
        }
    }

    fun openPostReplies(sourcePost: Post, totalCount: Int = sourcePost.replyCount) {
        postRepliesRequestGeneration++
        _uiState.value = _uiState.value.copy(
            postRepliesSheet = TopicPostRepliesSheetUiState(
                sourcePost = sourcePost,
                totalCount = totalCount,
            ),
            postReplies = TopicPostRepliesUiState(isLoading = true),
        )
        loadPostReplies(afterPostNumber = 1, initial = true)
    }

    fun loadMorePostReplies() {
        val state = _uiState.value.postReplies
        if (!state.canLoadMore) return
        loadPostReplies(afterPostNumber = state.nextAfterPostNumber, initial = false)
    }

    fun retryPostReplies() {
        val state = _uiState.value.postReplies
        if (state.isLoading || state.isLoadingMore) return
        loadPostReplies(afterPostNumber = state.nextAfterPostNumber, initial = state.posts.isEmpty())
    }

    fun dismissPostReplies() {
        postRepliesRequestGeneration++
        _uiState.value = _uiState.value.copy(
            postRepliesSheet = null,
            postReplies = TopicPostRepliesUiState(),
        )
    }

    private fun loadPostReplies(afterPostNumber: Int, initial: Boolean) {
        val sheet = _uiState.value.postRepliesSheet ?: return
        val requestGeneration = postRepliesRequestGeneration
        _uiState.value = _uiState.value.copy(
            postReplies = _uiState.value.postReplies.copy(
                isLoading = initial,
                isLoadingMore = !initial,
                errorMessage = null,
            ),
        )
        viewModelScope.launch {
            repository.loadPostReplies(sheet.sourcePost.id, afterPostNumber).fold(
                onSuccess = { responsePosts ->
                    if (requestGeneration != postRepliesRequestGeneration || _uiState.value.postRepliesSheet?.sourcePost?.id != sheet.sourcePost.id) {
                        return@fold
                    }
                    val previous = _uiState.value.postReplies.posts
                    val merged = (previous + responsePosts).distinctBy(Post::id)
                    val nextAfter = responsePosts.mapNotNull(Post::postNumber).maxOrNull()
                        ?.coerceAtLeast(afterPostNumber)
                        ?: afterPostNumber
                    val endReached = responsePosts.isEmpty()
                        || merged.size == previous.size
                        || (sheet.totalCount > 0 && merged.size >= sheet.totalCount)
                    _uiState.value = _uiState.value.copy(
                        postReplies = TopicPostRepliesUiState(
                            posts = merged,
                            nextAfterPostNumber = nextAfter,
                            isLoading = false,
                            isLoadingMore = false,
                            hasLoaded = true,
                            endReached = endReached,
                            errorMessage = null,
                        ),
                        replyPreviewsBySourcePostId = _uiState.value.replyPreviewsBySourcePostId +
                            (sheet.sourcePost.id to merged),
                    )
                },
                onFailure = { error ->
                    if (requestGeneration != postRepliesRequestGeneration || _uiState.value.postRepliesSheet?.sourcePost?.id != sheet.sourcePost.id) {
                        return@fold
                    }
                    _uiState.value = _uiState.value.copy(
                        postReplies = _uiState.value.postReplies.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            hasLoaded = !initial || _uiState.value.postReplies.posts.isNotEmpty(),
                            errorMessage = error.readableMessage(),
                        ),
                    )
                },
            )
        }
    }

    fun openPollVotersSheet(postId: Long, args: PollVotersSheetArgs) {
        pollVotersRequestGeneration++
        val preferredOptionId = args.initialSelectedOptionId?.takeIf { it.isNotBlank() }
            ?: args.options.firstOrNull { option -> option.votes > 0 }?.id
            ?: args.options.firstOrNull()?.id
        val initialGroups = mergePollVoterGroups(args.options, emptyList())
        val initialPages = initialGroups.associate { group ->
            group.id to TopicPollVoterOptionPageUiState(endReached = group.count <= 0)
        }
        _uiState.value = _uiState.value.copy(
            pollVotersSheet = TopicPollVotersSheetUiState(
                postId = postId,
                pollName = args.pollName,
                pollTitle = args.pollTitle,
                options = args.options,
            ),
            pollVoters = TopicPollVotersUiState(
                groups = initialGroups,
                selectedOptionId = preferredOptionId,
                optionPages = initialPages,
            ),
        )
        preferredOptionId?.let { optionId -> loadPollVotersPage(optionId, page = 1) }
    }

    fun selectPollOptionInSheet(optionId: String) {
        val state = _uiState.value
        if (state.pollVotersSheet?.options?.none { option -> option.id == optionId } != false) return
        _uiState.value = state.copy(pollVoters = state.pollVoters.copy(selectedOptionId = optionId))
        val pageState = _uiState.value.pollVoters.optionPages[optionId]
        if (pageState == null || (!pageState.hasLoaded && !pageState.isLoading)) {
            loadPollVotersPage(optionId, page = 1)
        }
    }

    fun loadMorePollVoters() {
        val pollState = _uiState.value.pollVoters
        val optionId = pollState.selectedOptionId ?: return
        val pageState = pollState.optionPages[optionId] ?: return
        if (!pageState.hasLoaded || pageState.isLoading || pageState.endReached) return
        loadPollVotersPage(optionId, pageState.nextPage)
    }

    fun retryPollVoters() {
        val pollState = _uiState.value.pollVoters
        val optionId = pollState.selectedOptionId ?: return
        val pageState = pollState.optionPages[optionId] ?: return
        if (pageState.isLoading) return
        loadPollVotersPage(optionId, pageState.nextPage)
    }

    fun dismissPollVotersSheet() {
        pollVotersRequestGeneration++
        _uiState.value = _uiState.value.copy(
            pollVotersSheet = null,
            pollVoters = TopicPollVotersUiState(),
        )
    }

    private fun loadPollVotersPage(optionId: String, page: Int) {
        val sheet = _uiState.value.pollVotersSheet ?: return
        val currentPage = _uiState.value.pollVoters.optionPages[optionId]
            ?: TopicPollVoterOptionPageUiState()
        if (currentPage.isLoading || currentPage.endReached) return
        val requestGeneration = pollVotersRequestGeneration
        _uiState.value = _uiState.value.copy(
            pollVoters = _uiState.value.pollVoters.copy(
                optionPages = _uiState.value.pollVoters.optionPages + (
                    optionId to currentPage.copy(isLoading = true, errorMessage = null)
                ),
            ),
        )
        viewModelScope.launch {
            repository.loadPollVoters(
                postId = sheet.postId,
                pollName = sheet.pollName,
                optionId = optionId,
                page = page,
                limit = POLL_VOTERS_PAGE_SIZE,
            ).fold(
                onSuccess = { responseGroups ->
                    if (requestGeneration != pollVotersRequestGeneration || _uiState.value.pollVotersSheet?.postId != sheet.postId) {
                        return@fold
                    }
                    val responseUsers = responseGroups.firstOrNull { group -> group.id == optionId }?.users.orEmpty()
                    val existingGroup = _uiState.value.pollVoters.groups.firstOrNull { group -> group.id == optionId }
                    val existingUsers = existingGroup?.users.orEmpty()
                    val mergedUsers = (existingUsers + responseUsers).distinctBy { user -> user.username }
                    val optionTotal = sheet.options.firstOrNull { option -> option.id == optionId }?.votes
                    val endReached = when {
                        responseUsers.size < POLL_VOTERS_PAGE_SIZE -> true
                        optionTotal != null -> mergedUsers.size >= optionTotal
                        else -> false
                    }
                    val groups = _uiState.value.pollVoters.groups.map { group ->
                        if (group.id == optionId) group.copy(users = mergedUsers) else group
                    }
                    val updatedPage = TopicPollVoterOptionPageUiState(
                        nextPage = page + 1,
                        isLoading = false,
                        hasLoaded = true,
                        endReached = endReached,
                        errorMessage = null,
                    )
                    _uiState.value = _uiState.value.copy(
                        pollVoters = _uiState.value.pollVoters.copy(
                            groups = groups,
                            optionPages = _uiState.value.pollVoters.optionPages + (optionId to updatedPage),
                        ),
                    )
                },
                onFailure = { error ->
                    if (requestGeneration != pollVotersRequestGeneration || _uiState.value.pollVotersSheet?.postId != sheet.postId) {
                        return@fold
                    }
                    val failedPage = (_uiState.value.pollVoters.optionPages[optionId] ?: currentPage).copy(
                        nextPage = page,
                        isLoading = false,
                        hasLoaded = page > 1 || currentPage.hasLoaded,
                        errorMessage = error.message?.takeIf { it.isNotBlank() } ?: "加载投票用户失败",
                    )
                    _uiState.value = _uiState.value.copy(
                        pollVoters = _uiState.value.pollVoters.copy(
                            optionPages = _uiState.value.pollVoters.optionPages + (optionId to failedPage),
                        ),
                    )
                },
            )
        }
    }

    suspend fun expandQuote(sourceTopicId: Long, sourcePostNumber: Int): Result<String> {
        return repository.loadPostByNumber(topicId = sourceTopicId, postNumber = sourcePostNumber)
            .map { post -> post.cookedHtml }
    }

    fun updateReplyDraftText(raw: String) {
        updateReplyDraft(_uiState.value.replyDraft.copy(raw = raw))
    }

    fun prepareReplyDraft(initialText: String = "", replyToPostNumber: Int? = null) {
        val current = _uiState.value.replyDraft
        val nextRaw = when {
            initialText.isBlank() -> current.raw
            current.raw.isBlank() -> initialText
            current.raw.endsWith('\n') -> current.raw + initialText
            else -> current.raw + "\n" + initialText
        }
        updateReplyDraft(TopicReplyDraft(raw = nextRaw, replyToPostNumber = replyToPostNumber))
    }

    fun clearReplyTarget() {
        updateReplyDraft(_uiState.value.replyDraft.copy(replyToPostNumber = null))
    }

    fun clearReplyDraft() {
        updateReplyDraft(TopicReplyDraft())
    }

    fun submitReply() {
        val draft = _uiState.value.replyDraft
        submitReply(raw = draft.raw, replyToPostNumber = draft.replyToPostNumber)
    }

    fun submitReply(raw: String, replyToPostNumber: Int?) {
        val body = raw.trim()
        if (body.isEmpty() || _uiState.value.isSubmittingReply) {
            return
        }
        if (_uiState.value.replyDraft.raw != raw || _uiState.value.replyDraft.replyToPostNumber != replyToPostNumber) {
            updateReplyDraft(TopicReplyDraft(raw = raw, replyToPostNumber = replyToPostNumber))
        }
        _uiState.value = _uiState.value.copy(
            isSubmittingReply = true,
            replySubmitErrorMessage = null,
        )
        viewModelScope.launch {
            repository.createReply(topicId = topicId, raw = body, replyToPostNumber = replyToPostNumber).fold(
                onSuccess = { createdPost ->
                    if (!streamPostIds.contains(createdPost.id)) {
                        streamPostIds = streamPostIds + createdPost.id
                    }
                    remainingPostIds = remainingPostIds.filterNot { postId -> postId == createdPost.id }
                    val current = _uiState.value
                    val alreadyLoaded = current.posts.any { post -> post.id == createdPost.id }
                    val updatedExistingPosts = current.posts
                        .filterNot { post -> post.id == createdPost.id }
                        .map { post ->
                            if (!alreadyLoaded && replyToPostNumber != null && post.postNumber == replyToPostNumber) {
                                post.copy(replyCount = post.replyCount + 1)
                            } else {
                                post
                            }
                        }
                    val nextPosts = mergePostsInStreamOrder(updatedExistingPosts, listOf(createdPost))
                    val activeRepliesSheet = current.postRepliesSheet
                    val replyBelongsToOpenSheet = !alreadyLoaded
                        && replyToPostNumber != null
                        && activeRepliesSheet?.sourcePost?.postNumber == replyToPostNumber
                    val updatedRepliesSheet = if (replyBelongsToOpenSheet && activeRepliesSheet != null) {
                        activeRepliesSheet.copy(
                            sourcePost = activeRepliesSheet.sourcePost.copy(replyCount = activeRepliesSheet.sourcePost.replyCount + 1),
                            totalCount = activeRepliesSheet.totalCount + 1,
                        )
                    } else {
                        activeRepliesSheet
                    }
                    val updatedReplies = if (replyBelongsToOpenSheet) {
                        current.postReplies.copy(
                            posts = (current.postReplies.posts + createdPost).distinctBy(Post::id),
                        )
                    } else {
                        current.postReplies
                    }
                    _uiState.value = current.copy(
                        posts = nextPosts,
                        streamPostIds = streamPostIds,
                        totalPosts = if (alreadyLoaded) current.totalPosts else maxOf(current.totalPosts + 1, nextPosts.size),
                        isSubmittingReply = false,
                        replySubmitErrorMessage = null,
                        postRepliesSheet = updatedRepliesSheet,
                        postReplies = updatedReplies,
                        replyDraft = TopicReplyDraft(),
                    )
                    persistReplyDraft(TopicReplyDraft())
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isSubmittingReply = false,
                        replySubmitErrorMessage = error.readableMessage(),
                    )
                },
            )
        }
    }

    private fun updateReplyDraft(draft: TopicReplyDraft) {
        _uiState.value = _uiState.value.copy(
            replyDraft = draft,
            replySubmitErrorMessage = null,
        )
        persistReplyDraft(draft)
    }

    private fun persistReplyDraft(draft: TopicReplyDraft) {
        replyDraftKey?.let { key -> replyDraftStore.save(key, draft) }
    }

    fun onPollOptionsSelected(postId: Long, postNumber: Int?, pollName: String, optionIds: List<String>) {
        viewModelScope.launch {
            repository.votePoll(postId = postId, pollName = pollName, optionIds = optionIds).fold(
                onSuccess = {
                    if (postNumber != null) {
                        repository.loadPostByNumber(topicId = topicId, postNumber = postNumber).onSuccess(::replacePostById)
                    }
                },
                onFailure = { emitPollActionFailure(postId) },
            )
        }
    }

    fun onPollVoteRemoved(postId: Long, postNumber: Int?, pollName: String) {
        viewModelScope.launch {
            repository.removePollVote(postId = postId, pollName = pollName).fold(
                onSuccess = {
                    if (postNumber != null) {
                        repository.loadPostByNumber(topicId = topicId, postNumber = postNumber).onSuccess(::replacePostById)
                    }
                },
                onFailure = { emitPollActionFailure(postId) },
            )
        }
    }

    fun consumePollActionFailure(nonce: Int) {
        if (_uiState.value.pollActionFailure?.nonce != nonce) return
        _uiState.value = _uiState.value.copy(pollActionFailure = null)
    }

    fun requestJumpToReplyTarget(postNumber: Int) {
        val target = TopicReplyTargetResolver.replyTargetsByPostNumber(_uiState.value.posts)[postNumber]
        if (target == null) {
            if (!pendingJumpPostNumbers.add(postNumber)) return
            viewModelScope.launch {
                val loadedPost = repository.loadPostByNumber(topicId = topicId, postNumber = postNumber).getOrNull()
                pendingJumpPostNumbers.remove(postNumber)
                if (loadedPost != null) {
                    remainingPostIds = remainingPostIds.filterNot { id -> id == loadedPost.id }
                    _uiState.value = _uiState.value.copy(
                        posts = mergePostsInStreamOrder(_uiState.value.posts, listOf(loadedPost)),
                    )
                    requestJumpToReplyTarget(postNumber)
                }
            }
            return
        }
        jumpNonce += 1
        _uiState.value = _uiState.value.copy(
            jumpEvent = TopicJumpEvent(
                itemIndex = target.itemIndex,
                postId = target.postId,
                nonce = jumpNonce,
            ),
            jumpHighlight = TopicJumpHighlight(
                postId = target.postId,
                nonce = jumpNonce,
            ),
        )
    }

    fun requestJumpToPostId(postId: Long) {
        val loadedIndex = _uiState.value.posts.indexOfFirst { post -> post.id == postId }
        if (loadedIndex >= 0) {
            emitJumpToPost(postId, loadedIndex)
            return
        }
        if (!pendingJumpPostIds.add(postId)) return
        viewModelScope.launch {
            val loadedPost = repository.loadPosts(topicId = topicId, postIds = listOf(postId))
                .getOrNull()
                ?.firstOrNull { post -> post.id == postId }
            pendingJumpPostIds.remove(postId)
            if (loadedPost != null) {
                remainingPostIds = remainingPostIds.filterNot { id -> id == loadedPost.id }
                _uiState.value = _uiState.value.copy(
                    posts = mergePostsInStreamOrder(_uiState.value.posts, listOf(loadedPost)),
                    hasMore = remainingPostIds.isNotEmpty(),
                )
                requestJumpToPostId(postId)
            }
        }
    }

    fun requestJumpToLatest() {
        val targetPostId = streamPostIds.lastOrNull() ?: return
        val loadedIndex = _uiState.value.posts.indexOfFirst { it.id == targetPostId }
        if (loadedIndex >= 0) {
            emitJumpToPost(targetPostId, loadedIndex)
            return
        }
        if (_uiState.value.isJumpingToLatest) return
        val requestGeneration = ++jumpToLatestGeneration
        val tailIds = streamPostIds.takeLast(TOPIC_JUMP_TO_LATEST_PAGE_SIZE)
        _uiState.value = _uiState.value.copy(
            isJumpingToLatest = true,
            jumpToLatestErrorMessage = null,
        )
        viewModelScope.launch {
            repository.loadPosts(topicId, tailIds).fold(
                onSuccess = { posts ->
                    if (requestGeneration != jumpToLatestGeneration) return@fold
                    remainingPostIds = remainingPostIds.filterNot(posts.map(Post::id).toSet()::contains)
                    val merged = mergePostsInStreamOrder(_uiState.value.posts, posts)
                    val targetIndex = merged.indexOfFirst { it.id == targetPostId }
                    _uiState.value = _uiState.value.copy(
                        posts = merged,
                        isJumpingToLatest = false,
                        jumpToLatestErrorMessage = null,
                        hasMore = remainingPostIds.isNotEmpty(),
                    )
                    if (targetIndex >= 0) {
                        emitJumpToPost(targetPostId, targetIndex)
                    }
                },
                onFailure = { error ->
                    if (requestGeneration != jumpToLatestGeneration) return@fold
                    _uiState.value = _uiState.value.copy(
                        isJumpingToLatest = false,
                        jumpToLatestErrorMessage = error.readableMessage(),
                    )
                },
            )
        }
    }

    fun consumeJumpToLatestError() {
        if (_uiState.value.jumpToLatestErrorMessage == null) return
        _uiState.value = _uiState.value.copy(jumpToLatestErrorMessage = null)
    }

    fun consumeJumpEvent(nonce: Int) {
        if (_uiState.value.jumpEvent?.nonce != nonce) return
        _uiState.value = _uiState.value.copy(jumpEvent = null)
    }

    fun clearJumpHighlight(nonce: Int) {
        if (_uiState.value.jumpHighlight?.nonce != nonce) return
        _uiState.value = _uiState.value.copy(jumpHighlight = null)
    }

    private fun replacePostById(post: Post) {
        _uiState.value = _uiState.value.copy(
            posts = _uiState.value.posts.map { current ->
                if (current.id == post.id) post else current
            },
        )
    }

    private fun emitJumpToPost(postId: Long, postsIndex: Int) {
        jumpNonce += 1
        _uiState.value = _uiState.value.copy(
            jumpEvent = TopicJumpEvent(
                itemIndex = postsIndex + 1,
                postId = postId,
                nonce = jumpNonce,
            ),
            jumpHighlight = TopicJumpHighlight(postId = postId, nonce = jumpNonce),
        )
    }

    private fun mergePostsInStreamOrder(current: List<Post>, incoming: List<Post>): List<Post> {
        val postsById = LinkedHashMap<Long, Post>()
        current.forEach { postsById[it.id] = it }
        incoming.forEach { postsById[it.id] = it }
        val order = streamPostIds.withIndex().associate { it.value to it.index }
        return postsById.values.sortedWith(
            compareBy<Post> { order[it.id] ?: Int.MAX_VALUE }
                .thenBy { it.postNumber ?: Int.MAX_VALUE }
                .thenBy(Post::id),
        )
    }

    private var pollActionFailureNonce = 0

    private fun emitPollActionFailure(postId: Long) {
        pollActionFailureNonce += 1
        _uiState.value = _uiState.value.copy(
            pollActionFailure = PollActionFailure(postId = postId, nonce = pollActionFailureNonce),
        )
    }

    private fun mergePollVoterGroups(
        options: List<PollOptionRef>,
        apiGroups: List<PostReactionUsersGroup>,
    ): List<PostReactionUsersGroup> {
        val ordered = options.mapNotNull { option ->
            if (option.id.isBlank()) return@mapNotNull null
            val apiGroup = apiGroups.firstOrNull { group -> group.id == option.id }
            PostReactionUsersGroup(
                id = option.id,
                count = option.votes,
                users = apiGroup?.users.orEmpty(),
            )
        }
        val merged = ordered.ifEmpty { apiGroups }
        return if (merged.all { group -> group.users.isEmpty() } && apiGroups.any { group -> group.users.isNotEmpty() }) {
            apiGroups
        } else {
            merged
        }
    }
}
