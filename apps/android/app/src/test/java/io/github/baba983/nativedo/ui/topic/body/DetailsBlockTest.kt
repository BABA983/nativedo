package io.github.baba983.nativedo.ui.topic.body

import android.view.View
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.html.CookedPostBlock
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.tgnet.TLRPC

@RunWith(RobolectricTestRunner::class)
class DetailsBlockTest {
    @org.junit.Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun detailsHeaderGeometry_matchesTelegramBlockDetailsCell() {
        assertThat(DetailsBlock.titleTextXPx()).isEqualTo(AndroidUtilities.dp(44f + 6f))
        assertThat(DetailsBlock.minHeaderHeightPx()).isEqualTo(AndroidUtilities.dp(39f))
        assertThat(DetailsBlock.titleWidthInsetPx()).isEqualTo(AndroidUtilities.dp(36f + 16f))
        assertThat(DetailsBlock.titleBaselineExtraHeightPx()).isEqualTo(AndroidUtilities.dp(21f))
        assertThat(DetailsBlock.arrowLeftPx()).isEqualTo(AndroidUtilities.dp(18f))
        assertThat(DetailsBlock.arrowSizePx()).isEqualTo(AndroidUtilities.dp(13f))
    }

    @Test
    fun detailsChildrenGeometry_matchesTelegramDetailsChildRows() {
        assertThat(DetailsBlock.childLevelInsetPx()).isEqualTo(AndroidUtilities.dp(14f))
        assertThat(DetailsBlock.childContentLeftPx()).isEqualTo(AndroidUtilities.dp(18f))
        assertThat(DetailsBlock.childRightInsetPx()).isEqualTo(AndroidUtilities.dp(18f))
    }

    @Test
    fun detailsChildRowCodeBlock_usesFullWidthLikeBlockPreformattedCell() {
        val code = io.github.baba983.nativedo.ui.topic.body.CodeBlock("{}", buildContext())
        val paragraph = ParagraphBlock("<p>x</p>", buildContext())

        assertThat(DetailsBlock.usesFullWidthDetailsChildRow(code)).isTrue()
        assertThat(DetailsBlock.usesFullWidthDetailsChildRow(paragraph)).isFalse()
    }

    @Test
    fun expandedHeight_doesNotAddInactiveTelegramDetailsBottomSpacer() {
        val headerHeight = AndroidUtilities.dp(39f) + 1
        val childrenHeight = AndroidUtilities.dp(80f)

        assertThat(DetailsBlock.expandedHeightPx(headerHeight, childrenHeight))
            .isEqualTo(headerHeight + childrenHeight)
        assertThat(DetailsBlock.expandedHeightPx(headerHeight, childrenHeight))
            .isNotEqualTo(headerHeight + childrenHeight + 1 + AndroidUtilities.dp(4f))
    }

