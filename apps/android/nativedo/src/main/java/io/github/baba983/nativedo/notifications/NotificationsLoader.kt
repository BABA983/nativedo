package io.github.baba983.nativedo.notifications

import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.model.NotificationPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Bridges the notifications data layer to the Java fragment, the way
 * [io.github.baba983.nativedo.topic.DiscourseLoader] bridges the topic screens: a suspend call made
 * from Java needs a continuation by hand, so the launch lives here and reports back through
 * callbacks.
 */
object NotificationsLoader {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** One page of the reader's notifications, from [offset] on. */
    fun page(
        container: AppContainer,
        offset: Int,
        onLoaded: (NotificationPage) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            runCatching { container.notificationRepository.load(offset).getOrThrow() }
                .onSuccess(onLoaded)
                .onFailure(onFailed)
        }
    }
}
