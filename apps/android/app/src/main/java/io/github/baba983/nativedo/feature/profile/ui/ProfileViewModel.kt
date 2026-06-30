package io.github.baba983.nativedo.feature.profile.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import io.github.baba983.nativedo.core.session.AppSessionState
import io.github.baba983.nativedo.core.storage.CategoryStore
import io.github.baba983.nativedo.feature.home.ui.readableMessage
import io.github.baba983.nativedo.feature.profile.data.ProfileFeedRepository
import io.github.baba983.nativedo.feature.profile.data.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val repository: ProfileRepository,
    private val feedRepository: ProfileFeedRepository? = null,
    private val categoryStore: CategoryStore? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var currentUsername: String? = null

    fun onSessionStateChanged(sessionState: AppSessionState) {
        val username = (sessionState as? AppSessionState.Authenticated)?.username
        if (username == null) {
            currentUsername = null
            _uiState.value = ProfileUiState()
            return
        }
        if (username == currentUsername && _uiState.value.profile != null) {
            return
        }
        currentUsername = username
        load(username)
    }

    fun retry() {
        currentUsername?.let(::load)
    }

    fun retryFeed() {
        currentUsername?.let(::loadFeed)
    }

    // Java-interop bridge: expose profile state as LiveData for Java Fragments
    fun getStateLiveData(): LiveData<ProfileUiState> = uiState.asLiveData()

    private fun load(username: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                username = username,
                isLoading = true,
                errorMessage = null,
                recentTopics = emptyList(),
                recentReplies = emptyList(),
                isFeedLoading = false,
                feedErrorMessage = null,
            )
            repository.load(username).fold(
                onSuccess = { profile ->
                    if (username != currentUsername) {
                        return@launch
                    }
                    _uiState.value = ProfileUiState(
                        username = username,
                        profile = profile,
                        isLoading = false,
                    )
                    loadFeed(username)
                },
                onFailure = { error ->
                    if (username != currentUsername) {
                        return@launch
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.readableMessage(),
                        isFeedLoading = false,
                    )
                },
            )
        }
    }

    private fun loadFeed(username: String) {
        val feedRepository = feedRepository ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isFeedLoading = true,
                feedErrorMessage = null,
            )

            val categories = categoryStore
                ?.loadIfNeeded()
                ?.getOrElse { emptyMap() }
                ?: emptyMap()
            val topicsResult = feedRepository.load(
                username = username,
                filter = ProfileFeedRepository.FILTER_TOPICS,
                offset = 0,
            )
            val repliesResult = feedRepository.load(
                username = username,
                filter = ProfileFeedRepository.FILTER_REPLIES,
                offset = 0,
            )

            if (username != currentUsername) {
                return@launch
            }

            val feedError = topicsResult.exceptionOrNull() ?: repliesResult.exceptionOrNull()
            _uiState.value = _uiState.value.copy(
                categories = categories,
                recentTopics = topicsResult.getOrElse { emptyList() },
                recentReplies = repliesResult.getOrElse { emptyList() },
                isFeedLoading = false,
                feedErrorMessage = feedError?.readableMessage(),
            )
        }
    }
}
