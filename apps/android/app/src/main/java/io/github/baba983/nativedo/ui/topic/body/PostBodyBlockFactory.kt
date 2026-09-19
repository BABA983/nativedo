package io.github.baba983.nativedo.ui.topic.body

import io.github.baba983.nativedo.core.html.CookedPostBlock
import io.github.baba983.nativedo.core.html.GalleryImage
import io.github.baba983.nativedo.core.html.cookedGalleryImages

internal object PostBodyBlockFactory {
    @JvmOverloads
    fun build(
        blocks: List<CookedPostBlock>,
        context: PostBodyBuildContext,
        state: PostBodyBlockState? = null,
        gallery: List<GalleryImage> = cookedGalleryImages(blocks, context.baseUrl),
        stateKeyPrefix: String = "",
        persistStructuralState: Boolean = true,
        listLevel: Int = 0,
    ): List<PostBodyBlock> {
        // The top-level call (no state prefix, no list level) is the post body itself: when the
        // rich-message switch is on, the body is upstream's renderer for every run of mappable
        // blocks, with our own blocks (callout/onebox/poll) kept between those runs. Nested calls
        // always carry a prefix, so the switch can never fire inside a subtree.
        if (RichMessageBody.enabled && listLevel == 0 && stateKeyPrefix.isEmpty()) {
            return RichMessageBody.blocks(blocks, context, state)
        }
        return blocks.mapIndexed { index, block ->
            val stateKey = if (stateKeyPrefix.isEmpty()) index.toString() else "$stateKeyPrefix.$index"
            val structuralState = if (persistStructuralState) state else null
            when (block) {
                is CookedPostBlock.Text -> ParagraphBlock(
                    block.html,
                    context,
                    state,
                    TextLayoutBlock.inlineSpoilerStateKey("text:$stateKey", block.html),
                )
                is CookedPostBlock.Heading -> HeadingBlock(
                    block.html,
                    block.level,
                    context,
                    state,
                    TextLayoutBlock.inlineSpoilerStateKey("heading:$stateKey", block.html),
                )
                is CookedPostBlock.Image -> ImageBlock(
                    block,
                    gallery,
                    context,
                    index == 0,
                    state,
                    TextLayoutBlock.inlineSpoilerStateKey("image:$stateKey", block.captionHtml ?: block.alt),
                )
                is CookedPostBlock.Collage -> CollageBlock(
                    block,
                    gallery,
                    context,
                    state,
                    TextLayoutBlock.inlineSpoilerStateKey("collage:$stateKey", block.images.joinToString("|") { it.captionHtml ?: it.alt }),
                )
                is CookedPostBlock.Onebox -> OneboxBlock(
                    block,
                    context,
                    state,
                    "onebox:$stateKey:${block.href}",
                )
                is CookedPostBlock.Code -> CodeBlock(
                    block.code,
                    context,
                )
                is CookedPostBlock.ListItems -> ListBlock(
                    block,
                    context,
                    gallery,
                    state,
                    "list:$stateKey",
                    listLevel,
                )
                is CookedPostBlock.Quote -> QuoteBlock(
                    block,
                    context,
                    structuralState,
                    index,
                    gallery,
                    state,
                    "quote:$stateKey",
                )
                is CookedPostBlock.Callout -> CalloutBlock(
                    block,
                    context,
                    gallery,
                    state,
                    structuralState,
                    index,
                    "callout:$stateKey",
                )
                is CookedPostBlock.Details -> DetailsBlock(
                    block,
                    context,
                    structuralState,
                    index,
                    gallery,
                    state,
                    "details:$stateKey",
                )
                is CookedPostBlock.Table -> TableBlock(
                    block,
                    context,
                    gallery,
                    state,
                    "table:$stateKey",
                )
                is CookedPostBlock.Poll -> PollBlock(
                    block,
                    context,
                    structuralState,
                    index,
                    state,
                    "poll:$stateKey",
                )
                CookedPostBlock.Rule -> RuleBlock(context)
            }
        }
    }
}
