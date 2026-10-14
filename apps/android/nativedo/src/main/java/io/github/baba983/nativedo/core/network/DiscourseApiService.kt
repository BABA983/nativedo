package io.github.baba983.nativedo.core.network

import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.PostPoll
import io.github.baba983.nativedo.core.model.PostPollOption
import io.github.baba983.nativedo.core.model.PostPollVote
import io.github.baba983.nativedo.core.model.PostReaction
import io.github.baba983.nativedo.core.model.PostReactionUser
import io.github.baba983.nativedo.core.model.PostReactionUsersGroup
import io.github.baba983.nativedo.core.html.plainTextFromHtml
import io.github.baba983.nativedo.core.model.Bookmark
import io.github.baba983.nativedo.core.model.ChatChannel
import io.github.baba983.nativedo.core.model.ChatChannelPage
import io.github.baba983.nativedo.core.model.ChatLastMessage
import io.github.baba983.nativedo.core.model.ChatMembership
import io.github.baba983.nativedo.core.model.ChatMessage
import io.github.baba983.nativedo.core.model.ChatMessagePage
import io.github.baba983.nativedo.core.model.ChatReplyTarget
import io.github.baba983.nativedo.core.model.ChatUser
import io.github.baba983.nativedo.core.model.CreatedTopic
import io.github.baba983.nativedo.core.model.Tag
import io.github.baba983.nativedo.core.model.CategorySummary
import io.github.baba983.nativedo.core.model.DiscourseEmoji
import io.github.baba983.nativedo.core.model.DiscourseEmojiCategory
import io.github.baba983.nativedo.core.model.SearchResult
import io.github.baba983.nativedo.core.model.SearchUserSummary
import io.github.baba983.nativedo.core.model.MentionCandidate
import io.github.baba983.nativedo.core.model.MentionSearchResult
import io.github.baba983.nativedo.core.model.NotificationItem
import io.github.baba983.nativedo.core.model.NotificationPage
import io.github.baba983.nativedo.core.model.TopicDetail
import io.github.baba983.nativedo.core.model.TopicListPage
import io.github.baba983.nativedo.core.model.TopicPostsPage
import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.core.model.UserActionPage
import io.github.baba983.nativedo.core.model.UserBadge
import io.github.baba983.nativedo.core.model.UserProfile
import io.github.baba983.nativedo.core.model.UserPreferences
import io.github.baba983.nativedo.core.model.UserSummary
import io.github.baba983.nativedo.core.session.SessionUserSummary
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface DiscourseApiService {
    @Headers("Accept: text/html")
    @GET("/")
    suspend fun bootstrap(): ResponseBody

    @GET("latest.json")
    suspend fun latest(@Query("page") page: Int): TopicListResponseDto

    @GET("new.json")
    suspend fun newTopics(@Query("page") page: Int): TopicListResponseDto

    /** The reader's own unread feed; the same envelope `latest.json` answers with. */
    @GET("unread.json")
    suspend fun unread(@Query("page") page: Int): TopicListResponseDto

    @GET("top.json")
    suspend fun topTopics(@Query("page") page: Int): TopicListResponseDto

    /**
     * `GET /topics/private-messages/{username}.json`: the reader's private-message inbox, carried in
     * the same `topic_list` envelope a public feed answers with. The site answers it only for the
     * reader themselves, so the username is the session's own.
     */
    @GET("topics/private-messages/{username}.json")
    suspend fun privateMessages(
        @Path("username") username: String,
        @Query("page") page: Int,
    ): TopicListResponseDto

    /** `GET /topics/private-messages-sent/{username}.json`: the messages the reader sent. */
    @GET("topics/private-messages-sent/{username}.json")
    suspend fun privateMessagesSent(
        @Path("username") username: String,
        @Query("page") page: Int,
    ): TopicListResponseDto

    /** `GET /topics/private-messages-unread/{username}.json`: the reader's unread messages. */
    @GET("topics/private-messages-unread/{username}.json")
    suspend fun privateMessagesUnread(
        @Path("username") username: String,
        @Query("page") page: Int,
    ): TopicListResponseDto

    @GET("search.json")
    suspend fun search(
        @Query("q") query: String,
        @Query("page") page: Int,
        @Query("type_filter") typeFilter: String? = null,
    ): SearchResponseDto

    @GET("notifications.json")
    suspend fun notifications(
        @Query("offset") offset: Int,
        @Query("limit") limit: Int,
    ): NotificationsResponseDto

    /**
     * `PUT /notifications/mark-read.json`: mark the reader's own notifications read. With `id` it
     * is that one notification; without it, every one of them (the site's own `mark-read` route,
     * whose `id` parameter is optional). Requires `X-CSRF-Token`.
     */
    @FormUrlEncoded
    @PUT("notifications/mark-read.json")
    suspend fun markNotificationsRead(
        @Field("id") id: Long?,
        @Header("X-CSRF-Token") csrfToken: String,
    ): ResponseBody

    @GET("u/search/users")
    suspend fun searchMentionUsers(
        @Query("term") term: String,
        @Query("topic_id") topicId: Long? = null,
        @Query("include_groups") includeGroups: Boolean = true,
        @Query("limit") limit: Int = 6,
    ): MentionSearchResponseDto

    /**
     * `GET /u/{username}/bookmarks.json`: the reader's own bookmarks, a page of twenty at a time.
     * The site only answers this for the reader themselves (an admin may read another's).
     */
    @GET("u/{username}/bookmarks.json")
    suspend fun bookmarks(
        @Path("username") username: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int,
    ): UserBookmarksResponseDto

    /** `GET /tags.json`: the site's tags, for a topic list's tag filter. */
    @GET("tags.json")
    suspend fun tags(): TagsResponseDto

    /** One tag's own latest feed. */
    @GET("tag/{slug}/l/latest.json")
    suspend fun tagTopics(
        @Path("slug") slug: String,
        @Query("page") page: Int,
    ): TopicListResponseDto

    /**
     * One category *and* one tag: the canonical combined route, which carries both the category's
     * and the tag's id in its path.
     */
    @GET("tags/c/{categorySlug}/{categoryId}/{tagSlug}/{tagId}/l/latest.json")
    suspend fun categoryTagTopics(
        @Path("categorySlug") categorySlug: String,
        @Path("categoryId") categoryId: Int,
        @Path("tagSlug") tagSlug: String,
        @Path("tagId") tagId: Long,
        @Query("page") page: Int,
    ): TopicListResponseDto

    @GET("site.json")
    suspend fun site(): SiteResponseDto

    /**
     * `GET /categories.json`: the category list a topic list is filtered by. The same entries
     * `site.json` embeds, with the site's own ordering and the colors the badges draw with.
     */
    @GET("categories.json")
    suspend fun categories(): CategoriesResponseDto

    /** One category's own latest feed; the same envelope `latest.json` answers with. */
    @GET("c/{slug}/{id}/l/latest.json")
    suspend fun categoryLatest(
        @Path("slug") slug: String,
        @Path("id") id: Int,
        @Query("page") page: Int,
    ): TopicListResponseDto

    @GET("t/{id}.json")
    suspend fun topic(@Path("id") id: Long): TopicResponseDto

    @GET("t/{id}/{postNumber}.json")
    suspend fun topicAtPost(
        @Path("id") id: Long,
        @Path("postNumber") postNumber: Int,
    ): TopicResponseDto

    @GET("t/{id}/posts.json")
    suspend fun topicPosts(
        @Path("id") id: Long,
        @Query("post_ids[]") postIds: List<Long>,
        /**
         * `include_suggested=true` on the page that reaches the real end of the stream, so the site
         * carries its own ranked `suggested_topics` with it; null omits the field, which the site
         * reads as false (the same as any page that is not the end).
         */
        @Query("include_suggested") includeSuggested: Boolean?,
    ): TopicPostsResponseDto

    @GET("posts/by_number/{topicId}/{postNumber}.json")
    suspend fun postByNumber(
        @Path("topicId") topicId: Long,
        @Path("postNumber") postNumber: Int,
    ): PostDto

    @GET("posts/{id}/replies")
    suspend fun postReplies(
        @Path("id") postId: Long,
        @Query("after") afterPostNumber: Int,
    ): List<PostDto>

    /**
     * `GET /posts/{id}.json`: one post on its own. This is a post's own read - the same serializer
     * `posts/by_number` answers with - and unlike a topic window it carries `raw`, the markdown an
     * edit begins from. A moderator may read a deleted post; another reader gets a 404.
     */
    @GET("posts/{id}.json")
    suspend fun post(@Path("id") id: Long): PostDto

    /**
     * `PUT /posts/{id}.json`: the edit route. Discourse's `posts#update` requires `post` and reads
     * `post[raw]` (and optional `post[edit_reason]`), and answers `{"post": {...}}` - not a bare post
     * like `show`.
     */
    @FormUrlEncoded
    @PUT("posts/{id}.json")
    suspend fun updatePost(
        @Path("id") id: Long,
        @Field("post[raw]") raw: String,
        @Field("post[edit_reason]") editReason: String?,
        @Header("X-CSRF-Token") csrfToken: String,
    ): PostUpdateResponseDto

    /**
     * `DELETE /posts/{id}.json`: the delete route. Discourse's `posts#destroy` renders no body, so
     * the site answers a bodyless `204`. Retrofit hands a `204`/`205` a null body, which a non-null
     * `ResponseBody` return cannot accept; `Unit` is the return that reads a bodyless success.
     */
    @DELETE("posts/{id}.json")
    suspend fun deletePost(
        @Path("id") id: Long,
        @Header("X-CSRF-Token") csrfToken: String,
    ): Unit

    /**
     * `PUT /posts/{id}/recover.json`: the undelete route. Discourse's `posts#recover` renders the
     * recovered post through the same helper `show` uses, so the answer is a bare post.
     */
    @PUT("posts/{id}/recover.json")
    suspend fun recoverPost(
        @Path("id") id: Long,
        @Header("X-CSRF-Token") csrfToken: String,
    ): PostDto

    @FormUrlEncoded
    @POST("posts.json")
    suspend fun createPost(
        @Field("topic_id") topicId: Long,
        @Field("raw") raw: String,
        @Field("reply_to_post_number") replyToPostNumber: Int?,
        @Header("X-CSRF-Token") csrfToken: String,
    ): PostDto

    /**
     * `POST /posts.json` as the web composer sends a *new topic*: the same route a reply takes, with
     * the topic's own fields instead of `topic_id` - the site creates the topic and its first floor
     * from `title`, `category` and `raw`. Requires `X-CSRF-Token`.
     */
    @FormUrlEncoded
    @POST("posts.json")
    suspend fun createTopic(
        @Field("title") title: String,
        @Field("category") categoryId: Int,
        @Field("raw") raw: String,
        @Header("X-CSRF-Token") csrfToken: String,
    ): PostDto

    /**
     * `POST /posts.json` as the site's own private-message composer sends it: the same route a new
     * topic takes, with `archetype=private_message` and `target_recipients` (a comma-separated list
     * of usernames) instead of a category. The site - not the client - decides whether the reader
     * may send to those recipients and refuses the rest with the usual `errors` array. The field is
     * `target_recipients`, not `target_usernames`. Requires `X-CSRF-Token`.
     */
    @FormUrlEncoded
    @POST("posts.json")
    suspend fun createPrivateMessage(
        @Field("title") title: String,
        @Field("raw") raw: String,
        @Field("archetype") archetype: String,
        @Field("target_recipients") targetRecipients: String,
        @Header("X-CSRF-Token") csrfToken: String,
    ): PostDto

    /**
     * `POST /uploads.json` as the web composer sends it: the file as a multipart part, the
     * composer upload type, and `synchronous=true` so the answer carries the id and URL instead
     * of the asynchronous upload's empty acknowledgement. Requires `X-CSRF-Token`.
     */
    @Multipart
    @POST("uploads.json")
    suspend fun upload(
        @Part file: MultipartBody.Part,
        @Part("upload_type") uploadType: RequestBody,
        @Part("synchronous") synchronous: RequestBody,
        @Header("X-CSRF-Token") csrfToken: String,
    ): UploadResponseDto

    @FormUrlEncoded
    @POST("topics/timings")
    suspend fun postTimings(
        @Field("topic_id") topicId: Long,
        @Field("topic_time") topicTime: Long,
        @FieldMap timings: Map<String, String>,
        @Header("X-CSRF-Token") csrfToken: String,
    )

    @FormUrlEncoded
    @POST("bookmarks.json")
    suspend fun createBookmark(
        @Field("bookmarkable_id") bookmarkableId: Long,
        @Field("bookmarkable_type") bookmarkableType: String,
        @Field("auto_delete_preference") autoDeletePreference: Int,
        @Header("X-CSRF-Token") csrfToken: String,
    ): ResponseBody

    @DELETE("posts/{id}/bookmark")
    suspend fun deletePostBookmark(
        @Path("id") postId: Long,
        @Header("X-CSRF-Token") csrfToken: String,
    ): ResponseBody

    /** Bookmarks the topic itself (Discourse's `topics#bookmark`); the state lands in `bookmarked`. */
    @PUT("t/{id}/bookmark.json")
    suspend fun bookmarkTopic(
        @Path("id") topicId: Long,
        @Header("X-CSRF-Token") csrfToken: String,
    ): ResponseBody

    /** Drops the reader's bookmarks on this topic - a topic's own bookmark has no single id here. */
    @PUT("t/{id}/remove_bookmarks.json")
    suspend fun removeTopicBookmarks(
        @Path("id") topicId: Long,
        @Header("X-CSRF-Token") csrfToken: String,
    ): ResponseBody

    /**
     * `POST /t/{id}/notifications.json`: the reader's own notification level for one topic, as the
     * web client's topic-tracking control sends it - `0` mute, `1` regular, `2` tracking, `3`
     * watching. Requires `X-CSRF-Token`; the site answers `{"success":"OK"}`.
     */
    @FormUrlEncoded
    @POST("t/{id}/notifications.json")
    suspend fun setTopicNotificationLevel(
        @Path("id") topicId: Long,
        @Field("notification_level") level: Int,
        @Header("X-CSRF-Token") csrfToken: String,
    ): ResponseBody

    @GET("discourse-reactions/posts/{id}/reactions-users.json")
    suspend fun reactionUsers(
        @Path("id") postId: Long,
    ): ReactionUsersResponseDto

    @PUT("discourse-reactions/posts/{id}/custom-reactions/{reaction}/toggle.json")
    suspend fun toggleReaction(
        @Path("id") postId: Long,
        @Path("reaction") reactionId: String,
        @Header("X-CSRF-Token") csrfToken: String,
    ): ResponseBody

    @FormUrlEncoded
    @PUT("polls/vote")
    suspend fun votePoll(
        @Field("post_id") postId: Long,
        @Field("poll_name") pollName: String,
        @Field("options[]") optionIds: List<String>,
        @Header("X-CSRF-Token") csrfToken: String,
    ): PollVoteResponseDto

    @FormUrlEncoded
    @HTTP(method = "DELETE", path = "polls/vote", hasBody = true)
    suspend fun removePollVote(
        @Field("post_id") postId: Long,
        @Field("poll_name") pollName: String,
        @Header("X-CSRF-Token") csrfToken: String,
    ): PollVoteResponseDto

    @GET("polls/voters.json")
    suspend fun pollVoters(
        @Query("post_id") postId: Long,
        @Query("poll_name") pollName: String,
        @Query("option_id") optionId: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
    ): PollVotersResponseDto

    @GET("emojis.json")
    suspend fun emojis(): EmojiGroupsDto

    @GET("u/{username}.json")
    suspend fun profile(@Path("username") username: String): UserProfileResponseDto

    /**
     * `GET /u/{username}.json` again, read for its other half: the same payload carries the
     * reader's own `user_option` when the username is the signed-in one. The public profile
     * endpoint does not have it, so the settings screens ask for the same route this way.
     */
    @GET("u/{username}.json")
    suspend fun userPreferences(@Path("username") username: String): UserPreferencesResponseDto

    /**
     * `PUT /u/{username}.json`: the reader updating their own preferences. Discourse's
     * `users#update` takes the option attributes as top-level form fields (`UserUpdater::
     * OPTION_ATTR`), a boolean as the string `"true"`/`"false"`, and answers with the updated
     * user. The write needs the session's CSRF token.
     */
    @FormUrlEncoded
    @PUT("u/{username}.json")
    suspend fun updateUserPreferences(
        @Path("username") username: String,
        @FieldMap fields: Map<String, String>,
        @Header("X-CSRF-Token") csrfToken: String,
    ): PreferencesUpdateResponseDto

    /** `GET /u/{username}/summary.json`: the stats block (likes, days visited, post counts). */
    @GET("u/{username}/summary.json")
    suspend fun userSummary(@Path("username") username: String): UserSummaryResponseDto

    /** `GET /user-badges/{username}.json`: the badges granted to the reader, newest first. */
    @GET("user-badges/{username}.json")
    suspend fun userBadges(@Path("username") username: String): UserBadgesResponseDto

    /** Follows: the discourse-follow plugin's `PUT /follow/{username}`. */
    @PUT("follow/{username}.json")
    suspend fun follow(
        @Path("username") username: String,
        @Header("X-CSRF-Token") csrfToken: String,
    ): ResponseBody

    /** Unfollows: the plugin's `DELETE /follow/{username}`. */
    @DELETE("follow/{username}.json")
    suspend fun unfollow(
        @Path("username") username: String,
        @Header("X-CSRF-Token") csrfToken: String,
    ): ResponseBody

    /**
     * `PUT /u/{username}/notification_level.json`: `mute`, `ignore` or `normal`. The site answers
     * with the reader's new notification level for that user.
     */
    @FormUrlEncoded
    @PUT("u/{username}/notification_level.json")
    suspend fun setNotificationLevel(
        @Path("username") username: String,
        @Field("notification_level") level: String,
        @Header("X-CSRF-Token") csrfToken: String,
    ): ResponseBody

    @GET("session/current.json")
    suspend fun currentSession(): CurrentSessionResponseDto

    @GET("session/csrf")
    suspend fun csrf(): CsrfResponseDto

    // Discourse user activity stream. filter values: 4 = topics created,
    // 5 = replies/posts. Returns a uniform action feed (see contracts/discourse).
    @GET("user_actions.json")
    suspend fun userActions(
        @Query("username") username: String,
        @Query("filter") filter: String,
        @Query("offset") offset: Int,
        @Query("limit") limit: Int,
    ): UserActionsResponseDto

    /**
     * `GET /chat/api/channels.json?limit=25&offset=N`: the public (category) channels the reader can
     * see, in the site's own order, one page at a time. Direct-message channels are not public and
     * are not in this list. The envelope is `{channels:[...], meta:{load_more_url}}`, and each
     * channel carries its own `status`, `current_user_membership` and `meta` (the live-update ids).
     */
    @GET("chat/api/channels.json")
    suspend fun chatChannels(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): ChatChannelsResponseDto

    /** `GET /chat/api/channels/{id}.json`: one channel, with the reader's own membership. */
    @GET("chat/api/channels/{id}.json")
    suspend fun chatChannel(@Path("id") channelId: Long): ChatChannelResponseDto

    /**
     * `GET /chat/api/channels/{id}/messages.json`: one window of a channel's messages. With no
     * `target_message_id` the site answers around the reader's last-read position; with one and a
     * `direction` of `past` or `future` it answers a window before or after that message. The
     * answer's own `meta` says whether another window follows in each direction.
     */
    @GET("chat/api/channels/{id}/messages.json")
    suspend fun chatChannelMessages(
        @Path("id") channelId: Long,
        @Query("page_size") pageSize: Int,
        @Query("target_message_id") targetMessageId: Long? = null,
        @Query("direction") direction: String? = null,
        @Query("fetch_from_last_read") fetchFromLastRead: Boolean? = null,
    ): ChatMessagesResponseDto

    /**
     * `POST /chat/api/channels/{id}/memberships/me.json`: joins the channel. Discourse's
     * `channels_current_user_membership#create` takes no body and answers `{membership:{...}}`.
     * Requires `X-CSRF-Token`.
     */
    @POST("chat/api/channels/{id}/memberships/me.json")
    suspend fun joinChatChannel(
        @Path("id") channelId: Long,
        @Header("X-CSRF-Token") csrfToken: String,
    ): ChatMembershipResponseDto

    /**
     * `POST /chat/{id}.json` (the chat controller route, **not** `chat/api/...`): sends one message,
     * as the web client sends it - form fields `message`, optional `in_reply_to_id` and `staged_id`.
     * The answer is an acknowledgement only (`{success:"OK", message_id}`) - not the cooked message -
     * so a client reads the accepted id back rather than drawing the write. Requires `X-CSRF-Token`.
     */
    @FormUrlEncoded
    @POST("chat/{id}.json")
    suspend fun createChatMessage(
        @Path("id") channelId: Long,
        @Field("message") message: String,
        @Field("in_reply_to_id") inReplyToId: Long?,
        @Field("staged_id") stagedId: String?,
        @Header("X-CSRF-Token") csrfToken: String,
    ): ChatWriteAckDto

    /**
     * `PUT /chat/api/channels/{id}/read.json`: advances the reader's last-read position, form field
     * `message_id`. The site answers `{success:"OK"}`. Requires `X-CSRF-Token`.
     */
    @FormUrlEncoded
    @PUT("chat/api/channels/{id}/read.json")
    suspend fun markChatChannelRead(
        @Path("id") channelId: Long,
        @Field("message_id") messageId: Long,
        @Header("X-CSRF-Token") csrfToken: String,
    ): ChatAckDto
}

