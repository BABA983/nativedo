package io.github.baba983.nativedo.core.navigation

import io.github.baba983.nativedo.core.ui.TextDocument
import io.github.baba983.nativedo.core.ui.PostBodyImage

internal data class AppBrowserReaderDocument(
    val url: String,
    val title: String,
    val sourceName: String? = null,
    val excerpt: String? = null,
    val byline: String? = null,
    val publishedTime: String? = null,
    val heroImageUrl: String? = null,
    val blocks: List<AppBrowserReaderBlock>,
)

internal sealed interface AppBrowserReaderBlock {
    data class Paragraph(
        val content: TextDocument,
    ) : AppBrowserReaderBlock

    data class Heading(
        val level: Int,
        val content: TextDocument,
    ) : AppBrowserReaderBlock

    data class Quote(
        val blocks: List<AppBrowserReaderBlock>,
    ) : AppBrowserReaderBlock

    data class PullQuote(
        val content: TextDocument,
        val attribution: TextDocument? = null,
    ) : AppBrowserReaderBlock

    data class Image(
        val image: PostBodyImage,
        val alt: String? = null,
        val caption: TextDocument? = null,
        val credit: TextDocument? = null,
    ) : AppBrowserReaderBlock

    data class Details(
        val summary: TextDocument,
        val children: List<AppBrowserReaderBlock>,
        val expandedByDefault: Boolean = false,
    ) : AppBrowserReaderBlock

    data class Table(
        val title: TextDocument? = null,
        val rows: List<AppBrowserReaderTableRow>,
        val striped: Boolean = false,
        val bordered: Boolean = true,
    ) : AppBrowserReaderBlock

    data class ListBlock(
        val ordered: Boolean,
        val items: List<AppBrowserReaderListItem>,
    ) : AppBrowserReaderBlock

    data class CodeBlock(
        val code: String,
        val languageHint: String?,
    ) : AppBrowserReaderBlock

    data object Divider : AppBrowserReaderBlock
}

internal data class AppBrowserReaderListItem(
    val blocks: List<AppBrowserReaderBlock>,
)

internal data class AppBrowserReaderTableRow(
    val cells: List<AppBrowserReaderTableCell>,
)

internal data class AppBrowserReaderTableCell(
    val content: TextDocument,
    val isHeader: Boolean = false,
    val columnSpan: Int = 1,
    val rowSpan: Int = 1,
    val alignment: AppBrowserReaderTableCellAlignment = AppBrowserReaderTableCellAlignment.Start,
)

internal enum class AppBrowserReaderTableCellAlignment {
    Start,
    Center,
    End,
}

internal fun buildPlainAppBrowserReaderDocument(
    url: String,
    title: String,
    bodyText: String,
    sourceName: String? = null,
    excerpt: String? = null,
    byline: String? = null,
    publishedTime: String? = null,
    heroImageUrl: String? = null,
): AppBrowserReaderDocument {
    val blocks = bodyText
        .split(Regex("""\n\s*\n"""))
        .map(String::trim)
        .filter(String::isNotBlank)
        .map { paragraph ->
            AppBrowserReaderBlock.Paragraph(
                content = TextDocument(
                    text = paragraph,
                    entities = emptyList(),
                ),
            )
        }
    return AppBrowserReaderDocument(
        url = url,
        title = title,
        sourceName = sourceName,
        excerpt = excerpt,
        byline = byline,
        publishedTime = publishedTime,
        heroImageUrl = heroImageUrl,
        blocks = blocks,
    )
}

internal fun AppBrowserReaderDocument.flattenBodyText(): String {
    return blocks
        .mapNotNull(::flattenReaderBlockText)
        .filter(String::isNotBlank)
        .joinToString(separator = "\n\n")
        .trim()
}

private fun flattenReaderBlockText(
    block: AppBrowserReaderBlock,
): String? {
    return when (block) {
        is AppBrowserReaderBlock.Paragraph -> block.content.text
        is AppBrowserReaderBlock.Heading -> block.content.text
        is AppBrowserReaderBlock.Quote -> block.blocks
            .mapNotNull(::flattenReaderBlockText)
            .filter(String::isNotBlank)
            .joinToString(separator = "\n\n")
            .takeIf(String::isNotBlank)
        is AppBrowserReaderBlock.PullQuote -> listOfNotNull(
            block.content.text.trim().takeIf(String::isNotBlank),
            block.attribution?.text?.trim()?.takeIf(String::isNotBlank),
        ).joinToString(separator = "\n")
            .takeIf(String::isNotBlank)
        is AppBrowserReaderBlock.Image -> listOfNotNull(
            block.alt?.trim()?.takeIf(String::isNotBlank),
            block.caption?.text?.trim()?.takeIf(String::isNotBlank),
            block.credit?.text?.trim()?.takeIf(String::isNotBlank),
        ).joinToString(separator = "\n")
            .takeIf(String::isNotBlank)
        is AppBrowserReaderBlock.Details -> listOfNotNull(
            block.summary.text.trim().takeIf(String::isNotBlank),
            block.children
                .mapNotNull(::flattenReaderBlockText)
                .filter(String::isNotBlank)
                .joinToString(separator = "\n\n")
                .takeIf(String::isNotBlank),
        ).joinToString(separator = "\n\n")
            .takeIf(String::isNotBlank)
        is AppBrowserReaderBlock.Table -> listOfNotNull(
            block.title?.text?.trim()?.takeIf(String::isNotBlank),
            block.rows.joinToString(separator = "\n") { row ->
                row.cells.joinToString(separator = " | ") { cell ->
                    cell.content.text.trim()
                }
            }.takeIf(String::isNotBlank),
        ).joinToString(separator = "\n")
            .takeIf(String::isNotBlank)
        is AppBrowserReaderBlock.ListBlock -> block.items
            .mapNotNull { item ->
                item.blocks
                    .mapNotNull(::flattenReaderBlockText)
                    .filter(String::isNotBlank)
                    .joinToString(separator = "\n")
                    .takeIf(String::isNotBlank)
            }
            .filter(String::isNotBlank)
            .joinToString(separator = "\n")
            .takeIf(String::isNotBlank)
        is AppBrowserReaderBlock.CodeBlock -> block.code.trim().takeIf(String::isNotBlank)
        AppBrowserReaderBlock.Divider -> null
    }
}
