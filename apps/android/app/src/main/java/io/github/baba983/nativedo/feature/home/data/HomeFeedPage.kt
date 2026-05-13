package io.github.baba983.nativedo.feature.home.data

import io.github.baba983.nativedo.core.model.CategorySummary
import io.github.baba983.nativedo.core.model.TopicSummary

data class HomeFeedPage(
    val items: List<TopicSummary>,
    val nextPage: Int?,
    val hasMore: Boolean,
    val categories: Map<Int, CategorySummary> = emptyMap(),
)
