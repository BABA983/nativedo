package io.github.baba983.nativedo.feature.home.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.feature.home.data.HomeFeedPage
import io.github.baba983.nativedo.feature.home.domain.LoadHomeFeedUseCase
import io.github.baba983.nativedo.test.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadLatest_emitsItems() = runTest {
        val viewModel = HomeViewModel(
            loadHomeFeed = FakeLoadHomeFeedUseCase(
                pages = mapOf(
                    HomeFeedRequest(HomeFeedTab.Latest, 0) to HomeFeedPage(
                        items = listOf(
                            TopicSummary(
                                id = 1,
                                title = "Linux.do topic",
                                authorUsername = "baba",
                            ),
                        ),
                        nextPage = null,
                        hasMore = false,
                    ),
                ),
            ),
        )

        viewModel.uiState.test {
            assertThat(awaitItem().isLoading).isTrue()
            runCurrent()
            assertThat(awaitItem().items).hasSize(1)
        }
    }

    @Test
    fun switchFeed_reloadsRequestedTab() = runTest {
        val useCase = FakeLoadHomeFeedUseCase(
            pages = mapOf(
                HomeFeedRequest(HomeFeedTab.Latest, 0) to HomeFeedPage(
                    items = emptyList(),
                    nextPage = null,
                    hasMore = false,
                ),
                HomeFeedRequest(HomeFeedTab.Top, 0) to HomeFeedPage(
                    items = emptyList(),
                    nextPage = null,
                    hasMore = false,
                ),
            ),
        )
        val viewModel = HomeViewModel(loadHomeFeed = useCase)

        viewModel.onFeedSelected(HomeFeedTab.Top)
        runCurrent()

        assertThat(useCase.requests.last()).isEqualTo(HomeFeedRequest(HomeFeedTab.Top, 0))
    }

    @Test
    fun refresh_replacesItemsAndResetsNextPage() = runTest {
        val useCase = FakeLoadHomeFeedUseCase(
            pages = mapOf(
                HomeFeedRequest(HomeFeedTab.Latest, 0) to HomeFeedPage(
                    items = listOf(
                        TopicSummary(id = 1, title = "First page", authorUsername = "baba"),
                    ),
                    nextPage = 1,
                    hasMore = true,
                ),
                HomeFeedRequest(HomeFeedTab.Latest, 1) to HomeFeedPage(
                    items = listOf(
                        TopicSummary(id = 2, title = "Second page", authorUsername = "alice"),
                    ),
                    nextPage = null,
                    hasMore = false,
                ),
            ),
        )
        val viewModel = HomeViewModel(loadHomeFeed = useCase)

        runCurrent()
        viewModel.loadNextPage()
        runCurrent()
        viewModel.onRefresh()
        runCurrent()

        assertThat(viewModel.uiState.value.items.map { it.id }).containsExactly(1L)
        assertThat(viewModel.uiState.value.nextPage).isEqualTo(1)
        assertThat(viewModel.uiState.value.hasMore).isTrue()
    }

    @Test
    fun loadNextPage_appendsUniqueItemsAndStopsAtEnd() = runTest {
        val useCase = FakeLoadHomeFeedUseCase(
            pages = mapOf(
                HomeFeedRequest(HomeFeedTab.Latest, 0) to HomeFeedPage(
                    items = listOf(
                        TopicSummary(id = 1, title = "Page 0", authorUsername = "baba"),
                    ),
                    nextPage = 1,
                    hasMore = true,
                ),
                HomeFeedRequest(HomeFeedTab.Latest, 1) to HomeFeedPage(
                    items = listOf(
                        TopicSummary(id = 1, title = "Page 0 duplicate", authorUsername = "baba"),
                        TopicSummary(id = 2, title = "Page 1", authorUsername = "alice"),
                    ),
                    nextPage = null,
                    hasMore = false,
                ),
            ),
        )
        val viewModel = HomeViewModel(loadHomeFeed = useCase)

        runCurrent()
        viewModel.loadNextPage()
        runCurrent()
        viewModel.loadNextPage()
        runCurrent()

        assertThat(viewModel.uiState.value.items.map { it.id }).containsExactly(1L, 2L).inOrder()
        assertThat(viewModel.uiState.value.hasMore).isFalse()
        assertThat(useCase.requests).containsExactly(
            HomeFeedRequest(HomeFeedTab.Latest, 0),
            HomeFeedRequest(HomeFeedTab.Latest, 1),
        ).inOrder()
    }

    @Test
    fun refresh_ignoresStaleResponseWhenAuthVersionChanges() = runTest {
        var authVersion = 0L
        val firstPage = CompletableDeferred<Result<HomeFeedPage>>()
        val secondPage = CompletableDeferred<Result<HomeFeedPage>>()
        val useCase = ControlledLoadHomeFeedUseCase(
            deferredPages = mapOf(
                VersionedHomeFeedRequest(HomeFeedTab.Latest, 0, 0L) to firstPage,
                VersionedHomeFeedRequest(HomeFeedTab.Latest, 0, 1L) to secondPage,
            ),
            currentSessionVersion = { authVersion },
        )
        val viewModel = HomeViewModel(
            loadHomeFeed = useCase,
            currentSessionVersion = { authVersion },
        )

        runCurrent()

        authVersion = 1L
        viewModel.onRefresh()
        runCurrent()

        secondPage.complete(
            Result.success(
                HomeFeedPage(
                    items = listOf(
                        TopicSummary(id = 2, title = "Authenticated", authorUsername = "baba"),
                    ),
                    nextPage = null,
                    hasMore = false,
                ),
            ),
        )
        runCurrent()

        firstPage.complete(
            Result.success(
                HomeFeedPage(
                    items = listOf(
                        TopicSummary(id = 1, title = "Anonymous", authorUsername = "guest"),
                    ),
                    nextPage = null,
                    hasMore = false,
                ),
            ),
        )
        runCurrent()

        assertThat(viewModel.uiState.value.items.map { it.id }).containsExactly(2L)
    }
}

private data class HomeFeedRequest(
    val tab: HomeFeedTab,
    val page: Int,
)

private data class VersionedHomeFeedRequest(
    val tab: HomeFeedTab,
    val page: Int,
    val authVersion: Long,
)

private class FakeLoadHomeFeedUseCase(
    private val pages: Map<HomeFeedRequest, HomeFeedPage> = emptyMap(),
) : LoadHomeFeedUseCase() {
    val requests = mutableListOf<HomeFeedRequest>()

    override suspend fun invoke(tab: HomeFeedTab, page: Int): Result<HomeFeedPage> {
        val request = HomeFeedRequest(tab, page)
        requests += request
        return Result.success(
            checkNotNull(pages[request]) {
                "Missing fake page for $request"
            },
        )
    }
}

private class ControlledLoadHomeFeedUseCase(
    private val deferredPages: Map<VersionedHomeFeedRequest, CompletableDeferred<Result<HomeFeedPage>>>,
    private val currentSessionVersion: () -> Long,
) : LoadHomeFeedUseCase() {
    override suspend fun invoke(tab: HomeFeedTab, page: Int): Result<HomeFeedPage> {
        return deferredPages.getValue(
            VersionedHomeFeedRequest(
                tab = tab,
                page = page,
                authVersion = currentSessionVersion(),
            ),
        ).await()
    }
}
