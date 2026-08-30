package io.github.baba983.nativedo.core.model

data class TopicDetail(
    val id: Long,
    val title: String,
    val posts: List<Post>,
    val participants: List<MentionCandidate> = emptyList(),
    val createdAt: String = "",
    val lastPostedAt: String = "",
    val viewCount: Int = 0,
    val tags: List<String> = emptyList(),
    val validReactions: List<String> = emptyList(),
    val emojiCatalog: Map<String, String> = emptyMap(),
    val emojiCategories: List<DiscourseEmojiCategory> = emptyList(),
    val totalPosts: Int = posts.size,
    val streamPostIds: List<Long> = posts.map { it.id },
    /** Server-truth newest post number (`highest_post_number`). */
    val highestPostNumber: Int = 0,
    /** The user's Discourse read position (`last_read_post_number`); 0 when anonymous. */
    val lastReadPostNumber: Int = 0,
)

data class DiscourseEmojiCategory(
    val id: String,
    val title: String,
    val emojis: List<DiscourseEmoji>,
)

data class DiscourseEmoji(
    val name: String,
    val url: String,
)
