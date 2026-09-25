package io.github.baba983.nativedo.core.ui

import io.github.baba983.nativedo.core.html.CookedPostBlock
import io.github.baba983.nativedo.core.html.resolvePostImageUrl
import org.telegram.tgnet.tl.TL_iv
import java.util.ArrayList

/**
 * Cooked post blocks as Telegram's rich-message model.
 *
 * This is the block-level half of the boundary [cookInlineHtml] draws for inline runs: the post's
 * structure becomes `TL_iv` page blocks, so upstream's renderer (`RichMessageLayout`) can draw a
 * topic body without ever seeing Discourse HTML. The inline leaves stay [CookedInline.richText].
 *
 * What maps, and what does not yet:
 *
 * - mapped: paragraph, headings 1-6, blockquote (with the author as the caption), preformatted,
 *   lists (plain and ordered, with nested blocks), details/toggle, tables, divider, images and
 *   collages.
 * - not mapped (the Discourse-only half of `CookedPostBlock`): callouts, oneboxes and polls.
 *   `pageBlock(...)` returns null for them, so the hybrid body keeps them, and any container whose
 *   subtree holds one, in our own block tree (see [splitPostBodySegments]).
 *
 * A quote maps (upstream's blockquote, with the author as the caption), but the hybrid body does not
 * use that mapping: [isOurBlock] keeps quotes in our block tree so they keep the card `QuoteBlock`
 * draws. The mapping stays here for the model's sake (the editor reads quotes the same way).
 */
internal object PostRichMessageBuilder {

    fun build(blocks: List<CookedPostBlock>, baseUrl: String = ""): TL_iv.RichMessage = TL_iv.RichMessage().apply {
        this.blocks = ArrayList(pageBlocks(blocks, baseUrl))
    }

    fun pageBlocks(blocks: List<CookedPostBlock>, baseUrl: String = ""): List<TL_iv.PageBlock> =
        blocks.mapNotNull { pageBlock(it, baseUrl) }

    /**
     * True when this block, and every block it carries, has an upstream counterpart. A container
     * whose subtree holds a Discourse-only block (callout, onebox, poll) is not mappable as a whole:
     * the renderer would silently drop the child, so the hybrid body keeps the whole subtree in our
     * block tree instead.
     */
    fun isMappable(block: CookedPostBlock): Boolean = when (block) {
        is CookedPostBlock.Callout,
        is CookedPostBlock.Onebox,
        is CookedPostBlock.Poll,
        -> false
        is CookedPostBlock.Quote -> block.children.all(::isMappable)
        is CookedPostBlock.Details -> block.children.all(::isMappable)
        is CookedPostBlock.ListItems -> block.items.all { item -> item.blocks.all(::isMappable) }
        else -> true
    }

    fun pageBlock(block: CookedPostBlock, baseUrl: String = ""): TL_iv.PageBlock? {
        if (!isMappable(block)) {
            return null
        }
        return when (block) {
            is CookedPostBlock.Text -> paragraph(block.html, baseUrl)
            is CookedPostBlock.Heading -> heading(block.level, block.html, baseUrl)
            is CookedPostBlock.Quote -> quote(block, baseUrl)
            is CookedPostBlock.Code -> preformatted(block)
            is CookedPostBlock.ListItems -> list(block, baseUrl)
            is CookedPostBlock.Details -> details(block, baseUrl)
            is CookedPostBlock.Table -> table(block, baseUrl)
            is CookedPostBlock.Image -> photo(block, baseUrl)
            is CookedPostBlock.Collage -> collage(block, baseUrl)
            CookedPostBlock.Rule -> TL_iv.pageBlockDivider()
            is CookedPostBlock.Callout,
            is CookedPostBlock.Onebox,
            is CookedPostBlock.Poll,
            -> null
        }
    }

    private fun paragraph(html: String, baseUrl: String): TL_iv.PageBlock = TL_iv.pageBlockParagraph().apply {
        text = inline(html, baseUrl)
    }

    private fun heading(level: Int, html: String, baseUrl: String): TL_iv.PageBlock {
        val block: TL_iv.PageBlock = when (level.coerceIn(1, 6)) {
            1 -> TL_iv.pageBlockHeading1()
            2 -> TL_iv.pageBlockHeading2()
            3 -> TL_iv.pageBlockHeading3()
            4 -> TL_iv.pageBlockHeading4()
            5 -> TL_iv.pageBlockHeading5()
            else -> TL_iv.pageBlockHeading6()
        }
        block.text = inline(html, baseUrl)
        return block
    }

