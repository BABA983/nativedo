package io.github.baba983.nativedo.feature.notifications.data

import io.github.baba983.nativedo.core.model.NotificationPage
import io.github.baba983.nativedo.core.network.DiscourseApiClient

/**
 * The reader's own in-app notifications, from Discourse's `notifications.json`; see
 * contracts/discourse/endpoints.md. History is offset-paged: the site answers with the page it was
 * asked for plus the total it has, so the caller's next offset is its own count of what it has read.
 */
open class NotificationRepository protected constructor() {
    private lateinit var client: DiscourseApiClient

    constructor(client: DiscourseApiClient) : this() {
        this.client = client
    }

    open suspend fun load(offset: Int): Result<NotificationPage> =
        runCatching {
            client.fetchNotifications(offset = offset, limit = PAGE_SIZE)
        }

    companion object {
        /** The page size the site is asked for; its own cap is 60. */
        const val PAGE_SIZE = 30
    }
}
