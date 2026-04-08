package io.github.baba983.nativedo.feature.browser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppBrowserHistoryPanelStateTest {
    @Test
    fun filterHistoryEntries_matchesTitleHostAndUrl() {
        val entries = listOf(
            AppBrowserHistoryEntry(
                title = "CDK Dashboard",
                url = "https://cdk.linux.do/dashboard",
                offset = null,
                host = "cdk.linux.do",
                visitedAtMillis = 1_710_000_000_000L,
            ),
            AppBrowserHistoryEntry(
                title = "Render Debug Case",
                url = "https://linux.do/t/render-debug-case/123456",
                offset = null,
                host = "linux.do",
                visitedAtMillis = 1_710_000_100_000L,
            ),
        )

        assertThat(filterAppBrowserHistoryEntries(entries, "cdk")).hasSize(1)
        assertThat(filterAppBrowserHistoryEntries(entries, "render-debug-case")).hasSize(1)
        assertThat(filterAppBrowserHistoryEntries(entries, "linux.do")).hasSize(2)
    }

    @Test
    fun groupHistoryEntries_splitsTodayYesterdayAndOlder() {
        val groups = groupAppBrowserHistoryEntries(
            entries = listOf(
                AppBrowserHistoryEntry(
                    title = "Today",
                    url = "https://linux.do/t/today/1",
                    offset = null,
                    host = "linux.do",
                    visitedAtMillis = 1_710_000_000_000L,
                ),
                AppBrowserHistoryEntry(
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
}
