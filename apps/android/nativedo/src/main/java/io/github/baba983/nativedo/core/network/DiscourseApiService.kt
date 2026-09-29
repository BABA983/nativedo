package io.github.baba983.nativedo.core.network

import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.PostReaction
import io.github.baba983.nativedo.core.model.PostReactionUser
import io.github.baba983.nativedo.core.model.PostReactionUsersGroup
import io.github.baba983.nativedo.core.model.CategorySummary
import io.github.baba983.nativedo.core.model.DiscourseEmoji
import io.github.baba983.nativedo.core.model.DiscourseEmojiCategory
import io.github.baba983.nativedo.core.model.SearchResult
import io.github.baba983.nativedo.core.model.SearchUserSummary
import io.github.baba983.nativedo.core.model.MentionCandidate
import io.github.baba983.nativedo.core.model.MentionSearchResult
import io.github.baba983.nativedo.core.model.TopicDetail
import io.github.baba983.nativedo.core.model.TopicListPage
import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.core.model.UserActionPage
import io.github.baba983.nativedo.core.model.UserProfile
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

    @GET("top.json")
    suspend fun topTopics(@Query("page") page: Int): TopicListResponseDto

    @GET("search.json")
    suspend fun search(
        @Query("q") query: String,
        @Query("page") page: Int,
        @Query("type_filter") typeFilter: String? = null,
    ): SearchResponseDto

    @GET("u/search/users")
    suspend fun searchMentionUsers(
        @Query("term") term: String,
        @Query("topic_id") topicId: Long,
        @Query("include_groups") includeGroups: Boolean = true,
        @Query("limit") limit: Int = 6,
    ): MentionSearchResponseDto

    @GET("site.json")
    suspend fun site(): SiteResponseDto

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

    @FormUrlEncoded
    @POST("posts.json")
    suspend fun createPost(
        @Field("topic_id") topicId: Long,
        @Field("raw") raw: String,
        @Field("reply_to_post_number") replyToPostNumber: Int?,
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
    ): ResponseBody

    @FormUrlEncoded
    @HTTP(method = "DELETE", path = "polls/vote", hasBody = true)
    suspend fun removePollVote(
        @Field("post_id") postId: Long,
        @Field("poll_name") pollName: String,
        @Header("X-CSRF-Token") csrfToken: String,
    ): ResponseBody

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
)

@Serializable
data class TopicDetailsDto(
    val participants: List<TopicParticipantDto> = emptyList(),
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
    val pollsVotes: Map<String, List<String>> = emptyMap(),
)

@Serializable
data class DiscoursePollDto(
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
    val options: List<DiscoursePollOptionDto> = emptyList(),
)

@Serializable
data class DiscoursePollOptionDto(
    val id: String,
    val html: String = "",
    val votes: Int? = null,
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
    )
}

internal fun TopicPostsResponseDto.toModel(
    emojiCatalog: Map<String, String> = emptyMap(),
): List<Post> = postStream.posts.map { post -> post.toModel(emojiCatalog = emojiCatalog) }

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
)

internal fun PostDto.toModel(
    emojiCatalog: Map<String, String> = emptyMap(),
): Post {
    val enrichedCookedHtml = cookedHtml.withSerializedPollData(polls, pollsVotes)
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
        cookedHtml = enrichedCookedHtml,
        imageUrls = Regex("<img[^>]+src=\\\"([^\\\"]+)\\\"")
            .findAll(enrichedCookedHtml)
            .map { match -> match.groupValues[1] }
            .toList(),
    )
}

