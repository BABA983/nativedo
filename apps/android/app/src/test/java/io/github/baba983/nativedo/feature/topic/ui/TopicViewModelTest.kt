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
}

private class FakeLoadTopicUseCase(
    private val detail: TopicDetail,
) : LoadTopicUseCase() {
    override suspend fun invoke(topicId: Long): Result<TopicDetail> {
        return Result.success(detail)
    }
}
