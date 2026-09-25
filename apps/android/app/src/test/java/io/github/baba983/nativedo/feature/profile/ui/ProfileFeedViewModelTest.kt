package io.github.baba983.nativedo.feature.profile.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.core.model.UserActionPage
import io.github.baba983.nativedo.feature.profile.data.ProfileFeedRepository
import io.github.baba983.nativedo.test.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test

class ProfileFeedViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun filteredRowsDoNotEndPaginationWhenServerReturnedFullPage() = runTest {
        val repository = FakeProfileFeedRepository(
            0 to page(actions(1..19), rawCount = 20),
        )
        val viewModel = ProfileFeedViewModel("baba", ProfileFeedType.Replies, repository)

        val state = viewModel.awaitState { !it.isLoading }

        assertThat(state.items).hasSize(19)
        assertThat(state.hasMore).isTrue()
    }

    @Test
    fun serverPageLargerThanRequestedUsesRawCountAsTheNextOffset() = runTest {
        val repository = FakeProfileFeedRepository(
            0 to page(actions(1..30), rawCount = 30),
            30 to page(listOf(action(31, null))),
        )
        val viewModel = ProfileFeedViewModel("baba", ProfileFeedType.Replies, repository)

        val initial = viewModel.awaitState { !it.isLoading }
        assertThat(initial.hasMore).isTrue()
        assertThat(initial.nextOffset).isEqualTo(30)

        viewModel.loadNextPage()
        val loaded = viewModel.awaitState { it.items.size == 31 }

        assertThat(loaded.nextOffset).isEqualTo(31)
        assertThat(repository.requests).containsExactly(
            Request("baba", "5", 0),
            Request("baba", "5", 30),
        ).inOrder()
    }

    @Test
    fun missingPostNumbersKeepDistinctActivityRows() = runTest {
        val repository = FakeProfileFeedRepository(
            0 to page(
                listOf(
                    action(topicId = 101, postNumber = null, rowIdentity = "post:1001", targetPostId = 1001),
                    action(topicId = 101, postNumber = null, rowIdentity = "post:1002", targetPostId = 1002),
                ),
            ),
        )
        val viewModel = ProfileFeedViewModel("baba", ProfileFeedType.Replies, repository)

        val state = viewModel.awaitState { !it.isLoading }

        assertThat(state.items.map { it.rowIdentity }).containsExactly("post:1001", "post:1002").inOrder()
        assertThat(state.items.map { it.targetPostId }).containsExactly(1001L, 1002L).inOrder()
    }

    @Test
    fun retryCancelsOldLoadMoreAndIgnoresItsLateResult() = runTest {
        val loadMoreResult = CompletableDeferred<Result<UserActionPage>>()
        val retryResult = CompletableDeferred<Result<UserActionPage>>()
        var initialRequests = 0
        val repository = object : ProfileFeedRepository() {
            override suspend fun load(username: String, filter: String, offset: Int): Result<UserActionPage> {
                return when (offset) {
                    0 -> if (initialRequests++ == 0) page(actions(1..20)) else retryResult.await()
                    else -> withContext(NonCancellable) { loadMoreResult.await() }
                }
            }
        }
        val viewModel = ProfileFeedViewModel("baba", ProfileFeedType.Replies, repository)
        viewModel.awaitState { !it.isLoading }

        viewModel.loadNextPage()
        runCurrent()
        viewModel.retry()
        runCurrent()
        retryResult.complete(page(listOf(action(900, 9, "action:new"))))
        viewModel.awaitState { !it.isLoading && it.items.singleOrNull()?.id == 900L }
        loadMoreResult.complete(page(listOf(action(800, 8, "action:old"))))
        runCurrent()

        assertThat(viewModel.uiState.value.items.map { it.id }).containsExactly(900L)
    }

    @Test
    fun repliesKeepSeparateFloorsAndLoadTheNextOffset() = runTest {
        val firstPage = actions(1..20).toMutableList().apply {
            this[0] = action(topicId = 101, postNumber = 3)
        }
        val repository = FakeProfileFeedRepository(
            0 to page(firstPage),
            20 to page(
                listOf(
                    action(topicId = 101, postNumber = 3),
                    action(topicId = 101, postNumber = 8),
                ),
            ),
        )
        val viewModel = ProfileFeedViewModel(
            username = "baba",
            feedType = ProfileFeedType.Replies,
            repository = repository,
        )
        viewModel.awaitState { !it.isLoading }

        viewModel.loadNextPage()
        val state = viewModel.awaitState { !it.isLoadingMore && it.nextOffset == 22 }

        assertThat(state.items.filter { it.id == 101L }.map { it.targetPostNumber })
            .containsExactly(3, 8)
            .inOrder()
        assertThat(state.hasMore).isFalse()
        assertThat(repository.requests).containsExactly(
            Request("baba", "5", 0),
            Request("baba", "5", 20),
        ).inOrder()
    }

    @Test
    fun initialFailureCanRetryIntoAnEmptyState() = runTest {
        val repository = FakeProfileFeedRepository(
            0 to Result.failure(IllegalStateException("offline")),
        )
        val viewModel = ProfileFeedViewModel(
            username = "baba",
            feedType = ProfileFeedType.Replies,
            repository = repository,
        )

        val failed = viewModel.awaitState { it.errorMessage != null }
        assertThat(failed.isLoading).isFalse()
        assertThat(failed.isEmpty).isFalse()

        repository.responses[0] = page(emptyList())
        viewModel.retry()
        val empty = viewModel.awaitState { !it.isLoading && it.errorMessage == null }

        assertThat(empty.isEmpty).isTrue()
        assertThat(empty.hasMore).isFalse()
        assertThat(repository.requests).containsExactly(
            Request("baba", "5", 0),
            Request("baba", "5", 0),
        ).inOrder()
    }

    @Test
    fun loadMoreFailureKeepsRowsAndCanRetry() = runTest {
        val repository = FakeProfileFeedRepository(
            0 to page(actions(1..20)),
            20 to Result.failure(IllegalStateException("network down")),
        )
        val viewModel = ProfileFeedViewModel(
            username = "baba",
            feedType = ProfileFeedType.Topics,
            repository = repository,
        )
        viewModel.awaitState { !it.isLoading }

        viewModel.loadNextPage()
        val failed = viewModel.awaitState { it.loadMoreErrorMessage != null }
        assertThat(failed.items).hasSize(20)

        repository.responses[20] = page(listOf(action(21, null)))
        viewModel.retryLoadMore()
        val recovered = viewModel.awaitState { it.items.size == 21 }

        assertThat(recovered.loadMoreErrorMessage).isNull()
        assertThat(recovered.items.last().id).isEqualTo(21L)
        assertThat(repository.requests).containsExactly(
            Request("baba", "4", 0),
            Request("baba", "4", 20),
            Request("baba", "4", 20),
        ).inOrder()
    }

    private suspend fun ProfileFeedViewModel.awaitState(
        predicate: (ProfileFeedUiState) -> Boolean,
    ): ProfileFeedUiState = withTimeout(2_000L) {
        uiState.first(predicate)
    }

    private class FakeProfileFeedRepository(
        vararg responses: Pair<Int, Result<UserActionPage>>,
    ) : ProfileFeedRepository() {
        val responses = responses.toMap().toMutableMap()
        val requests = mutableListOf<Request>()

        override suspend fun load(
            username: String,
            filter: String,
            offset: Int,
        ): Result<UserActionPage> {
            requests += Request(username, filter, offset)
            return responses.getValue(offset)
        }
    }

    private fun actions(range: IntRange): List<TopicSummary> =
        range.map { id -> action(topicId = id, postNumber = null) }

    private fun action(
        topicId: Int,
        postNumber: Int?,
        rowIdentity: String? = null,
        targetPostId: Int? = null,
    ): TopicSummary =
        TopicSummary(
            id = topicId.toLong(),
            title = "Topic $topicId",
            authorUsername = "baba",
            targetPostNumber = postNumber,
            targetPostId = targetPostId?.toLong(),
            rowIdentity = rowIdentity,
        )

    private fun page(items: List<TopicSummary>, rawCount: Int = items.size): Result<UserActionPage> =
        Result.success(UserActionPage(items = items, rawCount = rawCount))
}

private data class Request(
    val username: String,
    val filter: String,
    val offset: Int,
)
