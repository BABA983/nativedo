package io.github.baba983.nativedo.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import io.github.baba983.nativedo.navigation.NativeDoNavHost
import io.github.baba983.nativedo.navigation.RootDestination
import io.github.baba983.nativedo.ui.theme.NativeDoChromeShape
import io.github.baba983.nativedo.ui.theme.NativeDoPillShape
import io.github.baba983.nativedo.ui.theme.NativeDoTheme
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import io.github.baba983.nativedo.ui.theme.ThemeMode
import kotlinx.coroutines.launch

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
    var homeSearchRequestToken by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(appContainer) {
        appContainer.sessionCoordinator.restoreSession()
    }

    NativeDoTheme(themeMode = themeMode) {
        val navController = rememberNavController()
        val currentEntry = navController.currentBackStackEntryAsState().value
        val currentRoute = currentEntry?.destination?.route
        val chromeState = remember(sessionState) { NativeDoChromeState.from(sessionState) }
        val rootPagerState = rememberPagerState(
            initialPage = RootDestination.bottomTabs.indexOf(RootDestination.start),
        ) { RootDestination.bottomTabs.size }
        val coroutineScope = rememberCoroutineScope()
        val selectedRootRoute = RootDestination.bottomTabs
            .getOrNull(rootPagerState.currentPage)
            ?.route
            ?: RootDestination.start.route
        val activeRoute = when (currentRoute) {
            null,
            RootDestination.rootContainerRoute -> selectedRootRoute
            else -> currentRoute
        }
        val isRootRoute = currentRoute == null || currentRoute == RootDestination.rootContainerRoute

        Box(modifier = Modifier.fillMaxSize()) {
            NativeDoBackground(modifier = Modifier.fillMaxSize())

            Scaffold(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    NativeDoTopBar(
                        currentRoute = activeRoute,
                        chromeState = chromeState,
                        showBack = !isRootRoute,
                        reserveHomeSearchSlot = isRootRoute && activeRoute == RootDestination.Home.route,
                        showHomeSearchAction = isRootRoute &&
                            activeRoute == RootDestination.Home.route &&
                            homeHeaderSearchVisible,
                        themeMode = themeMode,
                        onHomeSearchClick = { homeSearchRequestToken += 1 },
                        onThemeModeClick = { themeMode = themeMode.next() },
                        onBackClick = { navController.popBackStack() },
                    )
                },
            ) { paddingValues ->
                NativeDoNavHost(
                    navController = navController,
                    contentPadding = paddingValues,
                    rootPagerState = rootPagerState,
                    homeSearchRequestToken = homeSearchRequestToken,
                    onHomeHeaderSearchVisibilityChanged = { homeHeaderSearchVisible = it },
                )
            }

            if (isRootRoute) {
                NativeDoBottomBarFade(
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                NativeDoBottomBar(
                    currentRoute = activeRoute,
                    modifier = Modifier.align(Alignment.BottomCenter),
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
        }
    }
}

@Composable
private fun NativeDoTopBar(
    currentRoute: String?,
    chromeState: NativeDoChromeState,
    showBack: Boolean,
    reserveHomeSearchSlot: Boolean,
    showHomeSearchAction: Boolean,
    themeMode: ThemeMode,
    onHomeSearchClick: () -> Unit,
    onThemeModeClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    val colors = NativeDoThemeExtras.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = null,
                    )
                }
            }

            Text(
                text = routeTitle(currentRoute = currentRoute, chromeState = chromeState),
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = if (showBack) 4.dp else 0.dp,
                        end = 12.dp,
                    ),
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (reserveHomeSearchSlot) {
                    Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (showHomeSearchAction) {
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

                Box(
                    modifier = Modifier
                        .clip(NativeDoPillShape)
                        .background(colors.backgroundElevated.copy(alpha = 0.82f))
                        .clickable(onClick = onThemeModeClick)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = themeModeLabel(themeMode),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                    )
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun NativeDoBottomBar(
    currentRoute: String?,
    modifier: Modifier = Modifier,
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
            strong = true,
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
                        modifier = Modifier.widthIn(min = 68.dp),
                        destination = destination,
                        selected = currentRoute == destination.route,
                        onClick = { onNavigate(destination.route) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NativeDoBottomTab(
    modifier: Modifier = Modifier,
    destination: RootDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = NativeDoThemeExtras.colors
    val interactionSource = remember { MutableInteractionSource() }
    val iconColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else colors.textSecondary,
        animationSpec = tween(durationMillis = 220),
        label = "bottom_tab_icon",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else colors.textSecondary,
        animationSpec = tween(durationMillis = 220),
        label = "bottom_tab_text",
    )
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "bottom_tab_background",
    )
    val backgroundScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.6f,
        animationSpec = tween(durationMillis = 220),
        label = "bottom_tab_background_scale",
    )

    Box(
        modifier = modifier
            .widthIn(min = 68.dp)
            .height(48.dp)
            .clip(NativeDoPillShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 2.dp, vertical = 3.dp)
                .graphicsLayer(
                    scaleX = backgroundScale,
                    scaleY = backgroundScale,
                )
                .clip(NativeDoPillShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.09f * backgroundAlpha)),
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 4.dp),
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
                    .size(24.dp),
                tint = iconColor,
            )
            Text(
                text = stringResource(destination.labelRes),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 28.dp),
                color = textColor,
                fontSize = 12.sp,
                lineHeight = 13.sp,
                maxLines = 1,
            )
        }
    }
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
private fun routeTitle(
    currentRoute: String?,
    chromeState: NativeDoChromeState,
): String = when (currentRoute) {
    RootDestination.Search.route -> stringResource(R.string.tab_search)
    RootDestination.Profile.route -> chromeState.title.ifBlank { stringResource(R.string.tab_profile) }
    "topic/{topicId}" -> stringResource(R.string.screen_topic)
    "user/{username}" -> stringResource(R.string.tab_profile)
    "login" -> stringResource(R.string.screen_sign_in)
    else -> stringResource(R.string.app_name)
}

@Composable
private fun themeModeLabel(themeMode: ThemeMode): String = when (themeMode) {
    ThemeMode.System -> stringResource(R.string.theme_mode_system)
    ThemeMode.Light -> stringResource(R.string.theme_mode_light)
    ThemeMode.Dark -> stringResource(R.string.theme_mode_dark)
}

data class NativeDoChromeState(
    val title: String = "",
) {
    companion object {
        fun from(sessionState: AppSessionState): NativeDoChromeState = when (sessionState) {
            AppSessionState.Anonymous,
            AppSessionState.Authenticating,
            AppSessionState.Expired -> NativeDoChromeState()
            is AppSessionState.Authenticated -> NativeDoChromeState(title = sessionState.username)
        }
    }
}
