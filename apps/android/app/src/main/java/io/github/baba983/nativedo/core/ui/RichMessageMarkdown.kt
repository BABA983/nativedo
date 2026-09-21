/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.core.ui

import android.text.SpannableStringBuilder
import io.github.baba983.nativedo.ui.topic.TopicReplyComposerMarkdown
import org.telegram.messenger.nativedo.RichMediaUploadBridge
import org.telegram.tgnet.tl.TL_iv
import org.telegram.tgnet.tl.TL_keyboard
import org.telegram.ui.iv.BlockRow
import org.telegram.ui.iv.RichTextStyle
import org.telegram.ui.iv.TableModel

/**
 * The block editor's document as Discourse markdown.
 *
 * Telegram's rich editor edits a block model (`TL_iv.RichMessage`, presented as [BlockRow]s: a row
 * carries the list nesting, the quote nesting and the details span) while Discourse stores a post as
 * markdown (`raw`). This is that bridge, and it is markdown and not HTML on purpose: the site's web
 * composer edits the same `raw`, so a post written in our editor stays editable there and cooks
 * exactly as the server renders it (see `docs/render-model-alignment.md`, "The ui/iv project").
 *
 * What markdown cannot express is dropped or approximated, per block:
 * - table geometry (rowspan/colspan) is flattened into a plain table, alignment survives;
 * - Telegram-only inline styles (custom emoji, `marked`, sub/superscript, the accent/strike-red diff
 *   colours) have no markdown counterpart and are dropped by the inline serializer behind
 *   [TopicReplyComposerMarkdown];
 * - a map block has no Discourse equivalent and is skipped;
 * - math is emitted as `$source$`, the syntax Discourse's math plugin reads.
 */
internal object RichMessageMarkdown {

    /** One indent step per list level; the first level is flush left. */
    private const val INDENT = "    "

    /**
     * The editor's rows as markdown. [urlForId] resolves a media block's Telegram-side id to the URL
     * the file was uploaded to; a block that already carries a URL (a post loaded from the site)
     * uses that instead.
     */
    fun toMarkdown(
        rows: List<BlockRow>,
        urlForId: (Long) -> String? = RichMediaUploadBridge::urlForId,
        authors: Map<Long, TL_iv.RichText>? = null,
    ): String {
        val out = StringBuilder()
        var i = 0
        while (i < rows.size) {
            val row = rows[i]
            if (row.detailsEnd) {
                out.append("\n[/details]\n")
                i++
                continue
            }
            // A quote whose outermost id names an author becomes Discourse's quote construct; the rows
            // inside it are written without their `>` prefix. (The editor fills this map when it
            // flattens a document - `flattenBlocks(out, blocks, authors)`.)
            val qid = row.quoteIds.firstOrNull()
            val author = if (qid != null && authors != null) authors[qid] else null
            if (qid != null && author != null) {
                val run = ArrayList<BlockRow>()
                var j = i
                while (j < rows.size && rows[j].quoteIds.firstOrNull() == qid) {
                    run.add(rows[j])
                    j++
                }
                out.append("[quote=\"").append(escapeAttr(RichTextStyle.plainOf(author).trim())).append("\"]\n")
                out.append(toMarkdown(run, urlForId, null).let { stripOuterQuoteMarkers(it) }).append("\n")
                out.append("[/quote]\n")
                i = j
                continue
            }
            if (row.level > 0) {
                out.append(listItem(row, row.level, urlForId))
                i++
                continue
            }
            out.append(block(row, urlForId))
            i++
        }
        return out.toString().trim()
    }

    /** Inside a `[quote="…"]` block Discourse takes plain markdown, not `>` lines. */
    private fun stripOuterQuoteMarkers(markdown: String): String =
        markdown.split('\n').joinToString("\n") { line ->
            if (line.startsWith("> > ")) line.substring(2) else if (line.startsWith("> ")) line.substring(2) else line
        }

    /** A message's blocks without row structure (what a renderer or a copy path holds). */
    fun blocksToMarkdown(blocks: List<TL_iv.PageBlock>, urlForId: (Long) -> String? = RichMediaUploadBridge::urlForId): String {
        val rows = ArrayList<BlockRow>()
        expandBlocks(rows, blocks)
        return toMarkdown(rows, urlForId)
    }

