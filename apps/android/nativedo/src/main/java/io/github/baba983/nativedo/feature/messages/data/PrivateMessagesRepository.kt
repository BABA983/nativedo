package io.github.baba983.nativedo.feature.messages.data

import io.github.baba983.nativedo.core.model.TopicListPage
import io.github.baba983.nativedo.core.network.DiscourseApiClient

/**
 * The reader's private messages, one box at a time, from the three `topics/private-messages*`
 * routes; see contracts/discourse/endpoints.md. Each box is a standard topic-list envelope, so a
 * page is a [TopicListPage] and paging follows the same `more_topics_url` rule a feed does.
 *
 * The list is the reader's own by the site's rule - the site answers it only for the reader
 * themselves - so the username is the session's and never a lookup.
 */
class PrivateMessagesRepository(
    private val client: DiscourseApiClient,
) {
    suspend fun load(username: String, box: PrivateMessageBox, page: Int): Result<TopicListPage> =
        runCatching {
            when (box) {
                PrivateMessageBox.Inbox -> client.fetchPrivateMessages(username = username, page = page)
                PrivateMessageBox.Sent -> client.fetchPrivateMessagesSent(username = username, page = page)
                PrivateMessageBox.Unread -> client.fetchPrivateMessagesUnread(username = username, page = page)
            }
        }
}
