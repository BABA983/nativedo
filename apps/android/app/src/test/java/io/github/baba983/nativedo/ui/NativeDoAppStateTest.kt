package io.github.baba983.nativedo.ui

import com.google.common.truth.Truth.assertThat
import androidx.compose.ui.unit.IntRect
import io.github.baba983.nativedo.core.navigation.AppBrowserOpenRequest
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderContent
import io.github.baba983.nativedo.core.navigation.AppBrowserPresentationAvailability
import io.github.baba983.nativedo.core.navigation.AppBrowserPresentationPolicy
import io.github.baba983.nativedo.core.navigation.AppBrowserPresentationTarget
import io.github.baba983.nativedo.navigation.RootDestination
import org.junit.Test
import androidx.compose.ui.unit.dp

class NativeDoAppStateTest {
    @Test
    fun browserTopBarOverlayHeight_matchesRootTopBarHeight() {
        assertThat(NativeDoBrowserTopBarOverlayHeight)
            .isEqualTo(AppTopBarRowMinHeight + (AppTopBarVerticalPadding * 2))
    }

    @Test
    fun resolveNativeDoTopBarOwner_returnsRootForRootContainer() {
        assertThat(resolveNativeDoTopBarOwner(null)).isEqualTo(NativeDoTopBarOwner.Root)
        assertThat(
            resolveNativeDoTopBarOwner(RootDestination.rootContainerRoute),
        ).isEqualTo(NativeDoTopBarOwner.Root)
    }

    @Test
    fun resolveNativeDoTopBarOwner_returnsPushedForStandaloneSearchRoute() {
        assertThat(
            resolveNativeDoTopBarOwner(RootDestination.Search.route),
        ).isEqualTo(NativeDoTopBarOwner.Pushed)
    }

    @Test
    fun resolveNativeDoTopBarOwner_returnsPushedForReaderStyleRoutes() {
        assertThat(
            resolveNativeDoTopBarOwner("topic/{topicId}"),
        ).isEqualTo(NativeDoTopBarOwner.Pushed)
        assertThat(
            resolveNativeDoTopBarOwner("user/{username}"),
        ).isEqualTo(NativeDoTopBarOwner.Pushed)
    }

    @Test
    fun resolveNativeDoTopBarOwner_returnsPushedWhenReaderSceneIsShowing() {
        assertThat(
            resolveNativeDoTopBarOwner(
                currentRoute = RootDestination.rootContainerRoute,
                pushedEntryRoutePattern = "topic/{topicId}",
            ),
        ).isEqualTo(NativeDoTopBarOwner.Pushed)
    }

    @Test
    fun resolveNativeDoTopBarOwner_returnsBrowserWhenBrowserSceneIsShowing() {
        val overlayState = BrowserHostState(
            browserEntry = BrowserHostBrowserEntry(
                id = 1L,
                request = AppBrowserOpenRequest(url = "https://example.com/article"),
            ),
        )

        assertThat(
            resolveNativeDoTopBarOwner(
                currentRoute = RootDestination.rootContainerRoute,
                overlayState = overlayState,
            ),
        ).isEqualTo(NativeDoTopBarOwner.Browser)
    }

    @Test
    fun resolveNativeDoTopBarOwner_prefersOverlayOwnerWhenPresent() {
        val overlayState = BrowserHostState(
            browserEntry = BrowserHostBrowserEntry(
                id = 1L,
                request = AppBrowserOpenRequest(url = "https://example.com/article"),
            ),
        )

        assertThat(
            resolveNativeDoTopBarOwner(
                currentRoute = RootDestination.rootContainerRoute,
                overlayState = overlayState,
            ),
        ).isEqualTo(NativeDoTopBarOwner.Browser)
    }

    @Test
    fun nativeDoRootChromeVisibility_followsTopBarOwnerMatrix() {
        assertThat(
            shouldRenderNativeDoRootTopBar(NativeDoTopBarOwner.Root),
        ).isTrue()
        assertThat(
            shouldRenderNativeDoRootTopBar(NativeDoTopBarOwner.Pushed),
        ).isFalse()
        assertThat(
            shouldRenderNativeDoRootTopBar(NativeDoTopBarOwner.Browser),
        ).isFalse()

        assertThat(
            shouldRenderNativeDoRootBottomBar(NativeDoTopBarOwner.Root),
        ).isTrue()
        assertThat(
            shouldRenderNativeDoRootBottomBar(NativeDoTopBarOwner.Pushed),
        ).isFalse()
        assertThat(
            shouldRenderNativeDoRootBottomBar(NativeDoTopBarOwner.Browser),
        ).isFalse()
    }

