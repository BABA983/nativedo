package io.github.baba983.nativedo.core.network

import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.PostReactionUsersGroup
import io.github.baba983.nativedo.core.model.CategorySummary
import io.github.baba983.nativedo.core.model.SearchResult
import io.github.baba983.nativedo.core.model.MentionSearchResult
import io.github.baba983.nativedo.core.model.TopicDetail
import io.github.baba983.nativedo.core.model.TopicListPage
import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.core.model.UserProfile
import io.github.baba983.nativedo.core.model.UserActionPage
import io.github.baba983.nativedo.core.session.SessionBootstrapClient
import io.github.baba983.nativedo.core.session.SessionLoggedOutException
import io.github.baba983.nativedo.core.session.SessionUserSummary
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import retrofit2.HttpException

class DiscourseApiClient(
    private val service: DiscourseApiService,
) : SessionBootstrapClient {
    override suspend fun fetchBootstrap(): ParsedBootstrap =
        runWithSessionBoundary {
            service.bootstrap().use { responseBody ->
                BootstrapHtmlParser.parse(responseBody.string())
            }
        }

    override suspend fun fetchCurrentUser(): SessionUserSummary? =
        runWithSessionBoundary {
            service.currentSession().currentUser?.toSessionUserSummary()
        }

    override suspend fun fetchCurrentUsername(): String? =
        fetchCurrentUser()?.username

    suspend fun fetchCsrfToken(): String = runWithSessionBoundary {
        service.csrf().csrf
    }

    suspend fun fetchLatestPage(page: Int): TopicListPage =
        service.latest(page).toModel()

    suspend fun fetchNewPage(page: Int): TopicListPage =
        service.newTopics(page).toModel()

    suspend fun fetchTopPage(page: Int): TopicListPage =
        service.topTopics(page).toModel()

    suspend fun search(query: String, page: Int = 0, typeFilter: String? = null): SearchResult =
        service.search(query = query, page = page, typeFilter = typeFilter).toModel()

    suspend fun searchMentionUsers(term: String, topicId: Long): MentionSearchResult =
        runWithSessionBoundary {
            service.searchMentionUsers(term = term, topicId = topicId).toModel()
        }

    suspend fun fetchCategories(): List<CategorySummary> =
        service.site().toModel()

    suspend fun fetchTopic(topicId: Long): TopicDetail =
        runWithSessionBoundary {
            val emojiGroups = service.emojis()
            val emojiCatalog = emojiGroups.toEmojiCatalog()
            service.topic(topicId).toModel(
                emojiCatalog = emojiCatalog,
                emojiCategories = emojiGroups.toEmojiCategories(),
            )
        }

    suspend fun fetchTopicPosts(topicId: Long, postIds: List<Long>): List<Post> =
        runWithSessionBoundary {
            val emojiCatalog = service.emojis().toEmojiCatalog()
            service.topicPosts(id = topicId, postIds = postIds).toModel(emojiCatalog = emojiCatalog)
        }

    suspend fun fetchPostByNumber(topicId: Long, postNumber: Int): Post =
        runWithSessionBoundary {
            val emojiCatalog = service.emojis().toEmojiCatalog()
            service.postByNumber(
                topicId = topicId,
                postNumber = postNumber,
            ).toModel(emojiCatalog = emojiCatalog)
        }

    suspend fun fetchPostReplies(postId: Long, afterPostNumber: Int = 1): List<Post> =
        runWithSessionBoundary {
            service.postReplies(postId = postId, afterPostNumber = afterPostNumber).map { post -> post.toModel() }
        }

    suspend fun createPost(topicId: Long, raw: String, replyToPostNumber: Int?): Post =
        runWithSessionBoundary {
            val csrfToken = service.csrf().csrf
            service.createPost(
                topicId = topicId,
                raw = raw,
                replyToPostNumber = replyToPostNumber,
                csrfToken = csrfToken,
            ).toModel()
        }

    suspend fun fetchReactionUsers(postId: Long): List<PostReactionUsersGroup> =
        runWithSessionBoundary {
            service.reactionUsers(postId).toModel()
        }

    suspend fun fetchPollVoters(
        postId: Long,
        pollName: String,
        optionId: String? = null,
        page: Int = 1,
        limit: Int = 50,
    ): List<PostReactionUsersGroup> =
        runWithSessionBoundary {
            service.pollVoters(
                postId = postId,
                pollName = pollName,
                optionId = optionId,
                page = page,
                limit = limit,
            ).toModel()
        }

    suspend fun toggleReaction(postId: Long, reactionId: String) =
        runWithSessionBoundary {
            val csrfToken = service.csrf().csrf
            service.toggleReaction(
                postId = postId,
                reactionId = reactionId,
                csrfToken = csrfToken,
            ).close()
        }

    suspend fun setPostBookmark(postId: Long, bookmarked: Boolean) =
        runWithSessionBoundary {
            val csrfToken = service.csrf().csrf
            if (bookmarked) {
                service.createBookmark(
                    bookmarkableId = postId,
                    bookmarkableType = "Post",
                    autoDeletePreference = 0,
                    csrfToken = csrfToken,
                ).close()
            } else {
                service.deletePostBookmark(
                    postId = postId,
                    csrfToken = csrfToken,
                ).close()
            }
        }

    suspend fun votePoll(postId: Long, pollName: String, optionIds: List<String>) =
        runWithSessionBoundary {
            val csrfToken = service.csrf().csrf
            service.votePoll(
                postId = postId,
                pollName = pollName,
                optionIds = optionIds,
                csrfToken = csrfToken,
            ).close()
        }

    suspend fun removePollVote(postId: Long, pollName: String) =
        runWithSessionBoundary {
            val csrfToken = service.csrf().csrf
            service.removePollVote(
                postId = postId,
                pollName = pollName,
                csrfToken = csrfToken,
            ).close()
        }

    suspend fun fetchProfile(username: String): UserProfile =
        runWithSessionBoundary {
            service.profile(username).toModel()
        }

    suspend fun fetchUserActions(
        username: String,
        filter: String,
        offset: Int,
        limit: Int,
    ): UserActionPage =
        runWithSessionBoundary {
            service.userActions(
                username = username,
                filter = filter,
                offset = offset,
                limit = limit,
            ).toModel()
        }

    private suspend fun <T> runWithSessionBoundary(block: suspend () -> T): T {
        return try {
            block()
        } catch (error: HttpException) {
            if (error.isLoggedOutResponse() || error.isNotLoggedInPayload()) {
                throw SessionLoggedOutException(cause = error)
            }
            throw error
        }
    }

    private fun HttpException.isLoggedOutResponse(): Boolean {
        val headerValue = response()?.headers()?.get("discourse-logged-out").orEmpty()
        return headerValue.equals("true", ignoreCase = true) || headerValue == "1"
    }

    private fun HttpException.isNotLoggedInPayload(): Boolean {
        val errorBody = response()?.errorBody()?.string().orEmpty()
        if (errorBody.isBlank()) {
            return false
        }
        return runCatching {
            NetworkJson.instance.parseToJsonElement(errorBody)
                .jsonObject["error_type"]
                ?.jsonPrimitive
                ?.contentOrNull == "not_logged_in"
        }.getOrDefault(false)
    }
}

data class ParsedBootstrap(
    val currentUsername: String?,
)

object BootstrapHtmlParser {
    fun parse(html: String): ParsedBootstrap {
        val document = Jsoup.parse(html)
        val username = document.selectFirst("meta[name=current-username]")
            ?.attr("content")
            ?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("#data-preloaded")
                ?.attr("data-preloaded")
                ?.let(::parsePreloadedCurrentUsername)
        return ParsedBootstrap(currentUsername = username)
    }

    fun parsePreloadedCurrentUsername(preloadedData: String): String? =
        preloadedData.currentUserUsername()

    private fun String.currentUserUsername(): String? {
        val currentUserJson = runCatching {
            NetworkJson.instance.parseToJsonElement(this)
                .jsonObject["currentUser"]
                ?.jsonPrimitive
                ?.contentOrNull
        }.getOrNull()
        return currentUserJson
            ?.let { value ->
                runCatching {
                    NetworkJson.instance.parseToJsonElement(value)
                        .jsonObject["username"]
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.takeIf { username -> username.isNotBlank() }
                }.getOrNull()
            }
    }
}
