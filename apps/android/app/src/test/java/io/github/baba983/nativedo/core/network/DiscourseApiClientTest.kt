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
}
