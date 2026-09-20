package io.github.baba983.nativedo.core.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.html.CookedPostBlock
import org.junit.Test

class PostRichMessageSegmentTest {

    @Test
    fun runsWrapMappableBlocksAndKeepTheDiscourseOnlyBlocksInPlace() {
        val segments = splitPostBodySegments(
            listOf(
                text("one"),
                text("two"),
                poll("frontend"),
                text("three"),
                onebox("https://developers.example.dev/tooling/cli"),
                text("four"),
            ),
        )

        assertThat(segments).hasSize(5)
        assertThat((segments[0] as PostBodySegment.RichRun).blocks).hasSize(2)
        assertThat((segments[1] as PostBodySegment.OwnBlock).block).isEqualTo(poll("frontend"))
        assertThat((segments[2] as PostBodySegment.RichRun).blocks).hasSize(1)
        assertThat((segments[3] as PostBodySegment.OwnBlock).block)
            .isEqualTo(onebox("https://developers.example.dev/tooling/cli"))
        assertThat((segments[4] as PostBodySegment.RichRun).blocks).hasSize(1)
    }

    @Test
    fun aBodyOfOnlyDiscourseOnlyBlocksHasNoRichRun() {
        val segments = splitPostBodySegments(listOf(poll("first"), callout()))

        assertThat(segments).hasSize(2)
        assertThat(segments.all { it is PostBodySegment.OwnBlock }).isTrue()
    }

    @Test
    fun aContainerHoldingADiscourseOnlyBlockFallsBackAsAWhole() {
        val details = CookedPostBlock.Details(
            html = "<p>inside</p>",
            summary = "More",
            children = listOf(text("inside"), poll("nested")),
        )
        val quote = CookedPostBlock.Quote(
            title = "alice",
            html = "",
            children = listOf(onebox("https://example.dev")),
        )

        assertThat(PostRichMessageBuilder.isMappable(details)).isFalse()
        assertThat(PostRichMessageBuilder.isMappable(quote)).isFalse()
        assertThat(PostRichMessageBuilder.pageBlock(details)).isNull()
        assertThat(PostRichMessageBuilder.pageBlock(quote)).isNull()

        val segments = splitPostBodySegments(listOf(text("before"), details, quote, text("after")))
        assertThat(segments.map { segment -> segment::class.java }).containsExactly(
            PostBodySegment.RichRun::class.java,
            PostBodySegment.OwnBlock::class.java,
            PostBodySegment.OwnBlock::class.java,
            PostBodySegment.RichRun::class.java,
        ).inOrder()
    }

    /**
     * The mapper can express a quote, but the hybrid must not take it: only `QuoteBlock` draws the
     * card (bar, quoted author, topic title, category chip, collapse), which is what the device pass
     * found missing when the rich run drew the quote instead.
     */
    @Test
    fun aPlainQuoteStaysInOurBlockTreeForItsCard() {
        val quote = CookedPostBlock.Quote(title = "alice", html = "<p>quoted</p>", children = emptyList())

        assertThat(PostRichMessageBuilder.isMappable(quote)).isTrue()
        assertThat(PostRichMessageBuilder.pageBlock(quote)).isNotNull()
        assertThat(isOurBlock(quote)).isTrue()

        val segments = splitPostBodySegments(listOf(text("before"), quote, text("after")))
        assertThat(segments.map { segment -> segment::class.java }).containsExactly(
            PostBodySegment.RichRun::class.java,
            PostBodySegment.OwnBlock::class.java,
            PostBodySegment.RichRun::class.java,
        ).inOrder()
        assertThat((segments[1] as PostBodySegment.OwnBlock).block).isSameInstanceAs(quote)
    }

    @Test
    fun aListHoldingADiscourseOnlyItemFallsBackAsAWhole() {
        val list = CookedPostBlock.ListItems(
            ordered = false,
            items = listOf(
                CookedPostBlock.ListItem(html = "plain", blocks = listOf(text("plain"))),
                CookedPostBlock.ListItem(html = "", blocks = listOf(poll("nested"))),
            ),
        )

        assertThat(PostRichMessageBuilder.isMappable(list)).isFalse()
        assertThat(PostRichMessageBuilder.pageBlock(list)).isNull()
    }

    @Test
    fun everyBlockAppearsExactlyOnceAcrossTheSegments() {
        val blocks = listOf(
            text("a"),
            CookedPostBlock.Rule,
            poll("p"),
            text("b"),
            callout(),
        )

        val flattened = splitPostBodySegments(blocks).flatMap { segment ->
            when (segment) {
                is PostBodySegment.RichRun -> segment.blocks
                is PostBodySegment.OwnBlock -> listOf(segment.block)
            }
        }

        assertThat(flattened).isEqualTo(blocks)
    }

    private fun text(html: String) = CookedPostBlock.Text(html)

    private fun poll(name: String) = CookedPostBlock.Poll(
        name = name,
        type = "regular",
        status = "open",
        voters = 3,
        options = listOf(CookedPostBlock.PollOption(id = "a", label = "A", votes = 3)),
    )

    private fun onebox(href: String) = CookedPostBlock.Onebox(
        href = href,
        domain = "developers.example.dev",
        siteIconUrl = null,
        thumbnailUrl = null,
        title = "Title",
        description = null,
    )

    private fun callout() = CookedPostBlock.Callout(
        type = "note",
        titleHtml = "Note",
        icon = "",
        color = "",
        html = "<p>body</p>",
        children = listOf(text("body")),
    )
}
