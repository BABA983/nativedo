package io.github.baba983.nativedo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import io.github.baba983.nativedo.ui.theme.ThemeMode
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

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
            when (
                val target = browserPresentationPolicy.resolveTarget(
                    request = request,
                    availability = browserPresentationAvailability,
                )
            ) {
                AppBrowserPresentationTarget.Sheet,
                AppBrowserPresentationTarget.InstantView,
                AppBrowserPresentationTarget.FullRoute -> {
                    browserHostState = browserHostState.present(
                        request = request,
                        target = target,
                    )
                }
            }
        }
        val openReader: (AppBrowserReaderContent, AppBrowserOpenRequest?) -> Unit = { content, sourceRequest ->
            browserHostState = browserHostState.presentReader(
                content = content,
                sourceBrowserRequest = sourceRequest
                    ?: browserHostState.sheetRequest
                    ?: browserHostState.fullScreenRequest,
            )
        }
        val onOpenLink: (String) -> Unit = { url ->
            when (val decision = linkOpenPolicy.resolve(url)) {
                is LinkNavigationDecision.NativeTopic -> {
                    browserHostState = browserHostState.dismissAll()
                    nativePageStackState.presentTopic(decision.topicId)
                }

                is LinkNavigationDecision.NativeUser -> {
                    browserHostState = browserHostState.dismissAll()
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

        CompositionLocalProvider(LocalBulletinHostState provides bulletinHostState) {
            NativeDoChromeScaffold(
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
                        fullScreenContent = { request ->
                            io.github.baba983.nativedo.feature.browser.AppBrowserRoute(
                                url = request.url,
                                onOpenLink = onOpenLink,
                                onOpenReader = openReader,
                                onClose = {
                                    browserHostState = browserHostState.dismissFullScreen()
                                },
                                presentationMode = io.github.baba983.nativedo.feature.browser.AppBrowserContainerMode.FullRoute,
                                modifier = Modifier.fillMaxSize(),
                            )
                        },
                        sheetContent = { request ->
                            AppBrowserSheetContainer(
                                url = request.url,
                                onOpenLink = onOpenLink,
                                onOpenReader = openReader,
                                onClose = {
                                    browserHostState = browserHostState.dismissAll()
                                },
                            )
                        },
                        readerContent = { request ->
                            AppBrowserReaderScene(
                                content = request.content,
                                onClose = {
                                    browserHostState = browserHostState.dismissReader()
                                },
                                onOpenLink = onOpenLink,
                            )
                        },
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
