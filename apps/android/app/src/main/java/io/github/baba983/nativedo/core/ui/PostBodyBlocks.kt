package io.github.baba983.nativedo.core.ui

sealed interface PostBodyBlock {
    data class HtmlBlock(
        val html: String,
    ) : PostBodyBlock

    data class RichTextBlock(
        val style: RichTextBlockStyle,
        val inlines: List<PostInline>,
    ) : PostBodyBlock

    data class ImageBlock(
        val image: PostBodyImage,
    ) : PostBodyBlock

    data class ImageGridBlock(
        val images: List<PostBodyImage>,
        val columns: Int,
    ) : PostBodyBlock

    data class DetailsBlock(
        val summary: RichTextBlock,
        val children: List<PostBodyBlock>,
        val expandedByDefault: Boolean,
    ) : PostBodyBlock

    data class QuoteBlock(
        val children: List<PostBodyBlock>,
    ) : PostBodyBlock

    data class CodeBlock(
        val code: String,
        val languageHint: String?,
    ) : PostBodyBlock

    data class ListBlock(
        val ordered: Boolean,
        val items: List<ListItemBlock>,
    ) : PostBodyBlock
}

data class ListItemBlock(
    val children: List<PostBodyBlock>,
)

data class PostBodyImage(
    val rawUrl: String,
    val originalUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)
