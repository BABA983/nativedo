package io.github.baba983.nativedo.core.storage

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

class CookieStoreTest {
    private val siteUrl = "https://linux.do/".toHttpUrl()

    @Test
    fun expiredSetCookieRemovesThePreviousValue() {
        val store = CookieStore()
        store.save(
            siteUrl,
            listOf(
                Cookie.Builder()
                    .name("_t")
                    .value("live-token")
                    .domain("linux.do")
                    .path("/")
                    .build(),
            ),
        )

        store.save(
            siteUrl,
            listOf(
                Cookie.Builder()
                    .name("_t")
                    .value("")
                    .domain("linux.do")
                    .path("/")
                    .expiresAt(System.currentTimeMillis() - 1)
                    .build(),
            ),
        )

        assertThat(store.hasAuthenticatedCookies()).isFalse()
        assertThat(store.load(siteUrl)).isEmpty()
    }

    @Test
    fun saveReplacesOnlyTheSameCookieIdentity() {
        val store = CookieStore()
        store.save(
            siteUrl,
            listOf(
                Cookie.Builder().name("_t").value("token").domain("linux.do").path("/").build(),
                Cookie.Builder().name("cf_clearance").value("clear").domain("linux.do").path("/").build(),
            ),
        )

        store.save(
            siteUrl,
            listOf(Cookie.Builder().name("_t").value("new-token").domain("linux.do").path("/").build()),
        )

        assertThat(store.load(siteUrl).associate { it.name to it.value }).containsExactly(
            "_t", "new-token",
            "cf_clearance", "clear",
        )
    }

    @Test
    fun concurrentResponsesDoNotOverwriteEachOthersCookies() {
        val store = CookieStore()
        val executor = Executors.newFixedThreadPool(8)

        repeat(200) { index ->
            executor.execute {
                store.save(
                    siteUrl,
                    listOf(
                        Cookie.Builder()
                            .name("response_cookie_$index")
                            .value("value_$index")
                            .domain("linux.do")
                            .path("/")
                            .build(),
                    ),
                )
            }
        }
        executor.shutdown()

        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue()
        assertThat(store.load(siteUrl)).hasSize(200)
    }
}
