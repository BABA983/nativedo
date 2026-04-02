package io.github.baba983.nativedo.core.network

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.di.AppContainer
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
}
