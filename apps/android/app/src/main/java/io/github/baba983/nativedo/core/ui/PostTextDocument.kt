package io.github.baba983.nativedo.core.ui

data class TextDocument(
    val text: String,
    val entities: List<TextEntity>,
)

sealed interface TextEntity {
    val start: Int
    val end: Int

    data class Bold(
        override val start: Int,
        override val end: Int,
    ) : TextEntity

    data class Italic(
        override val start: Int,
        override val end: Int,
    ) : TextEntity

    data class Strike(
        override val start: Int,
        override val end: Int,
    ) : TextEntity

    data class Link(
        override val start: Int,
        override val end: Int,
        val rawUrl: String,
    ) : TextEntity

    data class Mention(
        override val start: Int,
        override val end: Int,
        val username: String,
    ) : TextEntity

    data class InlineCode(
        override val start: Int,
        override val end: Int,
    ) : TextEntity

    data class Spoiler(
        override val start: Int,
        override val end: Int,
    ) : TextEntity

    data class Emoji(
        override val start: Int,
        override val end: Int,
        val rawUrl: String,
        val fallback: String,
    ) : TextEntity

    data class SiteIcon(
        override val start: Int,
        override val end: Int,
        val rawUrl: String,
        val fallback: String,
    ) : TextEntity
}
