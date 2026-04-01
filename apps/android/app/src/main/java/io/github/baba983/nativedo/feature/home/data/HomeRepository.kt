package io.github.baba983.nativedo.feature.home.data

import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.core.network.DiscourseApiClient
import io.github.baba983.nativedo.feature.home.ui.HomeFeedTab

class HomeRepository(
    private val client: DiscourseApiClient,
) {
    suspend fun load(tab: HomeFeedTab): Result<List<TopicSummary>> = runCatching {
        when (tab) {
            HomeFeedTab.Latest -> client.fetchLatest(page = 0)
            HomeFeedTab.New -> client.fetchNew(page = 0)
            HomeFeedTab.Top -> client.fetchTop(page = 0)
        }
    }
}
