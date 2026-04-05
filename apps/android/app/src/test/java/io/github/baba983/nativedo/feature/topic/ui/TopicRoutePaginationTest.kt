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
    fun isTopicListAtBottom_treatsTheLastTwoItemsAsBottomZone() {
        assertThat(isTopicListAtBottom(lastVisibleIndex = 9, totalItemsCount = 10)).isTrue()
        assertThat(isTopicListAtBottom(lastVisibleIndex = 8, totalItemsCount = 10)).isTrue()
        assertThat(isTopicListAtBottom(lastVisibleIndex = 6, totalItemsCount = 10)).isFalse()
    }

    @Test
    fun isTopicScrollMovingUp_detectsReverseReading() {
        assertThat(
            isTopicScrollMovingUp(
                previous = TopicScrollSnapshot(firstVisibleItemIndex = 6, firstVisibleItemScrollOffset = 48),
                current = TopicScrollSnapshot(firstVisibleItemIndex = 6, firstVisibleItemScrollOffset = 12),
            ),
        ).isTrue()

        assertThat(
            isTopicScrollMovingUp(
                previous = TopicScrollSnapshot(firstVisibleItemIndex = 6, firstVisibleItemScrollOffset = 48),
                current = TopicScrollSnapshot(firstVisibleItemIndex = 7, firstVisibleItemScrollOffset = 0),
            ),
        ).isFalse()
    }

    @Test
    fun shouldArmTopicBottomShelf_requiresPostsAndUpwardScrollAwayFromBottom() {
        assertThat(
            shouldArmTopicBottomShelf(
                hasPosts = false,
                isAtBottom = false,
                isScrollingUp = true,
            ),
        ).isFalse()

        assertThat(
            shouldArmTopicBottomShelf(
                hasPosts = true,
                isAtBottom = true,
                isScrollingUp = true,
            ),
        ).isFalse()

        assertThat(
            shouldArmTopicBottomShelf(
                hasPosts = true,
                isAtBottom = false,
                isScrollingUp = false,
            ),
        ).isFalse()

        assertThat(
            shouldArmTopicBottomShelf(
                hasPosts = true,
                isAtBottom = false,
                isScrollingUp = true,
            ),
        ).isTrue()
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
