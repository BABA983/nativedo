package io.github.baba983.nativedo.profile

import io.github.baba983.nativedo.core.model.TopicSummary
import io.github.baba983.nativedo.feature.profile.data.ProfileFeedRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * A profile's activity stream: the topics the reader created (`/user_actions.json`, filter 4) and
 * their replies (filter 5) as one list, newest first, which is what the site's own profile shows
 * where a reader looks for what someone has been doing.
 *
 * <p>A page of this stream is one page from each filter that still has one. The site pages each
 * filter on its own, so one filter's second page says nothing about where the other one stands; the
 * two are merged by when the action happened, and a row that arrives twice is kept once.</p>
 */
class ProfileActivityFeed(
    private val repository: ProfileFeedRepository,
    private val username: String,
) {

    /** Actions by the identity the site gave them, so no action is a row twice. */
    private val actions = LinkedHashMap<String, TopicSummary>()
    private val offsets = HashMap<String, Int>()

    /** The filters whose stream has ended, which is what "no more" is counted from. */
    private val finished = HashSet<String>()
    private var loading = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** The stream so far, newest first. */
    fun actions(): List<TopicSummary> =
        actions.values.sortedWith(compareByDescending { epochSeconds(it.lastPostedAt) })

    fun hasMore(): Boolean = finished.size < FILTERS.size

    /**
     * The next page of both streams, and what the screen shows afterwards. The first call is the
     * screen opening; every later one is the reader reaching the end of what is there.
     */
    fun loadMore(onLoaded: () -> Unit) {
        if (loading || !hasMore()) {
            onLoaded()
            return
        }
        loading = true
        scope.launch {
            coroutineScope {
                FILTERS.filter { it !in finished }
                    .map { filter -> async { loadOnePage(filter) } }
                    .awaitAll()
            }
            loading = false
            onLoaded()
        }
    }

    /**
     * One page of one filter. What the cursor advances by, and what says the stream ended, is the
     * page's own count and never the rows that survived mapping: a page whose actions are all
     * dropped still consumed its offset, and still counts as the end only when the site sent less
     * than a whole page of actions.
     */
    private suspend fun loadOnePage(filter: String) {
        val page = repository.load(username, filter, offsets[filter] ?: 0).getOrNull() ?: return
        for (action in page.items) {
            actions[rowKey(action)] = action
        }
        offsets[filter] = (offsets[filter] ?: 0) + page.rawCount
        if (page.rawCount < ProfileFeedRepository.PAGE_SIZE) {
            finished += filter
        }
    }

    /**
     * An action's own row identity: the site's post id or action id where it sent one, and the
     * post id is what makes two replies in one topic two rows rather than one.
     */
    private fun rowKey(action: TopicSummary): String = action.rowIdentity
        ?: "${action.id}:${action.targetPostNumber ?: 0}:${action.lastPostedAt}:${action.excerpt}"

    private companion object {

        /** Filter 4 is a topic the reader started, filter 5 a reply they wrote. */
        val FILTERS = listOf(
            ProfileFeedRepository.FILTER_TOPICS,
            ProfileFeedRepository.FILTER_REPLIES,
        )

        /** Discourse dates an action with an ISO-8601 instant; an unreadable one sorts last. */
        fun epochSeconds(timestamp: String): Long =
            runCatching { Instant.parse(timestamp).epochSecond }.getOrDefault(0L)
    }
}
