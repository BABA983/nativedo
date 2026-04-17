package io.github.baba983.nativedo.feature.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.ui.NativeDoSectionHeader
import io.github.baba983.nativedo.ui.theme.NativeDoSpacing
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras

@Composable
internal fun AppBrowserBookmarksPanel(
    entries: List<AppBrowserBookmarkRecord>,
    onEntryClick: (AppBrowserBookmarkRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors

    if (entries.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.app_browser_bookmarks_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
        return
    }

    val historyEntries = remember(entries) {
        entries.map { bookmark ->
            AppBrowserHistoryEntry(
                title = bookmark.title,
                url = bookmark.url,
                offset = null,
                host = bookmark.host,
                visitedAtMillis = bookmark.savedAtMillis,
                favicon = bookmark.faviconBase64?.historyFaviconBitmap(),
            )
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(
                horizontal = NativeDoSpacing.lg,
                vertical = NativeDoSpacing.md + NativeDoSpacing.xxs,
            ),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "bookmarks") {
            NativeDoSectionHeader(
                title = stringResource(R.string.app_browser_bookmarks),
            )
        }
        items(
            items = historyEntries,
            key = { entry -> "${entry.url}-${entry.visitedAtMillis}" },
        ) { entry ->
            AppBrowserHistoryRow(
                entry = entry,
                onClick = {
                    entries
                        .firstOrNull { bookmark -> bookmark.url == entry.url }
                        ?.let(onEntryClick)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
