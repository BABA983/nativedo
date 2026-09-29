package io.github.baba983.nativedo.core.html

import io.github.baba983.nativedo.core.ui.resolveDiscourseCalloutSpec
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

internal fun parseCookedPostBody(html: String, baseUrl: String): List<CookedPostBlock> {
    val body = Jsoup.parseBodyFragment(html).body()
    val raw = parseCookedNodes(body.childNodes(), baseUrl = baseUrl)
    val blocks = coalescePollHeadings(coalesceImagesIntoCollage(raw))
    return blocks.ifEmpty {
        body.text().takeIf { it.isNotBlank() }?.let { text ->
            listOf(CookedPostBlock.Text("<p>$text</p>"))
        }.orEmpty()
    }
}

// Discourse cooked HTML emits adjacent images as adjacent <p> / .lightbox-wrapper
// blocks from the server, so coalesce 2+ adjacent Image blocks into a Collage.
private fun coalesceImagesIntoCollage(blocks: List<CookedPostBlock>): List<CookedPostBlock> {
    if (blocks.isEmpty()) return blocks
    val captionAttachedBlocks = attachAdjacentImageCaptions(blocks)
    val out = mutableListOf<CookedPostBlock>()
    var i = 0
    while (i < captionAttachedBlocks.size) {
        val block = coalesceNestedImages(captionAttachedBlocks[i])
        if (block is CookedPostBlock.Image) {
            var j = i + 1
            val group = mutableListOf(block)
            while (j < captionAttachedBlocks.size && captionAttachedBlocks[j] is CookedPostBlock.Image) {
                group.add(captionAttachedBlocks[j] as CookedPostBlock.Image)
                j++
            }
            if (group.size >= 2) {
                out.add(CookedPostBlock.Collage(images = group.toList()))
            } else {
                out.add(block)
            }
            i = j
        } else {
            out.add(block)
            i++
        }
    }
    return out
}

// Discourse often emits the poll question as a standalone heading immediately above the poll div.
private fun coalescePollHeadings(blocks: List<CookedPostBlock>): List<CookedPostBlock> {
    if (blocks.isEmpty()) return blocks
    val out = mutableListOf<CookedPostBlock>()
    var index = 0
    while (index < blocks.size) {
        val current = blocks[index]
        if (current is CookedPostBlock.Poll && current.title.isNullOrBlank() && index > 0) {
            val previous = blocks[index - 1]
            if (previous is CookedPostBlock.Heading && previous.level in 2..3) {
                if (out.isNotEmpty() && out.last() == previous) {
                    out.removeAt(out.lastIndex)
                }
                out += current.copy(title = headingPlainText(previous.html))
                index++
                continue
            }
        }
        out += current
        index++
    }
    return out
}

private fun headingPlainText(html: String): String {
    return Jsoup.parseBodyFragment(html).body().text().trim()
}

private fun attachAdjacentImageCaptions(blocks: List<CookedPostBlock>): List<CookedPostBlock> {
    val out = mutableListOf<CookedPostBlock>()
    var i = 0
    while (i < blocks.size) {
        val image = blocks[i] as? CookedPostBlock.Image
        val nextText = blocks.getOrNull(i + 1) as? CookedPostBlock.Text
        if (image != null && image.captionHtml == null && nextText != null && nextText.html.looksLikeImageCaptionHtml()) {
            out.add(image.copy(captionHtml = nextText.html))
            i += 2
        } else {
            out.add(blocks[i])
            i++
        }
    }
    return out
}

private fun coalesceNestedImages(block: CookedPostBlock): CookedPostBlock {
    return when (block) {
        is CookedPostBlock.Quote -> block.copy(children = coalesceImagesIntoCollage(block.children))
        is CookedPostBlock.Callout -> block.copy(children = coalesceImagesIntoCollage(block.children))
        is CookedPostBlock.ListItems -> block.copy(
            items = block.items.map { item ->
                item.copy(blocks = coalesceImagesIntoCollage(item.blocks))
            },
        )
        is CookedPostBlock.Table -> block.copy(
            headers = block.headers.map { cell -> cell.copy(blocks = coalesceImagesIntoCollage(cell.blocks)) },
            rows = block.rows.map { row ->
                row.map { cell -> cell.copy(blocks = coalesceImagesIntoCollage(cell.blocks)) }
            },
        )
        is CookedPostBlock.Details -> block.copy(children = coalesceImagesIntoCollage(block.children))
        else -> block
    }
}

internal fun cookedGalleryImages(blocks: List<CookedPostBlock>, baseUrl: String): List<GalleryImage> {
    val seen = linkedMapOf<String, GalleryImage>()
    blocks.flatMap(::renderedImageBlocks).forEach { image ->
        val srcResolved = resolvePostImageUrl(baseUrl, image.src)
        val fullUrl = resolvePostImageViewerUrl(baseUrl, image)
        val thumbUrl = if (srcResolved != fullUrl) srcResolved else null
        if (!seen.containsKey(fullUrl)) {
            seen[fullUrl] = GalleryImage(
                url = fullUrl,
                thumbUrl = thumbUrl,
                widthPx = image.widthPx,
                heightPx = image.heightPx,
                caption = image.captionHtml?.plainTextFromHtml()?.takeIf { it.isNotEmpty() },
            )
        }
    }
    return seen.values.toList()
}

