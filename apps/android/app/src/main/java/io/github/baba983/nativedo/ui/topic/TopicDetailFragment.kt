package io.github.baba983.nativedo.ui.topic

import android.content.Context
import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import io.github.baba983.nativedo.ui.actionbar.BackDrawable
import io.github.baba983.nativedo.ui.actionbar.BaseFragment

class TopicDetailFragment(
    private val topicId: Int,
) : BaseFragment() {

    override fun createView(context: Context): View {
        val topic = MockTopics.topicFor(topicId)
        actionBar.setBackButtonDrawable(BackDrawable(false))
        actionBar.setTitle(topic.title)

        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                MaterialTheme {
                    TopicDetailScreen(
                        topic = topic,
                        relatedTopics = MockTopics.relatedTopicsFor(topicId),
                        onRelatedTopicClick = { relatedTopic ->
                            presentFragment(TopicDetailFragment(relatedTopic.id))
                        },
                    )
                }
            }
        }
    }
}
