package io.github.baba983.nativedo.core.ui

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import kotlin.math.roundToInt

internal data class PreparedInlineContent(
    val html: String,
    val inlineImages: List<InlineImageSpec>,
    val inlineSpoilers: List<InlineSpoilerSpec>,
    val inlineTextStyles: List<InlineTextStyleSpec>,
    val inlineIcons: List<InlineIconSpec>,
    val inlineHashtagChips: List<InlineHashtagChipSpec>,
)

internal data class InlineSpoilerSpec(
    val startToken: String,
    val endToken: String,
)

internal data class InlineTextStyleSpec(
    val startToken: String,
    val endToken: String,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val relativeSize: Float? = null,
)

internal data class InlineIconSpec(
    val token: String,
    val symbolId: String,
    val rawTintColor: String,
    val contentDescription: String,
)

internal data class InlineHashtagChipSpec(
    val token: String,
    val href: String,
    val label: String,
    val symbolId: String,
    val rawIconColor: String,
)

internal data class InlineImageSpec(
    val token: String,
    val rawUrl: String,
    val contentDescription: String,
    val kind: InlineImageKind,
) {
    fun displayWidthPx(textSizePx: Float): Int = when (kind) {
        InlineImageKind.Emoji -> (textSizePx * 1.2f).roundToInt()
        InlineImageKind.SiteIcon -> textSizePx.roundToInt()
        InlineImageKind.Icon -> textSizePx.roundToInt()
    }

    fun displayHeightPx(textSizePx: Float): Int = displayWidthPx(textSizePx)
}

internal enum class InlineImageKind {
    Emoji,
    SiteIcon,
    Icon,
}

internal fun resolveAvatarUrl(
    baseUrl: String,
    avatarTemplate: String,
    animatedAvatarUrl: String = "",
    size: Int,
): String {
    val rawAvatarUrl = animatedAvatarUrl.ifBlank { avatarTemplate }
    if (rawAvatarUrl.isBlank()) {
        return ""
    }
    val expandedTemplate = rawAvatarUrl.replace("{size}", size.toString())
    return resolveDiscourseUrl(baseUrl, expandedTemplate)
}

internal fun avatarImageFilter(
    animatedAvatarUrl: String,
    size: Int,
): String {
    return "${size}_${size}"
}

internal fun resolveDiscourseUrl(
    baseUrl: String,
    rawUrl: String,
): String {
    val trimmedUrl = rawUrl.trim()
    if (trimmedUrl.isBlank()) {
        return ""
    }
    val baseHttpUrl = baseUrl.toHttpUrl()
    val normalizedUrl = if (trimmedUrl.startsWith("//")) {
        "${baseHttpUrl.scheme}:$trimmedUrl"
    } else {
        trimmedUrl
    }
    return baseHttpUrl.resolve(normalizedUrl)?.toString() ?: normalizedUrl
}

internal fun looksLikeDiscourseImageUrl(rawUrl: String): Boolean {
    val trimmedUrl = rawUrl.trim()
    return trimmedUrl.startsWith("/") ||
        trimmedUrl.startsWith("http://") ||
        trimmedUrl.startsWith("https://") ||
        trimmedUrl.startsWith("//")
}

internal fun isAnimatedDiscourseImageUrl(rawUrl: String): Boolean {
    val normalizedUrl = rawUrl
        .trim()
        .substringBefore('?')
        .substringBefore('#')
        .lowercase()
    return normalizedUrl.endsWith(".gif") ||
        normalizedUrl.endsWith(".svg") ||
        normalizedUrl.endsWith(".svgz")
}

internal fun normalizeInlineHtml(html: String): String {
    return prepareInlineContent(html).let { prepared ->
        var normalizedHtml = prepared.html
        prepared.inlineImages.forEach { image ->
            normalizedHtml = normalizedHtml.replace(
                image.token,
                image.contentDescription,
            )
        }
        prepared.inlineSpoilers.forEach { spoiler ->
            normalizedHtml = normalizedHtml
                .replace(spoiler.startToken, "")
                .replace(spoiler.endToken, "")
        }
        prepared.inlineTextStyles.forEach { style ->
            normalizedHtml = normalizedHtml
                .replace(style.startToken, "")
                .replace(style.endToken, "")
        }
        prepared.inlineIcons.forEach { icon ->
            normalizedHtml = normalizedHtml.replace(icon.token, icon.contentDescription)
        }
        prepared.inlineHashtagChips.forEach { chip ->
            normalizedHtml = normalizedHtml.replace(chip.token, chip.label)
        }
        normalizedHtml
    }
}

