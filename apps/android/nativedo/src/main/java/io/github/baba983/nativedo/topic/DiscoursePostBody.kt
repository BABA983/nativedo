package io.github.baba983.nativedo.topic

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
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
        val document = Jsoup.parseBodyFragment(cookedHtml ?: "")
        // The cell draws what upstream's importer reads, so the serializer must not touch the
        // markup: jsoup's pretty-printer injects a newline and indent after a <br>, which the
        // importer then turns into a second, blank line and a padded line start. Serialize
        // verbatim, and collapse the text the way a browser collapses ASCII whitespace.
        document.outputSettings().prettyPrint(false)
        val body = document.body()
        normalizeWhitespace(body)
        // Inline emoji stay as the site's own `img.emoji` here: DiscourseImageBridge turns each one
        // into the tag upstream's rich renderer draws (or its readable label) once it knows whether
        // that emoji's picture is on disk. A bare inline img is what upstream's importer drops, so
        // no img reaches it.
        // Discourse stores a quote's author outside its blockquote. RichHtml reads a cite inside it.
        for (aside in body.select("aside.quote")) {
            val quote = aside.children().firstOrNull { it.normalName() == "blockquote" } ?: continue
            val title = aside.children().firstOrNull { it.hasClass("title") }?.clone()
            title?.select(".quote-controls, img.avatar")?.remove()
            if (title != null && title.text().isNotBlank()) {
                val cite = Element(Tag.valueOf("cite"), "")
                cite.appendChildren(title.childNodes().toList())
                quote.appendChild(cite)
            }
            aside.replaceWith(quote)
        }
        val segments = ArrayList<Segment>()
        // The segment markup is serialized from a sink under its own non-pretty document: a
        // detached element keeps jsoup's default pretty-printer, which would put back the
        // whitespace removed above the moment the segment is written out.
        val sink = Jsoup.parseBodyFragment("")
        sink.outputSettings().prettyPrint(false)
        val pending = sink.body()
        fun flush() {
            if (pending.childNodeSize() > 0 && pending.html().isNotBlank()) {
                segments += Segment(pending.html(), null)
                pending.empty()
            }
        }
        for (child in body.childNodes().toList()) {
            val element = child as? Element
            val pollName = if (element?.hasClass("poll") == true) element.attr("data-poll-name") else ""
            if (pollName.isNotEmpty()) {
                if (!pollTitles[pollName].isNullOrBlank()) {
                    pending.select("div.poll-title").remove()
                }
                flush()
                segments += Segment("", pollName)
            } else if (element != null && DiscourseOnebox.isOnebox(element)) {
                // The card stands for the link, so the bare link line goes the way the site's own
                // renderer takes it: out of the text, not repeated next to the card.
                pending.select("a.onebox").remove()
                pending.select("p").removeIf { paragraph ->
                    paragraph.children().isEmpty() && paragraph.text().isBlank()
                }
                flush()
                segments += Segment(element.outerHtml(), null, onebox = true)
            } else {
                pending.appendChild(child)
            }
        }
        flush()
        return segments
    }

    /** ASCII whitespace only: NBSP (`\u00A0`) and the fullwidth space (`\u3000`) are content. */
    private val ASCII_WHITESPACE = Regex("[ \\t\\n\\r\\f\\u000B]+")

    /** Tags a browser renders with whitespace significant; their text is never collapsed. */
    private val VERBATIM_TAGS = setOf("pre", "code", "tt", "textarea")

    /**
     * Inline elements that stand for content of their own (a replaced box, a checkbox): a space
     * next to one is a real separator and stays. An empty span or anchor is invisible, so the
     * spaces on either side of one are the same run and collapse together.
     */
    private val OBJECT_TAGS =
        setOf("img", "input", "video", "audio", "iframe", "embed", "object", "canvas", "svg")

    /**
     * The text of [root] with a browser's ASCII whitespace rules: every run of spaces, tabs and
     * newlines becomes one space; that space is dropped at the start or end of the body and on
     * either side of a `<br>`. The rule reaches across inline elements, so the space a
     * `<b> bold </b>` or `<a> link </a>` opens or closes with is the same run as the text beside it.
     * `pre`/`code`/`tt`/`textarea` text, NBSP and fullwidth spaces are left alone, and `<br>`
     * itself stays one explicit break.
     */
    private fun normalizeWhitespace(root: Element) {
        val run = WhitespaceRun()
        run.walk(root.childNodes())
        run.finish()
    }

    /**
     * One walk over the body's inline content, carrying the state a browser would: whether the last
     * character emitted was a space (so the next run's leading space is a duplicate), a break (so
     * the next run starts a line and its indent goes), or an ordinary character. [lastText]
     * remembers the node that produced a trailing space so a following `<br>` can take it back.
     */
    private class WhitespaceRun {
        private var last = NONE
        private var lastText: TextNode? = null

        fun walk(nodes: List<Node>) {
            for (node in nodes) {
                when (node) {
                    is TextNode -> collapseText(node)
                    is Element -> collapseElement(node)
                    else -> Unit
                }
            }
        }

        /** The end of the body: a trailing space is not a separator. */
        fun finish() {
            trimTrailingSpace()
            lastText = null
        }

        private fun collapseElement(element: Element) {
            when (element.normalName()) {
                "br" -> {
                    trimTrailingSpace()
                    last = BREAK
                    lastText = null
                }
                // Authored-verbatim content is left exactly as written. It reads as content, so a
                // space beside it is a real separator and stays.
                in VERBATIM_TAGS, in OBJECT_TAGS -> {
                    last = CHAR
                    lastText = null
                }
                else -> walk(element.childNodes())
            }
        }

        private fun collapseText(node: TextNode) {
            var text = ASCII_WHITESPACE.replace(node.wholeText, " ")
            if (text.startsWith(" ") && last != CHAR) text = text.substring(1)
            node.text(text)
            if (text.isEmpty()) return
            last = if (text.endsWith(" ")) SPACE else CHAR
            lastText = node
        }

        private fun trimTrailingSpace() {
            if (last != SPACE) return
            val node = lastText ?: return
            node.text(node.wholeText.removeSuffix(" "))
        }

        private companion object {
            const val NONE = 0
            const val CHAR = 1
            const val SPACE = 2
            const val BREAK = 3
        }
    }
}
