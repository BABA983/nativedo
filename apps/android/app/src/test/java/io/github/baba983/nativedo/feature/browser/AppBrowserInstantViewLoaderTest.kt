package io.github.baba983.nativedo.feature.browser

import android.content.Context
import android.content.ContextWrapper
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderContent
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderDocument
import org.junit.Test

class AppBrowserInstantViewLoaderTest {
    @Test
    fun appBrowserInstantViewLoader_onPageFinished_startsInitialResolveWhileStateIsProbing() {
        val url = "https://example.com/article"
        var resolveCount = 0

        val loader = AppBrowserInstantViewLoader(
            resolveWithResolver = { _, _, _, onResolved ->
                resolveCount += 1
                onResolved(null)
            },
            buildReaderContent = ::buildAppBrowserReaderContent,
            onOpenReader = {},
            onUnavailable = {},
        )

        loader.onPageStarted(url)
        loader.onPageFinished(url)

        assertThat(resolveCount).isEqualTo(1)
        assertThat(loader.state.resolveInFlight).isFalse()
        assertThat(loader.state.phase).isEqualTo(AppBrowserInstantViewPhase.Unsupported)
    }

    @Test
    fun appBrowserInstantViewLoader_onPageFinished_resolvesDocumentFromCurrentHostAndUpdatesState() {
        val url = "https://example.com/article"
        val document = testAppBrowserInstantViewDocument(url)
        val host = LoaderFakeAppBrowserInstantViewResolveHost(
            url = url,
            title = "Example Article",
        )
        var resolverHost: AppBrowserInstantViewResolveHost? = null
        var resolverUrl: String? = null

        val loader = AppBrowserInstantViewLoader(
            resolveHost = { host },
            resolveWithResolver = { resolver, currentHost, requestedUrl, onResolved ->
                resolverHost = currentHost
                resolverUrl = requestedUrl
                onResolved(document)
            },
            buildReaderContent = ::buildAppBrowserReaderContent,
            onOpenReader = {},
            onUnavailable = {},
        )

        loader.onPageStarted(url)
        loader.onPageFinished(url)

        assertThat(resolverHost).isSameInstanceAs(host)
        assertThat(resolverUrl).isEqualTo(url)
        assertThat(loader.state.pageLoaded).isTrue()
        assertThat(loader.state.document).isEqualTo(document)
        assertThat(loader.state.phase).isEqualTo(AppBrowserInstantViewPhase.Ready)
        assertThat(loader.uiState.actionAvailability).isEqualTo(AppBrowserInstantViewActionAvailability.Enabled)
    }

    @Test
    fun appBrowserInstantViewLoader_onOpenRequested_whileResolving_marksPendingOpenAndOpensLater() {
        val url = "https://example.com/article"
        val document = testAppBrowserInstantViewDocument(url)
        var pendingResolve: ((AppBrowserReaderDocument?) -> Unit)? = null
        var openedContent: AppBrowserReaderContent? = null

        val loader = AppBrowserInstantViewLoader(
            resolveWithResolver = { _, _, _, onResolved ->
                pendingResolve = onResolved
            },
            buildReaderContent = ::buildAppBrowserReaderContent,
            onOpenReader = { content ->
                openedContent = content
            },
            onUnavailable = {},
        )

        loader.onPageStarted(url)
        loader.onPageFinished(url)
        loader.onOpenRequested()
        pendingResolve?.invoke(document)

        assertThat(loader.state.pendingOpenRequested).isFalse()
        assertThat(openedContent?.document?.url).isEqualTo(url)
        assertThat(openedContent?.document?.title).isEqualTo("Example Article")
    }

    @Test
    fun appBrowserInstantViewLoader_onOpenRequestedDuringProbing_keepsPendingOpenUntilResolveCompletes() {
        val url = "https://example.com/article"
        val document = testAppBrowserInstantViewDocument(url)
        var pendingResolve: ((AppBrowserReaderDocument?) -> Unit)? = null
        var openedContent: AppBrowserReaderContent? = null

        val loader = AppBrowserInstantViewLoader(
            resolveWithResolver = { _, _, _, onResolved ->
                pendingResolve = onResolved
            },
            buildReaderContent = ::buildAppBrowserReaderContent,
            onOpenReader = { content ->
                openedContent = content
            },
            onUnavailable = {},
        )

        loader.onPageStarted(url)
        loader.onPageSnapshotChanged(
            pageUrl = url,
            pageProgress = 0.42f,
        )
        loader.onOpenRequested()
        loader.onPageFinished(url)
        pendingResolve?.invoke(document)

        assertThat(loader.state.pendingOpenRequested).isFalse()
        assertThat(loader.state.phase).isEqualTo(AppBrowserInstantViewPhase.Ready)
        assertThat(openedContent?.document?.url).isEqualTo(url)
        assertThat(openedContent?.document?.title).isEqualTo("Example Article")
    }