internal fun replaceEmojiImagesWithText(html: String): String {
    return normalizeInlineHtml(html)
}

internal fun String.htmlToPreviewText(): String {
    return Jsoup.parseBodyFragment(normalizeInlineHtml(this))
        .body()
        .text()
        .replace(Regex("\\s+"), " ")
        .trim()
}

internal fun prepareInlineContent(html: String): PreparedInlineContent {
    val body = Jsoup.parseBodyFragment(html).body()
    val inlineImages = mutableListOf<InlineImageSpec>()
    val inlineSpoilers = mutableListOf<InlineSpoilerSpec>()
    val inlineTextStyles = mutableListOf<InlineTextStyleSpec>()
    val inlineIcons = mutableListOf<InlineIconSpec>()
    val inlineHashtagChips = mutableListOf<InlineHashtagChipSpec>()

    body.select(".spoiler, .spoiled").forEach { spoiler ->
        if (spoiler.parents().any { parent -> parent.hasClass("spoiler") || parent.hasClass("spoiled") }) {
            return@forEach
        }
        val startToken = "__nd_spoiler_start_${inlineSpoilers.size}__"
        val endToken = "__nd_spoiler_end_${inlineSpoilers.size}__"
        inlineSpoilers += InlineSpoilerSpec(
            startToken = startToken,
            endToken = endToken,
        )
        spoiler.before(TextNode(startToken))
        spoiler.after(TextNode(endToken))
    }

    body.select(".bbcode-u, [style*=font-size], [style*=text-decoration]").forEach { element ->
        val inlineTextStyle = element.inlineTextStyleSpec(inlineTextStyles.size) ?: return@forEach
        inlineTextStyles += inlineTextStyle
        element.before(TextNode(inlineTextStyle.startToken))
        element.after(TextNode(inlineTextStyle.endToken))
    }

    body.select("img").forEach { image ->
        val replacement = image.fallbackText()
        val kind = image.inlineImageKind()
        if (kind == null) {
            image.replaceWith(TextNode(replacement))
            return@forEach
        }

        val src = image.attr("src").ifBlank {
            image.replaceWith(TextNode(replacement))
            return@forEach
        }
        val token = "__nd_inline_image_${inlineImages.size}__"
        inlineImages += InlineImageSpec(
            token = token,
            rawUrl = src,
            contentDescription = replacement,
            kind = kind,
        )
        image.replaceWith(TextNode(token))
    }

    body.select("a.hashtag-cooked").forEach { hashtag ->
        val chip = hashtag.inlineHashtagChipSpec(inlineHashtagChips.size) ?: return@forEach
        inlineHashtagChips += chip
        hashtag.replaceWith(TextNode(chip.token))
    }

    body.select("svg").forEach { icon ->
        val symbolId = icon.inlineSvgSymbolId()
        if (symbolId.isNullOrBlank()) {
            if (icon.isDecorativeInlineIcon()) {
                icon.replaceWith(TextNode(icon.iconFallbackText()))
            }
            return@forEach
        }
        val token = "__nd_inline_icon_${inlineIcons.size}__"
        inlineIcons += InlineIconSpec(
            token = token,
            symbolId = symbolId,
            rawTintColor = icon.inlineSvgTintColor(),
            contentDescription = icon.iconFallbackText(),
        )
        icon.replaceWith(TextNode(token))
    }

    body.select("i, svg").forEach { icon ->
        if (icon.isDecorativeInlineIcon()) {
            icon.replaceWith(
                TextNode(
                    icon.iconFallbackText(),
                ),
            )
        }
    }

    return PreparedInlineContent(
        html = body.html(),
        inlineImages = inlineImages,
        inlineSpoilers = inlineSpoilers,
        inlineTextStyles = inlineTextStyles,
        inlineIcons = inlineIcons,
        inlineHashtagChips = inlineHashtagChips,
    )
}

