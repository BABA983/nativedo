package io.github.baba983.nativedo.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.baba983.nativedo.NativeDoApplication
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.session.SessionSnapshot
import io.github.baba983.nativedo.core.ui.BulletinHost
import io.github.baba983.nativedo.core.ui.BulletinHostState
import io.github.baba983.nativedo.core.ui.LocalBulletinHostState
import io.github.baba983.nativedo.core.ui.NativeDoAnimatedLabel
import io.github.baba983.nativedo.core.ui.NativeDoBackIcon
import io.github.baba983.nativedo.core.ui.NativeDoContextualOptions
import io.github.baba983.nativedo.core.ui.NativeDoGlassBar
import io.github.baba983.nativedo.core.ui.NativeDoGlassTab
import io.github.baba983.nativedo.core.ui.NativeDoPopupMenuItem
import io.github.baba983.nativedo.core.ui.UserAvatar
import io.github.baba983.nativedo.feature.auth.ui.AuthRestoreGate
import io.github.baba983.nativedo.feature.auth.ui.shouldShowAuthRestoreGate
import io.github.baba983.nativedo.navigation.APP_BROWSER_ROUTE
import io.github.baba983.nativedo.navigation.NativeDoNavHost
import io.github.baba983.nativedo.navigation.RootDestination
import io.github.baba983.nativedo.navigation.buildAppBrowserRoute
import io.github.baba983.nativedo.ui.theme.NativeDoChromeShape
import io.github.baba983.nativedo.ui.theme.NativeDoDuration
import io.github.baba983.nativedo.ui.theme.NativeDoEasing
import io.github.baba983.nativedo.ui.theme.NativeDoGlassRole
import io.github.baba983.nativedo.ui.theme.NativeDoPillShape
import io.github.baba983.nativedo.ui.theme.NativeDoTheme
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import io.github.baba983.nativedo.ui.theme.ThemeMode
import io.github.baba983.nativedo.core.navigation.LinkNavigationDecision
import io.github.baba983.nativedo.core.navigation.LinkOpenPolicy
import io.github.baba983.nativedo.core.navigation.openInExternalApp
import io.github.baba983.nativedo.core.navigation.openInSystemBrowser
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeDoApp() {
    val context = LocalContext.current
    val appContainer = remember(context) {
        (context.applicationContext as NativeDoApplication).appContainer
    }
    val sessionState by appContainer.sessionCoordinator.sessionState.collectAsStateWithLifecycle()
    var themeMode by rememberSaveable { mutableStateOf(ThemeMode.System) }
    var homeHeaderSearchVisible by rememberSaveable { mutableStateOf(false) }
    var homeSearchModeActive by rememberSaveable { mutableStateOf(false) }
    var homeSearchRequestToken by rememberSaveable { mutableIntStateOf(0) }
    var homeRefreshRequestToken by rememberSaveable { mutableIntStateOf(0) }
    var rootActionModeSelectionCount by rememberSaveable { mutableIntStateOf(0) }
    var rootContextualOptionsExpanded by rememberSaveable { mutableStateOf(false) }
    var topicHeaderTitle by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(appContainer.sessionCoordinator) {
        appContainer.sessionCoordinator.start()
    }

    NativeDoTheme(themeMode = themeMode) {
        val navController = rememberNavController()
        val bulletinHostState = remember { BulletinHostState() }
        val currentEntry = navController.currentBackStackEntryAsState().value
        val currentRoute = currentEntry?.destination?.route
        val cachedSessionSnapshot = appContainer.sessionStore.snapshot()
        val sessionSnapshot = sessionSnapshotForChrome(sessionState, cachedSessionSnapshot)
        val profileAvatarLabel = sessionSnapshot?.username
        val profileAvatarTemplate = sessionSnapshot
            ?.avatarTemplate
            ?.takeIf { it.isNotBlank() }
        val rootPagerState = rememberPagerState(
            initialPage = RootDestination.bottomTabs.indexOf(RootDestination.start),
        ) { RootDestination.bottomTabs.size }
        val coroutineScope = rememberCoroutineScope()
        val linkOpenPolicy = remember(appContainer) { LinkOpenPolicy(appContainer.baseUrl) }
        val openInAppBrowser: (String) -> Unit = { browserUrl ->
            val encoded = URLEncoder.encode(browserUrl, "UTF-8")
            navController.navigate(buildAppBrowserRoute(encoded))
        }
        val onOpenLink: (String) -> Unit = { url ->
            when (val decision = linkOpenPolicy.resolve(url)) {
                is LinkNavigationDecision.NativeTopic -> navController.navigate("topic/${decision.topicId}")
                is LinkNavigationDecision.NativeUser -> navController.navigate("user/${decision.username}")
                is LinkNavigationDecision.AppBrowser -> openInAppBrowser(decision.url)
                is LinkNavigationDecision.ExternalApp -> {
                    val openedInExternalApp = context.openInExternalApp(decision.url)
                    if (!openedInExternalApp && decision.url.toHttpUrlOrNull() != null) {
                        openInAppBrowser(decision.url)
                    }
                }
                is LinkNavigationDecision.SystemBrowser -> {
                    context.openInSystemBrowser(decision.url)
                }
            }
        }
        val selectedRootRoute = RootDestination.bottomTabs
            .getOrNull(rootPagerState.currentPage)
            ?.route
            ?: RootDestination.start.route
        val activeRoute = when (currentRoute) {
            null,
            RootDestination.rootContainerRoute -> selectedRootRoute
            else -> currentRoute
        }
        val isAppBrowserRoute = currentRoute == APP_BROWSER_ROUTE
        val isRootRoute = currentRoute == null || currentRoute == RootDestination.rootContainerRoute
        val rootChromeModel = remember(
            isRootRoute,
            activeRoute,
            homeHeaderSearchVisible,
            homeSearchModeActive,
            rootActionModeSelectionCount,
            rootContextualOptionsExpanded,
        ) {
            buildNativeDoRootChromeModel(
                isRootRoute = isRootRoute,
                activeRoute = activeRoute,
                homeHeaderSearchVisible = homeHeaderSearchVisible,
                isHomeSearchMode = homeSearchModeActive,
                actionModeSelectionCount = rootActionModeSelectionCount,
                isContextualOptionsExpanded = rootContextualOptionsExpanded,
            )
        }

        LaunchedEffect(isRootRoute, homeSearchModeActive, rootChromeModel.showContextualOptionsAction) {
            if (!isRootRoute || homeSearchModeActive || !rootChromeModel.showContextualOptionsAction) {
                rootContextualOptionsExpanded = false
            }
        }

        CompositionLocalProvider(LocalBulletinHostState provides bulletinHostState) {
            NativeDoChromeScaffold(
                topBar = if (!isAppBrowserRoute) {
                    {
                        NativeDoTopBar(
                            currentRoute = activeRoute,
                            rootChrome = rootChromeModel,
                            topicTitleOverride = topicHeaderTitle,
                            showBack = !isRootRoute,
                            themeMode = themeMode,
                            onHomeRefreshClick = { homeRefreshRequestToken += 1 },
                            onHomeSearchClick = { homeSearchRequestToken += 1 },
                            onThemeModeSelected = {
                                themeMode = it
                                rootContextualOptionsExpanded = false
                            },
                            onToggleContextualOptions = {
                                rootContextualOptionsExpanded = !rootContextualOptionsExpanded
                            },
                            onDismissContextualOptions = {
                                rootContextualOptionsExpanded = false
                            },
                            onExitActionMode = {
                                rootActionModeSelectionCount = 0
                                rootContextualOptionsExpanded = false
                            },
                            onBackClick = { navController.popBackStack() },
                        )
                    }
                } else {
                    null
                },
                bottomBar = if (isRootRoute) {
                    {
                        NativeDoBottomBarFade(
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                        NativeDoBottomBar(
                            baseUrl = appContainer.baseUrl,
                            currentRoute = activeRoute,
                            modifier = Modifier.align(Alignment.BottomCenter),
                            profileAvatarLabel = profileAvatarLabel,
                            profileAvatarTemplate = profileAvatarTemplate,
                            onHomeRefreshRequest = { homeRefreshRequestToken += 1 },
                            onNavigate = { route ->
                                val page = RootDestination.bottomTabs.indexOfFirst { it.route == route }
                                if (page != -1 && page != rootPagerState.currentPage) {
                                    coroutineScope.launch {
                                        rootPagerState.animateScrollToPage(page)
                                    }
                                }
                            },
                        )
                    }
                } else {
                    null
                },
                bulletinHost = {
                    BulletinHost(
                        hostState = bulletinHostState,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                },
                overlay = {
                    if (shouldShowAuthRestoreGate(sessionState)) {
                        AuthRestoreGate(
                            snapshot = sessionSnapshot,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                },
            ) { paddingValues ->
                NativeDoNavHost(
                    navController = navController,
                    contentPadding = paddingValues,
                    rootPagerState = rootPagerState,
                    homeSearchRequestToken = homeSearchRequestToken,
                    homeRefreshRequestToken = homeRefreshRequestToken,
                    onHomeHeaderSearchVisibilityChanged = { homeHeaderSearchVisible = it },
                    onHomeSearchModeChanged = { homeSearchModeActive = it },
                    onTopicHeaderTitleChanged = { topicHeaderTitle = it },
                    onOpenLink = onOpenLink,
                )
            }
        }
    }
}

@Composable
private fun NativeDoTopBar(
    currentRoute: String?,
    rootChrome: NativeDoRootChromeModel,
    topicTitleOverride: String?,
    showBack: Boolean,
    themeMode: ThemeMode,
    onHomeRefreshClick: () -> Unit,
    onHomeSearchClick: () -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onToggleContextualOptions: () -> Unit,
    onDismissContextualOptions: () -> Unit,
    onExitActionMode: () -> Unit,
    onBackClick: () -> Unit,
) {
    val colors = NativeDoThemeExtras.colors
    val titleInteractionSource = remember { MutableInteractionSource() }
    val showActionModeBack = rootChrome.actionModeSelectionCount > 0
    val showLeadingBack = showBack || showActionModeBack
    val titleRefreshEnabled = shouldRequestHomeRefreshFromTitleTap(
        currentRoute = currentRoute,
        showBack = showBack,
    ) && rootChrome.scene == NativeDoRootChromeScene.Normal

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        NativeDoGlassBar(
            modifier = Modifier.fillMaxWidth(),
            minHeight = AppTopBarRowMinHeight,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = AppTopBarHorizontalPadding,
                vertical = AppTopBarVerticalPadding,
            ),
            sectionSpacing = 12.dp,
            leading = if (showLeadingBack) {
                {
                    IconButton(
                        onClick = if (showActionModeBack) {
                            onExitActionMode
                        } else {
                            onBackClick
                        },
                    ) {
                        NativeDoBackIcon(
                            progress = 0f,
                            contentDescription = null,
                        )
                    }
                }
            } else {
                null
            },
            title = {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(NativeDoPillShape)
                        .clickable(
                            enabled = titleRefreshEnabled,
                            interactionSource = titleInteractionSource,
                            indication = null,
                            onClick = onHomeRefreshClick,
                        )
                        .padding(
                            start = if (showLeadingBack) 4.dp else 0.dp,
                            end = 12.dp,
                        )
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    NativeDoAnimatedLabel(
                        title = resolveNativeDoRootChromeTitle(
                            defaultTitle = topBarTitle(
                                currentRoute = currentRoute,
                                topicTitleOverride = topicTitleOverride,
                            ),
                            actionModeSelectionCount = rootChrome.actionModeSelectionCount,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        titleStyle = MaterialTheme.typography.titleLarge,
                        titleColor = colors.textPrimary,
                    )
                }
            },
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (rootChrome.reserveHomeSearchSlot) {
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (rootChrome.showHomeSearchAction) {
                                IconButton(
                                    onClick = onHomeSearchClick,
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Search,
                                        contentDescription = stringResource(R.string.tab_search),
                                        tint = colors.textPrimary,
                                    )
                                }
                            }
                        }
                    }

                    if (rootChrome.showContextualOptionsAction) {
                        Box(
                            modifier = Modifier.size(44.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            IconButton(
                                onClick = onToggleContextualOptions,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = stringResource(R.string.app_browser_more_actions),
                                    tint = colors.textPrimary,
                                )
                            }
                            NativeDoContextualOptions(
                                expanded = rootChrome.isContextualOptionsExpanded,
                                onDismissRequest = onDismissContextualOptions,
                                width = 220.dp,
                                itemCount = ThemeMode.entries.size,
                            ) { popupProgress ->
                                ThemeMode.entries.forEachIndexed { index, mode ->
                                    NativeDoPopupMenuItem(
                                        label = themeModeLabel(mode),
                                        index = index,
                                        itemCount = ThemeMode.entries.size,
                                        popupProgress = popupProgress,
                                        onClick = { onThemeModeSelected(mode) },
                                        trailing = if (mode == themeMode) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Outlined.Check,
                                                    contentDescription = null,
                                                    tint = colors.textSecondary,
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            },
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun NativeDoBottomBar(
    baseUrl: String,
    currentRoute: String?,
    modifier: Modifier = Modifier,
    profileAvatarLabel: String?,
    profileAvatarTemplate: String?,
    onHomeRefreshRequest: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NativeDoGlassSurface(
            modifier = Modifier,
            shape = NativeDoChromeShape,
            glassSpec = NativeDoThemeExtras.glass(NativeDoGlassRole.Chrome),
        ) {
            Row(
                modifier = Modifier
                    .height(56.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RootDestination.bottomTabs.forEach { destination ->
                    NativeDoBottomTab(
                        baseUrl = baseUrl,
                        modifier = Modifier.widthIn(min = 68.dp),
                        destination = destination,
                        profileAvatarLabel = profileAvatarLabel,
                        profileAvatarTemplate = profileAvatarTemplate,
                        selected = currentRoute == destination.route,
                        onDoubleClick = if (
                            shouldRequestHomeRefreshFromBottomTabDoubleTap(
                                destination = destination,
                                currentRoute = currentRoute,
                            )
                        ) onHomeRefreshRequest else null,
                        onClick = { onNavigate(destination.route) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NativeDoBottomTab(
    baseUrl: String,
    modifier: Modifier = Modifier,
    destination: RootDestination,
    profileAvatarLabel: String?,
    profileAvatarTemplate: String?,
    selected: Boolean,
    onDoubleClick: (() -> Unit)?,
    onClick: () -> Unit,
    ) {
        NativeDoGlassTab(
            selected = selected,
            onClick = onClick,
            onDoubleClick = onDoubleClick,
            modifier = modifier.widthIn(min = 68.dp),
        ) { _, contentColor ->
        Box(
            modifier = Modifier
                .height(48.dp)
                .padding(horizontal = 4.dp),
        ) {
            if (shouldRenderProfileAvatarTab(destination, profileAvatarLabel, profileAvatarTemplate)) {
                UserAvatar(
                    label = requireNotNull(profileAvatarLabel),
                    avatarTemplate = requireNotNull(profileAvatarTemplate),
                    baseUrl = baseUrl,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp),
                    size = 24.dp,
                )
            } else {
                Icon(
                    imageVector = destination.icon,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                        .size(24.dp),
                    tint = contentColor,
                )
            }
            Text(
                text = stringResource(destination.labelRes),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 28.dp),
                color = contentColor,
                fontSize = 12.sp,
                lineHeight = 13.sp,
                maxLines = 1,
            )
        }
    }
}

internal fun shouldRequestHomeRefreshFromTitleTap(
    currentRoute: String?,
    showBack: Boolean,
): Boolean = !showBack && currentRoute == RootDestination.Home.route

internal fun shouldRequestHomeRefreshFromBottomTabDoubleTap(
    destination: RootDestination,
    currentRoute: String?,
): Boolean = destination == RootDestination.Home && currentRoute == RootDestination.Home.route

internal fun shouldRenderProfileAvatarTab(
    destination: RootDestination,
    profileAvatarLabel: String?,
    profileAvatarTemplate: String?,
): Boolean {
    return destination == RootDestination.Profile &&
        !profileAvatarLabel.isNullOrBlank() &&
        !profileAvatarTemplate.isNullOrBlank()
}

@Composable
private fun NativeDoBottomBarFade(
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        colors.backgroundBase.copy(alpha = 0.08f),
                        colors.backgroundBase.copy(alpha = 0.34f),
                        colors.backgroundBase.copy(alpha = 0.74f),
                    ),
                ),
            ),
    )
}

@Composable
private fun topBarTitle(
    currentRoute: String?,
    topicTitleOverride: String?,
): String = when (currentRoute) {
    "topic/{topicId}" -> topicRouteTitleOverride(
        currentRoute = currentRoute,
        topicTitleOverride = topicTitleOverride,
    ) ?: stringResource(R.string.screen_topic)
    RootDestination.Search.route -> stringResource(R.string.tab_search)
    RootDestination.Profile.route -> stringResource(R.string.tab_profile)
    "user/{username}" -> stringResource(R.string.tab_profile)
    "login" -> stringResource(R.string.screen_sign_in)
    "app-browser?url={url}" -> ""
    else -> stringResource(R.string.app_name)
}

internal fun topicRouteTitleOverride(
    currentRoute: String?,
    topicTitleOverride: String?,
): String? {
    return topicTitleOverride
        ?.takeUnless(String::isBlank)
        ?.takeIf { currentRoute == "topic/{topicId}" }
}

@Composable
private fun themeModeLabel(themeMode: ThemeMode): String = when (themeMode) {
    ThemeMode.System -> stringResource(R.string.theme_mode_system)
    ThemeMode.Light -> stringResource(R.string.theme_mode_light)
    ThemeMode.Dark -> stringResource(R.string.theme_mode_dark)
}

internal fun sessionSnapshotForChrome(
    sessionState: AppSessionState,
    cachedSnapshot: SessionSnapshot? = null,
): SessionSnapshot? = when (sessionState) {
    AppSessionState.Anonymous -> null
    AppSessionState.Unknown -> cachedSnapshot
    is AppSessionState.Restoring -> sessionState.snapshot
    is AppSessionState.Authenticated -> sessionState.snapshot
    is AppSessionState.Expired -> sessionState.snapshot
}
