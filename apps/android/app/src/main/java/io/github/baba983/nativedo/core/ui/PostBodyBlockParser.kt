package io.github.baba983.nativedo.core.ui

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

private const val defaultGridColumns = 2
private val standaloneImageContainers = setOf("p", "div", "figure")
private val richTextTags = setOf("p", "h1", "h2", "h3")

internal fun parsePostBodyBlocks(html: String): List<PostBodyBlock> {
    val body = Jsoup.parseBodyFragment(html).body()
    return parsePostBodyNodes(body.childNodes())
}

private fun parsePostBodyNodes(
    nodes: List<Node>,
): List<PostBodyBlock> {
    val blocks = mutableListOf<PostBodyBlock>()
    val htmlBuffer = mutableListOf<Node>()

    fun flushHtmlBuffer() {
        if (htmlBuffer.isEmpty()) {
            return
        }
        val fragment = htmlBuffer.joinToString(separator = "") { node ->
            node.outerHtml()
        }.trim()
        htmlBuffer.clear()
        if (fragment.isNotBlank()) {
            blocks += PostBodyBlock.HtmlBlock(fragment)
        }
    }

    nodes.forEach { node ->
        when {
            node is TextNode && node.isBlank -> {
                if (htmlBuffer.isNotEmpty()) {
                    htmlBuffer += node.clone()
                }
            }

            node is Element && node.isDiscourseImageGrid() -> {
                flushHtmlBuffer()
                val images = extractGridImages(node)
                if (images.isNotEmpty()) {
                    blocks += PostBodyBlock.ImageGridBlock(
                        images = images,
                        columns = node.attr("data-columns").toIntOrNull() ?: defaultGridColumns,
                    )
                }
            }

            node is Element && node.tagName() == "details" -> {
                flushHtmlBuffer()
                blocks += node.toDetailsBlock()
            }

            node is Element && node.tagName() == "blockquote" -> {
                flushHtmlBuffer()
                blocks += PostBodyBlock.QuoteBlock(
                    children = parsePostBodyNodes(node.childNodes()),
                )
            }

            node is Element && (node.tagName() == "ul" || node.tagName() == "ol") -> {
                flushHtmlBuffer()
                blocks += node.toListBlock()
            }

            node is Element && node.tagName() == "pre" -> {
                val codeBlock = node.toCodeBlock()
                if (codeBlock != null) {
                    flushHtmlBuffer()
                    blocks += codeBlock
                } else {
                    htmlBuffer += node.clone()
                }
            }

            node is Element -> {
                val image = node.extractStandaloneImage()
                if (image != null) {
                    flushHtmlBuffer()
                    blocks += PostBodyBlock.ImageBlock(image)
                } else if (node.tagName() in richTextTags) {
                    flushHtmlBuffer()
                    blocks += PostBodyBlock.RichTextBlock(
                        style = node.toRichTextBlockStyle(),
                        inlines = parseInlineHtml(node.html()),
                    )
                } else {
                    htmlBuffer += node.clone()
                }
            }

            else -> {
                htmlBuffer += node.clone()
            }
        }
    }

    flushHtmlBuffer()
    return blocks
}

private fun Element.toDetailsBlock(): PostBodyBlock.DetailsBlock {
    val summaryElement = children().firstOrNull { child ->
        child.tagName() == "summary"
    }
    val childNodes = childNodes().filterNot { node ->
        node is Element && node.tagName() == "summary"
    }
    return PostBodyBlock.DetailsBlock(
        summary = PostBodyBlock.RichTextBlock(
            style = RichTextBlockStyle.Paragraph,
            inlines = parseInlineHtml(summaryElement?.html().orEmpty()),
        ),
        children = parsePostBodyNodes(childNodes),
        expandedByDefault = hasAttr("open"),
    )
}

private fun Element.toRichTextBlockStyle(): RichTextBlockStyle {
    return when (tagName()) {
        "h1" -> RichTextBlockStyle.Heading1
        "h2" -> RichTextBlockStyle.Heading2
        "h3" -> RichTextBlockStyle.Heading3
        else -> RichTextBlockStyle.Paragraph
    }
}

private fun Element.toCodeBlock(): PostBodyBlock.CodeBlock? {
    val codeElement = children().firstOrNull { child ->
        child.tagName() == "code"
    } ?: return null

    val languageHint = codeElement.classNames()
        .firstOrNull { className -> className.startsWith("language-") }
        ?.removePrefix("language-")

    return PostBodyBlock.CodeBlock(
        code = codeElement.wholeText().ifBlank { codeElement.text() },
        languageHint = languageHint,
    )
}

private fun Element.toListBlock(): PostBodyBlock.ListBlock {
    return PostBodyBlock.ListBlock(
        ordered = tagName() == "ol",
        items = children()
            .filter { child -> child.tagName() == "li" }
            .map { item ->
                ListItemBlock(
                    children = parsePostBodyNodes(item.childNodes()),
                )
            },
    )
}

internal fun extractGridImages(element: Element): List<PostBodyImage> {
    return element.children().mapNotNull { child ->
        when {
            child.tagName() == "a" && child.hasClass("lightbox") -> child.extractLightboxImage()
            child.tagName() == "img" -> child.toPostBodyImage()
            else -> child.extractStandaloneImage()
        }
    }
}

private val TextNode.isBlank: Boolean
    get() = text().isBlank()

private fun Element.isDiscourseImageGrid(): Boolean {
    return tagName() == "div" && hasClass("d-image-grid")
}

private fun Element.extractStandaloneImage(): PostBodyImage? {
    return when {
        tagName() == "img" && !hasInlineTextImageClass() -> toPostBodyImage()
        tagName() == "a" && hasClass("lightbox") -> extractLightboxImage()
        tagName() in standaloneImageContainers && ownText().isBlank() && childrenSize() == 1 -> {
            child(0).extractStandaloneImage()
        }

        else -> null
    }
}

private fun Element.extractLightboxImage(): PostBodyImage? {
    val imageElement = children().firstOrNull { child ->
        child.tagName() == "img"
    } ?: return null

    return imageElement.toPostBodyImage(
        originalUrl = attr("href").ifBlank { null },
    )
}

private fun Element.toPostBodyImage(
    originalUrl: String? = null,
): PostBodyImage? {
    val rawUrl = attr("src").ifBlank { return null }
    return PostBodyImage(
        rawUrl = rawUrl,
        originalUrl = originalUrl,
        width = attr("width").toIntOrNull(),
        height = attr("height").toIntOrNull(),
    )
}

private fun Element.hasInlineTextImageClass(): Boolean {
    return hasClass("emoji") || hasClass("site-icon")
}
