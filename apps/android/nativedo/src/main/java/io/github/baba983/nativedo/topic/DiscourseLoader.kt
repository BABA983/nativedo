package io.github.baba983.nativedo.topic

import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.feature.topic.data.TopicReadPositionKey
import io.github.baba983.nativedo.feature.topic.data.TopicReadReporter
import io.github.baba983.nativedo.feature.topic.data.TopicRepository
import io.github.baba983.nativedo.core.media.PickedMedia
import io.github.baba983.nativedo.core.media.absoluteUploadUrl
import io.github.baba983.nativedo.core.media.replyUploadMarkup
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.PostPoll
import io.github.baba983.nativedo.core.model.PostReaction
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

    /**
     * The reader's place in one topic: the floors the viewport reaches, and the report of them the
     * site asks for. One per topic screen, so its position is that screen's own.
     */
    fun readReporter(container: AppContainer, topicId: Long): TopicReadReporter =
        TopicReadReporter(
            topicId = topicId,
            repository = TopicRepository(container.discourseApiClient),
            store = container.topicReadPositionStore,
            key = container.sessionStore.snapshot()
                ?.takeIf { snapshot -> snapshot.username.isNotBlank() && snapshot.username != "anonymous" }
                ?.let { snapshot -> TopicReadPositionKey(container.baseUrl, snapshot.username, topicId) },
            scope = scope,
        )

    /**
     * The reader's bookmark on one post, from where it is now to where it is not (and back). The
     * site is told first: the caller only draws the new state once the site has taken it.
     */
    fun setBookmark(
        container: AppContainer,
        postId: Long,
        bookmarked: Boolean,
        onDone: () -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            runCatching { container.discourseApiClient.setPostBookmark(postId, bookmarked) }
                .onSuccess { onDone() }
                .onFailure(onFailed)
        }
    }

    /**
     * The reader's bookmark on the topic itself, the same shape as a post's: the site is told
     * first, and the caller draws the new state only once the site has taken it.
     */
    fun setTopicBookmark(
        container: AppContainer,
        topicId: Long,
        bookmarked: Boolean,
        onDone: () -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            runCatching { container.discourseApiClient.setTopicBookmark(topicId, bookmarked) }
                .onSuccess { onDone() }
                .onFailure(onFailed)
        }
    }

    /**
     * The name of one of the site's categories, from the shared category store the topic list
     * already fills. A category the site did not name (or a store that could not load) answers
     * null, which the reader sees as a subtitle without a category.
     */
    fun categoryName(
        container: AppContainer,
        categoryId: Int,
        onLoaded: (String?) -> Unit,
    ) {
        scope.launch {
            val name = runCatching {
                container.categoryStore.loadIfNeeded().getOrNull()?.get(categoryId)?.name
            }.getOrNull()
            onLoaded(name)
        }
    }

    /** The categories the topic list filters by, in the site's own order. */
    fun categories(
        container: AppContainer,
        onLoaded: (List<io.github.baba983.nativedo.core.model.CategorySummary>) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            runCatching { container.discourseApiClient.fetchCategories() }
                .onSuccess(onLoaded)
                .onFailure(onFailed)
        }
    }

    /** The site's tags, for the topic list's tag filter. */
    fun tags(
        container: AppContainer,
        onLoaded: (List<io.github.baba983.nativedo.core.model.Tag>) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            runCatching { container.discourseApiClient.fetchTags() }
                .onSuccess(onLoaded)
                .onFailure(onFailed)
        }
    }

    /**
     * One page of the topic list's feed for what the reader has chosen: nothing, a category, a
     * tag, or both - one call, because which route that is is the site's business and not the
     * screen's. The whole page is handed back, because its `topic_list.more_topics_url` is what
     * says whether another page follows.
     */
    fun feed(
        container: AppContainer,
        category: io.github.baba983.nativedo.core.model.CategorySummary?,
        tag: io.github.baba983.nativedo.core.model.Tag?,
        page: Int,
        onLoaded: (io.github.baba983.nativedo.core.model.TopicListPage) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            runCatching { container.discourseApiClient.fetchFeed(category, tag, page) }
                .onSuccess(onLoaded)
                .onFailure(onFailed)
        }
    }

    /** The site's answer to a search: its topics, the posts, and the people it matched. */
    fun search(
        container: AppContainer,
        query: String,
        onLoaded: (io.github.baba983.nativedo.core.model.SearchResult) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            runCatching { container.discourseApiClient.search(query) }
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
        topic(container, topicId, null, onLoaded, onFailed)
    }

    /**
     * One topic, read from [postNumber] when it is given: the site answers such a request with the
     * window of posts around that floor instead of the head of the thread, which is what a jump to a
     * floor from a feed row or a search result needs.
     */
    fun topic(
        container: AppContainer,
        topicId: Long,
        postNumber: Int?,
        onLoaded: (TopicDetail) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            runCatching { container.discourseApiClient.fetchTopic(topicId, postNumber) }
                .onSuccess(onLoaded)
                .onFailure(onFailed)
        }
    }

    fun posts(detail: TopicDetail): List<Post> = detail.posts

    /** Starts a topic: the title, the category it goes to, and the body of its first floor. */
    fun newTopic(
        container: AppContainer,
        title: String,
        categoryId: Int,
        raw: String,
        onCreated: (Post) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            runCatching { container.discourseApiClient.createTopic(title, categoryId, raw) }
                .onSuccess(onCreated)
                .onFailure(onFailed)
        }
    }

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

    /**
     * Uploads one file for a reply and reports the site's markdown for it, or the failure the
     * reader has to see. The site decides what an upload looks like in a post, so the markup is
     * built here from the answer's own handle, name, size and dimensions.
     */
    fun uploadReplyMedia(
        container: AppContainer,
        picked: PickedMedia,
        onUploaded: (String) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            runCatching {
                val uploaded = container.discourseApiClient.uploadFile(
                    file = picked.file,
                    mimeType = picked.mimeType,
                    onProgress = { },
                )
                // A site that answers an upload without a resolvable target leaves nothing to point
                // a post at, and that is the one case the reader has to see as a failure.
                val url = absoluteUploadUrl(container.baseUrl, uploaded.url)
                if (uploaded.shortUrl.isNullOrBlank() && url == null) {
                    error("the site answered an upload without a URL")
                }
                replyUploadMarkup(
                    name = uploaded.filename ?: picked.name,
                    shortUrl = uploaded.shortUrl,
                    url = url.orEmpty(),
                    mimeType = picked.mimeType,
                    width = uploaded.width,
                    height = uploaded.height,
                    humanSize = uploaded.humanSize,
                )
            }
                .onSuccess(onUploaded)
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

    /**
     * Sends one poll vote, or its retraction, and reports the poll the site answered with. The
     * answer is the poll's whole new state (counts, and the reader's own selection), and a site that
     * answers without one leaves the caller with what it already had.
     */
    fun votePoll(
        container: AppContainer,
        postId: Long,
        pollName: String,
        selection: PollVoteSelection,
        onUpdated: (PostPoll?) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            runCatching {
                if (selection.retract) {
                    container.discourseApiClient.removePollVote(postId, pollName)
                } else {
                    container.discourseApiClient.votePoll(postId, pollName, selection.digests)
                }
            }
                .onSuccess(onUpdated)
                .onFailure(onFailed)
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

    /**
     * Fetches the covers of the clips the posts carry. A video block draws its cover before anyone
     * presses play; the clip's own bytes are fetched only when play is pressed ([fetchVideo]), so
     * opening a topic does not spend a reader's data on a video they did not ask for.
     */
    fun cacheVideoCovers(
        container: AppContainer,
        videos: List<PostVideo>,
        onReady: () -> Unit,
    ) {
        if (videos.isEmpty()) {
            onReady()
            return
        }
        scope.launch {
            videos.forEach { video ->
                DiscourseImageBridge.cacheVideoCover(container.imageOkHttpClient, video)
            }
            onReady()
        }
    }

    /**
     * Fetches one clip for a play press, and says which of the two players gets it: upstream's own
     * viewer for a file this client now holds, the device's player for a clip it will not fetch or
     * that the site will not hand over.
     *
     * <p>One fetch per clip: a press while the fetch runs - the block draws a cancel button, and a
     * reader may press it again - must not start a second transfer over the same bytes, which the
     * two would write to (and rename into) the same file.</p>
     */
    fun fetchVideo(
        container: AppContainer,
        video: PostVideo,
        onStored: () -> Unit,
        onStream: () -> Unit,
    ) {
        val name = DiscourseImageBridge.videoFileName(video)
        if (!fetching.add(name)) {
            return
        }
        scope.launch {
            try {
                when (DiscourseImageBridge.downloadVideo(container.imageOkHttpClient, video)) {
                    DiscourseImageBridge.VideoDownload.Stored -> onStored()
                    // A cancellation is not a clip the site would not hand over: nothing plays, and
                    // the block's own play button is where the reader left it.
                    DiscourseImageBridge.VideoDownload.Cancelled -> Unit
                    else -> onStream()
                }
            } finally {
                fetching.remove(name)
            }
        }
    }

    /** The clips a fetch is running for right now, by the name the block looks them up by. */
    private val fetching = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>(),
    )

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

    /**
     * Fetches the picture behind each reaction a floor carries and hands upstream's chip an entry it
     * can draw, so a reaction reads as its emoji instead of a bare count. A reaction the site named
     * no icon for, or whose icon does not arrive, is handed a fallback drawn from its own label; see
     * [DiscourseReactionEmoji]. The chip looks the emoticon up in a table nothing here fills.
     */
    fun cacheReactionEmoji(
        container: AppContainer,
        reactions: List<PostReaction>,
        onReady: () -> Unit,
    ) {
        if (reactions.isEmpty()) {
            onReady()
            return
        }
        scope.launch {
            for (reaction in reactions) {
                val emoticon = DiscourseReactions.emoticonOf(reaction.id)
                // The catalog's picture is a path on the site ("/images/emoji/twitter/heart.png");
                // the session's client needs the address it belongs to. A reaction with no icon at
                // all, or one whose fetch fails, keeps its place as a fallback below.
                val file = reaction.iconUrl
                    ?.takeUnless { it.isBlank() }
                    ?.let { url ->
                        runCatching { java.net.URI(container.baseUrl).resolve(url).toString() }.getOrNull()
                    }
                    ?.let { absolute -> DiscourseReactionEmoji.cache(container.imageOkHttpClient, absolute) }
                if (file != null) {
                    DiscourseReactionEmoji.register(emoticon, file)
                } else {
                    DiscourseReactionEmoji.registerFallback(
                        emoticon,
                        io.github.baba983.nativedo.core.model.PostReactionRendering.fallbackLabel(reaction.id),
                    )
                }
            }
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