@Serializable
data class TopicListResponseDto(
    val users: List<TopicListUserDto> = emptyList(),
    @SerialName("topic_list")
    val topicList: TopicListDto,
)

@Serializable
data class TopicListDto(
    @SerialName("more_topics_url")
    val moreTopicsUrl: String? = null,
    val topics: List<TopicDto>,
)

@Serializable
data class TopicDto(
    val id: Long,
    val title: String = "",
    @SerialName("fancy_title")
    val fancyTitle: String = "",
    @SerialName("image_url")
    val imageUrl: String? = null,
    val slug: String = "",
    @SerialName("reply_count")
    val replyCount: Int = 0,
    val views: Int = 0,
    @SerialName("like_count")
    val likeCount: Int = 0,
    @SerialName("last_posted_at")
    val lastPostedAt: String = "",
    @SerialName("category_id")
    val categoryId: Int? = null,
    @SerialName("last_poster_username")
    val lastPosterUsername: String? = null,
    val pinned: Boolean = false,
    @SerialName("pinned_globally")
    val pinnedGlobally: Boolean = false,
    @SerialName("is_hot")
    val isHot: Boolean = false,
    @Serializable(with = TopicTagListSerializer::class)
    val tags: List<String> = emptyList(),
    val excerpt: String = "",
    val posters: List<PosterDto> = emptyList(),
    @SerialName("unread_posts")
    val unreadPosts: Int = 0,
    val unseen: Boolean = false,
    @SerialName("notification_level")
    val notificationLevel: Int? = null,
)

