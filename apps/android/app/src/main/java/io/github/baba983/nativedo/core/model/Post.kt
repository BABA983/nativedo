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
            "confetti_ball", "confetti ball" -> "🎉"
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
