package io.github.baba983.nativedo.core.storage

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppBrowserRecentSearchStoreTest {

    @Test
    fun recordSearch_dedupesAndMovesNewestQueryToFront() = runTest {
        val persistence = InMemoryAppBrowserRecentSearchStorePersistence()
        val store = AppBrowserRecentSearchStore(
            persistence = persistence,
            persistenceDispatcher = StandardTestDispatcher(testScheduler),
        )

        store.recordSearch("compose")
        store.recordSearch("browser")
        store.recordSearch("compose")
        advanceUntilIdle()

        assertThat(store.recentSearches.value.map { it.query }).containsExactly("compose", "browser").inOrder()
        assertThat(persistence.saved!!.map { it.query }).containsExactly("compose", "browser").inOrder()
    }

    @Test
    fun restore_keepsPersistedRecentSearches() {
        val store = AppBrowserRecentSearchStore(
            persistence = InMemoryAppBrowserRecentSearchStorePersistence(
                snapshot = listOf(
                    AppBrowserRecentSearchSnapshot(
                        query = "compose",
                        searchedAtMillis = 42L,
                    ),
                ),
            ),
        )

        assertThat(store.recentSearches.value).containsExactly(
            AppBrowserRecentSearchRecord(
                query = "compose",
                searchedAtMillis = 42L,
            ),
        )
    }
}

private class InMemoryAppBrowserRecentSearchStorePersistence(
    snapshot: List<AppBrowserRecentSearchSnapshot>? = null,
) : AppBrowserRecentSearchStorePersistence {
    var saved: List<AppBrowserRecentSearchSnapshot>? = snapshot

    override fun load(): List<AppBrowserRecentSearchSnapshot> = saved.orEmpty()

    override fun save(records: List<AppBrowserRecentSearchSnapshot>) {
        saved = records
    }
}
