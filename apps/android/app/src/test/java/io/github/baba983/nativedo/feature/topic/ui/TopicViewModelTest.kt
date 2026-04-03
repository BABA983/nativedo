package io.github.baba983.nativedo.feature.topic.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.TopicDetail
import io.github.baba983.nativedo.feature.topic.domain.LoadTopicUseCase
import io.github.baba983.nativedo.test.MainDispatcherRule
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
