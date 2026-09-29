package io.github.baba983.nativedo.feature.topic.data

import io.github.baba983.nativedo.core.model.SearchResult
import io.github.baba983.nativedo.core.network.DiscourseApiClient
import kotlinx.coroutines.CancellationException

class TopicSearchRepository(
    private val client: DiscourseApiClient,
) {
    suspend fun search(query: String, page: Int = 0, typeFilter: String? = null): Result<SearchResult> = try {
        Result.success(client.search(query = query, page = page, typeFilter = typeFilter))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        Result.failure(error)
    }
}