    @Test
    fun nativeDoRootChromeVisibility_hidesRootBottomBarDuringActiveHomeSearch() {
        assertThat(
            shouldRenderNativeDoRootBottomBar(
                owner = NativeDoTopBarOwner.Root,
                isHomeSearchActive = true,
            ),
        ).isFalse()
    }

    @Test
    fun resolveNativeDoRootChromeRoute_prefersSelectedRootRoute_forRootContainer() {
        assertThat(
            resolveNativeDoRootChromeRoute(
                currentRoute = null,
                selectedRootRoute = RootDestination.Profile.route,
            ),
        ).isEqualTo(RootDestination.Profile.route)

        assertThat(
            resolveNativeDoRootChromeRoute(
                currentRoute = RootDestination.rootContainerRoute,
                selectedRootRoute = RootDestination.Home.route,
            ),
        ).isEqualTo(RootDestination.Home.route)
    }

    @Test
    fun resolveNativeDoRootChromeRoute_returnsNull_forDedicatedRoutes() {
        assertThat(
            resolveNativeDoRootChromeRoute(
                currentRoute = "topic/{topicId}",
                selectedRootRoute = RootDestination.Home.route,
            ),
        ).isNull()

        assertThat(
            resolveNativeDoRootChromeRoute(
                currentRoute = RootDestination.Search.route,
                selectedRootRoute = RootDestination.Home.route,
            ),
        ).isNull()
    }

    @Test
    fun rootChromeBackgroundVisibility_stopsKeepingRootTopBarBehindForegroundScenes() {
        assertThat(
            shouldKeepNativeDoRootTopBarInBackground(
                currentRoute = RootDestination.rootContainerRoute,
                isPushedEntryShowing = true,
            ),
        ).isFalse()

        assertThat(
            shouldKeepNativeDoRootTopBarInBackground(
                currentRoute = RootDestination.Search.route,
                overlayState = BrowserHostState(
                    browserEntry = BrowserHostBrowserEntry(
                        id = 1L,
                        request = AppBrowserOpenRequest(url = "https://example.com/article"),
                    ),
                ),
            ),
        ).isFalse()

        assertThat(
            shouldKeepNativeDoRootTopBarInBackground(
                currentRoute = "topic/{topicId}",
                isPushedEntryShowing = true,
            ),
        ).isFalse()
    }

    @Test
    fun rootChromeBackgroundVisibility_keepsRootBottomBarOnlyForRootContainerTransitions() {
        assertThat(
            shouldKeepNativeDoRootBottomBarInBackground(
                currentRoute = RootDestination.rootContainerRoute,
                isPushedEntryShowing = true,
            ),
        ).isTrue()

        assertThat(
            shouldKeepNativeDoRootBottomBarInBackground(
                currentRoute = RootDestination.rootContainerRoute,
                overlayState = BrowserHostState(
                    browserEntry = BrowserHostBrowserEntry(
                        id = 1L,
                        request = AppBrowserOpenRequest(url = "https://example.com/article"),
                    ),
                ),
            ),
        ).isTrue()

        assertThat(
            shouldKeepNativeDoRootBottomBarInBackground(
                currentRoute = RootDestination.Search.route,
                isPushedEntryShowing = true,
            ),
        ).isFalse()
    }

    @Test
    fun rootChromeBackgroundVisibility_dockedBrowserSessionsDoNotKeepRootBottomBarHidden() {
        val overlayState = BrowserHostState().present(
            request = AppBrowserOpenRequest(url = "https://example.com/article"),
            target = AppBrowserPresentationTarget.Sheet,
        ).minimize()

        assertThat(
            shouldKeepNativeDoRootBottomBarInBackground(
                currentRoute = RootDestination.rootContainerRoute,
                overlayState = overlayState,
            ),
        ).isFalse()
    }

