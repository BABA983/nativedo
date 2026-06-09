package io.github.baba983.nativedo.core.html

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

internal fun parseCookedPostBody(html: String, baseUrl: String): List<CookedPostBlock> {
    val body = Jsoup.parseBodyFragment(html).body()
    val raw = body.children().flatMap { element -> parseCookedElement(element) }
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
    if (blocks.size < 2) return blocks
    val out = mutableListOf<CookedPostBlock>()
    var i = 0
    while (i < blocks.size) {
        val block = blocks[i]
        if (block is CookedPostBlock.Image) {
            var j = i + 1
            val group = mutableListOf(block)
            while (j < blocks.size && blocks[j] is CookedPostBlock.Image) {
                group.add(blocks[j] as CookedPostBlock.Image)
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

internal fun cookedGalleryImages(blocks: List<CookedPostBlock>, baseUrl: String): List<GalleryImage> {
    val seen = linkedMapOf<String, GalleryImage>()
    blocks.flatMap { block ->
        when (block) {
            is CookedPostBlock.Image -> listOf(block)
            is CookedPostBlock.Collage -> block.images
            else -> emptyList()
        }
    }.forEach { image ->
        val srcResolved = resolvePostImageUrl(baseUrl, image.src)
        val fullUrl = image.href?.let { resolvePostImageUrl(baseUrl, it) } ?: srcResolved
        val thumbUrl = if (srcResolved != fullUrl) srcResolved else null
        if (!seen.containsKey(fullUrl)) {
            seen[fullUrl] = GalleryImage(
                url = fullUrl,
                thumbUrl = thumbUrl,
                widthPx = image.widthPx,
                heightPx = image.heightPx,
                caption = image.alt.trim().takeIf { it.isNotEmpty() },
            )
        }
    }
    return seen.values.toList()
}

private fun parseCookedElement(element: Element): List<CookedPostBlock> {
    return when (element.normalName()) {
        "p" -> parseParagraphElement(element)
        "blockquote" -> element.html()
            .takeIfHtmlNotBlank()
            ?.let { listOf(CookedPostBlock.Quote(title = null, html = it)) }
            .orEmpty()
        "aside" -> parseAsideElement(element)
        "pre" -> listOf(CookedPostBlock.Code((element.selectFirst("code")?.wholeText() ?: element.wholeText()).trimEnd()))
        "ul", "ol" -> parseListElement(element)
        "details" -> parseDetailsElement(element)
        "h1" -> listOf(CookedPostBlock.Heading(level = 1, html = element.html()))
        "h2" -> listOf(CookedPostBlock.Heading(level = 2, html = element.html()))
        "h3" -> listOf(CookedPostBlock.Heading(level = 3, html = element.html()))
        "h4" -> listOf(CookedPostBlock.Heading(level = 4, html = element.html()))
        "h5" -> listOf(CookedPostBlock.Heading(level = 5, html = element.html()))
        "h6" -> listOf(CookedPostBlock.Heading(level = 6, html = element.html()))
        "hr" -> listOf(CookedPostBlock.Rule)
        "table" -> parseTableElement(element)
        "div" -> parseDivElement(element)
        else -> element.html().takeIf { it.isNotBlank() }?.let { listOf(CookedPostBlock.Text(it)) }.orEmpty()
    }
}

private fun parseParagraphElement(element: Element): List<CookedPostBlock> {
    val images = imageBlocksFrom(element)
    val textHtml = element.clone().apply { select("img:not(.emoji):not(.site-icon), .lightbox-wrapper").remove() }.html().takeIfHtmlNotBlank()
    return listOfNotNull(textHtml?.let(CookedPostBlock::Text)) + images
}

private fun parseAsideElement(element: Element): List<CookedPostBlock> {
    if (element.hasClass("quote")) {
        val title = element.selectFirst(".title")?.text()?.takeIf { it.isNotBlank() }
        val quoteHtml = element.selectFirst("blockquote")?.html()?.takeIfHtmlNotBlank() ?: element.html()
        return listOf(
            CookedPostBlock.Quote(
                title = title,
                html = quoteHtml,
                sourceTopicId = element.attr("data-topic").toLongOrNull(),
                sourcePostNumber = element.attr("data-post").toIntOrNull(),
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
    val articleLink = article?.selectFirst("h3 a[href]")
    val href = element.attr("data-onebox-src")
        .ifBlank { articleLink?.attr("href").orEmpty() }
        .ifBlank { headerLink?.attr("href").orEmpty() }
        .ifBlank { return null }
    val domain = headerLink?.text()?.takeIf { it.isNotBlank() }
    val siteIconUrl = element.selectFirst("header.source img.site-icon")?.attr("src")?.takeIf { it.isNotBlank() }
    val thumbnailUrl = article?.selectFirst("img.thumbnail")?.attr("src")
        ?: article?.selectFirst("img[src]")?.attr("src")
    val title = (articleLink?.text() ?: article?.selectFirst("h3")?.text())
        ?.takeIf { it.isNotBlank() }
        ?: domain
        ?: href
    val description = article?.select("p")
        ?.joinToString(separator = "\n") { it.text() }
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    return CookedPostBlock.Onebox(
        href = href,
        domain = domain,
        siteIconUrl = siteIconUrl,
        thumbnailUrl = thumbnailUrl,
        title = title,
        description = description,
    )
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
            val label = option.selectFirst(".poll-option-label")?.text()
                ?: option.ownText().takeIf { it.isNotBlank() }
                ?: option.text()
            label.trim().takeIf { it.isNotEmpty() }?.let { text ->
                CookedPostBlock.PollOption(
                    id = option.attr("data-poll-option-id").takeIf { it.isNotBlank() },
                    label = text,
                    votes = option.selectFirst(".votes, .poll-option-votes, .info-number")?.text()?.pollNumber(),
                    percent = option.selectFirst(".percentage, .percent, .poll-option-percent")?.text()?.pollPercent(),
                    selected = option.hasClass("chosen") || option.hasClass("selected") || option.selectFirst(".chosen, .selected") != null,
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

private fun parseTableElement(element: Element): List<CookedPostBlock> {
    val table = if (element.normalName() == "table") element else element.selectFirst("table") ?: return emptyList()
    val headers = table.selectFirst("thead")?.select("tr")?.firstOrNull()
        ?.select("th")
        ?.map { it.text().trim() }
        .orEmpty()
    val rows = table.select("tbody tr").map { row ->
        row.select("th, td").map { it.text().trim() }
    }.filter { it.isNotEmpty() }
    if (headers.isEmpty() && rows.isEmpty()) return emptyList()
    return listOf(CookedPostBlock.Table(headers = headers, rows = rows))
}

private fun parseListElement(element: Element): List<CookedPostBlock> {
    val items = element.children()
        .filter { child -> child.normalName() == "li" }
        .mapNotNull { item -> item.html().takeIfHtmlNotBlank() }
    return if (items.isEmpty()) emptyList() else listOf(CookedPostBlock.ListItems(ordered = element.normalName() == "ol", items = items))
}

private fun parseDetailsElement(element: Element): List<CookedPostBlock> {
    val summary = element.selectFirst("summary")?.text()?.takeIf { it.isNotBlank() }
    val content = element.clone().apply { select("summary").remove() }
    val blocks = content.children().flatMap { child -> parseCookedElement(child) }
    return if (summary == null) blocks else listOf(CookedPostBlock.Text("<p><strong>$summary</strong></p>")) + blocks
}

private fun parseDivElement(element: Element): List<CookedPostBlock> {
    if (element.hasClass("poll")) {
        return listOf(parsePollElement(element))
    }
    if (element.hasClass("spoiler") || element.hasClass("spoiled")) {
        val inner = element.html().takeIfHtmlNotBlank() ?: return emptyList()
        return listOf(CookedPostBlock.Spoiler(html = inner))
    }
    if (element.hasClass("md-table") || element.selectFirst(":root > table") != null) {
        val tableBlocks = parseTableElement(element)
        if (tableBlocks.isNotEmpty()) return tableBlocks
    }
    val images = imageBlocksFrom(element)
    if (images.isNotEmpty()) {
        val textHtml = element.clone().apply { select("img:not(.emoji):not(.site-icon), .lightbox-wrapper").remove() }.html().takeIfHtmlNotBlank()
        return listOfNotNull(textHtml?.let(CookedPostBlock::Text)) + images
    }
    return if (element.children().isNotEmpty()) {
        element.children().flatMap { child -> parseCookedElement(child) }
    } else {
        element.html().takeIfHtmlNotBlank()?.let { listOf(CookedPostBlock.Text(it)) }.orEmpty()
    }
}

private fun imageBlocksFrom(element: Element): List<CookedPostBlock.Image> {
    return element.select("img[src]")
        .filterNot { image -> image.hasClass("emoji") || image.hasClass("avatar") || image.hasClass("site-icon") }
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

private fun String?.takeIfHtmlNotBlank(): String? = this
    ?.takeIf { Jsoup.parseBodyFragment(it).body().text().isNotBlank() || Jsoup.parseBodyFragment(it).select("img[src]").isNotEmpty() }

internal fun resolvePostImageUrl(baseUrl: String, rawUrl: String): String =
    baseUrl.toHttpUrl().resolve(rawUrl)?.toString() ?: rawUrl
