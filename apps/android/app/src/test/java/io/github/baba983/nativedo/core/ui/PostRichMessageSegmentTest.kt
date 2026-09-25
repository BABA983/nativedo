package io.github.baba983.nativedo.core.ui

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.baba983.nativedo.core.html.CookedPostBlock
import io.github.baba983.nativedo.core.html.parseCookedPostBody
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

    /**
     * The callout fed through the real parser, not a hand-built block: the `div`/`aside` callout
     * shapes used to be read by `CookedPostBodyParser` as plain `Text`, which the hybrid body then
     * handed to upstream's renderer and lost the callout card entirely. A callout parsed from cooked
     * HTML must land as an [PostBodySegment.OwnBlock].
     */
    @Test
    fun calloutsParsedFromRealCookedHtmlStayOurOwnBlock() {
        val shapes = listOf(
            "div" to
                """
                    <div class="callout callout-note" data-callout-type="note">
                      <div class="callout-title"><span class="callout-title-inner">Note</span></div>
                      <div class="callout-content"><p>Callouts stay our own block.</p></div>
                    </div>
                """.trimIndent(),
            "aside" to
                """
                    <aside class="callout callout-warning" data-callout-type="warning">
                      <div class="callout-title"><span class="callout-title-inner">Warning</span></div>
                      <div class="callout-content"><p>Callouts stay our own block.</p></div>
                    </aside>
                """.trimIndent(),
        )

        for ((shape, calloutHtml) in shapes) {
            val parsed = parseCookedPostBody("<p>before</p>$calloutHtml<p>after</p>", "https://linux.do/")
            val callout = parsed.filterIsInstance<CookedPostBlock.Callout>().single()

            val segments = splitPostBodySegments(parsed)
            assertThat(segments.map { segment -> segment::class.java }).containsExactly(
                PostBodySegment.RichRun::class.java,
                PostBodySegment.OwnBlock::class.java,
                PostBodySegment.RichRun::class.java,
            ).inOrder()
            assertWithMessage("$shape callout block")
                .that((segments[1] as PostBodySegment.OwnBlock).block)
                .isEqualTo(callout)
        }
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
