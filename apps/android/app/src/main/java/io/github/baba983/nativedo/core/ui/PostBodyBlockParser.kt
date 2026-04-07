package io.github.baba983.nativedo.core.ui

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

private const val defaultGridColumns = 2
private val headingTags = setOf("h1", "h2", "h3")
private val discourseCalloutMarkerRegex = Regex("""^\s*\[!([A-Za-z0-9_-]+)](?:[+-])?\s*""")

internal fun parsePostBodyBlocks(html: String): List<PostBodyBlock> {
    val body = Jsoup.parseBodyFragment(html).body()
    return parsePostBodyNodes(body.childNodes())
}

private fun parsePostBodyNodes(
    nodes: List<Node>,
): List<PostBodyBlock> {
    val blocks = mutableListOf<PostBodyBlock>()
    val inlineBuffer = mutableListOf<Node>()

    fun flushInlineBuffer() {
        if (inlineBuffer.isEmpty()) {
            return
        }
        val nodesToRender = inlineBuffer.toList().trimTrailingBlockNodes()
        inlineBuffer.clear()
        val document = buildTextDocumentFromNodes(nodesToRender)
        if (document.text.isNotBlank() || document.entities.isNotEmpty()) {
            blocks += PostBodyBlock.ParagraphBlock(content = document)
        }
    }

    nodes.forEach { node ->
        when {
            node is TextNode && node.isBlank -> {
                if (inlineBuffer.isNotEmpty()) {
                    inlineBuffer += node.clone()
                }
            }

            node is Element && node.tagName() == "br" -> {
                if (inlineBuffer.isNotEmpty()) {
                    inlineBuffer += node.clone()
                }
            }

            node is Element && node.tagName() == "hr" -> {
                flushInlineBuffer()
                blocks += PostBodyBlock.HorizontalRuleBlock
            }

            node is Element && node.isDiscourseImageGrid() -> {
                flushInlineBuffer()
                val images = extractGridImages(node)
                if (images.isNotEmpty()) {
                    blocks += PostBodyBlock.ImageGridBlock(
                        images = images,
                        columns = node.attr("data-columns").toIntOrNull() ?: defaultGridColumns,
                    )
                }
            }

            node is Element && node.isDiscourseQuoteAside() -> {
                flushInlineBuffer()
                blocks += node.toDiscourseQuoteBlock()
            }

            node is Element && node.isOneboxAside() -> {
                val oneboxBlock = node.toOneboxBlock()
                if (oneboxBlock != null) {
                    flushInlineBuffer()
                    blocks += oneboxBlock
                } else {
                    flushInlineBuffer()
                    blocks += node.toUnknownBlock()
                }
            }

            node is Element && node.tagName() == "details" -> {
                flushInlineBuffer()
                blocks += node.toDetailsBlock()
            }

            node is Element && node.tagName() == "p" -> {
                flushInlineBuffer()
                blocks += if (node.containsStandaloneParagraphMedia()) {
                    node.toParagraphBlocks()
                } else {
                    buildTextDocumentFromNodes(node.childNodes().trimTrailingBlockNodes())
                        .toParagraphBlockList()
                }
            }

            node is Element && node.tagName() in headingTags -> {
                flushInlineBuffer()
                blocks += if (node.containsStandaloneParagraphMedia()) {
                    node.toHeadingBlocks()
                } else {
                    val content = buildTextDocumentFromNodes(node.childNodes().trimTrailingBlockNodes())
                    if (content.text.isNotBlank() || content.entities.isNotEmpty()) {
                        listOf(
                            PostBodyBlock.HeadingBlock(
                                level = node.toHeadingLevel(),
                                content = content,
                            ),
                        )
                    } else {
                        emptyList()
                    }
                }
            }

            node is Element && node.tagName() == "blockquote" -> {
                flushInlineBuffer()
                blocks += node.toQuoteLikeBlock()
            }

            node is Element && (node.tagName() == "ul" || node.tagName() == "ol") -> {
                flushInlineBuffer()
                blocks += node.toListBlock()
            }

            node is Element && node.tagName() == "pre" -> {
                val codeBlock = node.toCodeBlock()
                if (codeBlock != null) {
                    flushInlineBuffer()
                    blocks += codeBlock
                } else {
                    flushInlineBuffer()
                    blocks += node.toUnknownBlock()
                }
            }

            node is Element -> {
                val lightboxBlock = node.extractStandaloneLightboxBlock()
                val image = node.extractStandaloneImage()
                if (lightboxBlock != null) {
                    flushInlineBuffer()
                    blocks += lightboxBlock
                } else if (image != null) {
                    flushInlineBuffer()
                    blocks += PostBodyBlock.ImageBlock(image)
                } else if (node.isInlineRenderableElement()) {
                    inlineBuffer += node.clone()
                } else {
                    flushInlineBuffer()
                    blocks += node.toUnknownBlock()
                }
            }

            else -> {
                inlineBuffer += node.clone()
            }
        }
    }

    flushInlineBuffer()
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

private fun Element.toParagraphBlocks(): List<PostBodyBlock> {
    val blocks = mutableListOf<PostBodyBlock>()
    val inlineBuffer = mutableListOf<Node>()

    fun flushInlineBuffer() {
        if (inlineBuffer.isEmpty()) {
            return
        }
        val nodes = inlineBuffer.toList().trimTrailingBlockNodes()
        inlineBuffer.clear()
        val document = buildTextDocumentFromNodes(nodes)
        if (document.text.isNotBlank() || document.entities.isNotEmpty()) {
            blocks += PostBodyBlock.ParagraphBlock(content = document)
        }
    }

    childNodes().forEach { child ->
        val lightboxBlock = (child as? Element)?.extractStandaloneLightboxBlock()
        val image = (child as? Element)?.extractStandaloneImage()
        when {
            child is TextNode && child.isBlank && inlineBuffer.isEmpty() -> Unit

            child is Element && child.tagName() == "br" && inlineBuffer.isEmpty() -> Unit

            lightboxBlock != null -> {
                flushInlineBuffer()
                blocks += lightboxBlock
            }

            image != null -> {
                flushInlineBuffer()
                blocks += PostBodyBlock.ImageBlock(image)
            }

            child is TextNode -> {
                inlineBuffer += if (
                    inlineBuffer.lastOrNull().isInlineImageNode() &&
                    child.hasLeadingLineBreakIndentation()
                ) {
                    TextNode(child.getWholeText().trimStart())
                } else {
                    child.clone()
                }
            }

            else -> inlineBuffer += child.clone()
        }
    }

    flushInlineBuffer()
    return blocks
}

private fun Element.toHeadingBlocks(): List<PostBodyBlock> {
    val blocks = mutableListOf<PostBodyBlock>()
    val inlineBuffer = mutableListOf<Node>()
    val level = toHeadingLevel()

    fun flushInlineBuffer() {
        if (inlineBuffer.isEmpty()) {
            return
        }
        val nodes = inlineBuffer.toList().trimTrailingBlockNodes()
        inlineBuffer.clear()
        val document = buildTextDocumentFromNodes(nodes)
        if (document.text.isNotBlank() || document.entities.isNotEmpty()) {
            blocks += PostBodyBlock.HeadingBlock(
                level = level,
                content = document,
            )
        }
    }

    childNodes().forEach { child ->
        val lightboxBlock = (child as? Element)?.extractStandaloneLightboxBlock()
        val image = (child as? Element)?.extractStandaloneImage()
        when {
            child is TextNode && child.isBlank && inlineBuffer.isEmpty() -> Unit

            child is Element && child.tagName() == "br" && inlineBuffer.isEmpty() -> Unit

            lightboxBlock != null -> {
                flushInlineBuffer()
                blocks += lightboxBlock
            }

            image != null -> {
                flushInlineBuffer()
                blocks += PostBodyBlock.ImageBlock(image)
            }

            child is TextNode -> {
                inlineBuffer += if (
                    inlineBuffer.lastOrNull().isInlineImageNode() &&
                    child.hasLeadingLineBreakIndentation()
                ) {
                    TextNode(child.getWholeText().trimStart())
                } else {
                    child.clone()
                }
            }

            else -> inlineBuffer += child.clone()
        }
    }

    flushInlineBuffer()
    return blocks
}

private fun TextDocument.toParagraphBlockList(): List<PostBodyBlock> {
    if (text.isBlank() && entities.isEmpty()) {
        return emptyList()
    }
    return listOf(PostBodyBlock.ParagraphBlock(content = this))
}

private fun Element.containsStandaloneParagraphMedia(): Boolean {
    return childNodes().any { child ->
        child is Element && (
            child.extractStandaloneLightboxBlock() != null ||
                child.extractStandaloneImage() != null
            )
    }
}

private fun List<Node>.trimTrailingBlockNodes(): List<Node> {
    val trimmedNodes = toMutableList()
    while (true) {
        when (val lastNode = trimmedNodes.lastOrNull()) {
            null -> return trimmedNodes

            is Element -> {
                if (lastNode.tagName() == "br" ||
                    lastNode.text().isBlank() && !lastNode.hasAttr("src") && !lastNode.hasAttr("href")
                ) {
                    trimmedNodes.removeAt(trimmedNodes.lastIndex)
                    continue
                }
                return trimmedNodes
            }

            is TextNode -> {
                val trimmedText = lastNode.getWholeText().trimEnd()
                when {
                    trimmedText.isEmpty() -> {
                        trimmedNodes.removeAt(trimmedNodes.lastIndex)
                    }

                    trimmedText != lastNode.getWholeText() -> {
                        trimmedNodes[trimmedNodes.lastIndex] = TextNode(trimmedText)
                        return trimmedNodes
                    }

                    else -> return trimmedNodes
                }
            }

            else -> return trimmedNodes
        }
    }
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
                    children = item.toListItemBlocks(),
                )
            },
    )
}

