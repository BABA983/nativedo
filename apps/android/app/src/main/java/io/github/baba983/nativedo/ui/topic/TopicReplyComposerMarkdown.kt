/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic

import android.text.Editable

import org.telegram.messenger.R

/**
 * The reply composer's style actions.
 *
 * The composer edits Discourse's markdown source, which is the wire format, so a style action
 * inserts or removes the markers around the selection instead of holding Telegram's span model:
 * the payload comes from the draft text (see `TopicViewModel.submitReply`), and Discourse parses
 * markdown server-side. The toolbar that surfaces the actions is upstream's (`EditTextCaption`'s
 * selection action mode plus `FloatingToolbar`); only the action itself is markdown.
 */
internal object TopicReplyComposerMarkdown {

    private const val QUOTE_PREFIX = "> "

    /** The pairs [clear] strips; the toolbar's toggles mirror them one by one. */
    private val inlinePairs = listOf(
        "**" to "**",
        "_" to "_",
        "~~" to "~~",
        "`" to "`",
        "[u]" to "[/u]",
        "[spoiler]" to "[/spoiler]",
    )

    /**
     * Runs the toolbar action behind [itemId] — the ids are upstream's
     * (`ChatActivity.fillActionModeMenu` adds them) — and returns the selection it leaves.
     * `null` means the item is not a markdown style, so the caller keeps upstream's behaviour.
     */
    fun apply(editable: Editable, itemId: Int, start: Int, end: Int): IntRange? = when (itemId) {
        R.id.menu_bold -> bold(editable, start, end)
        R.id.menu_italic -> italic(editable, start, end)
        R.id.menu_mono -> mono(editable, start, end)
        R.id.menu_strike -> strike(editable, start, end)
        R.id.menu_underline -> underline(editable, start, end)
        R.id.menu_spoiler -> spoiler(editable, start, end)
        R.id.menu_quote -> quote(editable, start, end)
        R.id.menu_regular -> clear(editable, start, end)
        else -> null
    }

    fun bold(editable: Editable, start: Int, end: Int): IntRange = toggleInline(editable, start, end, "**")

    fun italic(editable: Editable, start: Int, end: Int): IntRange = toggleInline(editable, start, end, "_")

    fun mono(editable: Editable, start: Int, end: Int): IntRange = toggleInline(editable, start, end, "`")

    fun strike(editable: Editable, start: Int, end: Int): IntRange = toggleInline(editable, start, end, "~~")

    fun underline(editable: Editable, start: Int, end: Int): IntRange =
        toggleInline(editable, start, end, "[u]", "[/u]")

    fun spoiler(editable: Editable, start: Int, end: Int): IntRange =
        toggleInline(editable, start, end, "[spoiler]", "[/spoiler]")

    /** Wraps the selection into `[label](url)`; an empty selection links the url itself. */
    fun link(editable: Editable, start: Int, end: Int, url: String): IntRange {
        val label = if (end > start) editable.subSequence(start, end).toString() else url
        val markdown = "[$label]($url)"
        editable.replace(start, end, markdown)
        return start until (start + markdown.length)
    }

    /**
     * Quotes the lines the selection touches, or unquotes them when all of them already carry
     * the prefix. Empty lines are quoted too, so the blockquote stays one block.
     */
    fun quote(editable: Editable, start: Int, end: Int): IntRange {
        val source = editable.toString()
        val blockStart = source.lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val blockEnd = source.indexOf('\n', end.coerceAtLeast(start)).let { if (it < 0) source.length else it }
        val lines = source.substring(blockStart, blockEnd).split('\n')
        val quoted = lines.isNotEmpty() && lines.all { it.isEmpty() || it.startsWith(QUOTE_PREFIX) }
        val toggled = lines.joinToString("\n") { line ->
            if (quoted) line.removePrefix(QUOTE_PREFIX) else QUOTE_PREFIX + line
        }
        editable.replace(blockStart, blockEnd, toggled)
        return blockStart until (blockStart + toggled.length)
    }

    /** Drops the inline markers inside the selection and unquotes its lines. A caret-only selection has nothing to clear. */
    fun clear(editable: Editable, start: Int, end: Int): IntRange {
        if (end <= start) return start until end
        var body = editable.subSequence(start, end).toString()
        for ((prefix, suffix) in inlinePairs) {
            body = body.replace(prefix, "").replace(suffix, "")
        }
        val cleared = body.split('\n').joinToString("\n") { it.removePrefix(QUOTE_PREFIX) }
        editable.replace(start, end, cleared)
        return start until (start + cleared.length)
    }

    private fun toggleInline(editable: Editable, start: Int, end: Int, prefix: String, suffix: String = prefix): IntRange {
        val source = editable.toString()
        val wrapped = start - prefix.length >= 0 &&
            end + suffix.length <= source.length &&
            source.startsWith(prefix, start - prefix.length) &&
            source.startsWith(suffix, end)
        if (wrapped) {
            editable.delete(end, end + suffix.length)
            editable.delete(start - prefix.length, start)
            return (start - prefix.length) until (end - prefix.length)
        }
        if (start == end) {
            editable.insert(start, prefix + suffix)
            val caret = start + prefix.length
            return caret until caret
        }
        editable.insert(end, suffix)
        editable.insert(start, prefix)
        return (start + prefix.length) until (end + prefix.length)
    }
}
