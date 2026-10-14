package io.github.baba983.nativedo.core.model

/**
 * An incremental topic response: posts and, when requested with `include_suggested=true`, the
 * site's ranked suggestions. The reader requests suggestions on the final forward page.
 */
data class TopicPostsPage(
    val posts: List<Post>,
    /** The site's own ranked suggestions; empty unless this page's read asked for them. */
    val suggestedTopics: List<TopicSummary> = emptyList(),
    /**
     * Whether this page was answered from the persisted offline cache rather than the site. A live
     * answer leaves it false; the per-[Post] [Post.fromCache] flag travels with each row too.
     */
    val fromCache: Boolean = false,
)
