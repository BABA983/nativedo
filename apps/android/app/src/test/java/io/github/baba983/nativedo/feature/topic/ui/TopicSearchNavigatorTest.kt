package io.github.baba983.nativedo.feature.topic.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.TopicSummary
import org.junit.Test

class TopicSearchNavigatorTest {
    @Test
    fun queryIsTrimmedAndScopedToTopic() {
        assertThat(topicScopedSearchQuery("  rate limit  ", 2566145L))
            .isEqualTo("rate limit topic:2566145")
        assertThat(topicScopedSearchQuery("   ", 2566145L)).isEmpty()
    }

    @Test
    fun matchesAreFilteredDeduplicatedAndOrderedByFloor() {
        val navigator = TopicSearchNavigator(topicId = 10L)

        navigator.submit(
            listOf(
                row(topicId = 10L, postId = 103L, postNumber = 9),
                row(topicId = 11L, postId = 999L, postNumber = 1),
                row(topicId = 10L, postId = 101L, postNumber = 2),
                row(topicId = 10L, postId = 101L, postNumber = 2),
            ),
        )

        assertThat(navigator.matches).containsExactly(
            TopicSearchMatch(postId = 101L, postNumber = 2),
            TopicSearchMatch(postId = 103L, postNumber = 9),
        ).inOrder()
        assertThat(navigator.selectedIndex).isEqualTo(0)
        assertThat(navigator.hasPrevious).isFalse()
        assertThat(navigator.hasNext).isTrue()
    }

    @Test
    fun paginationPreservesCurrentMatchBeforeMovingForward() {
        val navigator = TopicSearchNavigator(topicId = 10L)
        navigator.submit(
            listOf(
                row(topicId = 10L, postId = 101L, postNumber = 2),
                row(topicId = 10L, postId = 103L, postNumber = 9),
            ),
        )
        navigator.next()

        navigator.submit(
            listOf(
                row(topicId = 10L, postId = 101L, postNumber = 2),
                row(topicId = 10L, postId = 103L, postNumber = 9),
                row(topicId = 10L, postId = 108L, postNumber = 12),
            ),
        )

        assertThat(navigator.current).isEqualTo(TopicSearchMatch(postId = 103L, postNumber = 9))
        assertThat(navigator.next()).isEqualTo(TopicSearchMatch(postId = 108L, postNumber = 12))
        assertThat(navigator.next()).isEqualTo(TopicSearchMatch(postId = 108L, postNumber = 12))
    }

    private fun row(topicId: Long, postId: Long, postNumber: Int): TopicSummary = TopicSummary(
        id = topicId,
        title = "topic",
        authorUsername = "user",
        targetPostId = postId,
        targetPostNumber = postNumber,
    )
}
