package io.github.baba983.nativedo.messages

import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.model.CreatedTopic
import io.github.baba983.nativedo.core.model.TopicListPage
import io.github.baba983.nativedo.feature.messages.data.PrivateMessageBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Bridges the private-message data layer to the Java screens, the way
 * [io.github.baba983.nativedo.notifications.NotificationsLoader] and
 * [io.github.baba983.nativedo.topic.DiscourseLoader] do: a suspend call made from Java needs a
 * continuation by hand, so the launch lives here and reports back through callbacks.
 */
object PrivateMessagesLoader {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** One page of one box, from [page] on. */
    fun page(
        container: AppContainer,
        username: String,
        box: PrivateMessageBox,
        page: Int,
        onLoaded: (TopicListPage) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            container.privateMessagesRepository.load(username = username, box = box, page = page)
                .onSuccess(onLoaded)
                .onFailure(onFailed)
        }
    }

    /**
     * Starts a private message and reports the topic the site created. The site owns the permission
     * checks: a recipient the reader may not message comes back as a refusal the caller shows.
     */
    fun send(
        container: AppContainer,
        title: String,
        recipients: String,
        raw: String,
        onCreated: (CreatedTopic) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            runCatching { container.discourseApiClient.createPrivateMessage(title, recipients, raw) }
                .onSuccess(onCreated)
                .onFailure(onFailed)
        }
    }
}
