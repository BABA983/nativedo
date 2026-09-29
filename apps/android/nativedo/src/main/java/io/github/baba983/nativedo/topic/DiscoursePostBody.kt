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

    /** One run of a body: markup, a named poll's place, or one onebox of the site's own. */
    class Segment internal constructor(
        @JvmField val html: String,
        @JvmField val pollName: String?,
        @JvmField val onebox: Boolean = false,
    ) {
        val isPoll: Boolean get() = pollName != null
        val isOnebox: Boolean get() = onebox
    }

    /**
     * The body's segments, in order. Markup around a poll is kept as it was, and a run with no
     * markup between two polls produces no text segment.
     *
     * [pollTitles] names the polls the site gave a title to. Discourse's own cook puts that title
     * in a `div.poll-title` next to (or inside) the poll markup, and the same title is what the poll
     * bubble draws as its question - so the body's copy is taken out here rather than said twice.
     */
    @JvmStatic
    @JvmOverloads
    fun segments(cookedHtml: String?, pollTitles: Map<String, String> = emptyMap()): List<Segment> {
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
                if (!pollTitles[pollName].isNullOrBlank()) {
                    pending.select("div.poll-title").remove()
                }
                flush()
                segments += Segment("", pollName)
            } else if (DiscourseOnebox.isOnebox(child)) {
                // The card stands for the link, so the bare link line goes the way the site's own
                // renderer takes it: out of the text, not repeated next to the card.
                pending.select("a.onebox").remove()
                pending.select("p").removeIf { paragraph ->
                    paragraph.children().isEmpty() && paragraph.text().isBlank()
                }
                flush()
                segments += Segment(child.outerHtml(), null, onebox = true)
            } else {
                pending.appendChild(child)
            }
        }
        flush()
        return segments
    }
}
