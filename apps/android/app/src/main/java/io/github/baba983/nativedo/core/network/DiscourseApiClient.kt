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
import io.github.baba983.nativedo.core.session.LoginRequiredException
import io.github.baba983.nativedo.core.session.SessionBootstrapClient
import io.github.baba983.nativedo.core.session.SessionExpiredReason
import io.github.baba983.nativedo.core.session.SessionLoggedOutException
import io.github.baba983.nativedo.core.session.SessionRequestAuth
import io.github.baba983.nativedo.core.session.SessionRequestContext
import io.github.baba983.nativedo.core.session.SessionUserSummary
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
import org.jsoup.Jsoup
import retrofit2.HttpException

class DiscourseApiClient(
    private val service: DiscourseApiService,
) : SessionBootstrapClient {
    private var sessionContextProvider: () -> SessionRequestContext = {
        SessionRequestContext(
            generation = 0,
            auth = SessionRequestAuth.Authenticated,
        )
    }
    private var onSessionInvalidated: suspend (SessionExpiredReason, Int) -> Unit = { _, _ -> }
    private var onLoginRequired: suspend () -> Unit = {}

    /**
     * Injects the session boundary this client reports to: every request
     * resolves its [SessionRequestContext] up front, and a proven logout is
     * reported back with the generation that started the request so a stale
     * response cannot invalidate a newer WebView login.
     */
    fun setSessionBoundary(
        contextProvider: () -> SessionRequestContext,
        onSessionInvalidated: suspend (SessionExpiredReason, Int) -> Unit,
        onLoginRequired: suspend () -> Unit,
    ) {
        sessionContextProvider = contextProvider
        this.onSessionInvalidated = onSessionInvalidated
        this.onLoginRequired = onLoginRequired
    }

    override suspend fun fetchBootstrap(): ParsedBootstrap =
        runWithSessionBoundary {
            service.bootstrap().use { responseBody ->
                val html = responseBody.string()
                BootstrapHtmlParser.parse(html)
            }
        }

    override suspend fun fetchCurrentUser(): SessionUserSummary? =
        runWithSessionBoundary {
            try {
                val currentUser = service.currentSession().currentUser
                currentUser?.toSessionUserSummary()
                    ?: run {
                        // Discourse uses a 200 response with current_user=null
                        // for an authenticated session that has been revoked.
                        // Treat it like the explicit logout headers below so
                        // every caller reaches the same cookie-clearing path.
                        throw SessionLoggedOutException()
                    }
            } catch (error: SerializationException) {
                throw SessionRecoveryRequiredException(cause = error)
            }
        }

    override suspend fun fetchCurrentUsername(): String? =
        fetchCurrentUser()?.username

    suspend fun fetchCsrfToken(): String = runWithSessionBoundary {
        service.csrf().csrf
    }

    suspend fun fetchLatestPage(page: Int): TopicListPage =
        runWithSessionBoundary { service.latest(page).toModel() }

    suspend fun fetchNewPage(page: Int): TopicListPage =
        runWithSessionBoundary { service.newTopics(page).toModel() }

    suspend fun fetchTopPage(page: Int): TopicListPage =
        runWithSessionBoundary { service.topTopics(page).toModel() }

    suspend fun search(query: String, page: Int = 0, typeFilter: String? = null): SearchResult =
        runWithSessionBoundary {
            service.search(query = query, page = page, typeFilter = typeFilter).toModel()
        }

    suspend fun searchMentionUsers(term: String, topicId: Long): MentionSearchResult =
        runWithSessionBoundary {
            service.searchMentionUsers(term = term, topicId = topicId).toModel()
        }

    suspend fun fetchCategories(): List<CategorySummary> =
        runWithSessionBoundary { service.site().toModel() }

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
        runWithSessionBoundary(requiresAuthentication = true) {
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
        runWithSessionBoundary(requiresAuthentication = true) {
            val csrfToken = service.csrf().csrf
            service.toggleReaction(
                postId = postId,
                reactionId = reactionId,
                csrfToken = csrfToken,
            ).close()
        }

    suspend fun setPostBookmark(postId: Long, bookmarked: Boolean) =
        runWithSessionBoundary(requiresAuthentication = true) {
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
        runWithSessionBoundary(requiresAuthentication = true) {
            val csrfToken = service.csrf().csrf
            service.votePoll(
                postId = postId,
                pollName = pollName,
                optionIds = optionIds,
                csrfToken = csrfToken,
            ).close()
        }

    suspend fun removePollVote(postId: Long, pollName: String) =
        runWithSessionBoundary(requiresAuthentication = true) {
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

    private suspend fun <T> runWithSessionBoundary(
        requiresAuthentication: Boolean = false,
        block: suspend () -> T,
    ): T {
        val requestContext = sessionContextProvider()
        return try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: LoginRequiredException) {
            throw error
        } catch (error: SessionLoggedOutException) {
            handleLoggedOutResponse(requestContext, requiresAuthentication, error)
        } catch (error: HttpException) {
            // ResponseBody is one-shot. Read it once so a JSON logout check
            // cannot consume the Cloudflare challenge evidence that follows.
            val errorBody = error.response()?.errorBody()?.string().orEmpty()
            when {
                error.isLoggedOutResponse() || error.isNotLoggedInPayload(errorBody) -> {
                    handleLoggedOutResponse(requestContext, requiresAuthentication, error)
                }
                error.isCloudflareChallengeResponse(errorBody) -> {
                    throw SessionRecoveryRequiredException(cause = error)
                }
                else -> throw error
            }
        }
    }

    private suspend fun handleLoggedOutResponse(
        requestContext: SessionRequestContext,
        requiresAuthentication: Boolean,
        cause: Throwable,
    ): Nothing {
        if (requestContext.canUseAuthenticatedSession) {
            onSessionInvalidated(SessionExpiredReason.ServerLoggedOut, requestContext.generation)
            throw SessionLoggedOutException(cause = cause)
        }
        if (requiresAuthentication || requestContext.auth == SessionRequestAuth.Anonymous) {
            onLoginRequired()
        }
        throw LoginRequiredException(cause = cause)
    }

    private fun HttpException.isLoggedOutResponse(): Boolean {
        val headerValue = response()?.headers()?.get("discourse-logged-out").orEmpty()
        return headerValue.equals("true", ignoreCase = true) ||
            headerValue == "1" ||
            code() == 401
    }

    private fun HttpException.isNotLoggedInPayload(errorBody: String): Boolean {
        if (errorBody.isBlank()) {
            return false
        }
        return runCatching {
            val payload = NetworkJson.instance.parseToJsonElement(errorBody).jsonObject
            val errorType = payload["error_type"]?.jsonPrimitive?.contentOrNull
            val errorName = payload["error"]?.jsonPrimitive?.contentOrNull
            errorType.equals("not_logged_in", ignoreCase = true) ||
                errorType.equals("logged_out", ignoreCase = true) ||
                errorName.equals("not_logged_in", ignoreCase = true) ||
                errorName.equals("logged_out", ignoreCase = true)
        }.getOrDefault(false)
    }

    private fun HttpException.isCloudflareChallengeResponse(errorBody: String): Boolean {
        if (code() !in setOf(403, 429, 503)) return false
        val headers = response()?.headers() ?: return false
        val server = headers["server"].orEmpty()
        val mitigated = headers["cf-mitigated"].orEmpty()
        val contentType = headers["content-type"].orEmpty()
        val body = errorBody.lowercase(Locale.US)
        return mitigated.contains("challenge", ignoreCase = true) ||
            (server.contains("cloudflare", ignoreCase = true) &&
                (contentType.contains("text/html", ignoreCase = true) || body.contains("challenge"))) ||
            looksLikeCloudflareChallenge(body)
    }
}

internal fun looksLikeCloudflareChallenge(bodyLowercased: String): Boolean =
    bodyLowercased.contains("just a moment") ||
        bodyLowercased.contains("cf-chl-") ||
        bodyLowercased.contains("challenge-platform") ||
        bodyLowercased.contains("checking your browser")

data class ParsedBootstrap(
    val currentUsername: String?,
)

object BootstrapHtmlParser {
    fun parse(html: String): ParsedBootstrap {
        if (looksLikeCloudflareChallenge(html.lowercase(Locale.US))) {
            throw SessionRecoveryRequiredException()
        }
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
        }.getOrNull()
        return when {
            currentUserJson == null -> null
            currentUserJson is kotlinx.serialization.json.JsonObject ->
                currentUserJson["username"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.isNotBlank() }
            else -> currentUserJson.jsonPrimitive.contentOrNull?.let { value ->
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
}