/**
 * `POST /uploads.json`'s answer: `url` is what a post embeds (usually absolute; a path when the
 * site answered a relative one), the dimensions are what the site read from the file.
 */
@Serializable
data class UploadResponseDto(
    val id: Long = 0,
    val url: String = "",
    @SerialName("original_filename")
    val originalFilename: String? = null,
    // The site sends `null` for anything that is not an image or a video, so these must be nullable
    // even though the DTO's reader wants a number - a non-nullable Int here fails the decode of a
    // perfectly good 200 response (a `.zip` upload), which reads as "the upload failed".
    val width: Int? = null,
    val height: Int? = null,
    val extension: String? = null,
    @SerialName("short_url")
    val shortUrl: String? = null,
    // The site's own human size ("12.3 KB"), which its composer appends to an attachment's markdown.
    @SerialName("human_filesize")
    val humanFilesize: String? = null,
)

@Serializable
data class SearchResponseDto(
    val topics: List<TopicDto> = emptyList(),
    val posts: List<SearchPostDto> = emptyList(),
    val users: List<SearchUserDto> = emptyList(),
    @SerialName("grouped_search_result")
    val groupedSearchResult: GroupedSearchResultDto? = null,
)

@Serializable
data class GroupedSearchResultDto(
    @SerialName("more_posts")
    val morePosts: Boolean? = null,
    @SerialName("more_topics")
    val moreTopics: Boolean? = null,
    @SerialName("more_full_page_results")
    val moreFullPageResults: Boolean? = null,
)

@Serializable
data class SearchPostDto(
    val id: Long,
    @SerialName("topic_id")
    val topicId: Long,
    @SerialName("post_number")
    val postNumber: Int? = null,
    @SerialName("topic_title")
    val topicTitle: String = "",
    val username: String = "",
    @SerialName("avatar_template")
    val avatarTemplate: String = "",
    @SerialName("animated_avatar")
    val animatedAvatar: String? = null,
    @SerialName("created_at")
    val createdAt: String = "",
    val blurb: String = "",
    @SerialName("like_count")
    val likeCount: Int = 0,
    @SerialName("category_id")
    val categoryId: Int? = null,
)

@Serializable
data class SearchUserDto(
    val id: Long? = null,
    val username: String = "",
    val name: String = "",
    @SerialName("avatar_template")
    val avatarTemplate: String = "",
    @SerialName("animated_avatar")
    val animatedAvatar: String? = null,
)

@Serializable
data class MentionSearchResponseDto(
    val users: List<MentionUserDto> = emptyList(),
    val groups: List<MentionGroupDto> = emptyList(),
)

@Serializable
data class MentionUserDto(
    val username: String = "",
    val name: String = "",
    @SerialName("avatar_template")
    val avatarTemplate: String = "",
    @SerialName("priority_group")
    val priorityGroup: Int? = null,
)

@Serializable
data class MentionGroupDto(
    val name: String = "",
    @SerialName("full_name")
    val fullName: String = "",
    @SerialName("flair_url")
    val flairUrl: String = "",
    @SerialName("user_count")
    val userCount: Int? = null,
)

@Serializable
data class PosterDto(
    val description: String = "",
    @SerialName("user_id")
    val userId: Long? = null,
    val user: PosterUserDto? = null,
)

@Serializable
data class PosterUserDto(
    val username: String = "",
    @SerialName("avatar_template")
    val avatarTemplate: String = "",
    @SerialName("animated_avatar")
    val animatedAvatar: String? = null,
)

@Serializable
data class SiteResponseDto(
    val categories: List<SiteCategoryDto> = emptyList(),
    @SerialName("category_list")
    val categoryList: SiteCategoryListDto? = null,
)

/**
 * `GET /categories.json`: the list itself. Its envelope is the one `site.json` carries under
 * `category_list`, so the entries are the same type and are read the same way.
 */
@Serializable
data class CategoriesResponseDto(
    @SerialName("category_list")
    val categoryList: SiteCategoryListDto? = null,
)

/**
 * The bookmarks list. A page with rows arrives under `user_bookmark_list`; a page the site has
 * nothing for is a bare `bookmarks: []` with no wrapper at all (the site's own client reads both).
 */
/**
 * The site's tag list. A site that lists its tags by group answers `extras.tag_groups` beside the
 * plain list; the plain list is always there, so it is the one this client reads.
 */
@Serializable
data class TagsResponseDto(
    val tags: List<TagDto> = emptyList(),
    val extras: TagExtrasDto? = null,
)

@Serializable
data class TagExtrasDto(
    @SerialName("tag_groups")
    val tagGroups: List<TagGroupDto> = emptyList(),
)

@Serializable
data class TagGroupDto(
    val id: Long = 0,
    val name: String = "",
    val tags: List<TagDto> = emptyList(),
)

/** One tag: what a reader reads it as, what a URL calls it, and how many topics carry it. */
@Serializable
data class TagDto(
    val id: Long,
    val text: String = "",
    val name: String = "",
    val slug: String = "",
    val description: String? = null,
    val count: Int = 0,
)

/** What the site answers a write it refused: one or more messages, and the site's own type for it. */
@Serializable
data class DiscourseErrorsDto(
    val errors: List<String> = emptyList(),
    @SerialName("error_type")
    val errorType: String? = null,
)

@Serializable
data class UserBookmarksResponseDto(
    @SerialName("user_bookmark_list")
    val list: UserBookmarkListDto? = null,
    val bookmarks: List<UserBookmarkDto> = emptyList(),
)

@Serializable
data class UserBookmarkListDto(
    val bookmarks: List<UserBookmarkDto> = emptyList(),
    @SerialName("more_bookmarks_url")
    val moreBookmarksUrl: String? = null,
)

/**
 * One bookmark, as the site's own serializer writes it. The post is `bookmarkable_id` with
 * `bookmarkable_type == "Post"`, and the floor it is on is `linked_post_number` - the field the
 * serializer resolves from the post, not a copy of it.
 */
@Serializable
data class UserBookmarkDto(
    val id: Long,
    @SerialName("bookmarkable_id")
    val bookmarkableId: Long? = null,
    @SerialName("bookmarkable_type")
    val bookmarkableType: String = "",
    @SerialName("topic_id")
    val topicId: Long = 0,
    @SerialName("linked_post_number")
    val linkedPostNumber: Int? = null,
    val title: String = "",
    @SerialName("fancy_title")
    val fancyTitle: String = "",
    val excerpt: String = "",
    @SerialName("created_at")
    val createdAt: String = "",
    val user: TopicListUserDto? = null,
)

@Serializable
data class SiteCategoryListDto(
    val categories: List<SiteCategoryDto> = emptyList(),
)

@Serializable
data class SiteCategoryDto(
    val id: Int,
    val name: String = "",
    val slug: String = "",
    val color: String = "",
    @SerialName("text_color")
    val textColor: String = "",
    @SerialName("parent_category_id")
    val parentCategoryId: Int? = null,
    @SerialName("subcategory_list")
    val subcategoryList: List<SiteCategoryDto> = emptyList(),
)

@Serializable
data class TopicListUserDto(
    val id: Long,
    val username: String = "",
    /** Only the payloads that embed a full user carry this; a topic list's users do not. */
    val name: String? = null,
    @SerialName("avatar_template")
    val avatarTemplate: String = "",
    @SerialName("animated_avatar")
    val animatedAvatar: String? = null,
)

