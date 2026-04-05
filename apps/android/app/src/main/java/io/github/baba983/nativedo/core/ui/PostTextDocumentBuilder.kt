package io.github.baba983.nativedo.core.ui

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

internal fun buildTextDocumentFromHtml(html: String): TextDocument {
    val body = Jsoup.parseBodyFragment(html).body()
    val state = FlattenState()

    appendNodes(
        nodes = body.childNodes(),
        state = state,
        preserveRawText = false,
    )

    return TextDocument(
        text = state.text.toString(),
        entities = state.entities.sortedBy(TextEntity::start),
    )
}

private fun appendNodes(
    nodes: List<Node>,
    state: FlattenState,
    preserveRawText: Boolean,
) {
    nodes.forEachIndexed { index, node ->
        val nextOutputKind = nextOutputKind(nodes, index + 1)
        when (node) {
            is TextNode -> appendTextNode(
                node = node,
                state = state,
                preserveRawText = preserveRawText,
                nextOutputKind = nextOutputKind,
            )

            is Element -> appendElement(
                element = node,
                state = state,
                preserveRawText = preserveRawText,
                nextOutputKind = nextOutputKind,
            )

            else -> Unit
        }
    }
}

private fun appendTextNode(
    node: TextNode,
    state: FlattenState,
    preserveRawText: Boolean,
    nextOutputKind: OutputKind,
) {
    if (preserveRawText) {
        val rawText = node.getWholeText()
        if (rawText.isNotEmpty()) {
            state.text.append(rawText)
            state.lastOutputKind = OutputKind.VisibleText
        }
        return
    }

    val wholeText = node.getWholeText()
    if (!wholeText.isBlank()) {
        val normalizedText = node.text()
        if (normalizedText.isNotEmpty()) {
            state.text.append(normalizedText)
            state.lastOutputKind = OutputKind.VisibleText
        }
        return
    }

    if (shouldAppendNormalizedSpace(state.lastOutputKind, nextOutputKind)) {
        state.text.append(' ')
        state.lastOutputKind = OutputKind.VisibleText
    }
}

private fun appendElement(
    element: Element,
    state: FlattenState,
    preserveRawText: Boolean,
    nextOutputKind: OutputKind,
) {
    val start = state.text.length

    when {
        element.tagName() == "br" -> {
            state.text.append('\n')
            state.lastOutputKind = OutputKind.LineBreak
        }

        element.tagName() in setOf("strong", "b") -> {
            appendNodes(element.childNodes(), state, preserveRawText)
            if (state.text.length > start) {
                state.entities += TextEntity.Bold(start = start, end = state.text.length)
            }
        }

        element.tagName() in setOf("em", "i") && !element.isDecorativeInlineIcon() -> {
            appendNodes(element.childNodes(), state, preserveRawText)
            if (state.text.length > start) {
                state.entities += TextEntity.Italic(start = start, end = state.text.length)
            }
        }

        element.tagName() in setOf("del", "s", "strike") -> {
            appendNodes(element.childNodes(), state, preserveRawText)
            if (state.text.length > start) {
                state.entities += TextEntity.Strike(start = start, end = state.text.length)
            }
        }

        element.tagName() == "code" -> {
            appendNodes(element.childNodes(), state, preserveRawText = true)
            val end = state.text.length
            if (end > start) {
                state.entities += TextEntity.InlineCode(start = start, end = end)
            }
        }

        element.tagName() == "a" -> {
            appendNodes(element.childNodes(), state, preserveRawText)
            if (state.text.length > start) {
                val username = parseMentionUsername(element.attr("href"))
                state.entities += if (username != null) {
                    TextEntity.Mention(start = start, end = state.text.length, username = username)
                } else {
                    TextEntity.Link(start = start, end = state.text.length, rawUrl = element.attr("href"))
                }
            }
        }

        element.tagName() == "img" -> appendImage(
            element = element,
            start = start,
            state = state,
        )

        element.isDecorativeInlineIcon() -> {
            val fallback = element.attr("aria-label").ifBlank { element.attr("title") }
            if (fallback.isNotEmpty()) {
                state.text.append(fallback)
                state.lastOutputKind = OutputKind.VisibleText
            }
        }

        element.outputKind() == OutputKind.BlankSpace && !preserveRawText -> {
            if (shouldAppendNormalizedSpace(state.lastOutputKind, nextOutputKind)) {
                state.text.append(' ')
                state.lastOutputKind = OutputKind.VisibleText
            }
        }

        else -> appendNodes(element.childNodes(), state, preserveRawText)
    }
}

