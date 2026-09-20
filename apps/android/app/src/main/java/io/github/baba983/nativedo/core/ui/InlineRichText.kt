package io.github.baba983.nativedo.core.ui

import org.jsoup.Jsoup
import org.jsoup.nodes.Entities
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

private fun CookedInline.tokenReplacements(): Map<String, String> {
    val replacements = HashMap<String, String>()
    inlineImages.forEach { replacements[it.token] = it.contentDescription }
    inlineIcons.forEach { replacements[it.token] = it.contentDescription }
    inlineHashtagChips.forEach { replacements[it.token] = it.label }
    inlineSizes.forEach {
        replacements[it.startToken] = ""
        replacements[it.endToken] = ""
    }
    return replacements
}

/** The flattened text, with the token-form constructs replaced by their readable form. */
internal fun CookedInline.plainText(): String {
    val out = StringBuilder()
    appendText(richText, out)
    var text = out.toString()
    tokenReplacements().forEach { (token, value) -> text = text.replace(token, value) }
    return text
}

/**
 * The tree with the token-form constructs replaced by the readable form `plainText()` uses.
 *
 * The block path resolves a token into a real span - an emoji image, a hashtag chip, a size range
 * (`PostBodyInlineSpans`) - but upstream's renderer has no such step: handed a token, it draws it
 * literally, which is how `__nd_inline_image_0__` ended up as body text on the device. The rich
 * mapping flattens the tokens into the tree it hands over instead. The tree [cookInlineHtml]
 * returns is fresh, so this rewrites it in place.
 */
internal fun CookedInline.flattenTokensForLayout(): TL_iv.RichText {
    val replacements = tokenReplacements()
    fun replaceTokens(value: String): String {
        var text = value
        replacements.forEach { (token, replacement) -> text = text.replace(token, replacement) }
        return text
    }

    fun rewrite(node: TL_iv.RichText?) {
        when (node) {
            null -> Unit
            is TL_iv.textPlain -> node.text = replaceTokens(node.text.orEmpty())
            is TL_iv.textConcat -> node.texts.forEach(::rewrite)
            else -> rewrite(node.text)
        }
    }

    rewrite(richText)
    return richText
}

/** The model back as HTML, for the surfaces that still hand HTML to `HtmlCompat` (profile bios). */
internal fun CookedInline.toHtml(): String {
    val out = StringBuilder()
    appendHtml(richText, out)
    var html = out.toString()
    tokenReplacements().forEach { (token, value) ->
        html = html.replace(token, Entities.escape(value))
    }
    return html
}

private fun appendHtml(node: TL_iv.RichText, out: StringBuilder) {
    when (node) {
        is TL_iv.textEmpty -> Unit
        is TL_iv.textPlain -> out.append(Entities.escape(node.text.orEmpty()))
        is TL_iv.textConcat -> node.texts.forEach { appendHtml(it, out) }
        is TL_iv.textUrl -> {
            out.append("<a href=\"").append(Entities.escape(node.url.orEmpty())).append("\">")
            node.text?.let { appendHtml(it, out) }
            out.append("</a>")
        }
        else -> {
            val name = htmlTagOf(node) ?: return
            out.append(name)
            node.text?.let { appendHtml(it, out) }
            out.append(name.replaceFirst("<", "</"))
        }
    }
}

private fun htmlTagOf(node: TL_iv.RichText): String? = when (node) {
    is TL_iv.textBold -> "<b>"
    is TL_iv.textItalic -> "<i>"
    is TL_iv.textUnderline -> "<u>"
    is TL_iv.textStrike -> "<s>"
    is TL_iv.textFixed -> "<code>"
    is TL_iv.textSubscript -> "<sub>"
    is TL_iv.textSuperscript -> "<sup>"
    is TL_iv.textMarked -> "<mark>"
    is TL_iv.textSpoiler -> "<span class=\"spoiler\">"
    else -> null
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

        var node = nodes(element.childNodes())
        for (wrapper in wrappersOf(element)) {
            node = wrapper(node)
        }
        return listOf(sizeMarker(element, node))
    }

    /**
     * The wrappers an element asks for, outermost first. One element can carry several (a Discourse
     * span with both underline and line-through, a link inside a bold run), which is why this is a
     * list instead of a single `when` arm.
     */
    private fun wrappersOf(element: Element): List<(TL_iv.RichText) -> TL_iv.RichText> {
        val wrappers = mutableListOf<(TL_iv.RichText) -> TL_iv.RichText>()
        if (element.tagName() == "a" && element.attr("href").isNotBlank()) {
            val url = element.attr("href")
            wrappers += { child: TL_iv.RichText -> TL_iv.textUrl().apply { this.url = url; text = child } }
        }
        if (element.hasClass("spoiler") || element.hasClass("spoiled")) {
            wrappers += { child: TL_iv.RichText -> TL_iv.textSpoiler().apply { text = child } }
        }
        if (element.tagName() == "b" || element.tagName() == "strong") {
            wrappers += { child: TL_iv.RichText -> TL_iv.textBold().apply { text = child } }
        }
        if (element.tagName() == "i" || element.tagName() == "em") {
            wrappers += { child: TL_iv.RichText -> TL_iv.textItalic().apply { text = child } }
        }
        if (element.isUnderline()) {
            wrappers += { child: TL_iv.RichText -> TL_iv.textUnderline().apply { text = child } }
        }
        if (element.isStrike()) {
            wrappers += { child: TL_iv.RichText -> TL_iv.textStrike().apply { text = child } }
        }
        if (element.isMono()) {
            wrappers += { child: TL_iv.RichText -> TL_iv.textFixed().apply { text = child } }
        }
        when (element.tagName()) {
            "sub" -> wrappers += { child: TL_iv.RichText -> TL_iv.textSubscript().apply { text = child } }
            "sup" -> wrappers += { child: TL_iv.RichText -> TL_iv.textSuperscript().apply { text = child } }
            "mark" -> wrappers += { child: TL_iv.RichText -> TL_iv.textMarked().apply { text = child } }
        }
        return wrappers
    }

    /** `[size]` spans have no RichText case, so they keep the marker pair the HTML pipeline used. */
    private fun sizeMarker(element: Element, styled: TL_iv.RichText): TL_iv.RichText {
        val ratio = element.sizeRatio() ?: return styled
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

private fun Element.sizeRatio(): Float? = when (tagName()) {
    "big" -> 1.25f
    "small" -> 0.8f
    else -> attr("style").inlineFontSizeRatio()
}

private fun Element.isUnderline(): Boolean {
    if (tagName() == "u" || tagName() == "ins" || hasClass("bbcode-u")) return true
    val style = attr("style")
    return style.contains("text-decoration", ignoreCase = true) &&
        style.contains("underline", ignoreCase = true)
}

private fun Element.isStrike(): Boolean {
    if (tagName() == "s" || tagName() == "del" || tagName() == "strike" || hasClass("bbcode-s")) return true
    val style = attr("style")
    return style.contains("text-decoration", ignoreCase = true) &&
        style.contains("line-through", ignoreCase = true)
}

private fun Element.isMono(): Boolean {
    return tagName() == "code" || tagName() == "kbd" || tagName() == "tt" || tagName() == "samp"
}