@Serializable
data class TopicResponseDto(
    val id: Long,
    val title: String,
    /** Discourse's title slug; the canonical topic URL carries it (`/t/<slug>/<id>`). */
    val slug: String = "",
    @SerialName("posts_count")
    val postsCount: Int = 0,
    @SerialName("highest_post_number")
    val highestPostNumber: Int = 0,
    @SerialName("last_read_post_number")
    val lastReadPostNumber: Int? = null,
    @SerialName("last_read_post_id")
    val lastReadPostId: Long? = null,
    @SerialName("created_at")
    val createdAt: String = "",
    @SerialName("category_id")
    val categoryId: Int = 0,
    /** The reader's own topic-level bookmark; a post's own flag is the same idea one level down. */
    val bookmarked: Boolean = false,
    @SerialName("last_posted_at")
    val lastPostedAt: String = "",
    val views: Int = 0,
    @Serializable(with = TopicTagListSerializer::class)
    val tags: List<String> = emptyList(),
    @SerialName("valid_reactions")
    val validReactions: List<String> = emptyList(),
    @SerialName("post_stream")
    val postStream: PostStreamDto,
    val details: TopicDetailsDto? = null,
    /**
     * The site's own suggestions for what to read after this topic, in the order it ranked them
     * (`suggested_topics`, a `SuggestedTopicSerializer` array; the key is simply absent when the
     * site has none). Each entry is the topic-list shape the feed already reads, so the same
     * mapping applies. The client never invents the order or the ids.
     */
    @SerialName("suggested_topics")
    val suggestedTopics: List<TopicDto> = emptyList(),
)

@Serializable
data class TopicDetailsDto(
    @SerialName("created_by")
    val createdBy: TopicCreatedByDto? = null,
    val participants: List<TopicParticipantDto> = emptyList(),
    /**
     * The reader's own notification level for this topic, as the site's topic view reports it:
     * `0` mute, `1` regular, `2` tracking, `3` watching. Null when the site did not name one.
     */
    @SerialName("notification_level")
    val notificationLevel: Int? = null,
)

/** `details.created_by`, the topic's original poster (Discourse's own BasicUserSerializer). */
@Serializable
data class TopicCreatedByDto(
    val username: String = "",
    val name: String? = null,
    @SerialName("avatar_template")
    val avatarTemplate: String = "",
    @SerialName("animated_avatar")
    val animatedAvatar: String? = null,
)

@Serializable
data class TopicParticipantDto(
    val username: String = "",
    val name: String? = null,
    @SerialName("avatar_template")
    val avatarTemplate: String = "",
    @SerialName("animated_avatar")
    val animatedAvatar: String? = null,
    @SerialName("post_count")
    val postCount: Int = 0,
)

@Serializable
data class PostStreamDto(
    val stream: List<Long> = emptyList(),
    val posts: List<PostDto>,
)

@Serializable
data class TopicPostsResponseDto(
    @SerialName("post_stream")
    val postStream: TopicPostsStreamDto,
    /**
     * The site's own ranked suggestions for what to read after the topic, carried only when the
     * read asked for them (`include_suggested=true`) and the site has any. The key is absent in
     * every other answer, so an empty list here is "the site named none". Each entry is the same
     * topic-list shape a feed's `topic_list.topics[]` uses.
     */
    @SerialName("suggested_topics")
    val suggestedTopics: List<TopicDto> = emptyList(),
)

@Serializable
data class TopicPostsStreamDto(
    val posts: List<PostDto>,
)

@Serializable
data class PostDto(
    val id: Long,
    @SerialName("post_number")
    val postNumber: Int? = null,
    @SerialName("reply_to_post_number")
    val replyToPostNumber: Int? = null,
    // A created topic answers with the post plus the topic it created. A reply carries none of
    // these, so they are optional; a private message's own answer is what a client navigates by.
    @SerialName("topic_id")
    val topicId: Long? = null,
    @SerialName("topic_slug")
    val topicSlug: String? = null,
    @SerialName("topic_title")
    val topicTitle: String? = null,
    @SerialName("reply_count")
    val replyCount: Int = 0,
    val username: String,
    @SerialName("name")
    val displayName: String? = null,
    @SerialName("avatar_template")
    val avatarTemplate: String = "",
    @SerialName("animated_avatar")
    val animatedAvatar: String? = null,
    @SerialName("user_title")
    val userTitle: String? = null,
    @SerialName("flair_name")
    val flairName: String? = null,
    @SerialName("flair_url")
    val flairUrl: String? = null,
    @SerialName("flair_bg_color")
    val flairBgColor: String? = null,
    @SerialName("flair_color")
    val flairColor: String? = null,
    @SerialName("created_at")
    val createdAt: String = "",
    @SerialName("updated_at")
    val updatedAt: String = "",
    val version: Int = 1,
    /** The site's own authorship and permissions for this post; the menu is built from these. */
    val yours: Boolean? = null,
    @SerialName("can_edit")
    val canEdit: Boolean? = null,
    @SerialName("can_delete")
    val canDelete: Boolean? = null,
    @SerialName("can_recover")
    val canRecover: Boolean? = null,
    @SerialName("deleted_at")
    val deletedAt: String? = null,
    /** The markdown the site stores; present on a post's own read, absent in a topic window. */
    val raw: String? = null,
    @SerialName("reactions")
    val reactions: List<PostReactionDto> = emptyList(),
    @SerialName("current_user_reaction")
    val currentUserReaction: CurrentUserReactionDto? = null,
    @SerialName("reaction_users_count")
    val reactionUsersCount: Int = 0,
    @SerialName("bookmarked")
    val bookmarked: Boolean? = null,
    @SerialName("cooked")
    val cookedHtml: String,
    val polls: List<DiscoursePollDto> = emptyList(),
    @SerialName("polls_votes")
    val pollsVotes: Map<String, List<PollVoteDto>> = emptyMap(),
)

/** The answer to `PUT /posts/{id}.json`: Discourse's `posts#update` wraps the post under `post`. */
@Serializable
data class PostUpdateResponseDto(
    val post: PostDto,
)

@Serializable
data class DiscoursePollDto(
    val id: Long = 0,
    val name: String,
    val type: String? = null,
    val status: String? = null,
    @SerialName("public")
    val isPublic: Boolean? = null,
    val results: String? = null,
    val voters: Int? = null,
    @SerialName("chart_type")
    val chartType: String? = null,
    val title: String? = null,
    val min: Int? = null,
    val max: Int? = null,
    val step: Int? = null,
    @SerialName("closed_at")
    val closedAt: String? = null,
    @SerialName("closed_by")
    val closedBy: String? = null,
    val options: List<DiscoursePollOptionDto> = emptyList(),
)

@Serializable
data class DiscoursePollOptionDto(
    val id: String,
    val html: String = "",
    /** Missing, not null, while the reader may not see results; see `contracts/discourse/models.md`. */
    val votes: Int? = null,
    /** Only a vote response carries the reader's own selection. */
    val chosen: Boolean = false,
    val rank: Int? = null,
)

/** One option of a `polls_votes` entry: a plain digest, or a ranked `{digest, rank}` object. */
@Serializable(with = PollVoteDtoSerializer::class)
data class PollVoteDto(
    val digest: String,
    val rank: Int? = null,
)

/** The answer to `PUT`/`DELETE /polls/vote`: the poll's new state, and the reader's selection. */
@Serializable
data class PollVoteResponseDto(
    val poll: DiscoursePollDto? = null,
    val vote: List<PollVoteDto> = emptyList(),
)

@Serializable
data class PostReactionDto(
    val id: String,
    val count: Int = 0,
    @SerialName("icon")
    val icon: String? = null,
)

@Serializable
data class CurrentUserReactionDto(
    val id: String,
)

@Serializable
data class ReactionUsersResponseDto(
    @SerialName("reaction_users")
    val reactionUsers: List<PostReactionUsersGroupDto> = emptyList(),
)

@Serializable
data class PostReactionUsersGroupDto(
    val id: String,
    val count: Int = 0,
    val users: List<PostReactionUserDto> = emptyList(),
)

@Serializable
data class PostReactionUserDto(
    val id: Long? = null,
    val username: String,
    @SerialName("name")
    val displayName: String? = null,
    @SerialName("avatar_template")
    val avatarTemplate: String = "",
    @SerialName("animated_avatar")
    val animatedAvatar: String? = null,
    @SerialName("can_undo")
    val canUndo: Boolean = false,
    @SerialName("created_at")
    val createdAt: String = "",
)

@Serializable
data class PollVotersResponseDto(
    val voters: Map<String, List<PollVoterDto>> = emptyMap(),
)

@Serializable
data class PollVoterDto(
    val id: Long? = null,
    val username: String = "",
    @SerialName("name")
    val displayName: String? = null,
    @SerialName("avatar_template")
    val avatarTemplate: String = "",
    val title: String? = null,
    val user: PollVoterUserDto? = null,
)

@Serializable
data class PollVoterUserDto(
    val id: Long? = null,
    val username: String = "",
    @SerialName("name")
    val displayName: String? = null,
    @SerialName("avatar_template")
    val avatarTemplate: String = "",
)

typealias EmojiGroupsDto = Map<String, List<EmojiDto>>

@Serializable
data class EmojiDto(
    val name: String,
    val url: String,
    val group: String? = null,
)

@Serializable
data class CurrentSessionResponseDto(
    @SerialName("current_user")
    val currentUser: kotlinx.serialization.json.JsonElement,
)

@Serializable
data class CurrentUserDto(
    val username: String = "",
    val name: String = "",
    @SerialName("avatar_template")
    val avatarTemplate: String = "",
)

internal fun CurrentUserDto.toSessionUserSummary(): SessionUserSummary? {
    val normalizedUsername = username.takeIf { it.isNotBlank() } ?: return null
    return SessionUserSummary(
        username = normalizedUsername,
        displayName = name.takeIf { it.isNotBlank() } ?: normalizedUsername,
        avatarTemplate = avatarTemplate,
    )
}

@Serializable
data class UserProfileResponseDto(
    val user: UserDto,
)

/**
 * The reader's own payload read for its preferences: just enough of `user` to reach
 * `user_option`, which is the object the settings screens draw from. It is present only when the
 * username asked for is the signed-in reader.
 */
@Serializable
data class UserPreferencesResponseDto(
    val user: PreferencesUserDto,
)

@Serializable
data class PreferencesUserDto(
    val username: String = "",
    @SerialName("user_option")
    val userOption: UserOptionDto? = null,
)

@Serializable
data class UserOptionDto(
    @SerialName("like_notification_frequency")
    val likeNotificationFrequency: Int? = null,
    @SerialName("notify_on_linked_posts")
    val notifyOnLinkedPosts: Boolean? = null,
    @SerialName("notification_level_when_replying")
    val notificationLevelWhenReplying: Int? = null,
    @SerialName("auto_track_topics_after_msecs")
    val autoTrackTopicsAfterMsecs: Int? = null,
    @SerialName("new_topic_duration_minutes")
    val newTopicDurationMinutes: Int? = null,
    @SerialName("allow_private_messages")
    val allowPrivateMessages: Boolean? = null,
    @SerialName("hide_profile")
    val hideProfile: Boolean? = null,
)

