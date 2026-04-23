package io.github.baba983.nativedo.ui.topic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TopicDetailScreen(
    topic: MockTopic,
    relatedTopics: List<MockTopic>,
    onRelatedTopicClick: (MockTopic) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF9FAFB),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                ) {
                    Text(
                        text = topic.category,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF2563EB),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = topic.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color(0xFF111827),
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "by ${topic.author}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF6B7280),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = topic.excerpt,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF374151),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "这一阶段先用 mock 内容验证 Telegram 风格的导航骨架。后续再把真实的 post body、分页和回复操作接进来。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF374151),
                    )
                }
            }
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Related Topics",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF6B7280),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(relatedTopics, key = MockTopic::id) { relatedTopic ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .clickable { onRelatedTopicClick(relatedTopic) }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = relatedTopic.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF111827),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = relatedTopic.category,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF2563EB),
                        )
                        Text(
                            text = "${relatedTopic.replyCount} replies",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF6B7280),
                        )
                    }
                }
                HorizontalDivider(color = Color(0xFFE5E7EB))
            }
        }
    }
}