    @Test
    fun shouldBlockNavigationLayout_blocksLoginOnly() {
        assertThat(shouldBlockNavigationLayout("login")).isTrue()
        assertThat(shouldBlockNavigationLayout(RootDestination.rootContainerRoute)).isFalse()
        assertThat(shouldBlockNavigationLayout("topic/{topicId}")).isFalse()
    }

    @Test
    fun topicRouteTitleOverride_usesTopicOverrideOnTopicRoute() {
        assertThat(
            topicRouteTitleOverride(
                currentRoute = "topic/{topicId}",
                topicTitleOverride = "Compose on linux.do",
            ),
        ).isEqualTo("Compose on linux.do")
    }

    @Test
    fun topicRouteTitleOverride_ignoresBlankTitleAndNonTopicRoutes() {
        assertThat(
            topicRouteTitleOverride(
                currentRoute = "topic/{topicId}",
                topicTitleOverride = "",
            ),
        ).isNull()

        assertThat(
            topicRouteTitleOverride(
                currentRoute = RootDestination.Search.route,
                topicTitleOverride = "Compose on linux.do",
            ),
        ).isNull()
    }

    @Test
    fun browserOverlayState_dismissingReaderRestoresUnderlyingSheetOwner() {
        val sheetRequest = AppBrowserOpenRequest(url = "https://example.com/article")
        val overlayState = BrowserHostState(
            browserEntry = BrowserHostBrowserEntry(
                id = 1L,
                request = sheetRequest,
            ),
        ).presentReader(
            content = AppBrowserReaderContent(
                url = "https://example.com/article/instant-view",
                title = "Example Reader",
                bodyText = "Reader body",
            ),
            sourceBrowserRequest = AppBrowserOpenRequest(
                url = "https://example.com/article/instant-view",
                preferredTarget = AppBrowserPresentationTarget.InstantView,
            ),
        )

        assertThat(overlayState.activeTopBarOwner).isEqualTo(NativeDoTopBarOwner.Pushed)
        assertThat(overlayState.dismissReader().activeTopBarOwner).isEqualTo(NativeDoTopBarOwner.Browser)
    }

    @Test
    fun browserOverlayState_dismissingReaderWithoutSheetRestoresPreviousScene() {
        val overlayState = BrowserHostState().presentReader(
            content = AppBrowserReaderContent(
                url = "https://example.com/article/instant-view",
                title = "Example Reader",
                bodyText = "Reader body",
            ),
            sourceBrowserRequest = AppBrowserOpenRequest(
                url = "https://example.com/article/instant-view",
                preferredTarget = AppBrowserPresentationTarget.InstantView,
            ),
        )

        assertThat(overlayState.activeTopBarOwner).isEqualTo(NativeDoTopBarOwner.Pushed)
        assertThat(overlayState.dismissReader().activeTopBarOwner).isNull()
    }

    @Test
    fun browserOverlayState_minimize_hidesOverlayOwnershipUntilRestored() {
        val overlayState = BrowserHostState().present(
            request = AppBrowserOpenRequest(url = "https://example.com/article"),
            target = AppBrowserPresentationTarget.Sheet,
        ).minimize()

        assertThat(overlayState.activeTopBarOwner).isNull()
        assertThat(overlayState.restoreMinimized().activeTopBarOwner).isEqualTo(NativeDoTopBarOwner.Browser)
    }

    @Test
    fun browserOverlayState_minimize_movesActiveSessionIntoDockAndClearsForegroundSession() {
        val overlayState = BrowserHostState().present(
            request = AppBrowserOpenRequest(url = "https://example.com/article"),
            target = AppBrowserPresentationTarget.Sheet,
        ).minimize()

        assertThat(overlayState.activeSession).isNull()
        assertThat(overlayState.dockedSessions).hasSize(1)
        assertThat(overlayState.dockedSessions.first().browserEntry?.request?.url)
            .isEqualTo("https://example.com/article")

        val restored = overlayState.restoreMinimized()
        assertThat(restored.activeSession?.browserEntry?.request?.url)
            .isEqualTo("https://example.com/article")
        assertThat(restored.dockedSessions).isEmpty()
    }

