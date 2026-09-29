package io.github.baba983.nativedo.core.model

data class TopicListPage(
    val topics: List<TopicSummary>,
    val moreTopicsUrl: String?,
)
