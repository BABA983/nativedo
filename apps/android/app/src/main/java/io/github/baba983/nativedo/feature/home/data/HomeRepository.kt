package io.github.baba983.nativedo.feature.home.data

import io.github.baba983.nativedo.core.network.DiscourseApiClient
import io.github.baba983.nativedo.feature.home.ui.HomeFeedTab

private val PAGE_QUERY_REGEX = Regex("""[?&]page=(\d+)""")

class HomeRepository(
    private val client: DiscourseApiClient,
) {
    suspend fun load(tab: HomeFeedTab, page: Int): Result<HomeFeedPage> = runCatching {
        val result = when (tab) {
            HomeFeedTab.Latest -> client.fetchLatestPage(page)
            HomeFeedTab.New -> client.fetchNewPage(page)
            HomeFeedTab.Top -> client.fetchTopPage(page)
        }
        val parsedNextPage = result.moreTopicsUrl
            ?.let { PAGE_QUERY_REGEX.find(it)?.groupValues?.get(1)?.toIntOrNull() }

        HomeFeedPage(
            items = result.topics,
            nextPage = parsedNextPage,
            hasMore = parsedNextPage != null,
        )
    }
}
