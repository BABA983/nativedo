package io.github.baba983.nativedo.feature.topic.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.Post
import org.junit.Test

class TopicReplyTargetResolverTest {
    @Test
    fun topicReplyTargetsByPostNumber_offsetsIndexesForTopicHeader() {
        val posts = listOf(
            post(id = 1001L, postNumber = 1, username = "baba", cookedHtml = "<p>Hello</p>"),
            post(id = 1002L, postNumber = 2, username = "alice", cookedHtml = "<p>Reply</p>"),
        )

        assertThat(topicReplyTargetsByPostNumber(posts))
            .containsExactly(
                1,
                TopicReplyTarget(
                    itemIndex = 1,
                    postId = 1001L,
                    postNumber = 1,
                    authorUsername = "baba",
                    authorDisplayName = "",
                    authorAvatarTemplate = "/user_avatar/linux.do/baba/{size}/1.png",
                    authorAnimatedAvatarUrl = "",
                    createdAt = "",
                    cookedHtml = "<p>Hello</p>",
                ),
                2,
                TopicReplyTarget(
                    itemIndex = 2,
                    postId = 1002L,
                    postNumber = 2,
                    authorUsername = "alice",
                    authorDisplayName = "",
                    authorAvatarTemplate = "/user_avatar/linux.do/alice/{size}/1.png",
                    authorAnimatedAvatarUrl = "",
                    createdAt = "",
                    cookedHtml = "<p>Reply</p>",
                ),
            )
    }

    @Test
    fun topicReplySheetEntriesByPostNumber_groupsRepliesAndBuildsPreview() {
        val posts = listOf(
            post(id = 1001L, postNumber = 1, username = "baba", cookedHtml = "<p>Hello</p>"),
            post(
                id = 1002L,
                postNumber = 2,
                replyToPostNumber = 1,
                username = "alice",
                displayName = "Alice",
                cookedHtml = "<p>Reply <strong>preview</strong></p>",
            ),
            post(
                id = 1003L,
                postNumber = 3,
                replyToPostNumber = 1,
                username = "charlie",
                cookedHtml = "<p>Second reply</p>",
            ),
        )

        val entries = topicReplySheetEntriesByPostNumber(posts)

        assertThat(entries.keys).containsExactly(1)
        assertThat(entries.getValue(1).map { it.postId }).containsExactly(1002L, 1003L).inOrder()
        assertThat(entries.getValue(1).first().authorDisplayName).isEqualTo("Alice")
        assertThat(entries.getValue(1).first().previewText).isEqualTo("Reply preview")
    }

    private fun post(
        id: Long,
        postNumber: Int,
        username: String,
        cookedHtml: String,
        replyToPostNumber: Int? = null,
        displayName: String = "",
    ): Post = Post(
        id = id,
        postNumber = postNumber,
        replyToPostNumber = replyToPostNumber,
        authorUsername = username,
        authorDisplayName = displayName,
        authorAvatarTemplate = "/user_avatar/linux.do/${username.lowercase()}/{size}/1.png",
        cookedHtml = cookedHtml,
    )
}
