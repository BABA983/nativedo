package io.github.baba983.nativedo.topic

import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.TopicDetail
import io.github.baba983.nativedo.core.model.TopicSummary
import org.telegram.tgnet.TLRPC
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

    fun posts(detail: TopicDetail): List<Post> = detail.posts

    /**
     * Fetches the images of the posts that have just been bound, then reports once they are on
     * disk. The cells draw a media block that already exists in the cache and leave a placeholder
     * for one that does not, so the caller only has to re-bind afterwards.
     */
    fun cacheImages(
        container: AppContainer,
        images: List<PostImage>,
        onReady: () -> Unit,
    ) {
        if (images.isEmpty()) {
            onReady()
            return
        }
        scope.launch {
            images.forEach { image ->
                DiscourseImageBridge.cache(container.imageOkHttpClient, image)
            }
            onReady()
        }
    }

    /** Posts a reply and reports the site's answer, or the failure the reader has to see. */
    fun reply(
        container: AppContainer,
        topicId: Long,
        raw: String,
        replyToPostNumber: Int?,
        onSent: () -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            runCatching { container.discourseApiClient.createPost(topicId, raw, replyToPostNumber) }
                .onSuccess { onSent() }
                .onFailure(onFailed)
        }
    }

    /** Toggles one of a post's reactions; the caller re-reads the topic afterwards. */
    fun toggleReaction(
        container: AppContainer,
        postId: Long,
        reactionId: String,
        onDone: () -> Unit,
    ) {
        scope.launch {
            runCatching { container.discourseApiClient.toggleReaction(postId, reactionId) }
                .onFailure { error -> org.telegram.messenger.FileLog.e("reaction toggle failed", error) }
            onDone()
        }
    }

    /** Fetches the files the posts link to, then reports once they are on disk. */
    fun cacheAttachments(
        container: AppContainer,
        attachments: List<PostAttachment>,
        onReady: () -> Unit,
    ) {
        if (attachments.isEmpty()) {
            onReady()
            return
        }
        scope.launch {
            attachments.forEach { attachment ->
                DiscourseImageBridge.cacheAttachment(container.imageOkHttpClient, attachment)
            }
            onReady()
        }
    }

    /** Fetches one participant's avatar next to the location upstream will look it up in. */
    fun cacheAvatar(
        container: AppContainer,
        url: String,
        user: TLRPC.User,
        onReady: () -> Unit,
    ) {
        if (DiscourseAvatars.isCached(user)) {
            onReady()
            return
        }
        scope.launch {
            DiscourseAvatars.cache(container.imageOkHttpClient, url, user)
            onReady()
        }
    }

    /** One more slice of a topic's posts, for the pages above the first. */
    fun topicPosts(
        container: AppContainer,
        topicId: Long,
        postIds: List<Long>,
        onLoaded: (List<Post>) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        if (postIds.isEmpty()) {
            onLoaded(emptyList())
            return
        }
        scope.launch {
            runCatching { container.discourseApiClient.fetchTopicPosts(topicId, postIds) }
                .onSuccess(onLoaded)
                .onFailure(onFailed)
        }
    }
}