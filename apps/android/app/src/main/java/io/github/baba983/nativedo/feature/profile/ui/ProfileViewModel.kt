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
import kotlinx.coroutines.Job
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

    private var profileLoadJob: Job? = null
    private var profileLoadUsername: String? = null
    private var profileLoadGeneration = 0
    private var feedRequestUsername: String? = null
    private var feedRequestGeneration = 0
    fun onSessionStateChanged(sessionState: AppSessionState) {
        val username = (sessionState as? AppSessionState.Authenticated)?.username
        if (username == null) {
            currentUsername = null
            profileLoadJob?.cancel()
            profileLoadJob = null
            profileLoadUsername = null
            profileLoadGeneration++
            feedRequestUsername = null
            feedRequestGeneration++
            _uiState.value = ProfileUiState()
            return
        }
        if (username == currentUsername && _uiState.value.profile != null) {
            return
        }
        currentUsername = username
        val cached = repository.cachedProfile(username)
        if (cached != null) {
            _uiState.value = ProfileUiState(
                username = username,
                profile = cached,
                isLoading = false,
            )
            loadFeed(username)
        }
        load(username, forceRefresh = cached != null)
    }

    fun retry() {
        currentUsername?.let { username -> load(username, forceRefresh = true) }
    }

    fun retryFeed() {
        currentUsername?.let { username -> loadFeed(username, forceRefresh = true) }
    }

    // Java-interop bridge: expose profile state as LiveData for Java Fragments
    fun getStateLiveData(): LiveData<ProfileUiState> = uiState.asLiveData()

    private fun load(username: String, forceRefresh: Boolean = false) {
        if (profileLoadUsername == username && profileLoadJob?.isActive == true) return
        profileLoadJob?.cancel()
        profileLoadUsername = username
        val generation = ++profileLoadGeneration
        profileLoadJob = viewModelScope.launch {
            try {
                val hasCachedProfile = forceRefresh &&
                    _uiState.value.username == username &&
                    _uiState.value.profile != null
                _uiState.value = if (hasCachedProfile) {
                    _uiState.value.copy(
                        username = username,
                        isLoading = false,
                        errorMessage = null,
                    )
                } else {
                    _uiState.value.copy(
                        username = username,
                        isLoading = true,
                        errorMessage = null,
                        recentTopics = emptyList(),
                        recentReplies = emptyList(),
                        isFeedLoading = false,
                        feedErrorMessage = null,
                    )
                }
                val result = if (forceRefresh) repository.refresh(username) else repository.load(username)
                result.fold(
                    onSuccess = { profile ->
                        if (username != currentUsername) return@fold
                        _uiState.value = _uiState.value.copy(
                            username = username,
                            profile = profile,
                            isLoading = false,
                            errorMessage = null,
                        )
                        loadFeed(username)
                    },
                    onFailure = { error ->
                        if (username != currentUsername) return@fold
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = error.readableMessage(),
                            isFeedLoading = false,
                        )
                    },
                )
            } finally {
                if (generation == profileLoadGeneration && profileLoadUsername == username) {
                    profileLoadJob = null
                    profileLoadUsername = null
                }
            }
        }
    }


    private fun loadFeed(username: String, forceRefresh: Boolean = false) {
        val feedRepository = feedRepository ?: return
        if (!forceRefresh && feedRequestUsername == username) return
        feedRequestUsername = username
        val generation = ++feedRequestGeneration
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

            if (username != currentUsername || generation != feedRequestGeneration) {
                return@launch
            }

            val feedError = topicsResult.exceptionOrNull() ?: repliesResult.exceptionOrNull()
            _uiState.value = _uiState.value.copy(
                categories = categories,
                recentTopics = topicsResult.getOrNull()?.items.orEmpty(),
                recentReplies = repliesResult.getOrNull()?.items.orEmpty(),
                isFeedLoading = false,
                feedErrorMessage = feedError?.readableMessage(),
            )
        }
    }
}