private fun renderedImageBlocks(block: CookedPostBlock): List<CookedPostBlock.Image> {
    return when (block) {
        is CookedPostBlock.Image -> listOf(block)
        is CookedPostBlock.Collage -> block.images
        is CookedPostBlock.Quote -> block.children.flatMap(::renderedImageBlocks)
        is CookedPostBlock.Callout -> block.children.flatMap(::renderedImageBlocks)
        is CookedPostBlock.ListItems -> block.items.flatMap { item -> item.blocks.flatMap(::renderedImageBlocks) }
        is CookedPostBlock.Table -> {
            block.headers.flatMap { cell -> cell.blocks.flatMap(::renderedImageBlocks) } +
                block.rows.flatMap { row -> row.flatMap { cell -> cell.blocks.flatMap(::renderedImageBlocks) } }
        }
        is CookedPostBlock.Details -> block.children.flatMap(::renderedImageBlocks)
        else -> emptyList()
    }
}

private val blockElementNames = setOf(
    "p",
    "blockquote",
    "aside",
    "pre",
    "ul",
    "ol",
    "details",
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    "hr",
    "table",
    "div",
    "figure",
)

private fun parseCookedNodes(
    nodes: List<Node>,
    quoteLevel: Int = 0,
    baseUrl: String = "",
): List<CookedPostBlock> {
    val blocks = mutableListOf<CookedPostBlock>()
    val inline = StringBuilder()
    fun flushInline() {
        val html = inline.toString()
        inline.clear()
        blocks += parseInlineHtmlFragment(html)
    }
    nodes.forEachIndexed { index, node ->
        val element = node as? Element
        if (element != null && element.normalName() in blockElementNames) {
            flushInline()
            if (!element.isCookedNonContentArtifact() && !element.isOneboxPlaceholderBeforeAside(nodes, index)) {
                blocks += parseCookedElement(element, quoteLevel, baseUrl)
            }
        } else {
            inline.append(node.outerHtml())
        }
    }
    flushInline()
    return blocks
}

private fun parseCookedElement(element: Element, quoteLevel: Int, baseUrl: String): List<CookedPostBlock> {
    return when (element.normalName()) {
        "p" -> parseParagraphElement(element, baseUrl)
        "blockquote" -> parseBlockquoteElement(element, quoteLevel, baseUrl)
        "aside" -> parseAsideElement(element, quoteLevel, baseUrl)
        "pre" -> {
            val code = element.selectFirst("code")
            listOf(
                CookedPostBlock.Code(
                    code = (code?.wholeText() ?: element.wholeText()).trimEnd(),
                    // Discourse writes the fence's language as a class on the code element
                    // (`lang-kotlin`); anything else is a styling class, not a language.
                    language = code?.classNames()
                        ?.firstOrNull { it.startsWith("lang-") }
                        ?.removePrefix("lang-")
                        // `auto` is Discourse's "no language given", not a language upstream could
                        // label or highlight.
                        ?.takeIf { it.isNotBlank() && it != "auto" },
                ),
            )
        }
        "ul", "ol" -> parseListElement(element, quoteLevel, baseUrl)
        "details" -> parseDetailsElement(element, quoteLevel, baseUrl)
        "h1" -> listOf(CookedPostBlock.Heading(level = 1, html = element.html()))
        "h2" -> listOf(CookedPostBlock.Heading(level = 2, html = element.html()))
        "h3" -> listOf(CookedPostBlock.Heading(level = 3, html = element.html()))
        "h4" -> listOf(CookedPostBlock.Heading(level = 4, html = element.html()))
        "h5" -> listOf(CookedPostBlock.Heading(level = 5, html = element.html()))
        "h6" -> listOf(CookedPostBlock.Heading(level = 6, html = element.html()))
        "hr" -> listOf(CookedPostBlock.Rule)
        "table" -> parseTableElement(element, quoteLevel, baseUrl)
        "div" -> parseDivElement(element, quoteLevel, baseUrl)
        "figure" -> parseFigureElement(element, quoteLevel, baseUrl)
        else -> element.html().takeIf { it.isNotBlank() }?.let { listOf(CookedPostBlock.Text(it)) }.orEmpty()
    }
}

private fun parseInlineHtmlFragment(html: String): List<CookedPostBlock> {
    val body = Jsoup.parseBodyFragment(html).body()
    val images = imageBlocksFrom(body)
    val textHtml = body.clone().apply {
        removeCookedNonContentArtifacts()
        select("img:not(.emoji):not(.site-icon), .lightbox-wrapper").remove()
    }.html().takeIfHtmlNotBlank()
    return listOfNotNull(textHtml?.let(CookedPostBlock::Text)) + images
}

private fun parseParagraphElement(element: Element, baseUrl: String): List<CookedPostBlock> {
    attachmentBlock(element, baseUrl)?.let { attachment -> return listOf(attachment) }
    val images = imageBlocksFrom(element)
    val textHtml = element.clone().apply {
        removeCookedNonContentArtifacts()
        select("img:not(.emoji):not(.site-icon), .lightbox-wrapper").remove()
    }.html().takeIfHtmlNotBlank()
    if (images.size == 1 && textHtml != null) {
        return listOf(images.single().copy(captionHtml = textHtml))
    }
    return listOfNotNull(textHtml?.let(CookedPostBlock::Text)) + images
}

/**
 * A paragraph that is exactly one cooked attachment - Discourse's `a.attachment`, optionally
 * followed by the ` (12.3 KB)` size text it appends - is a block of its own, so both bodies draw it
 * as the document row. An attachment *inside* a sentence stays a link, which is also what the site
 * does with it. The site cooks the anchor's href as a short URL, a CDN URL or an old
 * `/uploads/...` path, so it is resolved against the post's base like any other link.
 */
