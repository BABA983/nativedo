package io.github.baba983.nativedo.core.storage

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.network.DiscourseApiClient
import io.github.baba983.nativedo.core.network.DiscourseApiService
import io.github.baba983.nativedo.core.network.NetworkJson
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

class CategoryStoreTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun loadIfNeededFetchesAndCachesCategories() = runTest {
        server.enqueue(jsonResponse(fixture("discourse/site.json")))
        server.enqueue(jsonResponse("""{"categories":[]}"""))
        val store = CategoryStore(client())

        val first = store.loadIfNeeded().getOrThrow()
        val second = store.loadIfNeeded().getOrThrow()

        assertThat(first[11]?.name).isEqualTo("搞七捻三")
        assertThat(second).isSameInstanceAs(first)
        assertThat(server.requestCount).isEqualTo(1)
    }

    private fun client(): DiscourseApiClient {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(NetworkJson.instance.asConverterFactory("application/json".toMediaType()))
            .build()
        return DiscourseApiClient(retrofit.create(DiscourseApiService::class.java))
    }

    private fun fixture(path: String): String {
        val url = checkNotNull(javaClass.classLoader?.getResource(path)) { "Missing fixture $path" }
        return url.readText()
    }

    private fun jsonResponse(body: String): MockResponse =
        MockResponse()
            .setBody(body)
            .setHeader("Content-Type", "application/json")
}
