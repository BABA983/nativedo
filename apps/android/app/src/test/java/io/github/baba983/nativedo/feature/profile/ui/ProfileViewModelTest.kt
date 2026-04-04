package io.github.baba983.nativedo.feature.profile.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.UserProfile
import io.github.baba983.nativedo.core.network.SessionRecoveryRequiredException
import io.github.baba983.nativedo.feature.profile.domain.LoadProfileUseCase
import io.github.baba983.nativedo.test.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadProfile_setsViewerAndProfile() = runTest {
        val viewModel = ProfileViewModel(
            loadProfile = FakeLoadProfileUseCase(
                UserProfile(
                    username = "baba",
                    displayName = "Baba",
                    bio = "Compose user",
                ),
            ),
            viewer = ProfileViewer.Other("baba"),
        )

        runCurrent()

        assertThat(viewModel.uiState.value.profile?.username).isEqualTo("baba")
    }

    @Test
    fun loadProfile_failureWithoutMessage_setsFallbackErrorMessage() = runTest {
        val viewModel = ProfileViewModel(
            loadProfile = FailingLoadProfileUseCase(RuntimeException()),
            viewer = ProfileViewer.Other("baba"),
        )

        runCurrent()

        assertThat(viewModel.uiState.value.errorMessage).isEqualTo("Failed to load profile")
    }

    @Test
    fun loadProfile_sessionRecoveryError_setsAuthRecoveryRequired() = runTest {
        val viewModel = ProfileViewModel(
            loadProfile = FailingLoadProfileUseCase(SessionRecoveryRequiredException()),
            viewer = ProfileViewer.Other("baba"),
        )

        runCurrent()

        assertThat(viewModel.uiState.value.isAuthRecoveryRequired).isTrue()
        assertThat(viewModel.uiState.value.errorMessage).isNull()
    }

    @Test
    fun onAuthRecoveryHandled_clearsAuthRecoveryRequired() = runTest {
        val viewModel = ProfileViewModel(
            loadProfile = FailingLoadProfileUseCase(SessionRecoveryRequiredException()),
            viewer = ProfileViewer.Other("baba"),
        )

        runCurrent()
        viewModel.onAuthRecoveryHandled()

        assertThat(viewModel.uiState.value.isAuthRecoveryRequired).isFalse()
    }

    @Test
    fun refresh_ignoresResultWhenSessionVersionChangesBeforeCompletion() = runTest {
        var sessionVersion = 1L
        val useCase = DelayedLoadProfileUseCase()
        val viewModel = ProfileViewModel(
            loadProfile = useCase,
            viewer = ProfileViewer.Other("baba"),
            currentSessionVersion = { sessionVersion },
        )

        runCurrent()
        sessionVersion = 2L
        useCase.complete(
            UserProfile(
                username = "baba",
                displayName = "Baba",
                bio = "",
            ),
        )
        runCurrent()

        assertThat(viewModel.uiState.value.profile).isNull()
        assertThat(viewModel.uiState.value.isLoading).isFalse()
        assertThat(viewModel.uiState.value.errorMessage).isNull()
    }
}

private class FakeLoadProfileUseCase(
    private val profile: UserProfile,
) : LoadProfileUseCase() {
    override suspend fun invoke(username: String): Result<UserProfile> = Result.success(profile)
}

private class FailingLoadProfileUseCase(
    private val error: Throwable,
) : LoadProfileUseCase() {
    override suspend fun invoke(username: String): Result<UserProfile> = Result.failure(error)
}

private class DelayedLoadProfileUseCase : LoadProfileUseCase() {
    private val deferredResult = CompletableDeferred<Result<UserProfile>>()

    override suspend fun invoke(username: String): Result<UserProfile> = deferredResult.await()

    fun complete(profile: UserProfile) {
        deferredResult.complete(Result.success(profile))
    }
}
