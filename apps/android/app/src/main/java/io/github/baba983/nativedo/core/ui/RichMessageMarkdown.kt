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
    fun toMarkdown(rows: List<BlockRow>, urlForId: (Long) -> String? = RichMediaUploadBridge::urlForId): String {
        val out = StringBuilder()
        for (row in rows) {
            if (row.detailsEnd) {
                out.append("\n[/details]\n")
                continue
            }
            if (row.level > 0) {
                out.append(listItem(row, row.level))
                continue
            }
            out.append(block(row, urlForId))
        }
        return out.toString().trim()
    }

    /** A message's blocks without row structure (what a renderer or a copy path holds). */
    fun blocksToMarkdown(blocks: List<TL_iv.PageBlock>, urlForId: (Long) -> String? = RichMediaUploadBridge::urlForId): String =
        toMarkdown(blocks.map { BlockRow(it) }, urlForId)

    private fun listItem(row: BlockRow, level: Int): String {
        val indent = INDENT.repeat((level - 1).coerceAtLeast(0))
        val prefix = when {
            row.isChecklist() -> if (row.checked) "- [x] " else "- [ ] "
            row.isOrdered() -> "${row.num}. "
            else -> "- "
        }
        return "$indent$prefix${inline(row)}\n"
    }

    private fun block(row: BlockRow, urlForId: (Long) -> String?): String {
        val b = row.block
        val quoteDepth = row.quoteIds.size
        val text = StringBuilder()
        when {
            b is TL_iv.pageBlockDetails -> {
                val title = inlineText(b.title, b)
                text.append("\n[details=\"").append(escapeAttr(title)).append("\"]\n\n")
            }
            b is TL_iv.pageBlockBlockquote || b is TL_iv.pageBlockPullquote -> {
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
                    text.append(quoteLines(body, 1))
                }
            }
            b is TL_iv.pageBlockPreformatted -> {
                text.append("```").append(b.language ?: "").append('\n')
                text.append(RichTextStyle.plainOf(b.text)).append('\n')
                text.append("```\n")
            }
            b is TL_iv.pageBlockTable -> text.append(table(b))
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
            headingLevel(b) > 0 -> text.append("#".repeat(headingLevel(b))).append(' ').append(inline(row)).append('\n')
            else -> {
                val body = inline(row)
                if (body.isNotEmpty()) text.append(body).append('\n')
            }
        }
        if (quoteDepth > 0 && text.isNotEmpty()) {
            return quoteLines(text.toString().trimEnd('\n'), quoteDepth) + "\n"
        }
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

    /** Discourse's `[details="…"]` takes the title inside quotes; keep it on one line. */
    private fun escapeAttr(text: String): String = text
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("]", "\\]")
        .replace("\n", " ")
}