private fun attachmentBlock(element: Element, baseUrl: String): CookedPostBlock.Attachment? {
    val anchor = element.selectFirst("a.attachment") ?: return null
    if (element.children().size != 1 || element.children()[0] !== anchor) return null
    var rest = ""
    for (node in element.childNodes()) {
        if (node === anchor) continue
        if (node !is TextNode) return null
        rest += node.text()
    }
    // The size is the site's optional sibling text, and nothing else may follow the anchor.
    if (rest.isNotBlank() && !rest.trim().startsWith("(")) return null
    val href = anchor.attr("href").ifBlank { return null }
    val name = anchor.text().trim().ifBlank { href.substringAfterLast('/') }
    return CookedPostBlock.Attachment(
        name = name,
        url = resolvePostImageUrl(baseUrl, href),
        sizeText = rest.trim().removePrefix("(").removeSuffix(")").trim(),
    )
}

// The element names a Discourse callout can be cooked onto. The Quote Callouts theme component
// emits a `blockquote.callout`, but the same `callout` / `callout-title` / `callout-content`
// structure also shows up on a `div` or `aside` (other callout renderers, older markup), and a
// `div.callout.callout-note` used to fall through `parseDivElement` into a plain run of Text.
private val calloutContainerNames = setOf("blockquote", "div", "aside")

/**
 * True when [element] carries callout semantics, whatever element it is cooked on.
 *
 * A Discourse quote is an `aside.quote`, so the `quote` class always wins and a quote is never read
 * as a callout. A `blockquote.callout` is a callout on the class alone (the theme component's
 * shape); a `div` / `aside` needs both a callout marker (`callout` class or `data-callout-type` /
 * `data-callout`) and the structural children, so an unrelated page `.callout` class is not stolen.
 */
private fun Element.isCalloutContainer(): Boolean {
    if (normalName() !in calloutContainerNames) return false
    if (hasClass("quote")) return false
    if (normalName() == "blockquote" && hasClass("callout")) return true
    val hasCalloutStructure = selectFirst(":root > .callout-title") != null ||
        selectFirst(":root > .callout-content") != null
    val hasCalloutMarker = hasClass("callout") || hasAttr("data-callout-type") || hasAttr("data-callout")
    return hasCalloutStructure && hasCalloutMarker
}

private fun parseBlockquoteElement(element: Element, quoteLevel: Int, baseUrl: String): List<CookedPostBlock> {
    if (element.isCalloutContainer()) {
        return parseCalloutElement(element, quoteLevel, baseUrl)
    }
    val markdownCallout = parseMarkdownCalloutElement(element, quoteLevel, baseUrl)
    if (markdownCallout != null) {
        return listOf(markdownCallout)
    }
    val html = element.html().takeIfHtmlNotBlank() ?: return emptyList()
    return listOf(
        CookedPostBlock.Quote(
            title = null,
            html = html,
            children = coalesceImagesIntoCollage(parseCookedNodes(element.childNodes(), quoteLevel + 1, baseUrl)),
            level = quoteLevel,
        ),
    )
}

private data class MarkdownCalloutHeader(
    val type: String,
    val titleHtml: String?,
    val collapsible: Boolean = false,
    val defaultExpanded: Boolean = true,
)

private fun parseMarkdownCalloutElement(element: Element, quoteLevel: Int, baseUrl: String): CookedPostBlock.Callout? {
    val content = element.clone().apply {
        removeCookedNonContentArtifacts()
    }
    val firstParagraph = content.selectFirst("p") ?: return null
    val header = firstParagraph.extractMarkdownCalloutHeader() ?: return null
    val spec = resolveDiscourseCalloutSpec(header.type)
    val children = coalesceImagesIntoCollage(parseCookedNodes(content.childNodes(), quoteLevel, baseUrl))
    val html = content.html().takeIfHtmlNotBlank()
        ?: children.joinToString(separator = "\n") { child ->
            when (child) {
                is CookedPostBlock.Text -> child.html
                is CookedPostBlock.Heading -> child.html
                is CookedPostBlock.Code -> "<pre><code>${child.code}</code></pre>"
                else -> ""
            }
        }.takeIfHtmlNotBlank()
        ?: return null
    return CookedPostBlock.Callout(
        type = spec.type,
        titleHtml = header.titleHtml,
        icon = spec.icon,
        color = spec.color,
        html = html,
        children = children,
        collapsible = header.collapsible,
        defaultExpanded = header.defaultExpanded,
    )
}

private fun Element.extractMarkdownCalloutHeader(): MarkdownCalloutHeader? {
    val html = html()
    val match = Regex(
        "^\\s*\\[!([A-Za-z][A-Za-z0-9_-]*)]([+\\-])?\\s*([^<\\n\\r]*)?\\s*(?:<br\\s*/?>)?",
        RegexOption.IGNORE_CASE,
    ).find(html)
        ?: return null
    val type = match.groupValues.getOrNull(1).orEmpty().lowercase()
    if (type.isBlank()) {
        return null
    }
    val foldModifier = match.groupValues.getOrNull(2).orEmpty()
    val title = match.groupValues.getOrNull(3)
        ?.trim()
        ?.removePrefix("+")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    val remainingHtml = html.removeRange(match.range).takeIfHtmlNotBlank()
    if (remainingHtml != null) {
        html(remainingHtml)
    } else {
        remove()
    }
    val collapsible = foldModifier.isNotEmpty()
    val defaultExpanded = foldModifier != "-"
    return MarkdownCalloutHeader(
        type = type,
        titleHtml = title,
        collapsible = collapsible,
        defaultExpanded = defaultExpanded,
    )
}

