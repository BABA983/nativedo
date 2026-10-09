package io.github.baba983.nativedo.core.network

import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.PostPoll
import io.github.baba983.nativedo.core.model.PostReactionUsersGroup
import io.github.baba983.nativedo.core.model.Bookmark
import io.github.baba983.nativedo.core.model.CategorySummary
import io.github.baba983.nativedo.core.model.ChatChannel
import io.github.baba983.nativedo.core.model.ChatChannelPage
import io.github.baba983.nativedo.core.model.ChatMembership
import io.github.baba983.nativedo.core.model.ChatMessagePage
import io.github.baba983.nativedo.core.model.CreatedTopic
import io.github.baba983.nativedo.core.model.Tag
import io.github.baba983.nativedo.core.model.SearchResult
import io.github.baba983.nativedo.core.model.MentionSearchResult
import io.github.baba983.nativedo.core.model.NotificationItem
import io.github.baba983.nativedo.core.model.NotificationPage
import io.github.baba983.nativedo.core.model.TopicDetail
import io.github.baba983.nativedo.core.model.TopicListPage
import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.core.model.UserProfile
import io.github.baba983.nativedo.core.model.UserPreferences
import io.github.baba983.nativedo.core.model.UserSummary
import io.github.baba983.nativedo.core.model.UserBadge
import io.github.baba983.nativedo.core.model.UploadedFile
import io.github.baba983.nativedo.core.model.UserActionPage
import io.github.baba983.nativedo.core.session.LoginRequiredException
import io.github.baba983.nativedo.core.session.SessionBootstrapClient
import io.github.baba983.nativedo.core.session.SessionExpiredReason
import io.github.baba983.nativedo.core.session.SessionLoggedOutException
import io.github.baba983.nativedo.core.session.SessionRequestAuth
import io.github.baba983.nativedo.core.session.SessionRequestContext
import io.github.baba983.nativedo.core.session.SessionUserSummary
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import org.jsoup.Jsoup
import retrofit2.HttpException

/** The `archetype` a private message is created with; the site reads it to route the write. */
private const val PRIVATE_MESSAGE_ARCHETYPE = "private_message"

/** How many public channels one `GET /chat/api/channels.json` page asks for; the site's own default. */
private const val CHAT_CHANNELS_PAGE_SIZE = 25

/** How many messages one `GET /chat/api/channels/{id}/messages.json` window asks for. */
private const val CHAT_MESSAGES_PAGE_SIZE = 50

/** The message window direction before a target (`Chat::MessagesQuery::VALID_DIRECTIONS`). */
const val CHAT_DIRECTION_PAST = "past"

/** The message window direction after a target. */
const val CHAT_DIRECTION_FUTURE = "future"

