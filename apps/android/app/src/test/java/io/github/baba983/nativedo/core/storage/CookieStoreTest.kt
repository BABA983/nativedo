package io.github.baba983.nativedo.core.storage

import com.google.common.truth.Truth.assertThat
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

class CookieStoreTest {
    private val baseUrl = "https://linux.do".toHttpUrl()

    @Test
    fun hasSessionCookies_ignoresNonSessionCookies() {
        val store = CookieStore()

        store.save(
            url = baseUrl,
            cookies = listOf(
                Cookie.Builder()
                    .name("cf_clearance")
                    .value("clearance")
                    .domain("linux.do")
                    .path("/")
                    .build(),
            ),
        )

        assertThat(store.hasSessionCookies()).isFalse()
    }

    @Test
    fun save_mergesCookiesForSameHost() {
        val store = CookieStore()

        store.save(
            url = baseUrl,
            cookies = listOf(
                Cookie.Builder()
                    .name("_forum_session")
                    .value("session")
                    .domain("linux.do")
                    .path("/")
                    .build(),
            ),
        )
        store.save(
            url = baseUrl,
            cookies = listOf(
                Cookie.Builder()
                    .name("_t")
                    .value("auth")
                    .domain("linux.do")
                    .path("/")
                    .build(),
            ),
        )

        assertThat(store.hasSessionCookies()).isTrue()
        assertThat(store.load(baseUrl).map { it.name }).containsExactly("_forum_session", "_t")
    }
}