    /**
     * A document's blocks as rows: a details block becomes its header, its children and the end
     * marker (the row shape `RichEditorListView.flattenDetails` builds), so one code path writes
     * a details block, whether it arrives as rows or as blocks.
     */
    private fun expandBlocks(out: MutableList<BlockRow>, blocks: List<TL_iv.PageBlock>) {
        for (b in blocks) {
            out.add(BlockRow(b))
            if (b is TL_iv.pageBlockDetails) {
                expandBlocks(out, b.blocks)
                out.add(BlockRow(TL_iv.pageBlockParagraph()).apply { detailsEnd = true })
            }
        }
    }

    private fun listItem(row: BlockRow, level: Int, urlForId: (Long) -> String?): String {
        val indent = INDENT.repeat((level - 1).coerceAtLeast(0))
        val prefix = when {
            row.isChecklist() -> if (row.checked) "- [x] " else "- [ ] "
            row.isOrdered() -> "${row.num}. "
            else -> "- "
        }
        val body = inline(row)
        // an item that wraps a block (a table, code, a quote) keeps that block's own form, indented
        if (body.isEmpty() && row.block !is TL_iv.pageBlockParagraph) {
            val inner = block(row, urlForId).trim('\n')
            return if (inner.isEmpty()) "" else indent + inner.replace("\n", "\n$indent") + "\n"
        }
        return "$indent$prefix$body\n"
    }

    /**
     * A list *block* as lines: the editor's own expansion (`RichEditorListView.expandListBlock`: one
     * row per item, nested lists one level deeper, a `*Blocks` item whose first block is a paragraph
     * keeps that paragraph as the item's text) with the writer's list form on top.
     */
    private fun listBlock(b: TL_iv.PageBlock, urlForId: (Long) -> String?): String {
        val rows = ArrayList<BlockRow>()
        expandListBlock(rows, b, 1)
        return rows.joinToString("") { listItem(it, it.level, urlForId) }
    }

    private fun expandListBlock(out: MutableList<BlockRow>, listBlock: TL_iv.PageBlock, level: Int) {
        var counter = 1
        if (listBlock is TL_iv.pageBlockOrderedList) {
            for (item in listBlock.items) {
                when (item) {
                    is TL_iv.TL_pageListOrderedItemText -> addListItemRow(out, item.text, level, counter, item.checkbox, item.checked)
                    is TL_iv.TL_pageListOrderedItemBlocks -> expandItemBlocks(out, item.blocks, level, true, counter, item.checkbox, item.checked)
                    else -> continue
                }
                counter++
            }
        } else if (listBlock is TL_iv.pageBlockList) {
            for (item in listBlock.items) {
                when (item) {
                    is TL_iv.TL_pageListItemText -> addListItemRow(out, item.text, level, 0, item.checkbox, item.checked)
                    is TL_iv.TL_pageListItemBlocks -> expandItemBlocks(out, item.blocks, level, false, 0, item.checkbox, item.checked)
                    else -> continue
                }
            }
        }
    }

    private fun expandItemBlocks(
        out: MutableList<BlockRow>,
        blocks: List<TL_iv.PageBlock>,
        level: Int,
        ordered: Boolean,
        num: Int,
        checkbox: Boolean,
        checked: Boolean,
    ) {
        var started = false
        for (b in blocks) {
            if (b is TL_iv.pageBlockList || b is TL_iv.pageBlockOrderedList) {
                if (!started) {
                    addListItemRow(out, null, level, num, checkbox, checked)
                    started = true
                }
                expandListBlock(out, b, level + 1)
                continue
            }
            if (!started) {
                if (b is TL_iv.pageBlockParagraph) {
                    addListItemRow(out, b.text, level, num, checkbox, checked)
                } else {
                    out.add(BlockRow(b, level, num).apply { this.checkbox = checkbox; this.checked = checked })
                }
                started = true
            } else {
                out.add(BlockRow(b, level, if (ordered) 1 else 0))
            }
        }
        if (!started) addListItemRow(out, null, level, num, checkbox, checked)
    }