internal fun Element.inlineHashtagChipSpec(index: Int): InlineHashtagChipSpec? {
    val href = attr("href").trim().takeIf { it.isNotBlank() } ?: return null
    val svg = selectFirst("svg")
    val iconWrapper = selectFirst(".hashtag-tag-icon")
    val symbolId = inlineHashtagSymbolId(svg)
    val label = clone()
        .apply { select(".hashtag-tag-icon, svg").remove() }
        .text()
        .ifBlank { attr("data-slug") }
        .ifBlank { attr("aria-label") }
        .ifBlank { attr("title") }
        .trim()
        .takeIf { it.isNotBlank() }
        ?: return null
    return InlineHashtagChipSpec(
        token = "__nd_hashtag_chip_${index}__",
        href = href,
        label = label,
        symbolId = symbolId,
        rawIconColor = iconWrapper?.attr("style")?.cssColorValue("--color1")
            ?: svg?.inlineSvgTintColor().orEmpty(),
    )
}

private fun Element.inlineHashtagSymbolId(svg: Element?): String {
    val symbolIds = svg?.inlineSvgSymbolIds().orEmpty()
    return symbolIds
        .firstOrNull { symbolId -> !symbolId.isHashtagBackgroundSymbol() }
        ?: normalizeFlairIconName(attr("data-icon")).takeIf { icon -> icon.isNotBlank() && !icon.isHashtagBackgroundSymbol() }
        ?: symbolIds.firstOrNull().orEmpty()
}

private fun String.isHashtagBackgroundSymbol(): Boolean {
    return this == "square" || this == "square-full"
}

internal fun Element.inlineTextStyleSpec(index: Int): InlineTextStyleSpec? {
    val style = attr("style")
    val underline = hasClass("bbcode-u") ||
        style.contains("text-decoration", ignoreCase = true) &&
        style.contains("underline", ignoreCase = true)
    val strike = style.contains("text-decoration", ignoreCase = true) &&
        style.contains("line-through", ignoreCase = true)
    val relativeSize = style.inlineFontSizeRatio()
    if (!underline && !strike && relativeSize == null) {
        return null
    }
    return InlineTextStyleSpec(
        startToken = "__nd_inline_style_start_${index}__",
        endToken = "__nd_inline_style_end_${index}__",
        underline = underline,
        strike = strike,
        relativeSize = relativeSize,
    )
}

private fun String.inlineFontSizeRatio(): Float? {
    val declaration = split(';')
        .firstOrNull { part -> part.substringBefore(':').trim().equals("font-size", ignoreCase = true) }
        ?.substringAfter(':', missingDelimiterValue = "")
        ?.trim()
        ?: return null
    val ratio = when {
        declaration.endsWith("%") -> declaration.removeSuffix("%").trim().toFloatOrNull()?.div(100f)
        declaration.endsWith("rem", ignoreCase = true) -> declaration.dropLast(3).trim().toFloatOrNull()
        declaration.endsWith("em", ignoreCase = true) -> declaration.dropLast(2).trim().toFloatOrNull()
        else -> null
    }
    return ratio?.coerceIn(0.5f, 2.5f)
}

internal fun Element.inlineSvgSymbolId(): String? {
    return inlineSvgSymbolIds().firstOrNull()
}

private fun Element.inlineSvgSymbolIds(): List<String> {
    return select("use[href], use[xlink:href]")
        .mapNotNull { use ->
            use.attr("href")
                .ifBlank { use.attr("xlink:href") }
                .substringAfterLast('#')
                .trim()
                .takeIf { it.isNotBlank() }
        }
}

internal fun Element.inlineSvgTintColor(): String {
    return sequenceOf(this)
        .plus(parents().asSequence())
        .mapNotNull { element -> element.attr("style").cssColorValue("--color1") ?: element.attr("style").cssColorValue("color") }
        .firstOrNull()
        .orEmpty()
}

private fun String.cssColorValue(name: String): String? {
    return split(';')
        .firstOrNull { part -> part.substringBefore(':').trim().equals(name, ignoreCase = true) }
        ?.substringAfter(':', missingDelimiterValue = "")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

internal fun Element.iconFallbackText(): String {
    return attr("aria-label").ifBlank { attr("title") }
}

internal fun Element.isDecorativeInlineIcon(): Boolean {
    return classNames().any { className ->
        className == "svg-icon" ||
            className == "d-icon" ||
            className.startsWith("d-icon-") ||
            className == "fa" ||
            className.startsWith("fa-")
    }
}

internal fun Element.inlineImageKind(): InlineImageKind? {
    return when {
        hasClass("emoji") -> InlineImageKind.Emoji
        hasClass("site-icon") -> InlineImageKind.SiteIcon
        else -> null
    }
}

internal fun Element.fallbackText(): String {
    return when {
        hasClass("emoji") -> attr("title").ifBlank { attr("alt") }
        else -> attr("alt").ifBlank { attr("title") }
    }
}
