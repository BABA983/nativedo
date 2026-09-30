package io.github.baba983.nativedo.feature.topic.data

import java.util.concurrent.ConcurrentHashMap

/**
 * The topics this reader has read in full on this device.
 *
 * <p>The site's own answer is the truth, but it is the truth on the site's clock: a timing report is
 * a request, and the feed read right after a topic is closed may be answered before the site has
 * taken it in - which shows the reader a badge for a topic they have just finished. A topic read to
 * its end is remembered here until the site's own answer agrees, and the row draws no badge for it
 * in the meantime.</p>
 */
object TopicReadMarks {

    private val readInFull = ConcurrentHashMap.newKeySet<Long>()

    fun markReadInFull(topicId: Long) {
        readInFull.add(topicId)
    }

    fun isReadInFull(topicId: Long): Boolean = readInFull.contains(topicId)

    /** Drops every mark: the render harness starts each screen test from a device that read nothing. */
    fun clear() {
        readInFull.clear()
    }

    /**
     * The feed's own answer for the topic: once it agrees (nothing is unread any more), the mark has
     * done its job and goes - from then on the site is the only thing the row is drawn from.
     */
    fun siteAgrees(topicId: Long, unreadCount: Int) {
        if (unreadCount <= 0) {
            readInFull.remove(topicId)
        }
    }
}
