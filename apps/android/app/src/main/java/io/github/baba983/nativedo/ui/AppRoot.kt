package io.github.baba983.nativedo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.baba983.nativedo.NativeDoApplication
import io.github.baba983.nativedo.core.navigation.AppBrowserOpenRequest
import io.github.baba983.nativedo.core.navigation.AppBrowserPresentationAvailability
import io.github.baba983.nativedo.core.navigation.AppBrowserPresentationPolicy
import io.github.baba983.nativedo.core.navigation.AppBrowserPresentationTarget
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderContent
import io.github.baba983.nativedo.core.navigation.LinkNavigationDecision
import io.github.baba983.nativedo.core.navigation.LinkOpenPolicy
import io.github.baba983.nativedo.core.navigation.openInExternalApp
import io.github.baba983.nativedo.core.navigation.openInSystemBrowser
import io.github.baba983.nativedo.core.ui.BulletinHost
import io.github.baba983.nativedo.core.ui.BulletinHostState
import io.github.baba983.nativedo.core.ui.LocalBulletinHostState
import io.github.baba983.nativedo.feature.auth.ui.AuthRestoreGate
import io.github.baba983.nativedo.feature.auth.ui.shouldShowAuthRestoreGate
import io.github.baba983.nativedo.feature.browser.AppBrowserReaderScene
import io.github.baba983.nativedo.feature.browser.AppBrowserSheetContainer
import io.github.baba983.nativedo.navigation.NativeDoNavHost
import io.github.baba983.nativedo.navigation.RootDestination
import io.github.baba983.nativedo.ui.theme.NativeDoTheme
import io.github.baba983.nativedo.ui.theme.NativeDoEasing
import io.github.baba983.nativedo.ui.theme.ThemeMode
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private enum class BrowserTabsManagerMode {
    Hidden,
    Entering,
    Visible,
    Exiting,
}

internal fun shouldEnqueueCloseTransitionForBrowserTabsManagerDismiss(
    dockedSessionCount: Int,
): Boolean = dockedSessionCount > 1

