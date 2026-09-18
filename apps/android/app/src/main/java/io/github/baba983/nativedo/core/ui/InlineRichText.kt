package io.github.baba983.nativedo.core.ui

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.telegram.tgnet.tl.TL_iv

/**
 * Discourse's cooked inline HTML as Telegram's inline model.
 *
 * The post body speaks `TL_iv.RichText` the way the composer speaks `TextStyleSpan`:
 * styles, links, spoilers and sub/superscript become model nodes, so the renderer never
 * parses HTML and the shape stays the one upstream's layout helpers expect
 * (`ArticleViewer.createLayoutForText` takes a `TL_iv.RichText`).
 *
 * Discourse-only constructs keep a token form instead of a model node, exactly as the
 * HTML pipeline did, because Telegram's model has no counterpart: emoji and inline
 * icons are image URLs (not Telegram documents), hashtag chips are a Discourse UI, and
 * `[size]` spans have no `RichText` case.
 */
internal data class CookedInline(
    val richText: TL_iv.RichText,
    val inlineImages: List<InlineImageSpec> = emptyList(),
    val inlineIcons: List<InlineIconSpec> = emptyList(),
    val inlineHashtagChips: List<InlineHashtagChipSpec> = emptyList(),
    val inlineSizes: List<InlineSizeSpec> = emptyList(),
)

internal data class InlineSizeSpec(
    val startToken: String,
    val endToken: String,
    val relativeSize: Float,
)

internal fun cookInlineHtml(html: String): CookedInline {
    val cooker = InlineCooker()
    val root = cooker.nodes(Jsoup.parseBodyFragment(html).body().childNodes())
    return CookedInline(
        richText = root,
        inlineImages = cooker.inlineImages,
        inlineIcons = cooker.inlineIcons,
        inlineHashtagChips = cooker.inlineHashtagChips,
        inlineSizes = cooker.inlineSizes,
    )
}

/** The flattened text, with the token-form constructs replaced by their readable form. */
internal fun CookedInline.plainText(): String {
    val replacements = HashMap<String, String>()
    inlineImages.forEach { replacements[it.token] = it.contentDescription }
    inlineIcons.forEach { replacements[it.token] = it.contentDescription }
    inlineHashtagChips.forEach { replacements[it.token] = it.label }
    inlineSizes.forEach {
        replacements[it.startToken] = ""
        replacements[it.endToken] = ""
    }
    val out = StringBuilder()
    appendText(richText, out)
    var text = out.toString()
    replacements.forEach { (token, value) -> text = text.replace(token, value) }
    return text
}

private fun appendText(node: TL_iv.RichText, out: StringBuilder) {
    when (node) {
        is TL_iv.textPlain -> out.append(node.text)
        is TL_iv.textConcat -> node.texts.forEach { appendText(it, out) }
        else -> node.text?.let { appendText(it, out) }
    }
}

private fun plain(text: String): TL_iv.RichText = TL_iv.textPlain().apply { this.text = text }

private fun concat(nodes: List<TL_iv.RichText>): TL_iv.RichText = when (nodes.size) {
    0 -> TL_iv.textEmpty()
    1 -> nodes[0]
    else -> TL_iv.textConcat().apply { texts = ArrayList(nodes) }
}

private class InlineCooker {
    val inlineImages = mutableListOf<InlineImageSpec>()
    val inlineIcons = mutableListOf<InlineIconSpec>()
    val inlineHashtagChips = mutableListOf<InlineHashtagChipSpec>()
    val inlineSizes = mutableListOf<InlineSizeSpec>()

    fun nodes(nodes: List<Node>): TL_iv.RichText = concat(nodes.flatMap(::node))

    private fun node(node: Node): List<TL_iv.RichText> = when (node) {
        is TextNode -> node.text().takeIf { it.isNotEmpty() }?.let { listOf(plain(it)) }.orEmpty()
        is Element -> element(node)
        else -> emptyList()
    }

