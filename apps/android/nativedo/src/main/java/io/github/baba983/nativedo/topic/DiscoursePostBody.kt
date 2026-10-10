package io.github.baba983.nativedo.topic

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Tag
import java.net.URI

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
        // Inline checkboxes become visible text before whitespace runs are collapsed.
        normalizeChecklistMarkers(body)
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
        // A cooked body also carries shapes upstream's importer cannot see: the site's spoiler
        // markup, a code block's language class, and the embeds it does not support. Each is
        // normalized to the shape upstream does understand, without changing the body's blocks.
        normalizeSpoilers(body)
        normalizeCodeLanguage(body)
        replaceUnsupportedEmbeds(body)
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

    // ---------------------------------------------------------------- cooked-input normalization

    private fun normalizeChecklistMarkers(body: Element) {
        for (box in body.select("span.chcklst-box")) {
            val parent = box.parent()!!
            val item = when {
                parent.normalName() == "li" -> parent
                parent.normalName() == "p" && parent.parent()?.normalName() == "li" &&
                    isFirstContentChild(parent) -> parent.parent()
                else -> null
            }
            val checked = box.hasClass("checked") || box.hasClass("permanent")
            if (item != null && isFirstContentChild(box)) {
                item.attr("data-checkbox", "1")
                if (checked) item.attr("data-checked", "1") else item.removeAttr("data-checked")
                box.remove()
            } else {
                // An inline box is content, not the list item's marker.
                box.replaceWith(TextNode(if (checked) "☑" else "☐"))
            }
        }
    }

    private fun isFirstContentChild(element: Element): Boolean {
        var previous = element.previousSibling()
        while (previous != null) {
            if (previous !is TextNode || previous.wholeText.isNotBlank()) return false
            previous = previous.previousSibling()
        }
        return true
    }

    /** The element name upstream's importer reads as inline spoiler text. */
    private const val SPOILER = "spoiler"

    /** Discourse's inline and block spoiler wrappers, which upstream has no element for. */
    private const val SPOILER_CLASS = "spoiler"

    /**
     * Block elements a spoiler may contain. The spoiler reaches their inline content and still
     * leaves the block skeleton the importer builds its own blocks from, so a block spoiler never
     * flattens the paragraph (or list, or quote) it wraps.
     */
    private val BLOCK_TAGS = setOf(
        "p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "blockquote", "pre",
        "table", "thead", "tbody", "tr", "td", "th", "div", "details", "summary", "figure",
        "figcaption", "section", "article", "aside", "header", "footer", "nav", "dl", "dt", "dd",
        "hr",
    )

    /**
     * Turns Discourse's spoiler markup into upstream's `<spoiler>` text semantics. An inline
     * `span.spoiler` becomes the `<spoiler>` element itself; a block `div.spoiler` keeps its block
     * children and wraps only their inline content, so the secret reaches the importer as spoiler
     * text without losing the paragraph it sat in. Media inside a spoiler carries `data-spoiler`,
     * which is the attribute upstream's own photo and video blocks read.
     */
    private fun normalizeSpoilers(body: Element) {
        for (span in body.select("span.$SPOILER_CLASS").toList()) {
            markSpoilerMedia(span)
            span.tagName(SPOILER)
            span.removeClass(SPOILER_CLASS)
        }
        for (div in body.select("div.$SPOILER_CLASS").toList()) {
            markSpoilerMedia(div)
            spoil(div)
            div.tagName("section")
        }
    }

    private fun markSpoilerMedia(element: Element) {
        for (media in element.select("img, video")) {
            media.attr("data-spoiler", "1")
        }
    }

    /** Spoils [element]'s inline content, keeping any media and block children where they are. */
    private fun spoil(element: Element) {
        wrapInlineRuns(element)
        for (child in element.children().filter { it.normalName() in BLOCK_TAGS || isMedia(it) }) {
            spoil(child)
        }
    }

    /**
     * Wraps each run of inline, non-media children of [element] in a `<spoiler>`. A picture or a
     * clip is left alone: for upstream it is a block of its own, and the spoiler travels on its
     * `data-spoiler` attribute rather than on an inline wrapper that would swallow the media. An
     * inline emoji is not media here - it is content of the run, and stays inside the spoiler.
     */
    private fun wrapInlineRuns(element: Element) {
        var inlineRun = ArrayList<Node>()
        fun flush() {
            if (inlineRun.isEmpty()) return
            if (inlineRun.all { it is TextNode && it.wholeText.isBlank() }) {
                inlineRun = ArrayList()
                return
            }
            if (inlineRun.size == 1 && (inlineRun[0] as? Element)?.normalName() == SPOILER) {
                inlineRun = ArrayList()
                return
            }
            val spoiler = Element(Tag.valueOf(SPOILER), "")
            inlineRun.first().before(spoiler)
            spoiler.appendChildren(inlineRun)
            inlineRun = ArrayList()
        }
        for (node in element.childNodes().toList()) {
            val child = node as? Element
            val standalone = child != null && (child.normalName() in BLOCK_TAGS
                || child.normalName() == SPOILER || isMedia(child))
            if (standalone) flush() else inlineRun.add(node)
        }
        flush()
    }

    /** A real media node to upstream; an inline `img.emoji`/`img.avatar` is content, not media. */
    private fun isMedia(element: Element): Boolean {
        if (element.normalName() == "video") return true
        if (element.normalName() == "img") return !element.hasClass("emoji") && !element.hasClass("avatar")
        return element.select("video").isNotEmpty() ||
            element.select("img").any { !it.hasClass("emoji") && !it.hasClass("avatar") }
    }

    /** Discourse writes a fenced block's language as a `lang-*` / `language-*` class. */
    private val LANGUAGE_CLASS = Regex("(?:^|\\s)(?:lang|language)-([A-Za-z0-9#+._-]+)(?=\\s|$)")

    /**
     * Moves a fenced block's language from the `lang-*` class Discourse writes onto the `language`
     * attribute upstream reads (`RichHtml` looks at `language`/`lang`/`lng` on the `<pre>`). The
     * text is untouched, so the block's whitespace reaches the importer exactly as written.
     */
    private fun normalizeCodeLanguage(body: Element) {
        for (pre in body.select("pre")) {
            // `RichHtml` reads any of these; don't override a language the cook already carries.
            if (listOf("language", "lang", "lng").any { !pre.attr(it).isBlank() }) continue
            val language = codeLanguageOf(pre.attr("class"))
                ?: codeLanguageOf(pre.selectFirst("code")?.attr("class").orEmpty())
            if (!language.isNullOrBlank()) pre.attr("language", language)
        }
    }

    private fun codeLanguageOf(classes: String): String? {
        val match = LANGUAGE_CLASS.find(classes) ?: return null
        // `lang-auto` means "guess it": upstream must keep guessing rather than be handed the
        // literal language "auto", which it would show and try to highlight.
        return match.groupValues[1].takeUnless { it.equals("auto", ignoreCase = true) }
    }

    /**
     * Replaces the embeds upstream drops (`audio`, and `iframe`/`embed`/`object`) with a readable
     * labeled link. A reader may not be able to play a clip upstream has no block for, but the
     * address it pointed at should still be visible and openable. An unopenable source - any
     * scheme other than http(s), which a relative reference has none of - stays readable text and
     * never becomes clickable, so a `javascript:` or `data:` URL cannot be followed.
     */
    private fun replaceUnsupportedEmbeds(body: Element) {
        for (element in body.select("audio, iframe, embed, object").toList()) {
            val source = embedSource(element).trim()
            if (source.isEmpty()) {
                val text = element.text().trim().ifEmpty { "嵌入内容（没有可用链接）" }
                element.replaceWith(TextNode(text))
                continue
            }
            val href = safeHref(source)
            if (href == null) {
                element.replaceWith(TextNode(source))
            } else {
                val label = element.text().trim().ifEmpty { source }
                element.replaceWith(Element(Tag.valueOf("a"), "").attr("href", href).text(label))
            }
        }
    }

    private fun embedSource(element: Element): String = when (element.normalName()) {
        "object" -> element.attr("data").ifBlank { element.attr("src") }
        "audio" -> element.attr("src").ifBlank {
            element.selectFirst("source[src]")?.attr("src").orEmpty()
        }
        else -> element.attr("src")
    }

    /**
     * The href an embed's source may become. An HTTP(S) URL or a relative reference is safe; any
     * other scheme (`javascript:`, `data:`, `file:`) is refused. A relative reference is left
     * relative here; DiscourseImageBridge resolves it against the configured site before import.
     */
    private fun safeHref(source: String): String? {
        val uri = runCatching { URI(source) }.getOrNull() ?: return null
        return when (uri.scheme?.lowercase()) {
            null, "http", "https" -> source
            else -> null
        }
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
