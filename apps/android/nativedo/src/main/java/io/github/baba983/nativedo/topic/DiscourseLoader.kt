package io.github.baba983.nativedo.topic

import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.TopicDetail
import io.github.baba983.nativedo.core.model.TopicSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Bridges the copied Kotlin data layer to the Java fragments. The fragments are Java because they
 * are thin hosts around upstream's Java widgets; calling a suspend function from there means
 * generating a continuation by hand, so the launch lives here and reports back through callbacks.
 */
object DiscourseLoader {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun latestTopics(
        container: AppContainer,
        onLoaded: (List<TopicSummary>) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            runCatching { container.discourseApiClient.fetchLatestPage(0).topics }
                .onSuccess(onLoaded)
                .onFailure(onFailed)
        }
    }

    fun topic(
        container: AppContainer,
        topicId: Long,
        onLoaded: (TopicDetail) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            runCatching { container.discourseApiClient.fetchTopic(topicId) }
                .onSuccess(onLoaded)
                .onFailure(onFailed)
        }
    }

    /** The posts of a topic as the models the stock cells are built from. */
    fun posts(detail: TopicDetail): List<Post> = detail.posts
}