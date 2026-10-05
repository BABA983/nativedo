package io.github.baba983.nativedo.feature.profile.data

import io.github.baba983.nativedo.core.model.UserBadge
import io.github.baba983.nativedo.core.model.UserSummary
import io.github.baba983.nativedo.core.network.DiscourseApiClient

/**
 * The profile's extras: the stats block of `/u/{username}/summary.json`, the badge list of
 * `/user-badges/{username}.json`, and the two write actions the profile's action row offers -
 * `follow` (`PUT|DELETE /follow/{username}`, the discourse-follow plugin's own routes) and the
 * site's notification level (`PUT /u/{username}/notification_level.json`).
 */
class ProfileExtrasRepository(
    private val client: DiscourseApiClient,
) {

    /** The reader's stats. A profile that hides them (403) reports the failure; the screen then
     * draws only what the profile endpoint carried. */
    suspend fun loadSummary(username: String): Result<UserSummary> = runCatching {
        client.fetchUserSummary(username)
    }

    suspend fun loadBadges(username: String): Result<List<UserBadge>> = runCatching {
        client.fetchUserBadges(username)
    }

    /** Follows or unfollows; the caller re-reads the profile afterwards for the site's own state. */
    suspend fun setFollow(username: String, follow: Boolean): Result<Unit> = runCatching {
        client.setUserFollow(username = username, follow = follow)
    }

    /**
     * Mutes or unmutes: Discourse's `notification_level` is `mute` while the reader muted the
     * user and `normal` to hand them back to the ordinary rules.
     */
    suspend fun setMuted(username: String, muted: Boolean): Result<Unit> = runCatching {
        client.setUserNotificationLevel(username = username, level = if (muted) "mute" else "normal")
    }

    companion object {
        /** `user_actions.json` filter 1 is "likes given", the posts the reader liked. */
        const val FILTER_LIKES = "1"
        const val FILTER_TOPICS_CREATED = "4"
        const val FILTER_REPLIES = "5"
    }
}