    private fun addListItemRow(out: MutableList<BlockRow>, text: TL_iv.RichText?, level: Int, num: Int, checkbox: Boolean, checked: Boolean) {
        val paragraph = TL_iv.pageBlockParagraph()
        paragraph.text = text ?: TL_iv.textEmpty()
        out.add(BlockRow(paragraph, level, num).apply { this.checkbox = checkbox; this.checked = checked })
    }

    private fun block(row: BlockRow, urlForId: (Long) -> String?): String {
        val b = row.block
        val quoteDepth = row.quoteIds.size
        val text = StringBuilder()
        // a quote writes its own `>` markers, so it does not need the blank line a plain block gets
        var quoted = false
        when {
            b is TL_iv.pageBlockDetails -> {
                val title = inlineText(b.title, b)
                text.append("\n[details=\"").append(escapeAttr(title)).append("\"]\n\n")
            }
            b is TL_iv.pageBlockBlockquote || b is TL_iv.pageBlockPullquote -> {
                quoted = true
                val body = inlineText(b.text, b)
                // both quote blocks shadow PageBlock.caption with the author as RichText
                val authorText = when (b) {
                    is TL_iv.pageBlockBlockquote -> b.caption
                    is TL_iv.pageBlockPullquote -> b.caption
                    else -> null
                }
                val author = RichTextStyle.plainOf(authorText).trim()
                if (author.isNotEmpty()) {
                    text.append("[quote=\"").append(escapeAttr(author)).append("\"]\n")
                    text.append(body).append("\n[/quote]\n")
                } else {
                    text.append(quoteLines(body, 1)).append('\n')
                }
            }
            // the reader's form of a `>` run that holds more than one paragraph, or a `[quote]`
            b is TL_iv.pageBlockBlockquoteBlocks -> {
                quoted = true
                val body = toMarkdown(b.blocks.map { BlockRow(it) }, urlForId)
                val author = RichTextStyle.plainOf(b.caption).trim()
                if (author.isNotEmpty()) {
                    text.append("[quote=\"").append(escapeAttr(author)).append("\"]\n")
                    text.append(body).append("\n[/quote]\n")
                } else {
                    text.append(quoteLines(body, 1)).append('\n')
                }
            }
            b is TL_iv.pageBlockPreformatted -> {
                text.append("```").append(b.language ?: "").append('\n')
                text.append(RichTextStyle.plainOf(b.text)).append('\n')
                text.append("```\n")
            }
            b is TL_iv.pageBlockTable -> text.append(table(b))
            b is TL_iv.pageBlockList || b is TL_iv.pageBlockOrderedList -> text.append(listBlock(b, urlForId))
            b is TL_iv.pageBlockDivider -> text.append("---\n")
            b is TL_iv.pageBlockMath -> {
                val source = b.source ?: ""
                if (source.isNotEmpty()) text.append("$$").append(source).append("$$\n")
            }
            b is TL_iv.pageBlockCollage -> text.append(gallery(b.items, urlForId))
            b is TL_iv.pageBlockSlideshow -> text.append(gallery(b.items, urlForId))
            b is TL_iv.pageBlockPhoto -> text.append(photo(b, urlForId)).append('\n')
            b is TL_iv.pageBlockVideo -> text.append(media(b, urlForId, "video", b.video_id)).append('\n')
            b is TL_iv.pageBlockAudio -> text.append(media(b, urlForId, "audio", b.audio_id)).append('\n')
            b is TL_iv.pageBlockDocument -> text.append(media(b, urlForId, "document", b.document_id)).append('\n')
            b is TL_iv.pageBlockButtonRow -> text.append(buttonRow(b)).append('\n')
            headingLevel(b) > 0 -> {
                // an empty heading is how the editor seeds its document, and it is not markdown
                val body = inline(row)
                if (body.isNotEmpty()) text.append("#".repeat(headingLevel(b))).append(' ').append(body).append('\n')
            }
            else -> {
                val body = inline(row)
                if (body.isNotEmpty()) text.append(body).append('\n')
            }
        }
        if (quoteDepth > 0 && text.isNotEmpty()) {
            return quoteLines(text.toString().trimEnd('\n'), quoteDepth) + "\n"
        }
        if (quoted) return text.toString()
        return "\n" + text
    }

