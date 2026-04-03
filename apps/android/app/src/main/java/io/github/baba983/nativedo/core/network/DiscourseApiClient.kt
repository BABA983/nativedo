package io.github.baba983.nativedo.core.network

import io.github.baba983.nativedo.core.model.SearchResult
import io.github.baba983.nativedo.core.model.TopicDetail
import io.github.baba983.nativedo.core.model.TopicListPage
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

    override suspend fun fetchCurrentUsername(): String? =
        service.currentSession().currentUser?.username?.takeIf { it.isNotBlank() }

    override suspend fun fetchProfile(username: String): UserProfile =
        service.profile(username).toModel()

    suspend fun fetchLatest(page: Int): List<TopicSummary> =
        fetchLatestPage(page).topics

    suspend fun fetchNew(page: Int): List<TopicSummary> =
        fetchNewPage(page).topics

    suspend fun fetchTop(page: Int): List<TopicSummary> =
        fetchTopPage(page).topics

    suspend fun fetchLatestPage(page: Int): TopicListPage = service.latest(page).toModel()

    suspend fun fetchNewPage(page: Int): TopicListPage = service.newTopics(page).toModel()

    suspend fun fetchTopPage(page: Int): TopicListPage = service.topTopics(page).toModel()

    suspend fun fetchTopic(topicId: Long): TopicDetail = service.topic(topicId).toModel()

    suspend fun fetchTopicPosts(topicId: Long, postIds: List<Long>) =
        service.topicPosts(id = topicId, postIds = postIds).toModel()

    suspend fun search(query: String): SearchResult = service.search(query).toModel()

    suspend fun fetchCsrfToken(): String = service.csrf().csrf
}
