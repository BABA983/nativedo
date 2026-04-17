package io.github.baba983.nativedo.core.ui

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import kotlin.math.roundToInt

internal data class PreparedInlineContent(
    val html: String,
    val inlineImages: List<InlineImageSpec>,
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
    }

    fun displayHeightPx(textSizePx: Float): Int = displayWidthPx(textSizePx)
}

internal enum class InlineImageKind {
    Emoji,
    SiteIcon,
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
    return baseUrl.toHttpUrl().resolve(expandedTemplate)?.toString() ?: expandedTemplate
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
        val token = "inline_image_${inlineImages.size}"
        inlineImages += InlineImageSpec(
            token = token,
            rawUrl = src,
            contentDescription = replacement,
            kind = kind,
        )
        image.replaceWith(TextNode(token))
    }

    body.select("i, svg").forEach { icon ->
        if (icon.isDecorativeInlineIcon()) {
            icon.replaceWith(
                TextNode(
                    icon.attr("aria-label")
                        .ifBlank { icon.attr("title") },
                ),
            )
        }
    }

    return PreparedInlineContent(
        html = body.html(),
        inlineImages = inlineImages,
    )
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

private fun Element.inlineImageKind(): InlineImageKind? {
    return when {
        hasClass("emoji") -> InlineImageKind.Emoji
        hasClass("site-icon") -> InlineImageKind.SiteIcon
        else -> null
    }
}

private fun Element.fallbackText(): String {
    return when {
        hasClass("emoji") -> attr("title").ifBlank { attr("alt") }
        else -> attr("alt").ifBlank { attr("title") }
    }
}