    private fun quoteLines(body: String, depth: Int): String {
        val prefix = "> ".repeat(depth)
        return body.split('\n').joinToString("\n") { prefix + it }
    }

    private fun photo(b: TL_iv.PageBlock, urlForId: (Long) -> String?): String {
        val url = urlForId(b.photo_id) ?: b.url
        if (url.isNullOrEmpty()) return ""
        val alt = caption(b)
        val image = "![${escapeAttr(alt)}]($url)"
        // Discourse shows no caption under an image; keep the editor's caption next to it as italic text.
        return if (alt.isEmpty()) image else "$image\n\n*${escapeAttr(alt)}*"
    }

    private fun media(b: TL_iv.PageBlock, urlForId: (Long) -> String?, kind: String, id: Long): String {
        val url = urlForId(id) ?: b.url
        if (url.isNullOrEmpty()) return ""
        val name = caption(b).ifEmpty { "$kind.${extensionFor(kind)}" }
        return "[${escapeAttr(name)}]($url)"
    }

    private fun gallery(items: List<TL_iv.PageBlock>, urlForId: (Long) -> String?): String {
        val out = StringBuilder()
        for (item in items) {
            val line = when (item) {
                is TL_iv.pageBlockPhoto -> photo(item, urlForId)
                is TL_iv.pageBlockVideo -> media(item, urlForId, "video", item.video_id)
                else -> ""
            }
            if (line.isNotEmpty()) out.append(line).append('\n')
        }
        return out.toString()
    }

    private fun buttonRow(b: TL_iv.pageBlockButtonRow): String {
        val parts = ArrayList<String>()
        for (button in b.buttons) {
            val label = RichTextStyle.plainOf(button.text)
            if (label.isEmpty()) continue
            val url = (button.type as? TL_keyboard.TL_inlineButtonTypeUrl)?.url
            parts.add(if (url.isNullOrEmpty()) label else "[${escapeAttr(label)}]($url)")
        }
        return parts.joinToString(" ")
    }

    private fun table(table: TL_iv.pageBlockTable): String {
        val model = TableModel(table)
        if (model.rowCount == 0 || model.colCount == 0) return ""
        val out = StringBuilder()
        for (r in 0 until model.rowCount) {
            out.append('|')
            for (c in 0 until model.colCount) {
                // A covered cell of a spanned anchor becomes an empty column, so the row keeps its width.
                val cell = if (model.isAnchor(r, c)) model.grid[r][c] else null
                out.append(' ').append(if (cell == null) "" else inlineText(TableModel.readStyledText(cell), table)).append(' ')
                out.append('|')
            }
            out.append('\n')
            if (r == 0) {
                out.append('|')
                for (c in 0 until model.colCount) {
                    out.append(' ').append(alignment(if (model.isAnchor(r, c)) model.grid[r][c] else null)).append(' ')
                    out.append('|')
                }
                out.append('\n')
            }
        }
        return out.toString()
    }

    private fun alignment(cell: TL_iv.pageTableCell?): String = when {
        cell == null -> "---"
        cell.align_center -> ":-:"
        cell.align_right -> "--:"
        else -> "---"
    }

    private fun inline(row: BlockRow): String = inlineText(RichTextStyle.toSpannable(row.block.text, row.block), row.block)

    private fun inlineText(rt: TL_iv.RichText?, block: TL_iv.PageBlock?): String =
        inlineText(if (rt == null) "" else RichTextStyle.toSpannable(rt, block), block)

    private fun inlineText(spanned: CharSequence?, block: TL_iv.PageBlock?): String =
        TopicReplyComposerMarkdown.serialize(SpannableStringBuilder(spanned ?: ""))

    private fun caption(b: TL_iv.PageBlock): String {
        val caption = b.caption ?: return ""
        return RichTextStyle.plainOf(caption.text).trim()
    }