class DiscourseApiClient(
    private val service: DiscourseApiService,
    /** The site's emoji table, when one is kept; see [emojiGroupsForDecoration]. */
    private val emojiCatalogStore: io.github.baba983.nativedo.core.storage.EmojiCatalogStore? = null,
) : SessionBootstrapClient {
    private var sessionContextProvider: () -> SessionRequestContext = {
        SessionRequestContext(
            generation = 0,
            auth = SessionRequestAuth.Authenticated,
        )
    }
    private var onSessionInvalidated: suspend (SessionExpiredReason, Int) -> Unit = { _, _ -> }
    private var onLoginRequired: suspend () -> Unit = {}
    private var hasAuthenticatedCookies: () -> Boolean = { true }
    private var readCache: io.github.baba983.nativedo.core.storage.DiscourseReadCache? = null
    private var readCacheScope: () -> ReadCacheScope? = { null }

    /**
     * Injects the session boundary this client reports to: every request
     * resolves its [SessionRequestContext] up front, and a proven logout is
     * reported back with the generation that started the request so a stale
     * response cannot invalidate a newer WebView login. [hasAuthenticatedCookies]
     * reads the shared jar, so a logout performed in the browser is noticed by
     * the first request that would have needed the session.
     */
    fun setSessionBoundary(
        contextProvider: () -> SessionRequestContext,
        onSessionInvalidated: suspend (SessionExpiredReason, Int) -> Unit,
        onLoginRequired: suspend () -> Unit,
        hasAuthenticatedCookies: () -> Boolean = { true },
    ) {
        sessionContextProvider = contextProvider
        this.onSessionInvalidated = onSessionInvalidated
        this.onLoginRequired = onLoginRequired
        this.hasAuthenticatedCookies = hasAuthenticatedCookies
    }

    /**
     * Injects the offline cache and the reader the reads belong to. [scope] answers the site and
     * authenticated username whose cache entries this client may read and write, or null when there
     * is no authenticated reader - an anonymous session neither fills nor reads the cache, so no
     * reader is ever served another's payload. The client combines it with the live request context
     * (an authenticated or restoring session that still carries its cookies) before a read counts as
     * cached; see [currentReadIdentity].
     */
    fun setReadCache(
        cache: io.github.baba983.nativedo.core.storage.DiscourseReadCache,
        scope: () -> ReadCacheScope?,
    ) {
        readCache = cache
        this.readCacheScope = scope
    }

    override suspend fun fetchBootstrap(): ParsedBootstrap =
        runWithSessionBoundary {
            BootstrapHtmlParser.parse(fetchBootstrapHtmlUnbidden())
        }

    /**
     * The same homepage `fetchBootstrap` reads, returned whole: the message bus' own positions
     * (`topicTrackingStateMeta`, `currentUser.notification_channel_position`) live in the page's
     * `#data-preloaded`, so a caller that wants to subscribe where Discourse's web client does
     * needs the HTML, not just the username.
     */
    suspend fun fetchBootstrapHtml(): String = runWithSessionBoundary { fetchBootstrapHtmlUnbidden() }

    private suspend fun fetchBootstrapHtmlUnbidden(): String =
        service.bootstrap().use { responseBody -> responseBody.string() }

    override suspend fun fetchCurrentUser(): SessionUserSummary? =
        runWithSessionBoundary {
            try {
                val currentUser = service.currentSession().currentUser
                if (currentUser == JsonNull) throw SessionLoggedOutException()
                NetworkJson.instance.decodeFromJsonElement<CurrentUserDto>(currentUser)
                    .toSessionUserSummary() ?: throw SessionRecoveryRequiredException()
            } catch (error: SerializationException) {
                throw SessionRecoveryRequiredException(cause = error)
            }
        }

    override suspend fun fetchCurrentUsername(): String? =
        fetchCurrentUser()?.username

    suspend fun fetchCsrfToken(): String = runWithSessionBoundary {
        service.csrf().csrf
    }

    /**
     * Reports read progress to Discourse (`topics/timings`), advancing the
     * server-side `last_read_post_number` the same way the web client does.
     * [postTimings] maps post_number to dwell milliseconds.
     */
    suspend fun markTopicRead(topicId: Long, topicTimeMillis: Long, postTimings: Map<Int, Int>) =
        runWithSessionBoundary(requiresAuthentication = true) {
            val csrfToken = service.csrf().csrf
            service.postTimings(
                topicId = topicId,
                topicTime = topicTimeMillis,
                timings = postTimings.mapKeys { (postNumber, _) -> "timings[$postNumber]" }
                    .mapValues { (_, millis) -> millis.toString() },
                csrfToken = csrfToken,
            )
        }

    suspend fun fetchLatestPage(page: Int): TopicListPage =
        runWithSessionBoundary { service.latest(page).toModel() }

    /**
     * The site's own new-topics list (`/new.json`). It is the reader's own list by the site's rule,
     * so it carries the session and is cached like the feeds: an outage after a read draws that read.
     */
    suspend fun fetchNewPage(page: Int): TopicListPage =
        runWithSessionBoundary(requiresAuthentication = true) {
            cachedRead(
                keySuffix = "new|$page",
                serializer = TopicListResponseDto.serializer(),
                fetch = { service.newTopics(page) },
                map = { response -> response.toModel() },
                markFromCache = { cached -> cached.copy(fromCache = true) },
            )
        }

    /** The reader's own unread feed (`/unread.json`); the same envelope `latest.json` answers with. */
    suspend fun fetchUnreadPage(page: Int): TopicListPage =
        runWithSessionBoundary(requiresAuthentication = true) {
            cachedRead(
                keySuffix = "unread|$page",
                serializer = TopicListResponseDto.serializer(),
                fetch = { service.unread(page) },
                map = { response -> response.toModel() },
                markFromCache = { cached -> cached.copy(fromCache = true) },
            )
        }

    suspend fun fetchTopPage(page: Int): TopicListPage =
        runWithSessionBoundary { service.topTopics(page).toModel() }

    suspend fun search(query: String, page: Int = 0, typeFilter: String? = null): SearchResult =
        runWithSessionBoundary {
            service.search(query = query, page = page, typeFilter = typeFilter).toModel()
        }

    suspend fun searchMentionUsers(term: String, topicId: Long?): MentionSearchResult =
        runWithSessionBoundary {
            service.searchMentionUsers(term = term, topicId = topicId).toModel()
        }

    /**
     * The reader's own bookmarks, one page at a time. The list is the reader's own by the site's
     * rule, so the username is the session's - a bookmark list is not something to look up.
     */
    suspend fun fetchBookmarks(username: String, page: Int = 0, limit: Int = 20): List<Bookmark> =
        runWithSessionBoundary(requiresAuthentication = true) {
            service.bookmarks(username = username, page = page, limit = limit).toModel()
        }

    /**
     * The reader's private-message inbox, one page at a time. Like the bookmark list it is the
     * reader's own by the site's rule, so the username is the session's - a private message is not
     * something to look up. The answer is a standard `topic_list` envelope, so a page maps to
     * [TopicListPage] the same way a feed does - including its `more_topics_url`, which is how the
     * caller knows another page follows.
     */
    suspend fun fetchPrivateMessages(username: String, page: Int = 0): TopicListPage =
        runWithSessionBoundary(requiresAuthentication = true) {
            service.privateMessages(username = username, page = page).toModel()
        }

    /** `GET /topics/private-messages-sent/{username}.json`: the messages the reader sent. */
    suspend fun fetchPrivateMessagesSent(username: String, page: Int = 0): TopicListPage =
        runWithSessionBoundary(requiresAuthentication = true) {
            service.privateMessagesSent(username = username, page = page).toModel()
        }

    /** `GET /topics/private-messages-unread/{username}.json`: the reader's unread messages. */
    suspend fun fetchPrivateMessagesUnread(username: String, page: Int = 0): TopicListPage =
        runWithSessionBoundary(requiresAuthentication = true) {
            service.privateMessagesUnread(username = username, page = page).toModel()
        }

    // -----------------------------------------------------------------------------------------
    // Discourse chat (public channels)
    // -----------------------------------------------------------------------------------------

    /**
     * The public (category) channels the reader can see, one page at a time. It is the reader's own
     * list by the site's rule - the site answers it for the session and marks each channel with the
     * reader's own membership - so it carries the session. Direct-message channels are not here.
     */
    suspend fun fetchChatChannels(offset: Int, limit: Int = CHAT_CHANNELS_PAGE_SIZE): ChatChannelPage =
        runWithSessionBoundary(requiresAuthentication = true) {
            service.chatChannels(limit = limit, offset = offset).toModel()
        }

    /**
     * One channel, with the reader's own membership and the live-update seeds. A site that answers
     * no channel at all left nothing to open; that is a failure, not an empty channel.
     */
    suspend fun fetchChatChannel(channelId: Long): ChatChannel =
        runWithSessionBoundary(requiresAuthentication = true) {
            service.chatChannel(channelId).toModel()
                ?: throw IOException("GET /chat/api/channels/$channelId.json answered no channel")
        }

    /**
     * One window of a channel's messages. With no [targetMessageId] the site answers the newest
     * window; with one and a [direction] of [CHAT_DIRECTION_PAST] or
     * [CHAT_DIRECTION_FUTURE] it answers a window before or after that message. [fetchFromLastRead]
     * asks the site to anchor on the reader's own last-read position.
     */
    suspend fun fetchChatMessages(
        channelId: Long,
        pageSize: Int = CHAT_MESSAGES_PAGE_SIZE,
        targetMessageId: Long? = null,
        direction: String? = null,
        fetchFromLastRead: Boolean? = null,
    ): ChatMessagePage =
        runWithSessionBoundary(requiresAuthentication = true) {
            service.chatChannelMessages(
                channelId = channelId,
                pageSize = pageSize,
                targetMessageId = targetMessageId,
                direction = direction,
                fetchFromLastRead = fetchFromLastRead,
            ).toModel()
        }

    /**
     * Joins the channel. The site is the only one that decides whether this reader may join; it
     * refuses the rest with the usual `errors` array through [readableRefusal], and an answer with
     * no membership left nothing joined. Requires the session's CSRF token.
     */
    suspend fun joinChatChannel(channelId: Long): ChatMembership =
        runWithSessionBoundary(requiresAuthentication = true) {
            val csrfToken = chatCsrfToken()
            readableRefusal {
                service.joinChatChannel(channelId, csrfToken).membership?.toModel()
                    ?: throw IOException(
                        "POST /chat/api/channels/$channelId/memberships/me.json answered no membership",
                    )
            }
        }

    /**
     * Sends one message and answers the id the site accepted. The acknowledgement is **not** the
     * cooked message, so the caller reads the id back with [fetchChatMessages] (a window at that id)
     * rather than drawing the write itself; a readback that fails keeps the accepted id, so the
     * message is never silently resent. Requires the session's CSRF token.
     */
    suspend fun createChatMessage(
        channelId: Long,
        message: String,
        inReplyToId: Long? = null,
        stagedId: String? = null,
    ): Long = runWithSessionBoundary(requiresAuthentication = true) {
        val csrfToken = chatCsrfToken()
        readableRefusal {
            val answer = service.createChatMessage(
                channelId = channelId,
                message = message,
                inReplyToId = inReplyToId,
                stagedId = stagedId,
                csrfToken = csrfToken,
            )
            if (answer.success != "OK") {
                throw IOException("POST /chat/$channelId.json answered ${answer.success}")
            }
            answer.messageId
                ?: throw IOException("POST /chat/$channelId.json answered no message_id")
        }
    }

    /**
     * Advances the reader's own last-read position in a channel to [messageId]. Requires the
     * session's CSRF token.
     */
    suspend fun markChatChannelRead(channelId: Long, messageId: Long) =
        runWithSessionBoundary(requiresAuthentication = true) {
            val csrfToken = chatCsrfToken()
            readableRefusal {
                val answer = service.markChatChannelRead(channelId, messageId, csrfToken)
                if (answer.success != "OK") {
                    throw IOException("PUT /chat/api/channels/$channelId/read.json answered ${answer.success}")
                }
            }
        }

    private suspend fun chatCsrfToken(): String {
        val owner = sessionContextProvider()
        if (!owner.canUseAuthenticatedSession) throw LoginRequiredException()
        val token = service.csrf().csrf
        if (sessionContextProvider() != owner || !hasAuthenticatedCookies()) {
            throw CancellationException("Session changed while acquiring chat CSRF")
        }
        return token
    }

    /**
     * Starts a private message: the same `POST /posts.json` route a new topic takes, with
     * `archetype=private_message` and `target_recipients` instead of a category. The site decides
     * whether the reader may send to those people and refuses the rest through the usual `errors`
     * array (surfaced by [readableRefusal]); the client never invents a permission answer. The
     * answer carries the created topic, which is where the writer lands.
     */
    suspend fun createPrivateMessage(
        title: String,
        recipients: String,
        raw: String,
    ): CreatedTopic = runWithSessionBoundary(requiresAuthentication = true) {
        val csrfToken = service.csrf().csrf
        readableRefusal {
            service.createPrivateMessage(
                title = title,
                raw = raw,
                archetype = PRIVATE_MESSAGE_ARCHETYPE,
                targetRecipients = recipients,
                csrfToken = csrfToken,
            ).toCreatedTopic()
        }
    }

    /** The categories a topic list filters by, as `/categories.json` answers them. */
    suspend fun fetchCategories(): List<CategorySummary> =
        runWithSessionBoundary { service.categories().toModel() }

    /**
     * A feed restricted by what the reader has chosen, as the site's own routes name it: the plain
     * list, one category, one tag, or both. The two filters are separate routes on the site - a
     * category has its own (`/c/...`) and a tag its own (`/tag/...`), and together they are the
     * combined one under `tags/c/`.
     */
    suspend fun fetchFeed(
        category: CategorySummary?,
        tag: Tag?,
        page: Int = 0,
    ): TopicListPage = runWithSessionBoundary {
        cachedRead(
            keySuffix = "feed|${category?.id ?: -1}|${category?.slug.orEmpty()}|" +
                "${tag?.id ?: -1}|${tag?.slug.orEmpty()}|$page",
            serializer = TopicListResponseDto.serializer(),
            fetch = {
                when {
                    category != null && tag != null -> service.categoryTagTopics(
                        categorySlug = category.slug,
                        categoryId = category.id,
                        tagSlug = tag.slug,
                        tagId = tag.id,
                        page = page,
                    )
                    category != null -> service.categoryLatest(slug = category.slug, id = category.id, page = page)
                    tag != null -> service.tagTopics(slug = tag.slug, page = page)
                    else -> service.latest(page)
                }
            },
            map = { response -> response.toModel() },
            markFromCache = { cached -> cached.copy(fromCache = true) },
        )
    }

    /** The site's tags, for the topic list's tag filter. */
    suspend fun fetchTags(): List<Tag> = runWithSessionBoundary { service.tags().toModel() }

    suspend fun fetchTopic(topicId: Long, postNumber: Int? = null): TopicDetail =
        runWithSessionBoundary {
            cachedRead(
                keySuffix = "topic|$topicId|${postNumber ?: 0}",
                serializer = TopicResponseDto.serializer(),
                fetch = {
                    if (postNumber == null) service.topic(topicId) else service.topicAtPost(topicId, postNumber)
                },
                map = { response ->
                    // The emoji table is decoration, and asking the site for it up front is what
                    // used to turn a decorative failure into a topic that never loaded.
                    val emojiGroups = emojiGroupsForDecoration()
                    response.toModel(
                        emojiCatalog = emojiGroups.toEmojiCatalog(),
                        emojiCategories = emojiGroups.toEmojiCategories(),
                    )
                },
                markFromCache = { cached -> cached.copy(fromCache = true) },
            )
        }

    suspend fun fetchTopicPosts(topicId: Long, postIds: List<Long>): List<Post> =
        runWithSessionBoundary {
            cachedRead(
                keySuffix = "topicposts|$topicId|${postIds.joinToString(",")}",
                serializer = TopicPostsResponseDto.serializer(),
                fetch = { service.topicPosts(id = topicId, postIds = postIds) },
                map = { response -> response.toModel(emojiCatalog = emojiGroupsForDecoration().toEmojiCatalog()) },
                markFromCache = { cached -> cached.map { post -> post.copy(fromCache = true) } },
            )
        }

    suspend fun fetchPostByNumber(topicId: Long, postNumber: Int): Post =
        runWithSessionBoundary {
            val response = service.postByNumber(
                topicId = topicId,
                postNumber = postNumber,
            )
            response.toModel(emojiCatalog = emojiGroupsForDecoration().toEmojiCatalog())
        }

    /**
     * The site's emoji table for a screen that only needs it to draw with.
     *
     * A warm cache answers without a request at all; a cold one asks once and keeps the answer, so
     * repeated topic reads do not become a request storm. This call is deliberately not routed
     * through [runWithSessionBoundary]: a table that failed to arrive is not evidence about the
     * session, and letting it be read as one is the defect this exists to remove. The request the
     * reader actually asked for - the topic - carries its own session boundary and error
     * classification, and it is the one that reports a challenge, a logout or an outage.
     */
    private suspend fun emojiGroupsForDecoration(): EmojiGroupsDto {
        val store = emojiCatalogStore ?: return emptyMap()
        store.groups.value.takeIf { groups -> groups.isNotEmpty() }?.let { cached -> return cached }
        return try {
            service.emojis().also(store::save)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            emptyMap()
        }
    }

    /**
     * The site's emoji table for a screen that offers it: the kept one when it is warm, otherwise
     * one request, which is then kept. A picker needs this on a screen that reads no topic (a new
     * topic has no floor), where nothing else would ever fill the store.
     *
     * Unlike [emojiGroupsForDecoration] this reports a failure instead of answering an empty table,
     * because a picker has to tell "the site offers no emoji" apart from "the table could not be
     * read". Like it, the read is not routed through [runWithSessionBoundary]: a decorative table
     * that failed to arrive is not evidence about the session, and the request the reader actually
     * asked for carries its own boundary and error classification.
     */
    suspend fun fetchEmojiCatalog(): EmojiGroupsDto {
        val store = emojiCatalogStore ?: return emptyMap()
        store.groups.value.takeIf { groups -> groups.isNotEmpty() }?.let { cached -> return cached }
        return service.emojis().also(store::save)
    }

    suspend fun fetchPostReplies(postId: Long, afterPostNumber: Int = 1): List<Post> =
        runWithSessionBoundary {
            service.postReplies(postId = postId, afterPostNumber = afterPostNumber).map { post -> post.toModel() }
        }

    suspend fun createPost(topicId: Long, raw: String, replyToPostNumber: Int?): Post =
        runWithSessionBoundary(requiresAuthentication = true) {
            val csrfToken = service.csrf().csrf
            readableRefusal {
                service.createPost(
                    topicId = topicId,
                    raw = raw,
                    replyToPostNumber = replyToPostNumber,
                    csrfToken = csrfToken,
                ).toModel()
            }
        }

    /** A new topic: the site creates it, and answers with the topic's first floor. */
    suspend fun createTopic(title: String, categoryId: Int, raw: String): Post =
        runWithSessionBoundary(requiresAuthentication = true) {
            val csrfToken = service.csrf().csrf
            readableRefusal {
                service.createTopic(
                    title = title,
                    categoryId = categoryId,
                    raw = raw,
                    csrfToken = csrfToken,
                ).toModel()
            }
        }

    /**
     * One post on its own (`GET /posts/{id}.json`). Unlike a topic window this answer carries
     * `raw` - the markdown the site stores, which is what an edit begins from - and the post's own
     * permissions.
     */
    suspend fun fetchPost(postId: Long): Post =
        runWithSessionBoundary {
            service.post(postId).toModel(emojiCatalog = emojiGroupsForDecoration().toEmojiCatalog())
        }

    /**
     * Edits one of the reader's own posts with the markdown the site stores. Discourse answers
     * `{"post": {...}}`, and that post - its cooked body and its refreshed permissions - is what the
     * caller rebuilds the row from; nothing here derives cooked from raw.
     */
    suspend fun updatePost(postId: Long, raw: String, editReason: String? = null): Post =
        runWithSessionBoundary(requiresAuthentication = true) {
            val csrfToken = service.csrf().csrf
            readableRefusal {
                service.updatePost(
                    id = postId,
                    raw = raw,
                    editReason = editReason,
                    csrfToken = csrfToken,
                ).post.toModel(emojiCatalog = emojiGroupsForDecoration().toEmojiCatalog())
            }
        }

    /** Deletes one of the reader's own posts. Discourse answers 204 with no body. */
    suspend fun deletePost(postId: Long) =
        runWithSessionBoundary(requiresAuthentication = true) {
            val csrfToken = service.csrf().csrf
            readableRefusal {
                service.deletePost(postId, csrfToken)
            }
        }

    /** Recovers (undeletes) a post. The answer is the recovered post, a bare post like `show`. */
    suspend fun recoverPost(postId: Long): Post =
        runWithSessionBoundary(requiresAuthentication = true) {
            val csrfToken = service.csrf().csrf
            readableRefusal {
                service.recoverPost(postId, csrfToken)
                    .toModel(emojiCatalog = emojiGroupsForDecoration().toEmojiCatalog())
            }
        }

    /**
     * A write the site refused. Discourse answers one with `{"errors":["..."]}` and a status, and the
     * message in it is what a reader can act on ("Title is too short") where the transport's own
     * status line ("HTTP 422 Unprocessable Entity") is not; anything else is passed on unchanged.
     */
    private inline fun <T> readableRefusal(write: () -> T): T = try {
        write()
    } catch (refused: retrofit2.HttpException) {
        // The session boundary still needs the original body to classify a challenge.
        val body = runCatching { refused.response()?.errorBody()?.source()?.peek()?.readUtf8() }.getOrNull()
        val message = body
            ?.let { text -> runCatching { NetworkJson.instance.decodeFromString<DiscourseErrorsDto>(text) }.getOrNull() }
            ?.errors
            ?.firstOrNull()
        throw if (message.isNullOrBlank()) refused else java.io.IOException(message, refused)
    }

    /**
     * Uploads a file to the site as the web composer does (`POST /uploads.json`,
     * `upload_type=composer`, synchronous) and reports the file's own progress as its bytes leave
     * the device. The URL comes back as the site wrote it - usually absolute, a path when the site
     * answered one - and the caller resolves it.
     */
    suspend fun uploadFile(
        file: File,
        mimeType: String?,
        onProgress: (Float) -> Unit,
    ): UploadedFile = runWithSessionBoundary(requiresAuthentication = true) {
        val csrfToken = service.csrf().csrf
        val response = service.upload(
            file = MultipartBody.Part.createFormData(
                "file",
                file.name,
                ProgressRequestBody(file, mimeType, onProgress),
            ),
            uploadType = "composer".toUploadField(),
            synchronous = "true".toUploadField(),
            csrfToken = csrfToken,
        )
        UploadedFile(
            id = response.id,
            url = response.url,
            filename = response.originalFilename,
            width = response.width ?: 0,
            height = response.height ?: 0,
            shortUrl = response.shortUrl,
            humanSize = response.humanFilesize,
        )
    }

    private fun String.toUploadField(): RequestBody =
        toRequestBody("text/plain; charset=utf-8".toMediaType())

    suspend fun fetchReactionUsers(postId: Long): List<PostReactionUsersGroup> =
        runWithSessionBoundary {
            service.reactionUsers(postId).toModel()
        }

    suspend fun fetchPollVoters(
        postId: Long,
        pollName: String,
        optionId: String? = null,
        page: Int = 1,
        limit: Int = 50,
    ): List<PostReactionUsersGroup> =
        runWithSessionBoundary {
            service.pollVoters(
                postId = postId,
                pollName = pollName,
                optionId = optionId,
                page = page,
                limit = limit,
            ).toModel()
        }

    suspend fun toggleReaction(postId: Long, reactionId: String) =
        runWithSessionBoundary(requiresAuthentication = true) {
            val csrfToken = service.csrf().csrf
            service.toggleReaction(
                postId = postId,
                reactionId = reactionId,
                csrfToken = csrfToken,
            ).close()
        }

    suspend fun setPostBookmark(postId: Long, bookmarked: Boolean) =
        runWithSessionBoundary(requiresAuthentication = true) {
            val csrfToken = service.csrf().csrf
            if (bookmarked) {
                service.createBookmark(
                    bookmarkableId = postId,
                    bookmarkableType = "Post",
                    autoDeletePreference = 0,
                    csrfToken = csrfToken,
                ).close()
            } else {
                service.deletePostBookmark(
                    postId = postId,
                    csrfToken = csrfToken,
                ).close()
            }
        }

    /**
     * The reader's bookmark on the topic itself. Discourse creates it through the topic's own
     * `bookmark` route and takes it back through `remove_bookmarks` (the topic-level undo), so a
     * client that only knows the topic id needs no bookmark id for either.
     */
    suspend fun setTopicBookmark(topicId: Long, bookmarked: Boolean) =
        runWithSessionBoundary(requiresAuthentication = true) {
            val csrfToken = service.csrf().csrf
            if (bookmarked) {
                service.bookmarkTopic(topicId, csrfToken).close()
            } else {
                service.removeTopicBookmarks(topicId, csrfToken).close()
            }
        }

    /** Sends the reader's selection; the answer carries the poll's new state. */
    suspend fun votePoll(postId: Long, pollName: String, optionIds: List<String>): PostPoll? =
        runWithSessionBoundary(requiresAuthentication = true) {
            val csrfToken = service.csrf().csrf
            val response = service.votePoll(
                postId = postId,
                pollName = pollName,
                optionIds = optionIds,
                csrfToken = csrfToken,
            )
            // The answer's `vote` is the site's own echo of the reader's selection, and it is the
            // only place a regular poll's answer carries it: the option serializer emits no
            // `chosen` for a single-choice poll (a multiple one merges it per option, the same
            // selection again). Without this the rebuilt floor draws the new counts as if the
            // reader had not voted.
            response.poll?.toModel(votes = response.vote)
        }

    /** Retracts the reader's vote; the answer carries the poll's new state. */
    suspend fun removePollVote(postId: Long, pollName: String): PostPoll? =
        runWithSessionBoundary(requiresAuthentication = true) {
            val csrfToken = service.csrf().csrf
            service.removePollVote(
                postId = postId,
                pollName = pollName,
                csrfToken = csrfToken,
            ).poll?.toModel()
        }

    suspend fun fetchProfile(username: String): UserProfile =
        runWithSessionBoundary {
            service.profile(username).toModel()
        }

    /**
     * The reader's own preferences, from the `user_option` half of `GET /u/{username}.json`. The
     * site fills that object only for the signed-in reader, so an answer without it is not "no
     * preferences" - it is an answer that cannot be drawn, and the screen says so instead.
     */
    suspend fun fetchUserPreferences(username: String): UserPreferences =
        runWithSessionBoundary {
            service.userPreferences(username).toModel()
                ?: throw IOException("GET /u/$username.json answered no user_option")
        }

    /**
     * Updates the reader's own preferences with the site's own encoding: each option is a
     * top-level form field, a boolean the string `"true"`/`"false"`, and the write carries the
     * session's CSRF token. A refusal keeps the site's message through [readableRefusal].
     */
    suspend fun updateUserPreferences(username: String, fields: Map<String, String>) =
        runWithSessionBoundary(requiresAuthentication = true) {
            val csrfToken = service.csrf().csrf
            readableRefusal {
                val answer = service.updateUserPreferences(
                    username = username,
                    fields = fields,
                    csrfToken = csrfToken,
                )
                if (answer.success != "OK") {
                    throw IOException("PUT /u/$username.json answered ${answer.success}")
                }
            }
        }

    /** The stats block of `/u/{username}/summary.json`; a hidden summary fails, the screen copes. */
    suspend fun fetchUserSummary(username: String): UserSummary =
        runWithSessionBoundary {
            service.userSummary(username).toModel()
        }

    /** The badges granted to the reader, `/user-badges/{username}.json`. */
    suspend fun fetchUserBadges(username: String): List<UserBadge> =
        runWithSessionBoundary {
            service.userBadges(username).toModel()
        }

    /** Follows or unfollows with the discourse-follow plugin's own routes. */
    suspend fun setUserFollow(username: String, follow: Boolean) =
        runWithSessionBoundary(requiresAuthentication = true) {
            val csrfToken = service.csrf().csrf
            if (follow) {
                service.follow(username = username, csrfToken = csrfToken).close()
            } else {
                service.unfollow(username = username, csrfToken = csrfToken).close()
            }
        }

    /** `normal` / `mute` / `ignore`, as `PUT /u/{username}/notification_level.json` names them. */
    suspend fun setUserNotificationLevel(username: String, level: String) =
        runWithSessionBoundary(requiresAuthentication = true) {
            val csrfToken = service.csrf().csrf
            service.setNotificationLevel(username = username, level = level, csrfToken = csrfToken).close()
        }

    suspend fun fetchUserActions(
        username: String,
        filter: String,
        offset: Int,
        limit: Int,
    ): UserActionPage =
        runWithSessionBoundary {
            service.userActions(
                username = username,
                filter = filter,
                offset = offset,
                limit = limit,
            ).toModel()
        }

    suspend fun fetchNotifications(offset: Int, limit: Int): NotificationPage =
        runWithSessionBoundary {
            service.notifications(offset = offset, limit = limit).toModel()
        }

    /**
     * Marks the reader's own notifications read: one notification when [id] is given, every one of
     * them when it is null (the site's `notifications#mark_read` takes an optional `id`). A refused
     * write keeps the site's own message through [readableRefusal].
     */
    suspend fun markNotificationsRead(id: Long?) =
        runWithSessionBoundary(requiresAuthentication = true) {
            val csrfToken = service.csrf().csrf
            readableRefusal {
                service.markNotificationsRead(id = id, csrfToken = csrfToken).close()
            }
        }

    /**
     * Sets the reader's own notification level for one topic: `0` mute, `1` regular, `2` tracking,
     * `3` watching (the web client's topic-tracking control). A refusal keeps the site's message.
     */
    suspend fun setTopicNotificationLevel(topicId: Long, level: Int) =
        runWithSessionBoundary(requiresAuthentication = true) {
            val csrfToken = service.csrf().csrf
            readableRefusal {
                service.setTopicNotificationLevel(topicId = topicId, level = level, csrfToken = csrfToken).close()
            }
        }

    /**
     * A read served from the site when it answers, and from the persisted offline cache when it
     * does not. The cache entry holds the site's own DTO under the reader's stable identity - the
     * site, the authenticated username and the route/query - so it survives a restart and can never
     * be handed to another account. The session generation is deliberately not part of the key: a
     * read that is still the same reader's is the same read across a re-login.
     *
     * <p>The generation is instead the per-flight fence. It is captured before [fetch] and checked
     * again before the answer is cached, before a live answer is delivered and before a cached one is
     * delivered: a read whose session moved on mid-flight must not write, must not be handed back as
     * live, and must not be answered from the cache.</p>
     *
     * <p>Only a true transport outage falls back. A challenge, a refusal, a lost session, a session
     * that changed mid-flight and a decode failure are all rethrown: the site was reached (or the
     * answer is about the session), and stale content would say the wrong thing about it. The catch
     * wraps [fetch] alone - a failure while [map] runs (the decorative emoji read, a decode) is the
     * mapper's, not the transport's, so it can never be answered with cached rows.</p>
     */
    private suspend fun <D, T> cachedRead(
        keySuffix: String,
        serializer: KSerializer<D>,
        fetch: suspend () -> D,
        map: suspend (D) -> T,
        markFromCache: (T) -> T,
    ): T {
        val cache = readCache
        // The reader and the session generation this read runs under, captured before the fetch.
        val identity = if (cache != null) currentReadIdentity() else null
        // Persisted identity only: site + authenticated username + route/query, never the generation.
        val key = identity?.let { "${it.site}|${it.username}|$keySuffix" }
        val dto = try {
            fetch()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            // A security error reached the site (or is about the session): never answer it with stale
            // rows. Only a true transport outage, for a known reader, may fall back.
            if (cache == null || key == null || identity == null || !isTransportOutage(error)) throw error
            // A session that moved on while this read was out must not be answered with the previous
            // generation's rows: the abort keeps the outage as the failure instead.
            if (currentReadIdentity() != identity) throw error
            // The site could not be reached: the last answer for this exact read is the best this
            // client has. A cache miss - or an undecodable entry - keeps the outage as the failure.
            val cached = cache.read(key) ?: throw error
            val decoded = try {
                NetworkJson.instance.decodeFromString(serializer, cached)
            } catch (decodeError: Throwable) {
                throw error
            }
            val value = map(decoded)
            // Recheck after the map: the fence covers the fallback delivery too.
            if (currentReadIdentity() != identity) throw error
            return markFromCache(value)
        }
        // The site answered. Cache it only while the session this read ran under is still current.
        if (cache != null && key != null) {
            runCatching {
                cache.write(key, NetworkJson.instance.encodeToString(serializer, dto)) {
                    currentReadIdentity() == identity
                }
            }
        }
        val value = map(dto)
        // Recheck after the map before delivery: a session that moved on while the read was out must
        // not be handed this answer.
        if (identity != null && currentReadIdentity() != identity) {
            throw SessionChangedException()
        }
        return value
    }

    /**
     * The reader a cached read belongs to: the site, the stored authenticated username and the
     * session generation captured at the request. Null when there is no authenticated reader - the
     * request context is anonymous or expired, the shared jar carries no authenticated cookies, or no
     * identity is stored - so an anonymous install neither fills nor falls back to a cache. A mere
     * persisted username while the session is Expired or Anonymous is not enough.
     */
    private fun currentReadIdentity(): ReadCacheIdentity? {
        val scope = readCacheScope() ?: return null
        if (scope.username.isBlank()) return null
        val context = sessionContextProvider()
        if (context.auth != SessionRequestAuth.Authenticated &&
            context.auth != SessionRequestAuth.Restoring
        ) {
            return null
        }
        if (!hasAuthenticatedCookies()) return null
        return ReadCacheIdentity(scope.site, scope.username, context.generation)
    }

    /**
     * Whether a failure is a transport outage the cache may answer for. Everything that is not a
     * plain [IOException] - an HTTP refusal, a decode failure - is excluded by type, and the two
     * [IOException]s the session layer raises are excluded explicitly: a challenge is about the
     * session, and a session that rotated mid-flight must be retried, not answered with stale rows.
     */
    private fun isTransportOutage(error: Throwable): Boolean =
        error is IOException &&
            error !is SessionRecoveryRequiredException &&
            error !is DiscourseRefusalException &&
            error !is SessionChangedException

    private suspend fun <T> runWithSessionBoundary(
        requiresAuthentication: Boolean = false,
        block: suspend () -> T,
    ): T {
        val requestContext = sessionContextProvider()
        if (requiresAuthentication && requestContext.canUseAuthenticatedSession &&
            !hasAuthenticatedCookies()
        ) {
            // The browser's own logout (or a server deletion) emptied the shared jar. An
            // authenticated call must report the session gone instead of going out anonymous.
            onSessionInvalidated(SessionExpiredReason.CookieMissing, requestContext.generation)
            onLoginRequired()
            throw LoginRequiredException()
        }
        return try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: LoginRequiredException) {
            throw error
        } catch (error: SessionLoggedOutException) {
            handleLoggedOutResponse(requestContext, requiresAuthentication, error)
        } catch (error: HttpException) {
            // ResponseBody is one-shot. Read it once so a JSON logout check
            // cannot consume the Cloudflare challenge evidence that follows.
            val errorBody = error.response()?.errorBody()?.string().orEmpty()
            val evidence = SessionResponseEvidence.classify(error.code(),
                error.response()?.headers() ?: okhttp3.Headers.Builder().build(), errorBody)
            when (evidence) {
                SessionResponseEvidence.Challenge -> throw SessionRecoveryRequiredException(cause = error)
                SessionResponseEvidence.LoggedOut -> handleLoggedOutResponse(requestContext, requiresAuthentication, error)
                SessionResponseEvidence.Csrf -> throw DiscourseRefusalException(403, "请求验证已过期，请重试；无需重新登录", error)
                SessionResponseEvidence.Forbidden -> throw DiscourseRefusalException(403, "请求被拒绝（403），尚不能确认是权限限制还是站点验证", error)
                SessionResponseEvidence.RateLimited -> throw DiscourseRefusalException(429, "请求过于频繁，请稍后重试", error)
                SessionResponseEvidence.Other -> throw error
            }
        }
    }

    private suspend fun handleLoggedOutResponse(
        requestContext: SessionRequestContext,
        requiresAuthentication: Boolean,
        cause: Throwable,
    ): Nothing {
        if (requestContext.canUseAuthenticatedSession) {
            // A restore is the coordinator's own verification and reports the rejection through
            // SessionLoggedOutException; an ordinary request reports it against its own
            // generation so a stale response cannot invalidate a newer WebView login.
            if (requestContext.auth != SessionRequestAuth.Restoring) {
                onSessionInvalidated(SessionExpiredReason.ServerLoggedOut, requestContext.generation)
            }
            throw SessionLoggedOutException(cause = cause)
        }
        if (requiresAuthentication || requestContext.auth == SessionRequestAuth.Anonymous) {
            onLoginRequired()
        }
        throw LoginRequiredException(cause = cause)
    }


}

