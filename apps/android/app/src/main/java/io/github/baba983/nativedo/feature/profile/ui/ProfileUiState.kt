package io.github.baba983.nativedo.feature.profile.ui

import io.github.baba983.nativedo.core.model.CategorySummary
import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.core.model.UserProfile

data class ProfileUiState(
    val username: String? = null,
    val profile: UserProfile? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val categories: Map<Int, CategorySummary> = emptyMap(),
    val recentTopics: List<TopicSummary> = emptyList(),
    val recentReplies: List<TopicSummary> = emptyList(),
    val isFeedLoading: Boolean = false,
    val feedErrorMessage: String? = null,
)