    private fun headingLevel(b: TL_iv.PageBlock): Int = when (b) {
        is TL_iv.pageBlockTitle, is TL_iv.pageBlockHeading1 -> 1
        is TL_iv.pageBlockSubheader, is TL_iv.pageBlockHeading2 -> 2
        is TL_iv.pageBlockHeader, is TL_iv.pageBlockHeading3 -> 3
        is TL_iv.pageBlockHeading4 -> 4
        is TL_iv.pageBlockHeading5 -> 5
        is TL_iv.pageBlockHeading6 -> 6
        else -> 0
    }

    private fun extensionFor(kind: String): String = when (kind) {
        "video" -> "mp4"
        "audio" -> "mp3"
        else -> "pdf"
    }


    // ---------------------------------------------------------------- reader

    /**
     * Discourse markdown as the editor's document: the inverse of [toMarkdown], used to re-open one of
     * our own posts (the API returns `raw` for those) or a draft. The result is a [TL_iv.RichMessage]
     * the editor loads with `RichEditorListView.loadRichMessage`, which flattens it into rows, so this
     * parser only has to produce the block model: paragraphs and headings, fenced code with its
     * language, bullet/ordered/task lists with their nesting, `[quote="author"]`/`>` quotes,
     * `[details="title"]`, markdown tables with their alignment row, `---`, `$…$`, images and media
     * links. Inline runs go through the composer's own parser, so the markers the reply box writes
     * read back the same way.
     */
    fun parse(markdown: String): TL_iv.RichMessage {
        val message = TL_iv.RichMessage()
        message.blocks = parseBlocks(markdown.replace("\r\n", "\n").split('\n'))
        return message
    }

    private class Reader(private val lines: List<String>) {
        var index = 0
        val done: Boolean get() = index >= lines.size
        fun peek(): String = if (done) "" else lines[index]
        fun at(offset: Int): String = lines.getOrNull(index + offset) ?: ""
    }

    private fun parseBlocks(lines: List<String>): ArrayList<TL_iv.PageBlock> {
        val out = ArrayList<TL_iv.PageBlock>()
        val reader = Reader(lines)
        while (!reader.done) {
            val raw = reader.peek()
            val line = raw.trim()
            if (line.isEmpty()) {
                reader.index++
                continue
            }
            when {
                isFence(line) -> out.add(readFence(reader))
                line.startsWith("[details=") -> out.add(readDetails(reader))
                line.startsWith("[quote=") -> out.add(readQuoteWithAuthor(reader))
                line.startsWith(">") -> out.add(readQuoteRun(reader))
                headingLevel(line) > 0 -> {
                    val level = headingLevel(line)
                    val text = line.substring(level).trim()
                    reader.index++
                    val block: TL_iv.PageBlock = when (level) {
                        1 -> TL_iv.pageBlockHeading1()
                        2 -> TL_iv.pageBlockHeading2()
                        3 -> TL_iv.pageBlockHeading3()
                        4 -> TL_iv.pageBlockHeading4()
                        5 -> TL_iv.pageBlockHeading5()
                        else -> TL_iv.pageBlockHeading6()
                    }
                    block.text = styled(text)
                    out.add(block)
                }
                isDivider(line) -> {
                    reader.index++
                    out.add(TL_iv.pageBlockDivider())
                }
                isMath(line) -> {
                    reader.index++
                    out.add(readMath(line))
                }
                line.startsWith("|") -> out.add(readTable(reader))
                listMarker(line) != null -> out.add(readList(reader, listIndent(raw)))
                isImage(line) -> {
                    reader.index++
                    out.add(readImage(line))
                    // the caption line the writer emits under an image
                    val caption = altOf(line)
                    if (caption.isNotEmpty()) {
                        var offset = 0
                        while (offset < 2 && reader.at(offset).isBlank()) offset++
                        val candidate = reader.at(offset).trim()
                        if (candidate == "*$caption*" || candidate == "_${caption}_") {
                            reader.index += offset + 1
                        }
                    }
                }
                mediaLinkKind(line) != null -> {
                    reader.index++
                    out.add(readMediaLink(line))
                }
                else -> out.add(readParagraph(reader))
            }
        }
        if (out.isEmpty()) out.add(TL_iv.pageBlockParagraph().apply { text = TL_iv.textEmpty() })
        return out
    }