private fun parseCalloutElement(element: Element, quoteLevel: Int, baseUrl: String): List<CookedPostBlock> {
    val titleElement = element.children().firstOrNull { child -> child.hasClass("callout-title") }
        ?: element.selectFirst(".callout-title")
    // The theme component names the title text `.callout-title-inner`; other renderers use
    // `.callout-title-text`. Both carry the same semantics.
    val titleInner = titleElement?.selectFirst(".callout-title-inner, .callout-title-text")
    val titlePlain = titleInner?.text()?.trim()?.takeIf { it.isNotBlank() }
        ?.removePrefix("+")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    val titleHtml = titlePlain?.let { org.jsoup.nodes.TextNode(it).outerHtml().trim() }
        ?: titleElement?.clone()?.apply {
            select(".callout-icon, .callout-title-icon, .callout-fold, .callout-fold-icon").remove()
        }?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?.removePrefix("+")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { org.jsoup.nodes.TextNode(it).outerHtml().trim() }
    val collapsible = element.hasClass("is-collapsible") ||
        element.attr("data-collapsible").equals("true", ignoreCase = true) ||
        titleElement?.selectFirst(".callout-fold, .callout-fold-icon") != null ||
        element.selectFirst(".callout-fold, .callout-fold-icon") != null
    val rawIcon = titleElement?.selectFirst(
        ".callout-icon svg use[href], .callout-icon svg use[xlink:href], " +
            ".callout-title-icon svg use[href], .callout-title-icon svg use[xlink:href]",
    )?.attr("href")
        ?.ifBlank {
            titleElement.selectFirst(".callout-icon svg use[xlink:href], .callout-title-icon svg use[xlink:href]")
                ?.attr("xlink:href").orEmpty()
        }
        .orEmpty()
    val rawType = element.attr("data-callout-type")
        .ifBlank { element.attr("data-callout") }
        .ifBlank {
            element.classNames().firstOrNull { className -> className.startsWith("callout-") }
                ?.removePrefix("callout-")
                .orEmpty()
        }
    val spec = resolveDiscourseCalloutSpec(rawType, rawIcon)
    val content = (element.children().firstOrNull { child -> child.hasClass("callout-content") }
        ?: element).clone().apply {
        if (!hasClass("callout-content")) {
            children().firstOrNull { child -> child.hasClass("callout-title") }?.remove()
            select(".callout-title").remove()
        }
        removeCookedNonContentArtifacts()
    }
    val children = coalesceImagesIntoCollage(parseCookedNodes(content.childNodes(), quoteLevel, baseUrl))
    val html = content.html().takeIfHtmlNotBlank()
        ?: children.joinToString(separator = "\n") { child ->
            when (child) {
                is CookedPostBlock.Text -> child.html
                is CookedPostBlock.Heading -> child.html
                is CookedPostBlock.Code -> "<pre><code>${child.code}</code></pre>"
                else -> ""
            }
        }.takeIfHtmlNotBlank()
        ?: return emptyList()

    return listOf(
        CookedPostBlock.Callout(
            type = spec.type,
            titleHtml = titleHtml,
            icon = spec.icon,
            color = spec.color,
            html = html,
            children = children,
            collapsible = collapsible,
        ),
    )
}

private data class DiscourseQuoteTitleParts(
    val avatarUrl: String?,
    val titleHtml: String?,
    val categoryName: String?,
    val categoryColor: String?,
    val categoryTextColor: String?,
    val categoryHref: String?,
)

private fun cssVarFromStyle(style: String, varName: String): String? =
    Regex("""--${Regex.escape(varName)}:\s*([^;]+)""", RegexOption.IGNORE_CASE)
        .find(style)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }

/**
 * Discourse `aside.quote .title`: avatar column + text column (BlockEmbedPostCell-style).
 * Strips quote-controls, inline avatar, and badge-category from title HTML.
 */
private fun parseDiscourseQuoteTitle(titleElement: Element?, baseUrl: String): DiscourseQuoteTitleParts {
    if (titleElement == null) {
        return DiscourseQuoteTitleParts(null, null, null, null, null, null)
    }
    val avatarSrc = titleElement.selectFirst("img.avatar[src]")?.attr("src")?.takeIf { it.isNotBlank() }
    val avatarUrl = avatarSrc?.let { resolvePostImageUrl(baseUrl, it) }
    val textContent = titleElement.selectFirst(".quote-title__text-content")
    val contentRoot = if (textContent != null) {
        textContent.clone()
    } else {
        titleElement.clone().apply {
            select(".quote-controls").remove()
            select("img.avatar").remove()
        }
    }
    contentRoot.select(".quote-controls").remove()
    contentRoot.select("img.avatar").remove()
    val categoryWrapper = contentRoot.selectFirst("a.badge-category__wrapper")
    val categoryHref = categoryWrapper?.attr("href")?.takeIf { it.isNotBlank() }
    val categoryName = contentRoot.selectFirst(".badge-category__name")?.text()?.takeIf { it.isNotBlank() }
    val badgeSpan = contentRoot.selectFirst(".badge-category")
    val categoryColor = badgeSpan?.attr("style")?.let { cssVarFromStyle(it, "category-badge-color") }
    val categoryTextColor = badgeSpan?.attr("style")?.let { cssVarFromStyle(it, "category-badge-text-color") }
    contentRoot.select("a.badge-category__wrapper").remove()
    contentRoot.select(".badge-category").remove()
    val titleHtml = contentRoot.html().takeIfHtmlNotBlank()
    return DiscourseQuoteTitleParts(
        avatarUrl = avatarUrl,
        titleHtml = titleHtml,
        categoryName = categoryName,
        categoryColor = categoryColor,
        categoryTextColor = categoryTextColor,
        categoryHref = categoryHref,
    )
}

