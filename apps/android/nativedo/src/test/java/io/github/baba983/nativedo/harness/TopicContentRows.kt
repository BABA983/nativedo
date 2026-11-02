package io.github.baba983.nativedo.harness

import io.github.baba983.nativedo.topic.TopicFragment
import org.telegram.messenger.MessageObject

/**
 * The presented screen's list rows that draw a post.
 *
 * A topic window also draws a date row above each day's first floor and, when the reader has an
 * unread start, the unread divider: rows of the list, but not posts the reader reads. Tests that
 * wait for "the topic loaded" or count what a page added mean posts, so they count these rows and
 * not the adapter's own length. Any other screen draws no such rows, and its count is the adapter's.
 */
internal fun contentRowCount(harness: UpstreamUiHarness): Int {
    val adapter = contentListOf(harness.activity)?.adapter ?: return 0
    val fragment = harness.visibleFragment() as? TopicFragment ?: return adapter.itemCount
    val bubblesField = TopicFragment::class.java.getDeclaredField("bubbles").apply { isAccessible = true }
    val dividerField = TopicFragment::class.java.getDeclaredField("unreadDivider").apply { isAccessible = true }

    @Suppress("UNCHECKED_CAST")
    val bubbles = bubblesField.get(fragment) as List<MessageObject>
    val divider = dividerField.get(fragment)
    return bubbles.count { !it.isDateObject && it !== divider }
}
