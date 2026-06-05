package io.github.baba983.nativedo.ui.topic.body

import io.github.baba983.nativedo.core.html.CookedPostBlock
import io.github.baba983.nativedo.core.html.cookedGalleryImages

internal object PostBodyBlockFactory {
    fun build(
        blocks: List<CookedPostBlock>,
        context: PostBodyBuildContext,
        state: PostBodyBlockState? = null,
    ): List<PostBodyBlock> {
        val gallery = cookedGalleryImages(blocks, context.baseUrl)
        return blocks.mapIndexed { index, block ->
            when (block) {
                is CookedPostBlock.Text -> ParagraphBlock(
                    html = block.html,
                    context = context,
                )
                is CookedPostBlock.Heading -> HeadingBlock(
                    html = block.html,
                    level = block.level,
                    context = context,
                )
                is CookedPostBlock.Image -> ImageBlock(
                    image = block,
                    gallery = gallery,
                    context = context,
                )
                is CookedPostBlock.Collage -> CollageBlock(
                    block = block,
                    gallery = gallery,
                    context = context,
                )
                is CookedPostBlock.Onebox -> OneboxBlock(
                    block = block,
                    context = context,
                )
                is CookedPostBlock.Code -> CodeBlock(
                    code = block.code,
                    context = context,
                )
                is CookedPostBlock.ListItems -> ListBlock(
                    block = block,
                    context = context,
                )
                is CookedPostBlock.Quote -> QuoteBlock(
                    block = block,
                    context = context,
                    state = state,
                    stateIndex = index,
                )
                is CookedPostBlock.Spoiler -> SpoilerBlock(
                    block = block,
                    context = context,
                    state = state,
                    stateIndex = index,
                )
                is CookedPostBlock.Table -> TableBlock(
                    block = block,
                    context = context,
                )
                is CookedPostBlock.Poll -> PollBlock(
                    block = block,
                    context = context,
                    state = state,
                    stateIndex = index,
                )
                CookedPostBlock.Rule -> RuleBlock(context)
            }
        }
    }
}
