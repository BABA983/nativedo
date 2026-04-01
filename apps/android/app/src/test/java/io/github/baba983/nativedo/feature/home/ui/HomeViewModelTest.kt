package io.github.baba983.nativedo.feature.home.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.feature.home.domain.LoadHomeFeedUseCase
import io.github.baba983.nativedo.test.MainDispatcherRule
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
                latest = listOf(
                    TopicSummary(
                        id = 1,
                        title = "Linux.do topic",
                        authorUsername = "baba",
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
        val useCase = FakeLoadHomeFeedUseCase()
        val viewModel = HomeViewModel(loadHomeFeed = useCase)

        viewModel.onFeedSelected(HomeFeedTab.Top)
        runCurrent()

        assertThat(useCase.requestedTabs.last()).isEqualTo(HomeFeedTab.Top)
    }
}

private class FakeLoadHomeFeedUseCase(
    private val latest: List<TopicSummary> = emptyList(),
) : LoadHomeFeedUseCase() {
    val requestedTabs = mutableListOf<HomeFeedTab>()

    override suspend fun invoke(tab: HomeFeedTab): Result<List<TopicSummary>> {
        requestedTabs += tab
        return Result.success(
            when (tab) {
                HomeFeedTab.Latest -> latest
                HomeFeedTab.New -> emptyList()
                HomeFeedTab.Top -> emptyList()
            },
        )
    }
}
