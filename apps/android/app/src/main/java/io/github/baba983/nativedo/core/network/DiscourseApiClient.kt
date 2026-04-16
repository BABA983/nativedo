package io.github.baba983.nativedo.core.network

import io.github.baba983.nativedo.core.log.NativeDoLogTag
import io.github.baba983.nativedo.core.log.debugLog
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.PostReactionUsersGroup
import io.github.baba983.nativedo.core.model.SearchResult
import io.github.baba983.nativedo.core.model.CategorySummary
import io.github.baba983.nativedo.core.model.TopicDetail
import io.github.baba983.nativedo.core.model.TopicListPage
import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.core.model.UserProfile
import io.github.baba983.nativedo.core.session.SessionBootstrapClient
import io.github.baba983.nativedo.core.session.SessionExpiredReason
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

class DiscourseApiClient(
    private val service: DiscourseApiService,
    private val onSessionInvalidated: suspend (SessionExpiredReason) -> Unit = {},
) : SessionBootstrapClient {
    override suspend fun fetchBootstrap(): ParsedBootstrap =
        runWithSessionRecovery {
            service.bootstrap().use { responseBody ->
                BootstrapHtmlParser.parse(responseBody.string())
            }
        }

    override suspend fun fetchCurrentUsername(): String? =
        runWithSessionRecovery {
            service.currentSession().currentUser?.username?.takeIf { it.isNotBlank() }
        }

    override suspend fun fetchProfile(username: String): UserProfile =
        runWithSessionRecovery {
            service.profile(username).toModel().also { profile ->
                logProfileMediaDebug(
                    requestedUsername = username,
                    profile = profile,
                )
            }
        }

    suspend fun fetchLatest(page: Int): List<TopicSummary> =
        fetchLatestPage(page).topics

    suspend fun fetchNew(page: Int): List<TopicSummary> =
        fetchNewPage(page).topics

    suspend fun fetchTop(page: Int): List<TopicSummary> =
        fetchTopPage(page).topics

    suspend fun fetchLatestPage(page: Int): TopicListPage = runWithSessionRecovery {
        service.latest(page).toModel()
    }

    suspend fun fetchNewPage(page: Int): TopicListPage = runWithSessionRecovery {
        service.newTopics(page).toModel()
    }

    suspend fun fetchTopPage(page: Int): TopicListPage = runWithSessionRecovery {
        service.topTopics(page).toModel()
    }

    suspend fun fetchTopic(topicId: Long): TopicDetail = runWithSessionRecovery {
        val emojiCatalog = service.emojis().toEmojiCatalog()
        service.topic(topicId).toModel(emojiCatalog = emojiCatalog).also { topic ->
            logPostMediaDebug(
                source = "topicId=$topicId initial",
                posts = topic.posts,
            )
        }
    }

    suspend fun fetchTopicPosts(topicId: Long, postIds: List<Long>) =
        runWithSessionRecovery {
            val emojiCatalog = service.emojis().toEmojiCatalog()
            service.topicPosts(id = topicId, postIds = postIds).toModel(emojiCatalog = emojiCatalog).also { posts ->
                logPostMediaDebug(
                    source = "topicId=$topicId chunk=${postIds.joinToString(separator = ",")}",
                    posts = posts,
                )
            }
        }

    suspend fun fetchPostByNumber(topicId: Long, postNumber: Int): Post = runWithSessionRecovery {
        val emojiCatalog = service.emojis().toEmojiCatalog()
        service.postByNumber(topicId = topicId, postNumber = postNumber).toModel(emojiCatalog = emojiCatalog).also { post ->
            logPostMediaDebug(
                source = "topicId=$topicId byNumber=$postNumber",
                posts = listOf(post),
            )
        }
    }

    suspend fun fetchReactionUsers(postId: Long): List<PostReactionUsersGroup> = runWithSessionRecovery {
        service.reactionUsers(postId).toModel()
    }

    suspend fun toggleReaction(postId: Long, reactionId: String) = runWithSessionRecovery {
        val csrfToken = service.csrf().csrf
        service.toggleReaction(postId = postId, reactionId = reactionId, csrfToken = csrfToken).close()
    }

    suspend fun search(query: String): SearchResult = runWithSessionRecovery {
        service.search(query).toModel()
    }

    suspend fun fetchCategories(): List<CategorySummary> = runWithSessionRecovery {
        service.site().toModel()
    }

    suspend fun fetchCsrfToken(): String = runWithSessionRecovery {
        service.csrf().csrf
    }

    private suspend fun <T> runWithSessionRecovery(block: suspend () -> T): T {
        return try {
            block()
        } catch (error: HttpException) {
            when {
                error.isCloudflareChallengeResponse() -> {
                    throw SessionRecoveryRequiredException(cause = error)
                }
                error.isLoggedOutResponse() || error.isNotLoggedInPayload() -> {
                    onSessionInvalidated(SessionExpiredReason.ServerLoggedOut)
                    throw SessionRecoveryRequiredException(cause = error)
                }
                else -> throw error
            }
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

    private fun HttpException.isCloudflareChallengeResponse(): Boolean {
        if (code() != 403) {
            return false
        }
        val response = response() ?: return false
        val serverHeader = response.headers()["server"].orEmpty()
        if (!serverHeader.contains("cloudflare", ignoreCase = true)) {
            return false
        }
        val contentType = response.headers()["content-type"].orEmpty()
        val cfMitigated = response.headers()["cf-mitigated"].orEmpty()
        return contentType.contains("text/html", ignoreCase = true) ||
            cfMitigated.contains("challenge", ignoreCase = true)
    }

    private fun logPostMediaDebug(
        source: String,
        posts: List<Post>,
    ) {
        debugLog(TAG, formatPostMediaDebugSummary(source = source, posts = posts))
    }

    private fun logProfileMediaDebug(
        requestedUsername: String,
        profile: UserProfile,
    ) {
        debugLog(
            TAG,
            formatProfileMediaDebugSummary(
                requestedUsername = requestedUsername,
                profile = profile,
            ),
        )
    }

    private companion object {
        val TAG: NativeDoLogTag = NativeDoLogTag.of("DiscourseApi")
    }
}

internal fun formatPostMediaDebugSummary(
    source: String,
    posts: List<Post>,
): String {
    return buildString {
        append("post-media source=")
        append(source)
        append(" count=")
        append(posts.size)
        append(" [")
        append(
            posts.joinToString(separator = "; ") { post ->
                buildString {
                    append("#")
                    append(post.postNumber ?: "?")
                    append("@")
                    append(post.authorUsername)
                    append(" avatar=")
                    append(post.authorAvatarTemplate.ifBlank { "-" })
                    append(" animated=")
                    append(post.authorAnimatedAvatarUrl.ifBlank { "-" })
                    append(" flair=")
                    append(post.authorFlairUrl.ifBlank { "-" })
                }
            },
        )
        append("]")
    }
}

internal fun formatProfileMediaDebugSummary(
    requestedUsername: String,
    profile: UserProfile,
): String {
    return buildString {
        append("profile-media requested=")
        append(requestedUsername)
        append(" resolved=")
        append(profile.username)
        append(" avatar=")
        append(profile.avatarUrl.ifBlank { "-" })
        append(" animated=")
        append(profile.animatedAvatarUrl.ifBlank { "-" })
    }
}
