package io.github.baba983.nativedo.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.baba983.nativedo.core.navigation.AppBrowserOpenRequest
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderContent
import io.github.baba983.nativedo.core.navigation.AppBrowserPresentationTarget
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val BrowserHostBrowserZIndex = 2f
private const val BrowserHostReaderZIndex = 3f
private const val BrowserHostLiveSurfaceZIndex = 12f

internal data class BrowserHostBrowserEntry(
    val id: Long,
    val request: AppBrowserOpenRequest,
    val foregroundActivationKey: Long = 0L,
    val preview: BrowserHostMinimizedPreview = buildBrowserHostBrowserPreview(
        url = request.url,
    ),
)

internal data class BrowserReaderSceneRequest(
    val id: Long,
    val content: AppBrowserReaderContent,
    val sourceBrowserRequest: AppBrowserOpenRequest? = null,
    val foregroundActivationKey: Long = 0L,
    val restoredFromDock: Boolean = false,
    val preview: BrowserHostMinimizedPreview = buildBrowserHostReaderPreview(content),
)

internal enum class BrowserHostMinimizedKind {
    Browser,
    Reader,
}

internal data class BrowserHostMinimizedPreview(
    val kind: BrowserHostMinimizedKind,
    val title: String,
    val subtitle: String?,
    val favicon: Bitmap? = null,
    val snapshot: Bitmap? = null,
)

internal data class BrowserHostSession(
    val browserEntry: BrowserHostBrowserEntry? = null,
    val readerEntry: BrowserReaderSceneRequest? = null,
) {
    val stableId: Long
        get() = browserEntry?.id ?: readerEntry?.id ?: 0L

    val activeTopBarOwner: NativeDoTopBarOwner?
        get() = when {
            readerEntry != null -> NativeDoTopBarOwner.Pushed
            browserEntry != null -> NativeDoTopBarOwner.Browser
            else -> null
        }

    val preview: BrowserHostMinimizedPreview?
        get() = when {
            readerEntry != null -> readerEntry.preview
            browserEntry != null -> browserEntry.preview
            else -> null
        }
}

