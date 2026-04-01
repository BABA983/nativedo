package io.github.baba983.nativedo.feature.search.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.SearchResult
import io.github.baba983.nativedo.core.model.SearchUser
import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.core.storage.RecentSearchStore
import io.github.baba983.nativedo.feature.search.domain.SearchTopicsUseCase
import io.github.baba983.nativedo.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun submitQuery_updatesResultsAndRecentQueries() = runTest {
        val viewModel = SearchViewModel(
            searchTopics = FakeSearchTopicsUseCase(
                SearchResult(
                    topics = listOf(
                        TopicSummary(
                            id = 2,
                            title = "Search result",
                            authorUsername = "baba",
                        ),
                    ),
                    users = listOf(SearchUser(username = "baba", displayName = "Baba")),
                ),
            ),
            recentSearchStore = RecentSearchStore(),
        )

        viewModel.onQuerySubmitted("compose")
        runCurrent()

        assertThat(viewModel.uiState.value.topics).hasSize(1)
        assertThat(viewModel.uiState.value.recentQueries.first()).isEqualTo("compose")
    }
}

private class FakeSearchTopicsUseCase(
    private val result: SearchResult,
) : SearchTopicsUseCase() {
    override suspend fun invoke(query: String): Result<SearchResult> = Result.success(result)
}
