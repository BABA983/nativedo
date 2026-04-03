package io.github.baba983.nativedo.feature.topic.data

import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.TopicDetail
import io.github.baba983.nativedo.core.network.DiscourseApiClient

class TopicRepository(
    private val client: DiscourseApiClient,
) {
    suspend fun load(topicId: Long): Result<TopicDetail> = runCatching {
        client.fetchTopic(topicId = topicId)
    }

    suspend fun loadPosts(topicId: Long, postIds: List<Long>): Result<List<Post>> = runCatching {
        client.fetchTopicPosts(topicId = topicId, postIds = postIds)
    }
}
