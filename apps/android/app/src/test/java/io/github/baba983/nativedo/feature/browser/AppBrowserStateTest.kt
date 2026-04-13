package io.github.baba983.nativedo.feature.browser

import io.github.baba983.nativedo.core.storage.AppBrowserHistorySnapshot
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.storage.AppBrowserHistoryStorePersistence
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppBrowserStateTest {
    @Test
    fun appBrowserRoutePresentationState_clampsOffsetAndDerivesProgressAlphaAndBackdrop() {
        val state = buildAppBrowserRoutePresentationState(
            offsetPx = 120f,
            screenWidthPx = 400f,
        )

        assertThat(state.progress).isWithin(0.0001f).of(0.7f)
        assertThat(state.alpha).isWithin(0.0001f).of(0.976f)
        assertThat(state.backdropAlpha).isWithin(0.0001f).of(0.096f)
    }

    @Test
    fun appBrowserRoutePresentationState_clampsOverflowOffsetToClosedState() {
        val state = buildAppBrowserRoutePresentationState(
            offsetPx = 999f,
            screenWidthPx = 400f,
        )

        assertThat(state.progress).isEqualTo(0f)
        assertThat(state.alpha).isWithin(0.0001f).of(0.92f)
        assertThat(state.backdropAlpha).isWithin(0.0001f).of(0.32f)
    }

    @Test
    fun appBrowserSwipeSettle_decidesDismissWhenThresholdExceeded() {
        val settle = buildAppBrowserSwipeSettle(
            currentOffsetPx = 180f,
            velocityPxPerSecond = 100f,
            screenWidthPx = 400f,
        )

        assertThat(settle.shouldDismiss).isTrue()
        assertThat(settle.targetOffsetPx).isEqualTo(400f)
        assertThat(settle.durationMillis).isEqualTo(250)
    }

    @Test
    fun appBrowserSwipeSettle_decidesDismissWhenVelocityExceeded() {
        val settle = buildAppBrowserSwipeSettle(
            currentOffsetPx = 80f,
            velocityPxPerSecond = 900f,
            screenWidthPx = 400f,
        )

        assertThat(settle.shouldDismiss).isTrue()
        assertThat(settle.targetOffsetPx).isEqualTo(400f)
        assertThat(settle.durationMillis).isEqualTo(336)
    }

    @Test
    fun appBrowserSwipeSettle_snapsBackWhenBelowThresholdAndVelocity() {
        val settle = buildAppBrowserSwipeSettle(
            currentOffsetPx = 80f,
            velocityPxPerSecond = 100f,
            screenWidthPx = 400f,
        )

        assertThat(settle.shouldDismiss).isFalse()
        assertThat(settle.targetOffsetPx).isEqualTo(0f)
        assertThat(settle.durationMillis).isEqualTo(250)
    }

    @Test
    fun appBrowserLoadProgressState_start_makesIndicatorVisibleWithHeadStart() {
        val state = AppBrowserLoadProgressState().start()

        assertThat(state.displayedProgress).isEqualTo(0.3f)
        assertThat(state.actualProgress).isEqualTo(0f)
        assertThat(state.isLoading).isTrue()
        assertThat(state.isVisible).isTrue()
    }

    @Test
    fun appBrowserLoadProgressState_advanceFakeTick_stopsAtTelegramCap() {
        var state = AppBrowserLoadProgressState().start()

        repeat(40) {
            state = state.advanceFakeTick()
        }

        assertThat(state.displayedProgress).isEqualTo(0.7f)
        assertThat(state.isLoading).isTrue()
        assertThat(state.isVisible).isTrue()
    }

    @Test
    fun appBrowserLoadProgressState_onActualProgressChanged_neverRegressesAndAllowsJumpAhead() {
        var state = AppBrowserLoadProgressState().start()
        state = state.advanceFakeTick()
        state = state.advanceFakeTick()

        val nonRegressed = state.onActualProgressChanged(0.12f)
        val jumpedAhead = nonRegressed.onActualProgressChanged(0.84f)

        assertThat(nonRegressed.displayedProgress).isEqualTo(state.displayedProgress)
        assertThat(jumpedAhead.displayedProgress).isEqualTo(0.84f)
        assertThat(jumpedAhead.actualProgress).isEqualTo(0.84f)
    }

    @Test
    fun appBrowserLoadProgressState_finish_keepsFullProgressUntilDismissed() {
        val state = AppBrowserLoadProgressState()
            .start()
            .onActualProgressChanged(0.86f)
            .finish()
        val dismissed = state.dismiss()

        assertThat(state.displayedProgress).isEqualTo(1f)
        assertThat(state.actualProgress).isEqualTo(1f)
        assertThat(state.isLoading).isFalse()
        assertThat(state.isVisible).isTrue()
        assertThat(dismissed.displayedProgress).isEqualTo(0f)
        assertThat(dismissed.actualProgress).isEqualTo(0f)
        assertThat(dismissed.isVisible).isFalse()
    }

    @Test
    fun appBrowserLoadProgressState_advanceFakeTick_usesTelegramTickCadence() {
        val fastTickState = AppBrowserLoadProgressState(
            displayedProgress = 0.3f,
            actualProgress = 0f,
            isLoading = true,
            isVisible = true,
        ).advanceFakeTick()
        val slowTickState = AppBrowserLoadProgressState(
            displayedProgress = 0.52f,
            actualProgress = 0f,
            isLoading = true,
            isVisible = true,
        ).advanceFakeTick()

        assertThat(fastTickState.displayedProgress).isWithin(0.0001f).of(0.32f)
        assertThat(slowTickState.displayedProgress).isWithin(0.0001f).of(0.53f)
    }

    @Test
    fun appBrowserScrollProgress_zeroWhenContentFitsViewport() {
        val progress = buildAppBrowserScrollProgress(
            scrollY = 0,
            contentHeightPx = 640,
            viewportHeightPx = 640,
        )

        assertThat(progress).isEqualTo(0f)
    }

    @Test
    fun appBrowserScrollProgress_computesFractionFromScrollableRange() {
        val progress = buildAppBrowserScrollProgress(
            scrollY = 240,
            contentHeightPx = 1_200,
            viewportHeightPx = 600,
        )

        assertThat(progress).isEqualTo(0.4f)
    }

    @Test
    fun appBrowserScrollProgress_clampsToFullRange() {
        val progress = buildAppBrowserScrollProgress(
            scrollY = 999,
            contentHeightPx = 1_200,
            viewportHeightPx = 600,
        )

        assertThat(progress).isEqualTo(1f)
    }

    @Test
    fun appBrowserScrollChromeBackgroundState_hidesFillWhileLoading() {
        val state = buildAppBrowserScrollChromeBackgroundState(
            scrollProgress = 0.75f,
            isLoadingVisible = true,
        )

        assertThat(state.fillFraction).isEqualTo(0f)
        assertThat(state.fillAlpha).isEqualTo(0f)
        assertThat(state.dividerAlpha).isEqualTo(0.10f)
    }

    @Test
    fun appBrowserScrollChromeBackgroundState_scalesFillAndShadowWithScroll() {
        val state = buildAppBrowserScrollChromeBackgroundState(
            scrollProgress = 0.5f,
            isLoadingVisible = false,
        )

        assertThat(state.fillFraction).isEqualTo(0.5f)
        assertThat(state.fillAlpha).isWithin(0.0001f).of(0.09f)
        assertThat(state.dividerAlpha).isWithin(0.0001f).of(0.13f)
    }

    @Test
    fun appBrowserScrollChromeBackgroundState_clampsOverflowProgress() {
        val state = buildAppBrowserScrollChromeBackgroundState(
            scrollProgress = 1.4f,
            isLoadingVisible = false,
        )

        assertThat(state.fillFraction).isEqualTo(1f)
        assertThat(state.fillAlpha).isWithin(0.0001f).of(0.18f)
        assertThat(state.dividerAlpha).isWithin(0.0001f).of(0.16f)
    }

    @Test
    fun appBrowserSearchPanelState_hiddenWhenSearchModeIsClosed() {
        val state = buildAppBrowserSearchPanelState(
            isSearching = false,
            query = "linux",
            activeMatchOrdinal = 0,
            numberOfMatches = 4,
        )

        assertThat(state.visible).isEqualTo(false)
    }

    @Test
    fun appBrowserSearchPanelState_hiddenWhenQueryIsBlank() {
        val state = buildAppBrowserSearchPanelState(
            isSearching = true,
            query = " ",
            activeMatchOrdinal = 0,
            numberOfMatches = 4,
        )

        assertThat(state.visible).isEqualTo(false)
    }

    @Test
    fun appBrowserSearchPanelState_showsNoResultsWhenNothingMatches() {
        val state = buildAppBrowserSearchPanelState(
            isSearching = true,
            query = "linux",
            activeMatchOrdinal = 0,
            numberOfMatches = 0,
        )

        assertThat(state.visible).isEqualTo(true)
        assertThat(state.matchText).isEmpty()
        assertThat(state.canNavigate).isEqualTo(false)
        assertThat(state.showsNoResults).isTrue()
    }

    @Test
    fun appBrowserSearchPanelState_showsOrdinalAndEnablesNavigationWhenMatchesExist() {
        val state = buildAppBrowserSearchPanelState(
            isSearching = true,
            query = "linux",
            activeMatchOrdinal = 1,
            numberOfMatches = 4,
        )

        assertThat(state.visible).isEqualTo(true)
        assertThat(state.matchText).isEqualTo("2/4")
        assertThat(state.canNavigate).isEqualTo(true)
        assertThat(state.showsNoResults).isFalse()
    }

    @Test
    fun appBrowserSearchPanelState_hidesWhenComposingTextResolvesToBlank() {
        val state = buildAppBrowserSearchPanelState(
            isSearching = true,
            query = "",
            activeMatchOrdinal = 0,
            numberOfMatches = 3,
        )

        assertThat(state.visible).isEqualTo(false)
        assertThat(state.canNavigate).isEqualTo(false)
        assertThat(state.showsNoResults).isFalse()
    }

    @Test
    fun buildAppBrowserMenuEntries_telegramLayout_includesSearchAndKeepsThreeGroups() {
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
            AppBrowserMenuAction.Search,
            AppBrowserMenuAction.History,
            AppBrowserMenuAction.Settings,
            AppBrowserMenuAction.Close,
        ).inOrder()
        assertThat(entries.count { entry -> entry is AppBrowserMenuEntry.Divider }).isEqualTo(2)
    }

    @Test
    fun buildAppBrowserMenuEntries_includesSearchBeforeHistory() {
        val entries = buildAppBrowserMenuEntries(
            canGoForward = true,
            hasBrowserHistory = true,
        )

        val actions = entries
            .filterIsInstance<AppBrowserMenuEntry.Item>()
            .map { entry -> entry.item.action }

        assertThat(actions).containsAtLeast(
            AppBrowserMenuAction.Search,
            AppBrowserMenuAction.History,
        ).inOrder()
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
    fun appBrowserHistoryStore_recordVisit_keepsNewestEntryAndDedupesUrl() = runTest {
        val store = AppBrowserHistoryStore(
            persistenceDispatcher = StandardTestDispatcher(testScheduler),
        )

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
        advanceUntilIdle()

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
    fun formatBrowserDataSize_zeroBytes_showsZero() {
        assertThat(formatAppBrowserDataSize(0L)).isEqualTo("0 B")
    }

    @Test
    fun formatBrowserDataSize_kilobytes() {
        assertThat(formatAppBrowserDataSize(1_536L)).isEqualTo("1.5 KB")
    }

    @Test
    fun formatBrowserDataSize_megabytes() {
        assertThat(formatAppBrowserDataSize(2_621_440L)).isEqualTo("2.5 MB")
    }

    @Test
    fun readAppBrowserStorageUsage_countsNestedCookieFilesSeparatelyFromCache() {
        val appDataDir = createTempDir(prefix = "appData")
        val appCacheDir = createTempDir(prefix = "appCache")
        try {
            writeSizedFile(File(appDataDir, "app_webview/Default/Cookies"), 120)
            writeSizedFile(File(appDataDir, "app_webview/Default/Cache_Data"), 80)
            writeSizedFile(File(appCacheDir, "WebView/page-cache"), 40)
            writeSizedFile(File(appDataDir, "databases/webview.db"), 30)
            writeSizedFile(File(appDataDir, "databases/webviewCache.db"), 20)

            val usage = readAppBrowserStorageUsage(
                appDataDir = appDataDir,
                appCacheDir = appCacheDir,
            )

            assertThat(usage.cookiesSizeBytes).isEqualTo(120L)
            assertThat(usage.cacheSizeBytes).isEqualTo(170L)
        } finally {
            appDataDir.deleteRecursively()
            appCacheDir.deleteRecursively()
        }
    }

    @Test
    fun clearAppBrowserCacheFiles_keepsCookieFiles() {
        val appDataDir = createTempDir(prefix = "appData")
        val appCacheDir = createTempDir(prefix = "appCache")
        try {
            val cookieFile = File(appDataDir, "app_webview/Default/Cookies")
            val cacheFile = File(appDataDir, "app_webview/Default/Cache_Data")
            val sharedCacheFile = File(appCacheDir, "WebView/page-cache")
            val webviewDb = File(appDataDir, "databases/webview.db")
            writeSizedFile(cookieFile, 120)
            writeSizedFile(cacheFile, 80)
            writeSizedFile(sharedCacheFile, 40)
            writeSizedFile(webviewDb, 30)

            clearAppBrowserCacheFiles(
                appDataDir = appDataDir,
                appCacheDir = appCacheDir,
            )

            assertThat(cookieFile.exists()).isTrue()
            assertThat(cacheFile.exists()).isFalse()
            assertThat(sharedCacheFile.exists()).isFalse()
            assertThat(webviewDb.exists()).isFalse()
        } finally {
            appDataDir.deleteRecursively()
            appCacheDir.deleteRecursively()
        }
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
    fun historyStore_clear_removesPersistedEntries() = runTest {
        val persistence = InMemoryAppBrowserHistoryStorePersistence()
        val store = AppBrowserHistoryStore(
            persistence = persistence,
            persistenceDispatcher = StandardTestDispatcher(testScheduler),
        )

        store.recordVisit(
            url = "https://linux.do/t/render-debug-case/123456",
            title = "Render Debug Case",
            visitedAtMillis = 200L,
        )
        store.clear()
        advanceUntilIdle()

        assertThat(store.history.value).isEmpty()
        assertThat(persistence.saved).isEmpty()
    }

    @Test
    fun historyStore_clearImmediate_clearsVisibleStateBeforeBackgroundPersistenceRuns() = runTest {
        val persistence = InMemoryAppBrowserHistoryStorePersistence()
        val store = AppBrowserHistoryStore(
            persistence = persistence,
            persistenceDispatcher = StandardTestDispatcher(testScheduler),
        )

        store.recordVisit(
            url = "https://linux.do/t/render-debug-case/123456",
            title = "Render Debug Case",
            visitedAtMillis = 200L,
        )
        advanceUntilIdle()

        store.clearImmediate()

        assertThat(store.history.value).isEmpty()
        advanceUntilIdle()
        assertThat(persistence.saved).isEmpty()
    }

    @Test
    fun historyStore_restore_keepsPersistedFaviconSnapshot() {
        val persistence = InMemoryAppBrowserHistoryStorePersistence(
            records = listOf(
                AppBrowserHistorySnapshot(
                    title = "Linux.do",
                    url = "https://linux.do",
                    host = "linux.do",
                    visitedAtMillis = 300L,
                    faviconBase64 = "ZmFrZS1pY29u",
                ),
            ),
        )

        val store = AppBrowserHistoryStore(persistence = persistence)

        assertThat(store.history.value.single().faviconBase64).isEqualTo("ZmFrZS1pY29u")
    }

    @Test
    fun historyStore_recordVisit_withoutNewFavicon_keepsExistingSnapshot() = runTest {
        val persistence = InMemoryAppBrowserHistoryStorePersistence(
            records = listOf(
                AppBrowserHistorySnapshot(
                    title = "Linux.do",
                    url = "https://linux.do",
                    host = "linux.do",
                    visitedAtMillis = 100L,
                    faviconBase64 = "persisted-favicon",
                ),
            ),
        )
        val store = AppBrowserHistoryStore(
            persistence = persistence,
            persistenceDispatcher = StandardTestDispatcher(testScheduler),
        )

        store.recordVisit(
            url = "https://linux.do",
            title = "Linux.do Updated",
            visitedAtMillis = 200L,
            favicon = null,
        )
        advanceUntilIdle()

        assertThat(store.history.value.single().faviconBase64).isEqualTo("persisted-favicon")
        assertThat(persistence.saved.single().faviconBase64).isEqualTo("persisted-favicon")
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

private fun writeSizedFile(file: File, size: Int) {
    file.parentFile?.mkdirs()
    file.writeBytes(ByteArray(size) { 1 })
}
