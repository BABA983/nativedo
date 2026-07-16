package io.github.baba983.nativedo.feature.profile.data

import io.github.baba983.nativedo.core.model.UserActionPage
import io.github.baba983.nativedo.core.network.DiscourseApiClient

/**
 * Loads a user's activity feed (topics created / replies) for the profile feed tabs.
 * Backed by Discourse's `user_actions.json`; see contracts/discourse/endpoints.md.
 */
open class ProfileFeedRepository protected constructor() {
    private lateinit var client: DiscourseApiClient

    constructor(client: DiscourseApiClient) : this() {
        this.client = client
    }

    /** filter: "4" = topics created, "5" = replies/posts. */
    open suspend fun load(username: String, filter: String, offset: Int): Result<UserActionPage> =
        runCatching {
            client.fetchUserActions(
                username = username,
                filter = filter,
                offset = offset,
                limit = PAGE_SIZE,
            )
        }

    companion object {
        const val PAGE_SIZE = 20
        const val FILTER_TOPICS = "4"
        const val FILTER_REPLIES = "5"
    }
}