/** `PUT /u/{username}.json` answers `{"success":"OK",...}` and the updated user. */
@Serializable
data class PreferencesUpdateResponseDto(
    val success: String = "",
)

internal fun UserPreferencesResponseDto.toModel(): UserPreferences? {
    val option = user.userOption ?: return null
    return UserPreferences(
        likeNotificationFrequency = option.likeNotificationFrequency ?: 0,
        notifyOnLinkedPosts = option.notifyOnLinkedPosts ?: true,
        notificationLevelWhenReplying = option.notificationLevelWhenReplying ?: 2,
        autoTrackTopicsAfterMsecs = option.autoTrackTopicsAfterMsecs ?: 300000,
        newTopicDurationMinutes = option.newTopicDurationMinutes ?: 2880,
        allowPrivateMessages = option.allowPrivateMessages ?: true,
        hideProfile = option.hideProfile ?: false,
    )
}

@Serializable
data class UserDto(
    val username: String,
    @SerialName("name")
    val displayName: String? = null,
    @SerialName("bio_cooked")
    val bio: String? = null,
    @SerialName("avatar_template")
    val avatarTemplate: String = "",
    @SerialName("animated_avatar")
    val animatedAvatar: String? = null,
    @SerialName("flair_name")
    val flairName: String? = null,
    @SerialName("flair_url")
    val flairUrl: String? = null,
    @SerialName("flair_bg_color")
    val flairBgColor: String? = null,
    @SerialName("flair_color")
    val flairColor: String? = null,
    @SerialName("trust_level")
    val trustLevel: Int = 0,
    val title: String? = null,
    @SerialName("badge_count")
    val badgeCount: Int = 0,
    @SerialName("time_read")
    val timeRead: Int = 0,
    @SerialName("recent_time_read")
    val recentTimeRead: Int = 0,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("last_seen_at")
    val lastSeenAt: String? = null,
    @SerialName("last_posted_at")
    val lastPostedAt: String? = null,
    @SerialName("profile_view_count")
    val profileViewCount: Int = 0,
    val admin: Boolean = false,
    val moderator: Boolean = false,
    @SerialName("gamification_score")
    val gamificationScore: Int = 0,
    @SerialName("cakedate")
    val cakeDate: String? = null,
    @SerialName("total_followers")
    val totalFollowers: Int = 0,
    @SerialName("total_following")
    val totalFollowing: Int = 0,
    @SerialName("accepted_answers")
    val acceptedAnswers: Int = 0,
    @SerialName("primary_group_name")
    val primaryGroupName: String? = null,
    // Optional fields: a public profile the reader may not act on sends none of these.
    val location: String? = null,
    val website: String? = null,
    @SerialName("website_name")
    val websiteName: String? = null,
    @SerialName("can_send_private_message_to_user")
    val canSendPrivateMessage: Boolean = false,
    @SerialName("can_follow")
    val canFollow: Boolean = false,
    @SerialName("is_followed")
    val isFollowed: Boolean = false,
    @SerialName("can_mute_user")
    val canMuteUser: Boolean = false,
    @SerialName("can_ignore_user")
    val canIgnoreUser: Boolean = false,
    val muted: Boolean = false,
    val ignored: Boolean = false,
)

@Serializable
data class UserSummaryResponseDto(
    @SerialName("user_summary")
    val userSummary: UserSummaryDto = UserSummaryDto(),
    /** The badge definitions the summary's own grants refer to; the same join as user-badges. */
    val badges: List<BadgeDto> = emptyList(),
)

@Serializable
data class UserSummaryDto(
    @SerialName("topic_count")
    val topicCount: Int = 0,
    @SerialName("post_count")
    val postCount: Int = 0,
    @SerialName("likes_given")
    val likesGiven: Int = 0,
    @SerialName("likes_received")
    val likesReceived: Int = 0,
    @SerialName("days_visited")
    val daysVisited: Int = 0,
    @SerialName("posts_read_count")
    val postsReadCount: Int = 0,
    @SerialName("topics_entered")
    val topicsEntered: Int = 0,
    @SerialName("bookmark_count")
    val bookmarkCount: Int = 0,
    @SerialName("time_read")
    val timeRead: Int = 0,
)

@Serializable
data class UserBadgesResponseDto(
    @SerialName("user_badges")
    val userBadges: List<UserBadgeGrantDto> = emptyList(),
    val badges: List<BadgeDto> = emptyList(),
)

@Serializable
data class UserBadgeGrantDto(
    val id: Long,
    @SerialName("badge_id")
    val badgeId: Long = 0,
    @SerialName("granted_at")
    val grantedAt: String = "",
    val count: Int = 1,
    /** Some payloads (the profile endpoint's own badge block) flatten the definition in. */
    val name: String? = null,
    val description: String? = null,
    val icon: String? = null,
    @SerialName("image_url")
    val imageUrl: String? = null,
)

@Serializable
data class BadgeDto(
    val id: Long,
    val name: String = "",
    val description: String = "",
    val icon: String? = null,
    @SerialName("image_url")
    val imageUrl: String? = null,
)

@Serializable
data class CsrfResponseDto(
    val csrf: String,
)

internal fun EmojiGroupsDto.toEmojiCatalog(): Map<String, String> =
    values.flatten().associate { emoji -> emoji.name to emoji.url }

internal fun EmojiGroupsDto.toEmojiCategories(): List<DiscourseEmojiCategory> =
    entries.mapNotNull { (groupName, emojis) ->
        val items = emojis.mapNotNull { emoji ->
            val name = emoji.name.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val url = emoji.url.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            DiscourseEmoji(name = name, url = url)
        }.distinctBy { it.name }
        if (items.isEmpty()) {
            null
        } else {
            DiscourseEmojiCategory(
                id = groupName,
                title = emojiGroupTitle(groupName),
                emojis = items,
            )
        }
    }

private fun emojiGroupTitle(groupName: String): String =
    groupName.replace('_', ' ')
        .replace('&', '/')
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.replaceFirstChar { char -> char.titlecase() }
        }

internal fun ReactionUsersResponseDto.toModel(): List<PostReactionUsersGroup> =
    reactionUsers.map { group ->
        PostReactionUsersGroup(
            id = group.id,
            count = group.count,
            users = group.users.map { user ->
                PostReactionUser(
                    id = user.id ?: 0L,
                    username = user.username,
                    displayName = user.displayName.orEmpty(),
                    avatarTemplate = user.avatarTemplate,
                    animatedAvatarUrl = user.animatedAvatar.orEmpty(),
                    canUndo = user.canUndo,
                    createdAt = user.createdAt,
                )
            },
        )
    }

internal fun PollVotersResponseDto.toModel(): List<PostReactionUsersGroup> =
    voters.map { (optionId, voters) ->
        val users = voters.mapNotNull { voter -> voter.toPostReactionUser() }
            .distinctBy { user -> user.username }
        PostReactionUsersGroup(
            id = optionId,
            count = users.size,
            users = users,
        )
    }.filter { group -> group.id.isNotBlank() }

internal fun PollVoterDto.toPostReactionUser(): PostReactionUser? {
    val resolvedUsername = user?.username?.takeIf { it.isNotBlank() } ?: username.takeIf { it.isNotBlank() }
        ?: return null
    val resolvedDisplayName = user?.displayName?.takeIf { it.isNotBlank() }
        ?: displayName?.takeIf { it.isNotBlank() }
        ?: resolvedUsername
    val resolvedAvatar = user?.avatarTemplate?.takeIf { it.isNotBlank() }
        ?: avatarTemplate
    return PostReactionUser(
        id = user?.id ?: id ?: 0L,
        username = resolvedUsername,
        displayName = resolvedDisplayName,
        avatarTemplate = resolvedAvatar,
    )
}

internal fun TopicListResponseDto.toModel(): TopicListPage = TopicListPage(
    topics = topicList.topics.map { topic ->
        topic.toTopicSummary(
            usersById = users.associateBy(TopicListUserDto::id),
        )
    },
    moreTopicsUrl = topicList.moreTopicsUrl,
)

internal fun SearchResponseDto.toModel(): SearchResult {
    val usersById = users.mapNotNull { user ->
        user.id?.let { id ->
            TopicListUserDto(
                id = id,
                username = user.username,
                avatarTemplate = user.avatarTemplate,
                animatedAvatar = user.animatedAvatar,
            )
        }
    }.associateBy(TopicListUserDto::id)
    val usersByUsername = users.mapNotNull { user ->
        user.username.takeIf(String::isNotBlank)?.let { username ->
            username to posterUser(
                username = username,
                avatarTemplate = user.avatarTemplate,
                animatedAvatarUrl = user.animatedAvatar.orEmpty(),
            )
        }
    }.toMap()
    val topicHits = topics.map { topic ->
        topic.toTopicSummary(
            usersById = usersById,
            usersByUsername = usersByUsername,
        )
    }
    val postTopicHits = posts.map { post -> post.toTopicSummary() }
    return SearchResult(
        topics = topicHits,
        posts = postTopicHits,
        users = users.mapNotNull { user ->
            user.username.takeIf(String::isNotBlank)?.let { username ->
                SearchUserSummary(
                    username = username,
                    name = user.name,
                    avatarTemplate = user.avatarTemplate,
                )
            }
        },
        hasMore = groupedSearchResult?.let { result ->
            result.moreTopics == true || result.morePosts == true || result.moreFullPageResults == true
        } ?: false,
    )
}

internal fun MentionSearchResponseDto.toModel(): MentionSearchResult = MentionSearchResult(
    candidates = users.mapNotNull { user ->
        user.username.takeIf(String::isNotBlank)?.let { username ->
            MentionCandidate(
                username = username,
                displayName = user.name,
                avatarTemplate = user.avatarTemplate,
                priorityGroup = user.priorityGroup,
            )
        }
    } + groups.mapNotNull { group ->
        group.name.takeIf(String::isNotBlank)?.let { name ->
            MentionCandidate(
                username = name,
                displayName = group.fullName.ifBlank { name },
                isGroup = true,
                flairUrl = group.flairUrl,
                userCount = group.userCount,
            )
        }
    },
)

