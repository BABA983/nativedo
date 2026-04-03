package io.github.baba983.nativedo.core.model

data class TopicDetail(
    val id: Long,
    val title: String,
    val posts: List<Post>,
    val totalPosts: Int = posts.size,
    val streamPostIds: List<Long> = posts.map { it.id },
)
