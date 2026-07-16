package io.github.baba983.nativedo.feature.topic.ui

import io.github.baba983.nativedo.core.model.TopicSummary

internal data class TopicSearchMatch(
    val postId: Long?,
    val postNumber: Int?,
)

internal class TopicSearchNavigator(
    private val topicId: Long,
) {
    var matches: List<TopicSearchMatch> = emptyList()
        private set

    var selectedIndex: Int = -1
        private set

    val current: TopicSearchMatch?
        get() = matches.getOrNull(selectedIndex)

    val hasPrevious: Boolean
        get() = selectedIndex > 0

    val hasNext: Boolean
        get() = selectedIndex >= 0 && selectedIndex < matches.lastIndex

    fun clear() {
        matches = emptyList()
        selectedIndex = -1
    }

    fun submit(rows: List<TopicSummary>): TopicSearchMatch? {
        val previous = current
        matches = rows.asSequence()
            .filter { row -> row.id == topicId }
            .mapNotNull { row ->
                if (row.targetPostId == null && row.targetPostNumber == null) {
                    null
                } else {
                    TopicSearchMatch(row.targetPostId, row.targetPostNumber)
                }
            }
            .distinctBy(::identity)
            .sortedWith(
                compareBy<TopicSearchMatch> { match -> match.postNumber ?: Int.MAX_VALUE }
                    .thenBy { match -> match.postId ?: Long.MAX_VALUE },
            )
            .toList()
        selectedIndex = when {
            matches.isEmpty() -> -1
            previous == null -> 0
            else -> matches.indexOfFirst { match -> identity(match) == identity(previous) }
                .takeIf { index -> index >= 0 }
                ?: 0
        }
        return current
    }

    fun previous(): TopicSearchMatch? {
        if (!hasPrevious) return current
        selectedIndex -= 1
        return current
    }

    fun next(): TopicSearchMatch? {
        if (!hasNext) return current
        selectedIndex += 1
        return current
    }

    private fun identity(match: TopicSearchMatch): String {
        return match.postId?.let { postId -> "id:$postId" }
            ?: "number:${match.postNumber}"
    }
}

internal fun topicScopedSearchQuery(query: String, topicId: Long): String {
    val normalized = query.trim()
    return if (normalized.isEmpty()) "" else "$normalized topic:$topicId"
}
