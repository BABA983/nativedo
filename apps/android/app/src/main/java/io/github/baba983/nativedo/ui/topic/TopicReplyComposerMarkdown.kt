/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.ui.topic

import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import org.telegram.messenger.CodeHighlighting
import org.telegram.ui.Components.QuoteSpan
import org.telegram.ui.Components.TextStyleSpan
import org.telegram.ui.Components.URLSpanReplacement

/**
 * Bridges the composer's rich text and Discourse's markdown.
 *
 * The composer edits Telegram's span model — `EditTextCaption` applies the toolbar's styles as
 * `TextStyleSpan` runs and draws them — while Discourse's payload is markdown. [serialize] turns
 * the spans into the markdown the draft and the request carry; [parse] turns a stored draft back
 * into spans, so a style survives a draft round-trip.
 */
internal object TopicReplyComposerMarkdown {

    private const val QUOTE_PREFIX = "> "

    /** What each style flag becomes on the wire; the open order is the nesting order. */
    private val markerOrder = listOf(
        TextStyleSpan.FLAG_STYLE_BOLD to "**",
        TextStyleSpan.FLAG_STYLE_ITALIC to "_",
        TextStyleSpan.FLAG_STYLE_STRIKE to "~~",
        TextStyleSpan.FLAG_STYLE_MONO to "`",
        TextStyleSpan.FLAG_STYLE_UNDERLINE to "[u]",
        TextStyleSpan.FLAG_STYLE_SPOILER to "[spoiler]",
    )

    private val closers = markerOrder.toMap() + mapOf(
        TextStyleSpan.FLAG_STYLE_UNDERLINE to "[/u]",
        TextStyleSpan.FLAG_STYLE_SPOILER to "[/spoiler]",
    )

    /** Flags the wire can carry: accent, strike-red, marked and sub/superscript have no markdown counterpart. */
    private val supportedFlags = markerOrder.fold(0) { flags, (flag, _) -> flags or flag }

    private val markers = listOf(
        Marker("**", "**", TextStyleSpan.FLAG_STYLE_BOLD),
        Marker("~~", "~~", TextStyleSpan.FLAG_STYLE_STRIKE),
        Marker("[spoiler]", "[/spoiler]", TextStyleSpan.FLAG_STYLE_SPOILER),
        Marker("[u]", "[/u]", TextStyleSpan.FLAG_STYLE_UNDERLINE),
        Marker("`", "`", TextStyleSpan.FLAG_STYLE_MONO),
        Marker("_", "_", TextStyleSpan.FLAG_STYLE_ITALIC),
        Marker("*", "*", TextStyleSpan.FLAG_STYLE_ITALIC),
    )

    /** The composer's spans as markdown: style runs become markers, links become labels, quotes become `> ` lines. */
    fun serialize(editable: Editable): String {
        val length = editable.length
        if (length == 0) return ""
        val text = editable.toString()

        val flags = IntArray(length)
        for (span in editable.getSpans(0, length, TextStyleSpan::class.java)) {
            val spanFlags = span.textStyleRun?.flags ?: 0
            addFlags(editable, flags, span, spanFlags)
        }
        for (span in editable.getSpans(0, length, URLSpanReplacement::class.java)) {
            val spanFlags = span.textStyleRun?.flags ?: 0
            addFlags(editable, flags, span, spanFlags)
        }
        for (span in editable.getSpans(0, length, CodeHighlighting.Span::class.java)) {
            addFlags(editable, flags, span, TextStyleSpan.FLAG_STYLE_MONO)
        }

        val links = ArrayList<String>()
        val linkIndex = IntArray(length) { -1 }
        for (span in editable.getSpans(0, length, URLSpanReplacement::class.java)) {
            val url = span.url ?: continue
            val index = links.size
            links.add(url)
            val start = editable.getSpanStart(span).coerceIn(0, length)
            val end = editable.getSpanEnd(span).coerceIn(0, length)
            for (i in start until end) {
                linkIndex[i] = index
            }
        }

        val quotedLineStarts = HashSet<Int>()
        for (span in editable.getSpans(0, length, QuoteSpan::class.java)) {
            val start = editable.getSpanStart(span).coerceIn(0, length)
            val end = editable.getSpanEnd(span).coerceIn(0, length)
            for (i in start until end) {
                if (i == 0 || text[i - 1] == '\n') {
                    quotedLineStarts.add(i)
                }
            }
        }

        val out = StringBuilder()
        val open = ArrayList<Int>()
        var openLink = -1
        for (i in 0 until length) {
            if (quotedLineStarts.contains(i)) {
                out.append(QUOTE_PREFIX)
            }
            val link = linkIndex[i]
            if (link != openLink) {
                if (openLink >= 0) {
                    out.append("](").append(links[openLink]).append(')')
                }
                if (link >= 0) {
                    out.append('[')
                }
                openLink = link
            }
            val desired = flags[i] and supportedFlags
            for (k in open.indices.reversed()) {
                if (desired and open[k] == 0) {
                    out.append(closeOf(open[k]))
                    open.removeAt(k)
                }
            }
            for ((flag, marker) in markerOrder) {
                if (desired and flag != 0 && !open.contains(flag)) {
                    out.append(marker)
                    open.add(flag)
                }
            }
            out.append(text[i])
        }
        for (k in open.indices.reversed()) {
            out.append(closeOf(open[k]))
        }
        if (openLink >= 0) {
            out.append("](").append(links[openLink]).append(')')
        }
        return out.toString()
    }