    private fun readFence(reader: Reader): TL_iv.PageBlock {
        val open = reader.peek().trim()
        val fence = open.take(3)
        val language = open.substring(3).trim()
        reader.index++
        val body = StringBuilder()
        while (!reader.done) {
            val line = reader.peek()
            if (line.trim().startsWith(fence)) {
                reader.index++
                break
            }
            if (body.isNotEmpty()) body.append('\n')
            body.append(line)
            reader.index++
        }
        return TL_iv.pageBlockPreformatted().apply {
            text = styled(body.toString())
            this.language = language
        }
    }

    private fun readDetails(reader: Reader): TL_iv.PageBlock {
        val title = detailsTitle(reader.peek().trim())
        reader.index++
        val inner = ArrayList<String>()
        var depth = 1
        while (!reader.done) {
            val line = reader.peek().trim()
            if (line.startsWith("[details=")) depth++
            if (line.startsWith("[/details]")) {
                depth--
                if (depth == 0) {
                    reader.index++
                    break
                }
            }
            inner.add(reader.peek())
            reader.index++
        }
        return TL_iv.pageBlockDetails().apply {
            this.title = styled(title)
            blocks = parseBlocks(inner)
            open = true
        }
    }

    private fun readQuoteWithAuthor(reader: Reader): TL_iv.PageBlock {
        val head = reader.peek().trim()
        val author = head.removePrefix("[quote=").substringBefore(']').trim().removeSurrounding("\"")
        val rest = head.substringAfter(']', "").trim()
        reader.index++
        val inner = ArrayList<String>()
        if (rest.isNotEmpty()) inner.add(rest)
        var depth = 1
        while (!reader.done) {
            val line = reader.peek().trim()
            if (line.startsWith("[quote=")) depth++
            if (line.startsWith("[/quote]")) {
                depth--
                if (depth == 0) {
                    reader.index++
                    break
                }
            }
            inner.add(reader.peek())
            reader.index++
        }
        return TL_iv.pageBlockBlockquoteBlocks().apply {
            caption = styled(author)
            blocks = parseBlocks(inner)
        }
    }

    private fun readQuoteRun(reader: Reader): TL_iv.PageBlock {
        val inner = ArrayList<String>()
        while (!reader.done) {
            val line = reader.peek()
            val trimmed = line.trim()
            if (!trimmed.startsWith(">")) break
            val marker = ">".length
            val after = trimmed.substring(marker).let { if (it.startsWith(" ")) it.substring(1) else it }
            inner.add(after)
            reader.index++
        }
        val content = parseBlocks(inner)
        // a quote of one paragraph is the leaf block; anything richer keeps its blocks
        val only = content.singleOrNull()
        if (only is TL_iv.pageBlockParagraph) {
            return TL_iv.pageBlockBlockquote().apply { text = only.text }
        }
        return TL_iv.pageBlockBlockquoteBlocks().apply { blocks = content }
    }

    private fun readMath(line: String): TL_iv.PageBlock {
        val source = line.removePrefix("$").removeSuffix("$").removeSurrounding("$").trim()
        return TL_iv.pageBlockMath().apply { this.source = source }
    }

    private fun readTable(reader: Reader): TL_iv.PageBlock {
        val table = TL_iv.pageBlockTable()
        var rowIndex = 0
        while (!reader.done) {
            val line = reader.peek().trim()
            if (!line.startsWith("|")) break
            reader.index++
            val cells = line.trim('|').split('|').map { it.trim() }
            val isAlignment = cells.isNotEmpty() && cells.all { it.matches(Regex(":?-+:?")) }
            if (isAlignment) {
                applyAlignment(table, cells)
                continue
            }
            val row = TL_iv.pageTableRow()
            for (text in cells) {
                val cell = TL_iv.pageTableCell()
                if (rowIndex == 0) cell.header = true
                TableModel.applyStyledText(cell, TopicReplyComposerMarkdown.parse(text))
                row.cells.add(cell)
            }
            table.rows.add(row)
            rowIndex++
        }
        return table
    }

    private fun applyAlignment(table: TL_iv.pageBlockTable, cells: List<String>) {
        val row = table.rows.firstOrNull() ?: return
        for ((i, spec) in cells.withIndex()) {
            val cell = row.cells.getOrNull(i) ?: continue
            cell.align_center = spec.startsWith(":") && spec.endsWith(":")
            cell.align_right = spec.startsWith("-") && spec.endsWith(":")
        }
    }

