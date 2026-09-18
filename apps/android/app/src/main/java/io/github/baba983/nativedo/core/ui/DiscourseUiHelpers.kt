package io.github.baba983.nativedo.core.ui

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element
import kotlin.math.roundToInt

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

internal fun normalizeInlineHtml(html: String): String = cookInlineHtml(html).toHtml()

internal fun replaceEmojiImagesWithText(html: String): String = normalizeInlineHtml(html)

internal fun String.htmlToPreviewText(): String {
    return cookInlineHtml(this)
        .plainText()
        .replace(Regex("\\s+"), " ")
        .trim()
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

internal fun String.inlineFontSizeRatio(): Float? {
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
