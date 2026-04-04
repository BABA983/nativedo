package io.github.baba983.nativedo.core.network

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.di.SessionInvalidationGate
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.session.SessionExpiredReason
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class DiscourseApiClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun latestTopics_mapsIntoTopicSummaryList() = runTest {
        val payload = checkNotNull(
            javaClass.classLoader?.getResource("discourse/latest-topics.json"),
        ).readText()
        server.enqueue(
            MockResponse()
                .setBody(payload)
                .setHeader("Content-Type", "application/json"),
        )

        val client = AppContainer(baseUrl = server.url("/").toString()).discourseApiClient
        val topics = client.fetchLatest(page = 0)

        assertThat(topics).isNotEmpty()
        assertThat(topics.first().title).isNotEmpty()
    }

    @Test
    fun fetchLatestPage_mapsMoreTopicsUrl() = runTest {
        val payload = checkNotNull(
            javaClass.classLoader?.getResource("discourse/latest-topics.json"),
        ).readText()
        server.enqueue(
            MockResponse()
                .setBody(payload)
                .setHeader("Content-Type", "application/json"),
        )

        val client = AppContainer(baseUrl = server.url("/").toString()).discourseApiClient
        val page = client.fetchLatestPage(page = 0)

        assertThat(page.topics).hasSize(1)
        assertThat(page.moreTopicsUrl).isEqualTo("/latest.json?page=1")
    }

    @Test
    fun latestTopics_sendsBrowserLikeXhrHeaders() = runTest {
        val payload = checkNotNull(
            javaClass.classLoader?.getResource("discourse/latest-topics.json"),
        ).readText()
        server.enqueue(
            MockResponse()
                .setBody(payload)
                .setHeader("Content-Type", "application/json"),
        )

        val baseUrl = server.url("/").toString()
        val client = AppContainer(baseUrl = baseUrl).discourseApiClient

        client.fetchLatest(page = 0)

        val request = checkNotNull(server.takeRequest())
        assertThat(request.getHeader("Accept"))
            .isEqualTo("application/json;q=0.9, text/plain;q=0.8, */*;q=0.5")
        assertThat(request.getHeader("Accept-Language"))
            .isEqualTo("zh-CN,zh;q=0.9,en;q=0.8")
        assertThat(request.getHeader("X-Requested-With")).isEqualTo("XMLHttpRequest")
        assertThat(request.getHeader("Origin")).isEqualTo(server.url("/").toString().removeSuffix("/"))
        assertThat(request.getHeader("Referer")).isEqualTo(baseUrl)
        assertThat(request.getHeader("User-Agent"))
            .contains("Mozilla/5.0")
    }

    @Test
    fun latestTopics_usesConfiguredUserAgent() = runTest {
        val payload = checkNotNull(
            javaClass.classLoader?.getResource("discourse/latest-topics.json"),
        ).readText()
        server.enqueue(
            MockResponse()
                .setBody(payload)
                .setHeader("Content-Type", "application/json"),
        )

        val client = AppContainer(
            baseUrl = server.url("/").toString(),
            userAgent = "NativeDo-Test-UA/1.0",
        ).discourseApiClient

        client.fetchLatest(page = 0)

        val request = checkNotNull(server.takeRequest())
        assertThat(request.getHeader("User-Agent")).isEqualTo("NativeDo-Test-UA/1.0")
    }

    @Test
    fun fetchBootstrap_parsesCurrentUserAndCsrfTokenFromHtml() = runTest {
        val payload = checkNotNull(
            javaClass.classLoader?.getResource("discourse/home-bootstrap.html"),
        ).readText()
        server.enqueue(
            MockResponse()
                .setBody(payload)
                .setHeader("Content-Type", "text/html"),
        )

        val client = AppContainer(baseUrl = server.url("/").toString()).discourseApiClient
        val bootstrap = client.fetchBootstrap()

        assertThat(bootstrap.currentUsername).isEqualTo("baba")
        assertThat(bootstrap.csrfToken).isEqualTo("csrf-token-demo")
    }

    @Test
    fun fetchCurrentUsername_readsSessionCurrentPayload() = runTest {
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    {
                      "current_user": {
                        "id": 1,
                        "username": "baba",
                        "name": "Baba"
                      }
                    }
                    """.trimIndent(),
                )
                .setHeader("Content-Type", "application/json"),
        )

        val client = AppContainer(baseUrl = server.url("/").toString()).discourseApiClient
        val username = client.fetchCurrentUsername()

        assertThat(username).isEqualTo("baba")
    }

    @Test
    fun fetchTopic_wrapsCloudflare403AsSessionRecoveryRequired() = runTest {
        var invalidatedReason: SessionExpiredReason? = null
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("Server", "cloudflare")
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody("<html><body><h1>Just a moment...</h1></body></html>"),
        )

        val client = AppContainer(
            baseUrl = server.url("/").toString(),
            onSessionInvalidatedForTest = { invalidatedReason = it },
        ).discourseApiClient

        val result = runCatching {
            client.fetchTopic(topicId = 42)
        }

        assertThat(result.exceptionOrNull())
            .isInstanceOf(SessionRecoveryRequiredException::class.java)
        assertThat(invalidatedReason).isNull()
    }

    @Test
    fun fetchTopic_discourseLoggedOutHeader_invokesSessionInvalidatedCallback() = runTest {
        var invalidatedReason: SessionExpiredReason? = null
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("discourse-logged-out", "true")
                .setHeader("Content-Type", "application/json")
                .setBody("""{"errors":["You are not permitted"],"error_type":"not_logged_in"}"""),
        )

        val client = AppContainer(
            baseUrl = server.url("/").toString(),
            onSessionInvalidatedForTest = { invalidatedReason = it },
        ).discourseApiClient

        val result = runCatching {
            client.fetchTopic(topicId = 42)
        }

        assertThat(result.exceptionOrNull())
            .isInstanceOf(SessionRecoveryRequiredException::class.java)
        assertThat(invalidatedReason).isEqualTo(SessionExpiredReason.ServerLoggedOut)
    }

    @Test
    fun fetchTopic_notLoggedInPayload_invokesSessionInvalidatedCallback() = runTest {
        var invalidatedReason: SessionExpiredReason? = null
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"errors":["You are not permitted"],"error_type":"not_logged_in"}"""),
        )

        val client = AppContainer(
            baseUrl = server.url("/").toString(),
            onSessionInvalidatedForTest = { invalidatedReason = it },
        ).discourseApiClient

        val result = runCatching {
            client.fetchTopic(topicId = 42)
        }

        assertThat(result.exceptionOrNull())
            .isInstanceOf(SessionRecoveryRequiredException::class.java)
        assertThat(invalidatedReason).isEqualTo(SessionExpiredReason.ServerLoggedOut)
    }

    @Test
    fun fetchTopic_plain403DoesNotInvokeSessionInvalidatedCallback() = runTest {
        var invalidatedReason: SessionExpiredReason? = null
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"errors":["Forbidden"],"error_type":"rate_limit"}"""),
        )

        val client = AppContainer(
            baseUrl = server.url("/").toString(),
            onSessionInvalidatedForTest = { invalidatedReason = it },
        ).discourseApiClient

        val result = runCatching {
            client.fetchTopic(topicId = 42)
        }

        assertThat(result.exceptionOrNull()).isInstanceOf(retrofit2.HttpException::class.java)
        assertThat(invalidatedReason).isNull()
    }

    @Test
    fun sessionInvalidationGate_coalescesConcurrentDuplicateInvalidations() = runTest {
        var state: AppSessionState = AppSessionState.Unknown
        var invalidationCalls = 0
        val gate = SessionInvalidationGate(
            currentSessionState = { state },
            markExpired = { reason ->
                invalidationCalls += 1
                state = AppSessionState.Expired(reason = reason)
            },
        )

        val first = backgroundScope.async {
            gate.invalidate(SessionExpiredReason.ServerLoggedOut)
        }
        val second = backgroundScope.async {
            gate.invalidate(SessionExpiredReason.ServerLoggedOut)
        }

        first.await()
        second.await()

        assertThat(invalidationCalls).isEqualTo(1)
        assertThat(state).isEqualTo(
            AppSessionState.Expired(reason = SessionExpiredReason.ServerLoggedOut),
        )
    }

    @Test
    fun latestTopics_mapsPosterAvatarTemplate() = runTest {
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    {
                      "topic_list": {
                        "topics": [
                          {
                            "id": 101,
                            "title": "Welcome",
                            "posters": [
                              {
                                "user": {
                                  "username": "baba",
                                  "avatar_template": "/user_avatar/linux.do/baba/{size}/1.png"
                                }
                              }
                            ]
                          }
                        ]
                      }
                    }
                    """.trimIndent(),
                )
                .setHeader("Content-Type", "application/json"),
        )

        val client = AppContainer(baseUrl = server.url("/").toString()).discourseApiClient
        val topics = client.fetchLatest(page = 0)

        assertThat(topics.single().authorAvatarTemplate)
            .isEqualTo("/user_avatar/linux.do/baba/{size}/1.png")
    }

    @Test
    fun latestTopics_mapsPosterFromTopicListUsersAndImageUrl() = runTest {
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    {
                      "users": [
                        {
                          "id": 7,
                          "username": "baba",
                          "avatar_template": "/user_avatar/linux.do/baba/{size}/1.png"
                        }
                      ],
                      "topic_list": {
                        "topics": [
                          {
                            "id": 101,
                            "title": "Welcome",
                            "image_url": "https://linux.do/uploads/default/original/1X/topic-cover.png",
                            "posters": [
                              {
                                "user_id": 7
                              }
                            ]
                          }
                        ]
                      }
                    }
                    """.trimIndent(),
                )
                .setHeader("Content-Type", "application/json"),
        )

        val client = AppContainer(baseUrl = server.url("/").toString()).discourseApiClient
        val topic = client.fetchLatest(page = 0).single()

        assertThat(topic.authorUsername).isEqualTo("baba")
        assertThat(topic.authorAvatarTemplate)
            .isEqualTo("/user_avatar/linux.do/baba/{size}/1.png")
        assertThat(topic.imageUrl)
            .isEqualTo("https://linux.do/uploads/default/original/1X/topic-cover.png")
    }

    @Test
    fun latestTopics_prefersOriginalPosterWhenMultiplePostersExist() = runTest {
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    {
                      "users": [
                        {
                          "id": 7,
                          "username": "baba",
                          "avatar_template": "/user_avatar/linux.do/baba/{size}/1.png"
                        },
                        {
                          "id": 8,
                          "username": "alice",
                          "avatar_template": "/user_avatar/linux.do/alice/{size}/1.png"
                        }
                      ],
                      "topic_list": {
                        "topics": [
                          {
                            "id": 101,
                            "title": "Welcome",
                            "posters": [
                              {
                                "description": "Most Recent Poster",
                                "user_id": 8
                              },
                              {
                                "description": "Original Poster",
                                "user_id": 7
                              }
                            ]
                          }
                        ]
                      }
                    }
                    """.trimIndent(),
                )
                .setHeader("Content-Type", "application/json"),
        )

        val client = AppContainer(baseUrl = server.url("/").toString()).discourseApiClient
        val topic = client.fetchLatest(page = 0).single()

        assertThat(topic.authorUsername).isEqualTo("baba")
        assertThat(topic.authorAvatarTemplate)
            .isEqualTo("/user_avatar/linux.do/baba/{size}/1.png")
    }

    @Test
    fun fetchTopic_mapsPostAvatarTemplate() = runTest {
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    {
                      "id": 101,
                      "title": "Welcome",
                      "post_stream": {
                        "posts": [
                          {
                            "id": 1001,
                            "username": "baba",
                            "avatar_template": "/user_avatar/linux.do/baba/{size}/1.png",
                            "cooked": "<p>Hello</p>"
                          }
                        ]
                      }
                    }
                    """.trimIndent(),
                )
                .setHeader("Content-Type", "application/json"),
        )

        val client = AppContainer(baseUrl = server.url("/").toString()).discourseApiClient
        val topic = client.fetchTopic(topicId = 101)

        assertThat(topic.posts.single().authorAvatarTemplate)
            .isEqualTo("/user_avatar/linux.do/baba/{size}/1.png")
    }

    @Test
    fun fetchTopic_mapsTotalPostsAndStreamIds() = runTest {
        val payload = checkNotNull(
            javaClass.classLoader?.getResource("discourse/topic-detail.json"),
        ).readText()
        server.enqueue(
            MockResponse()
                .setBody(payload)
                .setHeader("Content-Type", "application/json"),
        )

        val client = AppContainer(baseUrl = server.url("/").toString()).discourseApiClient
        val topic = client.fetchTopic(topicId = 101)

        assertThat(topic.totalPosts).isEqualTo(3)
        assertThat(topic.streamPostIds).containsExactly(1001L, 1002L, 1003L).inOrder()
    }

    @Test
    fun fetchTopicPosts_mapsChunkPosts() = runTest {
        val payload = checkNotNull(
            javaClass.classLoader?.getResource("discourse/topic-posts.json"),
        ).readText()
        server.enqueue(
            MockResponse()
                .setBody(payload)
                .setHeader("Content-Type", "application/json"),
        )

        val client = AppContainer(baseUrl = server.url("/").toString()).discourseApiClient
        val posts = client.fetchTopicPosts(topicId = 101, postIds = listOf(1002L, 1003L))

        assertThat(posts).hasSize(2)
        assertThat(posts.map { it.id }).containsExactly(1002L, 1003L).inOrder()
        assertThat(posts.map { it.authorAvatarTemplate }).containsExactly(
            "/user_avatar/linux.do/alice/{size}/1.png",
            "/user_avatar/linux.do/charlie/{size}/1.png",
        ).inOrder()
    }

    @Test
    fun search_mapsUserAvatarTemplate() = runTest {
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    {
                      "topics": [],
                      "users": [
                        {
                          "username": "searcher",
                          "name": "Searcher",
                          "avatar_template": "/user_avatar/linux.do/searcher/{size}/1.png"
                        }
                      ]
                    }
                    """.trimIndent(),
                )
                .setHeader("Content-Type", "application/json"),
        )

        val client = AppContainer(baseUrl = server.url("/").toString()).discourseApiClient
        val result = client.search(query = "compose")

        assertThat(result.users.single().avatarTemplate)
            .isEqualTo("/user_avatar/linux.do/searcher/{size}/1.png")
    }

    @Test
    fun search_mapsTopicPosterFromUsers() = runTest {
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    {
                      "topics": [
                        {
                          "id": 101,
                          "title": "Compose",
                          "posters": [
                            {
                              "user_id": 9
                            }
                          ]
                        }
                      ],
                      "users": [
                        {
                          "id": 9,
                          "username": "searcher",
                          "name": "Searcher",
                          "avatar_template": "/user_avatar/linux.do/searcher/{size}/1.png"
                        }
                      ]
                    }
                    """.trimIndent(),
                )
                .setHeader("Content-Type", "application/json"),
        )

        val client = AppContainer(baseUrl = server.url("/").toString()).discourseApiClient
        val result = client.search(query = "compose")

        assertThat(result.topics.single().authorUsername).isEqualTo("searcher")
        assertThat(result.topics.single().authorAvatarTemplate)
            .isEqualTo("/user_avatar/linux.do/searcher/{size}/1.png")
    }

    @Test
    fun latestTopics_mapsPosterFromRootUsers() = runTest {
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    {
                      "users": [
                        {
                          "id": 7,
                          "username": "baba",
                          "avatar_template": "/user_avatar/linux.do/baba/{size}/1.png"
                        }
                      ],
                      "topic_list": {
                        "topics": [
                          {
                            "id": 101,
                            "title": "Welcome",
                            "posters": [
                              {
                                "description": "Original Poster",
                                "user_id": 7
                              }
                            ]
                          }
                        ]
                      }
                    }
                    """.trimIndent(),
                )
                .setHeader("Content-Type", "application/json"),
        )

        val client = AppContainer(baseUrl = server.url("/").toString()).discourseApiClient
        val topic = client.fetchLatest(page = 0).single()

        assertThat(topic.authorUsername).isEqualTo("baba")
        assertThat(topic.authorAvatarTemplate)
            .isEqualTo("/user_avatar/linux.do/baba/{size}/1.png")
    }
}