    @Test
    fun browserOverlayState_restoreDockedSession_canSelectOlderDockedSessionWithoutClosingNewerOne() {
        val firstDocked = BrowserHostState().present(
            request = AppBrowserOpenRequest(url = "https://example.com/first"),
            target = AppBrowserPresentationTarget.Sheet,
        ).minimize()
        val secondDocked = firstDocked.present(
            request = AppBrowserOpenRequest(url = "https://example.com/second"),
            target = AppBrowserPresentationTarget.Sheet,
        ).minimize()
        val olderSessionId = firstDocked.dockedSessions.first().stableId

        val restored = secondDocked.restoreDockedSession(olderSessionId)

        assertThat(restored.activeSession?.browserEntry?.request?.url)
            .isEqualTo("https://example.com/first")
        assertThat(restored.dockedSessions).hasSize(1)
        assertThat(restored.dockedSessions.first().browserEntry?.request?.url)
            .isEqualTo("https://example.com/second")
    }

    @Test
    fun browserOverlayState_restoreDockedBrowserSession_keepsForegroundActivationKeyForLiveRestore() {
        val minimized = BrowserHostState().present(
            request = AppBrowserOpenRequest(url = "https://example.com/article"),
            target = AppBrowserPresentationTarget.Sheet,
        ).minimize()

        val restored = minimized.restoreMinimized()

        assertThat(restored.browserEntry?.foregroundActivationKey).isEqualTo(0L)
    }

    @Test
    fun browserOverlayState_restoreDockedReaderSession_keepsLiveReaderStateWithoutDockReentry() {
        val minimized = BrowserHostState().presentReader(
            content = AppBrowserReaderContent(
                url = "https://example.com/article/instant-view",
                title = "Example Reader",
                bodyText = "Reader body",
            ),
            sourceBrowserRequest = AppBrowserOpenRequest(
                url = "https://example.com/article",
            ),
        ).minimize()

        val restored = minimized.restoreMinimized()

        assertThat(restored.readerEntry?.foregroundActivationKey).isEqualTo(0L)
        assertThat(restored.readerEntry?.restoredFromDock).isFalse()
    }

    @Test
    fun browserOverlayState_closeDockedSession_keepsRemainingDockedSessionsAvailable() {
        val firstDocked = BrowserHostState().present(
            request = AppBrowserOpenRequest(url = "https://example.com/first"),
            target = AppBrowserPresentationTarget.Sheet,
        ).minimize()
        val secondDocked = firstDocked.present(
            request = AppBrowserOpenRequest(url = "https://example.com/second"),
            target = AppBrowserPresentationTarget.Sheet,
        ).minimize()
        val latestSessionId = secondDocked.dockedSessions.first().stableId

        val closed = secondDocked.closeDockedSession(latestSessionId)

        assertThat(closed.activeSession).isNull()
        assertThat(closed.dockedSessions).hasSize(1)
        assertThat(closed.dockedSessions.first().browserEntry?.request?.url)
            .isEqualTo("https://example.com/first")
    }

    @Test
    fun browserOverlayState_present_usesSheetForOrdinaryHttpBrowserRequest() {
        val policy = AppBrowserPresentationPolicy(baseUrl = "https://linux.do")
        val target = policy.resolveTarget(
            request = AppBrowserOpenRequest(url = "https://example.com/article"),
            availability = AppBrowserPresentationAvailability(supportsSheetBrowser = true),
        )

        val overlayState = BrowserHostState().present(
            request = AppBrowserOpenRequest(url = "https://example.com/article"),
            target = target,
        )

        assertThat(target).isEqualTo(AppBrowserPresentationTarget.Sheet)
        assertThat(overlayState.browserEntry?.request?.url).isEqualTo("https://example.com/article")
        assertThat(overlayState.readerEntry).isNull()
    }

    @Test
    fun browserHostState_present_collapsesLegacyFullRouteIntoSingleBrowserRequest() {
        val request = AppBrowserOpenRequest(
            url = "https://example.com/article",
            preferredTarget = AppBrowserPresentationTarget.FullRoute,
        )

        val overlayState = BrowserHostState().present(
            request = request,
            target = AppBrowserPresentationTarget.FullRoute,
        )

        assertThat(overlayState.browserEntry?.request).isEqualTo(request)
        assertThat(overlayState.readerEntry).isNull()
        assertThat(overlayState.activeTopBarOwner).isEqualTo(NativeDoTopBarOwner.Browser)
    }

