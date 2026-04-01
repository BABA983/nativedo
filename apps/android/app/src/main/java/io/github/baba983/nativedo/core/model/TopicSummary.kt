package io.github.baba983.nativedo.core.model

data class TopicSummary(
    val id: Long,
    val title: String,
    val authorUsername: String,
    val slug: String = "",
    val replyCount: Int = 0,
    val viewCount: Int = 0,
    val lastPostedAt: String = "",
    val categoryId: Int? = null,
)
