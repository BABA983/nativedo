package io.github.baba983.nativedo.core.model

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val username: String,
    val displayName: String,
    val bio: String,
    val avatarTemplate: String = "",
    val animatedAvatarUrl: String = "",
    val flairName: String = "",
    val flairUrl: String = "",
    val flairBgColor: String = "",
    val flairColor: String = "",
    val trustLevel: Int = 0,
    val title: String = "",
    val badgeCount: Int = 0,
    val timeRead: Int = 0,
    val recentTimeRead: Int = 0,
    val createdAt: String = "",
    val lastSeenAt: String = "",
    val lastPostedAt: String = "",
    val profileViewCount: Int = 0,
    val admin: Boolean = false,
    val moderator: Boolean = false,
    // linux.do / Discourse plugin fields surfaced on the profile endpoint.
    val gamificationScore: Int = 0,
    val cakeDate: String = "",
    val totalFollowers: Int = 0,
    val totalFollowing: Int = 0,
    val acceptedAnswers: Int = 0,
    val primaryGroupName: String = "",
    /** Optional fields the site only sends when the reader may see them. */
    val location: String = "",
    val website: String = "",
    val websiteName: String = "",
    /**
     * What the viewed user's own page says the reader may do to them. A profile without any of
     * these (an anonymous read, or a field the site no longer sends) leaves the action row empty
     * rather than offering an action that would fail.
     */
    val canSendPrivateMessage: Boolean = false,
    val canFollow: Boolean = false,
    val isFollowed: Boolean = false,
    val canMuteUser: Boolean = false,
    val canIgnoreUser: Boolean = false,
    val muted: Boolean = false,
    val ignored: Boolean = false,
)