internal fun SiteResponseDto.toModel(): List<CategorySummary> = (
    categories.takeIf { it.isNotEmpty() } ?: categoryList?.categories.orEmpty()
    ).flatMap { category ->
    category.flattenedModels()
}

internal fun TagsResponseDto.toModel(): List<Tag> = tags.map { tag -> tag.toModel() }

private fun TagDto.toModel(): Tag = Tag(
    id = id,
    name = text.takeIf { it.isNotBlank() } ?: name,
    // A tag without a slug of its own is addressed as `<id>-tag`, which is what the site substitutes
    // into the answer and into the route.
    slug = slug.takeIf { it.isNotBlank() } ?: "$id-tag",
    count = count,
)

internal fun UserBookmarksResponseDto.toModel(): List<Bookmark> =
    (list?.bookmarks ?: bookmarks).map { bookmark -> bookmark.toModel() }

private fun UserBookmarkDto.toModel(): Bookmark = Bookmark(
    id = id,
    topicId = topicId,
    postId = bookmarkableId ?: 0L,
    postNumber = linkedPostNumber ?: 0,
    title = fancyTitle.takeIf { it.isNotBlank() } ?: title,
    // The site computes a bookmark's excerpt from the post's cooked HTML, so it arrives as markup
    // ("<a name=\"p-123\" class=\"anchor\">..."). A row draws one line of text, so the excerpt is
    // read as text before it reaches the cell.
    excerpt = excerpt.plainTextFromHtml(),
    createdAt = createdAt,
    authorUsername = user?.username.orEmpty(),
    authorDisplayName = user?.name.orEmpty(),
    authorAvatarTemplate = user?.avatarTemplate.orEmpty(),
)

internal fun CategoriesResponseDto.toModel(): List<CategorySummary> =
    categoryList?.categories.orEmpty().flatMap { category -> category.flattenedModels() }

internal fun TopicResponseDto.toModel(
    emojiCatalog: Map<String, String> = emptyMap(),
    emojiCategories: List<DiscourseEmojiCategory> = emptyList(),
): TopicDetail {
    val streamPostIds = postStream.stream.ifEmpty { postStream.posts.map { it.id } }
    return TopicDetail(
        id = id,
        title = title,
        slug = slug,
        highestPostNumber = highestPostNumber,
        lastReadPostNumber = lastReadPostNumber ?: 0,
        lastReadPostId = lastReadPostId,
        categoryId = categoryId,
        bookmarked = bookmarked,
        createdBy = details?.createdBy?.toModel(),
        notificationLevel = details?.notificationLevel ?: 1,
        posts = postStream.posts.map { post -> post.toModel(emojiCatalog = emojiCatalog) },
        participants = details?.participants.orEmpty().mapNotNull { participant ->
            participant.username.takeIf(String::isNotBlank)?.let { username ->
                MentionCandidate(
                    username = username,
                    displayName = participant.name.orEmpty(),
                    avatarTemplate = participant.avatarTemplate,
                    animatedAvatarUrl = participant.animatedAvatar.orEmpty(),
                    postCount = participant.postCount.coerceAtLeast(0),
                )
            }
        },
        createdAt = createdAt,
        lastPostedAt = lastPostedAt,
        viewCount = views,
        tags = tags,
        validReactions = validReactions,
        emojiCatalog = emojiCatalog,
        emojiCategories = emojiCategories,
        totalPosts = postsCount.takeIf { it > 0 } ?: streamPostIds.size,
        streamPostIds = streamPostIds,
        suggestedTopics = suggestedTopics.map { topic -> topic.toTopicSummary(usersById = emptyMap()) },
    )
}

internal fun TopicPostsResponseDto.toModel(
    emojiCatalog: Map<String, String> = emptyMap(),
): TopicPostsPage =
    TopicPostsPage(
        posts = postStream.posts.map { post -> post.toModel(emojiCatalog = emojiCatalog) },
        suggestedTopics = suggestedTopics.map { topic -> topic.toTopicSummary(usersById = emptyMap()) },
    )

private fun TopicCreatedByDto.toModel(): MentionCandidate? {
    val name = username.takeIf(String::isNotBlank) ?: return null
    return MentionCandidate(
        username = name,
        displayName = this.name.orEmpty(),
        avatarTemplate = avatarTemplate,
        animatedAvatarUrl = animatedAvatar.orEmpty(),
    )
}

internal fun UserProfileResponseDto.toModel(): UserProfile = UserProfile(
    username = user.username,
    displayName = user.displayName.orEmpty(),
    bio = user.bio.orEmpty(),
    avatarTemplate = user.avatarTemplate,
    animatedAvatarUrl = user.animatedAvatar.orEmpty(),
    flairName = user.flairName.orEmpty(),
    flairUrl = user.flairUrl.orEmpty(),
    flairBgColor = user.flairBgColor.orEmpty(),
    flairColor = user.flairColor.orEmpty(),
    trustLevel = user.trustLevel,
    title = user.title.orEmpty(),
    badgeCount = user.badgeCount,
    timeRead = user.timeRead,
    recentTimeRead = user.recentTimeRead,
    createdAt = user.createdAt.orEmpty(),
    lastSeenAt = user.lastSeenAt.orEmpty(),
    lastPostedAt = user.lastPostedAt.orEmpty(),
    profileViewCount = user.profileViewCount,
    admin = user.admin,
    moderator = user.moderator,
    gamificationScore = user.gamificationScore,
    cakeDate = user.cakeDate.orEmpty(),
    totalFollowers = user.totalFollowers,
    totalFollowing = user.totalFollowing,
    acceptedAnswers = user.acceptedAnswers,
    primaryGroupName = user.primaryGroupName.orEmpty(),
    location = user.location.orEmpty(),
    website = user.website.orEmpty(),
    websiteName = user.websiteName.orEmpty(),
    canSendPrivateMessage = user.canSendPrivateMessage,
    canFollow = user.canFollow,
    isFollowed = user.isFollowed,
    canMuteUser = user.canMuteUser,
    canIgnoreUser = user.canIgnoreUser,
    muted = user.muted,
    ignored = user.ignored,
)

internal fun UserSummaryResponseDto.toModel(): UserSummary = UserSummary(
    topicCount = userSummary.topicCount,
    postCount = userSummary.postCount,
    likesGiven = userSummary.likesGiven,
    likesReceived = userSummary.likesReceived,
    daysVisited = userSummary.daysVisited,
    postsReadCount = userSummary.postsReadCount,
    topicsEntered = userSummary.topicsEntered,
    bookmarkCount = userSummary.bookmarkCount,
    timeRead = userSummary.timeRead,
)

/**
 * The badges list joins each grant with its definition by `badge_id`. A payload that already
 * carries the definition on the grant (the profile endpoint's flattened badge block) is read as
 * it is; a grant whose definition is missing is dropped rather than drawn as a nameless row.
 */
internal fun UserBadgesResponseDto.toModel(): List<UserBadge> {
    val definitions = badges.associateBy(BadgeDto::id)
    return userBadges.mapNotNull { grant ->
        val definition = definitions[grant.badgeId]
        val name = grant.name?.takeIf(String::isNotBlank) ?: definition?.name.orEmpty()
        if (name.isBlank()) {
            return@mapNotNull null
        }
        UserBadge(
            id = grant.id,
            badgeId = grant.badgeId,
            name = name,
            description = (grant.description ?: definition?.description.orEmpty()).plainTextFromHtml(),
            icon = grant.icon ?: definition?.icon.orEmpty(),
            imageUrl = grant.imageUrl ?: definition?.imageUrl.orEmpty(),
            grantedAt = grant.grantedAt,
            count = grant.count.coerceAtLeast(1),
        )
    }
}

internal fun PostDto.toModel(
    emojiCatalog: Map<String, String> = emptyMap(),
): Post {
    return Post(
        id = id,
        postNumber = postNumber,
        replyToPostNumber = replyToPostNumber,
        authorUsername = username,
        authorDisplayName = displayName.orEmpty(),
        authorAvatarTemplate = avatarTemplate,
        authorAnimatedAvatarUrl = animatedAvatar.orEmpty(),
        authorTitle = userTitle.orEmpty(),
        authorFlairName = flairName.orEmpty(),
        authorFlairUrl = flairUrl.orEmpty(),
        authorFlairBgColor = flairBgColor.orEmpty(),
        authorFlairColor = flairColor.orEmpty(),
        createdAt = createdAt,
        updatedAt = updatedAt,
        editCount = (version - 1).coerceAtLeast(0),
        replyCount = replyCount,
        currentUserReaction = currentUserReaction?.id,
        reactionUsersCount = reactionUsersCount,
        reactions = reactions.map { reaction ->
            PostReaction(
                id = reaction.id,
                count = reaction.count,
                iconUrl = reaction.icon ?: emojiCatalog[reaction.id],
                reacted = reaction.id == currentUserReaction?.id,
            )
        },
        isBookmarked = bookmarked == true,
        yours = yours == true,
        canEdit = canEdit == true,
        canDelete = canDelete == true,
        canRecover = canRecover == true,
        isDeleted = deletedAt != null,
        deletedAt = deletedAt.orEmpty(),
        raw = raw.orEmpty(),
        cookedHtml = cookedHtml,
        imageUrls = Regex("<img[^>]+src=\\\"([^\\\"]+)\\\"")
            .findAll(cookedHtml)
            .map { match -> match.groupValues[1] }
            .toList(),
        // The rendered body no longer needs the poll's state written into it: the poll is drawn
        // from this model, and the cooked HTML only says where the poll sits.
        polls = polls.map { poll -> poll.toModel(votes = pollsVotes[poll.name].orEmpty()) },
    )
}

/**
 * A write that created a topic - a new topic or a private message - answers with the post and the
 * topic the site made for it. The topic id is the whole point: a private message has no list to
 * return to, so its screen opens the topic the writer just created. A site that answers without one
 * left nothing to open, and that is a failure rather than a silent success.
 */
internal fun PostDto.toCreatedTopic(): CreatedTopic {
    val createdTopicId = topicId
        ?: throw IllegalStateException("the site answered a created post without a topic id")
    return CreatedTopic(
        topicId = createdTopicId,
        slug = topicSlug.orEmpty(),
        title = topicTitle.orEmpty(),
    )
}

