package io.github.baba983.nativedo.ui.topic

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.baba983.nativedo.core.model.CategorySummary
import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.core.ui.UserAvatar
import io.github.baba983.nativedo.core.ui.compactCount
import io.github.baba983.nativedo.feature.home.ui.HomeFeedTab
import io.github.baba983.nativedo.feature.home.ui.HomeViewModel
import org.telegram.ui.ActionBar.Theme
import io.github.baba983.nativedo.ui.actionbar.rememberThemeRevision
import io.github.baba983.nativedo.ui.actionbar.themedColor
import org.telegram.messenger.R as TelegramR
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val TOPIC_LIST_MAX_TAGS = 2
private val topicListDateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

private data class TopicListSkeletonTemplate(
    val secondLineWidth: androidx.compose.ui.unit.Dp,
    val metaWidth: androidx.compose.ui.unit.Dp,
    val showSecondLine: Boolean,
)

private val TOPIC_LIST_SKELETON_ROWS: List<TopicListSkeletonTemplate> = listOf(
    TopicListSkeletonTemplate(secondLineWidth = 180.dp, metaWidth = 90.dp, showSecondLine = true),
    TopicListSkeletonTemplate(secondLineWidth = 120.dp, metaWidth = 70.dp, showSecondLine = false),
    TopicListSkeletonTemplate(secondLineWidth = 200.dp, metaWidth = 110.dp, showSecondLine = true),
    TopicListSkeletonTemplate(secondLineWidth = 80.dp, metaWidth = 60.dp, showSecondLine = false),
    TopicListSkeletonTemplate(secondLineWidth = 160.dp, metaWidth = 80.dp, showSecondLine = true),
    TopicListSkeletonTemplate(secondLineWidth = 140.dp, metaWidth = 100.dp, showSecondLine = false),
    TopicListSkeletonTemplate(secondLineWidth = 190.dp, metaWidth = 80.dp, showSecondLine = true),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicListScreen(
    viewModel: HomeViewModel,
    baseUrl: String,
    mainTabsContentPadding: Int = 0,
    onTopicClick: (TopicSummary) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    val colors = rememberTopicListColors()
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        mainTabsContentPadding.dp +
        8.dp

    LaunchedEffect(
        listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index,
        listState.layoutInfo.totalItemsCount,
        uiState.hasMore,
        uiState.isLoadingMore,
    ) {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@LaunchedEffect
        if (
            uiState.hasMore &&
            !uiState.isLoadingMore &&
            !uiState.isLoading &&
            lastVisible >= listState.layoutInfo.totalItemsCount - 5
        ) {
            viewModel.loadNextPage()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.background,
    ) {
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::onRefresh,
            state = pullToRefreshState,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    FeedTabs(
                        selectedTab = uiState.selectedTab,
                        onTabSelected = viewModel::onFeedSelected,
                        colors = colors,
                    )
                }
                when {
                    uiState.isLoading -> {
                        items(TOPIC_LIST_SKELETON_ROWS.size) { index ->
                            val template = TOPIC_LIST_SKELETON_ROWS[index]
                            TopicListSkeletonRow(
                                colors = colors,
                                showDivider = index != TOPIC_LIST_SKELETON_ROWS.lastIndex,
                                secondLineWidth = template.secondLineWidth,
                                metaWidth = template.metaWidth,
                                showSecondLine = template.showSecondLine,
                            )
                        }
                    }
                    uiState.errorMessage != null && uiState.items.isEmpty() -> {
                        val message = uiState.errorMessage.orEmpty()
                        item {
                            ErrorState(
                                message = message,
                                onRetry = viewModel::onRefresh,
                                colors = colors,
                            )
                        }
                    }
                    uiState.isEmpty -> {
                        item { CenterState(text = "暂时没有话题", colors = colors) }
                    }
                    else -> {
                        itemsIndexed(uiState.items, key = { _, topic -> topic.id }) { index, topic ->
                            TopicSummaryRow(
                                topic = topic,
                                category = topic.categoryId?.let(uiState.categories::get),
                                baseUrl = baseUrl,
                                showDivider = index != uiState.items.lastIndex,
                                onClick = { onTopicClick(topic) },
                                colors = colors,
                            )
                        }
                        if (uiState.isLoadingMore) {
                            item { CenterState(text = "加载更多…", colors = colors, showProgress = true) }
                        }
                        uiState.loadMoreErrorMessage?.let { message ->
                            item {
                                LoadMoreErrorState(
                                    message = message,
                                    onRetry = viewModel::loadNextPage,
                                    colors = colors,
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
private fun FeedTabs(
    selectedTab: HomeFeedTab,
    onTabSelected: (HomeFeedTab) -> Unit,
    colors: TopicListColors,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.rowBackground)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HomeFeedTab.entries.forEach { tab ->
            FilterChip(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                label = { Text(text = tab.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.accent.copy(alpha = 0.14f),
                    selectedLabelColor = colors.accent,
                ),
            )
        }
    }
}

@Composable
private fun TopicSummaryRow(
    topic: TopicSummary,
    category: CategorySummary?,
    baseUrl: String,
    showDivider: Boolean,
    onClick: () -> Unit,
    colors: TopicListColors,
) {
    val tagLine = topicTagLine(topic)
    val activityLine = topicActivityLine(topic)
    val timeLabel = topicRelativeTime(topic.lastPostedAt).orEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.rowBackground)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            UserAvatar(
                label = topic.authorUsername.ifBlank { baseUrl },
                avatarTemplate = topic.authorAvatarTemplate,
                baseUrl = baseUrl,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        if (topic.isPinned) {
                            Icon(
                                painter = painterResource(id = TelegramR.drawable.msg_pin_mini),
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(14.dp),
                                tint = colors.accent.copy(alpha = 0.88f),
                            )
                        }
                        Text(
                            text = topic.title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.title,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (timeLabel.isNotBlank()) {
                        Text(
                            text = timeLabel,
                            modifier = Modifier.padding(top = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.secondary,
                            maxLines = 1,
                        )
                    }
                }
                if (category != null || tagLine.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        category?.let {
                            TopicCategoryBadge(category = it, colors = colors)
                        }
                        if (tagLine.isNotBlank()) {
                            Text(
                                text = tagLine,
                                modifier = Modifier.weight(1f, fill = false),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.accent.copy(alpha = 0.94f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (activityLine.isNotBlank()) {
                        Text(
                            text = activityLine,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TopicStatsRow(topic = topic, colors = colors)
                    TopicUnreadBadge(topic = topic, colors = colors)
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(
                color = colors.divider,
            )
        }
    }
}

@Composable
private fun TopicCategoryBadge(
    category: CategorySummary,
    colors: TopicListColors,
    modifier: Modifier = Modifier,
) {
    val badgeColor = discourseCategoryColor(
        rawHex = category.colorHex,
        fallback = colors.accent,
    )
    val labelColor = if (badgeColor.luminance() > 0.62f) {
        Color(0xFF161616)
    } else {
        Color.White.copy(alpha = 0.96f)
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = badgeColor.copy(alpha = if (badgeColor.luminance() > 0.62f) 0.2f else 0.28f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            text = category.name,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TopicStatsRow(
    topic: TopicSummary,
    colors: TopicListColors,
    modifier: Modifier = Modifier,
) {
    if (topic.replyCount <= 0 && topic.viewCount <= 0) {
        return
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (topic.replyCount > 0) {
            TopicStat(
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Reply,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = colors.accent,
                    )
                },
                text = compactCount(topic.replyCount),
                color = colors.accent,
            )
        }
        if (topic.viewCount > 0) {
            TopicStat(
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = colors.secondary,
                    )
                },
                text = compactCount(topic.viewCount),
                color = colors.secondary,
            )
        }
    }
}

@Composable
private fun TopicStat(
    icon: @Composable () -> Unit,
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun TopicUnreadBadge(
    topic: TopicSummary,
    colors: TopicListColors,
    modifier: Modifier = Modifier,
) {
    when {
        topic.unreadCount > 0 -> {
            val bgColor = if (topic.isMuted) {
                colors.secondary.copy(alpha = 0.4f)
            } else {
                colors.accent
            }
            Surface(
                modifier = modifier,
                shape = CircleShape,
                color = bgColor,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Text(
                    text = if (topic.unreadCount > 99) "99+" else topic.unreadCount.toString(),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                )
            }
        }
        topic.isUnseen -> {
            Box(
                modifier = modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(colors.accent),
            )
        }
    }
}

@Composable
private fun CenterState(
    text: String,
    colors: TopicListColors,
    showProgress: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.rowBackground)
            .padding(horizontal = 18.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showProgress) {
            CircularProgressIndicator()
        }
        Text(text = text, color = colors.secondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    colors: TopicListColors,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.rowBackground)
            .padding(horizontal = 18.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("加载失败", style = MaterialTheme.typography.titleMedium, color = colors.title, fontWeight = FontWeight.SemiBold)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.secondary)
        Button(onClick = onRetry) { Text("重试") }
    }
}

@Composable
private fun LoadMoreErrorState(
    message: String,
    onRetry: () -> Unit,
    colors: TopicListColors,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.rowBackground)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.secondary, modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry) { Text("重试") }
    }
}

private val HomeFeedTab.label: String
    get() = when (this) {
        HomeFeedTab.Latest -> "最新"
        HomeFeedTab.New -> "新帖"
        HomeFeedTab.Top -> "热门"
    }

private fun topicTagLine(
    topic: TopicSummary,
    maxTags: Int = TOPIC_LIST_MAX_TAGS,
): String = topic.tags
    .asSequence()
    .filter(String::isNotBlank)
    .take(maxTags)
    .joinToString(separator = " · ") { tag -> "#$tag" }

private fun topicActivityLine(
    topic: TopicSummary,
): String = buildList {
    topic.authorUsername
        .takeIf { it.isNotBlank() }
        ?.let { username -> add("@$username") }
    topic.lastPosterUsername
        .takeIf { it.isNotBlank() && it != topic.authorUsername }
        ?.let { username -> add("@$username") }
}.joinToString(separator = " · ")

private fun topicRelativeTime(
    rawValue: String,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): String? {
    if (rawValue.isBlank()) {
        return null
    }

    val postedAt = runCatching { OffsetDateTime.parse(rawValue) }.getOrNull() ?: return null
    val duration = Duration.between(postedAt.toInstant(), now)

    if (duration.isNegative) {
        return postedAt.atZoneSameInstant(zoneId).format(topicListDateFormatter)
    }

    return when {
        duration < Duration.ofMinutes(1) -> "just now"
        duration < Duration.ofHours(1) -> "${duration.toMinutes()}m"
        duration < Duration.ofDays(1) -> "${duration.toHours()}h"
        duration < Duration.ofDays(7) -> "${duration.toDays()}d"
        else -> postedAt.atZoneSameInstant(zoneId).format(topicListDateFormatter)
    }
}

private fun discourseCategoryColor(
    rawHex: String,
    fallback: Color,
): Color {
    val normalizedHex = rawHex.trim().removePrefix("#")
    if (normalizedHex.isBlank()) {
        return fallback
    }
    return runCatching {
        Color(AndroidColor.parseColor("#$normalizedHex"))
    }.getOrDefault(fallback)
}

internal data class TopicListColors(
    val background: Color,
    val rowBackground: Color,
    val title: Color,
    val secondary: Color,
    val accent: Color,
    val divider: Color,
)

@Composable
private fun rememberTopicListColors(): TopicListColors {
    rememberThemeRevision()
    return TopicListColors(
        background = themedColor(Theme.key_iv_background),
        rowBackground = themedColor(Theme.key_windowBackgroundWhite),
        title = themedColor(Theme.key_windowBackgroundWhiteBlackText),
        secondary = themedColor(Theme.key_windowBackgroundWhiteGrayText),
        accent = themedColor(Theme.key_windowBackgroundWhiteBlueText),
        divider = themedColor(Theme.key_divider),
    )
}
