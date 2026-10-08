package io.github.baba983.nativedo.topic

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.telegram.tgnet.tl.TL_iv
import org.telegram.ui.Components.MarkdownParser
import org.telegram.ui.iv.BlockRow
import org.telegram.ui.iv.RichHtml
import org.telegram.ui.iv.RichTextStyle

/**
 * The bridge between upstream's rich editor and the one text format linux.do stores: Discourse
 * markdown.
 *
 * <p>Import (site -> editor) uses upstream's own CommonMark parser, [MarkdownParser], for the
 * [TL_iv.RichMessage] the editor's constructor takes ([rawToRichMessage]) and, for the editor's
 * [BlockRow] model ([rawToBlocks] / [rawToHtml]), a thin block pass that keeps nested lists and
 * quotes and delegates every inline run back to the same upstream parser. Export (editor -> site)
 * renders the editor's blocks through upstream's own [RichHtml#serialize] and then writes the
 * markdown linux.do accepts ([blocksToRaw]).</p>
 *
 * <p>Only the constructs the site cannot carry at all are refused, through
 * [UnsupportedContentException]: a Telegram media id, an inline button row, a custom emoji. Tables,
 * images, links and code are stored, not refused.</p>
 */
object DiscourseRichText {

    /** A construct whose meaning has no Discourse markdown form. */
    class UnsupportedContentException(val construct: String) :
        IllegalArgumentException("linux.do cannot store $construct")

    // ------------------------------------------------------------------ import

    /**
     * Discourse markdown as the [TL_iv.RichMessage] upstream's editor constructor takes, parsed by
     * upstream's own CommonMark parser. Uploads (which the parser renders as their label) are kept
     * as the literal `!label` markdown so a reopen does not lose their `upload://` handle.
     *
     * <p>Upstream's parser emits its block model, whose lists and quotes are a single level; nested
     * list/quote depth is rebuilt on the [rawToBlocks] path (through [rawToHtml]) instead.</p>
     */
    fun rawToRichMessage(raw: String?): TL_iv.RichMessage {
        val blocks = ArrayList<TL_iv.PageBlock>()
        if (!raw.isNullOrBlank()) {
            MarkdownParser.parse(preserveImageMarkdown(raw), blocks)
        }
        if (blocks.isEmpty()) blocks.add(emptyParagraph())
        val message = TL_iv.RichMessage()
        message.blocks = blocks
        return message
    }

    /** Discourse markdown as the editor's own [BlockRow] list. */
    fun rawToBlocks(raw: String?): List<BlockRow> = RichHtml.parse(rawToHtml(raw))

    /** The editor HTML upstream's [RichHtml#parse] reads back (for `RichEditor(html, true)`). */
    fun rawToHtml(raw: String?): String = markdownToHtml(raw.orEmpty())

    // ------------------------------------------------------------------ export

    /** The editor's rows, as the markdown linux.do stores. */
    @Throws(UnsupportedContentException::class)
    fun blocksToRaw(rows: List<BlockRow>?): String {
        if (rows.isNullOrEmpty()) return ""
        return htmlToRaw(RichHtml.serialize(rows, 0, rows.size - 1, 0, Int.MAX_VALUE))
    }

    // ================================================================ markdown -> editor HTML

    // The block pass only decides *where* blocks start and how deep they nest; the inline text of
    // every block is handed to upstream's parser, so escapes, code spans and links are its job.
    private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
    private val HR = Regex("^(-{3,}|\\*{3,}|_{3,})$")
    private val LIST_ITEM = Regex("^([-*+]|\\d+[.)])\\s+(.*)$")
    private val DELIMITER_ROW = Regex("^\\|?\\s*:?-{1,}:?\\s*(\\|\\s*:?-{1,}:?\\s*)*\\|?$")
    private val DETAILS_OPEN = Regex("^\\[details(?:=([^\\]]*))?]$", RegexOption.IGNORE_CASE)
    private val SPOILER = Regex("\\[spoiler](.*?)\\[/spoiler]", RegexOption.DOT_MATCHES_ALL)
    private val IMAGE_MARKDOWN = Regex("(?<!\\\\)!\\[")

    private fun markdownToHtml(raw: String): String {
        if (raw.isBlank()) return ""
        val lines = raw.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val out = StringBuilder()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) {
                i++
                continue
            }
            val body = line.trimStart()