    /**
     * A Discourse quote is a blockquote with an author line. Upstream's renderer reads the author
     * from `caption`, so the title travels there; nested blocks (a quote can contain lists, code,
     * another quote) make it the `...Blocks` variant.
     */
    private fun quote(block: CookedPostBlock.Quote, baseUrl: String): TL_iv.PageBlock {
        val author = block.title
        return if (block.children.isNotEmpty()) {
            TL_iv.pageBlockBlockquoteBlocks().apply {
                caption = author?.let(::plain)
                blocks = ArrayList(pageBlocks(block.children, baseUrl))
            }
        } else {
            TL_iv.pageBlockBlockquote().apply {
                caption = author?.let(::plain)
                text = inline(block.html, baseUrl)
            }
        }
    }

    private fun preformatted(block: CookedPostBlock.Code): TL_iv.PageBlock =
        TL_iv.pageBlockPreformatted().apply {
            // The parser keeps the fence's language (`lang-kotlin` -> `kotlin`) and drops the site's
            // "no language" marker; upstream highlights and labels what it gets.
            language = block.language
            text = plain(block.code)
        }

    private fun list(block: CookedPostBlock.ListItems, baseUrl: String): TL_iv.PageBlock =
        if (block.ordered) {
            TL_iv.pageBlockOrderedList().apply {
                items = ArrayList(block.items.map { orderedItem(it, baseUrl) })
            }
        } else {
            TL_iv.pageBlockList().apply {
                items = ArrayList(block.items.map { listItem(it, baseUrl) })
            }
        }

    private fun listItem(item: CookedPostBlock.ListItem, baseUrl: String): TL_iv.PageListItem {
        val inlineHtml = item.inlineHtmlOrNull()
        return if (inlineHtml != null) {
            TL_iv.TL_pageListItemText().apply { text = inline(inlineHtml, baseUrl) }
        } else {
            TL_iv.TL_pageListItemBlocks().apply { blocks = ArrayList(pageBlocks(item.blocks, baseUrl)) }
        }
    }

    private fun orderedItem(item: CookedPostBlock.ListItem, baseUrl: String): TL_iv.PageListOrderedItem {
        val inlineHtml = item.inlineHtmlOrNull()
        return if (inlineHtml != null) {
            TL_iv.TL_pageListOrderedItemText().apply { text = inline(inlineHtml, baseUrl) }
        } else {
            TL_iv.TL_pageListOrderedItemBlocks().apply { blocks = ArrayList(pageBlocks(item.blocks, baseUrl)) }
        }
    }

    /**
     * The parser always fills a list item's child blocks (an `<li>` may hold anything), so an item
     * whose only child block is a paragraph is a plain inline item and keeps upstream's inline form
     * (`pageListItemText`), which is what the renderer draws as a list row.
     */
    private fun CookedPostBlock.ListItem.inlineHtmlOrNull(): String? =
        if (blocks.isEmpty()) html else (blocks.singleOrNull() as? CookedPostBlock.Text)?.html

    private fun details(block: CookedPostBlock.Details, baseUrl: String): TL_iv.PageBlock =
        TL_iv.pageBlockDetails().apply {
            open = block.open
            title = (block.summaryHtml ?: block.summary)?.let { html -> inline(html, baseUrl) }
            blocks = ArrayList(pageBlocks(block.children, baseUrl))
        }

    private fun table(block: CookedPostBlock.Table, baseUrl: String): TL_iv.PageBlock =
        TL_iv.pageBlockTable().apply {
            bordered = true
            rows = ArrayList(
                listOf(block.headers to true, *block.rows.map { it to false }.toTypedArray())
                    .map { (row, header) ->
                        TL_iv.pageTableRow().apply {
                            cells = ArrayList(row.map { cell -> tableCell(cell, header, baseUrl) })
                        }
                    }
            )
        }

    private fun tableCell(cell: CookedPostBlock.TableCell, header: Boolean, baseUrl: String): TL_iv.pageTableCell =
        TL_iv.pageTableCell().apply {
            this.header = header
            align_center = cell.align == CookedPostBlock.TableCellAlign.Center
            align_right = cell.align == CookedPostBlock.TableCellAlign.End
            colspan = cell.colspan
            rowspan = cell.rowspan
            text = inline(cell.html, baseUrl)
        }

    /**
     * A Discourse image as a rich-message photo: the block carries the absolute URL and the pixel
     * size the post declared, and the caption travels as upstream's `PageCaption`. There is no
     * `TLRPC.Photo` behind it - the renderer's photo block learns the URL case (its `applyImage`),
     * and the bitmap itself is device-only (a Paparazzi run has no network).
     */
    private fun photo(
        image: CookedPostBlock.Image,
        baseUrl: String,
        withCaption: Boolean = true,
    ): TL_iv.PageBlock =
        TL_iv.pageBlockPhoto().apply {
            // A relative src needs a base to resolve against; without one the raw src is all we have.
            url = if (baseUrl.isEmpty()) image.src else resolvePostImageUrl(baseUrl, image.src)
            w = image.widthPx
            h = image.heightPx
            if (withCaption) {
                image.captionHtml?.takeIf { it.isNotBlank() }?.let { html ->
                    caption = pageCaption(inline(html, baseUrl))
                }
            }
        }