    private fun readList(reader: Reader, baseIndent: Int): TL_iv.PageBlock {
        val first = listMarker(reader.peek().trim())!!
        val ordered = first.second
        val list: TL_iv.PageBlock = if (ordered) TL_iv.pageBlockOrderedList() else TL_iv.pageBlockList()
        var counter = 1
        while (!reader.done) {
            val raw = reader.peek()
            val line = raw.trim()
            if (line.isEmpty()) {
                // a blank line ends the list only when the next line is not part of it
                val next = reader.at(1).trim()
                if (listMarker(next) == null) break
                reader.index++
                continue
            }
            val marker = listMarker(line) ?: break
            val indent = listIndent(raw)
            if (indent < baseIndent) break
            if (indent > baseIndent) {
                // a nested list belongs to the item we just added
                val nested = readList(reader, indent)
                attachNested(list, nested)
                continue
            }
            if (marker.second != ordered) break
            val itemText = line.substring(marker.third).trim()
            val checked = checkedState(itemText)
            var text = itemText
            var checkbox = false
            var isChecked = false
            if (checked != null) {
                checkbox = true
                isChecked = checked
                text = itemText.substring(3).trim().let { if (it.startsWith(" ")) it.substring(1) else it }
            }
            reader.index++
            val number = if (ordered && marker.first > 0) marker.first else counter
            if (ordered) {
                val item = TL_iv.TL_pageListOrderedItemText()
                item.text = styled(text)
                item.value = number
                item.num = number.toString()
                item.checkbox = checkbox
                item.checked = isChecked
                (list as TL_iv.pageBlockOrderedList).items.add(item)
            } else {
                val item = TL_iv.TL_pageListItemText()
                item.text = styled(text)
                item.checkbox = checkbox
                item.checked = isChecked
                (list as TL_iv.pageBlockList).items.add(item)
            }
            counter = number + 1
        }
        return list
    }

    /**
     * A list item that owns a nested list becomes the *Blocks variant of its item - the editor's
     * `expandListBlock` only recurses into those, and it keeps the item's own text as a paragraph.
     */
    private fun attachNested(list: TL_iv.PageBlock, nested: TL_iv.PageBlock) {
        when (list) {
            is TL_iv.pageBlockList -> {
                val index = list.items.size - 1
                val last = list.items.getOrNull(index) ?: return
                val item = TL_iv.TL_pageListItemBlocks()
                item.blocks.add(TL_iv.pageBlockParagraph().apply { text = last.text ?: TL_iv.textEmpty() })
                item.blocks.add(nested)
                item.checkbox = last.checkbox
                item.checked = last.checked
                list.items[index] = item
            }
            is TL_iv.pageBlockOrderedList -> {
                val index = list.items.size - 1
                val last = list.items.getOrNull(index) ?: return
                val item = TL_iv.TL_pageListOrderedItemBlocks()
                item.blocks.add(TL_iv.pageBlockParagraph().apply { text = last.text ?: TL_iv.textEmpty() })
                item.blocks.add(nested)
                item.checkbox = last.checkbox
                item.checked = last.checked
                item.value = last.value
                item.num = last.num
                list.items[index] = item
            }
        }
    }

    private fun readImage(line: String): TL_iv.PageBlock {
        val url = line.substringAfter("](", "").substringBefore(")")
        return TL_iv.pageBlockPhoto().apply {
            this.url = url
            val alt = altOf(line)
            if (alt.isNotEmpty()) {
                caption = pageCaption(styled(alt))
            }
        }
    }

    private fun readMediaLink(line: String): TL_iv.PageBlock {
        val url = line.substringAfter("](", "").substringBefore(")")
        val kind = mediaLinkKind(line)
        val label = line.substringAfter("[").substringBefore("]")
        val block: TL_iv.PageBlock = when (kind) {
            "video" -> TL_iv.pageBlockVideo()
            "audio" -> TL_iv.pageBlockAudio()
            else -> TL_iv.pageBlockDocument()
        }
        block.url = url
        if (label.isNotEmpty()) {
            block.caption = pageCaption(styled(label))
        }
        return block
    }

