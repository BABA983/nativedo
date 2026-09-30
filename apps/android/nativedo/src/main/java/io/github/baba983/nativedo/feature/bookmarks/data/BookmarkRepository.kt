package io.github.baba983.nativedo.feature.bookmarks.data

import io.github.baba983.nativedo.core.model.Bookmark
import io.github.baba983.nativedo.core.network.DiscourseApiClient

/**
 * The reader's own bookmarks: `GET /u/{username}/bookmarks.json`, which the site answers for the
 * reader themselves and a page at a time.
 */
class BookmarkRepository(
    private val client: DiscourseApiClient,
) {
    suspend fun load(username: String, page: Int): Result<List<Bookmark>> = runCatching {
        client.fetchBookmarks(username = username, page = page)
    }
}
