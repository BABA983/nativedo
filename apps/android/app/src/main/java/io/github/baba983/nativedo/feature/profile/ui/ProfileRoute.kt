package io.github.baba983.nativedo.feature.profile.ui

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.baba983.nativedo.NativeDoApplication
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.session.SessionEvent
import io.github.baba983.nativedo.core.ui.HtmlPostBodyView
import io.github.baba983.nativedo.core.ui.RequestErrorCard
import io.github.baba983.nativedo.core.ui.UserAvatarWithFlair
import io.github.baba983.nativedo.core.ui.requestErrorPresentation
import io.github.baba983.nativedo.feature.profile.data.ProfileRepository
import io.github.baba983.nativedo.feature.profile.domain.LoadProfileUseCase
import io.github.baba983.nativedo.ui.theme.NativeDoChromeShape
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Composable
fun ProfileRoute(
    viewer: ProfileViewer,
    onLoginClick: () -> Unit = {},
    onSessionRecoveryRequired: () -> Unit = {},
    onTopicClick: (Long) -> Unit = {},
    onUserClick: (String) -> Unit = {},
    onOpenLink: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContainer = remember(context) {
        (context.applicationContext as NativeDoApplication).appContainer
    }
    val coroutineScope = rememberCoroutineScope()
    val sessionState by appContainer.sessionCoordinator.sessionState.collectAsStateWithLifecycle()
    val authVersion by appContainer.sessionCoordinator.authVersion.collectAsStateWithLifecycle()
    val svgSpriteMarkup by appContainer.svgSpriteStore.sprite.collectAsStateWithLifecycle()
    var handledAuthVersion by rememberSaveable { mutableStateOf(UNSET_AUTH_VERSION) }
    val resolvedViewer = resolveProfileViewer(
        viewer = viewer,
        sessionState = sessionState,
    )
    val showAuthLoading = shouldShowProfileAuthLoading(
        viewer = viewer,
        sessionState = sessionState,
    )
    val showDebugTools = shouldShowProfileDebugTools(
        viewer = viewer,
        isDebugBuild = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
    )
    var showTopicUrlDialog by rememberSaveable { mutableStateOf(false) }

    val viewModel: ProfileViewModel = viewModel(
        key = "profile-$resolvedViewer",
        factory = remember(appContainer, resolvedViewer) {
            ProfileViewModelFactory(
                loadProfile = LoadProfileUseCase(
                    repository = ProfileRepository(appContainer.discourseApiClient),
                ),
                viewer = resolvedViewer,
                currentSessionVersion = { appContainer.sessionCoordinator.authVersion.value },
            )
        },
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val errorMessage = uiState.errorMessage
    val errorPresentation = requestErrorPresentation(
        rawMessage = errorMessage,
        genericTitle = "Couldn't load profile",
        genericDescription = "Please try again in a moment.",
    )
    val currentSessionState by rememberUpdatedState(sessionState)
    val currentAuthVersion by rememberUpdatedState(authVersion)
    val refreshProfileIfNeeded: () -> Unit = {
        val latestAuthVersion = currentAuthVersion
        if (
            shouldRefreshProfileForHandledAuthVersion(
                viewer = viewer,
                sessionState = currentSessionState,
                handledAuthVersion = handledAuthVersion,
                currentAuthVersion = latestAuthVersion,
            )
        ) {
            handledAuthVersion = latestAuthVersion
            viewModel.refresh()
        }
    }

    LaunchedEffect(uiState.isAuthRecoveryRequired) {
        if (uiState.isAuthRecoveryRequired) {
            onSessionRecoveryRequired()
            viewModel.onAuthRecoveryHandled()
        }
    }

    LaunchedEffect(authVersion, sessionState, viewer) {
        if (handledAuthVersion == UNSET_AUTH_VERSION) {
            handledAuthVersion = authVersion
        } else {
            refreshProfileIfNeeded()
        }
    }

    LaunchedEffect(appContainer.sessionCoordinator, viewer) {
        appContainer.sessionCoordinator.events.collect { event ->
            if (
                (event is SessionEvent.LoginCompleted || event is SessionEvent.SessionVerified)
            ) {
                refreshProfileIfNeeded()
            }
        }
    }

    when {
        showAuthLoading -> {
            ProfileFeedbackCard(
                modifier = modifier.fillMaxSize(),
            ) {
                CircularProgressIndicator()
            }
        }

        uiState.isLoading -> {
            ProfileFeedbackCard(
                modifier = modifier.fillMaxSize(),
            ) {
                CircularProgressIndicator()
            }
        }

        errorMessage != null -> {
            ProfileFeedbackCard(
                modifier = modifier.fillMaxSize(),
            ) {
                RequestErrorCard(
                    presentation = errorPresentation,
                    onRetry = viewModel::refresh,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        uiState.profile == null && resolvedViewer == ProfileViewer.Self -> {
            ProfileFeedbackCard(
                modifier = modifier.fillMaxSize(),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Login required",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (resolvedViewer == ProfileViewer.Self) {
                        TextButton(onClick = onLoginClick) {
                            Text(text = "Login")
                        }
                    }
                }
            }
        }

        uiState.profile == null -> {
            ProfileFeedbackCard(
                modifier = modifier.fillMaxSize(),
            ) {
                RequestErrorCard(
                    presentation = errorPresentation,
                    onRetry = viewModel::refresh,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        else -> {
            val profile = requireNotNull(uiState.profile)
            val colors = NativeDoThemeExtras.colors

            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UserAvatarWithFlair(
                            label = profile.displayName.ifBlank { profile.username },
                            avatarTemplate = profile.avatarUrl,
                            animatedAvatarUrl = profile.animatedAvatarUrl,
                            flairName = profile.flairName,
                            flairUrl = profile.flairUrl,
                            flairBgColor = profile.flairBgColor,
                            flairColor = profile.flairColor,
                            svgSpriteMarkup = svgSpriteMarkup,
                            baseUrl = appContainer.baseUrl,
                            avatarSize = 56.dp,
                            flairBadgeSize = 18.dp,
                            flairContentSize = 14.dp,
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = profile.displayName.ifBlank { profile.username },
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                text = "@${profile.username}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                        }
                        if (viewer == ProfileViewer.Self) {
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        appContainer.sessionCoordinator.onLogout()
                                    }
                                },
                            ) {
                                Text(text = "Logout")
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f),
                    )
                }

                if (viewer == ProfileViewer.Self) {
                    item {
                        QuickAccessSection(
                            onOpenDashboardClick = {
                                onOpenLink(CDK_DASHBOARD_URL)
                            },
                        )
                    }

                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f),
                        )
                    }
                }

                if (showDebugTools) {
                    item {
                        DeveloperToolsSection(
                            onOpenTopicUrlClick = {
                                showTopicUrlDialog = true
                            },
                        )
                    }

                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f),
                        )
                    }
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Bio",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.textSecondary,
                        )
                        if (profile.bio.isBlank()) {
                            Text(
                                text = "No bio yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                        } else {
                            HtmlPostBodyView(
                                html = profile.bio,
                                cookieStore = appContainer.cookieStore,
                                baseUrl = appContainer.baseUrl,
                                svgSpriteMarkup = svgSpriteMarkup,
                                onTopicClick = onTopicClick,
                                onUserClick = onUserClick,
                                onOpenLink = onOpenLink,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDebugTools && showTopicUrlDialog) {
        OpenTopicUrlDialog(
            onDismissRequest = {
                showTopicUrlDialog = false
            },
            onTopicOpen = { topicId ->
                showTopicUrlDialog = false
                onTopicClick(topicId)
            },
        )
    }
}

internal const val CDK_DASHBOARD_URL = "https://cdk.linux.do/dashboard"

internal fun resolveProfileViewer(
    viewer: ProfileViewer,
    sessionState: AppSessionState,
): ProfileViewer {
    return when {
        viewer != ProfileViewer.Self -> viewer
        sessionState is AppSessionState.Authenticated -> ProfileViewer.Other(sessionState.username)
        else -> ProfileViewer.Self
    }
}

internal fun shouldShowProfileAuthLoading(
    viewer: ProfileViewer,
    sessionState: AppSessionState,
): Boolean {
    return viewer == ProfileViewer.Self &&
        (sessionState == AppSessionState.Unknown || sessionState is AppSessionState.Restoring)
}

internal fun shouldShowProfileDebugTools(
    viewer: ProfileViewer,
    isDebugBuild: Boolean,
): Boolean {
    return isDebugBuild && viewer == ProfileViewer.Self
}

internal fun parseLinuxDoTopicIdFromUrl(rawInput: String): Long? {
    val url = rawInput.trim().toHttpUrlOrNull() ?: return null
    if (url.host != "linux.do" && url.host != "www.linux.do") {
        return null
    }
    val pathSegments = url.pathSegments.filter(String::isNotBlank)
    if (pathSegments.firstOrNull() != "t") {
        return null
    }

    return pathSegments.getOrNull(1)?.toLongOrNull()
        ?: pathSegments.getOrNull(2)?.toLongOrNull()
}

internal fun shouldRefreshProfileForHandledAuthVersion(
    viewer: ProfileViewer,
    sessionState: AppSessionState,
    handledAuthVersion: Long,
    currentAuthVersion: Long,
): Boolean {
    return viewer != ProfileViewer.Self &&
        sessionState is AppSessionState.Authenticated &&
        currentAuthVersion > handledAuthVersion
}

@Composable
private fun ProfileFeedbackCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun QuickAccessSection(
    onOpenDashboardClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Quick Access",
            style = MaterialTheme.typography.labelLarge,
            color = colors.textSecondary,
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            shape = NativeDoChromeShape,
            color = colors.glassBackgroundStrong.copy(alpha = 0.92f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            onClick = onOpenDashboardClick,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "CDK Dashboard",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                Text(
                    text = "Open https://cdk.linux.do/dashboard directly in the in-app browser.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun DeveloperToolsSection(
    onOpenTopicUrlClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Developer Tools",
            style = MaterialTheme.typography.labelLarge,
            color = colors.textSecondary,
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            shape = NativeDoChromeShape,
            color = colors.glassBackgroundStrong.copy(alpha = 0.92f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            onClick = onOpenTopicUrlClick,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Open Topic URL",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                Text(
                    text = "Paste a linux.do topic URL and jump straight into the native topic screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun OpenTopicUrlDialog(
    onDismissRequest: () -> Unit,
    onTopicOpen: (Long) -> Unit,
) {
    var rawInput by rememberSaveable { mutableStateOf("") }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = "Open Topic URL")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Paste a linux.do web topic URL for render debugging.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextField(
                    value = rawInput,
                    onValueChange = { nextValue ->
                        rawInput = nextValue
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = errorMessage != null,
                    placeholder = {
                        Text(text = "https://linux.do/t/topic-slug/123456")
                    },
                )
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val topicId = parseLinuxDoTopicIdFromUrl(rawInput)
                    if (topicId != null) {
                        onTopicOpen(topicId)
                    } else {
                        errorMessage = "Only linux.do topic URLs are supported."
                    }
                },
                enabled = rawInput.isNotBlank(),
            ) {
                Text(text = "Open")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = "Cancel")
            }
        },
    )
}

private class ProfileViewModelFactory(
    private val loadProfile: LoadProfileUseCase,
    private val viewer: ProfileViewer,
    private val currentSessionVersion: () -> Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ProfileViewModel(
            loadProfile = loadProfile,
            viewer = viewer,
            currentSessionVersion = currentSessionVersion,
        ) as T
    }
}

private const val UNSET_AUTH_VERSION = Long.MIN_VALUE
