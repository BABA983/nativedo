package io.github.baba983.nativedo.core.network

import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.PostReaction
import io.github.baba983.nativedo.core.model.PostReactionUser
import io.github.baba983.nativedo.core.model.PostReactionUsersGroup
import io.github.baba983.nativedo.core.model.CategorySummary
import io.github.baba983.nativedo.core.model.SearchResult
import io.github.baba983.nativedo.core.model.SearchUserSummary
import io.github.baba983.nativedo.core.model.TopicDetail
import io.github.baba983.nativedo.core.model.TopicListPage
import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.core.model.UserProfile
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
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.PUT
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
    ): SearchResponseDto

    @GET("site.json")
    suspend fun site(): SiteResponseDto

    @GET("t/{id}.json")
    suspend fun topic(@Path("id") id: Long): TopicResponseDto

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

    @GET("emojis.json")
    suspend fun emojis(): EmojiGroupsDto

    @GET("u/{username}.json")
    suspend fun profile(@Path("username") username: String): UserProfileResponseDto

    @GET("session/current.json")
    suspend fun currentSession(): CurrentSessionResponseDto

    @GET("session/csrf")
    suspend fun csrf(): CsrfResponseDto
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
    val morePosts: Boolean = false,
    @SerialName("more_topics")
    val moreTopics: Boolean = false,
)

@Serializable
data class SearchPostDto(
    val id: Long,
    @SerialName("topic_id")
    val topicId: Long,
    @SerialName("topic_title")
    val topicTitle: String = "",
    val username: String = "",
    @SerialName("avatar_template")
    val avatarTemplate: String = "",
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
    val username: String = "",
    val name: String = "",
    @SerialName("avatar_template")
    val avatarTemplate: String = "",
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
)

@Serializable
data class TopicResponseDto(
    val id: Long,
    val title: String,
    @SerialName("posts_count")
    val postsCount: Int = 0,
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
    @SerialName("cooked")
    val cookedHtml: String,
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
    val username: String,
    @SerialName("name")
    val displayName: String? = null,
    @SerialName("avatar_template")
    val avatarTemplate: String = "",
    @SerialName("can_undo")
    val canUndo: Boolean = false,
    @SerialName("created_at")
    val createdAt: String = "",
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
    val currentUser: CurrentUserDto? = null,
)

@Serializable
data class CurrentUserDto(
    val username: String = "",
)

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
    @SerialName("profile_view_count")
    val profileViewCount: Int = 0,
    val admin: Boolean = false,
    val moderator: Boolean = false,
)

@Serializable
data class CsrfResponseDto(
    val csrf: String,
)

internal fun EmojiGroupsDto.toEmojiCatalog(): Map<String, String> =
    values.flatten().associate { emoji -> emoji.name to emoji.url }

internal fun ReactionUsersResponseDto.toModel(): List<PostReactionUsersGroup> =
    reactionUsers.map { group ->
        PostReactionUsersGroup(
            id = group.id,
            count = group.count,
            users = group.users.map { user ->
                PostReactionUser(
                    username = user.username,
                    displayName = user.displayName.orEmpty(),
                    avatarTemplate = user.avatarTemplate,
                    canUndo = user.canUndo,
                    createdAt = user.createdAt,
                )
            },
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
    val topicHits = topics.map { topic ->
        topic.toTopicSummary(usersById = emptyMap())
    }
    val postTopicHits = posts
        .filter { post -> topicHits.none { topic -> topic.id == post.topicId } }
        .map { post -> post.toTopicSummary() }
    return SearchResult(
        topics = topicHits + postTopicHits,
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
        hasMore = groupedSearchResult?.let { result -> result.moreTopics || result.morePosts } ?: false,
    )
}

internal fun SiteResponseDto.toModel(): List<CategorySummary> = (
    categories.takeIf { it.isNotEmpty() } ?: categoryList?.categories.orEmpty()
    ).flatMap { category ->
    category.flattenedModels()
}

internal fun TopicResponseDto.toModel(
    emojiCatalog: Map<String, String> = emptyMap(),
): TopicDetail {
    val streamPostIds = postStream.stream.ifEmpty { postStream.posts.map { it.id } }
    return TopicDetail(
        id = id,
        title = title,
        posts = postStream.posts.map { post -> post.toModel(emojiCatalog = emojiCatalog) },
        createdAt = createdAt,
        lastPostedAt = lastPostedAt,
        viewCount = views,
        tags = tags,
        validReactions = validReactions,
        emojiCatalog = emojiCatalog,
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
    profileViewCount = user.profileViewCount,
    admin = user.admin,
    moderator = user.moderator,
)

internal fun PostDto.toModel(
    emojiCatalog: Map<String, String> = emptyMap(),
): Post = Post(
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
    cookedHtml = cookedHtml,
    imageUrls = Regex("<img[^>]+src=\\\"([^\\\"]+)\\\"")
        .findAll(cookedHtml)
        .map { match -> match.groupValues[1] }
        .toList(),
)

private fun TopicDto.toTopicSummary(
    usersById: Map<Long, TopicListUserDto>,
): TopicSummary {
    val posterUser = posters.primaryPosterOrNull()?.let { poster ->
        poster.user?.let { user ->
            posterUser(username = user.username, avatarTemplate = user.avatarTemplate)
        } ?: poster.userId
            ?.let(usersById::get)
            ?.let { user ->
                posterUser(username = user.username, avatarTemplate = user.avatarTemplate)
            }
    }
    val recentPosterUser = posters.mostRecentPosterOrNull()?.let { poster ->
        poster.user?.let { user ->
            posterUser(username = user.username, avatarTemplate = user.avatarTemplate)
        } ?: poster.userId
            ?.let(usersById::get)
            ?.let { user ->
                posterUser(username = user.username, avatarTemplate = user.avatarTemplate)
            }
    }

    return TopicSummary(
        id = id,
        title = title.ifBlank { fancyTitle },
        authorUsername = posterUser?.username.orEmpty(),
        authorAvatarTemplate = posterUser?.avatarTemplate.orEmpty(),
        imageUrl = imageUrl,
        slug = slug,
        replyCount = replyCount,
        viewCount = views,
        likeCount = likeCount,
        lastPostedAt = lastPostedAt,
        categoryId = categoryId,
        lastPosterUsername = lastPosterUsername.orEmpty().ifBlank { recentPosterUser?.username.orEmpty() },
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
    likeCount = likeCount,
    lastPostedAt = createdAt,
    categoryId = categoryId,
    lastPosterUsername = username,
    excerpt = blurb,
)

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
): PosterUser = PosterUser(
    username = username,
    avatarTemplate = avatarTemplate,
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
