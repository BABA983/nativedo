package io.github.baba983.nativedo.topic

import io.github.baba983.nativedo.core.di.AppContainer
import io.github.baba983.nativedo.core.media.PickedMedia
import io.github.baba983.nativedo.core.media.absoluteUploadUrl
import io.github.baba983.nativedo.core.media.replyUploadMarkup
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.PostPoll
import io.github.baba983.nativedo.core.model.PostReaction
import io.github.baba983.nativedo.core.model.TopicDetail
import io.github.baba983.nativedo.core.model.TopicPostsPage
import io.github.baba983.nativedo.feature.topic.data.TopicReadPositionKey
import io.github.baba983.nativedo.feature.topic.data.TopicReadReporter
import io.github.baba983.nativedo.feature.topic.data.TopicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.telegram.tgnet.TLRPC

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
    fun readReporter(
        container: AppContainer,
        topicId: Long,
    ): TopicReadReporter =
        TopicReadReporter(
            topicId = topicId,
            repository = TopicRepository(container.discourseApiClient),
            store = container.topicReadPositionStore,
            key =
                container.sessionStore
                    .snapshot()
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
     * The reader's own notification level for one topic (0 mute, 1 regular, 2 tracking, 3
     * watching), the same shape as a bookmark: the site is told first, and the caller moves the
     * level it shows only once the site has taken it.
     */
    fun setTopicNotificationLevel(
        container: AppContainer,
        topicId: Long,
        level: Int,
        onDone: () -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            runCatching { container.discourseApiClient.setTopicNotificationLevel(topicId, level) }
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
            val name =
                runCatching {
                    container.categoryStore
                        .loadIfNeeded()
                        .getOrNull()
                        ?.get(categoryId)
                        ?.name
                }.getOrNull()
            onLoaded(name)
        }
    }

    /** The categories the topic list filters by, in the site's own order, read and written down. */
    fun categories(
        container: AppContainer,
        onLoaded: (List<io.github.baba983.nativedo.core.model.CategorySummary>) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            // Through the store: its answer is what the next launch draws the strip from before
            // the site has been asked.
            container.categoryStore
                .refresh()
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
     * screen's. The whole page is handed back; its own {@code fromCache} says whether the site or
     * the offline cache produced it, and its `topic_list.more_topics_url` says if another follows.
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

    /** One page of the site's own new-topics list (`/new.json`); the topic list's unfiltered mode. */
    fun newTopics(
        container: AppContainer,
        page: Int,
        onLoaded: (io.github.baba983.nativedo.core.model.TopicListPage) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            runCatching { container.discourseApiClient.fetchNewPage(page) }
                .onSuccess(onLoaded)
                .onFailure(onFailed)
        }
    }

    /** One page of the reader's own unread list (`/unread.json`); the topic list's unfiltered mode. */
    fun unreadTopics(
        container: AppContainer,
        page: Int,
        onLoaded: (io.github.baba983.nativedo.core.model.TopicListPage) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            runCatching { container.discourseApiClient.fetchUnreadPage(page) }
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
     * floor from a feed row or a search result needs. The detail's own {@code fromCache} says
     * whether the offline cache produced it.
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

    /**
     * One post by its floor number: how a reply preview resolves a target the loaded window does
     * not carry. It reads the single post rather than a window around it, so the list the reader is
     * standing on is never reset - only that floor's own rows are rebuilt once the target lands.
     */
    fun resolvePost(
        container: AppContainer,
        topicId: Long,
        postNumber: Int,
        onLoaded: (Post) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            runCatching { container.discourseApiClient.fetchPostByNumber(topicId, postNumber) }
                .onSuccess(onLoaded)
                .onFailure(onFailed)
        }
    }

    fun posts(detail: TopicDetail): List<Post> = detail.posts

    /**
     * The direct replies to one post, one page at a time: the replies sheet asks for the page after
     * the greatest floor it already holds, as the contract's `after` cursor. A read-only load, so a
     * failure is a page that did not arrive and the caller keeps what it has.
     */
    fun postReplies(
        container: AppContainer,
        postId: Long,
        afterPostNumber: Int,
        onLoaded: (List<Post>) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            runCatching { container.discourseApiClient.fetchPostReplies(postId, afterPostNumber) }
                .onSuccess(onLoaded)
                .onFailure(onFailed)
        }
    }

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

    /**
     * Fetches the pictures the topic's rows draw, reporting each the moment its bytes land so a
     * picture already on disk is drawn without waiting behind a slow neighbour. A picture the site
     * serves as a preview then an original reports again when the original replaces it.
     */
    fun cachePostPhotos(
        container: AppContainer,
        images: List<PostImage>,
        onImageReady: (PostImage) -> Unit,
    ) {
        if (images.isEmpty()) {
            return
        }
        scope.launch {
            // One fetch per picture, even when two rows name the same one: they share the file.
            for (image in images.distinctBy { it.id }) {
                launch {
                    fetchPhoto(container, image, onImageReady)
                }
            }
        }
    }

    /** One picture again, for a press on the block's own download button (a retry after a failure). */
    fun fetchPostPhoto(
        container: AppContainer,
        image: PostImage,
        onImageReady: (PostImage) -> Unit,
    ) {
        scope.launch {
            fetchPhoto(container, image, onImageReady)
        }
    }

    private suspend fun fetchPhoto(
        container: AppContainer,
        image: PostImage,
        onImageReady: (PostImage) -> Unit,
    ) {
        DiscourseImageBridge.downloadPhoto(container.imageOkHttpClient, image) { onImageReady(image) }
        onImageReady(image)
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
     * One post on its own, with the site's `raw` markdown: what an edit begins from. A topic window
     * carries only cooked bodies, so the post's own read is the one that can open the editor.
     */
    fun fetchPost(
        container: AppContainer,
        postId: Long,
        onLoaded: (Post) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            runCatching { container.discourseApiClient.fetchPost(postId) }
                .onSuccess(onLoaded)
                .onFailure(onFailed)
        }
    }

    /** Edits the reader's own post and reports the site's post, or the failure to show. */
    fun editPost(
        container: AppContainer,
        postId: Long,
        raw: String,
        editReason: String?,
        onEdited: (Post) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            runCatching { container.discourseApiClient.updatePost(postId, raw, editReason) }
                .onSuccess(onEdited)
                .onFailure(onFailed)
        }
    }

    /** Deletes the reader's own post; the site answers no body, so success carries nothing. */
    fun deletePost(
        container: AppContainer,
        postId: Long,
        onDeleted: () -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            runCatching { container.discourseApiClient.deletePost(postId) }
                .onSuccess { onDeleted() }
                .onFailure(onFailed)
        }
    }

    /** Recovers (undeletes) the reader's own post and reports the site's post, or the failure. */
    fun recoverPost(
        container: AppContainer,
        postId: Long,
        onRecovered: (Post) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            runCatching { container.discourseApiClient.recoverPost(postId) }
                .onSuccess(onRecovered)
                .onFailure(onFailed)
        }
    }

    /**
     * Uploads one file for a reply and reports the site's markdown for it, or the failure the
     * reader has to see. The site decides what an upload looks like in a post, so the markup is
     * built here from the answer's own handle, name, size and dimensions.
     *
     * The site is told the name the picker reported, not the cache copy's: the copy carries a
     * timestamp prefix that exists only to keep two same-named picks apart on disk, and the site
     * records the name it is sent (`original_filename`), which is what a reader sees on the upload.
     */
    fun uploadReplyMedia(
        container: AppContainer,
        picked: PickedMedia,
        onUploaded: (String) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            runCatching {
                val uploaded =
                    container.discourseApiClient.uploadFile(
                        file = picked.file,
                        mimeType = picked.mimeType,
                        onProgress = { },
                        filename = picked.name,
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
            }.onSuccess(onUploaded)
                .onFailure(onFailed)
        }
    }

    /** Toggles one of a post's reactions; the caller re-reads the topic afterwards. */
    fun toggleReaction(
        container: AppContainer,
        postId: Long,
        reactionId: String,
        onDone: () -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        scope.launch {
            runCatching { container.discourseApiClient.toggleReaction(postId, reactionId) }
                .onSuccess { onDone() }
                .onFailure(onFailed)
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
            }.onSuccess(onUpdated)
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
    private val fetching =
        java.util.Collections.newSetFromMap(
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
                val file =
                    reaction.iconUrl
                        ?.takeUnless { it.isBlank() }
                        ?.let { url ->
                            runCatching {
                                java.net
                                    .URI(container.baseUrl)
                                    .resolve(url)
                                    .toString()
                            }.getOrNull()
                        }?.let { absolute -> DiscourseReactionEmoji.cache(container.imageOkHttpClient, absolute) }
                if (file != null) {
                    DiscourseReactionEmoji.register(emoticon, file)
                } else {
                    DiscourseReactionEmoji.registerFallback(
                        emoticon,
                        io.github.baba983.nativedo.core.model.PostReactionRendering
                            .fallbackLabel(reaction.id),
                        reaction.id,
                    )
                }
            }
            onReady()
        }
    }

    /**
     * One more slice of a topic's posts, for the pages above the first. [includeSuggested] asks the
     * site for its own ranked suggestions on the slice that reaches the real end of the stream; the
     * answer is the whole page, so the caller takes its floors (and, on the last slice, its
     * candidate) from one model.
     */
    fun topicPosts(
        container: AppContainer,
        topicId: Long,
        postIds: List<Long>,
        includeSuggested: Boolean,
        onLoaded: (TopicPostsPage) -> Unit,
        onFailed: (Throwable) -> Unit,
    ) {
        if (postIds.isEmpty()) {
            onLoaded(TopicPostsPage(posts = emptyList()))
            return
        }
        scope.launch {
            runCatching { container.discourseApiClient.fetchTopicPosts(topicId, postIds, includeSuggested) }
                .onSuccess(onLoaded)
                .onFailure(onFailed)
        }
    }
}
