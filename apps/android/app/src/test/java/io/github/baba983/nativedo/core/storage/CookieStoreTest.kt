package io.github.baba983.nativedo.core.storage

import com.google.common.truth.Truth.assertThat
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

class CookieStoreTest {
    private val baseUrl = "https://linux.do".toHttpUrl()
    private val cdnUrl = "https://cdn.linux.do".toHttpUrl()

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
    fun hasAuthenticatedCookies_ignoresForumSessionWithoutTToken() {
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

        assertThat(store.hasAuthenticatedCookies()).isFalse()
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

    @Test
    fun load_domainCookieMatchesSubdomainRequest() {
        val store = CookieStore()

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

        assertThat(store.load(cdnUrl).map { it.name }).containsExactly("_t")
    }

    @Test
    fun load_hostOnlyCookieDoesNotLeakToSubdomain() {
        val store = CookieStore()

        store.save(
            url = baseUrl,
            cookies = listOf(
                Cookie.Builder()
                    .name("_t")
                    .value("auth")
                    .hostOnlyDomain("linux.do")
                    .path("/")
                    .build(),
            ),
        )

        assertThat(store.load(cdnUrl)).isEmpty()
    }

    @Test
    fun save_persistsCookiesAcrossInstances() {
        val persistence = InMemoryCookieStorePersistence()
        val firstStore = CookieStore(persistence = persistence)

        firstStore.save(
            url = baseUrl,
            cookies = listOf(
                Cookie.Builder()
                    .name("_forum_session")
                    .value("session")
                    .domain("linux.do")
                    .path("/")
                    .build(),
                Cookie.Builder()
                    .name("cf_clearance")
                    .value("clearance")
                    .domain("linux.do")
                    .path("/")
                    .build(),
            ),
        )

        val secondStore = CookieStore(persistence = persistence)

        assertThat(secondStore.load(baseUrl).map { it.name })
            .containsExactly("_forum_session", "cf_clearance")
    }
}

private class InMemoryCookieStorePersistence : CookieStorePersistence {
    private var snapshot: Map<String, List<String>> = emptyMap()

    override fun load(): Map<String, List<String>> = snapshot

    override fun save(snapshot: Map<String, List<String>>) {
        this.snapshot = snapshot
    }
}
