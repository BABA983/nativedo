package io.github.baba983.nativedo.feature.browser

import io.github.baba983.nativedo.core.storage.AppBrowserHistorySnapshot
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.storage.AppBrowserHistoryStorePersistence
import org.junit.Test

class AppBrowserStateTest {
    @Test
    fun buildAppBrowserMenuEntries_telegramLayout_removesSearchAndKeepsThreeGroups() {
        val entries = buildAppBrowserMenuEntries(
            canGoForward = false,
            hasBrowserHistory = true,
        )

        val actions = entries
            .filterIsInstance<AppBrowserMenuEntry.Item>()
            .map { entry -> entry.item.action }

        assertThat(actions).containsExactly(
            AppBrowserMenuAction.Forward,
            AppBrowserMenuAction.Refresh,
            AppBrowserMenuAction.OpenInBrowser,
            AppBrowserMenuAction.Share,
            AppBrowserMenuAction.CopyLink,
            AppBrowserMenuAction.History,
            AppBrowserMenuAction.Settings,
            AppBrowserMenuAction.Close,
        ).inOrder()
        assertThat(entries.count { entry -> entry is AppBrowserMenuEntry.Divider }).isEqualTo(2)
    }

    @Test
    fun buildAppBrowserMenuEntries_withoutBrowserHistory_disablesHistoryButKeepsEntry() {
        val entries = buildAppBrowserMenuEntries(
            canGoForward = false,
            hasBrowserHistory = false,
        )

        val historyEntry = entries
            .filterIsInstance<AppBrowserMenuEntry.Item>()
            .first { entry -> entry.item.action == AppBrowserMenuAction.History }

        assertThat(historyEntry.item.enabled).isFalse()
    }

    @Test
    fun appBrowserHistoryStore_recordVisit_keepsNewestEntryAndDedupesUrl() {
        val store = AppBrowserHistoryStore()

        store.recordVisit(
            url = "https://cdk.linux.do/dashboard",
            title = null,
            visitedAtMillis = 100L,
        )
        store.recordVisit(
            url = "https://linux.do/t/render-debug-case/123456",
            title = "Render Debug Case",
            visitedAtMillis = 200L,
        )
        store.recordVisit(
            url = "https://cdk.linux.do/dashboard",
            title = "CDK Dashboard",
            visitedAtMillis = 300L,
        )

        assertThat(store.history.value.map { entry -> entry.url }).containsExactly(
            "https://cdk.linux.do/dashboard",
            "https://linux.do/t/render-debug-case/123456",
        ).inOrder()
        assertThat(store.history.value.first().title).isEqualTo("CDK Dashboard")
        assertThat(store.history.value.first().visitedAtMillis).isEqualTo(300L)
    }

    @Test
    fun mergeAppBrowserHistoryEntries_withoutBackStack_returnsBrowserHistoryEntries() {
        val merged = mergeAppBrowserHistoryEntries(
            backHistoryEntries = emptyList(),
            browserHistoryEntries = listOf(
                AppBrowserHistoryRecord(
                    title = "CDK Dashboard",
                    url = "https://cdk.linux.do/dashboard",
                    host = "cdk.linux.do",
                    visitedAtMillis = 300L,
                ),
            ),
        )

        assertThat(merged).hasSize(1)
        assertThat(merged.first().url).isEqualTo("https://cdk.linux.do/dashboard")
        assertThat(merged.first().offset).isNull()
    }

    @Test
    fun mergeHistoryEntries_withoutBrowserHistory_returnsEmptyList() {
        val merged = mergeAppBrowserHistoryEntries(
            backHistoryEntries = listOf(
                AppBrowserHistoryEntry(
                    title = "Back stack only",
                    url = "https://linux.do/t/back-only/1",
                    offset = -1,
                    host = "linux.do",
                    visitedAtMillis = 100L,
                ),
            ),
            browserHistoryEntries = emptyList(),
        )

        assertThat(merged).isEmpty()
    }

    @Test
    fun mergeHistoryEntries_prefersBackStackOffsetWhenUrlExistsInCurrentTab() {
        val merged = mergeAppBrowserHistoryEntries(
            backHistoryEntries = listOf(
                AppBrowserHistoryEntry(
                    title = "Topic",
                    url = "https://linux.do/t/render-debug-case/123456",
                    offset = -1,
                    host = "linux.do",
                    visitedAtMillis = 200L,
                ),
            ),
            browserHistoryEntries = listOf(
                AppBrowserHistoryRecord(
                    title = "Topic",
                    url = "https://linux.do/t/render-debug-case/123456",
                    host = "linux.do",
                    visitedAtMillis = 200L,
                ),
            ),
        )

        assertThat(merged.single().offset).isEqualTo(-1)
    }

    @Test
    fun mergeHistoryEntries_prefersNearestBackStackMatchWhenUrlRepeats() {
        val merged = mergeAppBrowserHistoryEntries(
            backHistoryEntries = listOf(
                AppBrowserHistoryEntry(
                    title = "Nearest",
                    url = "https://linux.do/t/repeat/1",
                    offset = -1,
                    host = "linux.do",
                    visitedAtMillis = 300L,
                ),
                AppBrowserHistoryEntry(
                    title = "Older",
                    url = "https://linux.do/t/repeat/1",
                    offset = -3,
                    host = "linux.do",
                    visitedAtMillis = 100L,
                ),
            ),
            browserHistoryEntries = listOf(
                AppBrowserHistoryRecord(
                    title = "Repeat",
                    url = "https://linux.do/t/repeat/1",
                    host = "linux.do",
                    visitedAtMillis = 400L,
                ),
            ),
        )

        assertThat(merged.single().offset).isEqualTo(-1)
        assertThat(merged.single().title).isEqualTo("Nearest")
    }

    @Test
    fun historyStore_restore_keepsPersistedEntries() {
        val persistence = InMemoryAppBrowserHistoryStorePersistence(
            records = listOf(
                AppBrowserHistorySnapshot(
                    title = "CDK Dashboard",
                    url = "https://cdk.linux.do/dashboard",
                    host = "cdk.linux.do",
                    visitedAtMillis = 300L,
                ),
            ),
        )

        val store = AppBrowserHistoryStore(persistence = persistence)

        assertThat(store.history.value).hasSize(1)
        assertThat(store.history.value.single()).isEqualTo(
            AppBrowserHistoryRecord(
                title = "CDK Dashboard",
                url = "https://cdk.linux.do/dashboard",
                host = "cdk.linux.do",
                visitedAtMillis = 300L,
            ),
        )
    }

    @Test
    fun historyStore_clear_removesPersistedEntries() {
        val persistence = InMemoryAppBrowserHistoryStorePersistence()
        val store = AppBrowserHistoryStore(persistence = persistence)

        store.recordVisit(
            url = "https://linux.do/t/render-debug-case/123456",
            title = "Render Debug Case",
            visitedAtMillis = 200L,
        )
        store.clear()

        assertThat(store.history.value).isEmpty()
        assertThat(persistence.saved).isEmpty()
    }
}

private class InMemoryAppBrowserHistoryStorePersistence(
    records: List<AppBrowserHistorySnapshot> = emptyList(),
) : AppBrowserHistoryStorePersistence {
    var saved: List<AppBrowserHistorySnapshot> = records

    override fun load(): List<AppBrowserHistorySnapshot> = saved

    override fun save(records: List<AppBrowserHistorySnapshot>) {
        saved = records
    }
}