    /**
     * A Discourse collage as upstream's `pageBlockCollage`: one `pageBlockPhoto` per image, each with
     * its own URL and pixel size (upstream's collage lays them out from those aspect ratios).
     *
     * Telegram's model has **one caption per media group** - the renderer reads `collage.caption` and
     * never a member's - so a Discourse caption travels to the group instead of being dropped, and
     * several members' captions join as paragraphs of that one caption.
     */
    private fun collage(collage: CookedPostBlock.Collage, baseUrl: String): TL_iv.PageBlock =
        TL_iv.pageBlockCollage().apply {
            items = ArrayList(collage.images.map { image -> photo(image, baseUrl, withCaption = false) })
            val captions = collage.images.mapNotNull { image ->
                image.captionHtml?.takeIf { html -> html.isNotBlank() }
            }
            if (captions.isNotEmpty()) {
                caption = pageCaption(joinedCaptions(captions, baseUrl))
            }
        }

    /** Telegram's PageCaption wire requires both text and credit, even when credit is empty. */
    private fun pageCaption(text: TL_iv.RichText): TL_iv.PageCaption = TL_iv.PageCaption().apply {
        this.text = text
        credit = TL_iv.textEmpty()
    }

    private fun joinedCaptions(captions: List<String>, baseUrl: String): TL_iv.RichText {
        val parts = ArrayList<TL_iv.RichText>(captions.size * 2 - 1)
        captions.forEachIndexed { index, html ->
            if (index > 0) parts.add(TL_iv.textPlain().apply { text = "\n" })
            parts.add(inline(html, baseUrl))
        }
        return if (parts.size == 1) parts[0] else TL_iv.textConcat().apply { texts = parts }
    }

    private fun inline(html: String, baseUrl: String): TL_iv.RichText =
        cookInlineHtml(html).toLayoutTree(baseUrl)

    private fun plain(value: String): TL_iv.RichText = TL_iv.textPlain().apply { text = value }
}

/**
 * A stretch of the post body: what upstream's renderer draws ([RichRun]) and what stays in our own
 * block tree ([OwnBlock]). The cell stacks both kinds in the order the post wrote them, so a body
 * that mixes paragraphs with a poll/onebox/callout reads in place instead of losing content.
 */
internal sealed interface PostBodySegment {
    data class RichRun(val blocks: List<CookedPostBlock>) : PostBodySegment

    data class OwnBlock(val block: CookedPostBlock) : PostBodySegment
}

/**
 * Splits a body into maximal runs of blocks upstream's renderer draws and the blocks our block tree
 * keeps in between. A container (details, list) whose subtree holds one of those falls back as a
 * whole, so a run never silently drops a child.
 */
internal fun splitPostBodySegments(blocks: List<CookedPostBlock>): List<PostBodySegment> {
    val segments = ArrayList<PostBodySegment>()
    var run = ArrayList<CookedPostBlock>()
    for (block in blocks) {
        if (!isOurBlock(block)) {
            run.add(block)
            continue
        }
        if (run.isNotEmpty()) {
            segments.add(PostBodySegment.RichRun(run))
            run = ArrayList()
        }
        segments.add(PostBodySegment.OwnBlock(block))
    }
    if (run.isNotEmpty()) {
        segments.add(PostBodySegment.RichRun(run))
    }
    return segments
}

/**
 * True when the hybrid body keeps this block - and, for a container, its whole subtree - in our own
 * block tree, even where [PostRichMessageBuilder] could express it.
 *
 * Two families land here. The Discourse-only blocks (callout, onebox, poll) have no upstream
 * counterpart at all. A **quote** does, but only `QuoteBlock` draws the product's card: the leading
 * bar, the quoted author's avatar, the referenced topic's title, its category chip and the collapse
 * chevron (`expandQuote` fetches the quoted post for the expand affordance). A mapped blockquote is a
 * tinted run of text with none of that, which is what the device pass found
 * (`docs/device-verification.md` §7) - so the hybrid keeps quotes ours the same way.
 */
internal fun isOurBlock(block: CookedPostBlock): Boolean = when (block) {
    is CookedPostBlock.Callout,
    is CookedPostBlock.Onebox,
    is CookedPostBlock.Poll,
    is CookedPostBlock.Quote,
    -> true
    is CookedPostBlock.Details -> block.children.any(::isOurBlock)
    is CookedPostBlock.ListItems -> block.items.any { item -> item.blocks.any(::isOurBlock) }
    else -> false
}