    @Test
    fun browserOverlayState_presentReader_preservesSourceBrowserRequestForReaderRestoration() {
        val sourceRequest = AppBrowserOpenRequest(url = "https://example.com/article")
        val overlayState = BrowserHostState(
            browserEntry = BrowserHostBrowserEntry(
                id = 1L,
                request = sourceRequest,
            ),
        ).presentReader(
            content = AppBrowserReaderContent(
                url = "https://example.com/article/instant-view",
                title = "Example Reader",
                bodyText = "Reader body",
            ),
            sourceBrowserRequest = sourceRequest,
        )

        assertThat(overlayState.readerEntry?.sourceBrowserRequest).isEqualTo(sourceRequest)
        assertThat(overlayState.browserEntry?.request).isEqualTo(sourceRequest)
    }

    @Test
    fun browserOverlayState_presentReader_withoutSourceFallsBackToCurrentSheetRequest() {
        val sourceRequest = AppBrowserOpenRequest(url = "https://example.com/article")
        val overlayState = BrowserHostState(
            browserEntry = BrowserHostBrowserEntry(
                id = 1L,
                request = sourceRequest,
            ),
        ).presentReader(
            content = AppBrowserReaderContent(
                url = "https://example.com/article/instant-view",
                title = "Example Reader",
                bodyText = "Reader body",
            ),
        )

        assertThat(overlayState.readerEntry?.sourceBrowserRequest).isEqualTo(sourceRequest)
    }

    @Test
    fun browserHostState_presentReader_withoutSourceFallsBackToCollapsedLegacyFullRouteRequest() {
        val sourceRequest = AppBrowserOpenRequest(url = "https://example.com/article")
        val overlayState = BrowserHostState().present(
            request = sourceRequest,
            target = AppBrowserPresentationTarget.FullRoute,
        ).presentReader(
            content = AppBrowserReaderContent(
                url = "https://example.com/article/instant-view",
                title = "Example Reader",
                bodyText = "Reader body",
            ),
        )

        assertThat(overlayState.readerEntry?.sourceBrowserRequest).isEqualTo(sourceRequest)
        assertThat(overlayState.browserEntry?.request).isEqualTo(sourceRequest)
    }

    @Test
    fun browserOverlayState_closeBrowserStack_clearsMinimizedReaderAndBrowserEntries() {
        val overlayState = BrowserHostState().present(
            request = AppBrowserOpenRequest(url = "https://example.com/article"),
            target = AppBrowserPresentationTarget.Sheet,
        ).presentReader(
            content = AppBrowserReaderContent(
                url = "https://example.com/article/instant-view",
                title = "Example Reader",
                bodyText = "Reader body",
            ),
        ).minimize().closeBrowserStack()

        assertThat(overlayState.browserEntry).isNull()
        assertThat(overlayState.readerEntry).isNull()
        assertThat(overlayState.activeTopBarOwner).isNull()
    }

    @Test
    fun browserOverlayState_minimizedReaderRestore_keepsReaderAndSourceBrowserContinuity() {
        val sourceRequest = AppBrowserOpenRequest(url = "https://example.com/article")
        val minimized = BrowserHostState().present(
            request = sourceRequest,
            target = AppBrowserPresentationTarget.Sheet,
        ).presentReader(
            content = AppBrowserReaderContent(
                url = "https://example.com/article/instant-view",
                title = "Example Reader",
                bodyText = "Reader body",
            ),
            sourceBrowserRequest = sourceRequest,
        ).minimize()

        val restored = minimized.restoreMinimized()

        assertThat(restored.browserEntry?.request).isEqualTo(sourceRequest)
        assertThat(restored.readerEntry?.content?.title).isEqualTo("Example Reader")
        assertThat(restored.readerEntry?.sourceBrowserRequest).isEqualTo(sourceRequest)
    }

