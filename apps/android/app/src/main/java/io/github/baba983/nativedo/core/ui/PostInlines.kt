package io.github.baba983.nativedo.core.ui

sealed interface PostInline {
    data class Text(
        val text: String,
    ) : PostInline

    data class Link(
        val children: List<PostInline>,
        val rawUrl: String,
    ) : PostInline

    data class Emoji(
        val rawUrl: String,
        val fallback: String,
    ) : PostInline

    data class SiteIcon(
        val rawUrl: String,
        val fallback: String,
    ) : PostInline

    data class Styled(
        val style: InlineStyle,
        val children: List<PostInline>,
    ) : PostInline

    data object LineBreak : PostInline
}

enum class InlineStyle {
    Bold,
    Italic,
    Strike,
    Code,
    LargeText,
}

enum class RichTextBlockStyle {
    Paragraph,
    Heading1,
    Heading2,
    Heading3,
}
