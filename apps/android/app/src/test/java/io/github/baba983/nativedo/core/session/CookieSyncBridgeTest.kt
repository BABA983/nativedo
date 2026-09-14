package io.github.baba983.nativedo.core.session

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.storage.CookieStore
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class CookieSyncBridgeTest {
    private val baseUrl = "https://linux.do/"

    @Test
    fun browserRoundTripPreservesKnownAttributesAndDoesNotRewriteUnchangedCookie() = runTest {
        val store = CookieStore()
        val token = Cookie.Builder().name("_t").value("token").hostOnlyDomain("linux.do")
            .path("/").secure().httpOnly().expiresAt(System.currentTimeMillis() + 60000).build()
        store.save(baseUrl.toHttpUrl(), listOf(token))
        var writes = 0
        val bridge = CookieSyncBridge(baseUrl, store, readCookieHeader = { "_t=token" },
            flushCookies = {}, writeCookieToWebView = { _, _ -> writes++ })
        bridge.syncFromWebView()
        bridge.syncToWebView()
        assertThat(store.load(baseUrl.toHttpUrl()).single()).isEqualTo(token)
        assertThat(writes).isEqualTo(0)
    }

    @Test
    fun staleHiddenBrowserImportCannotOverwriteNativeRotation() = runTest {
        val store = CookieStore()
        val revision = store.revision()
        store.save(baseUrl.toHttpUrl(), listOf(Cookie.Builder().name("_t").value("new")
            .hostOnlyDomain("linux.do").path("/").build()))
        val bridge = CookieSyncBridge(baseUrl, store, readCookieHeader = { "_t=old" }, flushCookies = {})
        assertThat(bridge.syncFromWebViewIfCurrent(revision)).isFalse()
        assertThat(store.load(baseUrl.toHttpUrl()).single().value).isEqualTo("new")
    }

    @Test
    fun conflictingSameNameBrowserTokensRequireRecovery() = runTest {
        val store = CookieStore()
        val bridge = CookieSyncBridge(baseUrl, store,
            readCookieHeader = { "_t=one; _t=two" }, flushCookies = {})
        assertThat(runCatching { bridge.syncFromWebView() }.exceptionOrNull())
            .isInstanceOf(io.github.baba983.nativedo.core.network.SessionRecoveryRequiredException::class.java)
        assertThat(store.hasAuthenticatedCookies()).isFalse()
    }

    @Test
    fun syncToWebView_writesCookiesFromStoreToWebView() = runTest {
        val cookieStore = CookieStore()
        val writtenCookies = mutableListOf<Pair<String, String>>()
        val bridge = CookieSyncBridge(
            baseUrl = baseUrl,
            cookieStore = cookieStore,
            readCookieHeader = { null },
            writeCookieToWebView = { url, cookie -> writtenCookies.add(url to cookie) },
            flushCookies = {},
            clearCookies = { onComplete -> onComplete?.invoke() },
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
    fun syncToWebViewAsync_waitsForEverySetCookieCallbackBeforeFlushing() = runTest {
        val cookieStore = CookieStore()
        val callbacks = mutableListOf<(Boolean) -> Unit>()
        var flushCount = 0
        cookieStore.save(
            baseUrl.toHttpUrl(),
            listOf(
                Cookie.Builder().name("_t").value("token").domain("linux.do").path("/").build(),
                Cookie.Builder().name("cf_clearance").value("clearance").domain("linux.do").path("/").build(),
            ),
        )
        val bridge = CookieSyncBridge(
            baseUrl = baseUrl,
            cookieStore = cookieStore,
            readCookieHeader = { null },
            writeCookieToWebView = { _, _ -> },
            flushCookies = { flushCount += 1 },
            clearCookies = { onComplete -> onComplete?.invoke() },
            writeCookieToWebViewAsync = { _, _, callback -> callbacks += callback },
        )

        val sync = async { bridge.syncToWebViewAsync() }
        runCurrent()
        assertThat(sync.isCompleted).isFalse()
        callbacks.first().invoke(true)
        runCurrent()
        assertThat(sync.isCompleted).isFalse()
        callbacks.last().invoke(true)
        assertThat(sync.await()).isTrue()

        assertThat(flushCount).isEqualTo(1)
    }

    @Test
    fun syncToWebViewAsync_reportsRejectedCookieWithoutFlushing() = runTest {
        val cookieStore = CookieStore()
        var flushCount = 0
        cookieStore.save(
            baseUrl.toHttpUrl(),
            listOf(Cookie.Builder().name("_t").value("token").domain("linux.do").path("/").build()),
        )
        val bridge = CookieSyncBridge(
            baseUrl = baseUrl,
            cookieStore = cookieStore,
            readCookieHeader = { null },
            writeCookieToWebView = { _, _ -> },
            flushCookies = { flushCount += 1 },
            clearCookies = { onComplete -> onComplete?.invoke() },
            writeCookieToWebViewAsync = { _, _, callback -> callback(false) },
        )

        assertThat(bridge.syncToWebViewAsync()).isFalse()
        assertThat(flushCount).isEqualTo(0)
    }

    @Test
    fun syncToWebViewAsync_timesOutWhenWebViewDropsCallback() = runTest {
        val cookieStore = CookieStore()
        cookieStore.save(
            baseUrl.toHttpUrl(),
            listOf(Cookie.Builder().name("_t").value("token").domain("linux.do").path("/").build()),
        )
        val bridge = CookieSyncBridge(
            baseUrl = baseUrl,
            cookieStore = cookieStore,
            readCookieHeader = { null },
            writeCookieToWebView = { _, _ -> },
            flushCookies = {},
            clearCookies = { onComplete -> onComplete?.invoke() },
            writeCookieToWebViewAsync = { _, _, _ -> },
        )

        assertThat(bridge.syncToWebViewAsync(timeoutMillis = 1)).isFalse()
    }

    @Test
    fun syncFromWebView_writesCookiesToStore() = runTest {
        val cookieStore = CookieStore()
        val bridge = CookieSyncBridge(
            baseUrl = baseUrl,
            cookieStore = cookieStore,
            readCookieHeader = { "_t=auth_token; _forum_session=session_value" },
            writeCookieToWebView = { _, _ -> },
            flushCookies = {},
            clearCookies = { onComplete -> onComplete?.invoke() },
        )

        val result = bridge.syncFromWebView()

        assertThat(result).isTrue()
        assertThat(cookieStore.load(baseUrl.toHttpUrl()).map { it.name })
            .containsExactly("_t", "_forum_session")
    }

    @Test
    fun syncFromWebView_mergesVisibleCookiesWithoutTreatingMissingNamesAsDeletion() = runTest {
        val cookieStore = CookieStore()
        cookieStore.save(
            baseUrl.toHttpUrl(),
            listOf(
                Cookie.Builder()
                    .name("_t")
                    .value("stale_auth_token")
                    .domain("linux.do")
                    .path("/")
                    .build(),
            ),
        )
        val bridge = CookieSyncBridge(
            baseUrl = baseUrl,
            cookieStore = cookieStore,
            readCookieHeader = { "_forum_session=session_value" },
            writeCookieToWebView = { _, _ -> },
            flushCookies = {},
            clearCookies = { onComplete -> onComplete?.invoke() },
        )

        val result = bridge.syncFromWebView()

        assertThat(result).isTrue()
        assertThat(cookieStore.load(baseUrl.toHttpUrl()).map { it.name })
            .containsExactly("_t", "_forum_session")
    }

    @Test
    fun syncFromWebView_blankHeaderKeepsNativeCookiesUntilWebViewIsReady() = runTest {
        val cookieStore = CookieStore()
        cookieStore.save(
            baseUrl.toHttpUrl(),
            listOf(
                Cookie.Builder()
                    .name("_t")
                    .value("native_token")
                    .domain("linux.do")
                    .path("/")
                    .build(),
            ),
        )
        val bridge = CookieSyncBridge(
            baseUrl = baseUrl,
            cookieStore = cookieStore,
            readCookieHeader = { "" },
            writeCookieToWebView = { _, _ -> },
            flushCookies = {},
            clearCookies = { onComplete -> onComplete?.invoke() },
        )

        assertThat(bridge.syncFromWebView()).isTrue()
        assertThat(cookieStore.hasAuthenticatedCookies()).isTrue()
    }

    @Test
    fun syncFromWebView_nullHeaderKeepsNativeCookiesUntilWebViewIsReady() = runTest {
        val cookieStore = CookieStore()
        cookieStore.save(
            baseUrl.toHttpUrl(),
            listOf(
                Cookie.Builder()
                    .name("_t")
                    .value("native_token")
                    .domain("linux.do")
                    .path("/")
                    .build(),
            ),
        )
        val bridge = CookieSyncBridge(
            baseUrl = baseUrl,
            cookieStore = cookieStore,
            readCookieHeader = { null },
            writeCookieToWebView = { _, _ -> },
            flushCookies = {},
            clearCookies = { onComplete -> onComplete?.invoke() },
        )

        assertThat(bridge.syncFromWebView()).isTrue()
        assertThat(cookieStore.hasAuthenticatedCookies()).isTrue()
    }

    @Test
    fun hasAuthenticatedCookiesInWebView_readsNonEmptyTokenWithoutMutatingStore() {
        val cookieStore = CookieStore()
        val bridge = CookieSyncBridge(
            baseUrl = baseUrl,
            cookieStore = cookieStore,
            readCookieHeader = { "_forum_session=session_value; _t=auth_token" },
            writeCookieToWebView = { _, _ -> },
            flushCookies = {},
            clearCookies = { onComplete -> onComplete?.invoke() },
        )

        assertThat(bridge.hasAuthenticatedCookiesInWebView()).isTrue()
        assertThat(cookieStore.hasAuthenticatedCookies()).isFalse()
    }

    @Test
    fun hasAuthenticatedCookiesInWebView_ignoresEmptyToken() {
        val bridge = CookieSyncBridge(
            baseUrl = baseUrl,
            cookieStore = CookieStore(),
            readCookieHeader = { "_forum_session=session_value; _t=" },
            writeCookieToWebView = { _, _ -> },
            flushCookies = {},
            clearCookies = { onComplete -> onComplete?.invoke() },
        )

        assertThat(bridge.hasAuthenticatedCookiesInWebView()).isFalse()
    }

    @Test
    fun syncToWebView_emptyStore_doesNotWriteOrFlush() = runTest {
        val cookieStore = CookieStore()
        val writtenCookies = mutableListOf<Pair<String, String>>()
        var flushed = false
        val bridge = CookieSyncBridge(
            baseUrl = baseUrl,
            cookieStore = cookieStore,
            readCookieHeader = { null },
            writeCookieToWebView = { url, cookie -> writtenCookies.add(url to cookie) },
            flushCookies = { flushed = true },
            clearCookies = { onComplete -> onComplete?.invoke() },
        )

        bridge.syncToWebView()

        assertThat(writtenCookies).isEmpty()
        assertThat(flushed).isFalse()
    }

    @Test
    fun clearAsync_waitsForWebViewDeletionCallback() = runTest {
        val cookieStore = CookieStore().apply {
            save(
                baseUrl.toHttpUrl(),
                listOf(Cookie.Builder().name("_t").value("token").domain("linux.do").path("/").build()),
            )
        }
        var completion: (() -> Unit)? = null
        val bridge = CookieSyncBridge(
            baseUrl = baseUrl,
            cookieStore = cookieStore,
            readCookieHeader = { null },
            writeCookieToWebView = { _, _ -> },
            flushCookies = {},
            clearCookies = { onComplete -> completion = onComplete },
        )

        val clearing = async { bridge.clearAsync() }
        runCurrent()

        assertThat(clearing.isCompleted).isFalse()
        assertThat(cookieStore.hasAuthenticatedCookies()).isFalse()

        requireNotNull(completion).invoke()
        clearing.await()
    }

    @Test
    fun syncFromWebViewByName_syncsOnlySpecifiedCookies() = runTest {
        val cookieStore = CookieStore()
        val bridge = CookieSyncBridge(
            baseUrl = baseUrl,
            cookieStore = cookieStore,
            readCookieHeader = { "_t=auth_token; _forum_session=session_value; cf_clearance=cf_value" },
            writeCookieToWebView = { _, _ -> },
            flushCookies = {},
            clearCookies = { onComplete -> onComplete?.invoke() },
        )

        val result = bridge.syncFromWebViewByName(names = setOf("_t"))

        assertThat(result).isTrue()
        assertThat(cookieStore.load(baseUrl.toHttpUrl()).map { it.name })
            .containsExactly("_t")
    }

    @Test
    fun syncFromWebViewExcluding_syncsAllButExcludedCookies() = runTest {
        val cookieStore = CookieStore()
        val bridge = CookieSyncBridge(
            baseUrl = baseUrl,
            cookieStore = cookieStore,
            readCookieHeader = { "_t=auth_token; _forum_session=session_value; cf_clearance=cf_value" },
            writeCookieToWebView = { _, _ -> },
            flushCookies = {},
            clearCookies = { onComplete -> onComplete?.invoke() },
        )

        val result = bridge.syncFromWebViewExcluding(
            excludeNames = setOf("_t", "_forum_session"),
        )

        assertThat(result).isFalse()
        assertThat(cookieStore.load(baseUrl.toHttpUrl()).map { it.name })
            .containsExactly("cf_clearance")
    }
}
