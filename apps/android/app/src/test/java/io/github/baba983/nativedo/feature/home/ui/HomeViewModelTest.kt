package io.github.baba983.nativedo.feature.home.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.feature.home.data.HomeRepository
import io.github.baba983.nativedo.feature.home.data.HomeFeedPage
import io.github.baba983.nativedo.test.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: HomeViewModel

    @Test
    fun initLoadsLatestTopicsAndNextPage() = runTest {
        val repository = FakeHomeRepository(
            HomeFeedTab.Latest to 0 to page(
                topicIds = listOf(101L),
                nextPage = 1,
            ),
        )

        viewModel = HomeViewModel(repository)

        val state = viewModel.awaitState { state -> !state.isLoading && state.items.isNotEmpty() }
        assertThat(state.selectedTab).isEqualTo(HomeFeedTab.Latest)
        assertThat(state.isLoading).isFalse()
        assertThat(state.items.map { it.id }).containsExactly(101L)
        assertThat(state.hasMore).isTrue()
        assertThat(state.nextPage).isEqualTo(1)
        assertThat(repository.requests).containsExactly(HomeFeedTab.Latest to 0)
    }

    @Test
    fun selectingNewFeedLoadsNewEndpoint() = runTest {
        val repository = FakeHomeRepository(
            HomeFeedTab.Latest to 0 to page(topicIds = listOf(101L), nextPage = 1),
            HomeFeedTab.New to 0 to page(topicIds = listOf(202L), nextPage = null),
        )
        viewModel = HomeViewModel(repository)
        viewModel.awaitState { state -> !state.isLoading && state.items.isNotEmpty() }

        viewModel.onFeedSelected(HomeFeedTab.New)

        val state = viewModel.awaitState { state ->
            state.selectedTab == HomeFeedTab.New && !state.isLoading && state.items.isNotEmpty()
        }
        assertThat(state.selectedTab).isEqualTo(HomeFeedTab.New)
        assertThat(state.items.map { it.id }).containsExactly(202L)
        assertThat(state.hasMore).isFalse()
        assertThat(repository.requests).containsExactly(
            HomeFeedTab.Latest to 0,
            HomeFeedTab.New to 0,
        ).inOrder()
    }

    @Test
    fun switchingBackToLoadedFeedUsesCachedTabState() = runTest {
        val repository = FakeHomeRepository(
            HomeFeedTab.Latest to 0 to page(topicIds = listOf(101L), nextPage = 1),
            HomeFeedTab.New to 0 to page(topicIds = listOf(202L), nextPage = null),
        )
        viewModel = HomeViewModel(repository)
        viewModel.awaitState { state -> !state.isLoading && state.items.isNotEmpty() }

        viewModel.onFeedSelected(HomeFeedTab.New)
        viewModel.awaitState { state ->
            state.selectedTab == HomeFeedTab.New && !state.isLoading && state.items.map { it.id } == listOf(202L)
        }

        viewModel.onFeedSelected(HomeFeedTab.Latest)

        val state = viewModel.awaitState { state -> state.selectedTab == HomeFeedTab.Latest }
        assertThat(state.items.map { it.id }).containsExactly(101L)
        assertThat(state.feedState(HomeFeedTab.New).items.map { it.id }).containsExactly(202L)
        assertThat(repository.requests).containsExactly(
            HomeFeedTab.Latest to 0,
            HomeFeedTab.New to 0,
        ).inOrder()
    }

    @Test
    fun loadNextPageAppendsDistinctTopics() = runTest {
        val repository = FakeHomeRepository(
            HomeFeedTab.Latest to 0 to page(topicIds = listOf(101L), nextPage = 1),
            HomeFeedTab.Latest to 1 to page(topicIds = listOf(303L), nextPage = null),
        )
        viewModel = HomeViewModel(repository)
        viewModel.awaitState { state -> !state.isLoading && state.items.isNotEmpty() }

        viewModel.loadNextPage()

        val state = viewModel.awaitState { state -> !state.isLoadingMore && state.items.size == 2 }
        assertThat(state.items.map { it.id }).containsExactly(101L, 303L).inOrder()
        assertThat(state.isLoadingMore).isFalse()
        assertThat(state.hasMore).isFalse()
        assertThat(repository.requests).containsExactly(
            HomeFeedTab.Latest to 0,
            HomeFeedTab.Latest to 1,
        ).inOrder()
    }

    @Test
    fun loadNextPageFailureKeepsExistingItems() = runTest {
        val repository = FakeHomeRepository(
            HomeFeedTab.Latest to 0 to page(topicIds = listOf(101L), nextPage = 1),
            HomeFeedTab.Latest to 1 to Result.failure(IllegalStateException("server error")),
        )
        viewModel = HomeViewModel(repository)
        viewModel.awaitState { state -> !state.isLoading && state.items.isNotEmpty() }

        viewModel.loadNextPage()

        val state = viewModel.awaitState { state -> state.loadMoreErrorMessage != null }
        assertThat(state.items.map { it.id }).containsExactly(101L)
        assertThat(state.isLoadingMore).isFalse()
        assertThat(state.loadMoreErrorMessage).isNotEmpty()
    }

    @Test
    fun refreshSupersedesInFlightPaginationAndIgnoresItsLateResponse() = runTest {
        val pageResponse = CompletableDeferred<Result<HomeFeedPage>>()
        val refreshResponse = CompletableDeferred<Result<HomeFeedPage>>()
        val repository = SequencedHomeRepository(
            page(topicIds = listOf(101L), nextPage = 1),
            pageResponse,
            refreshResponse,
        )
        viewModel = HomeViewModel(repository)
        viewModel.awaitState { state -> !state.isLoading && state.items.isNotEmpty() }

        viewModel.loadNextPage()
        assertThat(viewModel.uiState.value.isLoadingMore).isTrue()
        viewModel.onRefresh()

        assertThat(viewModel.uiState.value.isLoadingMore).isFalse()
        assertThat(viewModel.uiState.value.isRefreshing).isTrue()
        pageResponse.complete(page(topicIds = listOf(202L), nextPage = null))
        refreshResponse.complete(page(topicIds = listOf(303L), nextPage = null))

        val state = viewModel.awaitState { state -> !state.isRefreshing }
        assertThat(state.items.map { it.id }).containsExactly(303L)
        assertThat(repository.requests).containsExactly(
            HomeFeedTab.Latest to 0,
            HomeFeedTab.Latest to 1,
            HomeFeedTab.Latest to 0,
        ).inOrder()
    }

    @Test
    fun duplicatePaginationRequestIsBlockedBeforeTheFirstCoroutineReturns() = runTest {
        val pageResponse = CompletableDeferred<Result<HomeFeedPage>>()
        val repository = SequencedHomeRepository(
            page(topicIds = listOf(101L), nextPage = 1),
            pageResponse,
        )
        viewModel = HomeViewModel(repository)
        viewModel.awaitState { state -> !state.isLoading && state.items.isNotEmpty() }

        viewModel.loadNextPage()
        viewModel.loadNextPage()

        assertThat(repository.requests).containsExactly(
            HomeFeedTab.Latest to 0,
            HomeFeedTab.Latest to 1,
        ).inOrder()
        pageResponse.complete(page(topicIds = listOf(202L), nextPage = null))
        val state = viewModel.awaitState { state -> !state.isLoadingMore }
        assertThat(state.items.map { it.id }).containsExactly(101L, 202L).inOrder()
    }

    private suspend fun HomeViewModel.awaitState(
        predicate: (HomeUiState) -> Boolean,
    ): HomeUiState = withTimeout(2_000L) {
        uiState.first(predicate)
    }

    private class FakeHomeRepository(
        vararg responses: Pair<Pair<HomeFeedTab, Int>, Result<HomeFeedPage>>,
    ) : HomeRepository() {
        private val responses = responses.toMap()
        val requests = mutableListOf<Pair<HomeFeedTab, Int>>()

        override suspend fun load(tab: HomeFeedTab, page: Int): Result<HomeFeedPage> {
            val request = tab to page
            requests += request
            return responses.getValue(request)
        }
    }

    private class SequencedHomeRepository(
        initialResponse: Result<HomeFeedPage>,
        vararg pendingResponses: CompletableDeferred<Result<HomeFeedPage>>,
    ) : HomeRepository() {
        private val responses = ArrayDeque<suspend () -> Result<HomeFeedPage>>().apply {
            addLast { initialResponse }
            pendingResponses.forEach { response ->
                addLast { withContext(NonCancellable) { response.await() } }
            }
        }
        val requests = mutableListOf<Pair<HomeFeedTab, Int>>()

        override suspend fun load(tab: HomeFeedTab, page: Int): Result<HomeFeedPage> {
            requests += tab to page
            return responses.removeFirst().invoke()
        }
    }

    private fun page(topicIds: List<Long>, nextPage: Int?): Result<HomeFeedPage> =
        Result.success(
            HomeFeedPage(
                items = topicIds.map { topicId ->
                    TopicSummary(
                        id = topicId,
                        title = "Topic $topicId",
                        authorUsername = "baba",
                    )
                },
                nextPage = nextPage,
                hasMore = nextPage != null,
            ),
        )
}
