package io.github.baba983.nativedo.core.html

internal sealed interface CookedPostBlock {
    data class Text(val html: String) : CookedPostBlock
    data class Heading(val level: Int, val html: String) : CookedPostBlock
    data class Quote(
        val title: String?,
        val titleHtml: String? = null,
        /** Resolved absolute URL for Discourse `.title img.avatar`, when present. */
        val titleAvatarUrl: String? = null,
        val titleCategoryName: String? = null,
        val titleCategoryColor: String? = null,
        val titleCategoryTextColor: String? = null,
        val titleCategoryHref: String? = null,
        val html: String,
        val children: List<CookedPostBlock> = emptyList(),
        val sourceTopicId: Long? = null,
        val sourcePostNumber: Int? = null,
        val level: Int = 0,
    ) : CookedPostBlock
    data class Callout(
        val type: String,
        val titleHtml: String?,
        val icon: String,
        val color: String,
        val html: String,
        val children: List<CookedPostBlock> = emptyList(),
        val collapsible: Boolean = false,
        val defaultExpanded: Boolean = true,
    ) : CookedPostBlock
    data class Code(val code: String) : CookedPostBlock
    data class ListItem(
        val html: String,
        val blocks: List<CookedPostBlock> = emptyList(),
    )
    data class ListItems(val ordered: Boolean, val items: List<ListItem>) : CookedPostBlock
    data class Image(
        val src: String,
        val alt: String,
        val href: String?,
        val widthPx: Int = 0,
        val heightPx: Int = 0,
        val captionHtml: String? = null,
    ) : CookedPostBlock {
        val aspectRatio: Float? = widthPx
            .takeIf { it > 0 }
            ?.let { width ->
                heightPx.takeIf { it > 0 }?.let { height -> width.toFloat() / height.toFloat() }
            }
    }

    data class Collage(val images: List<Image>) : CookedPostBlock

    data class Onebox(
        val href: String,
        val domain: String?,
        val siteIconUrl: String?,
        val thumbnailUrl: String?,
        val thumbnailWidthPx: Int = 0,
        val thumbnailHeightPx: Int = 0,
        val previewType: String? = null,
        val forceLargeMedia: Boolean = false,
        val forceSmallMedia: Boolean = false,
        val title: String,
        val titleHtml: String? = null,
        val description: String?,
        val descriptionHtml: String? = null,
    ) : CookedPostBlock

    enum class TableCellAlign {
        Start,
        Center,
        End,
    }

    data class TableCell(
        val text: String,
        val html: String,
        val blocks: List<CookedPostBlock> = emptyList(),
        val colspan: Int = 1,
        val rowspan: Int = 1,
        val align: TableCellAlign = TableCellAlign.Start,
    )

    data class Table(
        val headers: List<TableCell>,
        val rows: List<List<TableCell>>,
    ) : CookedPostBlock

    data class PollOption(
        val id: String?,
        val label: String,
        val votes: Int? = null,
        val percent: Float? = null,
        val selected: Boolean = false,
        val labelHtml: String? = null,
    )

    data class Poll(
        val name: String?,
        val type: String?,
        val status: String?,
        val voters: Int?,
        val options: List<PollOption>,
        val results: String? = null,
        val publicPoll: Boolean? = null,
        val chartType: String? = null,
        val title: String? = null,
    ) : CookedPostBlock

    data class Details(
        val html: String,
        val summary: String? = null,
        val summaryHtml: String? = null,
        val children: List<CookedPostBlock> = emptyList(),
        val open: Boolean = false,
    ) : CookedPostBlock

    data object Rule : CookedPostBlock
}

data class GalleryImage(
    val url: String,
    val thumbUrl: String?,
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    val caption: String? = null,
)
