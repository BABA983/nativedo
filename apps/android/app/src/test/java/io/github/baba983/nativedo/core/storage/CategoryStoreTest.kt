package io.github.baba983.nativedo.core.storage

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.di.AppContainer
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class CategoryStoreTest {
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
    fun loadIfNeeded_cachesLoadedCategories() = runTest {
        val payload = checkNotNull(
            javaClass.classLoader?.getResource("discourse/site.json"),
        ).readText()
        server.enqueue(
            MockResponse()
                .setBody(payload)
                .setHeader("Content-Type", "application/json"),
        )
        server.enqueue(
            MockResponse()
                .setBody("""{"categories":[]}""")
                .setHeader("Content-Type", "application/json"),
        )

        val appContainer = AppContainer(baseUrl = server.url("/").toString())
        val store = appContainer.categoryStore

        store.loadIfNeeded()
        store.loadIfNeeded()

        assertThat(store.categories.value[11]?.name).isEqualTo("搞七捻三")
        assertThat(server.requestCount).isEqualTo(1)
    }
}