private fun parseAsideElement(element: Element, quoteLevel: Int, baseUrl: String): List<CookedPostBlock> {
    if (element.hasClass("quote")) {
        val titleElement = element.selectFirst(".title")
        val title = titleElement?.text()?.takeIf { it.isNotBlank() }
        val titleParts = parseDiscourseQuoteTitle(titleElement, baseUrl)
        val quoteElement = element.selectFirst("blockquote")
        val quoteHtml = quoteElement?.html()?.takeIfHtmlNotBlank() ?: element.html()
        return listOf(
            CookedPostBlock.Quote(
                title = title,
                titleHtml = titleParts.titleHtml,
                titleAvatarUrl = titleParts.avatarUrl,
                titleCategoryName = titleParts.categoryName,
                titleCategoryColor = titleParts.categoryColor,
                titleCategoryTextColor = titleParts.categoryTextColor,
                titleCategoryHref = titleParts.categoryHref,
                html = quoteHtml,
                children = quoteElement
                    ?.let { coalesceImagesIntoCollage(parseCookedNodes(it.childNodes(), quoteLevel + 1, baseUrl)) }
                    .orEmpty(),
                sourceTopicId = element.attr("data-topic").toLongOrNull(),
                sourcePostNumber = element.attr("data-post").toIntOrNull(),
                level = quoteLevel,
            ),
        )
    }
    if (element.isCalloutContainer()) {
        return parseCalloutElement(element, quoteLevel, baseUrl)
    }
    if (element.hasClass("onebox")) {
        val onebox = parseOneboxAside(element)
        if (onebox != null) return listOf(onebox)
    }
    val link = element.selectFirst("a[href]")
    val title = link?.text()?.takeIf { it.isNotBlank() }
    val href = link?.attr("href").orEmpty()
    val bodyText = element.selectFirst("article")?.html()?.takeIfHtmlNotBlank()
    return listOfNotNull(
        title?.let { CookedPostBlock.Text("<p><a href=\"$href\">$it</a></p>") },
        bodyText?.let(CookedPostBlock::Text),
    ) + imageBlocksFrom(element)
}

private fun parseOneboxAside(element: Element): CookedPostBlock.Onebox? {
    val article = element.selectFirst("article.onebox-body") ?: element.selectFirst("article")
    val headerLink = element.selectFirst("header.source a[href]")
    val href = element.oneboxHrefCandidate() ?: return null
    val domain = headerLink?.text()?.takeIf { it.isNotBlank() }
    val siteIconUrl = element.selectFirst("header.source img.site-icon")?.attr("src")?.takeIf { it.isNotBlank() }
    val thumbnailElement = article?.selectFirst("img.thumbnail") ?: article?.selectFirst("img[src]")
    val thumbnailUrl = thumbnailElement?.attr("src")
    val titleElement = article?.selectFirst("h3")
    val articleLink = article?.selectFirst("h3 a[href]")
    val previewType = element.oneboxPreviewType()
    val title = (articleLink?.text() ?: titleElement?.text())
        ?.takeIf { it.isNotBlank() }
        ?: domain
        ?: href
    val titleHtml = titleElement?.html()?.takeIfHtmlNotBlank()
    val description = article?.select("p")
        ?.joinToString(separator = "\n") { it.text() }
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    val descriptionHtml = article?.select("p")
        ?.joinToString(separator = "<br>") { it.html() }
        ?.takeIfHtmlNotBlank()
    return CookedPostBlock.Onebox(
        href = href,
        domain = domain,
        siteIconUrl = siteIconUrl,
        thumbnailUrl = thumbnailUrl,
        thumbnailWidthPx = thumbnailElement?.attr("width")?.toIntOrNull() ?: 0,
        thumbnailHeightPx = thumbnailElement?.attr("height")?.toIntOrNull() ?: 0,
        previewType = previewType,
        forceLargeMedia = article?.selectFirst(".aspect-image") != null,
        forceSmallMedia = element.hasForceSmallMediaMarker(article),
        title = title,
        titleHtml = titleHtml,
        description = description,
        descriptionHtml = descriptionHtml,
    )
}

private fun Element.hasForceSmallMediaMarker(article: Element?): Boolean {
    val markers = listOf("force-small-media", "force-small", "small-image")
    return markers.any { marker ->
        hasClass(marker) ||
            article?.hasClass(marker) == true ||
            article?.selectFirst(".$marker") != null
    }
}

private fun Element.isOneboxPlaceholderBeforeAside(nodes: List<Node>, index: Int): Boolean {
    if (oneboxPlaceholderHref() == null) {
        return false
    }
    for (i in index + 1 until nodes.size) {
        val nextElement = nodes[i] as? Element ?: continue
        if (nextElement.normalName() !in blockElementNames || nextElement.isCookedNonContentArtifact()) {
            continue
        }
        return nextElement.normalName() == "aside" && nextElement.hasClass("onebox")
    }
    return false
}

private fun Element.oneboxPlaceholderHref(): String? {
    if (normalName() != "p") {
        return null
    }
    val content = clone().apply {
        removeCookedNonContentArtifacts()
    }
    val link = content.select("a.onebox[href]").singleOrNull() ?: return null
    val href = link.attr("href").takeIf { it.isNotBlank() } ?: return null
    link.remove()
    return href.takeIf { content.text().isBlank() && content.select("img[src]").isEmpty() }
}

private fun Element.oneboxHrefCandidate(): String? {
    val article = selectFirst("article.onebox-body") ?: selectFirst("article")
    val headerLink = selectFirst("header.source a[href]")
    val articleLink = article?.selectFirst("h3 a[href]")
    return attr("data-onebox-src")
        .ifBlank { articleLink?.attr("href").orEmpty() }
        .ifBlank { headerLink?.attr("href").orEmpty() }
        .takeIf { it.isNotBlank() }
}

