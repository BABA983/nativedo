package io.github.baba983.nativedo.feature.topic.data

import io.github.baba983.nativedo.core.model.SearchResult
import io.github.baba983.nativedo.core.network.DiscourseApiClient

class TopicSearchRepository(
    private val client: DiscourseApiClient,
) {
    suspend fun search(query: String, page: Int = 0): Result<SearchResult> = runCatching {
        client.search(query = query, page = page)
    }
}
