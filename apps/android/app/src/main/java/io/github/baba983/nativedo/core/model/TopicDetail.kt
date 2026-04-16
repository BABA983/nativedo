package io.github.baba983.nativedo.core.model

data class TopicDetail(
    val id: Long,
    val title: String,
    val posts: List<Post>,
    val createdAt: String = "",
    val lastPostedAt: String = "",
    val viewCount: Int = 0,
    val tags: List<String> = emptyList(),
    val validReactions: List<String> = emptyList(),
    val emojiCatalog: Map<String, String> = emptyMap(),
    val totalPosts: Int = posts.size,
    val streamPostIds: List<Long> = posts.map { it.id },
)
