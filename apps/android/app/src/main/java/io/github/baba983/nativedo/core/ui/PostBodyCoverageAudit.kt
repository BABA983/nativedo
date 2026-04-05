package io.github.baba983.nativedo.core.ui

data class PostBodyCoverageReport(
    val nativeBlockCount: Int,
    val htmlFallbackCount: Int,
    val unknownTagCounts: Map<String, Int>,
    val unknownClassCounts: Map<String, Int>,
)

internal fun collectPostBodyCoverage(blocks: List<PostBodyBlock>): PostBodyCoverageReport {
    var nativeBlockCount = 0
    var htmlFallbackCount = 0
    val unknownTagCounts = linkedMapOf<String, Int>()
    val unknownClassCounts = linkedMapOf<String, Int>()

    fun countUnknown(block: PostBodyBlock.UnknownBlock) {
        unknownTagCounts.merge(block.tagName, 1, Int::plus)
        block.classes.forEach { className ->
            unknownClassCounts.merge(className, 1, Int::plus)
        }
    }

    fun visit(block: PostBodyBlock) {
        when (block) {
            is PostBodyBlock.HtmlBlock -> {
                htmlFallbackCount += 1
            }

            is PostBodyBlock.UnknownBlock -> {
                countUnknown(block)
            }

            is PostBodyBlock.DetailsBlock -> {
                nativeBlockCount += 1
                block.children.forEach(::visit)
            }

            is PostBodyBlock.QuoteBlock -> {
                nativeBlockCount += 1
                block.children.forEach(::visit)
            }

            is PostBodyBlock.ListBlock -> {
                nativeBlockCount += 1
                block.items.forEach { item ->
                    item.children.forEach(::visit)
                }
            }

            is PostBodyBlock.RichTextBlock,
            is PostBodyBlock.ImageBlock,
            is PostBodyBlock.LightboxBlock,
            is PostBodyBlock.ImageGridBlock,
            is PostBodyBlock.OneboxBlock,
            is PostBodyBlock.DiscourseQuoteBlock,
            is PostBodyBlock.CodeBlock -> {
                nativeBlockCount += 1
            }
        }
    }

    blocks.forEach(::visit)
    return PostBodyCoverageReport(
        nativeBlockCount = nativeBlockCount,
        htmlFallbackCount = htmlFallbackCount,
        unknownTagCounts = unknownTagCounts,
        unknownClassCounts = unknownClassCounts,
    )
}
