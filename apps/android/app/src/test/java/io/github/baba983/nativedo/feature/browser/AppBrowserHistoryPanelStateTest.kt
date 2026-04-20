package io.github.baba983.nativedo.feature.browser

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.util.Locale
import org.junit.Test

class AppBrowserHistoryPanelStateTest {
    @Test
    fun filterHistoryEntries_matchesTitleHostAndUrl() {
        val entries = listOf(
            BrowserHistoryEntry(
                title = "CDK Dashboard",
                url = "https://cdk.linux.do/dashboard",
                offset = null,
                host = "cdk.linux.do",
                visitedAtMillis = 1_710_000_000_000L,
            ),
            BrowserHistoryEntry(
                title = "Render Debug Case",
                url = "https://linux.do/t/render-debug-case/123456",
                offset = null,
                host = "linux.do",
                visitedAtMillis = 1_710_000_100_000L,
            ),
        )

        assertThat(filterBrowserHistoryEntries(entries, "cdk")).hasSize(1)
        assertThat(filterBrowserHistoryEntries(entries, "render-debug-case")).hasSize(1)
        assertThat(filterBrowserHistoryEntries(entries, "linux.do")).hasSize(2)
    }

    @Test
    fun groupHistoryEntries_splitsTodayYesterdayAndOlder() {
        val groups = groupBrowserHistoryEntries(
            entries = listOf(
                BrowserHistoryEntry(
                    title = "Today",
                    url = "https://linux.do/t/today/1",
                    offset = null,
                    host = "linux.do",
                    visitedAtMillis = 1_710_000_000_000L,
                ),
                BrowserHistoryEntry(
                    title = "Yesterday",
                    url = "https://linux.do/t/yesterday/2",
                    offset = null,
                    host = "linux.do",
                    visitedAtMillis = 1_709_913_600_000L,
                ),
            ),
            nowMillis = 1_710_000_000_000L,
        )

        assertThat(groups.map { group -> group.label }).containsAtLeast("Today", "Yesterday")
    }

    @Test
    fun badgeLabel_usesTitleFirstLetterWhenAvailable() {
        assertThat(
            browserHistoryBadgeLabel(
                title = "Linux.do topic",
                host = "linux.do",
            ),
        ).isEqualTo("L")
    }

    @Test
    fun badgeLabel_fallsBackToHostWhenTitleIsBlank() {
        assertThat(
            browserHistoryBadgeLabel(
                title = "",
                host = "cdk.linux.do",
            ),
        ).isEqualTo("C")
    }

    @Test
    fun timeLabel_showsHourMinuteForToday() {
        assertThat(
            browserHistoryRowTimeLabel(
                visitedAtMillis = 1_710_000_000_000L,
                nowMillis = 1_710_000_100_000L,
                zoneId = ZoneId.of("UTC"),
                locale = Locale.US,
            ),
        ).isEqualTo("16:00")
    }

    @Test
    fun timeLabel_showsYesterdayForPreviousDay() {
        assertThat(
            browserHistoryRowTimeLabel(
                visitedAtMillis = 1_709_913_600_000L,
                nowMillis = 1_710_000_000_000L,
                zoneId = ZoneId.of("UTC"),
                locale = Locale.US,
            ),
        ).isEqualTo("Yesterday")
    }

    @Test
    fun timeLabel_showsMonthDayForOlderEntries() {
        assertThat(
            browserHistoryRowTimeLabel(
                visitedAtMillis = 1_709_222_400_000L,
                nowMillis = 1_710_000_000_000L,
                zoneId = ZoneId.of("UTC"),
                locale = Locale.US,
            ),
        ).isEqualTo("Feb 29")
    }
}
