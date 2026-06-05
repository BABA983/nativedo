package io.github.baba983.nativedo.feature.topic.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.PostReaction
import io.github.baba983.nativedo.core.model.PostReactionUser
import io.github.baba983.nativedo.core.model.PostReactionUsersGroup
import io.github.baba983.nativedo.core.model.TopicDetail
import io.github.baba983.nativedo.feature.topic.data.TopicRepository
import io.github.baba983.nativedo.test.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test

class TopicViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: TopicViewModel

    @Test
    fun initLoadsTopicAndTracksRemainingPosts() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(loadedPostIds = listOf(1001L), streamPostIds = listOf(1001L, 1002L, 1003L)),
        )

        viewModel = TopicViewModel(repository = repository, topicId = 101L)

        val state = viewModel.awaitState { state -> !state.isLoading && state.posts.isNotEmpty() }
        assertThat(state.title).isEqualTo("Welcome to NativeDo Android")
        assertThat(state.posts.map { it.id }).containsExactly(1001L)
        assertThat(state.totalPosts).isEqualTo(3)
        assertThat(state.hasMore).isTrue()
        assertThat(state.isLoading).isFalse()
        assertThat(repository.loadRequests).containsExactly(101L)
    }

    @Test
    fun loadMoreAppendsRemainingPosts() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(loadedPostIds = listOf(1001L), streamPostIds = listOf(1001L, 1002L, 1003L)),
            posts = Result.success(listOf(post(1002L), post(1003L))),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.isNotEmpty() }

        viewModel.loadMore()

        val state = viewModel.awaitState { state -> !state.isLoadingMore && state.posts.size == 3 }
        assertThat(state.posts.map { it.id }).containsExactly(1001L, 1002L, 1003L).inOrder()
        assertThat(state.isLoadingMore).isFalse()
        assertThat(state.hasMore).isFalse()
        assertThat(repository.loadPostRequests).containsExactly(101L to listOf(1002L, 1003L))
    }

    @Test
    fun loadMoreFailureKeepsExistingPosts() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(loadedPostIds = listOf(1001L), streamPostIds = listOf(1001L, 1002L, 1003L)),
            posts = Result.failure(IllegalStateException("server error")),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.isNotEmpty() }

        viewModel.loadMore()

        val state = viewModel.awaitState { state -> state.loadMoreErrorMessage != null }
        assertThat(state.posts.map { it.id }).containsExactly(1001L)
        assertThat(state.isLoadingMore).isFalse()
        assertThat(state.loadMoreErrorMessage).isNotEmpty()
        assertThat(state.hasMore).isTrue()
    }

    @Test
    fun replacePostUpdatesExistingPostInPlace() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(loadedPostIds = listOf(1001L, 1002L), streamPostIds = listOf(1001L, 1002L)),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.size == 2 }

        viewModel.replacePost(post(1002L).copy(currentUserReaction = "heart"))

        val state = viewModel.awaitState { state ->
            state.posts.any { post -> post.id == 1002L && post.currentUserReaction == "heart" }
        }
        assertThat(state.posts.map { it.id }).containsExactly(1001L, 1002L).inOrder()
        assertThat(state.posts.single { it.id == 1002L }.currentUserReaction).isEqualTo("heart")
    }

    @Test
    fun onReactionSelectedAppliesOptimisticReactionAndCallsRepository() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(loadedPostIds = listOf(1001L), streamPostIds = listOf(1001L)),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.isNotEmpty() }

        viewModel.onReactionSelected(postId = 1001L, reactionId = "heart")
        val state = viewModel.awaitState { state -> state.posts.single().currentUserReaction == "heart" }
        runCurrent()

        val post = state.posts.single()
        assertThat(post.reactionUsersCount).isEqualTo(1)
        assertThat(post.reactions).contains(PostReaction(id = "heart", count = 1, iconUrl = "heart.png", reacted = true))
        assertThat(repository.toggleReactionRequests).containsExactly(1001L to "heart")
    }

    @Test
    fun onReactionSelectedRollsBackOnFailure() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(loadedPostIds = listOf(1001L), streamPostIds = listOf(1001L)),
            toggleReactionResult = Result.failure(IllegalStateException("nope")),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.isNotEmpty() }

        viewModel.onReactionSelected(postId = 1001L, reactionId = "heart")
        runCurrent()

        val state = viewModel.uiState.value
        assertThat(state.posts.single().currentUserReaction).isNull()
        assertThat(state.posts.single().reactionUsersCount).isEqualTo(0)
        assertThat(state.posts.single().reactions).isEmpty()
    }

    @Test
    fun openReactionSheetLoadsUsersAndSelectsInitialReaction() = runTest {
        val groups = listOf(
            PostReactionUsersGroup(
                id = "heart",
                count = 1,
                users = listOf(PostReactionUser(username = "baba")),
            ),
        )
        val repository = FakeTopicRepository(
            detail = topicDetail(loadedPostIds = listOf(1001L), streamPostIds = listOf(1001L)),
            reactionUsers = Result.success(groups),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.isNotEmpty() }

        viewModel.openReactionSheet(
            postId = 1001L,
            reactions = listOf(PostReaction(id = "heart", count = 1)),
            initialReactionId = "heart",
        )
        runCurrent()

        val state = viewModel.uiState.value
        assertThat(state.reactionSheet?.postId).isEqualTo(1001L)
        assertThat(state.reactionUsers.groups).isEqualTo(groups)
        assertThat(state.reactionUsers.selectedReactionId).isEqualTo("heart")
        assertThat(state.reactionUsers.isLoading).isFalse()
        assertThat(state.reactionUsers.hasLoaded).isTrue()
        assertThat(repository.reactionUsersRequests).containsExactly(1001L)
    }

    @Test
    fun dismissReactionSheetClearsSheetState() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(loadedPostIds = listOf(1001L), streamPostIds = listOf(1001L)),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.isNotEmpty() }
        viewModel.openReactionSheet(
            postId = 1001L,
            reactions = listOf(PostReaction(id = "heart", count = 1)),
            initialReactionId = null,
        )
        runCurrent()

        viewModel.dismissReactionSheet()

        val state = viewModel.uiState.value
        assertThat(state.reactionSheet).isNull()
        assertThat(state.reactionUsers).isEqualTo(TopicReactionUsersUiState())
    }

    @Test
    fun expandQuoteReturnsCookedHtmlFromPostByNumber() = runTest {
        val quotePost = post(id = 2001L, postNumber = 9).copy(cookedHtml = "<p>quoted</p>")
        val repository = FakeTopicRepository(
            detail = topicDetail(loadedPostIds = listOf(1001L), streamPostIds = listOf(1001L)),
            quotePost = Result.success(quotePost),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.isNotEmpty() }

        val result = viewModel.expandQuote(sourceTopicId = 101L, sourcePostNumber = 9)

        assertThat(result.getOrThrow()).isEqualTo("<p>quoted</p>")
        assertThat(repository.loadPostByNumberRequests).containsExactly(101L to 9)
    }

    @Test
    fun requestJumpToReplyTargetEmitsEventAndHighlightWithNonce() = runTest {
        val repository = FakeTopicRepository(
            detail = Result.success(
                TopicDetail(
                    id = 101L,
                    title = "Welcome to NativeDo Android",
                    posts = listOf(
                        post(id = 1001L, postNumber = 1),
                        post(id = 1002L, postNumber = 2),
                    ),
                    totalPosts = 2,
                    streamPostIds = listOf(1001L, 1002L),
                ),
            ),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.size == 2 }

        viewModel.requestJumpToReplyTarget(postNumber = 2)
        val firstEvent = viewModel.uiState.value.jumpEvent
        viewModel.consumeJumpEvent(firstEvent!!.nonce)
        viewModel.clearJumpHighlight(firstEvent.nonce)
        viewModel.requestJumpToReplyTarget(postNumber = 2)

        val state = viewModel.uiState.value
        assertThat(firstEvent.itemIndex).isEqualTo(2)
        assertThat(firstEvent.postId).isEqualTo(1002L)
        assertThat(state.jumpEvent?.nonce).isGreaterThan(firstEvent.nonce)
        assertThat(state.jumpHighlight?.postId).isEqualTo(1002L)
    }

    private suspend fun TopicViewModel.awaitState(
        predicate: (TopicUiState) -> Boolean,
    ): TopicUiState = withTimeout(2_000L) {
        uiState.first(predicate)
    }

    private class FakeTopicRepository(
        private val detail: Result<TopicDetail>,
        private val posts: Result<List<Post>> = Result.success(emptyList()),
        private val quotePost: Result<Post> = Result.failure(IllegalStateException("quote not configured")),
        private val reactionUsers: Result<List<PostReactionUsersGroup>> = Result.success(emptyList()),
        private val toggleReactionResult: Result<Unit> = Result.success(Unit),
    ) : TopicRepository() {
        val loadRequests = mutableListOf<Long>()
        val loadPostRequests = mutableListOf<Pair<Long, List<Long>>>()
        val loadPostByNumberRequests = mutableListOf<Pair<Long, Int>>()
        val reactionUsersRequests = mutableListOf<Long>()
        val toggleReactionRequests = mutableListOf<Pair<Long, String>>()

        override suspend fun load(topicId: Long): Result<TopicDetail> {
            loadRequests += topicId
            return detail
        }

        override suspend fun loadPosts(topicId: Long, postIds: List<Long>): Result<List<Post>> {
            loadPostRequests += topicId to postIds
            return posts
        }

        override suspend fun loadPostByNumber(topicId: Long, postNumber: Int): Result<Post> {
            loadPostByNumberRequests += topicId to postNumber
            return quotePost
        }

        override suspend fun loadReactionUsers(postId: Long): Result<List<PostReactionUsersGroup>> {
            reactionUsersRequests += postId
            return reactionUsers
        }

        override suspend fun toggleReaction(postId: Long, reactionId: String): Result<Unit> {
            toggleReactionRequests += postId to reactionId
            return toggleReactionResult
        }
    }

    private fun topicDetail(
        loadedPostIds: List<Long>,
        streamPostIds: List<Long>,
    ): Result<TopicDetail> = Result.success(
        TopicDetail(
            id = 101L,
            title = "Welcome to NativeDo Android",
            validReactions = listOf("heart"),
            emojiCatalog = mapOf("heart" to "heart.png"),
            posts = loadedPostIds.mapIndexed { index, id -> post(id = id, postNumber = index + 1) },
            totalPosts = streamPostIds.size,
            streamPostIds = streamPostIds,
        ),
    )

    private fun post(
        id: Long,
        postNumber: Int? = null,
    ): Post = Post(
        id = id,
        postNumber = postNumber,
        authorUsername = "baba",
        cookedHtml = "<p>Post $id</p>",
    )
}
