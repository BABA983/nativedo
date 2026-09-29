package io.github.baba983.nativedo.auth

import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.session.AppSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Restores the Discourse session at startup and reports whether it is usable.
 *
 * The fragments are Java because they host upstream's Java widgets, and calling a suspend function
 * from there means building a continuation by hand, so the coroutine lives here and reports back
 * through a callback.
 */
object SessionStarter {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun start(container: AppContainer, onResult: (AppSessionState) -> Unit) {
        scope.launch {
            // start() restores from the cookie store and verifies against the site; a failure here
            // is "not logged in", not an error to surface.
            runCatching { container.sessionCoordinator.start() }
                .onSuccess { onResult(container.sessionCoordinator.sessionState.value) }
                .onFailure { onResult(AppSessionState.Anonymous) }
        }
    }
}