internal data class BrowserHostState(
    val nextEntryId: Long = 1L,
    val browserEntry: BrowserHostBrowserEntry? = null,
    val readerEntry: BrowserReaderSceneRequest? = null,
    val dockedSessions: List<BrowserHostSession> = emptyList(),
) {
    val activeSession: BrowserHostSession?
        get() = if (browserEntry == null && readerEntry == null) {
            null
        } else {
            BrowserHostSession(
                browserEntry = browserEntry,
                readerEntry = readerEntry,
            )
        }

    val activeTopBarOwner: NativeDoTopBarOwner?
        get() = activeSession?.activeTopBarOwner

    val isMinimized: Boolean
        get() = activeSession == null && dockedSessions.isNotEmpty()

    val minimizedPreview: BrowserHostMinimizedPreview?
        get() = dockedSessions.firstOrNull()?.preview

    fun present(
        request: AppBrowserOpenRequest,
        target: AppBrowserPresentationTarget,
    ): BrowserHostState = when (target) {
        AppBrowserPresentationTarget.FullRoute,
        AppBrowserPresentationTarget.Sheet -> copy(
            nextEntryId = nextEntryId + 1,
            browserEntry = BrowserHostBrowserEntry(
                id = nextEntryId,
                request = request,
                foregroundActivationKey = 0L,
                preview = buildBrowserHostBrowserPreview(url = request.url),
            ),
            readerEntry = null,
        )

        AppBrowserPresentationTarget.InstantView -> copy(
            nextEntryId = nextEntryId + 1,
            browserEntry = null,
            readerEntry = BrowserReaderSceneRequest(
                id = nextEntryId,
                content = AppBrowserReaderContent(
                    url = request.url,
                    title = request.url.toHttpUrlOrNull()?.host ?: request.url,
                    bodyText = "",
                ),
                sourceBrowserRequest = null,
                foregroundActivationKey = 0L,
                restoredFromDock = false,
                preview = buildBrowserHostReaderPreview(
                    content = AppBrowserReaderContent(
                        url = request.url,
                        title = request.url.toHttpUrlOrNull()?.host ?: request.url,
                        bodyText = "",
                    ),
                ),
            ),
        )
    }

    fun presentReader(
        content: AppBrowserReaderContent,
        sourceBrowserRequest: AppBrowserOpenRequest? = browserEntry?.request,
    ): BrowserHostState {
        val fallbackBrowserRequest = sourceBrowserRequest?.takeUnless { request ->
            request.preferredTarget == AppBrowserPresentationTarget.InstantView
        }
        val resolvedBrowserEntry = browserEntry ?: fallbackBrowserRequest?.let { request ->
            BrowserHostBrowserEntry(
                id = nextEntryId,
                request = request,
                foregroundActivationKey = 0L,
                preview = buildBrowserHostBrowserPreview(url = request.url),
            )
        }
        val nextAvailableId = if (browserEntry == null && resolvedBrowserEntry != null) {
            nextEntryId + 1
        } else {
            nextEntryId
        }
        return copy(
            nextEntryId = nextAvailableId + 1,
            browserEntry = resolvedBrowserEntry,
            readerEntry = BrowserReaderSceneRequest(
                id = nextAvailableId,
                content = content,
                sourceBrowserRequest = sourceBrowserRequest ?: resolvedBrowserEntry?.request,
                foregroundActivationKey = 0L,
                restoredFromDock = false,
                preview = buildBrowserHostReaderPreview(content),
            ),
        )
    }

    fun dismissReader(): BrowserHostState {
        if (readerEntry == null) {
            return this
        }
        return if (browserEntry != null) {
            copy(readerEntry = null)
        } else {
            copy(readerEntry = null)
        }
    }

    fun minimize(): BrowserHostState {
        val session = activeSession ?: return this
        return copy(
            browserEntry = null,
            readerEntry = null,
            dockedSessions = listOf(session) + dockedSessions.filterNot { it.stableId == session.stableId },
        )
    }

    fun restoreMinimized(): BrowserHostState {
        val latestSession = dockedSessions.firstOrNull() ?: return this
        return restoreDockedSession(latestSession.stableId)
    }

    fun restoreDockedSession(sessionId: Long): BrowserHostState {
        val session = dockedSessions.firstOrNull { it.stableId == sessionId } ?: return this
        return copy(
            browserEntry = session.browserEntry,
            readerEntry = session.readerEntry,
            dockedSessions = dockedSessions.filterNot { it.stableId == sessionId },
        )
    }

    fun closeActiveSession(): BrowserHostState = copy(
        browserEntry = null,
        readerEntry = null,
    )

    fun closeDockedSession(sessionId: Long): BrowserHostState = copy(
        dockedSessions = dockedSessions.filterNot { it.stableId == sessionId },
    )

    fun closeBrowserStack(): BrowserHostState = copy(
        browserEntry = null,
        readerEntry = null,
        dockedSessions = emptyList(),
    )

    fun updateSessionPreview(
        sessionId: Long,
        preview: BrowserHostMinimizedPreview,
    ): BrowserHostState {
        fun mergePreview(
            current: BrowserHostMinimizedPreview?,
        ): BrowserHostMinimizedPreview = current?.merge(preview) ?: preview

        return when {
            browserEntry?.id == sessionId -> copy(
                browserEntry = browserEntry.copy(
                    preview = mergePreview(browserEntry.preview),
                ),
            )

            readerEntry?.id == sessionId -> copy(
                readerEntry = readerEntry.copy(
                    preview = mergePreview(readerEntry.preview),
                ),
            )

            else -> copy(
                dockedSessions = dockedSessions.map { session ->
                    when (session.stableId) {
                        sessionId -> session.copyWithPreview(mergePreview(session.preview))
                        else -> session
                    }
                },
            )
        }
    }

    fun dismissAll(): BrowserHostState = closeBrowserStack()
}

