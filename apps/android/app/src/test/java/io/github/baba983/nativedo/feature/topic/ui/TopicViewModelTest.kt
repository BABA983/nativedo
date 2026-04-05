package io.github.baba983.nativedo.feature.topic.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.TopicDetail
import io.github.baba983.nativedo.core.network.SessionRecoveryRequiredException
import io.github.baba983.nativedo.feature.topic.domain.LoadTopicUseCase
import io.github.baba983.nativedo.test.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TopicViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadTopic_emitsTopicAndPosts() = runTest {
        val detail = TopicDetail(
            id = 42,
            title = "Compose on linux.do",
            posts = listOf(
                Post(
                    id = 1,
                    authorUsername = "baba",
                    cookedHtml = "<p>Hello</p>",
                    imageUrls = emptyList(),
                ),
            ),
        )
        val viewModel = TopicViewModel(
            loadTopic = FakeLoadTopicUseCase(detail),
            topicId = 42,
        )

        viewModel.uiState.test {
            assertThat(awaitItem().isLoading).isTrue()
            runCurrent()
            assertThat(awaitItem().posts).hasSize(1)
        }
    }

    @Test
    fun loadTopic_exposesTopicMetadataInUiState() = runTest {
        val detail = TopicDetail(
            id = 42,
            title = "Compose on linux.do",
            createdAt = "2026-04-01T02:30:00.000Z",
            lastPostedAt = "2026-04-05T09:07:00.000Z",
            viewCount = 1284,
            tags = listOf("android", "compose"),
            posts = listOf(
                Post(
                    id = 1,
                    authorUsername = "baba",
                    cookedHtml = "<p>Hello</p>",
                    imageUrls = emptyList(),
                ),
            ),
        )
        val viewModel = TopicViewModel(
            loadTopic = FakeLoadTopicUseCase(detail),
            topicId = 42,
        )

        runCurrent()

        assertThat(viewModel.uiState.value.createdAt).isEqualTo("2026-04-01T02:30:00.000Z")
        assertThat(viewModel.uiState.value.lastPostedAt).isEqualTo("2026-04-05T09:07:00.000Z")
        assertThat(viewModel.uiState.value.viewCount).isEqualTo(1284)
        assertThat(viewModel.uiState.value.tags).containsExactly("android", "compose").inOrder()
    }

    @Test
    fun refresh_resetsLoadedPostsAndRemainingStream() = runTest {
        val useCase = FakeLoadTopicUseCase(
            initial = TopicDetail(
                id = 42,
                title = "Compose on linux.do",
                posts = listOf(
                    Post(
                        id = 1,
                        authorUsername = "baba",
                        cookedHtml = "<p>Hello</p>",
                        imageUrls = emptyList(),
                    ),
                ),
                totalPosts = 3,
                streamPostIds = listOf(1L, 2L, 3L),
            ),
            chunks = mapOf(
                listOf(2L, 3L) to listOf(
                    Post(
                        id = 2,
                        authorUsername = "alice",
                        cookedHtml = "<p>Second</p>",
                        imageUrls = emptyList(),
                    ),
                    Post(
                        id = 3,
                        authorUsername = "charlie",
                        cookedHtml = "<p>Third</p>",
                        imageUrls = emptyList(),
                    ),
                ),
            ),
        )
        val viewModel = TopicViewModel(loadTopic = useCase, topicId = 42)

        runCurrent()
        viewModel.loadMore()
        runCurrent()
        viewModel.refresh()
        runCurrent()

        assertThat(viewModel.uiState.value.posts.map { it.id }).containsExactly(1L)
        assertThat(viewModel.uiState.value.hasMore).isTrue()
    }

    @Test
    fun loadMore_appendsChunkAndStopsWhenStreamExhausted() = runTest {
        val useCase = FakeLoadTopicUseCase(
            initial = TopicDetail(
                id = 42,
                title = "Compose on linux.do",
                posts = listOf(
                    Post(
                        id = 1,
                        authorUsername = "baba",
                        cookedHtml = "<p>Hello</p>",
                        imageUrls = emptyList(),
                    ),
                ),
                totalPosts = 3,
                streamPostIds = listOf(1L, 2L, 3L),
            ),
            chunks = mapOf(
                listOf(2L, 3L) to listOf(
                    Post(
                        id = 2,
                        authorUsername = "alice",
                        cookedHtml = "<p>Second</p>",
                        imageUrls = emptyList(),
                    ),
                    Post(
                        id = 3,
                        authorUsername = "charlie",
                        cookedHtml = "<p>Third</p>",
                        imageUrls = emptyList(),
                    ),
                ),
            ),
        )
        val viewModel = TopicViewModel(loadTopic = useCase, topicId = 42)

        runCurrent()
        viewModel.loadMore()
        runCurrent()
        viewModel.loadMore()
        runCurrent()

        assertThat(viewModel.uiState.value.posts.map { it.id }).containsExactly(1L, 2L, 3L).inOrder()
        assertThat(viewModel.uiState.value.hasMore).isFalse()
        assertThat(useCase.chunkRequests).containsExactly(listOf(2L, 3L))
    }

    @Test
    fun loadTopic_sessionRecoveryError_setsAuthRecoveryRequired() = runTest {
        val viewModel = TopicViewModel(
            loadTopic = FailingLoadTopicUseCase(SessionRecoveryRequiredException()),
            topicId = 42,
        )

        runCurrent()

        assertThat(viewModel.uiState.value.isAuthRecoveryRequired).isTrue()
        assertThat(viewModel.uiState.value.errorMessage).isNull()
    }

    @Test
    fun onAuthRecoveryHandled_clearsAuthRecoveryRequired() = runTest {
        val viewModel = TopicViewModel(
            loadTopic = FailingLoadTopicUseCase(SessionRecoveryRequiredException()),
            topicId = 42,
        )

        runCurrent()
        viewModel.onAuthRecoveryHandled()

        assertThat(viewModel.uiState.value.isAuthRecoveryRequired).isFalse()
    }

    @Test
    fun refresh_ignoresTopicResultWhenSessionVersionChanges() = runTest {
        var sessionVersion = 1L
        val useCase = DelayedLoadTopicUseCase()
        val viewModel = TopicViewModel(
            loadTopic = useCase,
            topicId = 42,
            currentSessionVersion = { sessionVersion },
        )

        runCurrent()
        sessionVersion = 2L
        useCase.completeInitial(
            TopicDetail(
                id = 42L,
                title = "Compose on linux.do",
                posts = listOf(
                    Post(
                        id = 1L,
                        authorUsername = "baba",
                        cookedHtml = "<p>Hello</p>",
                        imageUrls = emptyList(),
                    ),
                ),
                totalPosts = 1,
                streamPostIds = listOf(1L),
            ),
        )
        runCurrent()

        assertThat(viewModel.uiState.value.posts).isEmpty()
        assertThat(viewModel.uiState.value.isLoading).isFalse()
        assertThat(viewModel.uiState.value.isRefreshing).isFalse()
        assertThat(viewModel.uiState.value.errorMessage).isNull()
    }

    @Test
    fun loadMore_ignoresPostsWhenSessionVersionChanges() = runTest {
        var sessionVersion = 1L
        val useCase = DelayedLoadTopicUseCase()
        val viewModel = TopicViewModel(
            loadTopic = useCase,
            topicId = 42,
            currentSessionVersion = { sessionVersion },
        )

        runCurrent()
        useCase.completeInitial(
            TopicDetail(
                id = 42L,
                title = "Compose on linux.do",
                posts = listOf(
                    Post(
                        id = 1L,
                        authorUsername = "baba",
                        cookedHtml = "<p>Hello</p>",
                        imageUrls = emptyList(),
                    ),
                ),
                totalPosts = 2,
                streamPostIds = listOf(1L, 2L),
            ),
        )
        runCurrent()

        viewModel.loadMore()
        runCurrent()
        sessionVersion = 2L
        useCase.completeChunk(
            listOf(
                Post(
                    id = 2L,
                    authorUsername = "alice",
                    cookedHtml = "<p>Second</p>",
                    imageUrls = emptyList(),
                ),
            ),
        )
        runCurrent()

        assertThat(viewModel.uiState.value.posts.map { it.id }).containsExactly(1L)
        assertThat(viewModel.uiState.value.isLoadingMore).isFalse()
        assertThat(viewModel.uiState.value.loadMoreErrorMessage).isNull()
    }
}

