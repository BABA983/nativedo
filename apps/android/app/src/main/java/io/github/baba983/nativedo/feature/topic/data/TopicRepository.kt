package io.github.baba983.nativedo.feature.topic.data

import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.PostReactionUsersGroup
import io.github.baba983.nativedo.core.model.TopicDetail
import io.github.baba983.nativedo.core.network.DiscourseApiClient

open class TopicRepository protected constructor() {
    private lateinit var client: DiscourseApiClient

    constructor(client: DiscourseApiClient) : this() {
        this.client = client
    }

    open suspend fun load(topicId: Long): Result<TopicDetail> = runCatching {
        client.fetchTopic(topicId = topicId)
    }

    open suspend fun loadPosts(topicId: Long, postIds: List<Long>): Result<List<Post>> = runCatching {
        client.fetchTopicPosts(topicId = topicId, postIds = postIds)
    }

    open suspend fun loadPostByNumber(topicId: Long, postNumber: Int): Result<Post> = runCatching {
        client.fetchPostByNumber(topicId = topicId, postNumber = postNumber)
    }

    open suspend fun loadReactionUsers(postId: Long): Result<List<PostReactionUsersGroup>> = runCatching {
        client.fetchReactionUsers(postId = postId)
    }

    open suspend fun toggleReaction(postId: Long, reactionId: String): Result<Unit> = runCatching {
        client.toggleReaction(postId = postId, reactionId = reactionId)
    }

    open suspend fun votePoll(postId: Long, pollName: String, optionIds: List<String>): Result<Unit> = runCatching {
        client.votePoll(postId = postId, pollName = pollName, optionIds = optionIds)
    }
}
