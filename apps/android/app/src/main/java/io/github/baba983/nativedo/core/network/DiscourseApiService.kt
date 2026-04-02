package io.github.baba983.nativedo.core.network

import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.SearchResult
import io.github.baba983.nativedo.core.model.SearchUser
import io.github.baba983.nativedo.core.model.TopicDetail
import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.core.model.UserProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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

    @GET("search.json")
    suspend fun search(@Query("q") query: String): SearchResponseDto

    @GET("u/{username}.json")
    suspend fun profile(@Path("username") username: String): UserProfileResponseDto

    @GET("session/csrf")
    suspend fun csrf(): CsrfResponseDto
}

@Serializable
data class TopicListResponseDto(
    @SerialName("topic_list")
    val topicList: TopicListDto,
)

@Serializable
data class TopicListDto(
    val topics: List<TopicDto>,
)

@Serializable
data class TopicDto(
    val id: Long,
    val title: String,
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
    val user: PosterUserDto? = null,
)

@Serializable
data class PosterUserDto(
    val username: String = "",
    @SerialName("avatar_template")
    val avatarTemplate: String = "",
)

@Serializable
data class TopicResponseDto(
    val id: Long,
    val title: String,
    @SerialName("post_stream")
    val postStream: PostStreamDto,
)

@Serializable
data class PostStreamDto(
    val posts: List<PostDto>,
)

@Serializable
data class PostDto(
    val id: Long,
    val username: String,
    @SerialName("avatar_template")
    val avatarTemplate: String = "",
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
    val username: String,
    @SerialName("name")
    val displayName: String = "",
    @SerialName("avatar_template")
    val avatarTemplate: String = "",
)

@Serializable
data class UserProfileResponseDto(
    val user: UserDto,
)

@Serializable
data class UserDto(
    val username: String,
    @SerialName("name")
    val displayName: String = "",
    @SerialName("bio_cooked")
    val bio: String = "",
    @SerialName("avatar_template")
    val avatarTemplate: String = "",
)

@Serializable
data class CsrfResponseDto(
    @SerialName("csrf")
    val csrf: String,
)

internal fun TopicDto.toModel(): TopicSummary = TopicSummary(
    id = id,
    title = title,
    authorUsername = posters.firstOrNull()?.user?.username.orEmpty(),
    authorAvatarTemplate = posters.firstOrNull()?.user?.avatarTemplate.orEmpty(),
    slug = slug,
    replyCount = replyCount,
    viewCount = views,
    lastPostedAt = lastPostedAt,
    categoryId = categoryId,
)

internal fun TopicResponseDto.toModel(): TopicDetail = TopicDetail(
    id = id,
    title = title,
    posts = postStream.posts.map { it.toModel() },
)

internal fun PostDto.toModel(): Post = Post(
    id = id,
    authorUsername = username,
    authorAvatarTemplate = avatarTemplate,
    cookedHtml = cookedHtml,
    imageUrls = Regex("<img[^>]+src=\\\"([^\\\"]+)\\\"")
        .findAll(cookedHtml)
        .map { match -> match.groupValues[1] }
        .toList(),
)

internal fun SearchResponseDto.toModel(): SearchResult = SearchResult(
    topics = topics.map { it.toModel() },
    users = users.map { it.toModel() },
)

internal fun SearchUserDto.toModel(): SearchUser = SearchUser(
    username = username,
    displayName = displayName,
    avatarTemplate = avatarTemplate,
)

internal fun UserProfileResponseDto.toModel(): UserProfile = UserProfile(
    username = user.username,
    displayName = user.displayName,
    bio = user.bio,
    avatarUrl = user.avatarTemplate,
)
