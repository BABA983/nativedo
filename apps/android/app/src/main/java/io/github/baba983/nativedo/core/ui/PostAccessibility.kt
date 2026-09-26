package io.github.baba983.nativedo.core.ui

import io.github.baba983.nativedo.core.html.CookedPostBlock

/**
 * What a screen reader hears for a post body.
 *
 * A post body is canvas - no block is a View - so the body's only accessibility surface is the
 * description the cell carries. Upstream's own chat cell builds one description for a whole message
 * (`ChatMessageCell` walking `appendAccessibilityText` per block), and this is the same idea for the
 * cooked blocks: text blocks contribute their text, and every image contributes the one description
 * a Discourse image has - its `alt` (a caption, when the image has no alt) - so a picture is no
 * longer an unlabelled hole in the spoken post.
 *
 * The labels are inline on purpose: they are spoken punctuation, not chrome the user can localise
 * today (the app ships one locale for these surfaces, like `topicFloorLabelForPostNumber`).
 */
internal fun postBodyAccessibilityText(blocks: List<CookedPostBlock>): String {
    val parts = ArrayList<String>()
    blocks.appendAccessibilityParts(parts)
    return parts.joinToString("。")
}

private fun List<CookedPostBlock>.appendAccessibilityParts(parts: MutableList<String>) {
    for (block in this) {
        when (block) {
            is CookedPostBlock.Text -> parts.addText(block.html)
            is CookedPostBlock.Heading -> parts.addText(block.html)
            is CookedPostBlock.Quote -> {
                block.title?.takeIf { it.isNotBlank() }?.let { title -> parts.add(title) }
                if (block.children.isEmpty()) {
                    parts.addText(block.html)
                } else {
                    block.children.appendAccessibilityParts(parts)
                }
            }
            is CookedPostBlock.Callout -> {
                block.titleHtml?.let { html -> parts.addText(html) }
                if (block.children.isEmpty()) {
                    parts.addText(block.html)
                } else {
                    block.children.appendAccessibilityParts(parts)
                }
            }
            // A code block's body is long and mostly punctuation; the reader can open the post for it.
            is CookedPostBlock.Code -> Unit
            is CookedPostBlock.ListItems -> block.items.forEach { item ->
                if (item.blocks.isEmpty()) parts.addText(item.html) else item.blocks.appendAccessibilityParts(parts)
            }
            is CookedPostBlock.Math -> parts.add(block.source)
            is CookedPostBlock.Attachment -> parts.add(
                block.sizeText?.takeIf { it.isNotBlank() }?.let { size -> "${block.name} ($size)" } ?: block.name,
            )
            is CookedPostBlock.Image -> parts.addImage(block)
            is CookedPostBlock.Collage -> block.images.forEach { image -> parts.addImage(image) }
            is CookedPostBlock.Onebox -> {
                val domain = block.domain?.takeIf { it.isNotBlank() }
                parts.add(if (domain != null) "链接：${block.title}（$domain）" else "链接：${block.title}")
            }
            is CookedPostBlock.Table -> {
                (block.headers + block.rows.flatten()).forEach { cell -> parts.addText(cell.html) }
            }
            is CookedPostBlock.Poll -> {
                block.title?.takeIf { it.isNotBlank() }?.let { title -> parts.add("投票：$title") }
                block.options.forEach { option ->
                    val votes = option.votes
                    parts.add(if (votes != null) "${option.label}（$votes 票）" else option.label)
                }
            }
            is CookedPostBlock.Details -> {
                block.summary?.takeIf { it.isNotBlank() }?.let { summary -> parts.add(summary) }
                // A collapsed details block does not draw its children, so it does not read them either.
                if (block.open) block.children.appendAccessibilityParts(parts)
            }
            CookedPostBlock.Rule -> Unit
        }
    }
}

/** `alt` is the image's own description; a caption is the visible text under it. */
private fun MutableList<String>.addImage(image: CookedPostBlock.Image) {
    val alt = image.alt.trim()
    val caption = image.captionHtml?.let { html -> cookInlineHtml(html).plainText().trim() }.orEmpty()
    when {
        alt.isNotEmpty() -> add("图片：$alt")
        caption.isNotEmpty() -> add(caption)
        else -> add("图片")
    }
    if (alt.isNotEmpty() && caption.isNotEmpty()) {
        add(caption)
    }
}

private fun MutableList<String>.addText(html: String) {
    val text = cookInlineHtml(html).plainText().trim()
    if (text.isNotEmpty()) {
        add(text)
    }
}
