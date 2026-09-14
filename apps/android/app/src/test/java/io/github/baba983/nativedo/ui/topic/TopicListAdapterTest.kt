package io.github.baba983.nativedo.ui.topic

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.TopicSummary
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TopicListAdapterTest {
    @Test
    fun paginationLoadingAndErrorUseTheSameFooterViewType() {
        val adapter = TopicListAdapter(RuntimeEnvironment.getApplication(), "https://linux.do")
        val topics = listOf(TopicSummary(id = 1L, title = "Topic", authorUsername = "baba"))

        adapter.submit(topics, emptyMap(), true, false)
        val loadingType = adapter.getItemViewType(adapter.itemCount - 1)

        adapter.submit(
            topics,
            emptyMap(),
            false,
            false,
            null,
            null,
            null,
            null,
            "Load failed",
            null,
            Runnable {},
        )
        val errorType = adapter.getItemViewType(adapter.itemCount - 1)

        assertThat(loadingType).isEqualTo(TopicListAdapter.VIEW_TYPE_FOOTER)
        assertThat(errorType).isEqualTo(loadingType)
    }
}
