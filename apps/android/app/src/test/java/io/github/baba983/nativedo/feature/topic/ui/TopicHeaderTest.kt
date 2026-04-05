package io.github.baba983.nativedo.feature.topic.ui

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import org.junit.Test

class TopicHeaderTest {

    @Test
    fun topicHeaderDateLine_includesStartedAndActiveDates() {
        assertThat(
            topicHeaderDateLine(
                createdAt = "2026-04-01T02:30:00.000Z",
                lastPostedAt = "2026-04-05T09:07:00.000Z",
                zoneId = ZoneId.of("UTC"),
            ),
        ).isEqualTo("Started 2026-04-01 · Active 2026-04-05")
    }

    @Test
    fun topicHeaderStatsLine_usesCompactNumbers() {
        assertThat(
            topicHeaderStatsLine(
                viewCount = 0,
                totalPosts = 1284,
                loadedPosts = 3,
            ),
        ).isEqualTo("1.3K posts")
    }

    @Test
    fun topicHeaderStatsLine_includesViewsAndPosts() {
        assertThat(
            topicHeaderStatsLine(
                viewCount = 1284,
                totalPosts = 43000,
                loadedPosts = 3,
            ),
        ).isEqualTo("1.3K views · 43K posts")
    }

    @Test
    fun topicHeaderStatsLine_fallsBackToLoadedPostCountWhenTotalIsMissing() {
        assertThat(
            topicHeaderStatsLine(
                viewCount = 0,
                totalPosts = 0,
                loadedPosts = 3,
            ),
        ).isEqualTo("3 posts")
    }

    @Test
    fun topicTagLine_prefixesTagsWithHash() {
        assertThat(topicTagLine(listOf("android", "compose")))
            .isEqualTo("#android  #compose")
    }

    @Test
    fun topicHeaderCompactCount_formatsThousands() {
        assertThat(topicHeaderCompactCount(1284)).isEqualTo("1.3K")
        assertThat(topicHeaderCompactCount(12500)).isEqualTo("13K")
    }
}