    @Test
    fun pageBlockBridge_preservesNestedInstantViewBlocks() {
        val details = CookedPostBlock.Details(
            html = "<p>fallback</p>",
            summaryHtml = "<strong>Summary</strong>",
            children = listOf(
                CookedPostBlock.ListItems(
                    ordered = true,
                    items = listOf(
                        CookedPostBlock.ListItem(
                            html = "",
                            blocks = listOf(
                                CookedPostBlock.Text("<p>Nested <em>text</em></p>"),
                                CookedPostBlock.Image(
                                    src = "/uploads/default/original/1x/a.png",
                                    alt = "diagram",
                                    href = null,
                                    widthPx = 640,
                                    heightPx = 320,
                                    captionHtml = "<span>Caption</span>",
                                ),
                            ),
                        ),
                    ),
                ),
                CookedPostBlock.Table(
                    headers = listOf(
                        CookedPostBlock.TableCell(
                            text = "Name",
                            html = "<strong>Name</strong>",
                            align = CookedPostBlock.TableCellAlign.Center,
                        ),
                    ),
                    rows = listOf(
                        listOf(
                            CookedPostBlock.TableCell(
                                text = "Value",
                                html = "<code>Value</code>",
                                colspan = 2,
                                align = CookedPostBlock.TableCellAlign.End,
                            ),
                        ),
                    ),
                ),
                CookedPostBlock.Collage(
                    images = listOf(
                        CookedPostBlock.Image("/uploads/a.jpg", "A", null, widthPx = 100, heightPx = 80),
                        CookedPostBlock.Image("/uploads/b.jpg", "B", null, widthPx = 120, heightPx = 90),
                    ),
                ),
                CookedPostBlock.Quote(
                    title = "alice",
                    titleHtml = "<a href=\"/u/alice\">alice</a>",
                    html = "<p>quoted</p>",
                    level = 1,
                ),
                CookedPostBlock.Rule,
            ),
        )

        val pageBlock = DetailsBlock(details, buildContext(), PostBodyBlockState(), 0, emptyList(), PostBodyBlockState(), "details")
            .currentBlockViaReflection()

        assertThat((pageBlock.title as TLRPC.TL_textPlain).text).isEqualTo("Summary")

        val list = pageBlock.blocks[0] as TLRPC.TL_pageBlockList
        assertThat(list.ordered).isTrue()
        val itemBlocks = list.items.single() as TLRPC.TL_pageListItemBlocks
        assertThat(itemBlocks.blocks[0]).isInstanceOf(TLRPC.TL_pageBlockParagraph::class.java)
        val nestedPhoto = itemBlocks.blocks[1] as TLRPC.TL_pageBlockPhoto
        assertThat(nestedPhoto.url).isEqualTo("/uploads/default/original/1x/a.png")
        assertThat(nestedPhoto.w).isEqualTo(640)
        assertThat(nestedPhoto.h).isEqualTo(320)
        assertThat((nestedPhoto.caption.text as TLRPC.TL_textPlain).text).isEqualTo("Caption")

        val table = pageBlock.blocks[1] as TLRPC.TL_pageBlockTable
        assertThat(table.bordered).isTrue()
        assertThat(table.striped).isTrue()
        assertThat(table.rows).hasSize(2)
        assertThat(table.rows[0].cells.single().header).isTrue()
        assertThat(table.rows[0].cells.single().align_center).isTrue()
        assertThat(table.rows[1].cells.single().align_right).isTrue()
        assertThat(table.rows[1].cells.single().colspan).isEqualTo(2)

        val collage = pageBlock.blocks[2] as TLRPC.TL_pageBlockCollage
        assertThat(collage.items).hasSize(2)
        assertThat(collage.items[0]).isInstanceOf(TLRPC.TL_pageBlockPhoto::class.java)

        val quote = pageBlock.blocks[3] as TLRPC.TL_pageBlockBlockquote
        assertThat((quote.caption as TLRPC.TL_textPlain).text).isEqualTo("alice")
        assertThat(quote.level).isEqualTo(1)

        assertThat(pageBlock.blocks[4]).isInstanceOf(TLRPC.TL_pageBlockDivider::class.java)
    }

    private fun DetailsBlock.currentBlockViaReflection(): TLRPC.TL_pageBlockDetails {
        @Suppress("UNCHECKED_CAST")
        return DetailsBlock::class.java.getDeclaredMethod("getCurrentBlock")
            .apply { isAccessible = true }
            .invoke(this) as TLRPC.TL_pageBlockDetails
    }

    private fun buildContext(): PostBodyBuildContext {
        return PostBodyBuildContext(
            host = View(RuntimeEnvironment.getApplication()),
            baseUrl = "https://linux.do/",
            delegate = object : PostBodyDelegate {
                override fun onLinkClick(url: String) = Unit
                override fun onTopicClick(topicId: Long) = Unit
                override fun onUserClick(username: String) = Unit
                override fun onPollOptionsSelected(pollName: String, optionIds: List<String>) = Unit
            },
            textColor = 0xFF111111.toInt(),
            secondaryTextColor = 0xFF666666.toInt(),
            accentColor = 0xFF1677FF.toInt(),
            chipBackgroundColor = 0x221677FF,
            dividerColor = 0xFFE0E0E0.toInt(),
        )
    }
}