internal fun looksLikeCloudflareChallenge(bodyLowercased: String): Boolean =
    bodyLowercased.contains("just a moment") ||
        bodyLowercased.contains("cf-chl-") ||
        bodyLowercased.contains("challenge-platform") ||
        bodyLowercased.contains("checking your browser")

data class ParsedBootstrap(
    val currentUsername: String?,
)

object BootstrapHtmlParser {
    fun parse(html: String): ParsedBootstrap {
        if (looksLikeCloudflareChallenge(html.lowercase(Locale.US))) {
            throw SessionRecoveryRequiredException()
        }
        val document = Jsoup.parse(html)
        val username = document.selectFirst("meta[name=current-username]")
            ?.attr("content")
            ?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("#data-preloaded")
                ?.attr("data-preloaded")
                ?.let(::parsePreloadedCurrentUsername)
        return ParsedBootstrap(currentUsername = username)
    }

    fun parsePreloadedCurrentUsername(preloadedData: String): String? =
        preloadedData.currentUserUsername()

    private fun String.currentUserUsername(): String? {
        val currentUserJson = runCatching {
            NetworkJson.instance.parseToJsonElement(this)
                .jsonObject["currentUser"]
        }.getOrNull()
        return when {
            currentUserJson == null -> null
            currentUserJson is kotlinx.serialization.json.JsonObject ->
                currentUserJson["username"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.isNotBlank() }
            else -> currentUserJson.jsonPrimitive.contentOrNull?.let { value ->
                runCatching {
                    NetworkJson.instance.parseToJsonElement(value)
                        .jsonObject["username"]
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.takeIf { username -> username.isNotBlank() }
                }.getOrNull()
            }
        }
    }
}

/**
 * The site and authenticated username a cache entry belongs to. This half is stable across reads
 * (it is the persisted key), so it is what AppContainer wires once from the session store; the
 * session generation travels alongside it per read as the per-flight fence.
 */
data class ReadCacheScope(
    val site: String,
    val username: String,
)

/** The full cache identity: the site, the reader, and the session generation the read ran under. */
data class ReadCacheIdentity(
    val site: String,
    val username: String,
    val generation: Int,
)
