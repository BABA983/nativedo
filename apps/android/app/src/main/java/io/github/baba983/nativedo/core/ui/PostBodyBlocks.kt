package io.github.baba983.nativedo.core.ui

sealed interface PostBodyBlock {
    data class HtmlBlock(
        val html: String,
    ) : PostBodyBlock

    data class UnknownBlock(
        val rawHtml: String,
        val tagName: String,
        val classes: Set<String>,
    ) : PostBodyBlock

    data class RichTextBlock(
        val style: RichTextBlockStyle,
        val inlines: List<PostInline>,
    ) : PostBodyBlock

    data class ParagraphBlock(
        val content: TextDocument,
    ) : PostBodyBlock

    data class HeadingBlock(
        val level: Int,
        val content: TextDocument,
    ) : PostBodyBlock

    data object HorizontalRuleBlock : PostBodyBlock

    data class ImageBlock(
        val image: PostBodyImage,
    ) : PostBodyBlock

    data class LightboxBlock(
        val image: PostBodyImage,
        val filename: String?,
        val information: String?,
    ) : PostBodyBlock

    data class ImageGridBlock(
        val images: List<PostBodyImage>,
        val columns: Int,
    ) : PostBodyBlock

    data class OneboxBlock(
        val sourceLabel: String,
        val sourceUrl: String,
        val sourceIconUrl: String?,
        val title: String,
        val targetUrl: String,
        val description: String?,
        val image: PostBodyImage?,
    ) : PostBodyBlock

    data class DetailsBlock(
        val summary: TextDocument,
        val children: List<PostBodyBlock>,
        val expandedByDefault: Boolean,
    ) : PostBodyBlock

    data class CalloutBlock(
        val spec: DiscourseCalloutSpec,
        val title: TextDocument?,
        val children: List<PostBodyBlock>,
    ) : PostBodyBlock

    data class QuoteBlock(
        val children: List<PostBodyBlock>,
    ) : PostBodyBlock

    data class DiscourseQuoteBlock(
        val authorDisplayName: String,
        val authorUsername: String?,
        val avatarUrl: String?,
        val title: TextDocument?,
        val children: List<PostBodyBlock>,
        val sourceTopicId: Long? = null,
        val sourcePostNumber: Int? = null,
        val isExpandable: Boolean = false,
        val isInitiallyExpanded: Boolean = true,
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
