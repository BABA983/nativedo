package io.github.baba983.nativedo.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.navigation.AppBrowserOpenRequest
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderContent
import io.github.baba983.nativedo.core.navigation.AppBrowserPresentationAvailability
import io.github.baba983.nativedo.core.navigation.AppBrowserPresentationPolicy
import io.github.baba983.nativedo.core.navigation.AppBrowserPresentationTarget
import io.github.baba983.nativedo.navigation.RootDestination
import org.junit.Test

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
            fullScreenRequest = AppBrowserOpenRequest(url = "https://example.com/article"),
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
            sheetRequest = AppBrowserOpenRequest(url = "https://example.com/article"),
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
                    sheetRequest = AppBrowserOpenRequest(url = "https://example.com/article"),
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
                    sheetRequest = AppBrowserOpenRequest(url = "https://example.com/article"),
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
            sheetRequest = sheetRequest,
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
    fun browserReaderDialogProperties_drawBehindSystemBars_andDisableImplicitDismiss() {
        val properties = browserReaderDialogProperties()

        assertThat(properties.dismissOnBackPress).isFalse()
        assertThat(properties.dismissOnClickOutside).isFalse()
        assertThat(properties.usePlatformDefaultWidth).isFalse()
        assertThat(properties.decorFitsSystemWindows).isFalse()
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
        assertThat(overlayState.sheetRequest?.url).isEqualTo("https://example.com/article")
        assertThat(overlayState.readerRequest).isNull()
    }

    @Test
    fun browserHostState_present_usesFullScreenForFullRouteBrowserRequest() {
        val request = AppBrowserOpenRequest(
            url = "https://example.com/article",
            preferredTarget = AppBrowserPresentationTarget.FullRoute,
        )

        val overlayState = BrowserHostState().present(
            request = request,
            target = AppBrowserPresentationTarget.FullRoute,
        )

        assertThat(overlayState.fullScreenRequest).isEqualTo(request)
        assertThat(overlayState.sheetRequest).isNull()
        assertThat(overlayState.readerRequest).isNull()
        assertThat(overlayState.activeTopBarOwner).isEqualTo(NativeDoTopBarOwner.Browser)
    }

    @Test
    fun browserOverlayState_presentReader_preservesSourceBrowserRequestForReaderRestoration() {
        val sourceRequest = AppBrowserOpenRequest(url = "https://example.com/article")
        val overlayState = BrowserHostState(
            sheetRequest = sourceRequest,
        ).presentReader(
            content = AppBrowserReaderContent(
                url = "https://example.com/article/instant-view",
                title = "Example Reader",
                bodyText = "Reader body",
            ),
            sourceBrowserRequest = sourceRequest,
        )

        assertThat(overlayState.readerRequest?.sourceBrowserRequest).isEqualTo(sourceRequest)
        assertThat(overlayState.sheetRequest).isEqualTo(sourceRequest)
    }

    @Test
    fun browserOverlayState_presentReader_withoutSourceFallsBackToCurrentSheetRequest() {
        val sourceRequest = AppBrowserOpenRequest(url = "https://example.com/article")
        val overlayState = BrowserHostState(
            sheetRequest = sourceRequest,
        ).presentReader(
            content = AppBrowserReaderContent(
                url = "https://example.com/article/instant-view",
                title = "Example Reader",
                bodyText = "Reader body",
            ),
        )

        assertThat(overlayState.readerRequest?.sourceBrowserRequest).isEqualTo(sourceRequest)
    }

    @Test
    fun browserHostState_presentReader_withoutSourceFallsBackToCurrentFullScreenRequest() {
        val sourceRequest = AppBrowserOpenRequest(url = "https://example.com/article")
        val overlayState = BrowserHostState(
            fullScreenRequest = sourceRequest,
        ).presentReader(
            content = AppBrowserReaderContent(
                url = "https://example.com/article/instant-view",
                title = "Example Reader",
                bodyText = "Reader body",
            ),
        )

        assertThat(overlayState.readerRequest?.sourceBrowserRequest).isEqualTo(sourceRequest)
        assertThat(overlayState.fullScreenRequest).isEqualTo(sourceRequest)
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
}
