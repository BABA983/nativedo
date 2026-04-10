package io.github.baba983.nativedo.feature.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.ui.theme.NativeDoChromeShape
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class AppBrowserHistorySection(
    val label: String,
    val entries: List<AppBrowserHistoryEntry>,
)

@Composable
internal fun AppBrowserHistoryPanel(
    entries: List<AppBrowserHistoryEntry>,
    onEntryClick: (AppBrowserHistoryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    var query by remember { mutableStateOf("") }
    val filteredEntries = remember(entries, query) {
        filterAppBrowserHistoryEntries(entries, query)
    }
    val sections = remember(filteredEntries) {
        groupAppBrowserHistoryEntries(
            entries = filteredEntries,
            nowMillis = System.currentTimeMillis(),
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            shape = NativeDoChromeShape,
            color = colors.glassBackground,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text(text = stringResource(R.string.app_browser_history_search_placeholder))
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.glassBackground,
                    unfocusedContainerColor = colors.glassBackground,
                    disabledContainerColor = colors.glassBackground,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            )
        }

        if (sections.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(
                        if (query.isBlank()) {
                            R.string.app_browser_history_empty
                        } else {
                            R.string.app_browser_history_empty_filtered
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                sections.forEach { section ->
                    item(key = section.label) {
                        Text(
                            text = section.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                    items(
                        items = section.entries,
                        key = { entry -> "${entry.url}-${entry.visitedAtMillis}" },
                    ) { entry ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = NativeDoChromeShape,
                            color = colors.glassBackgroundStrong.copy(alpha = 0.92f),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onEntryClick(entry) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(
                                            color = colors.glassBackground,
                                            shape = CircleShape,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    AppBrowserHistoryPanelIcon(entry = entry)
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = entry.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = entry.host,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = formatAppBrowserHistoryTime(entry.visitedAtMillis),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppBrowserHistoryPanelIcon(
    entry: AppBrowserHistoryEntry,
) {
    val colors = NativeDoThemeExtras.colors
    val favicon = remember(entry.favicon) { entry.favicon }
    if (favicon != null) {
        Image(
            bitmap = favicon.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
        )
        return
    }

    val fallbackText = remember(entry.title, entry.host) {
        entry.title.ifBlank { entry.host }.trim().firstOrNull()?.uppercase() ?: ""
    }
    if (fallbackText.isNotBlank()) {
        Text(
            text = fallbackText,
            style = MaterialTheme.typography.labelLarge,
            color = colors.textPrimary,
            maxLines = 1,
        )
    } else {
        Icon(
            imageVector = Icons.Outlined.History,
            contentDescription = null,
            tint = colors.textPrimary,
        )
    }
}

internal fun filterAppBrowserHistoryEntries(
    entries: List<AppBrowserHistoryEntry>,
    query: String,
): List<AppBrowserHistoryEntry> {
    val needle = query.trim().lowercase()
    if (needle.isBlank()) {
        return entries
    }
    return entries.filter { entry ->
        entry.title.lowercase().contains(needle) ||
            entry.host.lowercase().contains(needle) ||
            entry.url.lowercase().contains(needle)
    }
}

internal fun groupAppBrowserHistoryEntries(
    entries: List<AppBrowserHistoryEntry>,
    nowMillis: Long,
): List<AppBrowserHistorySection> {
    val zoneId = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    val yesterday = today.minusDays(1)
    val olderFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

    return entries
        .groupBy { entry ->
            val visitedDate = Instant.ofEpochMilli(entry.visitedAtMillis)
                .atZone(zoneId)
                .toLocalDate()
            when (visitedDate) {
                today -> "Today"
                yesterday -> "Yesterday"
                else -> visitedDate.format(olderFormatter)
            }
        }
        .map { (label, groupedEntries) ->
            AppBrowserHistorySection(
                label = label,
                entries = groupedEntries,
            )
        }
}

internal fun formatAppBrowserHistoryTime(
    visitedAtMillis: Long,
): String {
    val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    return Instant.ofEpochMilli(visitedAtMillis)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}