private fun Element.toListItemBlocks(): List<PostBodyBlock> {
    val blocks = mutableListOf<PostBodyBlock>()
    val inlineBuffer = mutableListOf<Node>()

    fun flushInlineBuffer() {
        if (inlineBuffer.isEmpty()) {
            return
        }
        val fragment = inlineBuffer
            .toList()
            .trimTrailingBlockNodes()
            .joinToString(separator = "") { child -> child.outerHtml() }
        inlineBuffer.clear()
        val document = buildTextDocumentFromHtml(fragment)
        if (document.text.isNotBlank() || document.entities.isNotEmpty()) {
            blocks += PostBodyBlock.ParagraphBlock(content = document)
        }
    }

    childNodes().forEach { child ->
        val element = child as? Element
        val lightboxBlock = element?.extractStandaloneLightboxBlock()
        val image = element?.extractStandaloneImage()

        when {
            child is TextNode && child.isBlank && inlineBuffer.isEmpty() -> Unit

            element != null && element.isDiscourseImageGrid() -> {
                flushInlineBuffer()
                val images = extractGridImages(element)
                if (images.isNotEmpty()) {
                    blocks += PostBodyBlock.ImageGridBlock(
                        images = images,
                        columns = element.attr("data-columns").toIntOrNull() ?: defaultGridColumns,
                    )
                }
            }

            element != null && element.isDiscourseQuoteAside() -> {
                flushInlineBuffer()
                blocks += element.toDiscourseQuoteBlock()
            }

            element != null && element.isOneboxAside() -> {
                val oneboxBlock = element.toOneboxBlock()
                if (oneboxBlock != null) {
                    flushInlineBuffer()
                    blocks += oneboxBlock
                } else {
                    flushInlineBuffer()
                    blocks += element.toUnknownBlock()
                }
            }

            element != null && element.tagName() == "details" -> {
                flushInlineBuffer()
                blocks += element.toDetailsBlock()
            }

            element != null && element.tagName() == "p" -> {
                flushInlineBuffer()
                blocks += if (element.containsStandaloneParagraphMedia()) {
                    element.toParagraphBlocks()
                } else {
                    listOf(
                        PostBodyBlock.ParagraphBlock(
                            content = buildTextDocumentFromNodes(
                                element.childNodes().trimTrailingBlockNodes(),
                            ),
                        ),
                    )
                }
            }

            element != null && element.tagName() in headingTags -> {
                flushInlineBuffer()
                blocks += if (element.containsStandaloneParagraphMedia()) {
                    element.toHeadingBlocks()
                } else {
                    listOf(
                        PostBodyBlock.HeadingBlock(
                            level = element.toHeadingLevel(),
                            content = buildTextDocumentFromNodes(
                                element.childNodes().trimTrailingBlockNodes(),
                            ),
                        ),
                    )
                }
            }

            element != null && element.tagName() == "blockquote" -> {
                flushInlineBuffer()
                blocks += element.toQuoteLikeBlock()
            }

            element != null && (element.tagName() == "ul" || element.tagName() == "ol") -> {
                flushInlineBuffer()
                blocks += element.toListBlock()
            }

            element != null && element.tagName() == "pre" -> {
                val codeBlock = element.toCodeBlock()
                if (codeBlock != null) {
                    flushInlineBuffer()
                    blocks += codeBlock
                } else {
                    flushInlineBuffer()
                    blocks += element.toUnknownBlock()
                }
            }

            lightboxBlock != null -> {
                flushInlineBuffer()
                blocks += lightboxBlock
            }

            image != null -> {
                flushInlineBuffer()
                blocks += PostBodyBlock.ImageBlock(image)
            }

            child is TextNode && child.isBlank -> inlineBuffer += TextNode(" ")

            element != null && element.tagName() == "br" && inlineBuffer.isEmpty() -> Unit

            element != null && element.tagName() == "br" -> inlineBuffer += element.clone()

            element != null && !element.isInlineRenderableElement() -> {
                flushInlineBuffer()
                blocks += element.toUnknownBlock()
            }

            else -> inlineBuffer += child.clone()
        }
    }

    flushInlineBuffer()
    return blocks
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

private fun Node?.isInlineImageNode(): Boolean {
    val element = this as? Element ?: return false
    return element.tagName() == "img" && (
        element.hasClass("emoji") ||
            element.hasClass("site-icon")
        )
}

private fun TextNode.hasLeadingLineBreakIndentation(): Boolean {
    val rawText = getWholeText()
    val firstVisibleIndex = rawText.indexOfFirst { character -> !character.isWhitespace() }
    if (firstVisibleIndex <= 0) {
        return false
    }
    return rawText
        .substring(0, firstVisibleIndex)
        .any { character -> character == '\n' || character == '\r' }
}

private fun Element.isInlineRenderableElement(): Boolean {
    return tagName() in setOf(
        "a",
        "b",
        "strong",
        "i",
        "em",
        "span",
        "code",
        "img",
        "del",
        "s",
        "strike",
        "small",
        "mark",
        "u",
        "sup",
        "sub",
        "kbd",
    ) || isDecorativeInlineIcon()
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

private fun Element.isDiscourseImageGrid(): Boolean {
    return tagName() == "div" && hasClass("d-image-grid")
}

private fun Element.isDiscourseQuoteAside(): Boolean {
    return tagName() == "aside" && hasClass("quote")
}

private fun Element.toQuoteLikeBlock(): PostBodyBlock {
    toRenderedCalloutBlockOrNull()?.let { return it }

    val children = parsePostBodyNodes(childNodes())
    val firstParagraph = children.firstOrNull() as? PostBodyBlock.ParagraphBlock
    val parsedCallout = firstParagraph
        ?.content
        ?.toMarkdownCalloutLead()
        ?: return PostBodyBlock.QuoteBlock(children = children)

    return PostBodyBlock.CalloutBlock(
        spec = parsedCallout.spec,
        title = parsedCallout.title?.takeIf(TextDocument::hasVisibleContent),
        children = buildList {
            parsedCallout.bodyLead
                ?.takeIf(TextDocument::hasVisibleContent)
                ?.let { lead ->
                    add(PostBodyBlock.ParagraphBlock(content = lead))
                }
            addAll(children.drop(1))
        },
    )
}

private fun Element.toRenderedCalloutBlockOrNull(): PostBodyBlock.CalloutBlock? {
    val rawType = attr("data-callout-type")
        .ifBlank {
            if (hasClass("callout")) {
                "note"
            } else {
                ""
            }
        }
        .ifBlank { return null }
    val titleElement = children().firstOrNull { child ->
        child.tagName() == "div" && child.hasClass("callout-title")
    }
    val contentElement = children().firstOrNull { child ->
        child.tagName() == "div" && child.hasClass("callout-content")
    }

    return PostBodyBlock.CalloutBlock(
        spec = resolveDiscourseCalloutSpec(
            rawType = rawType,
            rawIcon = titleElement
                ?.selectFirst(".callout-icon use")
                ?.attr("href")
                .orEmpty(),
        ),
        title = titleElement
            ?.selectFirst(".callout-title-inner")
            ?.let { element -> buildTextDocumentFromHtml(element.html()) }
            ?.takeIf(TextDocument::hasVisibleContent),
        children = parsePostBodyNodes(
            contentElement?.childNodes() ?: childNodes().filterNot { node ->
                node is Element && node.hasClass("callout-title")
            },
        ),
    )
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

private data class MarkdownCalloutLead(
    val spec: DiscourseCalloutSpec,
    val title: TextDocument?,
    val bodyLead: TextDocument?,
)

private fun TextDocument.toMarkdownCalloutLead(): MarkdownCalloutLead? {
    val lineBreakStart = text.indexOfFirst { character ->
        character == '\n' || character == '\r'
    }
    val lineBreakEnd = when {
        lineBreakStart < 0 -> -1
        text.getOrNull(lineBreakStart) == '\r' && text.getOrNull(lineBreakStart + 1) == '\n' -> {
            lineBreakStart + 2
        }

        else -> lineBreakStart + 1
    }
    val firstLine = if (lineBreakStart >= 0) {
        slice(0, lineBreakStart)
    } else {
        this
    }
    val markerMatch = discourseCalloutMarkerRegex.find(firstLine.text) ?: return null
    val title = firstLine
        .slice(markerMatch.range.last + 1, firstLine.text.length)
        .trimWhitespace()
        .takeIf(TextDocument::hasVisibleContent)
    val bodyLead = if (lineBreakEnd >= 0) {
        slice(lineBreakEnd, text.length)
            .trimWhitespace()
            .takeIf(TextDocument::hasVisibleContent)
    } else {
        null
    }

    return MarkdownCalloutLead(
        spec = resolveDiscourseCalloutSpec(markerMatch.groupValues[1]),
        title = title,
        bodyLead = bodyLead,
    )
}

private fun TextDocument.slice(
    startIndex: Int,
    endIndex: Int,
): TextDocument {
    val clampedStart = startIndex.coerceIn(0, text.length)
    val clampedEnd = endIndex.coerceIn(clampedStart, text.length)
    return TextDocument(
        text = text.substring(clampedStart, clampedEnd),
        entities = entities.mapNotNull { entity ->
            entity.slice(
                rangeStart = clampedStart,
                rangeEnd = clampedEnd,
            )
        },
    )
}

private fun TextDocument.trimWhitespace(): TextDocument {
    val start = text.indexOfFirst { character -> !character.isWhitespace() }
    if (start < 0) {
        return TextDocument(text = "", entities = emptyList())
    }
    val end = text.indexOfLast { character -> !character.isWhitespace() } + 1
    return slice(start, end)
}

private fun TextDocument.hasVisibleContent(): Boolean {
    return text.isNotBlank() || entities.isNotEmpty()
}

private fun TextEntity.slice(
    rangeStart: Int,
    rangeEnd: Int,
): TextEntity? {
    val slicedStart = start.coerceIn(rangeStart, rangeEnd)
    val slicedEnd = end.coerceIn(rangeStart, rangeEnd)
    val relativeStart = slicedStart - rangeStart
    val relativeEnd = slicedEnd - rangeStart

    if (start == end) {
        if (start < rangeStart || start > rangeEnd) {
            return null
        }
        return copyWithRange(relativeStart, relativeStart)
    }
    if (slicedEnd <= slicedStart) {
        return null
    }
    return copyWithRange(relativeStart, relativeEnd)
}

private fun TextEntity.copyWithRange(
    start: Int,
    end: Int,
): TextEntity {
    return when (this) {
        is TextEntity.Bold -> copy(start = start, end = end)
        is TextEntity.Italic -> copy(start = start, end = end)
        is TextEntity.Strike -> copy(start = start, end = end)
        is TextEntity.Link -> copy(start = start, end = end)
        is TextEntity.Mention -> copy(start = start, end = end)
        is TextEntity.InlineCode -> copy(start = start, end = end)
        is TextEntity.Spoiler -> copy(start = start, end = end)
        is TextEntity.Emoji -> copy(start = start, end = end)
        is TextEntity.SiteIcon -> copy(start = start, end = end)
    }
}
