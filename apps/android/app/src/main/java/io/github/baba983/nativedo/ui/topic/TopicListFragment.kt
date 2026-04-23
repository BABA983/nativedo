package io.github.baba983.nativedo.ui.topic

import android.content.Context
import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.ui.actionbar.BaseFragment

class TopicListFragment : BaseFragment() {

    override fun createView(context: Context): View {
        actionBar.setTitle(context.getString(R.string.screen_topic_list))
        actionBar.setBackButtonDrawable(null)

        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                MaterialTheme {
                    TopicListScreen(
                        topics = MockTopics.all,
                        onTopicClick = { topic ->
                            presentFragment(TopicDetailFragment(topic.id))
                        },
                    )
                }
            }
        }
    }
}
