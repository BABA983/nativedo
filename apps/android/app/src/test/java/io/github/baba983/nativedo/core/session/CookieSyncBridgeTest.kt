package io.github.baba983.nativedo.core.session

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.storage.CookieStore
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

class CookieSyncBridgeTest {

    private val baseUrl = "https://linux.do/"

    @Test
    fun syncToWebView_writesCookiesFromStoreToWebView() {
        val cookieStore = CookieStore()
        val writtenCookies = mutableListOf<Pair<String, String>>()
        val bridge = CookieSyncBridge(
            baseUrl = baseUrl,
            cookieStore = cookieStore,
            readCookieHeader = { null },
            writeCookieToWebView = { url, cookie -> writtenCookies.add(url to cookie) },
            flushCookies = {},
            clearCookies = {},
        )

        val cookies = listOf(
            Cookie.Builder()
                .name("_t")
                .value("auth_token")
                .domain("linux.do")
                .path("/")
                .build(),
            Cookie.Builder()
                .name("_forum_session")
                .value("session_value")
                .domain("linux.do")
                .path("/")
                .build(),
        )
        cookieStore.save(baseUrl.toHttpUrl(), cookies)

        bridge.syncToWebView()

        assertThat(writtenCookies).hasSize(2)
        assertThat(writtenCookies.map { it.first }).containsExactly(baseUrl, baseUrl)
        assertThat(writtenCookies.map { it.second }).containsExactly(
            cookies[0].toString(),
            cookies[1].toString(),
        )
    }

    @Test
    fun syncToWebView_flushesAfterWriting() {
        val cookieStore = CookieStore()
        var flushed = false
        val bridge = CookieSyncBridge(
            baseUrl = baseUrl,
            cookieStore = cookieStore,
            readCookieHeader = { null },
            writeCookieToWebView = { _, _ -> },
            flushCookies = { flushed = true },
            clearCookies = {},
        )

        cookieStore.save(
            baseUrl.toHttpUrl(),
            listOf(
                Cookie.Builder()
                    .name("_t")
                    .value("auth_token")
                    .domain("linux.do")
                    .path("/")
                    .build(),
            ),
        )

        bridge.syncToWebView()

        assertThat(flushed).isTrue()
    }

    @Test
    fun syncFromWebView_writesCookiesToStore() {
        val cookieStore = CookieStore()
        val bridge = CookieSyncBridge(
            baseUrl = baseUrl,
            cookieStore = cookieStore,
            readCookieHeader = { "_t=auth_token; _forum_session=session_value" },
            writeCookieToWebView = { _, _ -> },
            flushCookies = {},
            clearCookies = {},
        )

        val result = bridge.syncFromWebView()

        assertThat(result).isTrue()
        val loaded = cookieStore.load(baseUrl.toHttpUrl())
        assertThat(loaded.map { it.name }).containsExactly("_t", "_forum_session")
    }

    @Test
    fun syncToWebView_emptyStore_doesNotWriteOrFlush() {
        val cookieStore = CookieStore()
        val writtenCookies = mutableListOf<Pair<String, String>>()
        var flushed = false
        val bridge = CookieSyncBridge(
            baseUrl = baseUrl,
            cookieStore = cookieStore,
            readCookieHeader = { null },
            writeCookieToWebView = { url, cookie -> writtenCookies.add(url to cookie) },
            flushCookies = { flushed = true },
            clearCookies = {},
        )

        bridge.syncToWebView()

        assertThat(writtenCookies).isEmpty()
        assertThat(flushed).isFalse()
    }
}