private fun appendImage(
    element: Element,
    start: Int,
    state: FlattenState,
) {
    val fallback = element.inlineFallbackText()
    val rawUrl = element.attr("src")

    when {
        element.hasClass("emoji") -> {
            state.text.append(fallback)
            state.entities += TextEntity.Emoji(
                start = start,
                end = state.text.length,
                rawUrl = rawUrl,
                fallback = fallback,
            )
            state.lastOutputKind = if (fallback.isEmpty()) OutputKind.None else OutputKind.ZeroLengthEntity
        }

        element.hasClass("site-icon") -> {
            state.entities += TextEntity.SiteIcon(
                start = start,
                end = start,
                rawUrl = rawUrl,
                fallback = fallback,
            )
            state.lastOutputKind = OutputKind.ZeroLengthEntity
        }

        else -> {
            if (fallback.isNotEmpty()) {
                state.text.append(fallback)
                state.lastOutputKind = OutputKind.VisibleText
            }
        }
    }
}

private fun nextOutputKind(
    nodes: List<Node>,
    startIndex: Int,
): OutputKind {
    for (index in startIndex until nodes.size) {
        val outputKind = nodes[index].outputKind()
        if (outputKind != OutputKind.None && outputKind != OutputKind.BlankSpace) {
            return outputKind
        }
    }
    return OutputKind.None
}

private fun Node.outputKind(): OutputKind {
    return when (this) {
        is TextNode -> {
            if (getWholeText().isBlank()) {
                OutputKind.BlankSpace
            } else {
                OutputKind.VisibleText
            }
        }

        is Element -> when {
            tagName() == "br" -> OutputKind.LineBreak
            tagName() == "img" && hasClass("site-icon") -> OutputKind.ZeroLengthEntity
            tagName() == "img" && inlineFallbackText().isNotEmpty() -> OutputKind.VisibleText
            tagName() == "code" && leadingRawCodeText().firstOrNull()?.isWhitespace() == true -> OutputKind.BlankSpace
            tagName() == "code" -> OutputKind.VisibleText
            isDecorativeInlineIcon() -> {
                if (attr("aria-label").ifBlank { attr("title") }.isNotEmpty()) {
                    OutputKind.VisibleText
                } else {
                    OutputKind.None
                }
            }

            else -> childNodes().fold(OutputKind.None) { current, child ->
                mergeOutputKinds(current, child.outputKind())
            }
        }

        else -> OutputKind.None
    }
}

private fun mergeOutputKinds(
    current: OutputKind,
    next: OutputKind,
): OutputKind {
    return when {
        current == OutputKind.VisibleText -> current
        current == OutputKind.LineBreak -> current
        current == OutputKind.ZeroLengthEntity -> current
        current == OutputKind.BlankSpace && next == OutputKind.None -> current
        current == OutputKind.BlankSpace && next != OutputKind.None -> next
        current == OutputKind.None -> next
        else -> next
    }
}

private fun shouldAppendNormalizedSpace(
    previousKind: OutputKind,
    nextKind: OutputKind,
): Boolean {
    if (previousKind == OutputKind.LineBreak || nextKind == OutputKind.LineBreak) {
        return false
    }

    return (previousKind == OutputKind.VisibleText && nextKind == OutputKind.VisibleText) ||
        (previousKind == OutputKind.VisibleText && nextKind == OutputKind.ZeroLengthEntity) ||
        (previousKind == OutputKind.ZeroLengthEntity && nextKind == OutputKind.VisibleText)
}

private fun parseMentionUsername(rawUrl: String): String? {
    return Regex("""^/u/([^/?#]+)""")
        .find(rawUrl)
        ?.groupValues
        ?.getOrNull(1)
}

private fun Element.isDecorativeInlineIcon(): Boolean {
    return classNames().any { className ->
        className == "svg-icon" ||
            className == "d-icon" ||
            className.startsWith("d-icon-") ||
            className == "fa" ||
            className.startsWith("fa-")
    }
}

private fun Element.inlineFallbackText(): String {
    return when {
        hasClass("emoji") -> attr("title").ifBlank { attr("alt") }
        else -> attr("alt").ifBlank { attr("title") }
    }
}

private fun Element.leadingRawCodeText(): String {
    return childNodes()
        .firstNotNullOfOrNull { child ->
            when (child) {
                is TextNode -> child.getWholeText().takeIf { it.isNotEmpty() }
                is Element -> child.leadingRawCodeText().takeIf { it.isNotEmpty() }
                else -> null
            }
        }
        .orEmpty()
}

private class FlattenState {
    val text = StringBuilder()
    val entities = mutableListOf<TextEntity>()
    var lastOutputKind: OutputKind = OutputKind.None
}

private enum class OutputKind {
    None,
    BlankSpace,
    VisibleText,
    LineBreak,
    ZeroLengthEntity,
}