    /** Markdown as the composer's rich text: marker pairs become style runs, `> ` lines become a quote block. */
    fun parse(markdown: String): CharSequence {
        val out = SpannableStringBuilder()
        val quotedBlocks = ArrayList<IntRange>()
        var blockStart = -1
        var blockEnd = -1
        val lines = markdown.split('\n')
        for ((lineIndex, line) in lines.withIndex()) {
            if (line.startsWith(QUOTE_PREFIX)) {
                if (blockStart < 0) {
                    blockStart = out.length
                }
                emitInline(line.substring(QUOTE_PREFIX.length), out, 0)
                blockEnd = out.length
            } else {
                if (blockStart >= 0) {
                    quotedBlocks.add(blockStart until blockEnd)
                    blockStart = -1
                }
                emitInline(line, out, 0)
            }
            if (lineIndex < lines.size - 1) {
                out.append('\n')
            }
        }
        if (blockStart >= 0) {
            quotedBlocks.add(blockStart until blockEnd)
        }
        for (block in quotedBlocks) {
            if (block.isEmpty()) continue
            val styleSpan = QuoteSpan.QuoteStyleSpan()
            val quoteSpan = QuoteSpan(true, false, styleSpan)
            out.setSpan(quoteSpan, block.first, block.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.setSpan(styleSpan, block.first, block.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return out
    }

    private fun emitInline(source: String, out: SpannableStringBuilder, flags: Int) {
        var i = 0
        while (i < source.length) {
            val marker = markerAt(source, i)
            if (marker != null) {
                val close = source.indexOf(marker.close, i + marker.open.length)
                if (close > i) {
                    val start = out.length
                    val inner = flags or marker.flag
                    emitInline(source.substring(i + marker.open.length, close), out, inner)
                    applyStyle(out, start, out.length, inner)
                    i = close + marker.close.length
                    continue
                }
            }
            val link = linkAt(source, i)
            if (link != null) {
                val start = out.length
                emitInline(link.label, out, flags)
                out.setSpan(URLSpanReplacement(link.url), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                i = link.end + 1
                continue
            }
            out.append(source[i])
            i++
        }
    }

    private fun applyStyle(out: SpannableStringBuilder, start: Int, end: Int, flags: Int) {
        if (start >= end || flags == 0) return
        val run = TextStyleSpan.TextStyleRun()
        run.flags = flags
        out.setSpan(TextStyleSpan(run), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun markerAt(source: String, index: Int): Marker? =
        markers.firstOrNull { source.startsWith(it.open, index) }

    private fun linkAt(source: String, index: Int): Link? {
        if (source[index] != '[') return null
        val labelEnd = source.indexOf(']', index + 1)
        if (labelEnd < 0 || labelEnd + 1 >= source.length || source[labelEnd + 1] != '(') return null
        val urlEnd = source.indexOf(')', labelEnd + 2)
        if (urlEnd < 0) return null
        val label = source.substring(index + 1, labelEnd)
        val url = source.substring(labelEnd + 2, urlEnd)
        if (label.isEmpty() || url.isEmpty() || label.contains('[') || url.any { it.isWhitespace() }) return null
        return Link(label, url, urlEnd)
    }

    private fun addFlags(editable: Editable, flags: IntArray, span: Any, spanFlags: Int) {
        if (spanFlags == 0) return
        val start = editable.getSpanStart(span).coerceIn(0, flags.size)
        val end = editable.getSpanEnd(span).coerceIn(0, flags.size)
        for (i in start until end) {
            flags[i] = flags[i] or spanFlags
        }
    }

    private fun closeOf(flag: Int): String = closers[flag] ?: "**"

    private data class Marker(val open: String, val close: String, val flag: Int)

    private data class Link(val label: String, val url: String, val end: Int)
}
