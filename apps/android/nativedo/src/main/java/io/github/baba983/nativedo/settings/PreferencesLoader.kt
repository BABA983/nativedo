package io.github.baba983.nativedo.settings

import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.model.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Bridges the preference screens to Discourse, the way the other loaders bridge their screens:
 * the suspend calls live in [io.github.baba983.nativedo.core.network.DiscourseApiClient] and a
 * Java fragment reaches them through callbacks.
 *
 * <p>A failed write is reported back with its failure: the screen that flipped a switch has to
 * put it back when the site refuses the change, or the reader is left looking at a state the
 * server does not have.</p>
 */
object PreferencesLoader {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Only fixed event names and field names; never values a reader typed or the session. The
     * device pass reads the loaded field count and the written field from logcat.
     */
    private fun log(event: String, detail: String) {
        runCatching {
            android.util.Log.i("NativeDoPreferences", "event=$event $detail")
        }
    }

    /** The reader's own preferences from `/u/{username}.json`. */
    fun load(
        container: AppContainer,
        username: String,
        onLoaded: (UserPreferences) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            runCatching { container.discourseApiClient.fetchUserPreferences(username) }
                .onSuccess { preferences ->
                    log("loaded", "fields=7")
                    onLoaded(preferences)
                }
                .onFailure { error ->
                    log("load_failed", "type=${error.javaClass.simpleName}")
                    onFailed(error)
                }
        }
    }

    /** One option's new value, written with the site's own field name and enum value. */
    fun save(
        container: AppContainer,
        username: String,
        fields: Map<String, String>,
        onSaved: () -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            val field = fields.keys.firstOrNull().orEmpty()
            runCatching { container.discourseApiClient.updateUserPreferences(username, fields) }
                .onSuccess {
                    log("saved", "field=$field")
                    onSaved()
                }
                .onFailure { error ->
                    log("save_failed", "field=$field type=${error.javaClass.simpleName}")
                    onFailed(error)
                }
        }
    }
}