internal fun BrowserHostState.findSession(
    sessionId: Long,
): BrowserHostSession? = when {
    activeSession?.stableId == sessionId -> activeSession
    else -> dockedSessions.firstOrNull { it.stableId == sessionId }
}

private fun BrowserHostSession.copyWithPreview(
    preview: BrowserHostMinimizedPreview,
): BrowserHostSession = when {
    readerEntry != null -> copy(
        readerEntry = readerEntry.copy(preview = preview),
    )

    browserEntry != null -> copy(
        browserEntry = browserEntry.copy(preview = preview),
    )

    else -> this
}

private fun BrowserHostMinimizedPreview.merge(
    incoming: BrowserHostMinimizedPreview,
): BrowserHostMinimizedPreview = copy(
    title = incoming.title,
    subtitle = incoming.subtitle,
    favicon = incoming.favicon ?: favicon,
    snapshot = incoming.snapshot ?: snapshot,
)

internal fun buildBrowserHostBrowserPreview(
    url: String,
    title: String? = null,
    subtitle: String? = null,
    favicon: Bitmap? = null,
    snapshot: Bitmap? = null,
): BrowserHostMinimizedPreview {
    val host = url.toHttpUrlOrNull()?.host ?: url
    val resolvedTitle = title
        ?.trim()
        ?.takeIf { value -> value.isNotBlank() }
        ?: host
    val resolvedSubtitle = subtitle
        ?.trim()
        ?.takeIf { value -> value.isNotBlank() }
        ?: host.takeIf { !resolvedTitle.equals(host, ignoreCase = true) }
    return BrowserHostMinimizedPreview(
        kind = BrowserHostMinimizedKind.Browser,
        title = resolvedTitle,
        subtitle = resolvedSubtitle,
        favicon = favicon,
        snapshot = snapshot,
    )
}

internal fun buildBrowserHostReaderPreview(
    content: AppBrowserReaderContent,
    favicon: Bitmap? = null,
    snapshot: Bitmap? = null,
): BrowserHostMinimizedPreview = BrowserHostMinimizedPreview(
    kind = BrowserHostMinimizedKind.Reader,
    title = content.title.ifBlank {
        content.url.toHttpUrlOrNull()?.host ?: content.url
    },
    subtitle = content.sourceName
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: content.url.toHttpUrlOrNull()?.host,
    favicon = favicon,
    snapshot = snapshot,
)

@Composable
internal fun BoxScope.BrowserHost(
    state: BrowserHostState,
    browserContent: @Composable BoxScope.(BrowserHostBrowserEntry, Boolean) -> Unit = { _, _ -> },
    readerContent: @Composable BoxScope.(BrowserReaderSceneRequest, Boolean) -> Unit = { _, _ -> },
    onActivePreviewFrameChange: (BrowserTabsPreviewFrame) -> Unit = {},
    liveSurfaceTransition: BrowserTabsLiveSurfaceTransitionState? = null,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        state.dockedSessions.asReversed().forEach { session ->
            BrowserHostSessionLayer(
                session = session,
                isActive = false,
                browserContent = browserContent,
                readerContent = readerContent,
                onActivePreviewFrameChange = onActivePreviewFrameChange,
                liveSurfaceTransition = liveSurfaceTransition,
            )
        }

        state.activeSession?.let { session ->
            BrowserHostSessionLayer(
                session = session,
                isActive = true,
                browserContent = browserContent,
                readerContent = readerContent,
                onActivePreviewFrameChange = onActivePreviewFrameChange,
                liveSurfaceTransition = liveSurfaceTransition,
            )
        }
    }
}

