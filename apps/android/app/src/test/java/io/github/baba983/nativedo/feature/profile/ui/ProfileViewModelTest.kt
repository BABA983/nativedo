package io.github.baba983.nativedo.feature.profile.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.UserProfile
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.session.SessionFreshness
import io.github.baba983.nativedo.core.session.SessionSnapshot
import io.github.baba983.nativedo.feature.profile.data.ProfileRepository
import io.github.baba983.nativedo.test.MainDispatcherRule
import kotlinx.coroutines.flow.first
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

        override suspend fun load(username: String): Result<UserProfile> {
            requests += username
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
