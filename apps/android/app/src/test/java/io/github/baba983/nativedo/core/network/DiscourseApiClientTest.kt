package io.github.baba983.nativedo.core.network

import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.github.baba983.nativedo.core.session.SessionExpiredReason
import io.github.baba983.nativedo.core.session.SessionLoggedOutException
import okhttp3.MediaType.Companion.toMediaType

class DiscourseApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: DiscourseApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(NetworkJson.instance.asConverterFactory("application/json".toMediaType()))
            .build()
        client = DiscourseApiClient(retrofit.create(DiscourseApiService::class.java))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun parsesSearchWhenDiscourseReturnsNullPaginationFlags() = runTest {
        server.enqueue(
            jsonResponse(
                """
                {
                  "topics": [],
                  "posts": [],
                  "users": [],
                  "grouped_search_result": {
                    "more_posts": null,
                    "more_topics": null,
                    "more_full_page_results": false
                  }
                }
                """.trimIndent(),
            ),
        )

        val result = client.search(query = "topic:2566145", page = 0)

        assertThat(result.hasMore).isFalse()
        assertThat(server.takeRequest().path).isEqualTo("/search.json?q=topic%3A2566145&page=0")
    }

    @Test
    fun topicScopedSearchSendsTypeFilterAndUsesFullPagePaginationFlag() = runTest {
        server.enqueue(
            jsonResponse(
                """
                {
                  "topics": [],
                  "posts": [],
                  "users": [],
                  "grouped_search_result": {
                    "more_posts": null,
                    "more_topics": false,
                    "more_full_page_results": true
                  }
                }
                """.trimIndent(),
            ),
        )

        val result = client.search(query = "429 topic:2566145", page = 1, typeFilter = "topic")

        assertThat(result.hasMore).isTrue()
        assertThat(server.takeRequest().path)
            .isEqualTo("/search.json?q=429%20topic%3A2566145&page=1&type_filter=topic")
    }

    @Test
    fun searchesTopicMentionUsersWithComposerEndpointAndBlankTerm() = runTest {
        server.enqueue(jsonResponse(fixture("discourse/mention-users.json")))

        val result = client.searchMentionUsers(term = "", topicId = 847468)

        assertThat(result.candidates.map { it.username }).containsExactly("orchid.hu", "team").inOrder()
        assertThat(result.candidates.first().priorityGroup).isEqualTo(0)
        assertThat(result.candidates.last().isGroup).isTrue()
        assertThat(server.takeRequest().path)
            .isEqualTo("/u/search/users?term=&topic_id=847468&include_groups=true&limit=6")
    }

    @Test
    fun topicDetailWithoutDetailsKeepsParticipantsEmpty() = runTest {
        server.enqueue(jsonResponse("""{"discourse_reactions":[]}"""))
        server.enqueue(
            jsonResponse(
                """{
                    "id": 2,
                    "title": "No details",
                    "post_stream": {
                        "stream": [2001],
                        "posts": [{"id":2001,"post_number":1,"username":"baba","cooked":"<p>x</p>"}]
                    }
                }""".trimIndent(),
            ),
        )

        val topic = client.fetchTopic(topicId = 2)

        assertThat(topic.participants).isEmpty()
    }

    @Test
    fun createPostFetchesCsrfAndSendsDiscourseReplyFields() = runTest {
        server.enqueue(jsonResponse("""{"csrf":"csrf-token"}"""))
        server.enqueue(
            jsonResponse(
                """
                {
                  "id": 1003,
                  "post_number": 3,
                  "reply_to_post_number": 1,
                  "username": "baba",
                  "avatar_template": "/user_avatar/linux.do/baba/{size}/1.png",
                  "cooked": "<p>Hello reply</p>"
                }
                """.trimIndent(),
            ),
        )

        val post = client.createPost(topicId = 101L, raw = "hello", replyToPostNumber = 1)

        assertThat(post.id).isEqualTo(1003L)
        assertThat(post.postNumber).isEqualTo(3)
        assertThat(post.replyToPostNumber).isEqualTo(1)
        assertThat(server.takeRequest().path).isEqualTo("/session/csrf")
        val createRequest = server.takeRequest()
        assertThat(createRequest.method).isEqualTo("POST")
        assertThat(createRequest.path).isEqualTo("/posts.json")
        assertThat(createRequest.getHeader("X-CSRF-Token")).isEqualTo("csrf-token")
        val body = createRequest.body.readUtf8()
        assertThat(body).contains("topic_id=101")
        assertThat(body).contains("raw=hello")
        assertThat(body).contains("reply_to_post_number=1")
    }

    @Test
    fun markTopicReadPostsTimingsFormWithCsrfToken() = runTest {
        server.enqueue(jsonResponse("""{"csrf":"csrf-token"}"""))
        server.enqueue(jsonResponse("{}"))

        client.markTopicRead(
            topicId = 101L,
            topicTimeMillis = 2_000L,
            postTimings = mapOf(3 to 1_000, 4 to 1_000),
        )

        assertThat(server.takeRequest().path).isEqualTo("/session/csrf")
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/topics/timings")
        assertThat(request.getHeader("X-CSRF-Token")).isEqualTo("csrf-token")
        val body = request.body.readUtf8()
        assertThat(body).contains("topic_id=101")
        assertThat(body).contains("topic_time=2000")
        assertThat(body).contains("timings%5B3%5D=1000")
        assertThat(body).contains("timings%5B4%5D=1000")
    }

    @Test
    fun fetchTopicUsesEmojiCatalogForReactionIcons() = runTest {
        server.enqueue(jsonResponse("""{"discourse_reactions":[{"name":"heart","url":"/images/emoji/heart.png"}]}"""))
        server.enqueue(
            jsonResponse(
                """
                {
                  "id": 101,
                  "title": "Welcome",
                  "valid_reactions": ["heart", "+1"],
                  "post_stream": {
                    "posts": [
                      {
                        "id": 1001,
                        "username": "baba",
                        "reactions": [{"id": "heart", "count": 2}],
                        "current_user_reaction": {"id": "heart"},
                        "reaction_users_count": 2,
                        "bookmarked": true,
                        "cooked": "<p>Hello</p>"
                      }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )

        val topic = client.fetchTopic(topicId = 101)
        val post = topic.posts.single()

        assertThat(topic.validReactions).containsExactly("heart", "+1").inOrder()
        assertThat(post.currentUserReaction).isEqualTo("heart")
        assertThat(post.reactionUsersCount).isEqualTo(2)
        assertThat(post.isBookmarked).isTrue()
        assertThat(post.reactions.single().iconUrl).isEqualTo("/images/emoji/heart.png")
        assertThat(post.reactions.single().reacted).isTrue()
    }

    @Test
    fun fetchPollVotersSupportsNestedRankedChoiceUserPayload() = runTest {
        server.enqueue(
            jsonResponse(
                """
                {
                  "voters": {
                    "option-a": [
                      {
                        "user": {
                          "id": 356,
                          "username": "baba",
                          "name": "Baba",
                          "avatar_template": "/user_avatar/linux.do/baba/{size}/1.png"
                        }
                      }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )

        val groups = client.fetchPollVoters(postId = 1001L, pollName = "frontend")

        assertThat(groups).hasSize(1)
        assertThat(groups.single().id).isEqualTo("option-a")
        assertThat(groups.single().users.single().username).isEqualTo("baba")
        assertThat(groups.single().users.single().displayName).isEqualTo("Baba")
    }

    @Test
    fun fetchCurrentUser_mapsHttp401ToSessionLoggedOutAndNotifiesCoordinator() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("content-type", "application/json")
                .setBody("{\"error\":\"unauthorized\"}"),
        )
        var invalidationReason: SessionExpiredReason? = null
        client.setSessionBoundary(
            contextProvider = { io.github.baba983.nativedo.core.session.SessionRequestContext(0, io.github.baba983.nativedo.core.session.SessionRequestAuth.Authenticated) },
            onSessionInvalidated = { reason, _ -> invalidationReason = reason },
            onLoginRequired = {},
        )

        val result = runCatching { client.fetchCurrentUser() }

        assertThat(result.exceptionOrNull()).isInstanceOf(SessionLoggedOutException::class.java)
        assertThat(invalidationReason).isEqualTo(SessionExpiredReason.ServerLoggedOut)
    }

    @Test
    fun anonymousAuthenticatedWriteMaps401ToLoginRequiredWithoutInvalidatingSession() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("content-type", "application/json")
                .setBody("{\"error\":\"unauthorized\"}"),
        )
        val context = io.github.baba983.nativedo.core.session.SessionRequestContext(
            generation = 7,
            auth = io.github.baba983.nativedo.core.session.SessionRequestAuth.Anonymous,
        )
        var invalidationReason: SessionExpiredReason? = null
        var loginRequiredCount = 0
        client.setSessionBoundary(
            contextProvider = { context },
            onSessionInvalidated = { reason, _ -> invalidationReason = reason },
            onLoginRequired = { loginRequiredCount += 1 },
        )

        val result = runCatching {
            client.createPost(topicId = 42L, raw = "reply", replyToPostNumber = null)
        }

        assertThat(result.exceptionOrNull()).isInstanceOf(
            io.github.baba983.nativedo.core.session.LoginRequiredException::class.java,
        )
        assertThat(invalidationReason).isNull()
        assertThat(loginRequiredCount).isEqualTo(1)
    }

    @Test
    fun forbiddenResponsesDistinguishChallengeCsrfAndPermissionWithoutInvalidation() = runTest {
        val cases = listOf(
            MockResponse().setResponseCode(403).setHeader("server", "cloudflare")
                .setHeader("content-type", "application/json").setBody("{\"error_type\":\"invalid_access\"}") to "请求被拒绝",
            MockResponse().setResponseCode(403).setBody("[\"BAD CSRF\"]") to "请求验证已过期",
            MockResponse().setResponseCode(403).setHeader("cf-mitigated", "challenge")
                .setHeader("discourse-logged-out", "true").setBody("<html>challenge</html>") to "登录状态暂时无法验证",
        )
        var invalidated = false
        client.setSessionBoundary(
            contextProvider = { io.github.baba983.nativedo.core.session.SessionRequestContext(
                1, io.github.baba983.nativedo.core.session.SessionRequestAuth.Authenticated) },
            onSessionInvalidated = { _, _ -> invalidated = true }, onLoginRequired = {})
        for ((response, message) in cases) {
            server.enqueue(response)
            val error = runCatching { client.fetchCurrentUser() }.exceptionOrNull()
            assertThat(error?.message).contains(message)
            assertThat(invalidated).isFalse()
        }
    }

    @Test
    fun currentSession404CanRecoverThroughHomepageWithoutInvalidation() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html")
            .setBody("<meta name=\"current-username\" content=\"baba\">"))
        val cookies = io.github.baba983.nativedo.core.storage.CookieStore().apply {
            save(server.url("/"), listOf(okhttp3.Cookie.Builder().name("_t").value("token")
                .hostOnlyDomain(server.url("/").host).path("/").build()))
        }
        val coordinator = io.github.baba983.nativedo.core.session.SessionCoordinator(
            cookies, io.github.baba983.nativedo.core.storage.SessionStore(), client)
        client.setSessionBoundary(coordinator::currentRequestContext,
            coordinator::markExpiredIfCurrent, coordinator::requestLogin)
        coordinator.start()
        assertThat(coordinator.sessionState.value)
            .isInstanceOf(io.github.baba983.nativedo.core.session.AppSessionState.Authenticated::class.java)
        assertThat(cookies.hasAuthenticatedCookies()).isTrue()
    }

    @Test
    fun incompleteCurrentUserPayloadRequiresRecoveryWithoutClearingCookies() = runTest {
        for (body in listOf("{}", "{\"current_user\":{}}", "{\"current_user\":{\"username\":\" \"}}")) {
            server.enqueue(jsonResponse(body))
            var invalidated = false
            client.setSessionBoundary(
                contextProvider = { io.github.baba983.nativedo.core.session.SessionRequestContext(
                    1, io.github.baba983.nativedo.core.session.SessionRequestAuth.Restoring) },
                onSessionInvalidated = { _, _ -> invalidated = true },
                onLoginRequired = {},
            )
            assertThat(runCatching { client.fetchCurrentUser() }.exceptionOrNull())
                .isInstanceOf(SessionRecoveryRequiredException::class.java)
            assertThat(invalidated).isFalse()
        }
    }

    @Test
    fun fetchCurrentUser_mapsNullCurrentUserToSessionLoggedOut() = runTest {
        server.enqueue(jsonResponse("{\"current_user\":null}"))
        var invalidationReason: SessionExpiredReason? = null
        client.setSessionBoundary(
            contextProvider = { io.github.baba983.nativedo.core.session.SessionRequestContext(0, io.github.baba983.nativedo.core.session.SessionRequestAuth.Authenticated) },
            onSessionInvalidated = { reason, _ -> invalidationReason = reason },
            onLoginRequired = {},
        )

        val result = runCatching { client.fetchCurrentUser() }

        assertThat(result.exceptionOrNull()).isInstanceOf(SessionLoggedOutException::class.java)
        assertThat(invalidationReason).isEqualTo(SessionExpiredReason.ServerLoggedOut)
    }

    @Test
    fun fetchCurrentUser_mapsCloudflareChallengeToRecoveryRequiredWithoutInvalidatingSession() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("server", "cloudflare")
                .setHeader("content-type", "text/html; charset=UTF-8")
                .setBody("<html><title>Just a moment...</title><body>challenge-platform</body></html>"),
        )
        var invalidationReason: SessionExpiredReason? = null
        client.setSessionBoundary(
            contextProvider = { io.github.baba983.nativedo.core.session.SessionRequestContext(0, io.github.baba983.nativedo.core.session.SessionRequestAuth.Authenticated) },
            onSessionInvalidated = { reason, _ -> invalidationReason = reason },
            onLoginRequired = {},
        )

        val result = runCatching { client.fetchCurrentUser() }

        assertThat(result.exceptionOrNull()).isInstanceOf(SessionRecoveryRequiredException::class.java)
        assertThat(invalidationReason).isNull()
    }

    @Test
    fun fetchCurrentUsername_mapsDiscourseLoggedOutHeaderToSessionLoggedOutException() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("discourse-logged-out", "true")
                .setHeader("content-type", "application/json")
                .setBody("""{"error_type":"not_logged_in"}"""),
        )

        val result = runCatching {
            client.fetchCurrentUsername()
        }

        assertThat(result.exceptionOrNull()).isInstanceOf(SessionLoggedOutException::class.java)
    }

    @Test
    fun fetchPollVotersSupportsPerOptionPagination() = runTest {
        server.enqueue(jsonResponse(fixture("discourse/poll-voters.json")))

        val groups = client.fetchPollVoters(
            postId = 1001L,
            pollName = "frontend",
            optionId = "e539a9df8700d0d05c69356a07b768cf",
            page = 2,
            limit = 50,
        )

        assertThat(groups.single().users).hasSize(2)
        assertThat(server.takeRequest().path).isEqualTo(
            "/polls/voters.json?post_id=1001&poll_name=frontend&option_id=e539a9df8700d0d05c69356a07b768cf&page=2&limit=50",
        )
    }

    @Test
    fun parseBootstrap_readsCurrentUserFromPreloadedData() {
        val bootstrap = BootstrapHtmlParser.parse(fixture("discourse/home-bootstrap.html"))

        assertThat(bootstrap.currentUsername).isEqualTo("baba")
    }

    @Test
    fun parseBootstrap_readsCurrentUsernameMeta() {
        val bootstrap = BootstrapHtmlParser.parse(
            """
            <html>
              <head><meta name="current-username" content="baba" /></head>
            </html>
            """.trimIndent(),
        )

        assertThat(bootstrap.currentUsername).isEqualTo("baba")
    }

    private fun jsonResponse(body: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("content-type", "application/json")
            .setBody(body)

    private fun fixture(path: String): String =
        File("../../../fixtures/$path").readText()
}
