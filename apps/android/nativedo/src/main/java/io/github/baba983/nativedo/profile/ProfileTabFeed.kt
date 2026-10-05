package io.github.baba983.nativedo.profile

import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.feature.profile.data.ProfileFeedRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * One profile tab's stream: a single `user_actions.json` filter, one page at a time.
 *
 * <p>ProfileActivityFeed merges the topics and replies of a profile into "动态"; this screen shows
 * the site's own tabs instead (话题 `filter=4`, 回复 `filter=5`, 点赞 `filter=1`), so each page owns
 * one filter and one offset. The end of the stream is a page shorter than the site's page size,
 * measured on the raw answer rather than the rows that survived mapping.</p>
 */
class ProfileTabFeed(
    private val repository: ProfileFeedRepository,
    private val username: String,
    private val filter: String,
) {

    private val items = ArrayList<TopicSummary>()
    private var offset = 0
    private var finished = false
    private var loading = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** The stream so far, in the order the site sent it (newest first). */
    fun rows(): List<TopicSummary> = items

    fun hasMore(): Boolean = !finished

    /** The next page; a call while one is in flight, or after the end, answers at once. */
    fun load(onLoaded: () -> Unit) {
        if (loading || finished) {
            onLoaded()
            return
        }
        loading = true
        scope.launch {
            repository.load(username, filter, offset).getOrNull()?.let { page ->
                items.addAll(page.items)
                offset += page.rawCount
                if (page.rawCount < ProfileFeedRepository.PAGE_SIZE) {
                    finished = true
                }
            }
            loading = false
            onLoaded()
        }
    }
}
