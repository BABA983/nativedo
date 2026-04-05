package io.github.baba983.nativedo.core.network

import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.SearchResult
import io.github.baba983.nativedo.core.model.SearchUser
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
import retrofit2.http.Headers
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

    @GET("t/{id}.json")
    suspend fun topic(@Path("id") id: Long): TopicResponseDto

    @GET("t/{id}/posts.json")
    suspend fun topicPosts(
        @Path("id") id: Long,
        @Query("post_ids[]") postIds: List<Long>,
    ): TopicPostsResponseDto

    @GET("search.json")
    suspend fun search(@Query("q") query: String): SearchResponseDto

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
    val title: String,
    @SerialName("image_url")
    val imageUrl: String? = null,
    val slug: String = "",
    @SerialName("reply_count")
    val replyCount: Int = 0,
    val views: Int = 0,
    @SerialName("last_posted_at")
    val lastPostedAt: String = "",
    @SerialName("category_id")
    val categoryId: Int? = null,
    val posters: List<PosterDto> = emptyList(),
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
    @SerialName("created_at")
    val createdAt: String = "",
    @SerialName("cooked")
    val cookedHtml: String,
)

@Serializable
data class SearchResponseDto(
    val topics: List<TopicDto> = emptyList(),
    val users: List<SearchUserDto> = emptyList(),
)

@Serializable
data class SearchUserDto(
    val id: Long? = null,
    val username: String,
    @SerialName("name")
    val displayName: String? = null,
    @SerialName("avatar_template")
    val avatarTemplate: String = "",
)

@Serializable
data class UserProfileResponseDto(
    val user: UserDto,
)

@Serializable
data class CurrentSessionResponseDto(
    @SerialName("current_user")
    val currentUser: CurrentSessionUserDto? = null,
)

@Serializable
data class CurrentSessionUserDto(
    val username: String = "",
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
)

@Serializable
data class CsrfResponseDto(
    @SerialName("csrf")
    val csrf: String,
)

internal fun TopicDto.toModel(): TopicSummary = toTopicSummary(usersById = emptyMap())

internal fun TopicListResponseDto.toModel(): TopicListPage = TopicListPage(
    topics = topicList.topics.map { topic ->
        topic.toTopicSummary(
            usersById = users.associateBy(TopicListUserDto::id),
        )
    },
    moreTopicsUrl = topicList.moreTopicsUrl,
)

internal fun TopicResponseDto.toModel(): TopicDetail {
    val streamPostIds = postStream.stream.ifEmpty { postStream.posts.map { it.id } }
    return TopicDetail(
        id = id,
        title = title,
        posts = postStream.posts.map { it.toModel() },
        createdAt = createdAt,
        lastPostedAt = lastPostedAt,
        viewCount = views,
        tags = tags,
        totalPosts = postsCount.takeIf { it > 0 } ?: streamPostIds.size,
        streamPostIds = streamPostIds,
    )
}

internal fun TopicPostsResponseDto.toModel(): List<Post> = postStream.posts.map { it.toModel() }

internal fun PostDto.toModel(): Post = Post(
    id = id,
    postNumber = postNumber,
    replyToPostNumber = replyToPostNumber,
    authorUsername = username,
    authorDisplayName = displayName.orEmpty(),
    authorAvatarTemplate = avatarTemplate,
    createdAt = createdAt,
    cookedHtml = cookedHtml,
    imageUrls = Regex("<img[^>]+src=\\\"([^\\\"]+)\\\"")
        .findAll(cookedHtml)
        .map { match -> match.groupValues[1] }
        .toList(),
)

internal fun SearchResponseDto.toModel(): SearchResult = SearchResult(
    topics = topics.map { topic ->
        topic.toTopicSummary(
            usersById = users
                .mapNotNull { user -> user.id?.let { id -> id to user.toTopicListUser() } }
                .toMap(),
        )
    },
    users = users.map { it.toModel() },
)

internal fun SearchUserDto.toModel(): SearchUser = SearchUser(
    username = username,
    displayName = displayName.orEmpty(),
    avatarTemplate = avatarTemplate,
)

internal fun UserProfileResponseDto.toModel(): UserProfile = UserProfile(
    username = user.username,
    displayName = user.displayName.orEmpty(),
    bio = user.bio.orEmpty(),
    avatarUrl = user.avatarTemplate,
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

    return TopicSummary(
        id = id,
        title = title,
        authorUsername = posterUser?.username.orEmpty(),
        authorAvatarTemplate = posterUser?.avatarTemplate.orEmpty(),
        imageUrl = imageUrl,
        slug = slug,
        replyCount = replyCount,
        viewCount = views,
        lastPostedAt = lastPostedAt,
        categoryId = categoryId,
    )
}

private data class PosterUser(
    val username: String,
    val avatarTemplate: String,
)

private fun SearchUserDto.toTopicListUser(): TopicListUserDto = TopicListUserDto(
    id = id ?: 0L,
    username = username,
    avatarTemplate = avatarTemplate,
)

private fun List<PosterDto>.primaryPosterOrNull(): PosterDto? {
    return firstOrNull { poster ->
        poster.description.contains("original poster", ignoreCase = true)
    } ?: firstOrNull()
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