@Composable
internal fun AppRoot() {
    val context = LocalContext.current
    val appContainer = remember(context) {
        (context.applicationContext as NativeDoApplication).appContainer
    }
    val sessionState by appContainer.sessionCoordinator.sessionState.collectAsStateWithLifecycle()
    var themeMode by rememberSaveable { mutableStateOf(ThemeMode.System) }
    var homeRefreshRequestToken by rememberSaveable { mutableIntStateOf(0) }
    var isHomeSearchActive by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(appContainer.sessionCoordinator) {
        appContainer.sessionCoordinator.start()
    }

    NativeDoTheme(themeMode = themeMode) {
        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        val navController = rememberNavController()
        val openLogin: () -> Unit = {
            if (navController.currentDestination?.route != "login") {
                navController.navigate("login")
            }
        }
        val bulletinHostState = remember { BulletinHostState() }
        val nativePageStackState = rememberNativePageStackState()
        val rootTabsHostState = rememberRootTabsHostState()
        val currentEntry = navController.currentBackStackEntryAsState().value
        val currentRoute = currentEntry?.destination?.route
        val cachedSessionSnapshot = appContainer.sessionStore.snapshot()
        val sessionSnapshot = sessionSnapshotForChrome(sessionState, cachedSessionSnapshot)
        var browserHostState by remember { mutableStateOf(BrowserHostState()) }
        var browserTabsManagerMode by remember { mutableStateOf(BrowserTabsManagerMode.Hidden) }
        var nextBrowserTabsTransitionId by rememberSaveable { mutableLongStateOf(1L) }
        var browserTabsTransitionRequest by remember { mutableStateOf<BrowserTabsTransitionRequest?>(null) }
        val browserTabsTransitionProgress = remember { Animatable(0f) }
        var pendingManagerRestoreSessionId by remember { mutableStateOf<Long?>(null) }
        var shouldCloseAllManagerSessions by remember { mutableStateOf(false) }
        val activePreviewFrames = remember { mutableStateMapOf<Long, BrowserTabsPreviewFrame>() }
        val dockPreviewFrames = remember { mutableStateMapOf<Long, BrowserTabsPreviewFrame>() }
        val managerPreviewFrames = remember { mutableStateMapOf<Long, BrowserTabsPreviewFrame>() }
        val isBrowserTabsManagerVisible = browserTabsManagerMode != BrowserTabsManagerMode.Hidden
        val profileAvatarLabel = sessionSnapshot?.username
        val profileAvatarTemplate = sessionSnapshot
            ?.avatarTemplate
            ?.takeIf { it.isNotBlank() }
        val browserPreferences by appContainer.appBrowserPreferenceStore.preferences.collectAsStateWithLifecycle()
        val linkOpenPolicy = remember(appContainer, browserPreferences) {
            LinkOpenPolicy(
                baseUrl = appContainer.baseUrl,
                restrictedDomains = browserPreferences.restrictedDomains,
                defaultExternalOpenMode = browserPreferences.defaultExternalOpenMode,
            )
        }
        val browserPresentationPolicy = remember(appContainer) {
            AppBrowserPresentationPolicy(appContainer.baseUrl)
        }
        val browserPresentationAvailability = remember {
            AppBrowserPresentationAvailability(
                supportsSheetBrowser = true,
            )
        }
        val openInAppBrowser: (AppBrowserOpenRequest) -> Unit = { request ->
            val target = browserPresentationPolicy.resolveTarget(
                request = request,
                availability = browserPresentationAvailability,
            )
            browserHostState = browserHostState.present(
                request = request,
                target = target,
            )
        }
        val openReader: (AppBrowserReaderContent, AppBrowserOpenRequest?) -> Unit = { content, sourceRequest ->
            browserHostState = browserHostState.presentReader(
                content = content,
                sourceBrowserRequest = sourceRequest ?: browserHostState.browserEntry?.request,
            )
        }
        val onOpenLink: (String) -> Unit = { url ->
                when (val decision = linkOpenPolicy.resolve(url)) {
                is LinkNavigationDecision.NativeTopic -> {
                    browserHostState = browserHostState.closeActiveSession()
                    nativePageStackState.presentTopic(decision.topicId)
                }

                is LinkNavigationDecision.NativeUser -> {
                    browserHostState = browserHostState.closeActiveSession()
                    nativePageStackState.presentUser(decision.username)
                }

                is LinkNavigationDecision.AppBrowser -> openInAppBrowser(
                    AppBrowserOpenRequest(url = decision.url),
                )

                is LinkNavigationDecision.ExternalApp -> {
                    val openedInExternalApp = context.openInExternalApp(decision.url)
                    if (!openedInExternalApp && decision.url.toHttpUrlOrNull() != null) {
                        openInAppBrowser(AppBrowserOpenRequest(url = decision.url))
                    }
                }

                is LinkNavigationDecision.SystemBrowser -> {
                    context.openInSystemBrowser(decision.url)
                }
            }
        }
        val selectedRootRoute = rootTabsHostState.selectedRoute
        val isBlockingNavigationLayout = shouldBlockNavigationLayout(currentRoute)
        val activeRoute = when {
            isBlockingNavigationLayout -> currentRoute
            nativePageStackState.hasPushedEntry -> nativePageStackState.currentRoutePattern
            currentRoute == null || currentRoute == RootDestination.rootContainerRoute -> selectedRootRoute
            else -> currentRoute
        }
        val topBarOwner = remember(
            currentRoute,
            browserHostState,
            nativePageStackState.currentRoutePattern,
        ) {
            resolveNativeDoTopBarOwner(
                currentRoute = currentRoute,
                overlayState = browserHostState,
                pushedEntryRoutePattern = nativePageStackState.currentRoutePattern,
            )
        }
        val keepRootBottomBarInBackground = remember(
            currentRoute,
            browserHostState,
            nativePageStackState.hasPushedEntry,
        ) {
            shouldKeepNativeDoRootBottomBarInBackground(
                currentRoute = currentRoute,
                overlayState = browserHostState,
                isPushedEntryShowing = nativePageStackState.hasPushedEntry,
            )
        }
        val hideBottomBarForHomeSearch = selectedRootRoute == RootDestination.Home.route &&
            isHomeSearchActive
        val resolvedShouldRenderRootBottomBar = shouldRenderNativeDoRootBottomBar(
            owner = topBarOwner,
            isHomeSearchActive = hideBottomBarForHomeSearch,
        ) || keepRootBottomBarInBackground
        val browserTabsShellState = remember(
            browserHostState,
            isBrowserTabsManagerVisible,
        ) {
            buildBrowserTabsShellState(
                hostState = browserHostState,
                isManagerVisible = isBrowserTabsManagerVisible,
            )
        }
        val browserTabsViewportWidthPx = remember(
            configuration.screenWidthDp,
            density,
        ) {
            with(density) {
                configuration.screenWidthDp.dp.roundToPx()
            }
        }
        val browserTabsTransitionState = buildBrowserTabsTransitionState(
            request = browserTabsTransitionRequest,
            activeFrames = activePreviewFrames,
            dockFrames = dockPreviewFrames,
            managerFrames = managerPreviewFrames,
            viewportWidthPx = browserTabsViewportWidthPx,
        )
        val browserTabsTransitionRenderSource = resolveBrowserTabsTransitionRenderSource(
            transition = browserTabsTransitionState,
            hasLiveSurface = browserTabsTransitionState
                ?.sessionId
                ?.let(browserHostState::findSession) != null,
        )
        val browserTabsLiveSurfaceTransitionState = buildBrowserTabsLiveSurfaceTransitionState(
            transition = browserTabsTransitionState,
            hasLiveSurface = browserTabsTransitionRenderSource ==
                BrowserTabsTransitionRenderSource.LiveSurface,
            progress = browserTabsTransitionProgress.value,
        )
        val hidesCollapsedDockForTransition = when (browserTabsTransitionState?.kind) {
            BrowserTabsTransitionKind.MinimizeToDock,
            BrowserTabsTransitionKind.RestoreFromDock,
            BrowserTabsTransitionKind.OpenManager,
            BrowserTabsTransitionKind.CloseManager,
            -> true

            null,
            BrowserTabsTransitionKind.RestoreFromManager,
            BrowserTabsTransitionKind.DismissFromManager,
            -> false
        }

        LaunchedEffect(browserTabsTransitionState?.transitionId) {
            val transition = browserTabsTransitionState ?: return@LaunchedEffect
            browserTabsTransitionProgress.snapTo(0f)
            browserTabsTransitionProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = browserTabsTransitionDurationMillis(transition.kind),
                    easing = NativeDoEasing.EaseOutQuint,
                ),
            )
            if (browserTabsTransitionRequest?.transitionId == transition.transitionId) {
                browserTabsTransitionRequest = null
            }
        }

        fun enqueueBrowserTabsTransition(
            kind: BrowserTabsTransitionKind,
            session: BrowserHostSession,
            dismissDirection: BrowserTabsDismissDirection? = null,
            sourceBoundsOverride: androidx.compose.ui.unit.IntRect? = null,
        ) {
            val preview = session.preview ?: return
            browserTabsTransitionRequest = BrowserTabsTransitionRequest(
                transitionId = nextBrowserTabsTransitionId,
                kind = kind,
                sessionId = session.stableId,
                preview = preview,
                dismissDirection = dismissDirection,
                sourceBoundsOverride = sourceBoundsOverride,
            )
            nextBrowserTabsTransitionId += 1L
        }

        fun minimizeActiveBrowserSession() {
            val activeSession = browserHostState.activeSession ?: return
            enqueueBrowserTabsTransition(
                kind = BrowserTabsTransitionKind.MinimizeToDock,
                session = activeSession,
            )
            browserHostState = browserHostState.minimize()
        }

        fun restoreLatestDockedSession() {
            val latestSession = browserHostState.dockedSessions.firstOrNull() ?: return
            enqueueBrowserTabsTransition(
                kind = BrowserTabsTransitionKind.RestoreFromDock,
                session = latestSession,
            )
            browserHostState = browserHostState.restoreDockedSession(latestSession.stableId)
        }

        fun openBrowserTabsManager() {
            val latestSession = browserHostState.dockedSessions.firstOrNull() ?: return
            if (browserHostState.dockedSessions.size <= 1) {
                return
            }
            pendingManagerRestoreSessionId = null
            shouldCloseAllManagerSessions = false
            browserTabsManagerMode = BrowserTabsManagerMode.Entering
            enqueueBrowserTabsTransition(
                kind = BrowserTabsTransitionKind.OpenManager,
                session = latestSession,
            )
        }

        fun dismissBrowserTabsManager() {
            pendingManagerRestoreSessionId = null
            shouldCloseAllManagerSessions = false
            if (shouldEnqueueCloseTransitionForBrowserTabsManagerDismiss(browserHostState.dockedSessions.size)) {
                browserHostState.dockedSessions.firstOrNull()?.let { latestSession ->
                    enqueueBrowserTabsTransition(
                        kind = BrowserTabsTransitionKind.CloseManager,
                        session = latestSession,
                    )
                }
            }
            browserTabsManagerMode = BrowserTabsManagerMode.Exiting
        }

        fun restoreManagerSession(sessionId: Long) {
            val dockedSession = browserHostState.dockedSessions.firstOrNull { it.stableId == sessionId } ?: return
            pendingManagerRestoreSessionId = null
            shouldCloseAllManagerSessions = false
            enqueueBrowserTabsTransition(
                kind = BrowserTabsTransitionKind.RestoreFromManager,
                session = dockedSession,
            )
            browserHostState = browserHostState.restoreDockedSession(sessionId)
            browserTabsManagerMode = BrowserTabsManagerMode.Exiting
        }

        fun dismissManagerSession(
            sessionId: Long,
            dismissDirection: BrowserTabsDismissDirection,
            sourceBoundsOverride: androidx.compose.ui.unit.IntRect?,
        ) {
            val dockedSession = browserHostState.dockedSessions.firstOrNull { it.stableId == sessionId } ?: return
            enqueueBrowserTabsTransition(
                kind = BrowserTabsTransitionKind.DismissFromManager,
                session = dockedSession,
                dismissDirection = dismissDirection,
                sourceBoundsOverride = sourceBoundsOverride,
            )
            browserHostState = browserHostState.closeDockedSession(sessionId)
        }

        LaunchedEffect(browserHostState.dockedSessions.size, browserTabsManagerMode) {
            if (
                browserHostState.dockedSessions.size <= 1 &&
                browserTabsManagerMode == BrowserTabsManagerMode.Visible
            ) {
                browserTabsManagerMode = BrowserTabsManagerMode.Hidden
                pendingManagerRestoreSessionId = null
                shouldCloseAllManagerSessions = false
            }
        }

        CompositionLocalProvider(LocalBulletinHostState provides bulletinHostState) {
            NativeDoChromeScaffold(
                bottomInsetHeight = browserTabsShellState.occupiedHeight,
                bottomBar = if (resolvedShouldRenderRootBottomBar) {
                    {
                        NativeDoBottomBarFade(
                            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
                        )
                        NativeDoBottomBar(
                            baseUrl = appContainer.baseUrl,
                            currentRoute = activeRoute,
                            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
                            profileAvatarLabel = profileAvatarLabel,
                            profileAvatarTemplate = profileAvatarTemplate,
                            onHomeRefreshRequest = { homeRefreshRequestToken += 1 },
                            onNavigate = rootTabsHostState::navigateTo,
                        )
                    }
                } else {
                    null
                },
                bottomDock = {
                    if (browserTabsShellState.showsCollapsedDock) {
                        BrowserTabsCollapsedDock(
                            sessions = browserHostState.dockedSessions,
                            dockAction = browserTabsShellState.dockAction,
                            onRestoreLatest = {
                                restoreLatestDockedSession()
                            },
                            onOpenManager = {
                                openBrowserTabsManager()
                            },
                            onCloseLatest = { sessionId ->
                                browserHostState = browserHostState.closeDockedSession(sessionId)
                            },
                            onPreviewFrameChange = { frame ->
                                dockPreviewFrames[frame.sessionId] = frame
                            },
                            modifier = Modifier
                                .align(androidx.compose.ui.Alignment.BottomCenter)
                                .graphicsLayer {
                                    alpha = if (hidesCollapsedDockForTransition) 0f else 1f
                                },
                        )
                    }
                },
                bulletinHost = {
                    BulletinHost(
                        hostState = bulletinHostState,
                        modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter),
                    )
                },
                overlay = {
                    if (!isBlockingNavigationLayout) {
                        NativePageStackHost(
                            state = nativePageStackState,
                            onSessionRecoveryRequired = openLogin,
                            onOpenLink = onOpenLink,
                        )
                    }
                    BrowserHost(
                        state = browserHostState,
                        browserContent = { entry, isForeground ->
                            AppBrowserSheetContainer(
                                entryId = entry.id,
                                url = entry.request.url,
                                foregroundActivationKey = entry.foregroundActivationKey,
                                onOpenLink = onOpenLink,
                                onOpenReader = openReader,
                                onMinimize = {
                                    minimizeActiveBrowserSession()
                                },
                                onClose = {
                                    browserHostState = browserHostState.closeActiveSession()
                                },
                                onPreviewChanged = { preview ->
                                    browserHostState = browserHostState.updateSessionPreview(
                                        sessionId = entry.id,
                                        preview = preview,
                                    )
                                },
                                isForeground = isForeground,
                            )
                        },
                        readerContent = { request, isForeground ->
                            AppBrowserReaderScene(
                                entryId = request.id,
                                content = request.content,
                                foregroundActivationKey = request.foregroundActivationKey,
                                restoredFromDock = request.restoredFromDock,
                                onMinimize = {
                                    minimizeActiveBrowserSession()
                                },
                                onClose = {
                                    browserHostState = browserHostState.dismissReader()
                                },
                                onPreviewChanged = { preview ->
                                    browserHostState = browserHostState.updateSessionPreview(
                                        sessionId = request.id,
                                        preview = preview,
                                    )
                                },
                                onOpenLink = onOpenLink,
                                isForeground = isForeground,
                            )
                        },
                        onActivePreviewFrameChange = { frame ->
                            activePreviewFrames[frame.sessionId] = frame
                        },
                        liveSurfaceTransition = browserTabsLiveSurfaceTransitionState,
                    )
                    if (isBrowserTabsManagerVisible &&
                        browserHostState.activeSession == null &&
                        browserHostState.dockedSessions.size > 1
                    ) {
                        BrowserTabsManager(
                            sessions = browserHostState.dockedSessions,
                            targetOpen = browserTabsManagerMode != BrowserTabsManagerMode.Exiting,
                            onDismiss = {
                                dismissBrowserTabsManager()
                            },
                            onRestoreSession = { sessionId ->
                                restoreManagerSession(sessionId)
                            },
                            onCloseSession = { sessionId ->
                                browserHostState = browserHostState.closeDockedSession(sessionId)
                            },
                            onSwipeDismissSession = { sessionId, dismissDirection, sourceBoundsOverride ->
                                dismissManagerSession(
                                    sessionId = sessionId,
                                    dismissDirection = dismissDirection,
                                    sourceBoundsOverride = sourceBoundsOverride,
                                )
                            },
                            onPreviewFrameChange = { frame ->
                                managerPreviewFrames[frame.sessionId] = frame
                            },
                            onCloseAll = {
                                pendingManagerRestoreSessionId = null
                                shouldCloseAllManagerSessions = true
                                browserTabsManagerMode = BrowserTabsManagerMode.Exiting
                            },
                            onOpenAnimationFinished = {
                                if (browserTabsManagerMode == BrowserTabsManagerMode.Entering) {
                                    browserTabsManagerMode = BrowserTabsManagerMode.Visible
                                }
                            },
                            onCloseAnimationFinished = {
                                val closeAllSessions = shouldCloseAllManagerSessions
                                pendingManagerRestoreSessionId = null
                                shouldCloseAllManagerSessions = false
                                browserTabsManagerMode = BrowserTabsManagerMode.Hidden
                                if (closeAllSessions) {
                                    browserHostState = browserHostState.closeBrowserStack()
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    BrowserTabsTransitionOverlay(
                        transition = browserTabsTransitionState,
                        progress = browserTabsTransitionProgress.value,
                        renderSource = browserTabsTransitionRenderSource,
                    )
                    if (shouldShowAuthRestoreGate(sessionState)) {
                        AuthRestoreGate(
                            snapshot = sessionSnapshot,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                },
            ) { paddingValues ->
                Box(modifier = Modifier.fillMaxSize()) {
                    NativeDoNavHost(
                        navController = navController,
                        contentPadding = paddingValues,
                        onOpenLogin = openLogin,
                        rootContainer = {
                            RootTabsHost(
                                state = rootTabsHostState,
                                homeRefreshRequestToken = homeRefreshRequestToken,
                                themeMode = themeMode,
                                onThemeModeSelected = {
                                    themeMode = it
                                },
                                onHomeSearchModeChanged = { isHomeSearchActive = it },
                                onOpenLogin = openLogin,
                                onOpenTopic = nativePageStackState::presentTopic,
                                onOpenUser = nativePageStackState::presentUser,
                                onOpenLink = onOpenLink,
                            )
                        },
                        onOpenTopic = nativePageStackState::presentTopic,
                        onOpenUser = nativePageStackState::presentUser,
                    )
                }
            }
        }
    }
}
