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
import io.github.baba983.nativedo.core.session.SessionExpiredReason
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

        assertThat(result.topics.map { it.id }).containsExactly(202L)
        assertThat(result.posts.map { it.id }).containsExactly(101L)
        assertThat(result.posts.single().targetPostNumber).isEqualTo(4)
        assertThat(result.posts.single().targetPostId).isEqualTo(9001L)
        assertThat(result.posts.single().rowIdentity).isEqualTo("search-post:9001")
        assertThat(result.topics.first().title).isEqualTo("How to configure the 5.5 model")
        assertThat(result.topics.single().authorUsername).isEqualTo("baba")
        assertThat(result.topics.single().authorAvatarTemplate)
            .isEqualTo("/user_avatar/linux.do/baba/{size}/1.png")
        assertThat(result.posts.single().authorUsername).isEqualTo("baba")
        assertThat(result.users.single().username).isEqualTo("baba")
        assertThat(result.hasMore).isTrue()
        assertThat(server.takeRequest().path).isEqualTo("/search.json?q=NativeDo&page=0")
    }

    @Test
    fun parsesSearchWhenDiscourseReturnsNullPaginationFlags() = runTest {
        server.enqueue(
            jsonResponse(
                """
                {
                  "topics": [],
                  "posts": [],
                  "users": [],
                  "grouped_search_result": {
                    "more_posts": null,
                    "more_topics": null,
                    "more_full_page_results": false
                  }
                }
                """.trimIndent(),
            ),
        )

        val result = client.search(query = "topic:2566145", page = 0)

        assertThat(result.hasMore).isFalse()
        assertThat(server.takeRequest().path).isEqualTo("/search.json?q=topic%3A2566145&page=0")
    }

    @Test
    fun topicScopedSearchSendsTypeFilterAndUsesFullPagePaginationFlag() = runTest {
        server.enqueue(
            jsonResponse(
                """
                {
                  "topics": [],
                  "posts": [],
                  "users": [],
                  "grouped_search_result": {
                    "more_posts": null,
                    "more_topics": false,
                    "more_full_page_results": true
                  }
                }
                """.trimIndent(),
            ),
        )

        val result = client.search(query = "429 topic:2566145", page = 1, typeFilter = "topic")

        assertThat(result.hasMore).isTrue()
        assertThat(server.takeRequest().path)
            .isEqualTo("/search.json?q=429%20topic%3A2566145&page=1&type_filter=topic")
    }

    @Test
    fun searchesTopicMentionUsersWithComposerEndpointAndBlankTerm() = runTest {
        server.enqueue(jsonResponse(fixture("discourse/mention-users.json")))

        val result = client.searchMentionUsers(term = "", topicId = 847468)

        assertThat(result.candidates.map { it.username }).containsExactly("orchid.hu", "team").inOrder()
        assertThat(result.candidates.first().priorityGroup).isEqualTo(0)
        assertThat(result.candidates.last().isGroup).isTrue()
        assertThat(server.takeRequest().path)
            .isEqualTo("/u/search/users?term=&topic_id=847468&include_groups=true&limit=6")
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
        assertThat(topic.posts.first().replyCount).isEqualTo(80)
        assertThat(topic.lastReadPostNumber).isEqualTo(1)
        assertThat(topic.lastReadPostId).isEqualTo(1001L)
        assertThat(topic.participants.map { it.username }).containsExactly("orchid.hu", "quiet-user").inOrder()
        assertThat(topic.participants.first().postCount).isEqualTo(4)
        assertThat(topic.participants.last().displayName).isEmpty()
        assertThat(topic.participants.last().avatarTemplate).contains("quiet-user")
        assertThat(topic.participants.last().animatedAvatarUrl).endsWith(".gif")
        assertThat(topic.emojiCatalog["heart"]).isEqualTo("/images/emoji/heart.png")
        assertThat(topic.emojiCategories).hasSize(1)
        assertThat(topic.emojiCategories.first().id).isEqualTo("discourse_reactions")
        assertThat(topic.emojiCategories.first().emojis.first().name).isEqualTo("heart")
        assertThat(server.takeRequest().path).isEqualTo("/emojis.json")
        assertThat(server.takeRequest().path).isEqualTo("/t/1.json")
    }

    @Test
    fun fetchTopicAtPostUsesDiscourseCenteredWindowRoute() = runTest {
        server.enqueue(jsonResponse("""{"discourse_reactions":[]}"""))
        server.enqueue(jsonResponse(fixture("discourse/topic-detail.json")))

        val topic = client.fetchTopic(topicId = 101L, postNumber = 144)

        assertThat(topic.id).isEqualTo(101L)
        assertThat(server.takeRequest().path).isEqualTo("/emojis.json")
        assertThat(server.takeRequest().path).isEqualTo("/t/101/144.json")
    }

    @Test
    fun topicDetailWithoutDetailsKeepsParticipantsEmpty() = runTest {
        server.enqueue(jsonResponse("""{"discourse_reactions":[]}"""))
        server.enqueue(
            jsonResponse(
                """{
                    "id": 2,
                    "title": "No details",
                    "post_stream": {
                        "stream": [2001],
                        "posts": [{"id":2001,"post_number":1,"username":"baba","cooked":"<p>x</p>"}]
                    }
                }""".trimIndent(),
            ),
        )

        val topic = client.fetchTopic(topicId = 2)

        assertThat(topic.participants).isEmpty()
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
    fun fetchPostRepliesMapsDirectReplyPage() = runTest {
        server.enqueue(jsonResponse(fixture("discourse/post-replies.json")))

        val replies = client.fetchPostReplies(postId = 1001L, afterPostNumber = 1)

        assertThat(replies.map { it.id }).containsExactly(1002L, 1003L).inOrder()
        assertThat(replies.first().replyToPostNumber).isEqualTo(1)
        assertThat(server.takeRequest().path).isEqualTo("/posts/1001/replies?after=1")
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
    fun markTopicReadPostsTimingsFormWithCsrfToken() = runTest {
        server.enqueue(jsonResponse("""{"csrf":"csrf-token"}"""))
        server.enqueue(jsonResponse("{}"))

        client.markTopicRead(
            topicId = 101L,
            topicTimeMillis = 2_000L,
            postTimings = mapOf(3 to 1_000, 4 to 1_000),
        )

        assertThat(server.takeRequest().path).isEqualTo("/session/csrf")
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/topics/timings")
        assertThat(request.getHeader("X-CSRF-Token")).isEqualTo("csrf-token")
        val body = request.body.readUtf8()
        assertThat(body).contains("topic_id=101")
        assertThat(body).contains("topic_time=2000")
        assertThat(body).contains("timings%5B3%5D=1000")
        assertThat(body).contains("timings%5B4%5D=1000")
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
        server.enqueue(jsonResponse(fixture("discourse/reaction-users.json")))

        val groups = client.fetchReactionUsers(postId = 1001L)

        assertThat(groups.map { it.id }).containsExactly("heart", "+1").inOrder()
        assertThat(groups.first().count).isEqualTo(50)
        assertThat(groups.first().users).hasSize(1)
        assertThat(groups.first().users.single().displayName).isEqualTo("Baba")
        assertThat(groups.first().users.single().canUndo).isTrue()
        assertThat(server.takeRequest().path).isEqualTo("/discourse-reactions/posts/1001/reactions-users.json")
    }

    @Test
    fun fetchPollVotersMapsOptionGroups() = runTest {
        server.enqueue(
            jsonResponse(
                """
                {
                  "voters": {
                    "option-a": [
                      {
                        "id": 356,
                        "username": "baba",
                        "name": "Baba",
                        "avatar_template": "/user_avatar/linux.do/baba/{size}/1.png"
                      }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )

        val groups = client.fetchPollVoters(postId = 1001L, pollName = "frontend")

        assertThat(groups).hasSize(1)
        assertThat(groups.single().id).isEqualTo("option-a")
        assertThat(groups.single().users.single().username).isEqualTo("baba")
        assertThat(server.takeRequest().path)
            .isEqualTo("/polls/voters.json?post_id=1001&poll_name=frontend&page=1&limit=50")
    }

    @Test
    fun fetchPollVotersSupportsNestedRankedChoiceUserPayload() = runTest {
        server.enqueue(
            jsonResponse(
                """
                {
                  "voters": {
                    "option-a": [
                      {
                        "user": {
                          "id": 356,
                          "username": "baba",
                          "name": "Baba",
                          "avatar_template": "/user_avatar/linux.do/baba/{size}/1.png"
                        }
                      }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )

        val groups = client.fetchPollVoters(postId = 1001L, pollName = "frontend")

        assertThat(groups).hasSize(1)
        assertThat(groups.single().id).isEqualTo("option-a")
        assertThat(groups.single().users.single().username).isEqualTo("baba")
        assertThat(groups.single().users.single().displayName).isEqualTo("Baba")
    }

    @Test
    fun fetchPollVotersSupportsPerOptionPagination() = runTest {
        server.enqueue(jsonResponse(fixture("discourse/poll-voters.json")))

        val groups = client.fetchPollVoters(
            postId = 1001L,
            pollName = "frontend",
            optionId = "e539a9df8700d0d05c69356a07b768cf",
            page = 2,
            limit = 50,
        )

        assertThat(groups.single().users).hasSize(2)
        assertThat(server.takeRequest().path).isEqualTo(
            "/polls/voters.json?post_id=1001&poll_name=frontend&option_id=e539a9df8700d0d05c69356a07b768cf&page=2&limit=50",
        )
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
    fun removePollVoteFetchesCsrfAndSendsDeleteBody() = runTest {
        server.enqueue(jsonResponse("""{"csrf":"csrf-token"}"""))
        server.enqueue(jsonResponse("""{}"""))

        client.removePollVote(postId = 20028390L, pollName = "poll")

        assertThat(server.takeRequest().path).isEqualTo("/session/csrf")
        val removeRequest = server.takeRequest()
        assertThat(removeRequest.method).isEqualTo("DELETE")
        assertThat(removeRequest.path).isEqualTo("/polls/vote")
        assertThat(removeRequest.getHeader("X-CSRF-Token")).isEqualTo("csrf-token")
        val body = removeRequest.body.readUtf8()
        assertThat(body).contains("post_id=20028390")
        assertThat(body).contains("poll_name=poll")
    }

    @Test
    fun votePollFetchesCsrfAndSendsPutBody() = runTest {
        server.enqueue(jsonResponse("""{"csrf":"csrf-token"}"""))
        server.enqueue(jsonResponse("""{"poll":{},"vote":["option-a"]}"""))

        client.votePoll(postId = 20028390L, pollName = "poll", optionIds = listOf("option-a"))

        assertThat(server.takeRequest().path).isEqualTo("/session/csrf")
        val voteRequest = server.takeRequest()
        assertThat(voteRequest.method).isEqualTo("PUT")
        assertThat(voteRequest.path).isEqualTo("/polls/vote")
        assertThat(voteRequest.getHeader("X-CSRF-Token")).isEqualTo("csrf-token")
        val body = voteRequest.body.readUtf8()
        assertThat(body).contains("post_id=20028390")
        assertThat(body).contains("poll_name=poll")
        assertThat(body).contains("options%5B%5D=option-a")
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
        assertThat(profile.flairUrl).isEqualTo("battery-quarter")
        assertThat(profile.flairBgColor).isEqualTo("001122")
        assertThat(profile.flairColor).isEqualTo("abcdef")
        assertThat(profile.trustLevel).isEqualTo(3)
        assertThat(profile.title).isEqualTo("活跃用户")
        assertThat(profile.badgeCount).isEqualTo(7)
        assertThat(profile.timeRead).isEqualTo(285035)
        assertThat(profile.recentTimeRead).isEqualTo(162657)
        assertThat(profile.lastSeenAt).isEqualTo("2026-05-28T06:00:20.612Z")
        assertThat(profile.lastPostedAt).isEqualTo("2026-05-27T12:30:00.000Z")
        assertThat(profile.profileViewCount).isEqualTo(97)
        assertThat(profile.gamificationScore).isEqualTo(2731)
        assertThat(profile.cakeDate).isEqualTo("2024-05-26")
        assertThat(profile.totalFollowers).isEqualTo(340)
        assertThat(profile.totalFollowing).isEqualTo(58)
        assertThat(profile.acceptedAnswers).isEqualTo(18)
        assertThat(profile.primaryGroupName).isEqualTo("battery")
        assertThat(server.takeRequest().path).isEqualTo("/u/baba.json")
    }

    @Test
    fun fetchCurrentUser_mapsAvatarTemplateFromSessionCurrent() = runTest {
        server.enqueue(
            jsonResponse(
                """
                {
                  "current_user": {
                    "username": "baba",
                    "name": "Baba",
                    "avatar_template": "/user_avatar/linux.do/baba/{size}/1.png"
                  }
                }
                """.trimIndent(),
            ),
        )

        val currentUser = client.fetchCurrentUser()

        assertThat(currentUser?.username).isEqualTo("baba")
        assertThat(currentUser?.displayName).isEqualTo("Baba")
        assertThat(currentUser?.avatarTemplate).isEqualTo("/user_avatar/linux.do/baba/{size}/1.png")
        assertThat(server.takeRequest().path).isEqualTo("/session/current.json")
    }

    @Test
    fun fetchCurrentUser_mapsHttp401ToSessionLoggedOutAndNotifiesCoordinator() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("content-type", "application/json")
                .setBody("{\"error\":\"unauthorized\"}"),
        )
        var invalidationReason: SessionExpiredReason? = null
        client.setSessionBoundary(
            contextProvider = { io.github.baba983.nativedo.core.session.SessionRequestContext(0, io.github.baba983.nativedo.core.session.SessionRequestAuth.Authenticated) },
            onSessionInvalidated = { reason, _ -> invalidationReason = reason },
            onLoginRequired = {},
        )

        val result = runCatching { client.fetchCurrentUser() }

        assertThat(result.exceptionOrNull()).isInstanceOf(SessionLoggedOutException::class.java)
        assertThat(invalidationReason).isEqualTo(SessionExpiredReason.ServerLoggedOut)
    }

    @Test
    fun anonymousAuthenticatedWriteMaps401ToLoginRequiredWithoutInvalidatingSession() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("content-type", "application/json")
                .setBody("{\"error\":\"unauthorized\"}"),
        )
        val context = io.github.baba983.nativedo.core.session.SessionRequestContext(
            generation = 7,
            auth = io.github.baba983.nativedo.core.session.SessionRequestAuth.Anonymous,
        )
        var invalidationReason: SessionExpiredReason? = null
        var loginRequiredCount = 0
        client.setSessionBoundary(
            contextProvider = { context },
            onSessionInvalidated = { reason, _ -> invalidationReason = reason },
            onLoginRequired = { loginRequiredCount += 1 },
        )

        val result = runCatching {
            client.createPost(topicId = 42L, raw = "reply", replyToPostNumber = null)
        }

        assertThat(result.exceptionOrNull()).isInstanceOf(
            io.github.baba983.nativedo.core.session.LoginRequiredException::class.java,
        )
        assertThat(invalidationReason).isNull()
        assertThat(loginRequiredCount).isEqualTo(1)
    }

    @Test
    fun forbiddenResponsesDistinguishChallengeCsrfAndPermissionWithoutInvalidation() = runTest {
        val cases = listOf(
            MockResponse().setResponseCode(403).setHeader("server", "cloudflare")
                .setHeader("content-type", "application/json").setBody("{\"error_type\":\"invalid_access\"}") to "请求被拒绝",
            MockResponse().setResponseCode(403).setBody("[\"BAD CSRF\"]") to "请求验证已过期",
            MockResponse().setResponseCode(403).setHeader("cf-mitigated", "challenge")
                .setHeader("discourse-logged-out", "true").setBody("<html>challenge</html>") to "登录状态暂时无法验证",
        )
        var invalidated = false
        client.setSessionBoundary(
            contextProvider = { io.github.baba983.nativedo.core.session.SessionRequestContext(
                1, io.github.baba983.nativedo.core.session.SessionRequestAuth.Authenticated) },
            onSessionInvalidated = { _, _ -> invalidated = true }, onLoginRequired = {})
        for ((response, message) in cases) {
            server.enqueue(response)
            val error = runCatching { client.fetchCurrentUser() }.exceptionOrNull()
            assertThat(error?.message).contains(message)
            assertThat(invalidated).isFalse()
        }
    }

    @Test
    fun currentSession404CanRecoverThroughHomepageWithoutInvalidation() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html")
            .setBody("<meta name=\"current-username\" content=\"baba\">"))
        val cookies = io.github.baba983.nativedo.core.storage.CookieStore().apply {
            save(server.url("/"), listOf(okhttp3.Cookie.Builder().name("_t").value("token")
                .hostOnlyDomain(server.url("/").host).path("/").build()))
        }
        val coordinator = io.github.baba983.nativedo.core.session.SessionCoordinator(
            cookies, io.github.baba983.nativedo.core.storage.SessionStore(), client)
        client.setSessionBoundary(coordinator::currentRequestContext,
            coordinator::markExpiredIfCurrent, coordinator::requestLogin)
        coordinator.start()
        assertThat(coordinator.sessionState.value)
            .isInstanceOf(io.github.baba983.nativedo.core.session.AppSessionState.Authenticated::class.java)
        assertThat(cookies.hasAuthenticatedCookies()).isTrue()
    }

    @Test
    fun incompleteCurrentUserPayloadRequiresRecoveryWithoutClearingCookies() = runTest {
        for (body in listOf("{}", "{\"current_user\":{}}", "{\"current_user\":{\"username\":\" \"}}")) {
            server.enqueue(jsonResponse(body))
            var invalidated = false
            client.setSessionBoundary(
                contextProvider = { io.github.baba983.nativedo.core.session.SessionRequestContext(
                    1, io.github.baba983.nativedo.core.session.SessionRequestAuth.Restoring) },
                onSessionInvalidated = { _, _ -> invalidated = true },
                onLoginRequired = {},
            )
            assertThat(runCatching { client.fetchCurrentUser() }.exceptionOrNull())
                .isInstanceOf(SessionRecoveryRequiredException::class.java)
            assertThat(invalidated).isFalse()
        }
    }

    @Test
    fun fetchCurrentUser_mapsNullCurrentUserToSessionLoggedOut() = runTest {
        server.enqueue(jsonResponse("{\"current_user\":null}"))
        var invalidationReason: SessionExpiredReason? = null
        client.setSessionBoundary(
            contextProvider = { io.github.baba983.nativedo.core.session.SessionRequestContext(0, io.github.baba983.nativedo.core.session.SessionRequestAuth.Authenticated) },
            onSessionInvalidated = { reason, _ -> invalidationReason = reason },
            onLoginRequired = {},
        )

        val result = runCatching { client.fetchCurrentUser() }

        assertThat(result.exceptionOrNull()).isInstanceOf(SessionLoggedOutException::class.java)
        assertThat(invalidationReason).isEqualTo(SessionExpiredReason.ServerLoggedOut)
    }

    @Test
    fun fetchCurrentUser_mapsCloudflareChallengeToRecoveryRequiredWithoutInvalidatingSession() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("server", "cloudflare")
                .setHeader("content-type", "text/html; charset=UTF-8")
                .setBody("<html><title>Just a moment...</title><body>challenge-platform</body></html>"),
        )
        var invalidationReason: SessionExpiredReason? = null
        client.setSessionBoundary(
            contextProvider = { io.github.baba983.nativedo.core.session.SessionRequestContext(0, io.github.baba983.nativedo.core.session.SessionRequestAuth.Authenticated) },
            onSessionInvalidated = { reason, _ -> invalidationReason = reason },
            onLoginRequired = {},
        )

        val result = runCatching { client.fetchCurrentUser() }

        assertThat(result.exceptionOrNull()).isInstanceOf(SessionRecoveryRequiredException::class.java)
        assertThat(invalidationReason).isNull()
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
