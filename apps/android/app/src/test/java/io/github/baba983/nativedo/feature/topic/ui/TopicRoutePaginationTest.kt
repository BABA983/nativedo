package io.github.baba983.nativedo.feature.topic.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.Post
import java.time.Instant
import java.time.ZoneId
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
    fun topicFloorLabelForPostNumber_usesAbsoluteFloorNumbers() {
        assertThat(topicFloorLabelForPostNumber(12)).isEqualTo("12F")
    }

    @Test
    fun topicReplyTargetsByPostNumber_offsetsIndexesForTopicHeader() {
        val posts = listOf(
            Post(
                id = 1001,
                postNumber = 1,
                authorUsername = "baba",
                authorAvatarTemplate = "/user_avatar/linux.do/baba/{size}/1.png",
                cookedHtml = "<p>Hello</p>",
                imageUrls = emptyList(),
            ),
            Post(
                id = 1002,
                postNumber = 2,
                authorUsername = "alice",
                authorAvatarTemplate = "/user_avatar/linux.do/alice/{size}/1.png",
                cookedHtml = "<p>Reply</p>",
                imageUrls = emptyList(),
            ),
        )

        assertThat(topicReplyTargetsByPostNumber(posts))
            .containsExactly(
                1,
                TopicReplyTarget(
                    itemIndex = 1,
                    postId = 1001,
                    authorUsername = "baba",
                    authorAvatarTemplate = "/user_avatar/linux.do/baba/{size}/1.png",
                ),
                2,
                TopicReplyTarget(
                    itemIndex = 2,
                    postId = 1002,
                    authorUsername = "alice",
                    authorAvatarTemplate = "/user_avatar/linux.do/alice/{size}/1.png",
                ),
            )
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
                now = Instant.parse("2026-04-05T09:15:00.000Z"),
                zoneId = ZoneId.of("UTC"),
            ),
        ).isEqualTo("3F · 8m")
    }

    @Test
    fun topicPostMetaLabel_appendsClockTimeForOlderRepliesOnTheSameDay() {
        assertThat(
            topicPostMetaLabel(
                index = 2,
                createdAt = "2026-04-05T09:07:00.000Z",
                now = Instant.parse("2026-04-05T12:15:00.000Z"),
                zoneId = ZoneId.of("UTC"),
            ),
        ).isEqualTo("3F · 09:07")
    }

    @Test
    fun topicPostMetaLabel_appendsDateTimeForOlderRepliesAcrossDays() {
        assertThat(
            topicPostMetaLabel(
                index = 2,
                createdAt = "2026-04-05T09:07:00.000Z",
                now = Instant.parse("2026-04-06T12:15:00.000Z"),
                zoneId = ZoneId.of("UTC"),
            ),
        ).isEqualTo("3F · 04-05 09:07")
    }

    @Test
    fun topicPostMetaLabel_fallsBackToFloorWhenCreatedAtMissing() {
        assertThat(
            topicPostMetaLabel(
                index = 2,
                createdAt = "",
                now = Instant.parse("2026-04-05T12:15:00.000Z"),
                zoneId = ZoneId.of("UTC"),
            ),
        ).isEqualTo("3F")
    }

    @Test
    fun topicPostEditedMetaLabel_formatsCountAndEditTime() {
        assertThat(
            topicPostEditedMetaLabel(
                editCount = 1,
            ),
        ).isEqualTo("1")
    }

    @Test
    fun topicPostEditedMetaLabel_returnsNullWhenThereAreNoEdits() {
        assertThat(
            topicPostEditedMetaLabel(
                editCount = 0,
            ),
        ).isNull()
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