            val fence = fenceOf(body)
            if (fence != null) {
                val language = body.removePrefix(fence).trim().substringBefore(' ').trim()
                val code = StringBuilder()
                var j = i + 1
                while (j < lines.size && !lines[j].trimStart().startsWith(fence)) {
                    code.append(lines[j]).append('\n')
                    j++
                }
                out.append("<pre")
                if (language.isNotEmpty()) out.append(" language=\"").append(escapeAttribute(language)).append('"')
                out.append('>').append(escapeHtml(code.toString())).append("</pre>")
                i = if (j < lines.size) j + 1 else j
                continue
            }

            val details = DETAILS_OPEN.find(body)
            if (details != null) {
                val title = details.groupValues[1]
                val inner = ArrayList<String>()
                var j = i + 1
                while (j < lines.size && lines[j].trim() != "[/details]") {
                    inner.add(lines[j])
                    j++
                }
                out.append("<details><summary>").append(inlineToHtml(title)).append("</summary>")
                out.append(markdownToHtml(inner.joinToString("\n")))
                out.append("</details>")
                i = if (j < lines.size) j + 1 else j
                continue
            }

            val heading = HEADING.find(body)
            if (heading != null) {
                val level = heading.groupValues[1].length
                out.append("<h").append(level).append('>')
                    .append(inlineToHtml(heading.groupValues[2].trim()))
                    .append("</h").append(level).append('>')
                i++
                continue
            }

            if (HR.matches(body)) {
                out.append("<hr>")
                i++
                continue
            }

            val item = listItem(line)
            if (item != null) {
                val (html, next) = listToHtml(lines, i, item.indent)
                out.append(html)
                i = next
                continue
            }

            if (isTableStart(lines, i)) {
                val (html, next) = tableToHtml(lines, i)
                out.append(html)
                i = next
                continue
            }

