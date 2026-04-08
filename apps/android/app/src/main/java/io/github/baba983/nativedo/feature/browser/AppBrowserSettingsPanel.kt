package io.github.baba983.nativedo.feature.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.ui.theme.NativeDoChromeShape
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras

@Composable
internal fun AppBrowserSettingsPanel(
    onOpenHistoryClick: () -> Unit,
    onClearHistoryClick: () -> Unit,
    onClearCookiesClick: () -> Unit,
    onClearCacheClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppBrowserSettingsRow(
            title = stringResource(R.string.app_browser_settings_open_history_title),
            subtitle = stringResource(R.string.app_browser_settings_open_history_subtitle),
            onClick = onOpenHistoryClick,
        )
        AppBrowserSettingsRow(
            title = stringResource(R.string.app_browser_settings_clear_history_title),
            subtitle = stringResource(R.string.app_browser_settings_clear_history_subtitle),
            onClick = onClearHistoryClick,
        )
        AppBrowserSettingsRow(
            title = stringResource(R.string.app_browser_settings_clear_cookies_title),
            subtitle = stringResource(R.string.app_browser_settings_clear_cookies_subtitle),
            onClick = onClearCookiesClick,
        )
        AppBrowserSettingsRow(
            title = stringResource(R.string.app_browser_settings_clear_cache_title),
            subtitle = stringResource(R.string.app_browser_settings_clear_cache_subtitle),
            onClick = onClearCacheClick,
        )
    }
}

@Composable
private fun AppBrowserSettingsRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val colors = NativeDoThemeExtras.colors

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = NativeDoChromeShape,
        color = colors.glassBackgroundStrong.copy(alpha = 0.92f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}
