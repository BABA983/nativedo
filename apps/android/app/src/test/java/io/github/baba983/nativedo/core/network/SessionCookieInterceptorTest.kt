package io.github.baba983.nativedo.core.network

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.storage.CookieStore
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Test
import okio.buffer

class SessionCookieInterceptorTest {
    @Test
    fun gzipChallengeDoesNotDeleteAuthCookieAndBodyRemainsReadable() {
        MockWebServer().use { server ->
            val compressed = okio.Buffer()
            okio.GzipSink(compressed).buffer().use {
                it.writeUtf8("<html>Just a moment... challenge-platform</html>")
            }
            server.enqueue(MockResponse().setResponseCode(403)
                .setHeader("Content-Type", "text/html").setHeader("Content-Encoding", "gzip")
                .addHeader("Set-Cookie", "_t=; Max-Age=0; Path=/").setBody(compressed))
            server.start()
            val store = CookieStore()
            store.save(server.url("/"), listOf(Cookie.Builder().name("_t").value("valid")
                .hostOnlyDomain(server.url("/").host).path("/").build()))
            val client = OkHttpClient.Builder().addNetworkInterceptor(SessionCookieInterceptor(store)).build()
            client.newCall(Request.Builder().url(server.url("/")).build()).execute().use {
                assertThat(it.code).isEqualTo(403)
                assertThat(it.body!!.string()).contains("challenge-platform")
            }
            assertThat(store.load(server.url("/")).single().value).isEqualTo("valid")
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test
    fun oldResponseCannotDeleteNewLoginCookieOrReportLogout() {
        MockWebServer().use { server ->
            val release = CountDownLatch(1)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    check(release.await(5, TimeUnit.SECONDS))
                    return MockResponse().setResponseCode(401)
                        .addHeader("Set-Cookie", "_t=; Max-Age=0; Path=/")
                }
            }
            server.start()
            val url = server.url("/")
            val store = CookieStore()
            fun token(value: String) = Cookie.Builder().name("_t").value(value)
                .hostOnlyDomain(url.host).path("/").build()
            store.save(url, listOf(token("old")))
            val client = OkHttpClient.Builder().addNetworkInterceptor(SessionCookieInterceptor(store)).build()
            val executor = Executors.newSingleThreadExecutor()
            try {
                val result = executor.submit<Throwable?> {
                    runCatching { client.newCall(Request.Builder().url(url).build()).execute().use { it.code } }
                        .exceptionOrNull()
                }
                assertThat(server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("Cookie")).isEqualTo("_t=old")
                store.advanceRevision()
                store.save(url, listOf(token("new")))
                release.countDown()
                assertThat(result.get(5, TimeUnit.SECONDS)).isInstanceOf(IOException::class.java)
                assertThat(store.load(url).single().value).isEqualTo("new")
            } finally {
                release.countDown()
                executor.shutdownNow()
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdown()
            }
        }
    }

    @Test
    fun fencedReadIsReturnedWhenARotationWonTheRace() {
        MockWebServer().use { server ->
            val release = CountDownLatch(1)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    check(release.await(5, TimeUnit.SECONDS))
                    return MockResponse().setResponseCode(200).setBody("""{"topic_list":{}}""")
                }
            }
            server.start()
            val url = server.url("/")
            val store = CookieStore()
            fun token(value: String) = Cookie.Builder().name("_t").value(value)
                .hostOnlyDomain(url.host).path("/").build()
            store.save(url, listOf(token("old")))
            val client = OkHttpClient.Builder().addNetworkInterceptor(SessionCookieInterceptor(store)).build()
            val executor = Executors.newSingleThreadExecutor()
            try {
                val result = executor.submit<Pair<Int, String>?> {
                    client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                        response.code to response.body!!.string()
                    }
                }
                assertThat(server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("Cookie")).isEqualTo("_t=old")
                // A parallel response rotates `_t` while this read is in flight.
                store.advanceRevision()
                store.save(url, listOf(token("rotated")))
                release.countDown()

                // The payload is still valid content: fencing drops the stale cookie write, not the
                // response. Throwing here is what surfaced as "Couldn't load topics".
                val (code, body) = result.get(5, TimeUnit.SECONDS)!!
                assertThat(code).isEqualTo(200)
                assertThat(body).contains("topic_list")
                assertThat(store.load(url).single().value).isEqualTo("rotated")
            } finally {
                release.countDown()
                executor.shutdownNow()
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdown()
            }
        }
    }

    @Test
    fun redirectUsesRotatedCookieAndLogoutCannotResurrectOldResponse() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/next")
                .addHeader("Set-Cookie", "_t=rotated; Path=/"))
            server.enqueue(MockResponse().setBody("ok"))
            server.start()
            val store = CookieStore()
            val client = OkHttpClient.Builder().addNetworkInterceptor(SessionCookieInterceptor(store)).build()
            client.newCall(Request.Builder().url(server.url("/")).build()).execute().use {
                assertThat(it.code).isEqualTo(200)
            }
            assertThat(server.takeRequest().getHeader("Cookie")).isNull()
            assertThat(server.takeRequest().getHeader("Cookie")).isEqualTo("_t=rotated")
            val revision = store.revision()
            val oldCookies = store.load(server.url("/"))
            store.clear()
            assertThat(store.saveIfCurrent(server.url("/"), oldCookies, revision)).isFalse()
            assertThat(store.hasAuthenticatedCookies()).isFalse()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }
}
