package io.github.baba983.nativedo.feature.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderContent
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderDocument

internal class AppBrowserInstantViewLoader(
    private val resolver: AppBrowserInstantViewResolver = AppBrowserInstantViewResolver(),
    private var resolveHost: () -> AppBrowserInstantViewResolveHost? = { null },
    private var resolveWithResolver: (
        AppBrowserInstantViewResolver,
        AppBrowserInstantViewResolveHost?,
        String,
        (AppBrowserReaderDocument?) -> Unit,
    ) -> Unit =
        { resolver, host, expectedUrl, onResolved ->
            resolver.resolve(
                host = host,
                expectedUrl = expectedUrl,
                onResolved = onResolved,
            )
        },
    private var buildReaderContent: (AppBrowserReaderDocument?) -> AppBrowserReaderContent? =
        { document ->
            buildAppBrowserReaderContent(
                document = document,
            )
        },
    private var onOpenReader: (AppBrowserReaderContent) -> Unit = {},
    private var onUnavailable: () -> Unit = {},
) {
    var state by mutableStateOf(AppBrowserInstantViewLoaderState())
        private set

    val uiState: AppBrowserInstantViewUiState
        get() = AppBrowserInstantViewUiState(
            phase = state.phase,
            actionAvailability = state.actionAvailability,
            isPendingOpen = state.pendingOpenRequested,
        )

    fun bind(
        resolveHost: () -> AppBrowserInstantViewResolveHost?,
        buildReaderContent: (AppBrowserReaderDocument?) -> AppBrowserReaderContent?,
        onOpenReader: (AppBrowserReaderContent) -> Unit,
        onUnavailable: () -> Unit,
    ) {
        this.resolveHost = resolveHost
        this.buildReaderContent = buildReaderContent
        this.onOpenReader = onOpenReader
        this.onUnavailable = onUnavailable
    }

    fun onPageStarted(pageUrl: String?) {
        state = state.onPageStarted(pageUrl)
    }

    fun onPageSnapshotChanged(
        pageUrl: String?,
        pageProgress: Float,
    ) {
        state = state.onPageSnapshotChanged(
            pageUrl = pageUrl,
            pageProgress = pageProgress,
        )
    }

    fun onPageFinished(pageUrl: String?) {
        state = state.onPageFinished(pageUrl)
        if (state.document == null) {
            retry(openWhenResolved = false)
        }
    }

    fun onOpenRequested() {
        val content = buildCurrentContent()
        if (content == null) {
            if (state.phase == AppBrowserInstantViewPhase.Probing) {
                state = state.copy(pendingOpenRequested = true)
            } else {
                retry(openWhenResolved = true)
            }
            return
        }
        openReaderContent(content)
    }

    fun clearPendingOpen() {
        state = state.clearPendingOpen()
    }

    private fun retry(openWhenResolved: Boolean) {
        val expectedUrl = state.pageUrl
        if (expectedUrl.isNullOrBlank()) {
            state = state.clearPendingOpen()
            return
        }
        if (state.resolveInFlight) {
            return
        }
        val resolveSnapshotProgress = state.pageProgress
        val resolveSnapshotLoaded = state.pageLoaded
        state = state.onResolveStarted(
            pageUrl = expectedUrl,
            openWhenResolved = openWhenResolved,
        )
        resolveWithResolver(resolver, resolveHost(), expectedUrl) { resolvedDocument ->
            if (
                state.isStaleForSnapshot(
                    pageUrl = expectedUrl,
                    pageProgress = resolveSnapshotProgress,
                    pageLoaded = resolveSnapshotLoaded,
                )
            ) {
                val pendingOpen = state.pendingOpenRequested
                if (state.pageLoaded) {
                    retry(openWhenResolved = pendingOpen)
                }
                return@resolveWithResolver
            }
            val pendingOpen = state.pendingOpenRequested
            state = state.onResolved(
                pageUrl = expectedUrl,
                document = resolvedDocument,
            )
            val resolvedContent = buildReaderContent(resolvedDocument)
            if (pendingOpen) {
                state = state.clearPendingOpen()
                if (resolvedContent != null) {
                    openReaderContent(resolvedContent)
                } else {
                    onUnavailable()
                }
            }
        }
    }

    private fun buildCurrentContent(): AppBrowserReaderContent? {
        return buildReaderContent(state.document)
    }

    private fun openReaderContent(content: AppBrowserReaderContent) {
        state = state.clearPendingOpen()
        onOpenReader(content)
    }
}
