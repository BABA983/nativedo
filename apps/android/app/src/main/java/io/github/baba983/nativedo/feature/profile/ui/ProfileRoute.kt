package io.github.baba983.nativedo.feature.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import io.github.baba983.nativedo.core.ui.HtmlPostBodyView
import io.github.baba983.nativedo.core.ui.UserAvatar
import io.github.baba983.nativedo.feature.profile.data.ProfileRepository
import io.github.baba983.nativedo.feature.profile.domain.LoadProfileUseCase
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import kotlinx.coroutines.launch

@Composable
fun ProfileRoute(
    viewer: ProfileViewer,
    sessionRestoreTrigger: Int = 0,
    onLoginClick: () -> Unit = {},
    onTopicClick: (Long) -> Unit = {},
    onUserClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContainer = remember(context) {
        (context.applicationContext as NativeDoApplication).appContainer
    }
    val coroutineScope = rememberCoroutineScope()
    val sessionState by appContainer.sessionCoordinator.sessionState.collectAsStateWithLifecycle()
    var restoreAttempted by remember(profileSessionRestoreKey(viewer, sessionRestoreTrigger)) {
        mutableStateOf(false)
    }
    val resolvedViewer = resolveProfileViewer(
        viewer = viewer,
        sessionState = sessionState,
    )
    val showAuthLoading = shouldShowProfileAuthLoading(
        viewer = viewer,
        sessionState = sessionState,
    )

    LaunchedEffect(viewer, sessionState, restoreAttempted) {
        if (shouldRequestProfileSessionRestore(viewer, sessionState, restoreAttempted)) {
            restoreAttempted = true
            appContainer.sessionCoordinator.restoreSession()
        }
    }

    val viewModel: ProfileViewModel = viewModel(
        key = "profile-$resolvedViewer",
        factory = remember(appContainer, resolvedViewer) {
            ProfileViewModelFactory(
                loadProfile = LoadProfileUseCase(
                    repository = ProfileRepository(appContainer.discourseApiClient),
                ),
                viewer = resolvedViewer,
            )
        },
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val errorMessage = uiState.errorMessage

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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = errorMessage, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = viewModel::refresh) {
                        Text(text = "Retry")
                    }
                }
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Failed to load profile",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = viewModel::refresh) {
                        Text(text = "Retry")
                    }
                }
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
                        UserAvatar(
                            label = profile.displayName.ifBlank { profile.username },
                            avatarTemplate = profile.avatarUrl,
                            baseUrl = appContainer.baseUrl,
                            size = 56.dp,
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
                                onTopicClick = onTopicClick,
                                onUserClick = onUserClick,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

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

internal fun profileSessionRestoreKey(
    viewer: ProfileViewer,
    sessionRestoreTrigger: Int,
): Pair<ProfileViewer, Int> = viewer to sessionRestoreTrigger

internal fun shouldShowProfileAuthLoading(
    viewer: ProfileViewer,
    sessionState: AppSessionState,
): Boolean {
    return viewer == ProfileViewer.Self && sessionState is AppSessionState.Authenticating
}

internal fun shouldRequestProfileSessionRestore(
    viewer: ProfileViewer,
    sessionState: AppSessionState,
    restoreAttempted: Boolean,
): Boolean {
    return viewer == ProfileViewer.Self &&
        !restoreAttempted &&
        (sessionState == AppSessionState.Anonymous || sessionState == AppSessionState.Expired)
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

private class ProfileViewModelFactory(
    private val loadProfile: LoadProfileUseCase,
    private val viewer: ProfileViewer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ProfileViewModel(
            loadProfile = loadProfile,
            viewer = viewer,
        ) as T
    }
}
