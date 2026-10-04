package io.github.baba983.nativedo.core.model

data class TopicDetail(
    val id: Long,
    val title: String,
    /** The topic's title slug, as the site's own URLs carry it (`/t/<slug>/<id>`); blank is legal. */
    val slug: String = "",

    val posts: List<Post>,
    val participants: List<MentionCandidate> = emptyList(),
    /** The topic's category (`category_id`); the name comes from the category store. */
    val categoryId: Int = 0,
    /** The topic's original poster (`details.created_by`), when the site names one. */
    val createdBy: MentionCandidate? = null,
    /** The reader's own topic-level bookmark state (`bookmarked`), like a post's own flag. */
    val bookmarked: Boolean = false,
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
    /** The user's Discourse read position (`last_read_post_number`); 0 also means no recorded reads. */
    val lastReadPostNumber: Int = 0,
    /** The post id at the user's Discourse read position, when the server provides it. */
    val lastReadPostId: Long? = null,
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
