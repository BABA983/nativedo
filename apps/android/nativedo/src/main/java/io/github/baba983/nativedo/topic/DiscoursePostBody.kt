package io.github.baba983.nativedo.topic

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Tag

/**
 * A cooked post body split at its polls.
 *
 * A Discourse poll is a `div.poll[data-poll-name]` inside the body, and the site's JSON carries the
 * poll's data separately (`post.polls[]`). The body is therefore cut into the runs of markup around
 * the polls; each run becomes a text bubble of its own, and each poll becomes a poll bubble. The
 * cut is what makes a poll drawable at all: upstream decides what a message is by its media, once
 * per message, so a body that both has rich text and a poll cannot be one bubble.
 */
object DiscoursePostBody {

    /** One run of a body: markup, or the place a named poll sits in. */
    class Segment internal constructor(
        @JvmField val html: String,
        @JvmField val pollName: String?,
    ) {
        val isPoll: Boolean get() = pollName != null
    }

    /**
     * The body's segments, in order. Markup around a poll is kept as it was, and a run with no
     * markup between two polls produces no text segment.
     */
    @JvmStatic
    fun segments(cookedHtml: String?): List<Segment> {
        val body = Jsoup.parseBodyFragment(cookedHtml ?: "").body()
        val segments = ArrayList<Segment>()
        val pending = Element(Tag.valueOf("div"), "")
        fun flush() {
            if (pending.children().isNotEmpty()) {
                segments += Segment(pending.html(), null)
                pending.empty()
            }
        }
        for (child in body.children()) {
            val pollName = if (child.hasClass("poll")) child.attr("data-poll-name") else ""
            if (pollName.isNotEmpty()) {
                flush()
                segments += Segment("", pollName)
            } else {
                pending.appendChild(child)
            }
        }
        flush()
        return segments
    }
}