    /** Telegram's PageCaption wire requires both text and credit, even when credit is empty. */
    private fun pageCaption(text: TL_iv.RichText): TL_iv.PageCaption = TL_iv.PageCaption().apply {
        this.text = text
        credit = TL_iv.textEmpty()
    }

    private fun readParagraph(reader: Reader): TL_iv.PageBlock {
        val body = StringBuilder()
        while (!reader.done) {
            val line = reader.peek()
            val trimmed = line.trim()
            if (trimmed.isEmpty() || isBlockStart(trimmed)) break
            if (body.isNotEmpty()) body.append('\n')
            body.append(trimmed)
            reader.index++
        }
        return TL_iv.pageBlockParagraph().apply { text = styled(body.toString()) }
    }

    private fun isBlockStart(line: String): Boolean =
        isFence(line) || line.startsWith("[details=") || line.startsWith("[quote=") || line.startsWith(">") ||
            headingLevel(line) > 0 || isDivider(line) || isMath(line) || line.startsWith("|") ||
            listMarker(line) != null || isImage(line) || mediaLinkKind(line) != null

    private fun isFence(line: String): Boolean = line.startsWith("```") || line.startsWith("~~~")

    private fun isDivider(line: String): Boolean =
        line.length >= 3 && (line.all { it == '-' } || line.all { it == '*' } || line.all { it == '_' })

    private fun isMath(line: String): Boolean = line.startsWith("$") && line.endsWith("$") && line.length > 4

    private fun isImage(line: String): Boolean = line.startsWith("![") && line.contains("](")

    private fun altOf(line: String): String = line.removePrefix("![").substringBefore("]")

    private fun headingLevel(line: String): Int {
        var level = 0
        while (level < line.length && line[level] == '#') level++
        return if (level in 1..6 && level < line.length && line[level] == ' ') level else 0
    }

    /** (the number a marker starts with, ordered, markerLength) of a list line, or null when it is not one. */
    private fun listMarker(line: String): Triple<Int, Boolean, Int>? {
        if (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ")) return Triple(0, false, 2)
        val dot = line.indexOf(". ")
        if (dot in 1..9 && line.substring(0, dot).all { it.isDigit() }) return Triple(line.substring(0, dot).toInt(), true, dot + 2)
        return null
    }

    private fun listIndent(raw: String): Int {
        var spaces = 0
        for (c in raw) {
            when (c) {
                ' ' -> spaces++
                '\t' -> spaces += 4
                else -> return spaces
            }
        }
        return spaces
    }

    private fun checkedState(text: String): Boolean? = when {
        text.startsWith("[x]") || text.startsWith("[X]") -> true
        text.startsWith("[ ]") -> false
        else -> null
    }

    private fun mediaLinkKind(line: String): String? {
        if (!line.startsWith("[") || !line.contains("](") || !line.endsWith(")")) return null
        val url = line.substringAfter("](").removeSuffix(")").lowercase()
        return when {
            url.endsWith(".mp4") || url.endsWith(".mov") || url.endsWith(".webm") -> "video"
            url.endsWith(".mp3") || url.endsWith(".m4a") || url.endsWith(".ogg") || url.endsWith(".opus") -> "audio"
            url.endsWith(".pdf") || url.endsWith(".zip") || url.endsWith(".txt") -> "document"
            else -> null
        }
    }

    private fun styled(text: String): TL_iv.RichText = RichTextStyle.fromSpannable(TopicReplyComposerMarkdown.parse(text))

    /** Discourse's `[details="…"]` takes the title inside quotes; keep it on one line. */
    private fun escapeAttr(text: String): String = text
        .replace("\\", "\\\\")
        .replace("]", "\\]")
        .replace("\n", " ")

    /** The title of a `[details=…]` line, quoted or bare (`[details="Summary"]` and `[details=Summary]`). */
    private fun detailsTitle(line: String): String {
        val raw = line.removePrefix("[details=").trim().removeSuffix("]").trim()
        return if (raw.startsWith("\"")) raw.substringAfter('"').substringBefore('"') else raw
    }
}