    private fun element(element: Element): List<TL_iv.RichText> {
        inlineImage(element)?.let { token -> return listOf(plain(token)) }
        inlineIcon(element)?.let { token -> return listOf(plain(token)) }
        inlineHashtagChip(element)?.let { token -> return listOf(plain(token)) }
        if (element.tagName() == "br") {
            return listOf(plain("\n"))
        }

        val content = nodes(element.childNodes())
        val styled = when {
            element.hasClass("spoiler") || element.hasClass("spoiled") ->
                TL_iv.textSpoiler().apply { text = content }

            element.tagName() == "b" || element.tagName() == "strong" ->
                TL_iv.textBold().apply { text = content }

            element.tagName() == "i" || element.tagName() == "em" ->
                TL_iv.textItalic().apply { text = content }

            element.isUnderline() -> TL_iv.textUnderline().apply { text = content }

            element.tagName() == "s" || element.tagName() == "del" || element.tagName() == "strike" ||
                element.hasLineThrough() -> TL_iv.textStrike().apply { text = content }

            element.tagName() == "code" || element.tagName() == "kbd" ||
                element.tagName() == "tt" || element.tagName() == "samp" ->
                TL_iv.textFixed().apply { text = content }

            element.tagName() == "sub" -> TL_iv.textSubscript().apply { text = content }

            element.tagName() == "sup" -> TL_iv.textSuperscript().apply { text = content }

            element.tagName() == "mark" -> TL_iv.textMarked().apply { text = content }

            element.tagName() == "a" && element.attr("href").isNotBlank() ->
                TL_iv.textUrl().apply {
                    url = element.attr("href")
                    text = content
                }

            else -> content
        }
        return listOf(sizeMarker(element, styled))
    }

    /** `[size]` spans have no RichText case, so they keep the marker pair the HTML pipeline used. */
    private fun sizeMarker(element: Element, styled: TL_iv.RichText): TL_iv.RichText {
        val ratio = element.inlineTextStyleSpec(inlineSizes.size)?.relativeSize ?: return styled
        val spec = InlineSizeSpec(
            startToken = "__nd_inline_size_start_${inlineSizes.size}__",
            endToken = "__nd_inline_size_end_${inlineSizes.size}__",
            relativeSize = ratio,
        )
        inlineSizes += spec
        return TL_iv.textConcat().apply {
            texts = ArrayList(listOf(plain(spec.startToken), styled, plain(spec.endToken)))
        }
    }

    private fun inlineImage(element: Element): String? {
        if (element.tagName() != "img") return null
        val replacement = element.fallbackText()
        val kind = element.inlineImageKind() ?: return replacement
        val src = element.attr("src").ifBlank { return replacement }
        val token = "__nd_inline_image_${inlineImages.size}__"
        inlineImages += InlineImageSpec(
            token = token,
            rawUrl = src,
            contentDescription = replacement,
            kind = kind,
        )
        return token
    }

    private fun inlineIcon(element: Element): String? {
        if (element.tagName() != "svg") {
            // `<i class="fa...">` icons are decorative and fall back to their text.
            return if (element.tagName() == "i" && element.isDecorativeInlineIcon()) {
                element.iconFallbackText()
            } else {
                null
            }
        }
        val symbolId = element.inlineSvgSymbolId()
        if (symbolId.isNullOrBlank()) {
            return if (element.isDecorativeInlineIcon()) element.iconFallbackText() else null
        }
        val token = "__nd_inline_icon_${inlineIcons.size}__"
        inlineIcons += InlineIconSpec(
            token = token,
            symbolId = symbolId,
            rawTintColor = element.inlineSvgTintColor(),
            contentDescription = element.iconFallbackText(),
        )
        return token
    }

    private fun inlineHashtagChip(element: Element): String? {
        if (element.tagName() != "a" || !element.hasClass("hashtag-cooked")) return null
        val chip = element.inlineHashtagChipSpec(inlineHashtagChips.size) ?: return null
        inlineHashtagChips += chip
        return chip.token
    }
}

private fun Element.isUnderline(): Boolean {
    return tagName() == "u" || tagName() == "ins" || hasClass("bbcode-u") || hasUnderline()
}

private fun Element.hasUnderline(): Boolean {
    val style = attr("style")
    return style.contains("text-decoration", ignoreCase = true) &&
        style.contains("underline", ignoreCase = true)
}

private fun Element.hasLineThrough(): Boolean {
    val style = attr("style")
    return style.contains("text-decoration", ignoreCase = true) &&
        style.contains("line-through", ignoreCase = true)
}
