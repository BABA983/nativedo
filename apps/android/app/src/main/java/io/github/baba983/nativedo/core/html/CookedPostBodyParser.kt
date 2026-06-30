package io.github.baba983.nativedo.core.html

import io.github.baba983.nativedo.core.ui.resolveDiscourseCalloutSpec
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node

internal fun parseCookedPostBody(html: String, baseUrl: String): List<CookedPostBlock> {
    val body = Jsoup.parseBodyFragment(html).body()
    val raw = parseCookedNodes(body.childNodes())
    val blocks = coalesceImagesIntoCollage(raw)
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
        val hrefResolved = image.href?.let { resolvePostImageUrl(baseUrl, it) }
        val fullUrl = hrefResolved?.takeIf(::isLikelyPostImageUrl) ?: srcResolved
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

private fun parseCookedNodes(nodes: List<Node>, quoteLevel: Int = 0): List<CookedPostBlock> {
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
                blocks += parseCookedElement(element, quoteLevel)
            }
        } else {
            inline.append(node.outerHtml())
        }
    }
    flushInline()
    return blocks
}

private fun parseCookedElement(element: Element, quoteLevel: Int): List<CookedPostBlock> {
    return when (element.normalName()) {
        "p" -> parseParagraphElement(element)
        "blockquote" -> parseBlockquoteElement(element, quoteLevel)
        "aside" -> parseAsideElement(element, quoteLevel)
        "pre" -> listOf(CookedPostBlock.Code((element.selectFirst("code")?.wholeText() ?: element.wholeText()).trimEnd()))
        "ul", "ol" -> parseListElement(element, quoteLevel)
        "details" -> parseDetailsElement(element, quoteLevel)
        "h1" -> listOf(CookedPostBlock.Heading(level = 1, html = element.html()))
        "h2" -> listOf(CookedPostBlock.Heading(level = 2, html = element.html()))
        "h3" -> listOf(CookedPostBlock.Heading(level = 3, html = element.html()))
        "h4" -> listOf(CookedPostBlock.Heading(level = 4, html = element.html()))
        "h5" -> listOf(CookedPostBlock.Heading(level = 5, html = element.html()))
        "h6" -> listOf(CookedPostBlock.Heading(level = 6, html = element.html()))
        "hr" -> listOf(CookedPostBlock.Rule)
        "table" -> parseTableElement(element, quoteLevel)
        "div" -> parseDivElement(element, quoteLevel)
        "figure" -> parseFigureElement(element, quoteLevel)
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

private fun parseParagraphElement(element: Element): List<CookedPostBlock> {
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

private fun parseBlockquoteElement(element: Element, quoteLevel: Int): List<CookedPostBlock> {
    if (element.hasClass("callout")) {
        return parseCalloutElement(element, quoteLevel)
    }
    val markdownCallout = parseMarkdownCalloutElement(element, quoteLevel)
    if (markdownCallout != null) {
        return listOf(markdownCallout)
    }
    val html = element.html().takeIfHtmlNotBlank() ?: return emptyList()
    return listOf(
        CookedPostBlock.Quote(
            title = null,
            html = html,
            children = coalesceImagesIntoCollage(parseCookedNodes(element.childNodes(), quoteLevel + 1)),
            level = quoteLevel,
        ),
    )
}

private data class MarkdownCalloutHeader(
    val type: String,
    val titleHtml: String?,
)

private fun parseMarkdownCalloutElement(element: Element, quoteLevel: Int): CookedPostBlock.Callout? {
    val content = element.clone().apply {
        removeCookedNonContentArtifacts()
    }
    val firstParagraph = content.selectFirst("p") ?: return null
    val header = firstParagraph.extractMarkdownCalloutHeader() ?: return null
    val spec = resolveDiscourseCalloutSpec(header.type)
    val children = coalesceImagesIntoCollage(parseCookedNodes(content.childNodes(), quoteLevel))
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
    )
}

private fun Element.extractMarkdownCalloutHeader(): MarkdownCalloutHeader? {
    val html = html()
    val match = Regex("^\\s*\\[!([A-Za-z][A-Za-z0-9_-]*)](?:\\s*([^<\\n\\r]+))?\\s*(?:<br\\s*/?>)?", RegexOption.IGNORE_CASE)
        .find(html)
        ?: return null
    val type = match.groupValues.getOrNull(1).orEmpty().lowercase()
    if (type.isBlank()) {
        return null
    }
    val title = match.groupValues.getOrNull(2)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    val remainingHtml = html.removeRange(match.range).takeIfHtmlNotBlank()
    if (remainingHtml != null) {
        html(remainingHtml)
    } else {
        remove()
    }
    return MarkdownCalloutHeader(
        type = type,
        titleHtml = title,
    )
}

private fun parseCalloutElement(element: Element, quoteLevel: Int): List<CookedPostBlock> {
    val titleElement = element.children().firstOrNull { child -> child.hasClass("callout-title") }
        ?: element.selectFirst(".callout-title")
    val titleInner = titleElement?.selectFirst(".callout-title-inner")
    val titleHtml = titleInner?.html()?.takeIfHtmlNotBlank()
        ?: titleElement?.clone()?.apply {
            select(".callout-icon, .callout-fold").remove()
        }?.html()?.takeIfHtmlNotBlank()
    val rawIcon = titleElement?.selectFirst(".callout-icon svg use[href], .callout-icon svg use[xlink:href]")
        ?.attr("href")
        ?.ifBlank { titleElement.selectFirst(".callout-icon svg use[xlink:href]")?.attr("xlink:href").orEmpty() }
        .orEmpty()
    val rawType = element.attr("data-callout-type").ifBlank {
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
    val children = coalesceImagesIntoCollage(parseCookedNodes(content.childNodes(), quoteLevel))
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
        ),
    )
}

private fun parseAsideElement(element: Element, quoteLevel: Int): List<CookedPostBlock> {
    if (element.hasClass("quote")) {
        val titleElement = element.selectFirst(".title")
        val title = titleElement?.text()?.takeIf { it.isNotBlank() }
        val titleHtml = titleElement?.html()?.takeIfHtmlNotBlank()
        val quoteElement = element.selectFirst("blockquote")
        val quoteHtml = quoteElement?.html()?.takeIfHtmlNotBlank() ?: element.html()
        return listOf(
            CookedPostBlock.Quote(
                title = title,
                titleHtml = titleHtml,
                html = quoteHtml,
                children = quoteElement
                    ?.let { coalesceImagesIntoCollage(parseCookedNodes(it.childNodes(), quoteLevel + 1)) }
                    .orEmpty(),
                sourceTopicId = element.attr("data-topic").toLongOrNull(),
                sourcePostNumber = element.attr("data-post").toIntOrNull(),
                level = quoteLevel,
            ),
        )
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
    val type = element.attr("data-poll-type").takeIf { it.isNotBlank() }
    val status = element.attr("data-poll-status").takeIf { it.isNotBlank() }
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
        type = type,
        status = status,
        voters = voters,
        options = options,
    )
}

private fun String.pollNumber(): Int? = filter { it.isDigit() }.toIntOrNull()

private fun String.pollPercent(): Float? {
    val value = replace('%', ' ').trim().toFloatOrNull() ?: return null
    return (value / 100f).coerceIn(0f, 1f)
}

private fun parseTableElement(element: Element, quoteLevel: Int): List<CookedPostBlock> {
    val table = if (element.normalName() == "table") element else element.selectFirst("table") ?: return emptyList()
    val headerRow = table.selectFirst("thead tr")
    val headers = headerRow
        ?.children()
        ?.filter { it.normalName() == "th" || it.normalName() == "td" }
        ?.map { cell -> parseTableCellElement(cell, quoteLevel) }
        .orEmpty()
    val bodyRows = table.select("tbody tr")
        .ifEmpty { table.children().filter { it.normalName() == "tr" } }
        .filter { row -> row != headerRow }
    val rows = bodyRows.map { row ->
        row.children()
            .filter { it.normalName() == "th" || it.normalName() == "td" }
            .map { cell -> parseTableCellElement(cell, quoteLevel) }
    }.filter { it.isNotEmpty() }
    if (headers.isEmpty() && rows.isEmpty()) return emptyList()
    return listOf(CookedPostBlock.Table(headers = headers, rows = rows))
}

private fun parseTableCellElement(element: Element, quoteLevel: Int): CookedPostBlock.TableCell {
    return CookedPostBlock.TableCell(
        text = element.text().trim(),
        html = element.html(),
        blocks = coalesceImagesIntoCollage(parseCookedNodes(element.childNodes(), quoteLevel)),
        colspan = element.attr("colspan").toIntOrNull()?.coerceAtLeast(1) ?: 1,
        rowspan = element.attr("rowspan").toIntOrNull()?.coerceAtLeast(1) ?: 1,
        align = when {
            element.hasClass("align-center") || element.attr("align").equals("center", ignoreCase = true) -> CookedPostBlock.TableCellAlign.Center
            element.hasClass("align-right") || element.attr("align").equals("right", ignoreCase = true) -> CookedPostBlock.TableCellAlign.End
            else -> CookedPostBlock.TableCellAlign.Start
        },
    )
}

private fun parseListElement(element: Element, quoteLevel: Int): List<CookedPostBlock> {
    val items = element.children()
        .filter { child -> child.normalName() == "li" }
        .mapNotNull { item ->
            val html = item.html().takeIfHtmlNotBlank() ?: return@mapNotNull null
            CookedPostBlock.ListItem(
                html = html,
                blocks = coalesceImagesIntoCollage(parseCookedNodes(item.childNodes(), quoteLevel)),
            )
        }
    return if (items.isEmpty()) emptyList() else listOf(CookedPostBlock.ListItems(ordered = element.normalName() == "ol", items = items))
}

private fun parseDetailsElement(element: Element, quoteLevel: Int): List<CookedPostBlock> {
    val summaryElement = element.selectFirst("summary")
    val summary = summaryElement?.text()?.takeIf { it.isNotBlank() }
    val summaryHtml = summaryElement?.html()?.takeIfHtmlNotBlank()
    val content = element.clone().apply {
        select("summary").remove()
        removeCookedNonContentArtifacts()
    }
    val blocks = parseCookedNodes(content.childNodes(), quoteLevel)
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

private fun parseDivElement(element: Element, quoteLevel: Int): List<CookedPostBlock> {
    if (element.isCookedNonContentArtifact()) {
        return emptyList()
    }
    if (element.hasClass("lightbox-wrapper")) {
        return imageBlocksFrom(element)
    }
    if (element.hasClass("poll")) {
        return listOf(parsePollElement(element))
    }
    if (element.hasClass("spoiler") || element.hasClass("spoiled")) {
        return parseInlineHtmlFragment(element.outerHtml())
    }
    if (element.hasClass("md-table") || element.selectFirst(":root > table") != null) {
        val tableBlocks = parseTableElement(element, quoteLevel)
        if (tableBlocks.isNotEmpty()) return tableBlocks
    }
    return if (element.childNodes().isNotEmpty()) {
        parseCookedNodes(element.childNodes(), quoteLevel)
    } else {
        element.html().takeIfHtmlNotBlank()?.let { listOf(CookedPostBlock.Text(it)) }.orEmpty()
    }
}

private fun parseFigureElement(element: Element, quoteLevel: Int): List<CookedPostBlock> {
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
        hasClass("callout-fold")
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