    @Test
    fun browserTabsShellState_hidesCollapsedDockWhileBrowserSessionIsActive() {
        val overlayState = BrowserHostState().present(
            request = AppBrowserOpenRequest(url = "https://example.com/article"),
            target = AppBrowserPresentationTarget.Sheet,
        )

        assertThat(
            buildBrowserTabsShellState(
                hostState = overlayState,
                isManagerVisible = false,
            ),
        ).isEqualTo(
            BrowserTabsShellState(
                showsCollapsedDock = false,
                occupiedHeight = 0.dp,
                dockAction = BrowserTabsShellDockAction.None,
            ),
        )
    }

    @Test
    fun browserTabsShellState_singleDockedSession_usesDirectRestoreAndOccupiesShellBottom() {
        val overlayState = BrowserHostState().present(
            request = AppBrowserOpenRequest(url = "https://example.com/article"),
            target = AppBrowserPresentationTarget.Sheet,
        ).minimize()

        assertThat(
            buildBrowserTabsShellState(
                hostState = overlayState,
                isManagerVisible = false,
            ),
        ).isEqualTo(
            BrowserTabsShellState(
                showsCollapsedDock = true,
                occupiedHeight = 60.dp,
                dockAction = BrowserTabsShellDockAction.RestoreLatest,
            ),
        )
    }

    @Test
    fun browserTabsShellState_multipleDockedSessions_openManagerInsteadOfDirectRestore() {
        val firstDocked = BrowserHostState().present(
            request = AppBrowserOpenRequest(url = "https://example.com/first"),
            target = AppBrowserPresentationTarget.Sheet,
        ).minimize()
        val secondDocked = firstDocked.present(
            request = AppBrowserOpenRequest(url = "https://example.com/second"),
            target = AppBrowserPresentationTarget.Sheet,
        ).minimize()

        assertThat(
            buildBrowserTabsShellState(
                hostState = secondDocked,
                isManagerVisible = false,
            ),
        ).isEqualTo(
            BrowserTabsShellState(
                showsCollapsedDock = true,
                occupiedHeight = BrowserTabsCollapsedDockOccupiedHeight,
                dockAction = BrowserTabsShellDockAction.OpenManager,
            ),
        )
    }

    @Test
    fun browserTabsShellState_managerVisibilityHidesCollapsedDockAndReleasesBottomInset() {
        val overlayState = BrowserHostState().present(
            request = AppBrowserOpenRequest(url = "https://example.com/article"),
            target = AppBrowserPresentationTarget.Sheet,
        ).minimize()

        assertThat(
            buildBrowserTabsShellState(
                hostState = overlayState,
                isManagerVisible = true,
            ),
        ).isEqualTo(
            BrowserTabsShellState(
                showsCollapsedDock = false,
                occupiedHeight = 0.dp,
                dockAction = BrowserTabsShellDockAction.None,
            ),
        )
    }

    @Test
    fun shouldRenderProfileAvatarTab_returnsTrueForLoggedInProfileTab() {
        assertThat(
            shouldRenderProfileAvatarTab(
                destination = RootDestination.Profile,
                profileAvatarLabel = "baba",
                profileAvatarTemplate = "/user_avatar/linux.do/baba/{size}/1.png",
            ),
        ).isTrue()
    }

    @Test
    fun shouldRenderProfileAvatarTab_returnsFalseForOtherTabsOrMissingAvatar() {
        assertThat(
            shouldRenderProfileAvatarTab(
                destination = RootDestination.Home,
                profileAvatarLabel = "baba",
                profileAvatarTemplate = "/user_avatar/linux.do/baba/{size}/1.png",
            ),
        ).isFalse()

        assertThat(
            shouldRenderProfileAvatarTab(
                destination = RootDestination.Profile,
                profileAvatarLabel = "baba",
                profileAvatarTemplate = "",
            ),
        ).isFalse()
    }

