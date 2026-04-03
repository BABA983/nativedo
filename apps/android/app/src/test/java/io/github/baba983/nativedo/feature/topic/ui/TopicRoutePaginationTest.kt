package io.github.baba983.nativedo.feature.topic.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TopicRoutePaginationTest {

    @Test
    fun loadMore_requestedWhenLastVisibleItemIsInsideThreshold() {
        assertThat(
            shouldLoadMoreTopicPosts(
                lastVisibleIndex = 15,
                totalItemsCount = 18,
                hasMore = true,
                isLoadingMore = false,
            ),
        ).isTrue()
    }

    @Test
    fun postCountLabel_prefersServerTotalCount() {
        assertThat(topicPostCountLabel(totalPosts = 12, loadedPosts = 3))
            .isEqualTo("12 posts")
    }
}
