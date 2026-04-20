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
import io.github.baba983.nativedo.core.ui.NativeDoDivider
import io.github.baba983.nativedo.core.ui.NativeDoSectionHeader
import io.github.baba983.nativedo.ui.theme.NativeDoChromeShape
import io.github.baba983.nativedo.ui.theme.NativeDoSpacing
import io.github.baba983.nativedo.ui.theme.NativeDoThemeExtras
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class BrowserHistorySection(
    val label: String,
    val entries: List<BrowserHistoryEntry>,
)

@Composable
internal fun BrowserHistoryPanel(
    entries: List<BrowserHistoryEntry>,
    onEntryClick: (BrowserHistoryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors
    var query by remember { mutableStateOf("") }
    val filteredEntries = remember(entries, query) {
        filterBrowserHistoryEntries(entries, query)
    }
    val sections = remember(filteredEntries) {
        groupBrowserHistoryEntries(
            entries = filteredEntries,
            nowMillis = System.currentTimeMillis(),
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                horizontal = NativeDoSpacing.lg,
                vertical = NativeDoSpacing.md + NativeDoSpacing.xxs,
            ),
        verticalArrangement = Arrangement.spacedBy(NativeDoSpacing.md + NativeDoSpacing.xxs),
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
                        NativeDoSectionHeader(
                            title = section.label,
                        )
                    }
                    items(
                        items = section.entries,
                        key = { entry -> "${entry.url}-${entry.visitedAtMillis}" },
                    ) { entry ->
                        BrowserHistoryRow(
                            entry = entry,
                            onClick = { onEntryClick(entry) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun BrowserHistoryRow(
    entry: BrowserHistoryEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = false,
) {
    val colors = NativeDoThemeExtras.colors

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = NativeDoChromeShape,
        color = colors.glassBackgroundStrong.copy(alpha = 0.92f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(
                        horizontal = NativeDoSpacing.md + NativeDoSpacing.xxs,
                        vertical = NativeDoSpacing.md,
                    ),
                horizontalArrangement = Arrangement.spacedBy(NativeDoSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrowserHistoryBadge(
                    title = entry.title,
                    host = entry.host,
                    favicon = entry.favicon,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(NativeDoSpacing.xs),
                ) {
                    Text(
                        text = entry.title.ifBlank { entry.host },
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = entry.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = browserHistoryRowTimeLabel(entry.visitedAtMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                )
            }

            if (showDivider) {
                NativeDoDivider(
                    startIndent = 58.dp,
                    alpha = 0.18f,
                )
            }
        }
    }
}

@Composable
private fun BrowserHistoryBadge(
    title: String,
    host: String,
    favicon: android.graphics.Bitmap?,
    modifier: Modifier = Modifier,
) {
    val colors = NativeDoThemeExtras.colors

    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                color = colors.glassBackground,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (favicon != null) {
            Image(
                bitmap = favicon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
        } else {
            val fallbackText = remember(title, host) {
                browserHistoryBadgeLabel(
                    title = title,
                    host = host,
                )
            }
            if (fallbackText.isNotBlank() && fallbackText != "•") {
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
    }
}

internal fun filterBrowserHistoryEntries(
    entries: List<BrowserHistoryEntry>,
    query: String,
): List<BrowserHistoryEntry> {
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

internal fun groupBrowserHistoryEntries(
    entries: List<BrowserHistoryEntry>,
    nowMillis: Long,
): List<BrowserHistorySection> {
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
            BrowserHistorySection(
                label = label,
                entries = groupedEntries,
            )
        }
}

internal fun browserHistoryBadgeLabel(
    title: String,
    host: String,
): String {
    val source = title.ifBlank { host }.trim()
    return source.firstOrNull()
        ?.uppercaseChar()
        ?.toString()
        ?: "•"
}

internal fun browserHistoryRowTimeLabel(
    visitedAtMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    val visitedAt = Instant.ofEpochMilli(visitedAtMillis).atZone(zoneId)
    val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)

    return when {
        visitedAt.toLocalDate() == now.toLocalDate() -> {
            DateTimeFormatter.ofPattern("HH:mm", locale).format(visitedAt)
        }
        visitedAt.toLocalDate() == now.toLocalDate().minusDays(1) -> {
            "Yesterday"
        }
        else -> {
            DateTimeFormatter.ofPattern("MMM d", locale).format(visitedAt)
        }
    }
}
