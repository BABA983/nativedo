package io.github.baba983.nativedo.ui.topic

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.MentionCandidate
import org.junit.Test

class TopicMentionSuggestionsTest {
    @Test
    fun parserFindsMentionAtStartAndAfterWhitespace() {
        assertThat(topicMentionTokenAt("@ali", 4))
            .isEqualTo(TopicMentionToken(start = 0, cursor = 4, end = 4, query = "ali"))
        assertThat(topicMentionTokenAt("hello @ali", 10))
            .isEqualTo(TopicMentionToken(start = 6, cursor = 10, end = 10, query = "ali"))
        assertThat(topicMentionTokenAt("hello\n@ali", 10))
            .isEqualTo(TopicMentionToken(start = 6, cursor = 10, end = 10, query = "ali"))
    }

    @Test
    fun parserUsesCursorRangeAndRejectsInvalidBoundaries() {
        assertThat(topicMentionTokenAt("say @alice later", 8))
            .isEqualTo(TopicMentionToken(start = 4, cursor = 8, end = 10, query = "ali"))
        assertThat(topicMentionTokenAt("email@test", 10)).isNull()
        assertThat(topicMentionTokenAt("hello (@ali", 11))
            .isEqualTo(TopicMentionToken(start = 7, cursor = 11, end = 11, query = "ali"))
        assertThat(topicMentionTokenAt("hello @ali there", 16)).isNull()
        assertThat(topicMentionTokenAt("@orchid.hu", 10))
            .isEqualTo(TopicMentionToken(start = 0, cursor = 10, end = 10, query = "orchid.hu"))
        assertThat(topicMentionTokenAt("@foo_bar-baz", 12))
            .isEqualTo(TopicMentionToken(start = 0, cursor = 12, end = 12, query = "foo_bar-baz"))
    }

    @Test
    fun replacementOnlyChangesCurrentTokenAndPreservesSuffix() {
        val replacement = topicMentionReplacement(
            text = "say @alice later",
            token = TopicMentionToken(start = 4, cursor = 8, end = 10, query = "ali"),
            username = "alice",
        )

        assertThat(replacement).isEqualTo(
            TopicMentionReplacement(text = "say @alice later", cursor = 10),
        )
    }

    @Test
    fun replacementOfCompleteTokenProducesTelegramTrailingSpace() {
        val replacement = topicMentionReplacement(
            text = "say @ali",
            token = TopicMentionToken(start = 4, cursor = 8, end = 8, query = "ali"),
            username = "alice",
        )

        assertThat(replacement?.text?.substring(0, replacement.cursor))
            .isEqualTo("say @alice ")
    }

    @Test
    fun participantsKeepFirstTopicAppearanceAndDedupeCaseInsensitively() {
        val posts = listOf(
            post(1, "Alice", "Alice One"),
            post(2, "bob", "Bob"),
            post(3, "alice", "Alice Two"),
        )

        assertThat(topicMentionParticipants(posts).map { it.username })
            .containsExactly("Alice", "bob").inOrder()
    }

    @Test
    fun serverParticipantsStayAheadOfLoadedPostAuthors() {
        val serverParticipants = listOf(
            MentionCandidate(username = "orchid.hu", displayName = "Orchid", postCount = 8),
            MentionCandidate(username = "Alice", displayName = "Server Alice", postCount = 4),
        )
        val posts = listOf(
            post(1, "alice", "Loaded Alice"),
            post(2, "bob", "Bob"),
        )

        assertThat(topicMentionParticipants(serverParticipants, posts).map { it.username })
            .containsExactly("orchid.hu", "Alice", "bob").inOrder()
    }

    @Test
    fun mergedUsersKeepLocalMatchesBeforeDedupedRemoteMatches() {
        val local = listOf(
            MentionCandidate(username = "alice", displayName = "Alice"),
            MentionCandidate(username = "albert", displayName = "Albert"),
        )
        val remote = listOf(
            MentionCandidate(username = "ALICE", displayName = "Duplicate"),
            MentionCandidate(username = "alex", displayName = "Alex"),
        )

        assertThat(topicMentionMergedUsers(local, remote, "al").map { it.username })
            .containsExactly("alice", "albert", "alex").inOrder()
    }

    private fun post(id: Long, username: String, name: String): Post {
        return Post(
            id = id,
            authorUsername = username,
            authorDisplayName = name,
            cookedHtml = "",
        )
    }
}