internal fun String.withSerializedPollData(
    polls: List<DiscoursePollDto>,
    pollsVotes: Map<String, List<String>> = emptyMap(),
): String {
    if (polls.isEmpty() && pollsVotes.isEmpty()) {
        return this
    }
    val body = Jsoup.parseBodyFragment(this).body()
    var changed = false
    if (polls.isEmpty()) {
        for ((pollName, optionIds) in pollsVotes) {
            val pollElement = body.select("div.poll")
                .firstOrNull { element -> element.attr("data-poll-name") == pollName }
                ?: continue
            val list = pollElement.selectFirst(".poll-container ul") ?: continue
            val selectedOptionIds = optionIds.toSet()
            for (optionElement in list.select("li[data-poll-option-id]")) {
                val optionId = optionElement.attr("data-poll-option-id")
                if (optionId in selectedOptionIds) {
                    optionElement.addClass("chosen")
                } else {
                    optionElement.removeClass("chosen")
                    optionElement.removeClass("selected")
                }
                changed = true
            }
        }
        return if (changed) body.html() else this
    }
    for (poll in polls) {
        val pollElement = body.select("div.poll")
            .firstOrNull { element -> element.attr("data-poll-name") == poll.name }
            ?: continue
        poll.type?.takeIf { it.isNotBlank() }?.let { pollElement.attr("data-poll-type", it) }
        poll.status?.takeIf { it.isNotBlank() }?.let { pollElement.attr("data-poll-status", it) }
        poll.results?.takeIf { it.isNotBlank() }?.let { pollElement.attr("data-poll-results", it) }
        poll.isPublic?.let { pollElement.attr("data-poll-public", it.toString()) }
        poll.chartType?.takeIf { it.isNotBlank() }?.let { pollElement.attr("data-poll-charttype", it) }
        poll.title?.takeIf { it.isNotBlank() }?.let { pollElement.attr("data-poll-title", it) }
        val selectedOptionIds = pollsVotes[poll.name].orEmpty().toSet()
        val container = pollElement.selectFirst(".poll-container") ?: pollElement.appendElement("div").addClass("poll-container")
        val list = container.selectFirst("ul") ?: container.appendElement("ul")
        val existingById = list.select("li[data-poll-option-id]")
            .associateBy { option -> option.attr("data-poll-option-id") }
        for (optionElement in list.select("li[data-poll-option-id]")) {
            val optionId = optionElement.attr("data-poll-option-id")
            if (optionId in selectedOptionIds) {
                optionElement.addClass("chosen")
            } else {
                optionElement.removeClass("chosen")
                optionElement.removeClass("selected")
            }
        }
        for (option in poll.options) {
            if (option.id.isBlank()) {
                continue
            }
            val item = existingById[option.id]
                ?: list.appendElement("li").attr("data-poll-option-id", option.id)
            item.attr("data-poll-option-id", option.id)
            if (option.id in selectedOptionIds) {
                item.addClass("chosen")
            } else {
                item.removeClass("chosen")
                item.removeClass("selected")
            }
            val labelElement = item.selectFirst(".poll-option-label")
                ?: item.prependElement("span").addClass("poll-option-label")
            if (labelElement.html().isBlank() && option.html.isNotBlank()) {
                labelElement.html(option.html)
            }
            if (option.votes != null) {
                val votesElement = item.selectFirst(".poll-option-votes, .votes")
                    ?: item.appendElement("span").addClass("poll-option-votes")
                votesElement.text(option.votes.toString())
            }
        }
        val totalVoters = poll.voters?.takeIf { it > 0 }
            ?: poll.options.mapNotNull { it.votes }.sum().takeIf { it > 0 }
        if (totalVoters != null) {
            val pollInfo = pollElement.selectFirst(".poll-info")
                ?: pollElement.appendElement("div").addClass("poll-info")
            val counts = pollInfo.selectFirst(".poll-info_counts")
                ?: pollInfo.appendElement("div").addClass("poll-info_counts")
            val countElement = counts.selectFirst(".poll-info_counts-count")
                ?: counts.appendElement("div").addClass("poll-info_counts-count")
            val numberElement = countElement.selectFirst(".info-number")
                ?: countElement.appendElement("span").addClass("info-number")
            numberElement.text(totalVoters.toString())
        }
        changed = true
    }
    return if (changed) body.html() else this
}

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
                excerpt = action.excerpt,
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
