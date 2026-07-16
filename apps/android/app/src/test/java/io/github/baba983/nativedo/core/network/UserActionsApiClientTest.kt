package io.github.baba983.nativedo.core.network

import com.google.common.truth.Truth.assertThat
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class UserActionsApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: DiscourseApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = DiscourseApiClient(
            Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(NetworkJson.instance.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(DiscourseApiService::class.java),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun mapsReplyPostNumberAndUsesRequestedPage() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "user_actions": [
                        {
                          "id": 7001,
                          "post_id": 9001,
                          "topic_id": 101,
                          "title": "A reply target",
                          "excerpt": "reply excerpt",
                          "username": "baba",
                          "avatar_template": "/user_avatar/linux.do/baba/{size}/1.png",
                          "created_at": "2026-07-11T08:00:00.000Z",
                          "category_id": 2,
                          "slug": "a-reply-target",
                          "post_number": 37
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
        )

        val actions = client.fetchUserActions(
            username = "baba",
            filter = "5",
            offset = 20,
            limit = 20,
        )

        assertThat(actions.rawCount).isEqualTo(1)
        assertThat(actions.items.single().id).isEqualTo(101L)
        assertThat(actions.items.single().targetPostNumber).isEqualTo(37)
        assertThat(actions.items.single().targetPostId).isEqualTo(9001L)
        assertThat(actions.items.single().categoryId).isEqualTo(2)
        assertThat(actions.items.single().rowIdentity).isEqualTo("post:9001")
        assertThat(server.takeRequest().path)
            .isEqualTo("/user_actions.json?username=baba&filter=5&offset=20&limit=20")
    }
}
