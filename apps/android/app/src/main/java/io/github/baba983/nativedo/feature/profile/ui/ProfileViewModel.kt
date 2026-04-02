package io.github.baba983.nativedo.feature.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.baba983.nativedo.feature.profile.domain.LoadProfileUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val loadProfile: LoadProfileUseCase,
    private val viewer: ProfileViewer,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState(viewer = viewer))
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        when (val currentViewer = viewer) {
            ProfileViewer.Self -> {
                _uiState.value = ProfileUiState(
                    viewer = currentViewer,
                    isLoading = false,
                )
            }

            is ProfileViewer.Other -> {
                viewModelScope.launch {
                    _uiState.value = _uiState.value.copy(
                        isLoading = true,
                        errorMessage = null,
                    )
                    loadProfile(currentViewer.username).fold(
                        onSuccess = { profile ->
                            _uiState.value = ProfileUiState(
                                viewer = currentViewer,
                                profile = profile,
                                isLoading = false,
                            )
                        },
                        onFailure = { error ->
                            _uiState.value = ProfileUiState(
                                viewer = currentViewer,
                                isLoading = false,
                                errorMessage = error.message ?: "Failed to load profile",
                            )
                        },
                    )
                }
            }
        }
    }
}