            if (body.startsWith(">")) {
                val block = ArrayList<String>()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    block.add(lines[i])
                    i++
                }
                out.append(quoteToHtml(block))
                continue
            }

            val paragraph = ArrayList<String>()
            while (i < lines.size && lines[i].isNotBlank() && !isBlockStart(lines, i)) {
                paragraph.add(lines[i].trim())
                i++
            }
            if (paragraph.isEmpty()) {
                i++
            } else {
                // A soft line break inside a paragraph is a space, the way CommonMark reads it.
                out.append("<p>").append(inlineToHtml(paragraph.joinToString(" "))).append("</p>")
            }
        }
        return out.toString()
    }

    /** Whether the line at [index] opens a block, which ends the paragraph before it. */
    private fun isBlockStart(lines: List<String>, index: Int): Boolean {
        val line = lines[index]
        val body = line.trimStart()
        return fenceOf(body) != null || HEADING.containsMatchIn(body) || HR.matches(body) ||
            DETAILS_OPEN.containsMatchIn(body) || body.startsWith(">") ||
            listItem(line) != null || isTableStart(lines, index)
    }

    private fun fenceOf(body: String): String? {
        if (body.startsWith("```") || body.startsWith("~~~")) {
            val marker = body[0]
            var n = 0
            while (n < body.length && body[n] == marker) n++
            if (n >= 3) return body.substring(0, n)
        }
        return null
    }

    private class ListItem(val indent: Int, val ordered: Boolean, val text: String)

    private fun listItem(line: String): ListItem? {
        val indent = line.length - line.trimStart(' ', '\t').length
        val match = LIST_ITEM.find(line.trimStart(' ', '\t')) ?: return null
        return ListItem(indent, match.groupValues[1].first().isDigit(), match.groupValues[2])
    }

    private fun listToHtml(lines: List<String>, start: Int, indent: Int): Pair<String, Int> {
        val first = listItem(lines[start]) ?: return "" to start
        val ordered = first.ordered
        val out = StringBuilder(if (ordered) "<ol>" else "<ul>")
        var i = start
        while (i < lines.size) {
            val item = listItem(lines[i]) ?: break
            if (item.indent != indent || item.ordered != ordered) break
            val li = StringBuilder("<li>").append(inlineToHtml(item.text))
            i++
            while (i < lines.size) {
                val nested = listItem(lines[i]) ?: break
                if (nested.indent <= indent) break
                val (html, next) = listToHtml(lines, i, nested.indent)
                li.append(html)
                i = next
            }
            out.append(li).append("</li>")
        }
        out.append(if (ordered) "</ol>" else "</ul>")
        return out.toString() to i
    }

    private fun quoteToHtml(block: List<String>): String {
        val quoted = Regex("^\\s*(?:>\\s*)+")
        val parsed = block.map { line ->
            val prefix = quoted.find(line)?.value ?: ""
            prefix.count { it == '>' } to line.substring(prefix.length)
        }
        val out = StringBuilder()
        var depth = 0
        var i = 0
        while (i < parsed.size) {
            val (lineDepth, text) = parsed[i]
            if (text.isBlank()) {
                i++
                continue
            }
            while (depth < lineDepth) {
                out.append("<blockquote>")
                depth++
            }
            while (depth > lineDepth) {
                out.append("</blockquote>")
                depth--
            }
            val paragraph = ArrayList<String>()
            while (i < parsed.size && parsed[i].first == lineDepth && parsed[i].second.isNotBlank()) {
                paragraph.add(parsed[i].second.trim())
                i++
            }
            out.append("<p>").append(inlineToHtml(paragraph.joinToString(" "))).append("</p>")
        }
        while (depth > 0) {
            out.append("</blockquote>")
            depth--
        }
        return out.toString()
    }

    private fun isTableStart(lines: List<String>, index: Int): Boolean =
        index + 1 < lines.size && lines[index].contains('|') && DELIMITER_ROW.matches(lines[index + 1].trim())

    private fun tableToHtml(lines: List<String>, start: Int): Pair<String, Int> {
        val out = StringBuilder("<table border=\"1\"><tr>")
        for (cell in splitCells(lines[start])) out.append("<th>").append(inlineToHtml(cell)).append("</th>")
        out.append("</tr>")
        var i = start + 2
        while (i < lines.size && lines[i].isNotBlank() && lines[i].contains('|')) {
            out.append("<tr>")
            for (cell in splitCells(lines[i])) out.append("<td>").append(inlineToHtml(cell)).append("</td>")
            out.append("</tr>")
            i++
        }
        out.append("</table>")
        return out.toString() to i
    }

    private fun splitCells(line: String): List<String> {
        var body = line.trim()
        if (body.startsWith("|")) body = body.substring(1)
        if (body.endsWith("|")) body = body.dropLast(1)
        return body.split("|").map { it.trim() }
    }

    /**
     * One inline run, as editor HTML. The run is parsed by upstream's CommonMark parser; the only
     * shapes it does not carry are spoilers (a telegram marker the editor stores as a tag) and
     * images (which upstream resolves to their label, losing the site's `upload://` handle), so
     * both are handled around it.
     */
    private fun inlineToHtml(markdown: String): String {
        if (markdown.isEmpty()) return ""
        val spoilers = ArrayList<String>()
        val tokenized = SPOILER.replace(markdown) { match ->
            spoilers.add(inlineToHtml(match.groupValues[1]))
            "\uE000" + (spoilers.size - 1) + "\uE001"
        }
        val blocks = ArrayList<TL_iv.PageBlock>()
        MarkdownParser.parse(preserveImageMarkdown(tokenized), blocks)
        val rich = (blocks.firstOrNull() as? TL_iv.pageBlockParagraph)?.text
        var html = if (rich == null) escapeHtml(tokenized) else RichHtml.inlineToHtml(RichTextStyle.toSpannable(rich))
        for ((index, body) in spoilers.withIndex()) {
            html = html.replace("\uE000$index\uE001", "<spoiler>$body</spoiler>")
        }
        return html
    }

    /** Keeps `![label](upload://x)` readable instead of letting upstream drop its destination. */
    private fun preserveImageMarkdown(markdown: String): String =
        IMAGE_MARKDOWN.replace(markdown) { "\\![" }

    private fun emptyParagraph(): TL_iv.pageBlockParagraph {
        val paragraph = TL_iv.pageBlockParagraph()
        paragraph.text = TL_iv.textEmpty()
        return paragraph
    }

    // ================================================================ editor HTML -> markdown

    @Throws(UnsupportedContentException::class)
    private fun htmlToRaw(html: String): String {
        if (html.isBlank()) return ""
        val body = Jsoup.parseBodyFragment(html).body()
        val out = StringBuilder()
        writeBlocks(body.childNodes(), out)
        return out.toString().trim()
    }

    @Throws(UnsupportedContentException::class)
    private fun writeBlocks(nodes: List<Node>, out: StringBuilder) {
        for (node in nodes) {
            when (node) {
                is TextNode -> {
                    val text = node.text().trim()
                    if (text.isNotEmpty()) appendParagraph(out, escapeMarkdown(text))
                }
                is Element -> writeBlock(node, out)
                else -> {}
            }
        }
    }

    @Throws(UnsupportedContentException::class)
    private fun writeBlock(el: Element, out: StringBuilder) {
        when (el.normalName()) {
            "p", "div" -> {
                val classes = el.className().lowercase()
                if ("button-row" in classes) throw UnsupportedContentException("inline buttons")
                if ("collage" in classes || "slideshow" in classes) throw UnsupportedContentException("a media gallery")
                appendParagraph(out, renderInline(el))
            }
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val hashes = "#".repeat(el.normalName()[1] - '0')
                appendParagraph(out, "$hashes ${renderInline(el)}")
            }
            "blockquote" -> {
                val inner = StringBuilder()
                writeBlocks(el.childNodes(), inner)
                val text = inner.toString().trim()
                if (text.isNotEmpty()) {
                    out.append(text.lineSequence().joinToString("\n") { if (it.isEmpty()) ">" else "> $it" })
                    out.append("\n\n")
                }
            }
            "pre" -> {
                val language = el.attr("language").ifBlank { el.attr("lang") }.trim()
                val code = preText(el)
                out.append("```").append(language).append('\n').append(code)
                if (!code.endsWith("\n")) out.append('\n')
                out.append("```\n\n")
            }
            "ul" -> {
                renderList(el, ordered = false, depth = 0, out = out)
                out.append('\n')
            }
            "ol" -> {
                renderList(el, ordered = true, depth = 0, out = out)
                out.append('\n')
            }
            "hr" -> out.append("---\n\n")
            "table" -> renderTable(el, out)
            "details" -> renderDetails(el, out)
            "figure" -> for (child in el.children()) writeBlock(child, out)
            "img", "video", "audio", "document", "location" -> appendParagraph(out, renderMedia(el))
            "summary", "cite" -> {
                val text = renderInline(el)
                if (text.isNotEmpty()) appendParagraph(out, text)
            }
            else -> appendParagraph(out, renderInline(el).trim())
        }
    }

    @Throws(UnsupportedContentException::class)
    private fun renderMedia(el: Element): String = when (el.normalName()) {
        "img" -> {
            val src = el.attr("src").trim()
            if (src.isEmpty() || src.toLongOrNull() != null) {
                // The editor carries an image as a Telegram media id, which has no site URL.
                throw UnsupportedContentException("an embedded photo")
            }
            "![${el.attr("alt")}](${linkDestination(src)})"
        }
        "video" -> throw UnsupportedContentException("an embedded video")
        "audio" -> throw UnsupportedContentException("an audio attachment")
        "document" -> throw UnsupportedContentException("a document attachment")
        "location" -> throw UnsupportedContentException("a map location")
        else -> ""
    }

    private fun renderDetails(el: Element, out: StringBuilder) {
        val summary = el.children().firstOrNull { it.normalName() == "summary" }
            ?.let { renderInline(it).trim() }.orEmpty().ifEmpty { "Details" }
        val body = StringBuilder()
        for (child in el.children()) if (child.normalName() != "summary") writeBlock(child, body)
        out.append("[details=").append(summary).append("]\n\n")
        out.append(body.toString().trim()).append("\n\n")
        out.append("[/details]\n\n")
    }

    @Throws(UnsupportedContentException::class)
    private fun renderList(el: Element, ordered: Boolean, depth: Int, out: StringBuilder) {
        val indent = "  ".repeat(depth)
        var counter = 1
        for (child in el.children()) {
            when (child.normalName()) {
                "li" -> {
                    val head = StringBuilder()
                    for (node in child.childNodes()) {
                        if (node is Element && (node.normalName() == "ul" || node.normalName() == "ol")) continue
                        head.append(renderInlineNode(node))
                    }
                    out.append(indent).append(if (ordered) "${counter++}. " else "- ")
                        .append(head.toString().trim()).append('\n')
                    for (node in child.children()) {
                        when (node.normalName()) {
                            "ul" -> renderList(node, ordered = false, depth = depth + 1, out = out)
                            "ol" -> renderList(node, ordered = true, depth = depth + 1, out = out)
                        }
                    }
                }
                "ul" -> renderList(child, ordered = false, depth = depth + 1, out = out)
                "ol" -> renderList(child, ordered = true, depth = depth + 1, out = out)
            }
        }
    }

    private fun renderTable(el: Element, out: StringBuilder) {
        val rows = el.select("tr")
            .map { row -> row.children().filter { it.normalName() == "td" || it.normalName() == "th" }.map { cellText(it) } }
        val columns = rows.maxOfOrNull { it.size } ?: 0
        if (columns == 0) return
        out.append("| ").append(rows[0].joinToString(" | ")).append(" |\n")
        out.append("| ").append((0 until columns).joinToString(" | ") { "---" }).append(" |\n")
        for (row in rows.drop(1)) {
            out.append("| ").append((0 until columns).joinToString(" | ") { row.getOrElse(it) { "" } }).append(" |\n")
        }
        out.append('\n')
    }

    private fun cellText(cell: Element): String =
        renderInline(cell).replace("|", "\\|").replace("\n", " ").trim()

    /** A `{@code <pre>}` whose own newlines upstream serializes as `{@code <br>}`. */
    private fun preText(el: Element): String {
        val sb = StringBuilder()
        for (node in el.childNodes()) {
            when (node) {
                is TextNode -> sb.append(node.text())
                is Element -> if (node.normalName() == "br") sb.append('\n') else sb.append(preText(node))
                else -> {}
            }
        }
        return sb.toString()
    }

    @Throws(UnsupportedContentException::class)
    private fun renderInline(el: Element): String {
        val sb = StringBuilder()
        for (node in el.childNodes()) sb.append(renderInlineNode(node))
        return sb.toString()
    }

    @Throws(UnsupportedContentException::class)
    private fun renderInlineNode(node: Node): String = when (node) {
        is TextNode -> escapeMarkdown(node.text())
        is Element -> {
            when (node.normalName()) {
                "b", "strong" -> "**${renderInline(node)}**"
                "i", "em" -> "*${renderInline(node)}*"
                "s", "strike", "del" -> "~~${renderInline(node)}~~"
                "u" -> "<u>${renderInline(node)}</u>"
                "mark" -> "==${renderInline(node)}=="
                "code", "tt", "kbd" -> codeSpan(node.text())
                "sub" -> "<sub>${renderInline(node)}</sub>"
                "sup" -> "<sup>${renderInline(node)}</sup>"
                "spoiler", "tg-spoiler" -> "[spoiler]${renderInline(node)}[/spoiler]"
                "a" -> {
                    val label = renderInline(node)
                    val href = node.attr("href")
                    if (href.isBlank()) label else "[$label](${linkDestination(href)})"
                }
                "br" -> "\n"
                "cite" -> renderInline(node)
                "img" -> renderMedia(node)
                "animated-emoji" -> throw UnsupportedContentException("a custom emoji")
                "button" -> throw UnsupportedContentException("inline buttons")
                else -> renderInline(node)
            }
        }
        else -> ""
    }

    /** Markdown metacharacters in the reader's own text must not turn into formatting. */
    private fun escapeMarkdown(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when (ch) {
                '\\', '`', '*', '_', '[', ']', '~', '<', '>' -> sb.append('\\').append(ch)
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** A code span keeps its text literally; only the backtick fence is chosen around it. */
    private fun codeSpan(text: String): String {
        val body = text.replace('\n', ' ')
        var longest = 0
        var run = 0
        for (ch in body) {
            run = if (ch == '`') run + 1 else 0
            if (run > longest) longest = run
        }
        val fence = "`".repeat(longest + 1)
        val pad = if (body.startsWith("`") || body.endsWith("`")) " " else ""
        return "$fence$pad$body$pad$fence"
    }

    private fun linkDestination(href: String): String =
        if (href.any { it == ' ' || it == '(' || it == ')' || it == '<' || it == '>' }) "<$href>" else href

    private fun appendParagraph(out: StringBuilder, text: String) {
        if (text.isEmpty()) return
        out.append(text).append("\n\n")
    }

    private fun escapeHtml(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun escapeAttribute(text: String): String =
        text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
}
