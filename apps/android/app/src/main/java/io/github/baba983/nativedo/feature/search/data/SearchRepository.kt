package io.github.baba983.nativedo.feature.search.data

import io.github.baba983.nativedo.core.model.SearchResult
import io.github.baba983.nativedo.core.network.DiscourseApiClient

class SearchRepository(
    private val client: DiscourseApiClient,
) {
    suspend fun search(query: String): Result<SearchResult> = runCatching {
        client.search(query)
    }
}
