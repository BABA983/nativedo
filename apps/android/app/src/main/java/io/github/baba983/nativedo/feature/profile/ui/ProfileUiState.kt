package io.github.baba983.nativedo.feature.profile.ui

import io.github.baba983.nativedo.core.model.UserProfile

data class ProfileUiState(
    val username: String? = null,
    val profile: UserProfile? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
