package io.github.baba983.nativedo.feature.topic.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.Post
import java.time.ZoneId
import org.junit.Test

class TopicDateUiTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")
    @Test
    fun groupsPostsByDeviceLocalDay() {
        val entries = topicPostDateEntries(
            posts = listOf(
                post(1, "2026-07-11T15:30:00Z"),
                post(2, "2026-07-11T15:59:59Z"),
                post(3, "2026-07-11T16:00:00Z"),
            ),
            zoneId = shanghai,
        )

        assertThat(entries.map { it.header?.epochDay }).containsExactly(
            java.time.LocalDate.of(2026, 7, 11).toEpochDay(),
            null,
            java.time.LocalDate.of(2026, 7, 12).toEpochDay(),
        ).inOrder()
        assertThat(entries[0].header?.epochSeconds).isEqualTo(java.time.Instant.parse("2026-07-10T16:00:00Z").epochSecond)
        assertThat(entries[2].header?.epochSeconds).isEqualTo(java.time.Instant.parse("2026-07-11T16:00:00Z").epochSecond)
    }

    @Test
    fun invalidTimestampDoesNotCreateOrResetDateGroup() {
        val entries = topicPostDateEntries(
            posts = listOf(
                post(1, "2026-07-12T01:00:00Z"),
                post(2, "invalid"),
                post(3, "2026-07-12T02:00:00Z"),
            ),
            zoneId = shanghai,
        )

        assertThat(entries.map { it.header != null }).containsExactly(true, false, false).inOrder()
    }

    @Test
    fun acceptsDiscourseUtcFallbackFormat() {
        val header = topicDateHeader("2026-07-12 01:02:03 UTC", shanghai)

        assertThat(header?.epochDay).isEqualTo(java.time.LocalDate.of(2026, 7, 12).toEpochDay())
    }

    @Test
    fun parserAcceptsFractionalZuluAndExplicitOffsetAndRejectsInvalidValues() {
        assertThat(parseTopicPostInstant("2026-07-12T01:02:03.456Z"))
            .isEqualTo(java.time.Instant.parse("2026-07-12T01:02:03.456Z"))
        assertThat(parseTopicPostInstant("2026-07-12T09:02:03+08:00"))
            .isEqualTo(java.time.Instant.parse("2026-07-12T01:02:03Z"))
        assertThat(parseTopicPostInstant("")).isNull()
        assertThat(parseTopicPostInstant("not-a-date")).isNull()
    }

    private fun post(id: Long, createdAt: String): Post = Post(
        id = id,
        authorUsername = "user$id",
        createdAt = createdAt,
        cookedHtml = "<p>$id</p>",
    )
}
