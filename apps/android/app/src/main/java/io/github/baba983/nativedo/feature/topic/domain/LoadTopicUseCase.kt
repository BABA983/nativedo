package io.github.baba983.nativedo.feature.topic.domain

import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.TopicDetail
import io.github.baba983.nativedo.feature.topic.data.TopicRepository

open class LoadTopicUseCase protected constructor() {
    private lateinit var repository: TopicRepository

    constructor(repository: TopicRepository) : this() {
        this.repository = repository
    }

    open suspend operator fun invoke(topicId: Long): Result<TopicDetail> =
        repository.load(topicId)

    open suspend fun loadPosts(topicId: Long, postIds: List<Long>): Result<List<Post>> =
        repository.loadPosts(topicId, postIds)
}
