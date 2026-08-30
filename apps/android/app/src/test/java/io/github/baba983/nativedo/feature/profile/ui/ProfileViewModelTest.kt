package io.github.baba983.nativedo.feature.profile.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.UserActionPage
import io.github.baba983.nativedo.core.model.UserProfile
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.session.SessionFreshness
import io.github.baba983.nativedo.core.session.SessionSnapshot
import io.github.baba983.nativedo.feature.profile.data.ProfileFeedRepository
import io.github.baba983.nativedo.feature.profile.data.ProfileRepository
import io.github.baba983.nativedo.test.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test

class ProfileViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: ProfileViewModel

    @Test
    fun authenticatedSessionLoadsProfile() = runTest {
        val repository = FakeProfileRepository(
            "baba" to Result.success(profile("baba", displayName = "Baba")),
        )
        viewModel = ProfileViewModel(repository)

        viewModel.onSessionStateChanged(authenticated("baba"))

        val state = viewModel.awaitState { state -> !state.isLoading && state.profile != null }
        assertThat(state.username).isEqualTo("baba")
        assertThat(state.profile?.displayName).isEqualTo("Baba")
        assertThat(repository.requests).containsExactly("baba")
    }

    @Test
    fun cachedProfileIsRefreshedFromNetwork() = runTest {
        val repository = FakeProfileRepository(
            "baba" to Result.success(profile("baba", displayName = "Network Baba")),
        ).apply {
            cachedProfiles["baba"] = profile("baba", displayName = "Cached Baba")
        }
        viewModel = ProfileViewModel(repository)

        viewModel.onSessionStateChanged(authenticated("baba"))

        val state = viewModel.awaitState { profileState ->
            !profileState.isLoading && profileState.profile?.displayName == "Network Baba"
        }
        assertThat(state.profile?.displayName).isEqualTo("Network Baba")
        assertThat(repository.loadRequests).isEmpty()
        assertThat(repository.refreshRequests).containsExactly("baba")
    }

    @Test
    fun cachedProfileDoesNotLoadEachFeedTwiceAfterRefresh() = runTest {
        val profileRepository = FakeProfileRepository(
            "baba" to Result.success(profile("baba", displayName = "Network Baba")),
        ).apply {
            cachedProfiles["baba"] = profile("baba", displayName = "Cached Baba")
        }
        val feedRequests = mutableListOf<String>()
        val feedRepository = object : ProfileFeedRepository() {
            override suspend fun load(
                username: String,
                filter: String,
                offset: Int,
            ): Result<UserActionPage> {
                feedRequests += filter
                return Result.success(UserActionPage(emptyList(), rawCount = 0))
            }
        }
        viewModel = ProfileViewModel(profileRepository, feedRepository)

        viewModel.onSessionStateChanged(authenticated("baba"))
        advanceUntilIdle()

        assertThat(feedRequests).containsExactly(
            ProfileFeedRepository.FILTER_TOPICS,
            ProfileFeedRepository.FILTER_REPLIES,
        ).inOrder()
    }

    @Test
    fun replayingSameAuthenticatedSessionDoesNotDuplicateInFlightProfileLoad() = runTest {
        val response = CompletableDeferred<Result<UserProfile>>()
        var loadCount = 0
        val repository = object : ProfileRepository() {
            override suspend fun load(username: String): Result<UserProfile> {
                loadCount += 1
                return response.await()
            }
        }
        viewModel = ProfileViewModel(repository)

        viewModel.onSessionStateChanged(authenticated("baba"))
        runCurrent()
        viewModel.onSessionStateChanged(authenticated("baba"))
        runCurrent()

        assertThat(loadCount).isEqualTo(1)
        response.complete(Result.success(profile("baba", displayName = "Baba")))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.profile?.username).isEqualTo("baba")
    }

    @Test
    fun anonymousSessionClearsProfile() = runTest {
        val repository = FakeProfileRepository(
            "baba" to Result.success(profile("baba", displayName = "Baba")),
        )
        viewModel = ProfileViewModel(repository)
        viewModel.onSessionStateChanged(authenticated("baba"))
        viewModel.awaitState { state -> state.profile != null }

        viewModel.onSessionStateChanged(AppSessionState.Anonymous)

        val state = viewModel.uiState.value
        assertThat(state.username).isNull()
        assertThat(state.profile).isNull()
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun failedLoadCanRetry() = runTest {
        val repository = FakeProfileRepository(
            "baba" to Result.failure(IllegalStateException("server error")),
        )
        viewModel = ProfileViewModel(repository)
        viewModel.onSessionStateChanged(authenticated("baba"))
        viewModel.awaitState { state -> state.errorMessage != null }
        repository.responses["baba"] = Result.success(profile("baba", displayName = "Baba"))

        viewModel.retry()

        val state = viewModel.awaitState { state -> !state.isLoading && state.profile != null }
        assertThat(state.errorMessage).isNull()
        assertThat(state.profile?.username).isEqualTo("baba")
        assertThat(repository.requests).containsExactly("baba", "baba").inOrder()
    }

    private suspend fun ProfileViewModel.awaitState(
        predicate: (ProfileUiState) -> Boolean,
    ): ProfileUiState = withTimeout(2_000L) {
        uiState.first(predicate)
    }

    private class FakeProfileRepository(
        vararg responses: Pair<String, Result<UserProfile>>,
    ) : ProfileRepository() {
        val responses = responses.toMap().toMutableMap()
        val requests = mutableListOf<String>()
        val loadRequests = mutableListOf<String>()
        val refreshRequests = mutableListOf<String>()
        val cachedProfiles = mutableMapOf<String, UserProfile>()

        override fun cachedProfile(username: String): UserProfile? = cachedProfiles[username]

        override suspend fun load(username: String): Result<UserProfile> {
            requests += username
            loadRequests += username
            return responses.getValue(username)
        }

        override suspend fun refresh(username: String): Result<UserProfile> {
            requests += username
            refreshRequests += username
            return responses.getValue(username)
        }
    }

    private fun authenticated(username: String): AppSessionState.Authenticated =
        AppSessionState.Authenticated(
            snapshot = SessionSnapshot(
                username = username,
                displayName = username,
                avatarTemplate = "",
                lastValidatedAt = 1L,
            ),
            freshness = SessionFreshness.Verified,
        )

    private fun profile(username: String, displayName: String): UserProfile =
        UserProfile(
            username = username,
            displayName = displayName,
            bio = "<p>Bio</p>",
        )
}