@Composable
private fun BoxScope.BrowserHostSessionLayer(
    session: BrowserHostSession,
    isActive: Boolean,
    browserContent: @Composable BoxScope.(BrowserHostBrowserEntry, Boolean) -> Unit,
    readerContent: @Composable BoxScope.(BrowserReaderSceneRequest, Boolean) -> Unit,
    onActivePreviewFrameChange: (BrowserTabsPreviewFrame) -> Unit,
    liveSurfaceTransition: BrowserTabsLiveSurfaceTransitionState?,
) {
    val preview = session.preview
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = remember(configuration.screenWidthDp, density) {
        with(density) { configuration.screenWidthDp.dp.roundToPx() }.coerceAtLeast(1)
    }
    val screenHeightPx = remember(configuration.screenHeightDp, density) {
        with(density) { configuration.screenHeightDp.dp.roundToPx() }.coerceAtLeast(1)
    }
    val transitionState = liveSurfaceTransition?.takeIf { it.sessionId == session.stableId }

    session.browserEntry?.let { entry ->
        Box(
            modifier = browserHostSessionModifier(
                isActive = isActive,
                preview = preview,
                sessionId = session.stableId,
                activeZIndex = BrowserHostBrowserZIndex,
                publishPreviewFrameWhenActive = session.readerEntry == null,
                onActivePreviewFrameChange = onActivePreviewFrameChange,
                liveSurfaceTransition = transitionState,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
            ),
        ) {
            browserContent(
                entry,
                isActive && session.readerEntry == null && transitionState == null,
            )
        }
    }

    session.readerEntry?.let { entry ->
        Box(
            modifier = browserHostSessionModifier(
                isActive = isActive,
                preview = preview,
                sessionId = session.stableId,
                activeZIndex = BrowserHostReaderZIndex,
                publishPreviewFrameWhenActive = true,
                onActivePreviewFrameChange = onActivePreviewFrameChange,
                liveSurfaceTransition = transitionState,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
            ),
        ) {
            readerContent(
                entry,
                isActive && transitionState == null,
            )
        }
    }
}

private fun browserHostSessionModifier(
    isActive: Boolean,
    preview: BrowserHostMinimizedPreview?,
    sessionId: Long,
    activeZIndex: Float,
    publishPreviewFrameWhenActive: Boolean,
    onActivePreviewFrameChange: (BrowserTabsPreviewFrame) -> Unit,
    liveSurfaceTransition: BrowserTabsLiveSurfaceTransitionState?,
    screenWidthPx: Int,
    screenHeightPx: Int,
): Modifier {
    val baseModifier = when {
        liveSurfaceTransition != null -> Modifier
            .fillMaxSize()
            .zIndex(BrowserHostLiveSurfaceZIndex)
            .graphicsLayer {
                val boundedWidth = screenWidthPx.coerceAtLeast(1).toFloat()
                val boundedHeight = screenHeightPx.coerceAtLeast(1).toFloat()
                translationX = liveSurfaceTransition.boundsInRoot.left.toFloat()
                translationY = liveSurfaceTransition.boundsInRoot.top.toFloat()
                scaleX = liveSurfaceTransition.boundsInRoot.width / boundedWidth
                scaleY = liveSurfaceTransition.boundsInRoot.height / boundedHeight
                alpha = liveSurfaceTransition.alpha
                transformOrigin = TransformOrigin(0f, 0f)
                clip = true
                shape = androidx.compose.foundation.shape.RoundedCornerShape(
                    liveSurfaceTransition.cornerRadius,
                )
            }

        isActive -> Modifier
            .fillMaxSize()
            .zIndex(activeZIndex)

        else -> Modifier
            .fillMaxSize()
            .browserHostOffstage()
    }

    return if (isActive && publishPreviewFrameWhenActive && preview != null) {
        baseModifier.then(
            Modifier.onGloballyPositioned { coordinates ->
                onActivePreviewFrameChange(
                    coordinates.toBrowserTabsPreviewFrame(
                        sessionId = sessionId,
                        preview = preview,
                    ),
                )
            },
        )
    } else {
        baseModifier
    }
}

private fun Modifier.browserHostOffstage(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(width = 0, height = 0) {
        placeable.placeRelative(
            x = -placeable.width * 2,
            y = -placeable.height * 2,
        )
    }
}