internal fun DiscoursePollDto.toModel(
    votes: List<PollVoteDto> = emptyList(),
): PostPoll = PostPoll(
    id = id,
    name = name,
    type = type.orEmpty().ifBlank { "regular" },
    status = status.orEmpty().ifBlank { "open" },
    results = results.orEmpty().ifBlank { "always" },
    title = title.orEmpty(),
    options = options.map { option ->
        PostPollOption(
            id = option.id,
            // An option's html is markup ("<strong>Tool B</strong>"); the poll button draws text.
            label = Jsoup.parse(option.html).text().ifBlank { option.id },
            votes = option.votes,
            chosen = option.chosen,
            rank = option.rank,
        )
    },
    voters = voters ?: 0,
    chartType = chartType.orEmpty(),
    isPublic = isPublic == true,
    min = min,
    max = max,
    step = step,
    closedAt = closedAt.orEmpty(),
    closedBy = closedBy.orEmpty(),
    votes = votes.map { vote -> PostPollVote(digest = vote.digest, rank = vote.rank) },
)


private fun TopicDto.toTopicSummary(
    usersById: Map<Long, TopicListUserDto>,
    usersByUsername: Map<String, PosterUser> = emptyMap(),
): TopicSummary {
    val posterUser = posters.primaryPosterOrNull()?.let { poster ->
        poster.user?.let { user ->
            posterUser(
                username = user.username,
                avatarTemplate = user.avatarTemplate,
                animatedAvatarUrl = user.animatedAvatar.orEmpty(),
            )
        } ?: poster.userId
            ?.let(usersById::get)
            ?.let { user ->
                posterUser(
                    username = user.username,
                    avatarTemplate = user.avatarTemplate,
                    animatedAvatarUrl = user.animatedAvatar.orEmpty(),
                )
            }
    }
    val recentPosterUser = posters.mostRecentPosterOrNull()?.let { poster ->
        poster.user?.let { user ->
            posterUser(
                username = user.username,
                avatarTemplate = user.avatarTemplate,
                animatedAvatarUrl = user.animatedAvatar.orEmpty(),
            )
        } ?: poster.userId
            ?.let(usersById::get)
            ?.let { user ->
                posterUser(
                    username = user.username,
                    avatarTemplate = user.avatarTemplate,
                    animatedAvatarUrl = user.animatedAvatar.orEmpty(),
                )
            }
    }
    val resolvedPosterUser = posterUser?.let { embedded ->
        usersByUsername[embedded.username]?.let { responseUser ->
            embedded.copy(
                avatarTemplate = embedded.avatarTemplate.ifBlank { responseUser.avatarTemplate },
                animatedAvatarUrl = embedded.animatedAvatarUrl.ifBlank { responseUser.animatedAvatarUrl },
            )
        } ?: embedded
    } ?: lastPosterUsername
        ?.takeIf(String::isNotBlank)
        ?.let(usersByUsername::get)
    val resolvedRecentPosterUser = recentPosterUser?.let { embedded ->
        usersByUsername[embedded.username]?.let { responseUser ->
            embedded.copy(
                avatarTemplate = embedded.avatarTemplate.ifBlank { responseUser.avatarTemplate },
                animatedAvatarUrl = embedded.animatedAvatarUrl.ifBlank { responseUser.animatedAvatarUrl },
            )
        } ?: embedded
    }

    return TopicSummary(
        id = id,
        title = title.ifBlank { fancyTitle },
        authorUsername = resolvedPosterUser?.username.orEmpty(),
        authorAvatarTemplate = resolvedPosterUser?.avatarTemplate.orEmpty(),
        authorAnimatedAvatarUrl = resolvedPosterUser?.animatedAvatarUrl.orEmpty(),
        imageUrl = imageUrl,
        slug = slug,
        replyCount = replyCount,
        viewCount = views,
        likeCount = likeCount,
        lastPostedAt = lastPostedAt,
        categoryId = categoryId,
        lastPosterUsername = lastPosterUsername.orEmpty().ifBlank { resolvedRecentPosterUser?.username.orEmpty() },
        isPinned = pinned || pinnedGlobally,
        isPinnedGlobally = pinnedGlobally,
        isHot = isHot,
        tags = tags,
        excerpt = excerpt,
        unreadCount = if (notificationLevel != null && notificationLevel >= 2) unreadPosts else 0,
        isUnseen = unseen,
        isMuted = notificationLevel == 0,
    )
}

private fun SearchPostDto.toTopicSummary(): TopicSummary = TopicSummary(
    id = topicId,
    title = topicTitle,
    authorUsername = username,
    authorAvatarTemplate = avatarTemplate,
    authorAnimatedAvatarUrl = animatedAvatar.orEmpty(),
    likeCount = likeCount,
    lastPostedAt = createdAt,
    categoryId = categoryId,
    lastPosterUsername = username,
    excerpt = blurb,
    targetPostNumber = postNumber,
    targetPostId = id,
    rowIdentity = "search-post:$id",
)

@Serializable
data class UserActionsResponseDto(
    @SerialName("user_actions")
    val userActions: List<UserActionDto> = emptyList(),
)

@Serializable
data class NotificationDataDto(
    @SerialName("topic_title")
    val topicTitle: String? = null,
    @SerialName("display_username")
    val displayUsername: String? = null,
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("original_username")
    val originalUsername: String? = null,
    val username: String? = null,
    val username2: String? = null,
    val count: Int? = null,
    @SerialName("consolidated_count")
    val consolidatedCount: Int? = null,
    @SerialName("badge_name")
    val badgeName: String? = null,
    val message: String? = null,
)

@Serializable
data class NotificationDto(
    val id: Long,
    @SerialName("notification_type")
    val notificationType: Int = 0,
    val read: Boolean = false,
    @SerialName("created_at")
    val createdAt: String = "",
    @SerialName("post_number")
    val postNumber: Int? = null,
    @SerialName("topic_id")
    val topicId: Long? = null,
    // A notification about no topic at all - a badge, consolidated likes - carries no slug either.
    val slug: String? = null,
    @SerialName("fancy_title")
    val fancyTitle: String? = null,
    val data: NotificationDataDto = NotificationDataDto(),
    @SerialName("acting_user_name")
    val actingUserName: String? = null,
    @SerialName("acting_user_avatar_template")
    val actingUserAvatarTemplate: String? = null,
)

@Serializable
data class NotificationsResponseDto(
    val notifications: List<NotificationDto> = emptyList(),
    @SerialName("total_rows_notifications")
    val totalRowsNotifications: Int = 0,
    @SerialName("seen_notification_id")
    val seenNotificationId: Long = 0,
    @SerialName("load_more_notifications")
    val loadMoreNotifications: String? = null,
)

@Serializable
data class UserActionDto(
    val id: Long? = null,
    @SerialName("post_id")
    val postId: Long? = null,
    @SerialName("topic_id")
    val topicId: Long = 0,
    val title: String = "",
    val excerpt: String = "",
    val username: String = "",
    @SerialName("avatar_template")
    val avatarTemplate: String = "",
    @SerialName("created_at")
    val createdAt: String = "",
    @SerialName("category_id")
    val categoryId: Int? = null,
    val slug: String = "",
    @SerialName("post_number")
    val postNumber: Int? = null,
)

// Maps a Discourse user-action feed to topic-summary rows so the profile feed can
// reuse TopicListAdapter/TopicCell. An action's topic_id may repeat (e.g. several
// replies in one topic); we keep each action as its own row to preserve order.
internal fun UserActionsResponseDto.toModel(): UserActionPage = UserActionPage(
    rawCount = userActions.size,
    items = userActions
        .filter { it.topicId > 0 && it.title.isNotBlank() }
        .map { action ->
            TopicSummary(
                id = action.topicId,
                title = action.title,
                authorUsername = action.username,
                authorAvatarTemplate = action.avatarTemplate,
                slug = action.slug,
                lastPostedAt = action.createdAt,
                categoryId = action.categoryId,
                lastPosterUsername = action.username,
                // The site computes some excerpts from the post's cooked HTML; a row draws one
                // line of text, so the markup comes off the same way a bookmark's excerpt does.
                excerpt = action.excerpt.plainTextFromHtml(),
                targetPostNumber = action.postNumber?.takeIf { it > 0 },
                targetPostId = action.postId?.takeIf { it > 0 },
                rowIdentity = action.activityRowIdentity(),
            )
        },
)

private fun UserActionDto.activityRowIdentity(): String = when {
    postId != null && postId > 0 -> "post:$postId"
    id != null && id > 0 -> "action:$id"
    else -> "fallback:$topicId:${postNumber ?: 0}:$createdAt:$username:$excerpt"
}

internal fun NotificationsResponseDto.toModel(): NotificationPage = NotificationPage(
    items = notifications.map(NotificationDto::toModel),
    totalRows = totalRowsNotifications,
)

/**
 * The actor is `data.display_username` and its fallbacks, never `user_id`: the site addresses a
 * notification to the reader and keeps the actor in the data. A consolidated notification (several
 * replies the site folded into one row) has no single actor - its `display_username` is a phrase in
 * the site's own language - so the count travels instead and the row names the count.
 */
internal fun NotificationDto.toModel(): NotificationItem {
    val consolidated = data.consolidatedCount ?: 0
    val actor = if (consolidated > 1) {
        ""
    } else {
        data.displayUsername.orAlternate(data.originalUsername, data.username)
    }
    return NotificationItem(
        id = id,
        type = notificationType,
        isRead = read,
        createdAt = createdAt,
        topicId = topicId ?: 0,
        postNumber = postNumber?.takeIf { it > 0 },
        title = fancyTitle.orAlternate(data.topicTitle).orEmpty(),
        actorUsername = actor,
        actorName = if (consolidated > 1) "" else actingUserName.orEmpty(),
        actorAvatarTemplate = if (consolidated > 1) "" else actingUserAvatarTemplate.orEmpty(),
        secondUsername = if (consolidated > 1) "" else data.username2.orEmpty(),
        count = data.count ?: 0,
        badgeName = data.badgeName.orEmpty(),
        consolidatedCount = consolidated,
    )
}

private fun String?.orAlternate(vararg alternatives: String?): String =
    alternatives.fold(this) { chosen, candidate -> chosen?.takeIf(String::isNotBlank) ?: candidate }
        .orEmpty()

private fun SiteCategoryDto.flattenedModels(): List<CategorySummary> {
    return buildList {
        add(toModel())
        subcategoryList.forEach { category ->
            addAll(category.flattenedModels())
        }
    }
}

