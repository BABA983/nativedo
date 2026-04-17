package io.github.baba983.nativedo.core.storage

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal enum class AppBrowserExternalOpenMode {
    InAppBrowser,
    SystemBrowser,
}

@Serializable
internal enum class AppBrowserSearchEngine {
    LinuxDo,
    Google,
    Bing,
}

@Serializable
internal data class AppBrowserPreferenceSnapshot(
    val defaultExternalOpenMode: AppBrowserExternalOpenMode = AppBrowserExternalOpenMode.InAppBrowser,
    val searchEngine: AppBrowserSearchEngine = AppBrowserSearchEngine.LinuxDo,
    val restrictedDomains: List<String> = emptyList(),
)

internal data class AppBrowserPreferences(
    val defaultExternalOpenMode: AppBrowserExternalOpenMode = AppBrowserExternalOpenMode.InAppBrowser,
    val searchEngine: AppBrowserSearchEngine = AppBrowserSearchEngine.LinuxDo,
    val restrictedDomains: Set<String> = emptySet(),
)

internal interface AppBrowserPreferenceStorePersistence {
    fun load(): AppBrowserPreferenceSnapshot?

    fun save(snapshot: AppBrowserPreferenceSnapshot)
}

internal class SharedPreferencesAppBrowserPreferenceStorePersistence(
    private val sharedPreferences: SharedPreferences,
) : AppBrowserPreferenceStorePersistence {
    override fun load(): AppBrowserPreferenceSnapshot? {
        val raw = sharedPreferences.getString(KEY_PREFERENCES, null).orEmpty()
        if (raw.isBlank()) {
            return null
        }
        return runCatching {
            json.decodeFromString(AppBrowserPreferenceSnapshot.serializer(), raw)
        }.getOrNull()
    }

    override fun save(snapshot: AppBrowserPreferenceSnapshot) {
        sharedPreferences.edit()
            .putString(
                KEY_PREFERENCES,
                json.encodeToString(AppBrowserPreferenceSnapshot.serializer(), snapshot),
            )
            .apply()
    }

    private companion object {
        const val KEY_PREFERENCES: String = "app_browser_preferences"
        val json: Json = Json
    }
}

internal class AppBrowserPreferenceStore(
    private val persistence: AppBrowserPreferenceStorePersistence = NoOpAppBrowserPreferenceStorePersistence,
    private val persistenceDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _preferences = MutableStateFlow(
        persistence.load()?.toPreferences() ?: AppBrowserPreferences(),
    )
    val preferences: StateFlow<AppBrowserPreferences> = _preferences.asStateFlow()
    private val persistenceScope = CoroutineScope(SupervisorJob() + persistenceDispatcher)

    fun setDefaultExternalOpenMode(mode: AppBrowserExternalOpenMode) {
        _preferences.value = _preferences.value.copy(defaultExternalOpenMode = mode)
        persist()
    }

    fun setSearchEngine(searchEngine: AppBrowserSearchEngine) {
        _preferences.value = _preferences.value.copy(searchEngine = searchEngine)
        persist()
    }

    fun updateRestrictedDomains(domains: List<String>) {
        val normalized = domains
            .mapNotNull(::normalizeAppBrowserRestrictedDomain)
            .distinct()
            .toSet()
        _preferences.value = _preferences.value.copy(restrictedDomains = normalized)
        persist()
    }

    private fun persist() {
        val snapshot = _preferences.value.toSnapshot()
        persistenceScope.launch {
            persistence.save(snapshot)
        }
    }

    private object NoOpAppBrowserPreferenceStorePersistence : AppBrowserPreferenceStorePersistence {
        override fun load(): AppBrowserPreferenceSnapshot? = null

        override fun save(snapshot: AppBrowserPreferenceSnapshot) = Unit
    }
}

internal fun normalizeAppBrowserRestrictedDomain(input: String): String? {
    val trimmed = input.trim().removePrefix("https://").removePrefix("http://")
    val hostCandidate = trimmed.substringBefore('/').substringBefore('?').substringBefore('#')
    val normalized = hostCandidate.lowercase().removePrefix("www.").trim('.')
    return normalized.takeIf(String::isNotBlank)
}

private fun AppBrowserPreferenceSnapshot.toPreferences(): AppBrowserPreferences {
    return AppBrowserPreferences(
        defaultExternalOpenMode = defaultExternalOpenMode,
        searchEngine = searchEngine,
        restrictedDomains = restrictedDomains
            .mapNotNull(::normalizeAppBrowserRestrictedDomain)
            .toSet(),
    )
}

private fun AppBrowserPreferences.toSnapshot(): AppBrowserPreferenceSnapshot {
    return AppBrowserPreferenceSnapshot(
        defaultExternalOpenMode = defaultExternalOpenMode,
        searchEngine = searchEngine,
        restrictedDomains = restrictedDomains.toList(),
    )
}
