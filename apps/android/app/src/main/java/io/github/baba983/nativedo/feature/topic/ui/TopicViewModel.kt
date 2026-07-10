package io.github.baba983.nativedo.feature.topic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.PostReaction
import io.github.baba983.nativedo.core.model.toggledReaction
import io.github.baba983.nativedo.feature.home.ui.readableMessage
import io.github.baba983.nativedo.core.model.PostReactionUsersGroup
import io.github.baba983.nativedo.feature.topic.data.TopicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TOPIC_POST_PAGE_SIZE = 20

class TopicViewModel(
    private val repository: TopicRepository,
    private val topicId: Long,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TopicUiState())
    val uiState: StateFlow<TopicUiState> = _uiState.asStateFlow()

    private var remainingPostIds: List<Long> = emptyList()
    private var jumpNonce: Int = 0

    init {
        refresh()
    }

    fun refresh() {
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
                    val loadedIds = detail.posts.map(Post::id).toSet()
                    remainingPostIds = detail.streamPostIds.filterNot(loadedIds::contains)
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
                        isLoading = false,
                        isRefreshing = false,
                        hasMore = remainingPostIds.isNotEmpty(),
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
                        posts = (_uiState.value.posts + posts).distinctBy(Post::id),
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
    ) {
        val selectedReactionId = initialReactionId
            ?: reactions.firstOrNull { reaction -> reaction.reacted }?.id
            ?: reactions.firstOrNull()?.id
        _uiState.value = _uiState.value.copy(
            reactionSheet = TopicReactionSheetUiState(
                postId = postId,
                reactions = reactions,
                initialReactionId = initialReactionId,
            ),
            reactionUsers = TopicReactionUsersUiState(
                selectedReactionId = selectedReactionId,
                isLoading = true,
                hasLoaded = false,
            ),
        )

        viewModelScope.launch {
            repository.loadReactionUsers(postId = postId).fold(
                onSuccess = { groups ->
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
                onFailure = {
                    _uiState.value = _uiState.value.copy(
                        reactionUsers = _uiState.value.reactionUsers.copy(
                            groups = emptyList(),
                            isLoading = false,
                            hasLoaded = true,
                        ),
                    )
                },
            )
        }
    }

    fun selectReactionInSheet(reactionId: String) {
        _uiState.value = _uiState.value.copy(
            reactionUsers = _uiState.value.reactionUsers.copy(selectedReactionId = reactionId),
        )
    }

    fun dismissReactionSheet() {
        _uiState.value = _uiState.value.copy(
            reactionSheet = null,
            reactionUsers = TopicReactionUsersUiState(),
        )
    }

    fun openPollVotersSheet(postId: Long, args: PollVotersSheetArgs) {
        val preferredOptionId = args.initialSelectedOptionId?.takeIf { it.isNotBlank() }
            ?: args.options.firstOrNull { option -> option.votes > 0 }?.id
            ?: args.options.firstOrNull()?.id
        _uiState.value = _uiState.value.copy(
            pollVotersSheet = TopicPollVotersSheetUiState(
                postId = postId,
                pollName = args.pollName,
                pollTitle = args.pollTitle,
                options = args.options,
            ),
            pollVoters = TopicPollVotersUiState(
                selectedOptionId = preferredOptionId,
                isLoading = true,
                hasLoaded = false,
                errorMessage = null,
            ),
        )

        viewModelScope.launch {
            repository.loadPollVoters(postId = postId, pollName = args.pollName).fold(
                onSuccess = { groups ->
                    val mergedGroups = mergePollVoterGroups(args.options, groups)
                    val resolvedSelected = preferredOptionId?.takeIf { id ->
                        mergedGroups.any { group -> group.id == id && group.users.isNotEmpty() }
                    } ?: mergedGroups.firstOrNull { group -> group.users.isNotEmpty() }?.id
                        ?: preferredOptionId?.takeIf { id -> mergedGroups.any { group -> group.id == id } }
                        ?: mergedGroups.firstOrNull()?.id
                    _uiState.value = _uiState.value.copy(
                        pollVoters = TopicPollVotersUiState(
                            groups = mergedGroups,
                            selectedOptionId = resolvedSelected,
                            isLoading = false,
                            hasLoaded = true,
                            errorMessage = null,
                        ),
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        pollVoters = TopicPollVotersUiState(
                            groups = emptyList(),
                            selectedOptionId = preferredOptionId,
                            isLoading = false,
                            hasLoaded = true,
                            errorMessage = error.message?.takeIf { it.isNotBlank() } ?: "加载投票用户失败",
                        ),
                    )
                },
            )
        }
    }

    fun selectPollOptionInSheet(optionId: String) {
        _uiState.value = _uiState.value.copy(
            pollVoters = _uiState.value.pollVoters.copy(selectedOptionId = optionId),
        )
    }

    fun dismissPollVotersSheet() {
        _uiState.value = _uiState.value.copy(
            pollVotersSheet = null,
            pollVoters = TopicPollVotersUiState(),
        )
    }

    suspend fun expandQuote(sourceTopicId: Long, sourcePostNumber: Int): Result<String> {
        return repository.loadPostByNumber(topicId = sourceTopicId, postNumber = sourcePostNumber)
            .map { post -> post.cookedHtml }
    }

    fun submitReply(raw: String, replyToPostNumber: Int?) {
        val body = raw.trim()
        if (body.isEmpty() || _uiState.value.isSubmittingReply) {
            return
        }
        _uiState.value = _uiState.value.copy(
            isSubmittingReply = true,
            replySubmitErrorMessage = null,
        )
        viewModelScope.launch {
            repository.createReply(topicId = topicId, raw = body, replyToPostNumber = replyToPostNumber).fold(
                onSuccess = { createdPost ->
                    remainingPostIds = remainingPostIds.filterNot { postId -> postId == createdPost.id }
                    val current = _uiState.value
                    val alreadyLoaded = current.posts.any { post -> post.id == createdPost.id }
                    val nextPosts = (current.posts.filterNot { post -> post.id == createdPost.id } + createdPost)
                        .sortedWith(compareBy<Post> { post -> post.postNumber ?: Int.MAX_VALUE }.thenBy { post -> post.id })
                    _uiState.value = current.copy(
                        posts = nextPosts,
                        totalPosts = if (alreadyLoaded) current.totalPosts else maxOf(current.totalPosts + 1, nextPosts.size),
                        isSubmittingReply = false,
                        replySubmitErrorMessage = null,
                    )
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
        val target = TopicReplyTargetResolver.replyTargetsByPostNumber(_uiState.value.posts)[postNumber] ?: return
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
                count = apiGroup?.count?.takeIf { it > 0 } ?: option.votes,
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
