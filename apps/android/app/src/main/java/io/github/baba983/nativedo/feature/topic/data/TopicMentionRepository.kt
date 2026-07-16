package io.github.baba983.nativedo.feature.topic.data

import io.github.baba983.nativedo.core.model.MentionSearchResult
import io.github.baba983.nativedo.core.network.DiscourseApiClient

class TopicMentionRepository(
    private val client: DiscourseApiClient,
) {
    suspend fun search(term: String, topicId: Long): Result<MentionSearchResult> = runCatching {
        client.searchMentionUsers(term = term, topicId = topicId)
    }
}
