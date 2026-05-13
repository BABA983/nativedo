package io.github.baba983.nativedo.feature.topic.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.Post
import java.time.Instant
import java.time.ZoneId
import org.junit.Test

class TopicPostMetaTest {
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
    fun topicFloorLabelForPostNumber_usesAbsoluteFloorNumbers() {
        assertThat(topicFloorLabelForPostNumber(12)).isEqualTo("12F")
    }

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

    @Test
    fun topicPostDisplayName_prefersDisplayNameWhenPresent() {
        assertThat(
            topicPostDisplayName(
                displayName = "Baba",
                username = "baba983",
            ),
        ).isEqualTo("Baba")
    }

    @Test
    fun topicPostDisplayName_fallsBackToUsernameWhenDisplayNameMissing() {
        assertThat(
            topicPostDisplayName(
                displayName = "",
                username = "baba983",
            ),
        ).isEqualTo("baba983")
    }

    @Test
    fun topicPostUsernameLabel_prefixesUsernameWithAtSign() {
        assertThat(topicPostUsernameLabel("very_long_username")).isEqualTo("@very_long_username")
    }

    @Test
    fun topicPostUsesAnimatedTitle_matchesSeedUserTitle() {
        assertThat(topicPostUsesAnimatedTitle("种子用户")).isTrue()
        assertThat(topicPostUsesAnimatedTitle(" 种子用户 ")).isTrue()
        assertThat(topicPostUsesAnimatedTitle("破界者")).isTrue()
    }

    @Test
    fun topicPostUsesAnimatedTitle_ignoresRegularTitles() {
        assertThat(topicPostUsesAnimatedTitle("骐骥驰骋")).isFalse()
        assertThat(topicPostUsesAnimatedTitle("")).isFalse()
    }

    @Test
    fun topicPostMetaLabel_appendsRelativeTimeForRecentReplies() {
        assertThat(
            topicPostMetaLabel(
                index = 2,
                createdAt = "2026-04-05T09:07:00.000Z",
                now = Instant.parse("2026-04-05T09:10:00.000Z"),
                zoneId = ZoneId.of("Asia/Shanghai"),
            ),
        ).isEqualTo("3F · 3m")
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