    @Test
    fun browserTabsTransitionState_minimizeUsesActiveAndDockFrames() {
        val preview = BrowserHostMinimizedPreview(
            kind = BrowserHostMinimizedKind.Browser,
            title = "linux.do",
            subtitle = "Browser",
        )
        val request = BrowserTabsTransitionRequest(
            transitionId = 1L,
            kind = BrowserTabsTransitionKind.MinimizeToDock,
            sessionId = 11L,
            preview = preview,
        )

        assertThat(
            buildBrowserTabsTransitionState(
                request = request,
                activeFrames = mapOf(
                    11L to BrowserTabsPreviewFrame(
                        sessionId = 11L,
                        preview = preview,
                        boundsInRoot = IntRect(0, 0, 1080, 2200),
                    ),
                ),
                dockFrames = mapOf(
                    11L to BrowserTabsPreviewFrame(
                        sessionId = 11L,
                        preview = preview,
                        boundsInRoot = IntRect(48, 2088, 1032, 2188),
                    ),
                ),
                managerFrames = emptyMap(),
                viewportWidthPx = 1080,
            ),
        ).isEqualTo(
            BrowserTabsTransitionState(
                transitionId = 1L,
                kind = BrowserTabsTransitionKind.MinimizeToDock,
                sessionId = 11L,
                preview = preview,
                sourceSurface = BrowserTabsTransitionSurface.Active,
                targetSurface = BrowserTabsTransitionSurface.Dock,
                sourceBounds = IntRect(0, 0, 1080, 2200),
                targetBounds = IntRect(48, 2088, 1032, 2188),
                dismissDirection = null,
            ),
        )
    }

    @Test
    fun browserTabsTransitionState_restoreFromManagerUsesManagerAndActiveFrames() {
        val preview = BrowserHostMinimizedPreview(
            kind = BrowserHostMinimizedKind.Reader,
            title = "Instant View",
            subtitle = "linux.do",
        )
        val request = BrowserTabsTransitionRequest(
            transitionId = 2L,
            kind = BrowserTabsTransitionKind.RestoreFromManager,
            sessionId = 17L,
            preview = preview,
        )

        assertThat(
            buildBrowserTabsTransitionState(
                request = request,
                activeFrames = mapOf(
                    17L to BrowserTabsPreviewFrame(
                        sessionId = 17L,
                        preview = preview,
                        boundsInRoot = IntRect(0, 0, 1080, 2200),
                    ),
                ),
                dockFrames = emptyMap(),
                managerFrames = mapOf(
                    17L to BrowserTabsPreviewFrame(
                        sessionId = 17L,
                        preview = preview,
                        boundsInRoot = IntRect(92, 320, 988, 1180),
                    ),
                ),
                viewportWidthPx = 1080,
            ),
        ).isEqualTo(
            BrowserTabsTransitionState(
                transitionId = 2L,
                kind = BrowserTabsTransitionKind.RestoreFromManager,
                sessionId = 17L,
                preview = preview,
                sourceSurface = BrowserTabsTransitionSurface.Manager,
                targetSurface = BrowserTabsTransitionSurface.Active,
                sourceBounds = IntRect(92, 320, 988, 1180),
                targetBounds = IntRect(0, 0, 1080, 2200),
                dismissDirection = null,
            ),
        )
    }

    @Test
    fun browserTabsTransitionState_swipeDismissBuildsOffscreenTargetFromViewportAndDirection() {
        val preview = BrowserHostMinimizedPreview(
            kind = BrowserHostMinimizedKind.Browser,
            title = "linux.do",
            subtitle = "Browser",
        )
        val request = BrowserTabsTransitionRequest(
            transitionId = 3L,
            kind = BrowserTabsTransitionKind.DismissFromManager,
            sessionId = 23L,
            preview = preview,
            dismissDirection = BrowserTabsDismissDirection.Right,
        )

        assertThat(
            buildBrowserTabsTransitionState(
                request = request,
                activeFrames = emptyMap(),
                dockFrames = emptyMap(),
                managerFrames = mapOf(
                    23L to BrowserTabsPreviewFrame(
                        sessionId = 23L,
                        preview = preview,
                        boundsInRoot = IntRect(100, 240, 500, 920),
                    ),
                ),
                viewportWidthPx = 1080,
            ),
        ).isEqualTo(
            BrowserTabsTransitionState(
                transitionId = 3L,
                kind = BrowserTabsTransitionKind.DismissFromManager,
                sessionId = 23L,
                preview = preview,
                sourceSurface = BrowserTabsTransitionSurface.Manager,
                targetSurface = BrowserTabsTransitionSurface.Dismissed,
                sourceBounds = IntRect(100, 240, 500, 920),
                targetBounds = IntRect(1112, 240, 1512, 920),
                dismissDirection = BrowserTabsDismissDirection.Right,
            ),
        )
    }
}
