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

    open suspend fun loadPostReplies(postId: Long, afterPostNumber: Int): Result<List<Post>> = runCatching {
        client.fetchPostReplies(postId = postId, afterPostNumber = afterPostNumber)
    }

    open suspend fun createReply(topicId: Long, raw: String, replyToPostNumber: Int?): Result<Post> = runCatching {
        client.createPost(topicId = topicId, raw = raw, replyToPostNumber = replyToPostNumber)
    }

    open suspend fun loadReactionUsers(postId: Long): Result<List<PostReactionUsersGroup>> = runCatching {
        client.fetchReactionUsers(postId = postId)
    }

    open suspend fun loadPollVoters(
        postId: Long,
        pollName: String,
        optionId: String? = null,
        page: Int = 1,
        limit: Int = 50,
    ): Result<List<PostReactionUsersGroup>> = runCatching {
        client.fetchPollVoters(postId = postId, pollName = pollName, optionId = optionId, page = page, limit = limit)
    }

    open suspend fun toggleReaction(postId: Long, reactionId: String): Result<Unit> = runCatching {
        client.toggleReaction(postId = postId, reactionId = reactionId)
    }

    open suspend fun setPostBookmark(postId: Long, bookmarked: Boolean): Result<Unit> = runCatching {
        client.setPostBookmark(postId = postId, bookmarked = bookmarked)
    }

    open suspend fun votePoll(postId: Long, pollName: String, optionIds: List<String>): Result<Unit> = runCatching {
        client.votePoll(postId = postId, pollName = pollName, optionIds = optionIds)
    }

    open suspend fun removePollVote(postId: Long, pollName: String): Result<Unit> = runCatching {
        client.removePollVote(postId = postId, pollName = pollName)
    }
}
