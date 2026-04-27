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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TopicDetailScreen(
    topic: MockTopic,
    relatedTopics: List<MockTopic>,
    mainTabsContentPadding: Int = 0,
    onRelatedTopicClick: (MockTopic) -> Unit,
) {
    val colors = rememberTopicThemeColors()
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        mainTabsContentPadding.dp +
        24.dp

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.background,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.rowBackground)
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                ) {
                    Text(
                        text = topic.category,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = topic.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.title,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "by ${topic.author}",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.secondary,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = topic.excerpt,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.body,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "这一阶段先用 mock 内容验证 Telegram 风格的导航骨架。后续再把真实的 post body、分页和回复操作接进来。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.body,
                    )
                }
            }
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Related Topics",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.secondary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(relatedTopics, key = MockTopic::id) { relatedTopic ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.rowBackground)
                        .clickable { onRelatedTopicClick(relatedTopic) }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = relatedTopic.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.title,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = relatedTopic.category,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.primary,
                        )
                        Text(
                            text = "${relatedTopic.replyCount} replies",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.secondary,
                        )
                    }
                }
                HorizontalDivider(color = colors.divider)
            }
        }
    }
}
