package io.github.baba983.nativedo.feature.topic.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.SearchResult
import io.github.baba983.nativedo.core.model.SearchUserSummary
import io.github.baba983.nativedo.core.model.TopicSummary
import org.junit.Test

class TopicSearchSessionTest {
    @Test
    fun staleResponseCannotOverwriteNewQuery() {
        val session = TopicSearchSession()
        val oldRequest = session.start("old")
        val newRequest = session.start("new")

        assertThat(
            session.accept(oldRequest, SearchResult(topics = listOf(topic(1, "old")))),
        ).isFalse()
        assertThat(
            session.accept(newRequest, SearchResult(topics = listOf(topic(2, "new")))),
        ).isTrue()
        assertThat(session.snapshot.query).isEqualTo("new")
        assertThat(session.snapshot.result.topics.map(TopicSummary::id)).containsExactly(2L)
    }

    @Test
    fun paginationAppendsAndDeduplicatesEveryResultType() {
        val session = TopicSearchSession()
        val first = session.start("telegram")
        session.accept(
            first,
            SearchResult(
                topics = listOf(topic(1, "one")),
                posts = listOf(post(topicId = 10, postId = 100)),
                users = listOf(SearchUserSummary("Baba")),
                hasMore = true,
            ),
        )

        val second = session.nextPage()!!
        session.accept(
            second,
            SearchResult(
                topics = listOf(topic(1, "duplicate"), topic(2, "two")),
                posts = listOf(post(topicId = 10, postId = 100), post(topicId = 10, postId = 101)),
                users = listOf(SearchUserSummary("baba"), SearchUserSummary("alice")),
                hasMore = false,
            ),
        )

        assertThat(session.snapshot.result.topics.map(TopicSummary::id)).containsExactly(1L, 2L).inOrder()
        assertThat(session.snapshot.result.posts.map(TopicSummary::targetPostId)).containsExactly(100L, 101L).inOrder()
        assertThat(session.snapshot.result.users.map(SearchUserSummary::username)).containsExactly("Baba", "alice").inOrder()
        assertThat(session.snapshot.page).isEqualTo(1)
        assertThat(session.snapshot.hasMore).isFalse()
    }

    @Test
    fun duplicateOnlyPageStopsFurtherPagination() {
        val session = TopicSearchSession()
        session.accept(
            session.start("query"),
            SearchResult(topics = listOf(topic(1, "one")), hasMore = true),
        )
        val next = session.nextPage()!!

        session.accept(next, SearchResult(topics = listOf(topic(1, "one")), hasMore = true))

        assertThat(session.snapshot.hasMore).isFalse()
        assertThat(session.nextPage()).isNull()
    }

    @Test
    fun loadMoreFailureKeepsLoadedRowsAndCanRetryPage() {
        val session = TopicSearchSession()
        session.accept(
            session.start("query"),
            SearchResult(topics = listOf(topic(1, "one")), hasMore = true),
        )
        val failed = session.nextPage()!!

        session.fail(failed, "offline")

        assertThat(session.snapshot.result.topics.map(TopicSummary::id)).containsExactly(1L)
        assertThat(session.snapshot.loadMoreError).isEqualTo("offline")
        assertThat(session.nextPage()?.page).isEqualTo(1)
    }

    private fun topic(id: Long, title: String) = TopicSummary(
        id = id,
        title = title,
        authorUsername = "author",
    )

    private fun post(topicId: Long, postId: Long) = TopicSummary(
        id = topicId,
        title = "post",
        authorUsername = "author",
        targetPostId = postId,
    )
}
