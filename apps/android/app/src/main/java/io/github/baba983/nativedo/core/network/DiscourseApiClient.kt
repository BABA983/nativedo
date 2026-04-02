package io.github.baba983.nativedo.core.network

import io.github.baba983.nativedo.core.model.SearchResult
import io.github.baba983.nativedo.core.model.TopicDetail
import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.core.model.UserProfile
import io.github.baba983.nativedo.core.session.SessionBootstrapClient

class DiscourseApiClient(
    private val service: DiscourseApiService,
) : SessionBootstrapClient {
    override suspend fun fetchBootstrap(): ParsedBootstrap =
        service.bootstrap().use { responseBody ->
            BootstrapHtmlParser.parse(responseBody.string())
        }

    suspend fun fetchLatest(page: Int): List<TopicSummary> =
        service.latest(page).topicList.topics.map { it.toModel() }

    suspend fun fetchNew(page: Int): List<TopicSummary> =
        service.newTopics(page).topicList.topics.map { it.toModel() }

    suspend fun fetchTop(page: Int): List<TopicSummary> =
        service.topTopics(page).topicList.topics.map { it.toModel() }

    suspend fun fetchTopic(topicId: Long): TopicDetail = service.topic(topicId).toModel()

    suspend fun search(query: String): SearchResult = service.search(query).toModel()

    suspend fun fetchProfile(username: String): UserProfile = service.profile(username).toModel()

    suspend fun fetchCsrfToken(): String = service.csrf().csrf
}