private class FakeLoadTopicUseCase(
    private val initial: TopicDetail,
    private val chunks: Map<List<Long>, List<Post>> = emptyMap(),
) : LoadTopicUseCase() {
    val chunkRequests = mutableListOf<List<Long>>()

    override suspend fun invoke(topicId: Long): Result<TopicDetail> {
        return Result.success(initial)
    }

    override suspend fun loadPosts(topicId: Long, postIds: List<Long>): Result<List<Post>> {
        chunkRequests += postIds
        return Result.success(
            checkNotNull(chunks[postIds]) {
                "Missing fake chunk for $postIds"
            },
        )
    }
}

private class FailingLoadTopicUseCase(
    private val error: Throwable,
) : LoadTopicUseCase() {
    override suspend fun invoke(topicId: Long): Result<TopicDetail> = Result.failure(error)
}

private class DelayedLoadTopicUseCase : LoadTopicUseCase() {
    private val initialResult = CompletableDeferred<Result<TopicDetail>>()
    private val chunkResult = CompletableDeferred<Result<List<Post>>>()

    override suspend fun invoke(topicId: Long): Result<TopicDetail> = initialResult.await()

    override suspend fun loadPosts(topicId: Long, postIds: List<Long>): Result<List<Post>> = chunkResult.await()

    fun completeInitial(detail: TopicDetail) {
        initialResult.complete(Result.success(detail))
    }

    fun completeChunk(posts: List<Post>) {
        chunkResult.complete(Result.success(posts))
    }
}
