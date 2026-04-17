package io.github.baba983.nativedo.feature.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.storage.AppBrowserExternalOpenMode
import io.github.baba983.nativedo.core.storage.AppBrowserSearchEngine
import io.github.baba983.nativedo.core.ui.NativeDoSettingsRow
import io.github.baba983.nativedo.ui.theme.NativeDoSpacing

@Composable
internal fun AppBrowserSettingsPanel(
    historyCount: Int,
    bookmarkCount: Int,
    cookiesSizeBytes: Long,
    cacheSizeBytes: Long,
    externalOpenMode: AppBrowserExternalOpenMode,
    searchEngine: AppBrowserSearchEngine,
    restrictedDomainCount: Int,
    onExternalOpenModeClick: () -> Unit,
    onSearchEngineClick: () -> Unit,
    onRestrictedDomainsClick: () -> Unit,
    onOpenHistoryClick: () -> Unit,
    onOpenBookmarksClick: () -> Unit,
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
        val bookmarkCountSubtitle = appBrowserSettingsBookmarkSubtitleCount(bookmarkCount)
        NativeDoSettingsRow(
            title = stringResource(R.string.app_browser_settings_external_open_mode_title),
            subtitle = stringResource(R.string.app_browser_settings_external_open_mode_subtitle),
            trailingValue = appBrowserExternalOpenModeLabel(externalOpenMode),
            onClick = onExternalOpenModeClick,
        )
        NativeDoSettingsRow(
            title = stringResource(R.string.app_browser_settings_search_engine_title),
            subtitle = stringResource(R.string.app_browser_settings_search_engine_subtitle),
            trailingValue = appBrowserSearchEngineLabel(searchEngine),
            onClick = onSearchEngineClick,
        )
        NativeDoSettingsRow(
            title = stringResource(R.string.app_browser_settings_restricted_domains_title),
            subtitle = restrictedDomainCount.takeIf { it > 0 }?.let { count ->
                stringResource(R.string.app_browser_settings_restricted_domains_count, count)
            } ?: stringResource(R.string.app_browser_settings_restricted_domains_subtitle),
            onClick = onRestrictedDomainsClick,
        )
        NativeDoSettingsRow(
            title = stringResource(R.string.app_browser_settings_open_history_title),
            subtitle = historyCountSubtitle?.let { count ->
                stringResource(R.string.app_browser_settings_history_count, count)
            } ?: stringResource(R.string.app_browser_settings_open_history_subtitle),
            onClick = onOpenHistoryClick,
        )
        NativeDoSettingsRow(
            title = stringResource(R.string.app_browser_settings_open_bookmarks_title),
            subtitle = bookmarkCountSubtitle?.let { count ->
                stringResource(R.string.app_browser_settings_bookmark_count, count)
            } ?: stringResource(R.string.app_browser_settings_open_bookmarks_subtitle),
            onClick = onOpenBookmarksClick,
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

internal fun appBrowserSettingsBookmarkSubtitleCount(bookmarkCount: Int): Int? = bookmarkCount.takeIf { it > 0 }

internal fun appBrowserSettingsDataSizeSubtitle(sizeBytes: Long): String? = if (sizeBytes > 0L) {
    formatAppBrowserDataSize(sizeBytes)
} else {
    null
}

@Composable
internal fun appBrowserExternalOpenModeLabel(mode: AppBrowserExternalOpenMode): String = when (appBrowserExternalOpenModeLabelText(mode)) {
    "In-app browser" -> stringResource(R.string.app_browser_settings_external_open_mode_in_app)
    else -> stringResource(R.string.app_browser_settings_external_open_mode_system)
}

internal fun appBrowserExternalOpenModeLabelText(mode: AppBrowserExternalOpenMode): String = when (mode) {
    AppBrowserExternalOpenMode.InAppBrowser -> "In-app browser"
    AppBrowserExternalOpenMode.SystemBrowser -> "System browser"
}

@Composable
internal fun appBrowserSearchEngineLabel(engine: AppBrowserSearchEngine): String = when (appBrowserSearchEngineLabelText(engine)) {
    "linux.do" -> stringResource(R.string.app_browser_settings_search_engine_linux_do)
    "Google" -> stringResource(R.string.app_browser_settings_search_engine_google)
    else -> stringResource(R.string.app_browser_settings_search_engine_bing)
}

internal fun appBrowserSearchEngineLabelText(engine: AppBrowserSearchEngine): String = when (engine) {
    AppBrowserSearchEngine.LinuxDo -> "linux.do"
    AppBrowserSearchEngine.Google -> "Google"
    AppBrowserSearchEngine.Bing -> "Bing"
}
