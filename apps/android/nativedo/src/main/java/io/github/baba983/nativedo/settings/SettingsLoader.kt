package io.github.baba983.nativedo.settings

import io.github.baba983.nativedo.core.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Bridges the settings screen's Java fragment to the session layer.
 *
 * <p>Signing out is asynchronous - the cookies are cleared on a storage thread - and the screen
 * that asked for it has to know when it is done, because the login page must not open while the
 * session it is replacing is still in place. The waiting lives here, the same way the topic
 * screens' loads do.
 */
object SettingsLoader {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun signOut(container: AppContainer, onDone: () -> Unit) {
        scope.launch {
            container.sessionCoordinator.onLogout().join()
            onDone()
        }
    }

    /**
     * The signed-in reader's trust level, read from the reader's own profile payload. The
     * settings header shows it next to the username the way an account header shows who the
     * account belongs to; a failed read leaves the header without it rather than showing a zero.
     */
    fun trustLevel(container: AppContainer, username: String, onLoaded: (Int) -> Unit) {
        scope.launch {
            runCatching { container.discourseApiClient.fetchProfile(username).trustLevel }
                .onSuccess(onLoaded)
        }
    }
}