private fun Element.oneboxPreviewType(): String? {
    attr("data-onebox-type")
        .ifBlank {
            classNames()
                .firstOrNull { className -> className != "onebox" && className != "allowlistedgeneric" }
                .orEmpty()
        }
        .ifBlank {
            if (hasClass("allowlistedgeneric")) "article" else ""
        }
        .takeIf { it.isNotBlank() }
        ?.let { return it.lowercase() }
    return null
}

private fun parsePollElement(element: Element): CookedPostBlock.Poll {
    val name = element.attr("data-poll-name").takeIf { it.isNotBlank() }
    val title = element.attr("data-poll-title").takeIf { it.isNotBlank() }
    val type = element.attr("data-poll-type").takeIf { it.isNotBlank() }
    val status = element.attr("data-poll-status").takeIf { it.isNotBlank() }
    val results = element.attr("data-poll-results").takeIf { it.isNotBlank() }
    val publicPoll = element.attr("data-poll-public").takeIf { it.isNotBlank() }?.discourseBoolean()
    val chartType = element.attr("data-poll-charttype").takeIf { it.isNotBlank() }
    val voters = element.selectFirst(".poll-info_counts-count .info-number")?.text()
        ?.trim()
        ?.toIntOrNull()
    val options = element.select(".poll-container li[data-poll-option-id]")
        .mapNotNull { option ->
            val labelElement = option.selectFirst(".poll-option-label")
            val label = labelElement?.text()
                ?: option.ownText().takeIf { it.isNotBlank() }
                ?: option.text()
            val labelHtml = labelElement?.html()?.takeIfHtmlNotBlank()
                ?: option.clone().apply {
                    select(".votes, .poll-option-votes, .info-number, .percentage, .percent, .poll-option-percent").remove()
                }.html().takeIfHtmlNotBlank()
            label.trim().takeIf { it.isNotEmpty() }?.let { text ->
                CookedPostBlock.PollOption(
                    id = option.attr("data-poll-option-id").takeIf { it.isNotBlank() },
                    label = text,
                    votes = option.selectFirst(".votes, .poll-option-votes, .info-number")?.text()?.pollNumber(),
                    percent = option.selectFirst(".percentage, .percent, .poll-option-percent")?.text()?.pollPercent(),
                    selected = option.hasClass("chosen") || option.hasClass("selected") || option.selectFirst(".chosen, .selected") != null,
                    labelHtml = labelHtml,
                )
            }
        }
    return CookedPostBlock.Poll(
        name = name,
        title = title,
        type = type,
        status = status,
        voters = voters,
        options = options,
        results = results,
        publicPoll = publicPoll,
        chartType = chartType,
    )
}

private fun String.pollNumber(): Int? = filter { it.isDigit() }.toIntOrNull()

private fun String.discourseBoolean(): Boolean? {
    return when (trim().lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }
}

private fun String.pollPercent(): Float? {
    val value = replace('%', ' ').trim().toFloatOrNull() ?: return null
    return (value / 100f).coerceIn(0f, 1f)
}

private fun parseTableElement(element: Element, quoteLevel: Int, baseUrl: String): List<CookedPostBlock> {
    val table = if (element.normalName() == "table") element else element.selectFirst("table") ?: return emptyList()
    val headerRow = table.selectFirst("thead tr")
    val headers = headerRow
        ?.children()
        ?.filter { it.normalName() == "th" || it.normalName() == "td" }
        ?.map { cell -> parseTableCellElement(cell, quoteLevel, baseUrl) }
        .orEmpty()
    val bodyRows = table.select("tbody tr")
        .ifEmpty { table.children().filter { it.normalName() == "tr" } }
        .filter { row -> row != headerRow }
    val rows = bodyRows.map { row ->
        row.children()
            .filter { it.normalName() == "th" || it.normalName() == "td" }
            .map { cell -> parseTableCellElement(cell, quoteLevel, baseUrl) }
    }.filter { it.isNotEmpty() }
    if (headers.isEmpty() && rows.isEmpty()) return emptyList()
    return listOf(CookedPostBlock.Table(headers = headers, rows = rows))
}

private fun parseTableCellElement(element: Element, quoteLevel: Int, baseUrl: String): CookedPostBlock.TableCell {
    return CookedPostBlock.TableCell(
        text = element.text().trim(),
        html = element.html(),
        blocks = coalesceImagesIntoCollage(parseCookedNodes(element.childNodes(), quoteLevel, baseUrl)),
        colspan = element.attr("colspan").toIntOrNull()?.coerceAtLeast(1) ?: 1,
        rowspan = element.attr("rowspan").toIntOrNull()?.coerceAtLeast(1) ?: 1,
        align = when {
            element.hasClass("align-center") || element.attr("align").equals("center", ignoreCase = true) -> CookedPostBlock.TableCellAlign.Center
            element.hasClass("align-right") || element.attr("align").equals("right", ignoreCase = true) -> CookedPostBlock.TableCellAlign.End
            else -> CookedPostBlock.TableCellAlign.Start
        },
    )
}

private fun parseListElement(element: Element, quoteLevel: Int, baseUrl: String): List<CookedPostBlock> {
    val items = element.children()
        .filter { child -> child.normalName() == "li" }
        .mapNotNull { item ->
            // Discourse's bundled checklist plugin cooks the marker into an empty
            // `span.chcklst-box` (`checked`/`permanent` are its state classes) and leaves the item
            // itself unclassed - the web UI's `li.has-checkbox` is added client-side. The span is
            // chrome, not content: it leaves the item before the item is parsed, and its state
            // travels as the item's `checked`. Only a *leading* box is the item's marker; a box
            // later in the item is content and stays for the inline cooker (the site's own
            // client-side rule removes the bullet for a leading box only).
            val checkbox = item.leadingChecklistBox()
            val checked = checkbox?.let { box -> box.hasClass("checked") || box.hasClass("permanent") }
            checkbox?.remove()
            val html = item.html()
            if (html.takeIfHtmlNotBlank() == null && checked == null) return@mapNotNull null
            CookedPostBlock.ListItem(
                html = html,
                blocks = coalesceImagesIntoCollage(parseCookedNodes(item.childNodes(), quoteLevel, baseUrl)),
                checked = checked,
            )
        }
    return if (items.isEmpty()) emptyList() else listOf(CookedPostBlock.ListItems(ordered = element.normalName() == "ol", items = items))
}

