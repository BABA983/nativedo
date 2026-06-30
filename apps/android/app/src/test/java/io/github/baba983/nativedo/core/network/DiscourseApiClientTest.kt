package io.github.baba983.nativedo.core.network

import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.github.baba983.nativedo.core.session.SessionLoggedOutException
import okhttp3.MediaType.Companion.toMediaType

class DiscourseApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: DiscourseApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(NetworkJson.instance.asConverterFactory("application/json".toMediaType()))
            .build()
        client = DiscourseApiClient(retrofit.create(DiscourseApiService::class.java))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun parsesLatestTopicsFixture() = runTest {
        server.enqueue(jsonResponse(fixture("discourse/latest-topics.json")))

        val page = client.fetchLatestPage(page = 0)

        assertThat(page.topics).isNotEmpty()
        assertThat(page.topics.first().id).isGreaterThan(0)
        assertThat(page.topics.first().title).isNotEmpty()
    }

    @Test
    fun parsesSearchFixture() = runTest {
        server.enqueue(jsonResponse(fixture("discourse/search.json")))

        val result = client.search(query = "NativeDo", page = 0)

        assertThat(result.topics.map { it.id }).containsExactly(202L, 101L).inOrder()
        assertThat(result.topics.first().title).isEqualTo("How to configure the 5.5 model")
        assertThat(result.topics.last().authorUsername).isEqualTo("baba")
        assertThat(result.users.single().username).isEqualTo("baba")
        assertThat(result.hasMore).isTrue()
        assertThat(server.takeRequest().path).isEqualTo("/search.json?q=NativeDo&page=0")
    }

    @Test
    fun fetchCategoriesMapsNestedCategoryMetadata() = runTest {
        server.enqueue(jsonResponse(fixture("discourse/site.json")))

        val categories = client.fetchCategories()

        assertThat(categories).hasSize(5)
        assertThat(categories.first { it.id == 2 }.name).isEqualTo("运营反馈")
        assertThat(categories.first { it.id == 99 }.parentCategoryId).isEqualTo(98)
        assertThat(categories.first { it.id == 99 }.colorHex).isEqualTo("0088CC")
        assertThat(server.takeRequest().path).isEqualTo("/site.json")
    }

    @Test
    fun parsesTopicDetailFixture() = runTest {
        server.enqueue(jsonResponse("""{"discourse_reactions":[{"name":"heart","url":"/images/emoji/heart.png"}]}"""))
        server.enqueue(jsonResponse(fixture("discourse/topic-detail.json")))

        val topic = client.fetchTopic(topicId = 1)

        assertThat(topic.id).isGreaterThan(0)
        assertThat(topic.title).isNotEmpty()
        assertThat(topic.posts).isNotEmpty()
        assertThat(topic.posts.first().cookedHtml).isNotEmpty()
        assertThat(topic.emojiCatalog["heart"]).isEqualTo("/images/emoji/heart.png")
        assertThat(topic.emojiCategories).hasSize(1)
        assertThat(topic.emojiCategories.first().id).isEqualTo("discourse_reactions")
        assertThat(topic.emojiCategories.first().emojis.first().name).isEqualTo("heart")
        assertThat(server.takeRequest().path).isEqualTo("/emojis.json")
        assertThat(server.takeRequest().path).isEqualTo("/t/1.json")
    }

    @Test
    fun parsesTopicPostsFixture() = runTest {
        server.enqueue(jsonResponse("""{"discourse_reactions":[{"name":"heart","url":"/images/emoji/heart.png"}]}"""))
        server.enqueue(jsonResponse(fixture("discourse/topic-posts.json")))

        val posts = client.fetchTopicPosts(topicId = 1, postIds = listOf(1, 2))

        assertThat(posts).isNotEmpty()
        assertThat(posts.first().authorUsername).isNotEmpty()
        assertThat(server.takeRequest().path).isEqualTo("/emojis.json")
        assertThat(server.takeRequest().path).isEqualTo("/t/1/posts.json?post_ids%5B%5D=1&post_ids%5B%5D=2")
    }

    @Test
    fun fetchPostByNumberMapsSinglePost() = runTest {
        server.enqueue(jsonResponse("""{"discourse_reactions":[{"name":"heart","url":"/images/emoji/heart.png"}]}"""))
        server.enqueue(
            jsonResponse(
                """
                {
                  "id": 1002,
                  "post_number": 2,
                  "reply_to_post_number": 1,
                  "username": "alice",
                  "avatar_template": "/user_avatar/linux.do/alice/{size}/1.png",
                  "reactions": [{"id": "heart", "count": 1}],
                  "cooked": "<p>Remote quote</p>"
                }
                """.trimIndent(),
            ),
        )

        val post = client.fetchPostByNumber(topicId = 101L, postNumber = 2)

        assertThat(post.id).isEqualTo(1002L)
        assertThat(post.postNumber).isEqualTo(2)
        assertThat(post.replyToPostNumber).isEqualTo(1)
        assertThat(post.reactions.single().iconUrl).isEqualTo("/images/emoji/heart.png")
        assertThat(server.takeRequest().path).isEqualTo("/emojis.json")
        assertThat(server.takeRequest().path).isEqualTo("/posts/by_number/101/2.json")
    }

    @Test
    fun createPostFetchesCsrfAndSendsDiscourseReplyFields() = runTest {
        server.enqueue(jsonResponse("""{"csrf":"csrf-token"}"""))
        server.enqueue(
            jsonResponse(
                """
                {
                  "id": 1003,
                  "post_number": 3,
                  "reply_to_post_number": 1,
                  "username": "baba",
                  "avatar_template": "/user_avatar/linux.do/baba/{size}/1.png",
                  "cooked": "<p>Hello reply</p>"
                }
                """.trimIndent(),
            ),
        )

        val post = client.createPost(topicId = 101L, raw = "hello", replyToPostNumber = 1)

        assertThat(post.id).isEqualTo(1003L)
        assertThat(post.postNumber).isEqualTo(3)
        assertThat(post.replyToPostNumber).isEqualTo(1)
        assertThat(server.takeRequest().path).isEqualTo("/session/csrf")
        val createRequest = server.takeRequest()
        assertThat(createRequest.method).isEqualTo("POST")
        assertThat(createRequest.path).isEqualTo("/posts.json")
        assertThat(createRequest.getHeader("X-CSRF-Token")).isEqualTo("csrf-token")
        val body = createRequest.body.readUtf8()
        assertThat(body).contains("topic_id=101")
        assertThat(body).contains("raw=hello")
        assertThat(body).contains("reply_to_post_number=1")
    }

    @Test
    fun fetchTopicUsesEmojiCatalogForReactionIcons() = runTest {
        server.enqueue(jsonResponse("""{"discourse_reactions":[{"name":"heart","url":"/images/emoji/heart.png"}]}"""))
        server.enqueue(
            jsonResponse(
                """
                {
                  "id": 101,
                  "title": "Welcome",
                  "valid_reactions": ["heart", "+1"],
                  "post_stream": {
                    "posts": [
                      {
                        "id": 1001,
                        "username": "baba",
                        "reactions": [{"id": "heart", "count": 2}],
                        "current_user_reaction": {"id": "heart"},
                        "reaction_users_count": 2,
                        "bookmarked": true,
                        "cooked": "<p>Hello</p>"
                      }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )

        val topic = client.fetchTopic(topicId = 101)
        val post = topic.posts.single()

        assertThat(topic.validReactions).containsExactly("heart", "+1").inOrder()
        assertThat(post.currentUserReaction).isEqualTo("heart")
        assertThat(post.reactionUsersCount).isEqualTo(2)
        assertThat(post.isBookmarked).isTrue()
        assertThat(post.reactions.single().iconUrl).isEqualTo("/images/emoji/heart.png")
        assertThat(post.reactions.single().reacted).isTrue()
    }

    @Test
    fun fetchReactionUsersMapsGroups() = runTest {
        server.enqueue(
            jsonResponse(
                """
                {
                  "reaction_users": [
                    {
                      "id": "heart",
                      "count": 1,
                      "users": [
                        {
                          "username": "baba",
                          "name": "Baba",
                          "avatar_template": "/user_avatar/linux.do/baba/{size}/1.png",
                          "can_undo": true,
                          "created_at": "2026-05-01T01:02:03.000Z"
                        }
                      ]
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val groups = client.fetchReactionUsers(postId = 1001L)

        assertThat(groups).hasSize(1)
        assertThat(groups.single().id).isEqualTo("heart")
        assertThat(groups.single().users.single().displayName).isEqualTo("Baba")
        assertThat(groups.single().users.single().canUndo).isTrue()
        assertThat(server.takeRequest().path).isEqualTo("/discourse-reactions/posts/1001/reactions-users.json")
    }

    @Test
    fun toggleReactionFetchesCsrfAndSendsHeader() = runTest {
        server.enqueue(jsonResponse("""{"csrf":"csrf-token"}"""))
        server.enqueue(jsonResponse("""{}"""))

        client.toggleReaction(postId = 1001L, reactionId = "heart")

        assertThat(server.takeRequest().path).isEqualTo("/session/csrf")
        val toggleRequest = server.takeRequest()
        assertThat(toggleRequest.method).isEqualTo("PUT")
        assertThat(toggleRequest.path)
            .isEqualTo("/discourse-reactions/posts/1001/custom-reactions/heart/toggle.json")
        assertThat(toggleRequest.getHeader("X-CSRF-Token")).isEqualTo("csrf-token")
    }

    @Test
    fun createBookmarkFetchesCsrfAndSendsDiscourseBookmarkFields() = runTest {
        server.enqueue(jsonResponse("""{"csrf":"csrf-token"}"""))
        server.enqueue(jsonResponse("""{}"""))

        client.setPostBookmark(postId = 1001L, bookmarked = true)

        assertThat(server.takeRequest().path).isEqualTo("/session/csrf")
        val bookmarkRequest = server.takeRequest()
        assertThat(bookmarkRequest.method).isEqualTo("POST")
        assertThat(bookmarkRequest.path).isEqualTo("/bookmarks.json")
        assertThat(bookmarkRequest.getHeader("X-CSRF-Token")).isEqualTo("csrf-token")
        val body = bookmarkRequest.body.readUtf8()
        assertThat(body).contains("bookmarkable_id=1001")
        assertThat(body).contains("bookmarkable_type=Post")
        assertThat(body).contains("auto_delete_preference=0")
    }

    @Test
    fun deletePostBookmarkFetchesCsrfAndSendsDeleteRequest() = runTest {
        server.enqueue(jsonResponse("""{"csrf":"csrf-token"}"""))
        server.enqueue(jsonResponse("""{}"""))

        client.setPostBookmark(postId = 1001L, bookmarked = false)

        assertThat(server.takeRequest().path).isEqualTo("/session/csrf")
        val deleteRequest = server.takeRequest()
        assertThat(deleteRequest.method).isEqualTo("DELETE")
        assertThat(deleteRequest.path).isEqualTo("/posts/1001/bookmark")
        assertThat(deleteRequest.getHeader("X-CSRF-Token")).isEqualTo("csrf-token")
    }

    @Test
    fun parsesUserProfileFixture() = runTest {
        server.enqueue(jsonResponse(fixture("discourse/user-profile.json")))

        val profile = client.fetchProfile(username = "baba")

        assertThat(profile.username).isEqualTo("baba")
        assertThat(profile.displayName).isEqualTo("Baba")
        assertThat(profile.bio).contains("Compose user")
        assertThat(profile.avatarTemplate).contains("/user_avatar/linux.do/baba/{size}/1.png")
        assertThat(profile.animatedAvatarUrl).contains("/user_avatar/linux.do/baba/120/1.gif")
        assertThat(profile.flairName).isEqualTo("Battery")
        assertThat(profile.gamificationScore).isEqualTo(2731)
        assertThat(profile.cakeDate).isEqualTo("2024-05-26")
        assertThat(profile.totalFollowers).isEqualTo(340)
        assertThat(profile.totalFollowing).isEqualTo(58)
        assertThat(profile.acceptedAnswers).isEqualTo(18)
        assertThat(profile.primaryGroupName).isEqualTo("battery")
        assertThat(server.takeRequest().path).isEqualTo("/u/baba.json")
    }

    @Test
    fun fetchCurrentUsername_mapsDiscourseLoggedOutHeaderToSessionLoggedOutException() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("discourse-logged-out", "true")
                .setHeader("content-type", "application/json")
                .setBody("""{"error_type":"not_logged_in"}"""),
        )

        val result = runCatching {
            client.fetchCurrentUsername()
        }

        assertThat(result.exceptionOrNull()).isInstanceOf(SessionLoggedOutException::class.java)
    }

    @Test
    fun parseBootstrap_readsCurrentUsernameMeta() {
        val bootstrap = BootstrapHtmlParser.parse(
            """
            <html>
              <head><meta name="current-username" content="baba" /></head>
            </html>
            """.trimIndent(),
        )

        assertThat(bootstrap.currentUsername).isEqualTo("baba")
    }

    @Test
    fun parseBootstrap_readsCurrentUserFromPreloadedData() {
        val bootstrap = BootstrapHtmlParser.parse(fixture("discourse/home-bootstrap.html"))

        assertThat(bootstrap.currentUsername).isEqualTo("baba")
    }

    private fun jsonResponse(body: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("content-type", "application/json")
            .setBody(body)

    private fun fixture(path: String): String =
        File("../../../fixtures/$path").readText()
}
