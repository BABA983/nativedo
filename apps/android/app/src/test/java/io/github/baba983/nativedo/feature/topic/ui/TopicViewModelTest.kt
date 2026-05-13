package io.github.baba983.nativedo.feature.topic.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.TopicDetail
import io.github.baba983.nativedo.feature.topic.data.TopicRepository
import io.github.baba983.nativedo.test.MainDispatcherRule
import kotlinx.coroutines.flow.first
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

    private suspend fun TopicViewModel.awaitState(
        predicate: (TopicUiState) -> Boolean,
    ): TopicUiState = withTimeout(2_000L) {
        uiState.first(predicate)
    }

    private class FakeTopicRepository(
        private val detail: Result<TopicDetail>,
        private val posts: Result<List<Post>> = Result.success(emptyList()),
    ) : TopicRepository() {
        val loadRequests = mutableListOf<Long>()
        val loadPostRequests = mutableListOf<Pair<Long, List<Long>>>()

        override suspend fun load(topicId: Long): Result<TopicDetail> {
            loadRequests += topicId
            return detail
        }

        override suspend fun loadPosts(topicId: Long, postIds: List<Long>): Result<List<Post>> {
            loadPostRequests += topicId to postIds
            return posts
        }
    }

    private fun topicDetail(
        loadedPostIds: List<Long>,
        streamPostIds: List<Long>,
    ): Result<TopicDetail> = Result.success(
        TopicDetail(
            id = 101L,
            title = "Welcome to NativeDo Android",
            posts = loadedPostIds.map(::post),
            totalPosts = streamPostIds.size,
            streamPostIds = streamPostIds,
        ),
    )

    private fun post(id: Long): Post = Post(
        id = id,
        authorUsername = "baba",
        cookedHtml = "<p>Post $id</p>",
    )
}