/**
 * The checklist box a cooked item leads with, or null. The site's own client-side rule
 * (`li.has-checkbox`, which is what removes the bullet) looks at a box that is the first content of
 * the item or of its first paragraph, and that is the position where the box is the item's marker
 * rather than content.
 */
private fun Element.leadingChecklistBox(): Element? {
    val first = firstContentNode()
    if (first !is Element) return null
    if (first.isChecklistBox()) return first
    if (first.normalName() != "p") return null
    val inner = first.firstContentNode()
    return (inner as? Element)?.takeIf { it.isChecklistBox() }
}

/** The first child node that is not blank text, i.e. where the element's content starts. */
private fun Element.firstContentNode(): Node? =
    childNodes().firstOrNull { node -> node !is TextNode || node.text().isNotBlank() }

private fun Element.isChecklistBox(): Boolean = normalName() == "span" && hasClass("chcklst-box")

private fun parseDetailsElement(element: Element, quoteLevel: Int, baseUrl: String): List<CookedPostBlock> {
    val summaryElement = element.selectFirst("summary")
    val summary = summaryElement?.text()?.takeIf { it.isNotBlank() }
    val summaryHtml = summaryElement?.html()?.takeIfHtmlNotBlank()
    val content = element.clone().apply {
        select("summary").remove()
        removeCookedNonContentArtifacts()
    }
    val blocks = parseCookedNodes(content.childNodes(), quoteLevel, baseUrl)
    val html = content.html().takeIfHtmlNotBlank()
        ?: blocks.joinToString(separator = "\n") { block ->
            when (block) {
                is CookedPostBlock.Text -> block.html
                is CookedPostBlock.Heading -> block.html
                is CookedPostBlock.Code -> "<pre><code>${block.code}</code></pre>"
                else -> ""
            }
        }.takeIfHtmlNotBlank()
        ?: return emptyList()
    return listOf(
        CookedPostBlock.Details(
            html = html,
            summary = summary,
            summaryHtml = summaryHtml,
            children = coalesceImagesIntoCollage(blocks),
            open = element.hasAttr("open"),
        ),
    )
}

private fun parseDivElement(element: Element, quoteLevel: Int, baseUrl: String): List<CookedPostBlock> {
    if (element.isCookedNonContentArtifact()) {
        return emptyList()
    }
    if (element.hasClass("lightbox-wrapper")) {
        return imageBlocksFrom(element)
    }
    if (element.hasClass("math")) {
        // The site's math plugin cooks `$$x^2$$` into `<div class="math">\nx^2\n</div>` - bare TeX,
        // the delimiters stripped - and that is a display formula, not a paragraph of text.
        val source = element.text().trim()
        if (source.isNotEmpty()) return listOf(CookedPostBlock.Math(source))
    }
    if (element.isCalloutContainer()) {
        val callout = parseCalloutElement(element, quoteLevel, baseUrl)
        if (callout.isNotEmpty()) return callout
    }
    if (element.hasClass("poll")) {
        return listOf(parsePollElement(element))
    }
    if (element.hasClass("spoiler") || element.hasClass("spoiled")) {
        return parseInlineHtmlFragment(element.outerHtml())
    }
    if (element.hasClass("md-table") || element.selectFirst(":root > table") != null) {
        val tableBlocks = parseTableElement(element, quoteLevel, baseUrl)
        if (tableBlocks.isNotEmpty()) return tableBlocks
    }
    return if (element.childNodes().isNotEmpty()) {
        parseCookedNodes(element.childNodes(), quoteLevel, baseUrl)
    } else {
        element.html().takeIfHtmlNotBlank()?.let { listOf(CookedPostBlock.Text(it)) }.orEmpty()
    }
}

private fun parseFigureElement(element: Element, quoteLevel: Int, baseUrl: String): List<CookedPostBlock> {
    val images = imageBlocksFrom(element)
    val captionHtml = element.selectFirst("figcaption")?.html()?.takeIfHtmlNotBlank()
    if (images.size == 1 && captionHtml != null) {
        return listOf(images.single().copy(captionHtml = captionHtml))
    }
    val textHtml = element.clone().apply {
        removeCookedNonContentArtifacts()
        select("img:not(.emoji):not(.site-icon), .lightbox-wrapper").remove()
    }.html().takeIfHtmlNotBlank()
    return listOfNotNull(textHtml?.let(CookedPostBlock::Text)) + images
}

private fun imageBlocksFrom(element: Element): List<CookedPostBlock.Image> {
    return element.select("img[src]")
        .filterNot { image -> image.hasClass("emoji") || image.hasClass("avatar") || image.hasClass("site-icon") }
        .filterNot { image -> image.parents().any { parent -> parent.isCookedNonContentArtifact() } }
        .map { image ->
            CookedPostBlock.Image(
                src = image.attr("src"),
                alt = image.attr("alt").ifBlank { image.attr("title") },
                href = image.parents().firstOrNull { parent -> parent.normalName() == "a" && parent.hasAttr("href") }?.attr("href"),
                widthPx = image.attr("width").toIntOrNull() ?: 0,
                heightPx = image.attr("height").toIntOrNull() ?: 0,
                srcset = image.attr("srcset").takeIf { it.isNotBlank() },
            )
        }
}

