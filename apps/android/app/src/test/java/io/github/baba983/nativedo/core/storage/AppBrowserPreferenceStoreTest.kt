package io.github.baba983.nativedo.core.storage

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppBrowserPreferenceStoreTest {

    @Test
    fun restore_keepsPersistedPreferenceSnapshot() {
        val store = AppBrowserPreferenceStore(
            persistence = InMemoryAppBrowserPreferenceStorePersistence(
                snapshot = AppBrowserPreferenceSnapshot(
                    defaultExternalOpenMode = AppBrowserExternalOpenMode.SystemBrowser,
                    searchEngine = AppBrowserSearchEngine.Google,
                    restrictedDomains = listOf("example.com"),
                ),
            ),
        )

        assertThat(store.preferences.value.defaultExternalOpenMode).isEqualTo(AppBrowserExternalOpenMode.SystemBrowser)
        assertThat(store.preferences.value.searchEngine).isEqualTo(AppBrowserSearchEngine.Google)
        assertThat(store.preferences.value.restrictedDomains).containsExactly("example.com")
    }

    @Test
    fun updateRestrictedDomains_normalizesAndPersistsHosts() = runTest {
        val persistence = InMemoryAppBrowserPreferenceStorePersistence()
        val store = AppBrowserPreferenceStore(
            persistence = persistence,
            persistenceDispatcher = StandardTestDispatcher(testScheduler),
        )

        store.updateRestrictedDomains(listOf(" https://WWW.Example.com/path ", "sub.example.com", "", "example.com"))
        advanceUntilIdle()

        assertThat(store.preferences.value.restrictedDomains).containsExactly("example.com", "sub.example.com").inOrder()
        assertThat(persistence.saved?.restrictedDomains).containsExactly("example.com", "sub.example.com").inOrder()
    }

    @Test
    fun setSearchEngine_updatesAndPersistsPreference() = runTest {
        val persistence = InMemoryAppBrowserPreferenceStorePersistence()
        val store = AppBrowserPreferenceStore(
            persistence = persistence,
            persistenceDispatcher = StandardTestDispatcher(testScheduler),
        )

        store.setSearchEngine(AppBrowserSearchEngine.Bing)
        advanceUntilIdle()

        assertThat(store.preferences.value.searchEngine).isEqualTo(AppBrowserSearchEngine.Bing)
        assertThat(persistence.saved?.searchEngine).isEqualTo(AppBrowserSearchEngine.Bing)
    }
}

private class InMemoryAppBrowserPreferenceStorePersistence(
    snapshot: AppBrowserPreferenceSnapshot? = null,
) : AppBrowserPreferenceStorePersistence {
    var saved: AppBrowserPreferenceSnapshot? = snapshot

    override fun load(): AppBrowserPreferenceSnapshot? = saved

    override fun save(snapshot: AppBrowserPreferenceSnapshot) {
        saved = snapshot
    }
}
