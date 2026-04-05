package io.github.baba983.nativedo.core.ui

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

private const val defaultGridColumns = 2
private val headingTags = setOf("h1", "h2", "h3")

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

            node is Element && node.isDiscourseQuoteAside() -> {
                flushHtmlBuffer()
                blocks += node.toDiscourseQuoteBlock()
            }

            node is Element && node.isOneboxAside() -> {
                val oneboxBlock = node.toOneboxBlock()
                if (oneboxBlock != null) {
                    flushHtmlBuffer()
                    blocks += oneboxBlock
                } else {
                    htmlBuffer += node.clone()
                }
            }

            node is Element && node.tagName() == "details" -> {
                flushHtmlBuffer()
                blocks += node.toDetailsBlock()
            }

            node is Element && node.tagName() == "p" -> {
                flushHtmlBuffer()
                blocks += PostBodyBlock.ParagraphBlock(
                    content = buildTextDocumentFromHtml(node.html()),
                )
            }

            node is Element && node.tagName() in headingTags -> {
                flushHtmlBuffer()
                blocks += PostBodyBlock.HeadingBlock(
                    level = node.toHeadingLevel(),
                    content = buildTextDocumentFromHtml(node.html()),
                )
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
                val lightboxBlock = node.extractStandaloneLightboxBlock()
                val image = node.extractStandaloneImage()
                if (lightboxBlock != null) {
                    flushHtmlBuffer()
                    blocks += lightboxBlock
                } else if (image != null) {
                    flushHtmlBuffer()
                    blocks += PostBodyBlock.ImageBlock(image)
                } else {
                    flushHtmlBuffer()
                    blocks += node.toUnknownBlock()
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

private fun Element.toUnknownBlock(): PostBodyBlock.UnknownBlock {
    return PostBodyBlock.UnknownBlock(
        rawHtml = outerHtml(),
        tagName = tagName(),
        classes = classNames(),
    )
}

private fun Element.toHeadingLevel(): Int {
    return when (tagName()) {
        "h1" -> 1
        "h2" -> 2
        "h3" -> 3
        else -> error("Unsupported heading tag: ${tagName()}")
    }
}

private fun Element.toDiscourseQuoteBlock(): PostBodyBlock.DiscourseQuoteBlock {
    val titleElement = children().firstOrNull { child ->
        child.tagName() == "div" && child.hasClass("title")
    }
    val blockquoteElement = children().firstOrNull { child ->
        child.tagName() == "blockquote"
    }
    return PostBodyBlock.DiscourseQuoteBlock(
        authorDisplayName = attr("data-display-name")
            .ifBlank { attr("data-username") }
            .ifBlank { "引用" },
        authorUsername = attr("data-username").ifBlank { null },
        avatarUrl = titleElement
            ?.selectFirst("img.avatar")
            ?.attr("src")
            ?.ifBlank { null },
        title = titleElement
            ?.toDiscourseQuoteTitleDocument()
            ?.takeIf { document -> document.text.isNotBlank() },
        children = blockquoteElement?.let { element ->
            parsePostBodyNodes(element.discourseQuoteBodyNodes())
        }.orEmpty(),
    )
}

private fun Element.toDetailsBlock(): PostBodyBlock.DetailsBlock {
    val summaryElement = children().firstOrNull { child ->
        child.tagName() == "summary"
    }
    val childNodes = childNodes().filterNot { node ->
        node is Element && node.tagName() == "summary"
    }
    return PostBodyBlock.DetailsBlock(
        summary = buildTextDocumentFromHtml(summaryElement?.html().orEmpty()),
        children = parsePostBodyNodes(childNodes),
        expandedByDefault = hasAttr("open"),
    )
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

private fun Element.isDiscourseQuoteAside(): Boolean {
    return tagName() == "aside" && hasClass("quote")
}

private fun Element.isOneboxAside(): Boolean {
    return tagName() == "aside" && hasClass("onebox")
}

private fun Element.toOneboxBlock(): PostBodyBlock.OneboxBlock? {
    val sourceLink = selectFirst("header.source a[href]") ?: return null
    val sourceUrl = sourceLink.attr("href").ifBlank { attr("data-onebox-src") }.ifBlank { return null }
    val sourceLabel = sourceLink.text().ifBlank { sourceUrl }
    val article = selectFirst("article.onebox-body")
    val titleLink = article?.selectFirst("h3 a[href]")
    val title = titleLink?.text()?.ifBlank { null } ?: sourceLabel
    val targetUrl = titleLink?.attr("href")?.ifBlank { null } ?: sourceUrl
    val description = article
        ?.selectFirst("p")
        ?.text()
        ?.ifBlank { null }
    val previewImage = article
        ?.selectFirst(".aspect-image img, img.thumbnail, img")
        ?.toPostBodyImage()

    return PostBodyBlock.OneboxBlock(
        sourceLabel = sourceLabel,
        sourceUrl = sourceUrl,
        sourceIconUrl = selectFirst("header.source img.site-icon")
            ?.attr("src")
            ?.ifBlank { null },
        title = title,
        targetUrl = targetUrl,
        description = description,
        image = previewImage,
    )
}

private fun Element.toDiscourseQuoteTitleDocument(): TextDocument {
    val normalizedTitle = clone().apply {
        select("img.avatar, .quote-controls").remove()
    }
    val titleLink = normalizedTitle.selectFirst("a") ?: return TextDocument(
        text = "",
        entities = emptyList(),
    )
    return buildTextDocumentFromHtml(titleLink.outerHtml())
}

private fun Element.discourseQuoteBodyNodes(): List<Node> {
    val contentWrapper = children().firstOrNull { child ->
        child.tagName() == "div" && child.hasClass("post__contents-cooked-quote")
    }
    return contentWrapper?.childNodes() ?: childNodes()
}

private fun Element.extractStandaloneImage(): PostBodyImage? {
    return when {
        tagName() == "img" && !hasInlineTextImageClass() -> toPostBodyImage()
        tagName() == "a" && hasClass("lightbox") -> extractLightboxImage()
        isStandaloneMediaWrapper() -> {
            child(0).extractStandaloneImage()
        }

        else -> null
    }
}

private fun Element.extractStandaloneLightboxBlock(): PostBodyBlock.LightboxBlock? {
    return when {
        tagName() == "a" && hasClass("lightbox") -> toLightboxBlock()
        isStandaloneMediaWrapper() -> {
            child(0).extractStandaloneLightboxBlock()
        }

        else -> null
    }
}

private fun Element.isStandaloneMediaWrapper(): Boolean {
    return ownText().isBlank() &&
        childrenSize() == 1 &&
        child(0).tagName() != "br"
}

private fun Element.extractLightboxImage(): PostBodyImage? {
    val imageElement = children().firstOrNull { child ->
        child.tagName() == "img"
    } ?: return null

    return imageElement.toPostBodyImage(
        originalUrl = attr("href").ifBlank { null },
    )
}

private fun Element.toLightboxBlock(): PostBodyBlock.LightboxBlock? {
    val metaElement = children().firstOrNull { child ->
        child.tagName() == "div" && child.hasClass("meta")
    } ?: return null
    val image = extractLightboxImage() ?: return null

    return PostBodyBlock.LightboxBlock(
        image = image,
        filename = metaElement
            .selectFirst(".filename")
            ?.text()
            ?.ifBlank { attr("title").ifBlank { null } },
        information = metaElement
            .selectFirst(".informations")
            ?.text()
            ?.ifBlank { null },
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
