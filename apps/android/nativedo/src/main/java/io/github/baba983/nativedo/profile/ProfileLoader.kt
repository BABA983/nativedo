package io.github.baba983.nativedo.profile

import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Bridges the profile's data layer to the Java fragment, the way
 * [io.github.baba983.nativedo.topic.DiscourseLoader] bridges the topic screens: a suspend call made
 * from Java needs a continuation by hand, so the launch lives here and reports back through
 * callbacks.
 */
object ProfileLoader {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Reads one profile, from the cache when it is there and from the site when it is not. */
    fun load(
        container: AppContainer,
        username: String,
        onLoaded: (UserProfile) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            runCatching { container.profileRepository.load(username).getOrThrow() }
                .onSuccess(onLoaded)
                .onFailure(onFailed)
        }
    }
}
