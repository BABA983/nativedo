package io.github.baba983.nativedo.core.ui

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

internal fun parseInlineHtml(html: String): List<PostInline> {
    val body = Jsoup.parseBodyFragment(html).body()
    return normalizeInlineWhitespace(
        mergeAdjacentText(parseInlineNodes(body.childNodes())),
    )
}

private fun parseInlineNodes(
    nodes: List<Node>,
    preserveRawText: Boolean = false,
): List<PostInline> {
    return nodes.flatMap { node ->
        when (node) {
            is TextNode -> node.toInlineTextOrNull(preserveRawText = preserveRawText)
                ?.let { listOf(it) }
                ?: emptyList()
            is Element -> node.toPostInlines(preserveRawText = preserveRawText)
            else -> emptyList()
        }
    }
}

private fun TextNode.toInlineTextOrNull(
    preserveRawText: Boolean,
): PostInline.Text? {
    if (preserveRawText) {
        return PostInline.Text(getWholeText())
    }

    val wholeText = getWholeText()
    if (!wholeText.isBlank()) {
        return PostInline.Text(text())
    }
    return PostInline.Text(" ")
}

private fun Element.toPostInlines(
    preserveRawText: Boolean,
): List<PostInline> {
    return when {
        tagName() == "br" -> listOf(PostInline.LineBreak)
        tagName() == "a" -> listOf(
            PostInline.Link(
                children = parseInlineNodes(childNodes(), preserveRawText = preserveRawText),
                rawUrl = attr("href"),
            ),
        )
        tagName() == "img" -> listOfNotNull(toInlineImage())
        tagName() in setOf("strong", "b") -> listOf(
            PostInline.Styled(
                style = InlineStyle.Bold,
                children = parseInlineNodes(childNodes(), preserveRawText = preserveRawText),
            ),
        )
        tagName() in setOf("em", "i") && !isDecorativeInlineIcon() -> listOf(
            PostInline.Styled(
                style = InlineStyle.Italic,
                children = parseInlineNodes(childNodes(), preserveRawText = preserveRawText),
            ),
        )
        tagName() == "code" -> listOf(
            PostInline.Styled(
                style = InlineStyle.Code,
                children = parseInlineNodes(childNodes(), preserveRawText = true),
            ),
        )
        tagName() == "span" && hasLargeFontSize() -> listOf(
            PostInline.Styled(
                style = InlineStyle.LargeText,
                children = parseInlineNodes(childNodes(), preserveRawText = preserveRawText),
            ),
        )
        isDecorativeInlineIcon() -> listOf(PostInline.Text(attr("aria-label").ifBlank { attr("title") }))
        else -> parseInlineNodes(childNodes(), preserveRawText = preserveRawText)
    }
}

private fun Element.toInlineImage(): PostInline? {
    val rawUrl = attr("src").ifBlank { return PostInline.Text(inlineFallbackText()) }
    return when {
        hasClass("emoji") -> PostInline.Emoji(rawUrl = rawUrl, fallback = inlineFallbackText())
        hasClass("site-icon") -> PostInline.SiteIcon(rawUrl = rawUrl, fallback = inlineFallbackText())
        else -> PostInline.Text(inlineFallbackText())
    }
}

private fun Element.hasLargeFontSize(): Boolean {
    val style = attr("style")
    val percentage = Regex("""font-size\s*:\s*(\d+)%""")
        .find(style)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
    return percentage != null && percentage > 100
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

private fun mergeAdjacentText(inlines: List<PostInline>): List<PostInline> {
    return buildList {
        inlines.forEach { inline ->
            val previous = lastOrNull()
            if (previous is PostInline.Text && inline is PostInline.Text) {
                set(lastIndex, PostInline.Text(previous.text + inline.text))
            } else {
                add(inline)
            }
        }
    }
}

private fun normalizeInlineWhitespace(inlines: List<PostInline>): List<PostInline> {
    val normalizedChildren = inlines.map { inline ->
        when (inline) {
            is PostInline.Link -> inline.copy(
                children = normalizeInlineWhitespace(inline.children),
            )

            is PostInline.Styled -> {
                if (inline.style == InlineStyle.Code) {
                    inline
                } else {
                    inline.copy(
                        children = normalizeInlineWhitespace(inline.children),
                    )
                }
            }

            else -> inline
        }
    }

    return normalizedChildren.filterIndexed { index, inline ->
        if (inline !is PostInline.Text || inline.text != " ") {
            return@filterIndexed true
        }

        val previous = normalizedChildren.getOrNull(index - 1)
        val next = normalizedChildren.getOrNull(index + 1)
        val isLeadingOrTrailing = previous == null || next == null
        val isAdjacentToLineBreak = previous == PostInline.LineBreak || next == PostInline.LineBreak
        val isBetweenInlineImages = previous.isInlineImage() && next.isInlineImage()
        !isLeadingOrTrailing && !isAdjacentToLineBreak && !isBetweenInlineImages
    }
}

private fun PostInline?.isInlineImage(): Boolean {
    return this is PostInline.Emoji || this is PostInline.SiteIcon
}
