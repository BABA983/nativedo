package io.github.baba983.nativedo.feature.topic.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.core.model.MentionCandidate
import io.github.baba983.nativedo.core.model.PostReaction
import io.github.baba983.nativedo.core.model.PostReactionUser
import io.github.baba983.nativedo.core.model.PostReactionUsersGroup
import kotlinx.coroutines.CompletableDeferred
import io.github.baba983.nativedo.core.model.TopicDetail
import io.github.baba983.nativedo.feature.topic.data.TopicRepository
import io.github.baba983.nativedo.feature.topic.data.TopicReplyDraft
import io.github.baba983.nativedo.feature.topic.data.TopicReplyDraftKey
import io.github.baba983.nativedo.feature.topic.data.TopicReplyDraftPersistence
import io.github.baba983.nativedo.feature.topic.data.TopicReplyDraftStore
import io.github.baba983.nativedo.feature.topic.data.TopicReadPositionKey
import io.github.baba983.nativedo.feature.topic.data.TopicReadPositionPersistence
import io.github.baba983.nativedo.feature.topic.data.TopicReadPositionStore
import io.github.baba983.nativedo.test.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test

class TopicViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: TopicViewModel

    @Test
    fun persistedReplyDraftSurvivesInitialTopicRefresh() = runTest {
        val key = TopicReplyDraftKey("https://linux.do", "alice", 101L)
        val persistence = FakeReplyDraftPersistence(
            mutableMapOf(key to TopicReplyDraft(raw = "saved draft", replyToPostNumber = 4)),
        )
        val repository = FakeTopicRepository(
            detail = topicDetail(loadedPostIds = listOf(1001L), streamPostIds = listOf(1001L)),
        )

        viewModel = TopicViewModel(
            repository = repository,
            topicId = 101L,
            replyDraftStore = TopicReplyDraftStore(persistence),
            replyDraftKey = key,
        )

        val state = viewModel.awaitState { !it.isLoading }
        assertThat(state.replyDraft).isEqualTo(TopicReplyDraft(raw = "saved draft", replyToPostNumber = 4))
    }

    @Test
    fun readPositionMirrorsServerAndClearsAtBottom() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(
                loadedPostIds = listOf(1001L, 1002L),
                streamPostIds = listOf(1001L, 1002L, 1003L, 1004L),
                highestPostNumber = 4,
                lastReadPostNumber = 2,
            ),
            posts = Result.success(
                listOf(
                    post(id = 1003L, postNumber = 3),
                    post(id = 1004L, postNumber = 4),
                ),
            ),
        )
        viewModel = TopicViewModel(
            repository = repository,
            topicId = 101L,
            canTrackReadPosition = true,
        )

        val state = viewModel.awaitState { !it.isLoading }
        assertThat(state.lastReadPostNumber).isEqualTo(2)
        assertThat(state.unreadPosts).isEqualTo(2)

        // Marking read with an unloaded tail is a no-op: nothing was emitted,
        // and the read position stays behind the unloaded tail.
        viewModel.markLoadedPostsRead()
        val readState = viewModel.uiState.value
        assertThat(readState.lastReadPostNumber).isEqualTo(2)

        viewModel.loadMore()
        val loadedState = viewModel.awaitState { it.posts.size == 4 }
        viewModel.markLoadedPostsRead()
        val caughtUp = viewModel.awaitState { it.lastReadPostNumber == 4 }
        assertThat(caughtUp.lastReadPostNumber).isEqualTo(4)
        assertThat(caughtUp.unreadPosts).isEqualTo(0)
    }

    @Test
    fun anonymousSessionNeverAdvancesReadPosition() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(
                loadedPostIds = listOf(1001L),
                streamPostIds = listOf(1001L, 1002L, 1003L),
                highestPostNumber = 3,
                lastReadPostNumber = 0,
            ),
        )
        viewModel = TopicViewModel(
            repository = repository,
            topicId = 101L,
            canTrackReadPosition = false,
        )

        val state = viewModel.awaitState { !it.isLoading }
        assertThat(state.lastReadPostNumber).isEqualTo(0)
        assertThat(state.unreadPosts).isEqualTo(0)

        viewModel.markLoadedPostsRead()
        assertThat(viewModel.uiState.value.lastReadPostNumber).isEqualTo(0)
        viewModel.advanceReadPositionTo(1)
        advanceTimeBy(250L)
        runCurrent()
        assertThat(repository.readReportRequests).isEmpty()
    }

    @Test
    fun authenticatedSessionAtZeroReadPositionShowsUnreadAndReportsAdvances() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(
                loadedPostIds = listOf(1001L, 1002L, 1003L),
                streamPostIds = listOf(1001L, 1002L, 1003L),
                highestPostNumber = 3,
                lastReadPostNumber = 0,
            ),
        )
        viewModel = TopicViewModel(
            repository = repository,
            topicId = 101L,
            canTrackReadPosition = true,
        )

        val state = viewModel.awaitState { !it.isLoading }
        assertThat(state.unreadPosts).isEqualTo(3)

        viewModel.advanceReadPositionTo(2)
        val updated = viewModel.awaitState { it.lastReadPostNumber == 2 }
        assertThat(updated.unreadPosts).isEqualTo(1)

        advanceTimeBy(250L)
        runCurrent()
        assertThat(repository.readReportRequests).containsExactly(
            ReadReportRequest(
                topicId = 101L,
                topicTimeMillis = 2_000L,
                postTimings = mapOf(1 to 1_000, 2 to 1_000),
            ),
        )
    }

    @Test
    fun advanceReadPositionToAdvancesPerScrollClampedToServerTruth() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(
                loadedPostIds = listOf(1001L, 1002L, 1003L),
                streamPostIds = listOf(1001L, 1002L, 1003L, 1004L),
                highestPostNumber =4,
                lastReadPostNumber =1,
            ),
        )
        viewModel = TopicViewModel(
            repository = repository,
            topicId = 101L,
            canTrackReadPosition = true,
        )

        val state = viewModel.awaitState { !it.isLoading }
        assertThat(state.lastReadPostNumber).isEqualTo(1)

        // Per-scroll advance (vendor updateVisibleMessagesPart marks visible
        // messages read and decrements newUnreadMessageCount at the same point).
        viewModel.advanceReadPositionTo(2)
        var updated = viewModel.awaitState { it.lastReadPostNumber == 2 }
        assertThat(updated.lastReadPostNumber).isEqualTo(2)

        // Monotonic: scrolling back up never rewinds the read position.
        viewModel.advanceReadPositionTo(1)
        assertThat(viewModel.uiState.value.lastReadPostNumber).isEqualTo(2)

        // Clamped to the server highest post number, and reported once per range.
        viewModel.advanceReadPositionTo(9)
        updated = viewModel.awaitState { it.lastReadPostNumber == 4 }
        assertThat(updated.lastReadPostNumber).isEqualTo(4)
    }

    @Test
    fun readReportsConflateRapidScrollAdvancesIntoOneRequest() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(
                loadedPostIds = listOf(1001L, 1002L, 1003L, 1004L),
                streamPostIds = listOf(1001L, 1002L, 1003L, 1004L),
                highestPostNumber = 4,
                lastReadPostNumber = 1,
            ),
        )
        viewModel = TopicViewModel(
            repository = repository,
            topicId = 101L,
            canTrackReadPosition = true,
        )
        viewModel.awaitState { !it.isLoading }

        viewModel.advanceReadPositionTo(2)
        viewModel.advanceReadPositionTo(3)
        viewModel.advanceReadPositionTo(4)
        advanceTimeBy(249L)
        runCurrent()
        assertThat(repository.readReportRequests).isEmpty()

        advanceTimeBy(1L)
        runCurrent()
        assertThat(repository.readReportRequests).containsExactly(
            ReadReportRequest(
                topicId = 101L,
                topicTimeMillis = 3_000L,
                postTimings = mapOf(2 to 1_000, 3 to 1_000, 4 to 1_000),
            ),
        )
    }

    @Test
    fun localReadPositionWinsStaleServerAndFlushesImmediately() = runTest {
        val key = TopicReadPositionKey("https://linux.do", "alice", 101L)
        val readPersistence = FakeReadPositionPersistence(mutableMapOf(key to 4))
        val repository = FakeTopicRepository(
            detail = topicDetail(
                loadedPostIds = listOf(1001L, 1002L),
                streamPostIds = listOf(1001L, 1002L, 1003L, 1004L, 1005L),
                highestPostNumber = 5,
                lastReadPostNumber = 1,
                lastReadPostId = 1001L,
            ),
            detailAtPostNumber = topicDetail(
                loadedPostIds = listOf(1003L, 1004L, 1005L),
                streamPostIds = listOf(1001L, 1002L, 1003L, 1004L, 1005L),
                highestPostNumber = 5,
                lastReadPostNumber = 1,
                lastReadPostId = 1001L,
            ),
        )
        viewModel = TopicViewModel(
            repository = repository,
            topicId = 101L,
            readPositionStore = TopicReadPositionStore(readPersistence),
            readPositionKey = key,
            canTrackReadPosition = true,
        )

        val state = viewModel.awaitState { !it.isLoading }
        assertThat(repository.loadAtPostNumberRequests).containsExactly(101L to 4)
        assertThat(state.lastReadPostNumber).isEqualTo(4)
        assertThat(state.lastReadPostId).isEqualTo(1004L)
        assertThat(state.posts.map(Post::id)).containsExactly(1003L, 1004L, 1005L).inOrder()

        viewModel.flushReadPosition()
        runCurrent()

        assertThat(repository.readReportRequests).containsExactly(
            ReadReportRequest(
                topicId = 101L,
                topicTimeMillis = 3_000L,
                postTimings = mapOf(2 to 1_000, 3 to 1_000, 4 to 1_000),
            ),
        )
    }

    @Test
    fun scrollAdvancePersistsBeforeDebouncedServerReport() = runTest {
        val key = TopicReadPositionKey("https://linux.do", "alice", 101L)
        val readPersistence = FakeReadPositionPersistence()
        val repository = FakeTopicRepository(
            detail = topicDetail(
                loadedPostIds = listOf(1001L, 1002L, 1003L),
                streamPostIds = listOf(1001L, 1002L, 1003L),
                highestPostNumber = 3,
                lastReadPostNumber = 1,
                lastReadPostId = 1001L,
            ),
        )
        viewModel = TopicViewModel(
            repository = repository,
            topicId = 101L,
            readPositionStore = TopicReadPositionStore(readPersistence),
            readPositionKey = key,
            canTrackReadPosition = true,
        )
        viewModel.awaitState { !it.isLoading }

        viewModel.advanceReadPositionTo(2)

        assertThat(readPersistence.values[key]).isEqualTo(2)
        assertThat(viewModel.uiState.value.lastReadPostId).isEqualTo(1002L)
        assertThat(repository.readReportRequests).isEmpty()
        viewModel.flushReadPosition()
        viewModel.flushReadPosition()
        runCurrent()
        assertThat(repository.readReportRequests).containsExactly(
            ReadReportRequest(
                topicId = 101L,
                topicTimeMillis = 1_000L,
                postTimings = mapOf(2 to 1_000),
            ),
        )
    }

    fun replyDraftTextAndTargetUseOnePersistedState() = runTest {
        val key = TopicReplyDraftKey("https://linux.do", "alice", 101L)
        val persistence = FakeReplyDraftPersistence()
        viewModel = TopicViewModel(
            repository = FakeTopicRepository(
                detail = topicDetail(loadedPostIds = listOf(1001L), streamPostIds = listOf(1001L)),
            ),
            topicId = 101L,
            replyDraftStore = TopicReplyDraftStore(persistence),
            replyDraftKey = key,
        )
        viewModel.awaitState { !it.isLoading }

        viewModel.updateReplyDraftText("hello")
        viewModel.prepareReplyDraft(replyToPostNumber = 3)
        assertThat(viewModel.uiState.value.replyDraft).isEqualTo(TopicReplyDraft("hello", 3))
        assertThat(persistence.values[key]).isEqualTo(TopicReplyDraft("hello", 3))

        viewModel.clearReplyTarget()
        assertThat(viewModel.uiState.value.replyDraft).isEqualTo(TopicReplyDraft("hello", null))
    }

    @Test
    fun initLoadsTopicAndTracksRemainingPosts() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(
                loadedPostIds = listOf(1001L),
                streamPostIds = listOf(1001L, 1002L, 1003L),
                participants = listOf(MentionCandidate(username = "topic-participant", postCount = 3)),
            ),
        )

        viewModel = TopicViewModel(repository = repository, topicId = 101L)

        val state = viewModel.awaitState { state -> !state.isLoading && state.posts.isNotEmpty() }
        assertThat(state.title).isEqualTo("Welcome to NativeDo Android")
        assertThat(state.posts.map { it.id }).containsExactly(1001L)
        assertThat(state.totalPosts).isEqualTo(3)
        assertThat(state.participants.single().username).isEqualTo("topic-participant")
        assertThat(state.hasMore).isTrue()
        assertThat(state.isLoading).isFalse()
        assertThat(repository.loadRequests).containsExactly(101L)
    }

    @Test
    fun loadMoreAppendsRemainingPosts() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(loadedPostIds = listOf(1001L), streamPostIds = listOf(1001L, 1002L, 1003L)),
            posts = Result.success(listOf(post(1002L), post(1003L))),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.isNotEmpty() }

        viewModel.loadMore()

        val state = viewModel.awaitState { state -> !state.isLoadingMore && state.posts.size == 3 }
        assertThat(state.posts.map { it.id }).containsExactly(1001L, 1002L, 1003L).inOrder()
        assertThat(state.isLoadingMore).isFalse()
        assertThat(state.hasMore).isFalse()
        assertThat(repository.loadPostRequests).containsExactly(101L to listOf(1002L, 1003L))
    }

    @Test
    fun loadMoreFailureKeepsExistingPosts() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(loadedPostIds = listOf(1001L), streamPostIds = listOf(1001L, 1002L, 1003L)),
            posts = Result.failure(IllegalStateException("server error")),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.isNotEmpty() }

        viewModel.loadMore()

        val state = viewModel.awaitState { state -> state.loadMoreErrorMessage != null }
        assertThat(state.posts.map { it.id }).containsExactly(1001L)
        assertThat(state.isLoadingMore).isFalse()
        assertThat(state.loadMoreErrorMessage).isNotEmpty()
        assertThat(state.hasMore).isTrue()
    }

    @Test
    fun replacePostUpdatesExistingPostInPlace() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(loadedPostIds = listOf(1001L, 1002L), streamPostIds = listOf(1001L, 1002L)),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.size == 2 }

        viewModel.replacePost(post(1002L).copy(currentUserReaction = "heart"))

        val state = viewModel.awaitState { state ->
            state.posts.any { post -> post.id == 1002L && post.currentUserReaction == "heart" }
        }
        assertThat(state.posts.map { it.id }).containsExactly(1001L, 1002L).inOrder()
        assertThat(state.posts.single { it.id == 1002L }.currentUserReaction).isEqualTo("heart")
    }

    @Test
    fun onReactionSelectedAppliesOptimisticReactionAndCallsRepository() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(loadedPostIds = listOf(1001L), streamPostIds = listOf(1001L)),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.isNotEmpty() }

        viewModel.onReactionSelected(postId = 1001L, reactionId = "heart")
        val state = viewModel.awaitState { state -> state.posts.single().currentUserReaction == "heart" }
        runCurrent()

        val post = state.posts.single()
        assertThat(post.reactionUsersCount).isEqualTo(1)
        assertThat(post.reactions).contains(PostReaction(id = "heart", count = 1, iconUrl = "heart.png", reacted = true))
        assertThat(repository.toggleReactionRequests).containsExactly(1001L to "heart")
    }

    @Test
    fun onReactionSelectedRollsBackOnFailure() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(loadedPostIds = listOf(1001L), streamPostIds = listOf(1001L)),
            toggleReactionResult = Result.failure(IllegalStateException("nope")),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.isNotEmpty() }

        viewModel.onReactionSelected(postId = 1001L, reactionId = "heart")
        runCurrent()

        val state = viewModel.uiState.value
        assertThat(state.posts.single().currentUserReaction).isNull()
        assertThat(state.posts.single().reactionUsersCount).isEqualTo(0)
        assertThat(state.posts.single().reactions).isEmpty()
    }

    @Test
    fun togglePostBookmarkAppliesOptimisticBookmarkAndCallsRepository() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(loadedPostIds = listOf(1001L), streamPostIds = listOf(1001L)),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.isNotEmpty() }

        viewModel.togglePostBookmark(postId = 1001L)
        val state = viewModel.awaitState { state -> state.posts.single().isBookmarked }
        runCurrent()

        assertThat(state.posts.single().isBookmarked).isTrue()
        assertThat(repository.setPostBookmarkRequests).containsExactly(1001L to true)
    }

    @Test
    fun togglePostBookmarkRollsBackOnFailure() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(loadedPostIds = listOf(1001L), streamPostIds = listOf(1001L)),
            setPostBookmarkResult = Result.failure(IllegalStateException("nope")),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.isNotEmpty() }

        viewModel.togglePostBookmark(postId = 1001L)
        runCurrent()

        val state = viewModel.uiState.value
        assertThat(state.posts.single().isBookmarked).isFalse()
        assertThat(repository.setPostBookmarkRequests).containsExactly(1001L to true)
    }

    @Test
    fun openReactionSheetLoadsUsersAndSelectsInitialReaction() = runTest {
        val groups = listOf(
            PostReactionUsersGroup(
                id = "heart",
                count = 1,
                users = listOf(PostReactionUser(username = "baba")),
            ),
        )
        val repository = FakeTopicRepository(
            detail = topicDetail(loadedPostIds = listOf(1001L), streamPostIds = listOf(1001L)),
            reactionUsers = Result.success(groups),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.isNotEmpty() }

        viewModel.openReactionSheet(
            postId = 1001L,
            reactions = listOf(PostReaction(id = "heart", count = 1)),
            initialReactionId = "heart",
        )
        runCurrent()

        val state = viewModel.uiState.value
        assertThat(state.reactionSheet?.postId).isEqualTo(1001L)
        assertThat(state.reactionUsers.groups).isEqualTo(groups)
        assertThat(state.reactionUsers.selectedReactionId).isEqualTo("heart")
        assertThat(state.reactionUsers.isLoading).isFalse()
        assertThat(state.reactionUsers.hasLoaded).isTrue()
        assertThat(repository.reactionUsersRequests).containsExactly(1001L)
    }

    @Test
    fun reactionUsersCanSelectTelegramStyleAllFilter() = runTest {
        val groups = listOf(
            PostReactionUsersGroup(id = "heart", count = 1, users = listOf(PostReactionUser(username = "baba"))),
            PostReactionUsersGroup(id = "+1", count = 1, users = listOf(PostReactionUser(username = "alice"))),
        )
        val repository = FakeTopicRepository(
            detail = topicDetail(loadedPostIds = listOf(1001L), streamPostIds = listOf(1001L)),
            reactionUsers = Result.success(groups),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.isNotEmpty() }

        viewModel.openReactionSheet(
            postId = 1001L,
            reactions = listOf(PostReaction(id = "heart", count = 1), PostReaction(id = "+1", count = 1)),
            initialReactionId = "heart",
            reactionUsersCount = 2,
        )
        runCurrent()
        viewModel.selectReactionInSheet(TOPIC_REACTION_ALL_FILTER_ID)

        val state = viewModel.uiState.value
        assertThat(state.reactionSheet?.reactionUsersCount).isEqualTo(2)
        assertThat(state.reactionUsers.selectedReactionId).isEqualTo(TOPIC_REACTION_ALL_FILTER_ID)
    }

    @Test
    fun lateReactionUsersResponseCannotOverwriteNewSheet() = runTest {
        val firstResponse = CompletableDeferred<Result<List<PostReactionUsersGroup>>>()
        val secondResponse = CompletableDeferred<Result<List<PostReactionUsersGroup>>>()
        val detail = topicDetail(loadedPostIds = listOf(1001L, 1002L), streamPostIds = listOf(1001L, 1002L))
        val repository = object : TopicRepository() {
            override suspend fun load(topicId: Long): Result<TopicDetail> = detail

            override suspend fun loadReactionUsers(postId: Long): Result<List<PostReactionUsersGroup>> {
                return if (postId == 1001L) firstResponse.await() else secondResponse.await()
            }
        }
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.size == 2 }

        viewModel.openReactionSheet(1001L, listOf(PostReaction("heart", 1)), "heart")
        runCurrent()
        viewModel.openReactionSheet(1002L, listOf(PostReaction("+1", 1)), "+1")
        runCurrent()

        firstResponse.complete(
            Result.success(listOf(PostReactionUsersGroup("heart", 1, listOf(PostReactionUser(username = "old"))))),
        )
        runCurrent()
        assertThat(viewModel.uiState.value.reactionSheet?.postId).isEqualTo(1002L)
        assertThat(viewModel.uiState.value.reactionUsers.groups).isEmpty()
        assertThat(viewModel.uiState.value.reactionUsers.isLoading).isTrue()

        secondResponse.complete(
            Result.success(listOf(PostReactionUsersGroup("+1", 1, listOf(PostReactionUser(username = "new"))))),
        )
        runCurrent()
        assertThat(viewModel.uiState.value.reactionUsers.groups.single().users.single().username).isEqualTo("new")
    }

    @Test
    fun postRepliesOpenFromAuthoritativeCountAndLoadDirectReplies() = runTest {
        val sourcePost = post(id = 1001L, postNumber = 1).copy(replyCount = 2)
        val replies = listOf(
            post(id = 1002L, postNumber = 2).copy(replyToPostNumber = 1),
            post(id = 1003L, postNumber = 3).copy(replyToPostNumber = 1),
        )
        val repository = FakeTopicRepository(
            detail = Result.success(
                TopicDetail(
                    id = 101L,
                    title = "Replies",
                    posts = listOf(sourcePost),
                    totalPosts = 3,
                    streamPostIds = listOf(1001L, 1002L, 1003L),
                ),
            ),
            postReplies = Result.success(replies),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.isNotEmpty() }

        viewModel.openPostReplies(sourcePost)
        runCurrent()

        assertThat(viewModel.uiState.value.postRepliesSheet?.totalCount).isEqualTo(2)
        assertThat(viewModel.uiState.value.postReplies.posts.map { it.id }).containsExactly(1002L, 1003L).inOrder()
        assertThat(viewModel.uiState.value.replyPreviewsBySourcePostId[1001L]?.map { it.id })
            .containsExactly(1002L, 1003L)
            .inOrder()
        assertThat(viewModel.uiState.value.postReplies.endReached).isTrue()
        assertThat(repository.postRepliesRequests).containsExactly(1001L to 1)
    }

    @Test
    fun reactionUsersFailureExposesRetryAndRetriesSamePost() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(loadedPostIds = listOf(1001L), streamPostIds = listOf(1001L)),
            reactionUsers = Result.failure(IllegalStateException("offline")),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.isNotEmpty() }

        viewModel.openReactionSheet(
            postId = 1001L,
            reactions = listOf(PostReaction(id = "heart", count = 1)),
            initialReactionId = "heart",
        )
        runCurrent()

        assertThat(viewModel.uiState.value.reactionUsers.errorMessage).isEqualTo("offline")
        assertThat(viewModel.uiState.value.reactionUsers.isLoading).isFalse()

        viewModel.retryReactionUsers()
        runCurrent()

        assertThat(repository.reactionUsersRequests).containsExactly(1001L, 1001L).inOrder()
        assertThat(viewModel.uiState.value.reactionUsers.errorMessage).isEqualTo("offline")
    }

    @Test
    fun dismissReactionSheetClearsSheetState() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(loadedPostIds = listOf(1001L), streamPostIds = listOf(1001L)),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.isNotEmpty() }
        viewModel.openReactionSheet(
            postId = 1001L,
            reactions = listOf(PostReaction(id = "heart", count = 1)),
            initialReactionId = null,
        )
        runCurrent()

        viewModel.dismissReactionSheet()

        val state = viewModel.uiState.value
        assertThat(state.reactionSheet).isNull()
        assertThat(state.reactionUsers).isEqualTo(TopicReactionUsersUiState())
    }

    @Test
    fun expandQuoteReturnsCookedHtmlFromPostByNumber() = runTest {
        val quotePost = post(id = 2001L, postNumber = 9).copy(cookedHtml = "<p>quoted</p>")
        val repository = FakeTopicRepository(
            detail = topicDetail(loadedPostIds = listOf(1001L), streamPostIds = listOf(1001L)),
            postByNumber = Result.success(quotePost),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.isNotEmpty() }

        val result = viewModel.expandQuote(sourceTopicId = 101L, sourcePostNumber = 9)

        assertThat(result.getOrThrow()).isEqualTo("<p>quoted</p>")
        assertThat(repository.loadPostByNumberRequests).containsExactly(101L to 9)
    }

    @Test
    fun submitReplyCreatesReplyAndAppendsReturnedPost() = runTest {
        val createdPost = post(id = 1002L, postNumber = 2).copy(
            replyToPostNumber = 1,
            cookedHtml = "<p>created reply</p>",
        )
        val repository = FakeTopicRepository(
            detail = topicDetail(loadedPostIds = listOf(1001L), streamPostIds = listOf(1001L)),
            createReplyResult = Result.success(createdPost),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.isNotEmpty() }

        viewModel.submitReply(raw = " reply body ", replyToPostNumber = 1)
        runCurrent()

        val state = viewModel.uiState.value
        assertThat(repository.createReplyRequests).containsExactly(ReplyCreateRequest(101L, "reply body", 1))
        assertThat(state.isSubmittingReply).isFalse()
        assertThat(state.replySubmitErrorMessage).isNull()
        assertThat(state.posts.map { post -> post.id }).containsExactly(1001L, 1002L).inOrder()
        assertThat(state.posts.first().replyCount).isEqualTo(1)
        assertThat(state.posts.last().replyToPostNumber).isEqualTo(1)
        assertThat(state.totalPosts).isEqualTo(2)
        assertThat(state.highestPostNumber).isEqualTo(2)
        assertThat(state.replyDraft).isEqualTo(TopicReplyDraft())
    }

    @Test
    fun submitReplyKeepsHighestPostNumberMonotonic() = runTest {
        val createdPost = post(id = 1002L, postNumber = 1)
        val repository = FakeTopicRepository(
            detail = topicDetail(
                loadedPostIds = listOf(1001L),
                streamPostIds = listOf(1001L),
                highestPostNumber = 7,
            ),
            createReplyResult = Result.success(createdPost),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.isNotEmpty() }

        viewModel.submitReply(raw = "reply", replyToPostNumber = null)
        runCurrent()

        assertThat(viewModel.uiState.value.highestPostNumber).isEqualTo(7)
    }

    @Test
    fun submitReplyFailureKeepsExistingPostsAndStoresError() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(loadedPostIds = listOf(1001L), streamPostIds = listOf(1001L)),
            createReplyResult = Result.failure(IllegalStateException("server error")),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.isNotEmpty() }

        viewModel.submitReply(raw = "reply body", replyToPostNumber = 1)
        runCurrent()

        val state = viewModel.uiState.value
        assertThat(repository.createReplyRequests).containsExactly(ReplyCreateRequest(101L, "reply body", 1))
        assertThat(state.isSubmittingReply).isFalse()
        assertThat(state.replySubmitErrorMessage).isNotEmpty()
        assertThat(state.posts.map { post -> post.id }).containsExactly(1001L)
        assertThat(state.replyDraft).isEqualTo(TopicReplyDraft("reply body", 1))
    }

    @Test
    fun submitReplyIgnoresBlankRawText() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(loadedPostIds = listOf(1001L), streamPostIds = listOf(1001L)),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.isNotEmpty() }

        viewModel.submitReply(raw = " \n ", replyToPostNumber = 1)
        runCurrent()

        assertThat(repository.createReplyRequests).isEmpty()
        assertThat(viewModel.uiState.value.isSubmittingReply).isFalse()
    }

    @Test
    fun onPollOptionsSelectedVotesAndRefreshesPost() = runTest {
        val refreshedPost = post(id = 1001L, postNumber = 1).copy(cookedHtml = "<p>updated poll</p>")
        val repository = FakeTopicRepository(
            detail = topicDetail(loadedPostIds = listOf(1001L), streamPostIds = listOf(1001L)),
            postByNumber = Result.success(refreshedPost),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.isNotEmpty() }

        viewModel.onPollOptionsSelected(postId = 1001L, postNumber = 1, pollName = "frontend", optionIds = listOf("option-a", "option-b"))
        runCurrent()

        assertThat(repository.votePollRequests).containsExactly(PollVoteRequest(1001L, "frontend", listOf("option-a", "option-b")))
        assertThat(repository.loadPostByNumberRequests).containsExactly(101L to 1)
        assertThat(viewModel.uiState.value.posts.single().cookedHtml).isEqualTo("<p>updated poll</p>")
    }

    @Test
    fun onPollVoteRemovedRetractsVoteAndRefreshesPost() = runTest {
        val refreshedPost = post(id = 1001L, postNumber = 1).copy(cookedHtml = "<p>poll retracted</p>")
        val repository = FakeTopicRepository(
            detail = topicDetail(loadedPostIds = listOf(1001L), streamPostIds = listOf(1001L)),
            postByNumber = Result.success(refreshedPost),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.isNotEmpty() }

        viewModel.onPollVoteRemoved(postId = 1001L, postNumber = 1, pollName = "poll")
        runCurrent()

        assertThat(repository.removePollVoteRequests).containsExactly(PollRemoveVoteRequest(1001L, "poll"))
        assertThat(repository.loadPostByNumberRequests).containsExactly(101L to 1)
        assertThat(viewModel.uiState.value.posts.single().cookedHtml).isEqualTo("<p>poll retracted</p>")
    }

    @Test
    fun openPollVotersSheetLoadsAndDismisses() = runTest {
        val voters = listOf(
            PostReactionUsersGroup(
                id = "option-a",
                count = 1,
                users = listOf(
                    io.github.baba983.nativedo.core.model.PostReactionUser(
                        username = "baba",
                        displayName = "Baba",
                    ),
                ),
            ),
        )
        val repository = FakeTopicRepository(
            detail = topicDetail(loadedPostIds = listOf(1001L), streamPostIds = listOf(1001L)),
            pollVoters = Result.success(voters),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.isNotEmpty() }

        viewModel.openPollVotersSheet(
            postId = 1001L,
            args = PollVotersSheetArgs(
                pollName = "frontend",
                pollTitle = "模型订阅的方式",
                options = listOf(
                    PollOptionRef(id = "option-a", label = "国模的API付费", votes = 3),
                    PollOptionRef(id = "option-b", label = "国外模型自己充账号", votes = 5),
                ),
            ),
        )
        runCurrent()

        assertThat(repository.pollVotersRequests).containsExactly(PollVotersRequest(1001L, "frontend", "option-a", 1, 50))
        val loaded = viewModel.awaitState { state -> state.pollVoters.hasLoaded }
        assertThat(loaded.pollVoters.groups).hasSize(2)
        assertThat(loaded.pollVoters.groups.first().users.single().username).isEqualTo("baba")

        viewModel.dismissPollVotersSheet()
        assertThat(viewModel.uiState.value.pollVotersSheet).isNull()
        assertThat(viewModel.uiState.value.pollVoters).isEqualTo(TopicPollVotersUiState())
    }

    @Test
    fun openPollVotersSheetPrefersInitialSelectedOptionWithUsers() = runTest {
        val voters = listOf(
            PostReactionUsersGroup(
                id = "option-a",
                count = 0,
                users = emptyList(),
            ),
            PostReactionUsersGroup(
                id = "option-b",
                count = 1,
                users = listOf(
                    io.github.baba983.nativedo.core.model.PostReactionUser(
                        username = "baba",
                        displayName = "Baba",
                    ),
                ),
            ),
        )
        val repository = FakeTopicRepository(
            detail = topicDetail(loadedPostIds = listOf(1001L), streamPostIds = listOf(1001L)),
            pollVoters = Result.success(voters),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.isNotEmpty() }

        viewModel.openPollVotersSheet(
            postId = 1001L,
            args = PollVotersSheetArgs(
                pollName = "frontend",
                options = listOf(
                    PollOptionRef(id = "option-a", label = "Option A", votes = 0),
                    PollOptionRef(id = "option-b", label = "Option B", votes = 1),
                ),
                initialSelectedOptionId = "option-b",
            ),
        )
        runCurrent()

        val loaded = viewModel.awaitState { state -> state.pollVoters.hasLoaded }
        assertThat(loaded.pollVoters.selectedOptionId).isEqualTo("option-b")
        assertThat(loaded.pollVoters.groups.first { it.id == "option-b" }.users.single().username).isEqualTo("baba")
    }

    @Test
    fun pollVotersLoadsSelectedOptionPagesAndKeepsRealVoteCount() = runTest {
        val firstPage = (1..50).map { index -> PostReactionUser(username = "user-$index") }
        val secondPage = listOf(PostReactionUser(username = "user-51"))
        val repository = FakeTopicRepository(
            detail = topicDetail(loadedPostIds = listOf(1001L), streamPostIds = listOf(1001L)),
            pollVotersByRequest = { request ->
                Result.success(
                    listOf(
                        PostReactionUsersGroup(
                            id = request.optionId.orEmpty(),
                            count = if (request.page == 1) firstPage.size else secondPage.size,
                            users = if (request.page == 1) firstPage else secondPage,
                        ),
                    ),
                )
            },
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.isNotEmpty() }

        viewModel.openPollVotersSheet(
            postId = 1001L,
            args = PollVotersSheetArgs(
                pollName = "frontend",
                options = listOf(PollOptionRef(id = "option-a", label = "Option A", votes = 51)),
            ),
        )
        runCurrent()

        assertThat(viewModel.uiState.value.pollVoters.groups.single().count).isEqualTo(51)
        assertThat(viewModel.uiState.value.pollVoters.groups.single().users).hasSize(50)
        assertThat(viewModel.uiState.value.pollVoters.canLoadMore).isTrue()

        viewModel.loadMorePollVoters()
        runCurrent()

        assertThat(viewModel.uiState.value.pollVoters.groups.single().users).hasSize(51)
        assertThat(viewModel.uiState.value.pollVoters.canLoadMore).isFalse()
        assertThat(repository.pollVotersRequests).containsExactly(
            PollVotersRequest(1001L, "frontend", "option-a", 1, 50),
            PollVotersRequest(1001L, "frontend", "option-a", 2, 50),
        ).inOrder()
    }

    @Test
    fun requestJumpToReplyTargetEmitsEventAndHighlightWithNonce() = runTest {
        val repository = FakeTopicRepository(
            detail = Result.success(
                TopicDetail(
                    id = 101L,
                    title = "Welcome to NativeDo Android",
                    posts = listOf(
                        post(id = 1001L, postNumber = 1),
                        post(id = 1002L, postNumber = 2),
                    ),
                    totalPosts = 2,
                    streamPostIds = listOf(1001L, 1002L),
                ),
            ),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.size == 2 }

        viewModel.requestJumpToReplyTarget(postNumber = 2)
        val firstEvent = viewModel.uiState.value.jumpEvent
        viewModel.consumeJumpEvent(firstEvent!!.nonce)
        viewModel.clearJumpHighlight(firstEvent.nonce)
        viewModel.requestJumpToReplyTarget(postNumber = 2)

        val state = viewModel.uiState.value
        assertThat(firstEvent.itemIndex).isEqualTo(2)
        assertThat(firstEvent.postId).isEqualTo(1002L)
        assertThat(state.jumpEvent?.nonce).isGreaterThan(firstEvent.nonce)
        assertThat(state.jumpHighlight?.postId).isEqualTo(1002L)
    }

    @Test
    fun requestJumpToUnreadTargetsFirstPostAfterReadPosition() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(
                loadedPostIds = listOf(1001L, 1002L, 1003L),
                streamPostIds = listOf(1001L, 1002L, 1003L),
                highestPostNumber = 3,
                lastReadPostNumber = 1,
            ),
        )
        viewModel = TopicViewModel(
            repository = repository,
            topicId = 101L,
            canTrackReadPosition = true,
        )
        viewModel.awaitState { state -> !state.isLoading && state.posts.size == 3 }

        viewModel.requestJumpToUnread()

        assertThat(viewModel.uiState.value.jumpEvent?.postId).isEqualTo(1002L)
        assertThat(viewModel.uiState.value.jumpEvent?.itemIndex).isEqualTo(2)
    }

    @Test
    fun requestJumpToUnreadLoadsFirstUnreadPostWhenItIsNotLoaded() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(
                loadedPostIds = listOf(1001L),
                streamPostIds = listOf(1001L, 1002L, 1003L),
                highestPostNumber = 3,
                lastReadPostNumber = 1,
            ),
            postByNumber = Result.success(post(id = 1002L, postNumber = 2)),
        )
        viewModel = TopicViewModel(
            repository = repository,
            topicId = 101L,
            canTrackReadPosition = true,
        )
        viewModel.awaitState { state -> !state.isLoading && state.posts.size == 1 }

        viewModel.requestJumpToUnread()
        runCurrent()

        assertThat(repository.loadPostByNumberRequests).containsExactly(101L to 2)
        assertThat(viewModel.uiState.value.jumpEvent?.postId).isEqualTo(1002L)
    }

    @Test
    fun requestJumpToReadPositionUsesServerReadPostId() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(
                loadedPostIds = listOf(1001L, 1002L),
                streamPostIds = listOf(1001L, 1002L),
                highestPostNumber = 2,
                lastReadPostNumber = 1,
                lastReadPostId = 1001L,
            ),
        )
        viewModel = TopicViewModel(
            repository = repository,
            topicId = 101L,
            canTrackReadPosition = true,
        )
        viewModel.awaitState { state -> !state.isLoading && state.posts.size == 2 }

        viewModel.requestJumpToReadPosition()

        assertThat(viewModel.uiState.value.jumpEvent?.postId).isEqualTo(1001L)
        assertThat(viewModel.uiState.value.jumpEvent?.itemIndex).isEqualTo(1)
    }

    @Test
    fun requestJumpToReadPositionFallsBackToServerReadPostNumber() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(
                loadedPostIds = listOf(1001L),
                streamPostIds = listOf(1001L, 1002L),
                highestPostNumber = 2,
                lastReadPostNumber = 2,
            ),
            postByNumber = Result.success(post(id = 1002L, postNumber = 2)),
        )
        viewModel = TopicViewModel(
            repository = repository,
            topicId = 101L,
            canTrackReadPosition = true,
        )
        viewModel.awaitState { state -> !state.isLoading && state.posts.size == 1 }

        viewModel.requestJumpToReadPosition()
        runCurrent()

        assertThat(repository.loadPostByNumberRequests).containsExactly(101L to 2)
        assertThat(viewModel.uiState.value.jumpEvent?.postId).isEqualTo(1002L)
    }

    @Test
    fun initialLoadPublishesReadPositionWindowInsteadOfFirstFloor() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(
                loadedPostIds = listOf(1001L, 1002L, 1003L, 1004L),
                streamPostIds = listOf(1001L, 1002L, 1003L, 1004L, 1005L),
                highestPostNumber = 5,
                lastReadPostNumber = 4,
                lastReadPostId = 1004L,
            ),
            detailAtPostNumber = topicDetail(
                loadedPostIds = listOf(1003L, 1004L, 1005L),
                streamPostIds = listOf(1001L, 1002L, 1003L, 1004L, 1005L),
                highestPostNumber = 5,
                lastReadPostNumber = 4,
                lastReadPostId = 1004L,
            ),
        )
        viewModel = TopicViewModel(
            repository = repository,
            topicId = 101L,
            canTrackReadPosition = true,
        )

        val state = viewModel.awaitState { !it.isLoading }

        assertThat(repository.loadRequests).containsExactly(101L)
        assertThat(repository.loadAtPostNumberRequests).containsExactly(101L to 4)
        assertThat(state.posts.map(Post::id)).containsExactly(1003L, 1004L, 1005L).inOrder()
        assertThat(state.posts.mapNotNull(Post::postNumber)).doesNotContain(1)
        assertThat(state.hasPrevious).isTrue()
        assertThat(state.hasMore).isFalse()
    }

    @Test
    fun initialExplicitPostNumberLoadsCenteredWindowDirectly() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(
                loadedPostIds = listOf(1001L),
                streamPostIds = listOf(1001L),
            ),
            detailAtPostNumber = topicDetail(
                loadedPostIds = listOf(1003L, 1004L),
                streamPostIds = listOf(1001L, 1002L, 1003L, 1004L, 1005L),
            ),
        )
        viewModel = TopicViewModel(
            repository = repository,
            topicId = 101L,
            initialPostNumber = 4,
        )

        val state = viewModel.awaitState { !it.isLoading }

        assertThat(repository.loadRequests).isEmpty()
        assertThat(repository.loadAtPostNumberRequests).containsExactly(101L to 4)
        assertThat(state.posts.map(Post::id)).containsExactly(1003L, 1004L).inOrder()
    }

    @Test
    fun loadPreviousFetchesAdjacentIdsBeforeReadPositionWindow() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(
                loadedPostIds = listOf(1001L),
                streamPostIds = listOf(1001L, 1002L, 1003L, 1004L, 1005L),
                lastReadPostNumber = 4,
                lastReadPostId = 1004L,
            ),
            detailAtPostNumber = topicDetail(
                loadedPostIds = listOf(1003L, 1004L, 1005L),
                streamPostIds = listOf(1001L, 1002L, 1003L, 1004L, 1005L),
                lastReadPostNumber = 4,
                lastReadPostId = 1004L,
            ),
            posts = Result.success(
                listOf(
                    post(id = 1001L, postNumber = 1),
                    post(id = 1002L, postNumber = 2),
                ),
            ),
        )
        viewModel = TopicViewModel(
            repository = repository,
            topicId = 101L,
            canTrackReadPosition = true,
        )
        viewModel.awaitState { !it.isLoading }

        viewModel.loadPrevious()
        val state = viewModel.awaitState { it.posts.size == 5 }

        assertThat(repository.loadPostRequests).containsExactly(101L to listOf(1001L, 1002L))
        assertThat(state.posts.map(Post::id)).containsExactly(1001L, 1002L, 1003L, 1004L, 1005L).inOrder()
        assertThat(state.hasPrevious).isFalse()
    }

    @Test
    fun requestJumpToUnloadedReplyFetchesPostBeforeEmittingJump() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(loadedPostIds = listOf(1001L), streamPostIds = listOf(1001L, 1002L)),
            postByNumber = Result.success(post(id = 1002L, postNumber = 2)),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.size == 1 }

        viewModel.requestJumpToReplyTarget(postNumber = 2)
        runCurrent()

        assertThat(repository.loadPostByNumberRequests).containsExactly(101L to 2)
        assertThat(viewModel.uiState.value.posts.map { it.id }).containsExactly(1001L, 1002L).inOrder()
        assertThat(viewModel.uiState.value.jumpEvent?.postId).isEqualTo(1002L)
    }

    @Test
    fun requestJumpToUnloadedPostIdFetchesExactActivityPost() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(loadedPostIds = listOf(1001L), streamPostIds = listOf(1001L, 1002L)),
            posts = Result.success(listOf(post(id = 1002L, postNumber = 2))),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { state -> !state.isLoading && state.posts.size == 1 }

        viewModel.requestJumpToPostId(postId = 1002L)
        runCurrent()

        assertThat(repository.loadPostRequests).containsExactly(101L to listOf(1002L))
        assertThat(viewModel.uiState.value.jumpEvent?.postId).isEqualTo(1002L)
        assertThat(viewModel.uiState.value.jumpEvent?.itemIndex).isEqualTo(2)
    }

    @Test
    fun requestJumpToLatestUsesLoadedPostWithoutNetworkRequest() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(
                loadedPostIds = listOf(1001L, 1002L),
                streamPostIds = listOf(1001L, 1002L),
            ),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { !it.isLoading }

        viewModel.requestJumpToLatest()

        assertThat(repository.loadPostRequests).isEmpty()
        assertThat(viewModel.uiState.value.jumpEvent?.postId).isEqualTo(1002L)
        assertThat(viewModel.uiState.value.jumpEvent?.itemIndex).isEqualTo(2)
    }

    @Test
    fun requestJumpToLatestLoadsTailAndMergesInCanonicalStreamOrder() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(
                loadedPostIds = listOf(1001L),
                streamPostIds = listOf(1001L, 1002L, 1003L),
            ),
            posts = Result.success(
                listOf(post(id = 1003L, postNumber = 3), post(id = 1002L, postNumber = 2)),
            ),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { !it.isLoading }

        viewModel.requestJumpToLatest()
        runCurrent()

        assertThat(repository.loadPostRequests)
            .containsExactly(101L to listOf(1001L, 1002L, 1003L))
        assertThat(viewModel.uiState.value.posts.map(Post::id))
            .containsExactly(1001L, 1002L, 1003L)
            .inOrder()
        assertThat(viewModel.uiState.value.jumpEvent?.postId).isEqualTo(1003L)
        assertThat(viewModel.uiState.value.jumpEvent?.itemIndex).isEqualTo(3)
        assertThat(viewModel.uiState.value.isJumpingToLatest).isFalse()
    }

    @Test
    fun requestJumpToLatestFailureKeepsPostsAndExposesRetryableError() = runTest {
        val repository = FakeTopicRepository(
            detail = topicDetail(
                loadedPostIds = listOf(1001L),
                streamPostIds = listOf(1001L, 1002L),
            ),
            posts = Result.failure(IllegalStateException("tail failed")),
        )
        viewModel = TopicViewModel(repository = repository, topicId = 101L)
        viewModel.awaitState { !it.isLoading }

        viewModel.requestJumpToLatest()
        runCurrent()

        assertThat(viewModel.uiState.value.posts.map(Post::id)).containsExactly(1001L)
        assertThat(viewModel.uiState.value.isJumpingToLatest).isFalse()
        assertThat(viewModel.uiState.value.jumpToLatestErrorMessage).contains("tail failed")
    }

    private suspend fun TopicViewModel.awaitState(
        predicate: (TopicUiState) -> Boolean,
    ): TopicUiState = withTimeout(2_000L) {
        uiState.first(predicate)
    }

    private class FakeTopicRepository(
        private val detail: Result<TopicDetail>,
        private val detailAtPostNumber: Result<TopicDetail> = Result.failure(
            IllegalStateException("topic window not configured"),
        ),
        private val posts: Result<List<Post>> = Result.success(emptyList()),
        private val postByNumber: Result<Post> = Result.failure(IllegalStateException("post not configured")),
        private val postReplies: Result<List<Post>> = Result.success(emptyList()),
        private val reactionUsers: Result<List<PostReactionUsersGroup>> = Result.success(emptyList()),
        private val pollVoters: Result<List<PostReactionUsersGroup>> = Result.success(emptyList()),
        private val pollVotersByRequest: ((PollVotersRequest) -> Result<List<PostReactionUsersGroup>>)? = null,
        private val toggleReactionResult: Result<Unit> = Result.success(Unit),
        private val setPostBookmarkResult: Result<Unit> = Result.success(Unit),
        private val votePollResult: Result<Unit> = Result.success(Unit),
        private val removePollVoteResult: Result<Unit> = Result.success(Unit),
        private val createReplyResult: Result<Post> = Result.failure(IllegalStateException("reply not configured")),
    ) : TopicRepository() {
        val loadRequests = mutableListOf<Long>()
        val loadAtPostNumberRequests = mutableListOf<Pair<Long, Int>>()
        val loadPostRequests = mutableListOf<Pair<Long, List<Long>>>()
        val loadPostByNumberRequests = mutableListOf<Pair<Long, Int>>()
        val postRepliesRequests = mutableListOf<Pair<Long, Int>>()
        val createReplyRequests = mutableListOf<ReplyCreateRequest>()
        val reactionUsersRequests = mutableListOf<Long>()
        val toggleReactionRequests = mutableListOf<Pair<Long, String>>()
        val setPostBookmarkRequests = mutableListOf<Pair<Long, Boolean>>()
        val votePollRequests = mutableListOf<PollVoteRequest>()
        val removePollVoteRequests = mutableListOf<PollRemoveVoteRequest>()
        val pollVotersRequests = mutableListOf<PollVotersRequest>()
        val readReportRequests = mutableListOf<ReadReportRequest>()

        override suspend fun load(topicId: Long): Result<TopicDetail> {
            loadRequests += topicId
            return detail
        }

        override suspend fun loadAtPostNumber(topicId: Long, postNumber: Int): Result<TopicDetail> {
            loadAtPostNumberRequests += topicId to postNumber
            return detailAtPostNumber
        }

        override suspend fun loadPosts(topicId: Long, postIds: List<Long>): Result<List<Post>> {
            loadPostRequests += topicId to postIds
            return posts
        }

        override suspend fun markTopicRead(
            topicId: Long,
            topicTimeMillis: Long,
            postTimings: Map<Int, Int>,
        ): Result<Unit> {
            readReportRequests += ReadReportRequest(topicId, topicTimeMillis, postTimings)
            return Result.success(Unit)
        }

        override suspend fun loadPostByNumber(topicId: Long, postNumber: Int): Result<Post> {
            loadPostByNumberRequests += topicId to postNumber
            return postByNumber
        }

        override suspend fun loadPostReplies(postId: Long, afterPostNumber: Int): Result<List<Post>> {
            postRepliesRequests += postId to afterPostNumber
            return postReplies
        }

        override suspend fun createReply(topicId: Long, raw: String, replyToPostNumber: Int?): Result<Post> {
            createReplyRequests += ReplyCreateRequest(topicId, raw, replyToPostNumber)
            return createReplyResult
        }

        override suspend fun loadReactionUsers(postId: Long): Result<List<PostReactionUsersGroup>> {
            reactionUsersRequests += postId
            return reactionUsers
        }

        override suspend fun loadPollVoters(
            postId: Long,
            pollName: String,
            optionId: String?,
            page: Int,
            limit: Int,
        ): Result<List<PostReactionUsersGroup>> {
            val request = PollVotersRequest(postId, pollName, optionId, page, limit)
            pollVotersRequests += request
            return pollVotersByRequest?.invoke(request) ?: pollVoters
        }

        override suspend fun toggleReaction(postId: Long, reactionId: String): Result<Unit> {
            toggleReactionRequests += postId to reactionId
            return toggleReactionResult
        }

        override suspend fun setPostBookmark(postId: Long, bookmarked: Boolean): Result<Unit> {
            setPostBookmarkRequests += postId to bookmarked
            return setPostBookmarkResult
        }

        override suspend fun votePoll(postId: Long, pollName: String, optionIds: List<String>): Result<Unit> {
            votePollRequests += PollVoteRequest(postId, pollName, optionIds)
            return votePollResult
        }

        override suspend fun removePollVote(postId: Long, pollName: String): Result<Unit> {
            removePollVoteRequests += PollRemoveVoteRequest(postId, pollName)
            return removePollVoteResult
        }
    }

    private data class PollVoteRequest(
        val postId: Long,
        val pollName: String,
        val optionIds: List<String>,
    )

    private data class ReadReportRequest(
        val topicId: Long,
        val topicTimeMillis: Long,
        val postTimings: Map<Int, Int>,
    )

    private data class PollRemoveVoteRequest(
        val postId: Long,
        val pollName: String,
    )

    private data class PollVotersRequest(
        val postId: Long,
        val pollName: String,
        val optionId: String?,
        val page: Int,
        val limit: Int,
    )

    private data class ReplyCreateRequest(
        val topicId: Long,
        val raw: String,
        val replyToPostNumber: Int?,
    )

    private class FakeReplyDraftPersistence(
        val values: MutableMap<TopicReplyDraftKey, TopicReplyDraft> = mutableMapOf(),
    ) : TopicReplyDraftPersistence {
        override fun load(key: TopicReplyDraftKey): TopicReplyDraft = values[key] ?: TopicReplyDraft()

        override fun save(key: TopicReplyDraftKey, draft: TopicReplyDraft) {
            if (draft.raw.isBlank() && draft.replyToPostNumber == null) {
                values.remove(key)
            } else {
                values[key] = draft
            }
        }
    }

    private class FakeReadPositionPersistence(
        val values: MutableMap<TopicReadPositionKey, Int> = mutableMapOf(),
    ) : TopicReadPositionPersistence {
        override fun load(key: TopicReadPositionKey): Int = values[key] ?: 0

        override fun save(key: TopicReadPositionKey, postNumber: Int) {
            values[key] = postNumber
        }
    }

    private fun topicDetail(
        loadedPostIds: List<Long>,
        streamPostIds: List<Long>,
        participants: List<MentionCandidate> = emptyList(),
        highestPostNumber: Int = 0,
        lastReadPostNumber: Int = 0,
        lastReadPostId: Long? = null,
    ): Result<TopicDetail> = Result.success(
        TopicDetail(
            id = 101L,
            title = "Welcome to NativeDo Android",
            validReactions = listOf("heart"),
            emojiCatalog = mapOf("heart" to "heart.png"),
            participants = participants,
            posts = loadedPostIds.map { id ->
                post(id = id, postNumber = streamPostIds.indexOf(id).takeIf { it >= 0 }?.plus(1))
            },
            totalPosts = streamPostIds.size,
            streamPostIds = streamPostIds,
            highestPostNumber = highestPostNumber,
            lastReadPostNumber = lastReadPostNumber,
            lastReadPostId = lastReadPostId,
        ),
    )

    private fun post(
        id: Long,
        postNumber: Int? = null,
    ): Post = Post(
        id = id,
        postNumber = postNumber,
        authorUsername = "baba",
        cookedHtml = "<p>Post $id</p>",
    )
}
