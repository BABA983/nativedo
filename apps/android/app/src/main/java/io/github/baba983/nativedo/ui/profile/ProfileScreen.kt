package io.github.baba983.nativedo.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.storage.CookieStore
import io.github.baba983.nativedo.core.ui.HtmlPostBodyView
import io.github.baba983.nativedo.core.ui.UserAvatar
import io.github.baba983.nativedo.feature.profile.ui.ProfileUiState
import org.telegram.ui.ActionBar.Theme
import io.github.baba983.nativedo.ui.actionbar.rememberThemeRevision
import io.github.baba983.nativedo.ui.actionbar.themedColor
import io.github.baba983.nativedo.ui.main.MainTabsActivity

@Composable
fun ProfileScreen(
    sessionState: AppSessionState,
    profileState: ProfileUiState,
    baseUrl: String,
    cookieStore: CookieStore,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onRetryProfileClick: () -> Unit,
    onDevToolClick: () -> Unit,
    onTopicDevToolClick: () -> Unit,
) {
    val colors = rememberProfileColors()
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        MainTabsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS.dp +
        8.dp

    Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding),
        ) {
            item {
                Section(colors = colors) {
                    Text("Profile", style = MaterialTheme.typography.headlineSmall, color = colors.title, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("账号、通知、阅读偏好和后续登录入口会放在这里。", style = MaterialTheme.typography.bodyLarge, color = colors.body)
                }
            }
            item {
                AccountSection(
                    sessionState = sessionState,
                    profileState = profileState,
                    baseUrl = baseUrl,
                    cookieStore = cookieStore,
                    onLoginClick = onLoginClick,
                    onLogoutClick = onLogoutClick,
                    onRetryProfileClick = onRetryProfileClick,
                    colors = colors,
                )
            }
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Section(
                    colors = colors,
                    modifier = Modifier.clickable(onClick = onDevToolClick),
                ) {
                    Text(
                        text = stringResource(R.string.profile_dev_tools_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.title,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.profile_dev_tools_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.secondary,
                    )
                }
            }
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Section(
                    colors = colors,
                    modifier = Modifier.clickable(onClick = onTopicDevToolClick),
                ) {
                    Text(
                        text = stringResource(R.string.profile_topic_dev_tools_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.title,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.profile_topic_dev_tools_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.secondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountSection(
    sessionState: AppSessionState,
    profileState: ProfileUiState,
    baseUrl: String,
    cookieStore: CookieStore,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onRetryProfileClick: () -> Unit,
    colors: ProfileColors,
) {
    val title: String
    val subtitle: String
    val primaryAction: (@Composable () -> Unit)?
    when (sessionState) {
        AppSessionState.Unknown -> {
            title = stringResource(R.string.profile_session_restoring_title)
            subtitle = stringResource(R.string.profile_session_restoring_subtitle)
            primaryAction = null
        }
        is AppSessionState.Restoring -> {
            title = stringResource(R.string.profile_session_restoring_title)
            subtitle = sessionState.snapshot?.displayName?.takeIf(String::isNotBlank)
                ?: stringResource(R.string.profile_session_restoring_subtitle)
            primaryAction = null
        }
        AppSessionState.Anonymous -> {
            title = stringResource(R.string.profile_login_title)
            subtitle = stringResource(R.string.profile_login_subtitle)
            primaryAction = {
                Button(onClick = onLoginClick) { Text(stringResource(R.string.profile_login_action)) }
            }
        }
        is AppSessionState.Expired -> {
            title = stringResource(R.string.profile_session_expired_title)
            subtitle = stringResource(R.string.profile_session_expired_subtitle)
            primaryAction = {
                Button(onClick = onLoginClick) { Text(stringResource(R.string.profile_login_again_action)) }
            }
        }
        is AppSessionState.Authenticated -> {
            val profile = profileState.profile
            title = profile?.displayName?.takeIf(String::isNotBlank)
                ?: sessionState.snapshot.displayName.takeIf(String::isNotBlank)
                ?: sessionState.snapshot.username
            subtitle = "@${profile?.username ?: sessionState.snapshot.username}"
            primaryAction = {
                TextButton(onClick = onLogoutClick) { Text(stringResource(R.string.profile_logout_action)) }
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.rowBackground)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (sessionState is AppSessionState.Authenticated) {
            val profile = profileState.profile
            UserAvatar(
                label = title,
                avatarTemplate = profile?.avatarTemplate ?: sessionState.snapshot.avatarTemplate,
                animatedAvatarUrl = profile?.animatedAvatarUrl.orEmpty(),
                baseUrl = baseUrl,
                size = 52.dp,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        primaryAction?.invoke()
    }

    if (sessionState is AppSessionState.Authenticated) {
        ProfileDetailsSection(
            profileState = profileState,
            cookieStore = cookieStore,
            baseUrl = baseUrl,
            onRetryProfileClick = onRetryProfileClick,
            colors = colors,
        )
    }
}

@Composable
private fun ProfileDetailsSection(
    profileState: ProfileUiState,
    cookieStore: CookieStore,
    baseUrl: String,
    onRetryProfileClick: () -> Unit,
    colors: ProfileColors,
) {
    when {
        profileState.isLoading -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.rowBackground)
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
        }
        profileState.errorMessage != null -> {
            Section(colors = colors) {
                Text(stringResource(R.string.profile_load_error_title), style = MaterialTheme.typography.titleMedium, color = colors.title, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(profileState.errorMessage, style = MaterialTheme.typography.bodyMedium, color = colors.secondary)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onRetryProfileClick) { Text("重试") }
            }
        }
        profileState.profile?.bio?.isNotBlank() == true -> {
            Section(colors = colors) {
                Text(stringResource(R.string.profile_about_title), style = MaterialTheme.typography.titleMedium, color = colors.title, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(10.dp))
                HtmlPostBodyView(
                    html = profileState.profile.bio,
                    cookieStore = cookieStore,
                    baseUrl = baseUrl,
                )
            }
        }
    }
}

@Composable
private fun Section(
    colors: ProfileColors,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.rowBackground)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        content = content,
    )
}

private data class ProfileColors(
    val background: Color,
    val rowBackground: Color,
    val title: Color,
    val body: Color,
    val secondary: Color,
)

@Composable
private fun rememberProfileColors(): ProfileColors {
    rememberThemeRevision()
    return ProfileColors(
        background = themedColor(Theme.key_iv_background),
        rowBackground = themedColor(Theme.key_windowBackgroundWhite),
        title = themedColor(Theme.key_windowBackgroundWhiteBlackText),
        body = themedColor(Theme.key_windowBackgroundWhiteBlackText),
        secondary = themedColor(Theme.key_windowBackgroundWhiteGrayText),
    )
}
