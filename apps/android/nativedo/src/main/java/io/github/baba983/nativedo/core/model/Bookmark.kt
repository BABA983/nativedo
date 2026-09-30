package io.github.baba983.nativedo.core.model

/**
 * One post this reader bookmarked, as the site lists it.
 *
 * <p>A bookmark is a post, not a topic: the list carries the topic the post is in and the post's own
 * floor inside it, which is where a tap on the row goes. `bookmarkableId` is the post's id (the
 * site's `bookmarkable_type` is `Post` for everything this client can bookmark).</p>
 */
data class Bookmark(
    val id: Long,
    val topicId: Long,
    val postId: Long,
    val postNumber: Int,
    val title: String,
    val excerpt: String = "",
    val createdAt: String = "",
    val authorUsername: String = "",
    val authorDisplayName: String = "",
    val authorAvatarTemplate: String = "",
)
