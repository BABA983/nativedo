package io.github.baba983.nativedo.feature.profile.ui

import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.core.model.UserProfile

sealed interface ProfileViewer {
    data object Self : ProfileViewer

    data class Other(val username: String) : ProfileViewer
}

data class ProfileUiState(
    val viewer: ProfileViewer,
    val profile: UserProfile? = null,
    val recentTopics: List<TopicSummary> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isAuthRecoveryRequired: Boolean = false,
)
