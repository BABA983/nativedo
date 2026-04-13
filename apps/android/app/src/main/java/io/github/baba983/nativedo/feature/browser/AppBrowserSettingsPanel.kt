package io.github.baba983.nativedo.feature.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.ui.NativeDoSettingsRow
import io.github.baba983.nativedo.ui.theme.NativeDoSpacing

@Composable
internal fun AppBrowserSettingsPanel(
    historyCount: Int,
    cookiesSizeBytes: Long,
    cacheSizeBytes: Long,
    onOpenHistoryClick: () -> Unit,
    onClearHistoryClick: () -> Unit,
    onClearCookiesClick: () -> Unit,
    onClearCacheClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                horizontal = NativeDoSpacing.lg,
                vertical = NativeDoSpacing.md + NativeDoSpacing.xxs,
            ),
        verticalArrangement = Arrangement.spacedBy(NativeDoSpacing.md),
    ) {
        val historyCountSubtitle = appBrowserSettingsHistorySubtitleCount(historyCount)
        NativeDoSettingsRow(
            title = stringResource(R.string.app_browser_settings_open_history_title),
            subtitle = historyCountSubtitle?.let { count ->
                stringResource(R.string.app_browser_settings_history_count, count)
            } ?: stringResource(R.string.app_browser_settings_open_history_subtitle),
            onClick = onOpenHistoryClick,
        )
        NativeDoSettingsRow(
            title = stringResource(R.string.app_browser_settings_clear_history_title),
            subtitle = historyCountSubtitle?.let { count ->
                stringResource(R.string.app_browser_settings_history_count, count)
            } ?: stringResource(R.string.app_browser_settings_clear_history_subtitle),
            onClick = onClearHistoryClick,
        )
        NativeDoSettingsRow(
            title = stringResource(R.string.app_browser_settings_clear_cookies_title),
            subtitle = appBrowserSettingsDataSizeSubtitle(cookiesSizeBytes)
                ?: stringResource(R.string.app_browser_settings_clear_cookies_subtitle),
            onClick = onClearCookiesClick,
        )
        NativeDoSettingsRow(
            title = stringResource(R.string.app_browser_settings_clear_cache_title),
            subtitle = appBrowserSettingsDataSizeSubtitle(cacheSizeBytes)
                ?: stringResource(R.string.app_browser_settings_clear_cache_subtitle),
            onClick = onClearCacheClick,
        )
    }
}

internal fun appBrowserSettingsHistorySubtitleCount(historyCount: Int): Int? = historyCount.takeIf { it > 0 }

internal fun appBrowserSettingsDataSizeSubtitle(sizeBytes: Long): String? = if (sizeBytes > 0L) {
    formatAppBrowserDataSize(sizeBytes)
} else {
    null
}
