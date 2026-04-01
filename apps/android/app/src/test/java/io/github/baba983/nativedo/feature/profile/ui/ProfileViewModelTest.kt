package io.github.baba983.nativedo.feature.profile.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.UserProfile
import io.github.baba983.nativedo.feature.profile.domain.LoadProfileUseCase
import io.github.baba983.nativedo.test.MainDispatcherRule
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
}

private class FakeLoadProfileUseCase(
    private val profile: UserProfile,
) : LoadProfileUseCase() {
    override suspend fun invoke(username: String): Result<UserProfile> = Result.success(profile)
}
