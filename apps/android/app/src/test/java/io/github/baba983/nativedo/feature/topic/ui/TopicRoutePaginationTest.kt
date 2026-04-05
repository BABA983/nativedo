package io.github.baba983.nativedo.feature.topic.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TopicRoutePaginationTest {

    @Test
    fun topicPostRole_marksFirstPostAsOriginalPoster() {
        assertThat(topicPostRole(0)).isEqualTo(TopicPostRole.OriginalPoster)
        assertThat(topicPostRole(3)).isEqualTo(TopicPostRole.Reply)
    }

    @Test
    fun topicFloorLabel_usesForumStyleNumbers() {
        assertThat(topicFloorLabel(0)).isEqualTo("1F")
        assertThat(topicFloorLabel(11)).isEqualTo("12F")
    }

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
