package io.github.baba983.nativedo.core.model

data class Post(
    val id: Long,
    val postNumber: Int? = null,
    val replyToPostNumber: Int? = null,
    val authorUsername: String,
    val authorDisplayName: String = "",
    val authorAvatarTemplate: String = "",
    val authorAnimatedAvatarUrl: String = "",
    val authorTitle: String = "",
    val authorFlairName: String = "",
    val authorFlairUrl: String = "",
    val authorFlairBgColor: String = "",
    val authorFlairColor: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val editCount: Int = 0,
    val replyCount: Int = 0,
    val currentUserReaction: String? = null,
    val reactionUsersCount: Int = 0,
    val reactions: List<PostReaction> = emptyList(),
    val isBookmarked: Boolean = false,
    val cookedHtml: String,
    val imageUrls: List<String> = emptyList(),
    /** The post's polls, in the order the site sent them; the vote state travels in [PostPoll.votes]. */
    val polls: List<PostPoll> = emptyList(),
) {
    /**
     * The same post with the reader's bookmark on it (or off it). What the site answered is what the
     * screen keeps, so the menu that offered to add a bookmark offers to take it back next time.
     */
    fun withBookmark(bookmarked: Boolean): Post = copy(isBookmarked = bookmarked)

    /**
     * The same post with the poll that shares [updated]'s name replaced. A vote response carries the
     * poll's new state and nothing else of the post, so the screen keeps the post it already drew.
     */
    fun withUpdatedPoll(updated: PostPoll): Post {
        if (polls.none { poll -> poll.name == updated.name }) {
            return this
        }
        return copy(polls = polls.map { poll -> if (poll.name == updated.name) updated else poll })
    }
}

/**
 * One poll on a post, from the post's own `polls[]` payload.
 *
 * The site is the only source of options and counts: the cooked HTML names where a poll sits, but
 * which options it has, what they are called and how many votes each carries come from here.
 */
data class PostPoll(
    val id: Long = 0,
    val name: String,
    val type: String = "regular",
    val status: String = "open",
    val results: String = "always",
    val title: String = "",
    val options: List<PostPollOption> = emptyList(),
    val voters: Int = 0,
    val chartType: String = "",
    val isPublic: Boolean = false,
    val min: Int? = null,
    val max: Int? = null,
    val step: Int? = null,
    val closedAt: String = "",
    val closedBy: String = "",
    /** What the reader chose; empty when they have not voted. */
    val votes: List<PostPollVote> = emptyList(),
) {
    val isClosed: Boolean get() = status == "closed"

    /** More than one option may be chosen, so the whole selection travels in one request. */
    val isMultiple: Boolean get() = type == "multiple"

    /** The reader's own selection, as the digests a vote request carries. */
    val chosenDigests: Set<String> get() = votes.map { vote -> vote.digest }.toSet()

    /** The reader may change a vote while the poll is open. */
    val canRevote: Boolean get() = !isClosed
}

data class PostPollOption(
    val id: String,
    val label: String,
    /** The site's count, or `null` when the reader may not see results yet. */
    val votes: Int? = null,
    val chosen: Boolean = false,
    val rank: Int? = null,
)

/** One option the reader chose, with the rank a `ranked_choice` poll assigned it. */
data class PostPollVote(
    val digest: String,
    val rank: Int? = null,
)

data class PostReaction(
    val id: String,
    val count: Int,
    val iconUrl: String? = null,
    val reacted: Boolean = false,
)

object PostReactionRendering {
    @JvmStatic
    fun iconUrl(
        id: String,
        explicitIconUrl: String?,
        emojiCatalog: Map<String, String>?,
    ): String? {
        return explicitIconUrl
            ?.takeUnless { it.isBlank() }
            ?: emojiCatalog?.get(id)?.takeUnless { it.isBlank() }
    }

    @JvmStatic
    fun fallbackLabel(id: String): String {
        return when (id) {
            "+1" -> "👍"
            "heart" -> "♡"
            "laughing" -> "😄"
            "open_mouth" -> "😮"
            "clap" -> "👏"
            "confetti_ball", "confetti ball" -> "🎊"
            "tada" -> "🎉"
            "hugs" -> "🤗"
            "distorted_face" -> "🥴"
            "tieba_087" -> "87"
            "bili_057" -> "57"
            else -> id.replace('_', ' ')
        }
    }

    @JvmStatic
    fun countText(
        id: String,
        count: Int,
        explicitIconUrl: String?,
        emojiCatalog: Map<String, String>?,
    ): String {
        return if (iconUrl(id, explicitIconUrl, emojiCatalog) != null) {
            count.toString()
        } else {
            "${fallbackLabel(id)} $count"
        }
    }
}

data class PostReactionUser(
    val id: Long = 0L,
    val username: String,
    val displayName: String = "",
    val avatarTemplate: String = "",
    val animatedAvatarUrl: String = "",
    val canUndo: Boolean = false,
    val createdAt: String = "",
)

data class PostReactionUsersGroup(
    val id: String,
    val count: Int = 0,
    val users: List<PostReactionUser> = emptyList(),
)

internal fun Post.defaultReactionFallback(
    emojiCatalog: Map<String, String>,
): List<PostReaction> {
    if (reactions.isNotEmpty()) {
        return reactions
    }
    if (reactionUsersCount <= 0) {
        return emptyList()
    }
    return listOf(
        PostReaction(
            id = currentUserReaction ?: "heart",
            count = reactionUsersCount,
            iconUrl = PostReactionRendering.iconUrl(currentUserReaction ?: "heart", null, emojiCatalog),
            reacted = currentUserReaction != null,
        ),
    )
}

fun Post.toggledReaction(
    reactionId: String,
    emojiCatalog: Map<String, String>,
): Post {
    val currentReactionId = currentUserReaction
    val nextReactions = reactions.toMutableList()
    fun updateReactionCount(targetReactionId: String, delta: Int, reacted: Boolean) {
        val index = nextReactions.indexOfFirst { it.id == targetReactionId }
        if (index == -1) {
            if (delta > 0) {
                nextReactions += PostReaction(
                    id = targetReactionId,
                    count = delta,
                    iconUrl = PostReactionRendering.iconUrl(targetReactionId, null, emojiCatalog),
                    reacted = reacted,
                )
            }
            return
        }
        val current = nextReactions[index]
        val nextCount = (current.count + delta).coerceAtLeast(0)
        if (nextCount == 0) {
            nextReactions.removeAt(index)
        } else {
            nextReactions[index] = current.copy(
                count = nextCount,
                iconUrl = PostReactionRendering.iconUrl(targetReactionId, current.iconUrl, emojiCatalog),
                reacted = reacted,
            )
        }
    }

    if (currentReactionId == reactionId) {
        updateReactionCount(reactionId, delta = -1, reacted = false)
        return copy(
            currentUserReaction = null,
            reactionUsersCount = (reactionUsersCount - 1).coerceAtLeast(0),
            reactions = nextReactions.map { reaction -> reaction.copy(reacted = false) },
        )
    }

    if (currentReactionId != null) {
        updateReactionCount(currentReactionId, delta = -1, reacted = false)
    }
    updateReactionCount(reactionId, delta = 1, reacted = true)

    val totalUsers = if (currentReactionId == null) {
        reactionUsersCount + 1
    } else {
        reactionUsersCount
    }
    return copy(
        currentUserReaction = reactionId,
        reactionUsersCount = totalUsers,
        reactions = nextReactions.map { reaction -> reaction.copy(reacted = reaction.id == reactionId) },
    )
}
