package io.github.baba983.nativedo.ui.topic

import android.content.Context
import android.view.ViewGroup
import io.github.baba983.nativedo.core.model.CategorySummary
import io.github.baba983.nativedo.core.model.SearchResult
import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.feature.topic.ui.TopicSearchSnapshot
import io.github.baba983.nativedo.screenshot.NativeDoPaparazziRule
import io.github.baba983.nativedo.screenshot.SystemAppearance
import org.junit.Rule
import org.junit.Test
import org.telegram.messenger.AndroidUtilities

internal class TopicSearchViewPagerScreenshotTest {
    @get:Rule
    val paparazziRule = NativeDoPaparazziRule(SystemAppearance.entries)

    @Test
    fun initialLoading() {
        paparazziRule.snapshot { context ->
            searchPager(
                context,
                TopicSearchSnapshot(query = "linux", isInitialLoading = true),
            )
        }
    }

    @Test
    fun topicResultsHighlightQuery() {
        paparazziRule.snapshot { context ->
            searchPager(
                context,
                TopicSearchSnapshot(
                    query = "linux",
                    result = SearchResult(
                        topics = listOf(
                            TopicSummary(
                                id = 1L,
                                title = "在Linux环境里使用 NativeDo",
                                authorUsername = "alice",
                                lastPosterUsername = "alice",
                                lastPostedAt = "2026-07-14T10:00:00Z",
                                categoryId = 1,
                                replyCount = 23,
                            ),
                            TopicSummary(
                                id = 2L,
                                title = "Linux do topic search result",
                                authorUsername = "bob",
                                lastPosterUsername = "bob",
                                lastPostedAt = "2026-07-13T10:00:00Z",
                                categoryId = 2,
                                replyCount = 6,
                            ),
                        ),
                    ),
                ),
            )
        }
    }

    private fun searchPager(
        context: Context,
        snapshot: TopicSearchSnapshot,
    ): TopicSearchViewPager {
        return TopicSearchViewPager(
            context = context,
            baseUrl = "https://linux.do/",
            topPaddingPx = AndroidUtilities.dp(48f),
            onFilterSelected = {},
            onTopicSelected = { _, _ -> },
            onUserSelected = {},
            onLoadMore = {},
            onRetry = {},
        ).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                AndroidUtilities.dp(520f),
            )
            // Match TopicListFragment's host setup so transparent child regions do not
            // render against Paparazzi's default black surface.
            updateColors()
            submit(
                snapshot,
                mapOf(
                    1 to CategorySummary(id = 1, name = "分享", colorHex = "0088CC"),
                    2 to CategorySummary(id = 2, name = "讨论", colorHex = "37B24D"),
                ),
            )
        }
    }
}
