package io.github.baba983.nativedo.ui

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
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
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
import io.github.baba983.nativedo.core.navigation.AppBrowserReaderContent
import io.github.baba983.nativedo.feature.auth.ui.AuthRestoreGate
import io.github.baba983.nativedo.feature.auth.ui.shouldShowAuthRestoreGate
import io.github.baba983.nativedo.feature.browser.BrowserReaderScene
import io.github.baba983.nativedo.feature.browser.BrowserSheetContainer
import io.github.baba983.nativedo.navigation.NativeDoNavHost
import io.github.baba983.nativedo.navigation.RootDestination
import io.github.baba983.nativedo.ui.theme.NativeDoChromeShape
import io.github.baba983.nativedo.ui.theme.NativeDoDuration
import io.github.baba983.nativedo.ui.theme.NativeDoEasing
import io.github.baba983.nativedo.ui.theme.NativeDoGlassRole
import io.github.baba983.nativedo.ui.theme.NativeDoPillShape
import io.github.baba983.nativedo.ui.theme.NativeDoTheme
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import io.github.baba983.nativedo.ui.theme.ThemeMode
import io.github.baba983.nativedo.core.navigation.AppBrowserOpenRequest
import io.github.baba983.nativedo.core.navigation.AppBrowserPresentationAvailability
import io.github.baba983.nativedo.core.navigation.AppBrowserPresentationPolicy
import io.github.baba983.nativedo.core.navigation.AppBrowserPresentationTarget
import io.github.baba983.nativedo.core.navigation.LinkNavigationDecision
import io.github.baba983.nativedo.core.navigation.LinkOpenPolicy
import io.github.baba983.nativedo.core.navigation.openInExternalApp
import io.github.baba983.nativedo.core.navigation.openInSystemBrowser
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Composable
fun NativeDoApp() {
    AppRoot()
}

@Composable
internal fun NativeDoTopBarPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(AppTopBarRowMinHeight + (AppTopBarVerticalPadding * 2)),
    )
}

@Composable
internal fun NativeDoTopBar(
    currentRoute: String?,
    rootChrome: NativeDoRootChromeModel,
    titleOverride: String?,
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
                                titleOverride = titleOverride,
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
internal fun NativeDoBottomBar(
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

internal fun shouldBlockNavigationLayout(
    currentRoute: String?,
): Boolean = currentRoute == "login"

internal enum class NativeDoTopBarOwner {
    Root,
    Pushed,
    Browser,
}

internal fun resolveNativeDoTopBarOwner(
    currentRoute: String?,
    overlayState: BrowserHostState = BrowserHostState(),
    pushedEntryRoutePattern: String? = null,
): NativeDoTopBarOwner = overlayState.activeTopBarOwner ?: when (pushedEntryRoutePattern) {
    null -> when (currentRoute) {
        null,
        RootDestination.rootContainerRoute -> NativeDoTopBarOwner.Root
        RootDestination.Search.route -> NativeDoTopBarOwner.Pushed
        else -> NativeDoTopBarOwner.Pushed
    }
    else -> NativeDoTopBarOwner.Pushed
}

internal fun shouldRenderNativeDoRootTopBar(
    owner: NativeDoTopBarOwner,
): Boolean = owner == NativeDoTopBarOwner.Root

internal fun resolveNativeDoRootChromeRoute(
    currentRoute: String?,
    selectedRootRoute: String,
): String? = when (currentRoute) {
    null,
    RootDestination.rootContainerRoute -> selectedRootRoute
    else -> null
}

internal fun shouldKeepNativeDoRootTopBarInBackground(
    currentRoute: String?,
    overlayState: BrowserHostState = BrowserHostState(),
    isPushedEntryShowing: Boolean = false,
): Boolean = false

internal fun shouldRenderNativeDoRootTopBarPlaceholder(
    owner: NativeDoTopBarOwner,
): Boolean = false

internal fun shouldRenderNativeDoRootBottomBar(
    owner: NativeDoTopBarOwner,
    isHomeSearchActive: Boolean = false,
): Boolean = owner == NativeDoTopBarOwner.Root && !isHomeSearchActive

internal fun shouldKeepNativeDoRootBottomBarInBackground(
    currentRoute: String?,
    overlayState: BrowserHostState = BrowserHostState(),
    isPushedEntryShowing: Boolean = false,
): Boolean {
    val isBottomBarRootRoute = currentRoute == null || currentRoute == RootDestination.rootContainerRoute
    if (!isBottomBarRootRoute) {
        return false
    }
    if (isPushedEntryShowing) {
        return true
    }
    return overlayState.activeTopBarOwner != null
}

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
internal fun NativeDoBottomBarFade(
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
    titleOverride: String?,
): String = titleOverride
    ?.takeUnless(String::isBlank)
    ?: when (currentRoute) {
        "topic/{topicId}" -> stringResource(R.string.screen_topic)
        RootDestination.Search.route -> stringResource(R.string.tab_search)
        RootDestination.Profile.route -> stringResource(R.string.tab_profile)
        "user/{username}" -> stringResource(R.string.tab_profile)
        "login" -> stringResource(R.string.screen_sign_in)
        null -> stringResource(R.string.app_name)
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
