package io.github.baba983.nativedo.feature.home.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.TopicSummary
import java.time.Instant
import java.time.ZoneId
import org.junit.Test

class TopicListItemTest {

    @Test
    fun topicTagLine_limitsVisibleTags() {
        val topic = TopicSummary(
            id = 1,
            title = "Welcome",
            authorUsername = "baba",
            isPinned = true,
            tags = listOf("公告", "站务", "第三个标签"),
        )

        assertThat(topicTagLine(topic))
            .isEqualTo("#公告 · #站务")
    }

    @Test
    fun topicActivityLine_includesAuthorAndLastPoster() {
        val topic = TopicSummary(
            id = 1,
            title = "Welcome",
            authorUsername = "neo",
            lastPosterUsername = "skl1227",
            lastPostedAt = "2026-04-06T05:53:00.000Z",
        )

        assertThat(topicActivityLine(topic))
            .isEqualTo("@neo · @skl1227")
    }

    @Test
    fun topicRelativeTime_usesCompactShortLabels() {
        assertThat(
            topicRelativeTime(
                rawValue = "2026-04-06T05:53:00.000Z",
                now = Instant.parse("2026-04-06T05:56:00.000Z"),
                zoneId = ZoneId.of("UTC"),
            ),
        ).isEqualTo("3m")
    }

    @Test
    fun topicRelativeTime_fallsBackToDateForOlderItems() {
        assertThat(
            topicRelativeTime(
                rawValue = "2026-03-31T09:00:00.000Z",
                now = Instant.parse("2026-04-10T09:00:00.000Z"),
                zoneId = ZoneId.of("UTC"),
            ),
        ).isEqualTo("2026-03-31")
    }
}
