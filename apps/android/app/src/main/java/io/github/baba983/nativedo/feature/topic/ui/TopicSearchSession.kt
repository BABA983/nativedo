package io.github.baba983.nativedo.feature.topic.ui

import io.github.baba983.nativedo.core.model.SearchResult
import io.github.baba983.nativedo.core.model.TopicSummary

internal data class TopicSearchRequest(
    val generation: Long,
    val query: String,
    val page: Int,
)

internal data class TopicSearchSnapshot(
    val query: String = "",
    val result: SearchResult = SearchResult(),
    val page: Int = -1,
    val hasMore: Boolean = false,
    val isInitialLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val initialError: String? = null,
    val loadMoreError: String? = null,
)

/**
 * Request-generation and pagination owner for Discourse search.
 *
 * This mirrors Telegram search adapters' captured search-id contract: a response may
 * mutate visible state only when its generation, query, and expected page still match.
 */
internal class TopicSearchSession {
    private var generation = 0L
    private var expectedRequest: TopicSearchRequest? = null

    var snapshot: TopicSearchSnapshot = TopicSearchSnapshot()
        private set

    fun start(query: String): TopicSearchRequest {
        generation += 1
        val request = TopicSearchRequest(generation, query, page = 0)
        expectedRequest = request
        snapshot = TopicSearchSnapshot(query = query, isInitialLoading = true)
        return request
    }

    fun clear() {
        generation += 1
        expectedRequest = null
        snapshot = TopicSearchSnapshot()
    }

    fun nextPage(): TopicSearchRequest? {
        val current = snapshot
        if (current.query.isBlank() || current.isInitialLoading || current.isLoadingMore || !current.hasMore) {
            return null
        }
        val request = TopicSearchRequest(generation, current.query, current.page + 1)
        expectedRequest = request
        snapshot = current.copy(isLoadingMore = true, loadMoreError = null)
        return request
    }

    fun accept(request: TopicSearchRequest, result: SearchResult): Boolean {
        if (!isExpected(request)) return false
        val merged = if (request.page == 0) result else mergeSearchResults(snapshot.result, result)
        val addedRows = if (request.page == 0) {
            merged.topics.size + merged.posts.size + merged.users.size
        } else {
            (merged.topics.size - snapshot.result.topics.size) +
                (merged.posts.size - snapshot.result.posts.size) +
                (merged.users.size - snapshot.result.users.size)
        }
        expectedRequest = null
        snapshot = snapshot.copy(
            result = merged,
            page = request.page,
            hasMore = result.hasMore && (request.page == 0 || addedRows > 0),
            isInitialLoading = false,
            isLoadingMore = false,
            initialError = null,
            loadMoreError = null,
        )
        return true
    }

    fun fail(request: TopicSearchRequest, message: String): Boolean {
        if (!isExpected(request)) return false
        expectedRequest = null
        snapshot = if (request.page == 0) {
            snapshot.copy(
                isInitialLoading = false,
                initialError = message,
            )
        } else {
            snapshot.copy(
                isLoadingMore = false,
                loadMoreError = message,
            )
        }
        return true
    }

    private fun isExpected(request: TopicSearchRequest): Boolean =
        expectedRequest == request &&
            request.generation == generation &&
            request.query == snapshot.query
}

internal fun mergeSearchResults(current: SearchResult, next: SearchResult): SearchResult = SearchResult(
    topics = mergeDistinct(current.topics, next.topics) { topic -> topic.id.toString() },
    posts = mergeDistinct(current.posts, next.posts, ::searchPostIdentity),
    users = mergeDistinct(current.users, next.users) { user -> user.username.lowercase() },
    hasMore = next.hasMore,
)

private fun searchPostIdentity(topic: TopicSummary): String =
    topic.targetPostId?.let { "post:$it" }
        ?: topic.rowIdentity?.let { "row:$it" }
        ?: "topic:${topic.id}:number:${topic.targetPostNumber}:author:${topic.authorUsername}:excerpt:${topic.excerpt}"

private inline fun <T> mergeDistinct(
    current: List<T>,
    next: List<T>,
    identity: (T) -> String,
): List<T> {
    val values = LinkedHashMap<String, T>(current.size + next.size)
    current.forEach { value -> values[identity(value)] = value }
    next.forEach { value -> values.putIfAbsent(identity(value), value) }
    return values.values.toList()
}