    @Test
    fun appBrowserInstantViewLoader_onProgressChangedAfterPageFinished_keepsLoadedSnapshotInternally() {
        val url = "https://example.com/article"

        val loader = AppBrowserInstantViewLoader(
            resolveWithResolver = { _, _, _, onResolved -> onResolved(null) },
            buildReaderContent = ::buildAppBrowserReaderContent,
            onOpenReader = {},
            onUnavailable = {},
        )

        loader.onPageStarted(url)
        loader.onPageFinished(url)
        loader.onPageSnapshotChanged(
            pageUrl = url,
            pageProgress = 0.85f,
        )

        assertThat(loader.state.pageLoaded).isTrue()
    }

    @Test
    fun appBrowserInstantViewLoader_uiState_exposesDisabledPlaceholderUntilDocumentResolves() {
        val url = "https://example.com/article"
        val document = testAppBrowserInstantViewDocument(url)
        var pendingResolve: ((AppBrowserReaderDocument?) -> Unit)? = null

        val loader = AppBrowserInstantViewLoader(
            resolveWithResolver = { _, _, _, onResolved ->
                pendingResolve = onResolved
            },
            buildReaderContent = ::buildAppBrowserReaderContent,
            onOpenReader = {},
            onUnavailable = {},
        )

        loader.onPageStarted(url)
        assertThat(loader.uiState.actionAvailability)
            .isEqualTo(AppBrowserInstantViewActionAvailability.Disabled)
        assertThat(loader.uiState.phase).isEqualTo(AppBrowserInstantViewPhase.Probing)

        loader.onPageFinished(url)
        pendingResolve?.invoke(document)

        assertThat(loader.uiState.actionAvailability)
            .isEqualTo(AppBrowserInstantViewActionAvailability.Enabled)
        assertThat(loader.uiState.phase).isEqualTo(AppBrowserInstantViewPhase.Ready)
    }

    @Test
    fun appBrowserInstantViewLoader_uiState_hidesActionAfterUnsupportedResolution() {
        val url = "https://example.com/article"
        var pendingResolve: ((AppBrowserReaderDocument?) -> Unit)? = null

        val loader = AppBrowserInstantViewLoader(
            resolveWithResolver = { _, _, _, onResolved ->
                pendingResolve = onResolved
            },
            buildReaderContent = ::buildAppBrowserReaderContent,
            onOpenReader = {},
            onUnavailable = {},
        )

        loader.onPageStarted(url)
        loader.onPageFinished(url)
        pendingResolve?.invoke(null)

        assertThat(loader.uiState.phase).isEqualTo(AppBrowserInstantViewPhase.Unsupported)
        assertThat(loader.uiState.actionAvailability)
            .isEqualTo(AppBrowserInstantViewActionAvailability.Hidden)
    }
}

private fun testAppBrowserInstantViewDocument(
    url: String,
): AppBrowserReaderDocument {
    return AppBrowserReaderContent(
        url = url,
        title = "Example Article",
        bodyText = """
            First paragraph contains enough text to satisfy the local Instant View support rules.

            Second paragraph keeps the article body substantial and reader-like for extraction.

            Third paragraph adds more detail so the document remains clearly article content.

            Fourth paragraph keeps the total body long enough for the conservative gating logic.

            Fifth paragraph closes the sample document with enough text for the reader builder.
        """.trimIndent(),
    ).document
}

private class LoaderFakeAppBrowserInstantViewResolveHost(
    override val url: String?,
    override val title: String?,
) : AppBrowserInstantViewResolveHost {
    override val context: Context =
        object : ContextWrapper(null) {}

    override fun captureArchiveSnapshot(
        onResult: (AppBrowserInstantViewArchiveSnapshot?) -> Unit,
    ) {
        onResult(null)
    }

    override fun evaluateJavascript(
        script: String,
        onResult: (String?) -> Unit,
    ) {
        onResult(null)
    }
}
