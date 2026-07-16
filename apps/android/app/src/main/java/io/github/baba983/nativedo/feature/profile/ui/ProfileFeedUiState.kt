package io.github.baba983.nativedo.feature.profile.ui

import androidx.annotation.StringRes
import io.github.baba983.nativedo.R
import io.github.baba983.nativedo.core.model.CategorySummary
import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.feature.profile.data.ProfileFeedRepository

enum class ProfileFeedType(
    val filter: String,
    @StringRes val screenTitleRes: Int,
) {
    Topics(
        filter = ProfileFeedRepository.FILTER_TOPICS,
        screenTitleRes = R.string.profile_action_topics,
    ),
    Replies(
        filter = ProfileFeedRepository.FILTER_REPLIES,
        screenTitleRes = R.string.profile_action_replies,
    ),
}

data class ProfileFeedUiState(
    val items: List<TopicSummary> = emptyList(),
    val categories: Map<Int, CategorySummary> = emptyMap(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val nextOffset: Int = 0,
    val errorMessage: String? = null,
    val loadMoreErrorMessage: String? = null,
) {
    val isEmpty: Boolean
        get() = !isLoading && items.isEmpty() && errorMessage == null
}
