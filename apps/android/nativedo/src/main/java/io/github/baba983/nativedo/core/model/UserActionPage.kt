package io.github.baba983.nativedo.core.model

data class UserActionPage(
    val items: List<TopicSummary>,
    val rawCount: Int,
)