private fun Element.removeCookedNonContentArtifacts() {
    select(
        listOf(
            ".codeblock-button-wrapper",
            "button.copy-cmd",
            "button.fullscreen-cmd",
            ".copy-cmd",
            ".fullscreen-cmd",
            ".callout-fold",
            ".callout-fold-icon",
            ".lightbox-wrapper .meta",
            ".lightbox .meta",
            ".lightbox-wrapper .filename",
            ".lightbox .filename",
            ".lightbox-wrapper .informations",
            ".lightbox .informations",
        ).joinToString(separator = ", "),
    ).remove()
}

private fun Element.isCookedNonContentArtifact(): Boolean {
    if (hasClass("codeblock-button-wrapper") ||
        hasClass("copy-cmd") ||
        hasClass("fullscreen-cmd") ||
        hasClass("callout-fold") ||
        hasClass("callout-fold-icon")
    ) {
        return true
    }
    if (normalName() == "button" && (hasClass("copy-cmd") || hasClass("fullscreen-cmd"))) {
        return true
    }
    if ((hasClass("meta") || hasClass("filename") || hasClass("informations")) &&
        parents().any { parent -> parent.hasClass("lightbox") || parent.hasClass("lightbox-wrapper") }
    ) {
        return true
    }
    return false
}

private fun String?.takeIfHtmlNotBlank(): String? = this
    ?.takeIf { Jsoup.parseBodyFragment(it).body().text().isNotBlank() || Jsoup.parseBodyFragment(it).select("img[src]").isNotEmpty() }

private fun String.plainTextFromHtml(): String = Jsoup.parseBodyFragment(this).body().text().trim()

private fun String.looksLikeImageCaptionHtml(): Boolean = trimStart().startsWith("<br", ignoreCase = true)

internal fun resolvePostImageUrl(baseUrl: String, rawUrl: String): String =
    baseUrl.toHttpUrl().resolve(rawUrl)?.toString() ?: rawUrl

/**
 * The widest candidate in an `srcset`, which is the file a viewer should open: the site draws the
 * `src` (a `…_2_690x326.jpeg`-style resize - 49 KB in the sample read) and offers the upload itself in
 * `srcset` (`…/original/4X/<hash>.jpeg` - 96 KB and the full pixel size). Opening the `src` is what a
 * device pass saw as "still blurry after opening", because no URL was ever sharper than the drawn one.
 * Returns null when there is nothing to widen with, so callers can fall back to an anchor's href or the
 * `src` itself.
 */
internal fun widestImageCandidate(srcset: String?): String? {
    val candidates = srcset
        ?.split(',')
        ?.mapNotNull { candidate -> candidate.trim().substringBefore(' ').takeIf { it.isNotEmpty() } }
        .orEmpty()
    if (candidates.isEmpty()) {
        return null
    }
    return candidates.firstOrNull { it.contains("/original/") } ?: candidates.last()
}

/**
 * The file to open in the viewer for an image the body drew.
 *
 * The widest `srcset` candidate first: it is the same upload un-resized (`…/original/4X/<hash>.jpeg`
 * beside the `…/optimized/…_2_690x326.jpeg` the site drew - 96 KB against 49 KB in the sample read), so
 * it is the sharp file the owner's "still blurry after opening" was missing. Then an anchor that stays
 * on the file's own host - Discourse wraps its own uploads in a lightbox link - and never an off-site
 * one, because a post may link its picture to somewhere else entirely (a cell-level test pins that).
 * `src` is the last resort: it is the resize the site chose to draw.
 */
internal fun resolvePostImageViewerUrl(baseUrl: String, image: CookedPostBlock.Image): String {
    val srcResolved = resolvePostImageUrl(baseUrl, image.src)
    val srcsetResolved = widestImageCandidate(image.srcset)?.let { resolvePostImageUrl(baseUrl, it) }
    if (srcsetResolved != null && isLikelyPostImageUrl(srcsetResolved)) {
        return srcsetResolved
    }
    val hrefResolved = image.href?.let { resolvePostImageUrl(baseUrl, it) }
    val sameHostAsTheDrawnFile = hrefResolved != null &&
        baseUrl.toHttpUrl().host == hrefResolved.toHttpUrl().host &&
        hrefResolved.toHttpUrl().host == srcResolved.toHttpUrl().host
    return hrefResolved?.takeIf { isLikelyPostImageUrl(it) && sameHostAsTheDrawnFile } ?: srcResolved
}

internal fun isLikelyPostImageUrl(url: String): Boolean {
    val path = url.substringBefore('#').substringBefore('?').lowercase()
    return path.endsWith(".jpg") ||
        path.endsWith(".jpeg") ||
        path.endsWith(".png") ||
        path.endsWith(".webp") ||
        path.endsWith(".gif") ||
        path.endsWith(".svg") ||
        path.endsWith(".avif")
}

/**
 * Finds a poll by name anywhere in a parsed body: a poll can sit at the top level or inside a
 * details/quote/callout container.
 */
internal fun findCookedPoll(blocks: List<CookedPostBlock>, pollName: String): CookedPostBlock.Poll? =
    blocks.firstNotNullOfOrNull { block -> block.findCookedPoll(pollName) }

private fun CookedPostBlock.findCookedPoll(pollName: String): CookedPostBlock.Poll? = when (this) {
    is CookedPostBlock.Poll -> takeIf { name == pollName }
    is CookedPostBlock.Details -> findCookedPoll(children, pollName)
    is CookedPostBlock.Callout -> findCookedPoll(children, pollName)
    is CookedPostBlock.Quote -> findCookedPoll(children, pollName)
    else -> null
}
