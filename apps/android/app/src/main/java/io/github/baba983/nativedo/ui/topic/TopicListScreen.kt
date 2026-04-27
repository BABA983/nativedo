package io.github.baba983.nativedo.ui.topic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TopicListScreen(
    topics: List<MockTopic>,
    mainTabsContentPadding: Int = 0,
    onTopicClick: (MockTopic) -> Unit,
) {
    val colors = rememberTopicThemeColors()
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        mainTabsContentPadding.dp +
        8.dp

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.background,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(topics, key = MockTopic::id) { topic ->
                TopicListRow(
                    topic = topic,
                    colors = colors,
                    onClick = { onTopicClick(topic) },
                )
            }
        }
    }
}

@Composable
private fun TopicListRow(
    topic: MockTopic,
    colors: TopicThemeColors,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.rowBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = topic.category,
                style = MaterialTheme.typography.labelMedium,
                color = colors.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = topic.author,
                style = MaterialTheme.typography.labelMedium,
                color = colors.secondary,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = topic.title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.title,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = topic.excerpt,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.body,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MetaText("回复 ${topic.replyCount}", colors)
            MetaText("浏览 ${topic.viewCount}", colors)
            MetaText("赞 ${topic.likeCount}", colors)
        }
    }
    HorizontalDivider(color = colors.divider)
}

@Composable
private fun MetaText(
    text: String,
    colors: TopicThemeColors,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = colors.secondary,
    )
}