private fun SiteCategoryDto.toModel(): CategorySummary = CategorySummary(
    id = id,
    name = name,
    slug = slug,
    colorHex = color,
    textColorHex = textColor,
    parentCategoryId = parentCategoryId,
)

private data class PosterUser(
    val username: String,
    val avatarTemplate: String,
    val animatedAvatarUrl: String,
)

private fun List<PosterDto>.primaryPosterOrNull(): PosterDto? {
    return firstOrNull { poster ->
        poster.description.contains("original poster", ignoreCase = true)
    } ?: firstOrNull()
}

private fun List<PosterDto>.mostRecentPosterOrNull(): PosterDto? {
    return firstOrNull { poster ->
        poster.description.contains("most recent poster", ignoreCase = true)
    }
}

private fun posterUser(
    username: String,
    avatarTemplate: String,
    animatedAvatarUrl: String,
): PosterUser = PosterUser(
    username = username,
    avatarTemplate = avatarTemplate,
    animatedAvatarUrl = animatedAvatarUrl,
)

/** A `polls_votes` entry is a plain digest, and a `ranked_choice` one is a `{digest, rank}` object. */
private object PollVoteDtoSerializer : kotlinx.serialization.KSerializer<PollVoteDto> {
    override val descriptor: SerialDescriptor = String.serializer().descriptor

    override fun deserialize(decoder: Decoder): PollVoteDto {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return PollVoteDto(decoder.decodeString())
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> PollVoteDto(element.content)
            is JsonObject -> PollVoteDto(
                digest = element["digest"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                rank = element["rank"]?.jsonPrimitive?.intOrNull,
            )
            else -> PollVoteDto("")
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: PollVoteDto,
    ) {
        encoder.encodeString(value.digest)
    }
}

private object TopicTagListSerializer : kotlinx.serialization.KSerializer<List<String>> {
    private val delegate = ListSerializer(String.serializer())

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<String> {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return decoder.decodeSerializableValue(delegate)
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonArray -> element.mapNotNull(::topicTagNameOrNull)
            else -> emptyList()
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: List<String>,
    ) {
        encoder.encodeSerializableValue(delegate, value)
    }
}

private fun topicTagNameOrNull(element: JsonElement): String? = when (element) {
    is JsonPrimitive -> element.contentOrNull?.takeIf(String::isNotBlank)
    is JsonObject -> {
        element["name"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
            ?: element["slug"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
    }
    else -> null
}

// ---------------------------------------------------------------------------------------------
// Discourse chat (public channels)
// ---------------------------------------------------------------------------------------------

@Serializable
data class ChatChannelsResponseDto(
    val channels: List<ChatChannelDto> = emptyList(),
    val meta: ChatChannelsMetaDto? = null,
)

@Serializable
data class ChatChannelsMetaDto(
    @SerialName("load_more_url")
    val loadMoreUrl: String? = null,
)

@Serializable
data class ChatChannelResponseDto(
    val channel: ChatChannelDto? = null,
)

@Serializable
data class ChatChannelDto(
    val id: Long,
    val title: String = "",
    val slug: String = "",
    val description: String? = null,
    val status: String = "open",
    @SerialName("memberships_count")
    val membershipsCount: Int = 0,
    @SerialName("last_message")
    val lastMessage: ChatLastMessageDto? = null,
    @SerialName("current_user_membership")
    val currentUserMembership: ChatMembershipDto? = null,
    val meta: ChatChannelMetaDto? = null,
)

@Serializable
data class ChatChannelMetaDto(
    @SerialName("can_join_chat_channel")
    val canJoin: Boolean = false,
    @SerialName("message_bus_last_ids")
    val messageBusLastIds: ChatMessageBusLastIdsDto? = null,
)

@Serializable
data class ChatMessageBusLastIdsDto(
    @SerialName("channel_message_bus_last_id")
    val channelMessageBusLastId: Long? = null,
    @SerialName("new_messages")
    val newMessages: Long? = null,
)

@Serializable
data class ChatLastMessageDto(
    val id: Long = 0,
    val message: String = "",
    val cooked: String = "",
    @SerialName("created_at")
    val createdAt: String = "",
    @SerialName("chat_channel_id")
    val chatChannelId: Long? = null,
    val user: ChatUserDto? = null,
)

/** The reader's membership, as the channel serializer and the membership serializer both carry it. */
@Serializable
data class ChatMembershipDto(
    val following: Boolean = false,
    @SerialName("last_read_message_id")
    val lastReadMessageId: Long? = null,
)

@Serializable
data class ChatMembershipResponseDto(
    val membership: ChatMembershipDto? = null,
)

/** A chat participant, as `MessageUserSerializer` / `BasicUserSerializer` write one. */
@Serializable
data class ChatUserDto(
    val id: Long? = null,
    val username: String = "",
    @SerialName("name")
    val name: String? = null,
    @SerialName("avatar_template")
    val avatarTemplate: String = "",
)

@Serializable
data class ChatMessageDto(
    val id: Long,
    val message: String = "",
    val cooked: String = "",
    @SerialName("created_at")
    val createdAt: String = "",
    @SerialName("chat_channel_id")
    val chatChannelId: Long? = null,
    val user: ChatUserDto? = null,
    @SerialName("in_reply_to")
    val inReplyTo: ChatInReplyToDto? = null,
)

@Serializable
data class ChatInReplyToDto(
    val id: Long,
    val cooked: String = "",
    val excerpt: String = "",
    val user: ChatUserDto? = null,
)

@Serializable
data class ChatMessagesResponseDto(
    val messages: List<ChatMessageDto> = emptyList(),
    val meta: ChatMessagesMetaDto? = null,
)

@Serializable
data class ChatMessagesMetaDto(
    @SerialName("can_load_more_past")
    val canLoadMorePast: Boolean = false,
    @SerialName("can_load_more_future")
    val canLoadMoreFuture: Boolean = false,
    @SerialName("target_message_id")
    val targetMessageId: Long? = null,
)

/** `{success:"OK", message_id}` - a send's acknowledgement, which is not the cooked message. */
@Serializable
data class ChatWriteAckDto(
    val success: String = "",
    @SerialName("message_id")
    val messageId: Long? = null,
)

/** `{success:"OK"}` - the site's acknowledgement of a read-position write. */
@Serializable
data class ChatAckDto(
    val success: String = "",
)

internal fun ChatChannelsResponseDto.toModel(): ChatChannelPage = ChatChannelPage(
    channels = channels.map(ChatChannelDto::toModel),
    loadMoreUrl = meta?.loadMoreUrl,
)

internal fun ChatChannelResponseDto.toModel(): ChatChannel? = channel?.toModel()

private fun ChatChannelDto.toModel(): ChatChannel = ChatChannel(
    id = id,
    title = title,
    slug = slug,
    description = description.orEmpty(),
    status = status.ifBlank { "open" },
    membershipsCount = membershipsCount,
    lastMessage = lastMessage?.toModel(),
    currentUserMembership = currentUserMembership?.toModel(),
    canJoin = meta?.canJoin == true,
    channelMessageBusLastId = meta?.messageBusLastIds?.channelMessageBusLastId,
    newMessagesBusLastId = meta?.messageBusLastIds?.newMessages,
)

private fun ChatLastMessageDto.toModel(): ChatLastMessage = ChatLastMessage(
    id = id,
    message = message,
    cooked = cooked,
    createdAt = createdAt,
    user = user?.toModel(),
)

internal fun ChatMembershipDto.toModel(): ChatMembership = ChatMembership(
    following = following,
    lastReadMessageId = lastReadMessageId,
)

private fun ChatUserDto.toModel(): ChatUser = ChatUser(
    id = id ?: 0L,
    username = username,
    name = name.orEmpty(),
    avatarTemplate = avatarTemplate,
)

internal fun ChatMessageDto.toModel(): ChatMessage = ChatMessage(
    id = id,
    message = message,
    cooked = cooked,
    createdAt = createdAt,
    channelId = chatChannelId ?: 0L,
    user = user?.toModel(),
    inReplyTo = inReplyTo?.toModel(),
)

private fun ChatInReplyToDto.toModel(): ChatReplyTarget = ChatReplyTarget(
    id = id,
    cooked = cooked,
    excerpt = excerpt,
    user = user?.toModel(),
)

internal fun ChatMessagesResponseDto.toModel(): ChatMessagePage = ChatMessagePage(
    messages = messages.map(ChatMessageDto::toModel),
    canLoadMorePast = meta?.canLoadMorePast == true,
    canLoadMoreFuture = meta?.canLoadMoreFuture == true,
    targetMessageId = meta?.targetMessageId,
)

/** The payload of a `/chat/{id}/new-messages` event: `{type:"channel", message:{...}}`. */
@Serializable
internal data class ChatMessageBusNewMessagesDto(
    val type: String = "",
    @SerialName("channel_id")
    val channelId: Long = 0L,
    val message: ChatMessageDto? = null,
)

@Serializable
internal data class ChatMessageBusEventDto(
    val type: String = "",
    @SerialName("chat_message")
    val chatMessage: ChatMessageDto? = null,
    @SerialName("deleted_id")
    val deletedId: Long? = null,
)

/**
 * One decoded `/chat/{id}` event: the site's own `type` and, for the message-carrying types, the
 * message itself (or the id a `delete` names). A payload the client cannot decode answers null, so a
 * live event it does not understand is dropped rather than drawn - explicit refresh is the fallback.
 */
data class ChatBusEvent(
    val type: String,
    val message: ChatMessage? = null,
    val deletedId: Long? = null,
)

/** The `chat_message` of a `/chat/{id}` event, decoded with the HTTP read's own DTO. */
fun decodeChatMessageBusEvent(element: JsonElement): ChatBusEvent? = runCatching {
    val dto = NetworkJson.instance.decodeFromJsonElement(ChatMessageBusEventDto.serializer(), element)
    ChatBusEvent(
        type = dto.type,
        message = dto.chatMessage?.toModel(),
        deletedId = dto.deletedId,
    )
}.getOrNull()

/** The `message` of a `/chat/{id}/new-messages` event, decoded with the HTTP read's own DTO. */
fun decodeChatNewMessagesEvent(element: JsonElement): ChatBusEvent? = runCatching {
    val dto = NetworkJson.instance.decodeFromJsonElement(ChatMessageBusNewMessagesDto.serializer(), element)
    ChatBusEvent(type = dto.type, message = dto.message?.toModel())
}.getOrNull()
