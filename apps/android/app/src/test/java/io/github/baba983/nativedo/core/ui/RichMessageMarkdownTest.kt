package io.github.baba983.nativedo.core.ui

import android.text.SpannableStringBuilder
import android.text.Spanned
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.nativedo.RichMediaUploadBridge
import org.telegram.tgnet.tl.TL_iv
import org.telegram.tgnet.tl.TL_keyboard
import org.telegram.ui.Components.TextStyleSpan
import org.telegram.ui.iv.BlockRow
import org.telegram.ui.iv.RichTextStyle
import org.telegram.ui.iv.TableModel

/**
 * The block editor's document as Discourse markdown: the model is Telegram's ([TL_iv] blocks in
 * [BlockRow]s, which carry list nesting, quote nesting and the details span), the output is what
 * `POST /posts.json` stores as `raw`. Each test pins one block's wire form.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class RichMessageMarkdownTest {

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    private fun styled(text: String, flags: Int = 0): TL_iv.RichText {
        val sb = SpannableStringBuilder(text)
        if (flags != 0) {
            val run = TextStyleSpan.TextStyleRun().apply { this.flags = flags }
            sb.setSpan(TextStyleSpan(run), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return RichTextStyle.fromSpannable(sb)
    }

    private fun paragraph(text: String): TL_iv.pageBlockParagraph =
        TL_iv.pageBlockParagraph().apply { this.text = styled(text) }

    private fun markdown(vararg blocks: TL_iv.PageBlock): String =
        RichMessageMarkdown.blocksToMarkdown(blocks.toList())

    private fun markdownRows(rows: List<BlockRow>): String = RichMessageMarkdown.toMarkdown(rows)

    @Test
    fun inlineStylesAndLinksKeepTheirMarkers() {
        val sb = SpannableStringBuilder("bold and link")
        val run = TextStyleSpan.TextStyleRun().apply { flags = RichTextStyle.BOLD }
        sb.setSpan(TextStyleSpan(run), 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val linkRun = TextStyleSpan.TextStyleRun().apply { flags = TextStyleSpan.FLAG_STYLE_TEXT_URL }
        sb.setSpan(org.telegram.ui.Components.URLSpanReplacement("https://linux.do/t/x", linkRun), 9, 13, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val block = TL_iv.pageBlockParagraph().apply { text = RichTextStyle.fromSpannable(sb) }

        assertThat(markdown(block)).isEqualTo("**bold** and [link](https://linux.do/t/x)")
    }

    @Test
    fun headingsBecomeHashes() {
        val heading = TL_iv.pageBlockHeading2().apply { text = styled("Title") }
        assertThat(markdown(heading)).isEqualTo("## Title")
    }

    @Test
    fun listsCarryTheirKindNestingAndChecks() {
        val rows = listOf(
            BlockRow(paragraph("one"), 1, 0),
            BlockRow(paragraph("two"), 2, 0),
            BlockRow(paragraph("three"), 1, 3),
            BlockRow(paragraph("task"), 1, 0).apply { checkbox = true; checked = true },
        )

        assertThat(markdownRows(rows))
            .isEqualTo("- one\n    - two\n3. three\n- [x] task")
    }

    @Test
    fun quoteNestingBecomesRepeatedQuoteMarkers() {
        val quoted = BlockRow(paragraph("quoted"), 0, 0).apply { quoteIds.add(7L) }
        val nested = BlockRow(paragraph("deeper"), 0, 0).apply { quoteIds.add(7L); quoteIds.add(8L) }

        assertThat(markdownRows(listOf(quoted))).isEqualTo("> quoted")
        assertThat(markdownRows(listOf(nested))).isEqualTo("> > deeper")
    }

    @Test
    fun aQuotedBlockWithAnAuthorBecomesADiscourseQuote() {
        val quote = TL_iv.pageBlockBlockquote().apply {
            text = styled("quoted text")
            caption = styled("alice")
        }

        assertThat(markdown(quote)).isEqualTo("[quote=\"alice\"]\nquoted text\n[/quote]")
    }

    @Test
    fun preformattedBecomesAFencedBlockWithItsLanguage() {
        val pre = TL_iv.pageBlockPreformatted().apply {
            text = styled("fun main() {}")
            language = "kotlin"
        }

        assertThat(markdown(pre)).isEqualTo("```kotlin\nfun main() {}\n```")
    }

    @Test
    fun aTableBecomesAMarkdownTableWithAlignment() {
        val table = TL_iv.pageBlockTable()
        val header = TL_iv.pageTableRow()
        header.cells.add(TL_iv.pageTableCell().apply { TableModel.applyPlainText(this, "Head") })
        header.cells.add(TL_iv.pageTableCell().apply { align_center = true; TableModel.applyPlainText(this, "Center") })
        table.rows.add(header)
        val body = TL_iv.pageTableRow()
        body.cells.add(TL_iv.pageTableCell().apply { TableModel.applyPlainText(this, "a") })
        body.cells.add(TL_iv.pageTableCell().apply { TableModel.applyPlainText(this, "b") })
        table.rows.add(body)

        assertThat(markdown(table)).isEqualTo("| Head | Center |\n| --- | :-: |\n| a | b |")
    }

    @Test
    fun detailsAreDelimitedByTheirTags() {
        val header = TL_iv.pageBlockDetails().apply { title = styled("Summary") }
        val rows = listOf(
            BlockRow(header),
            BlockRow(paragraph("inside")),
            BlockRow(paragraph("")).apply { detailsEnd = true },
        )

        val markdown = markdownRows(rows)

        assertThat(markdown).contains("[details=\"Summary\"]")
        assertThat(markdown).contains("inside")
        assertThat(markdown).contains("[/details]")
    }

    @Test
    fun dividerAndMathUseTheirSyntax() {
        assertThat(markdown(TL_iv.pageBlockDivider())).isEqualTo("---")
        val math = TL_iv.pageBlockMath().apply { source = "x^2" }
        assertThat(markdown(math)).isEqualTo("${'$'}${'$'}x^2${'$'}${'$'}")
    }

    @Test
    fun mediaBlocksUseTheUploadedUrl() {
        val url = "https://linux.do/uploads/original/1/x.png"
        val id = RichMediaUploadBridge.registerUrl(url)
        val photo = TL_iv.pageBlockPhoto().apply { photo_id = id }
        assertThat(markdown(photo)).isEqualTo("![]($url)")

        val captioned = TL_iv.pageBlockPhoto().apply {
            photo_id = id
            caption = TL_iv.PageCaption().apply { text = styled("a caption") }
        }
        assertThat(markdown(captioned)).isEqualTo("![a caption]($url)\n\n*a caption*")

        val videoUrl = "https://linux.do/uploads/original/1/clip.mp4"
        val videoId = RichMediaUploadBridge.registerUrl(videoUrl)
        val video = TL_iv.pageBlockVideo().apply { video_id = videoId }
        assertThat(markdown(video)).isEqualTo("[video.mp4]($videoUrl)")

        RichMediaUploadBridge.releaseId(id)
        RichMediaUploadBridge.releaseId(videoId)
    }

    @Test
    fun aPhotoWithoutAnUploadIdFallsBackToItsOwnUrl() {
        val photo = TL_iv.pageBlockPhoto().apply { url = "https://linux.do/uploads/original/2/y.jpg" }
        assertThat(markdown(photo)).isEqualTo("![](https://linux.do/uploads/original/2/y.jpg)")
    }

    @Test
    fun aCollageBecomesAdjacentImages() {
        val collage = TL_iv.pageBlockCollage()
        collage.items.add(TL_iv.pageBlockPhoto().apply { url = "https://x/1.png" })
        collage.items.add(TL_iv.pageBlockPhoto().apply { url = "https://x/2.png" })

        assertThat(markdown(collage)).isEqualTo("![](https://x/1.png)\n![](https://x/2.png)")
    }

    @Test
    fun aUrlButtonRowLinksItsButtons() {
        val row = TL_iv.pageBlockButtonRow()
        val button = TL_keyboard.PageButton().apply {
            text = styled("Visit")
            type = TL_keyboard.TL_inlineButtonTypeUrl().apply { url = "https://linux.do" }
        }
        row.buttons.add(button)

        assertThat(markdown(row)).isEqualTo("[Visit](https://linux.do)")
    }

    // ---------------------------------------------------------------- reader

    /** The editor's own blocks → rows step, then our writer: the pipeline the editor will use. */
    private fun viaEditor(markdown: String): String {
        val authors = HashMap<Long, TL_iv.RichText>()
        val message = RichMessageMarkdown.parse(markdown)
        val rows = org.telegram.ui.iv.RichEditorListView.flattenForCopy(message, authors)
        return RichMessageMarkdown.toMarkdown(rows, RichMediaUploadBridge::urlForId, authors)
    }

    private fun rows(markdown: String): List<BlockRow> =
        org.telegram.ui.iv.RichEditorListView.flattenForCopy(RichMessageMarkdown.parse(markdown))

    private fun blockTypes(markdown: String): List<String> =
        RichMessageMarkdown.parse(markdown).blocks.map { it.javaClass.simpleName }

    @Test
    fun theReaderTakesBackWhatTheWriterWrote() {
        val rows = listOf(
            BlockRow(paragraph("plain paragraph")),
            BlockRow(TL_iv.pageBlockHeading2().apply { text = styled("Title") }),
            BlockRow(paragraph("one"), 1, 0),
            BlockRow(paragraph("nested"), 2, 0),
            BlockRow(paragraph("third"), 1, 3),
            BlockRow(paragraph("task"), 1, 0).apply { checkbox = true; checked = true },
            BlockRow(paragraph("quoted"), 0, 0).apply { quoteIds.add(7L) },
        )
        val markdown = RichMessageMarkdown.toMarkdown(rows)

        // `expandListBlock` numbers an ordered list from 1 (upstream), so a hand-numbered row comes
        // back as `1.` - every other row is byte-for-byte what the writer wrote.
        assertThat(viaEditor(markdown)).isEqualTo(markdown.replace("3. third", "1. third"))
    }

    @Test
    fun complexBlocksSurviveTheRoundTrip() {
        val pre = TL_iv.pageBlockPreformatted().apply { text = styled("fun main() {}"); language = "kotlin" }
        val quote = TL_iv.pageBlockBlockquote().apply {
            text = styled("quoted text")
            caption = styled("alice")
        }
        val table = TL_iv.pageBlockTable()
        val header = TL_iv.pageTableRow()
        header.cells.add(TL_iv.pageTableCell().apply { TableModel.applyPlainText(this, "Head") })
        header.cells.add(TL_iv.pageTableCell().apply { align_center = true; TableModel.applyPlainText(this, "Center") })
        table.rows.add(header)
        val body = TL_iv.pageTableRow()
        body.cells.add(TL_iv.pageTableCell().apply { TableModel.applyPlainText(this, "a") })
        body.cells.add(TL_iv.pageTableCell().apply { TableModel.applyPlainText(this, "b") })
        table.rows.add(body)
        val photo = TL_iv.pageBlockPhoto().apply { url = "https://linux.do/uploads/original/9/k.png" }
        val math = TL_iv.pageBlockMath().apply { source = "x^2" }

        val markdown = RichMessageMarkdown.blocksToMarkdown(
            listOf(pre, quote, table, photo, math, TL_iv.pageBlockDivider()),
        )
        val again = RichMessageMarkdown.blocksToMarkdown(RichMessageMarkdown.parse(markdown).blocks)

        assertThat(again).isEqualTo(markdown)
    }

    @Test
    fun theReaderRecognisesDiscourseFlavouredMarkdown() {
        val sample = """
            # Title

            Intro with **bold** and [a link](https://linux.do/t/x).

            - one
                - nested
            - [x] done

            1. first
            2. second

            > quoted line

            [quote="alice"]
            quoted text
            [/quote]

            [details="Summary"]

            inside details

            [/details]

            ```kotlin
            fun main() {}
            ```

            | Head | Center |
            | --- | :-: |
            | a | b |

            ---

            ${'$'}${'$'}x^2${'$'}${'$'}

            ![a caption](https://linux.do/uploads/1.png)

            *a caption*

            [clip.mp4](https://linux.do/uploads/clip.mp4)
        """.trimIndent()

        val types = blockTypes(sample)
        assertThat(types).containsAtLeast(
            "pageBlockHeading1", "pageBlockParagraph", "pageBlockList", "pageBlockOrderedList",
            "pageBlockBlockquote", "pageBlockBlockquoteBlocks", "pageBlockDetails",
            "pageBlockPreformatted", "pageBlockTable", "pageBlockDivider", "pageBlockMath",
            "pageBlockPhoto", "pageBlockVideo",
        ).inOrder()

        val markdown = viaEditor(sample)
        assertThat(markdown).contains("**bold**")
        assertThat(markdown).contains("[a link](https://linux.do/t/x)")
        assertThat(markdown).contains("- one")
        assertThat(markdown).contains("    - nested")
        assertThat(markdown).contains("- [x] done")
        assertThat(markdown).contains("1. first")
        assertThat(markdown).contains("2. second")
        assertThat(markdown).contains("> quoted line")
        assertThat(markdown).contains("[quote=\"alice\"]")
        assertThat(markdown).contains("[details=\"Summary\"]")
        assertThat(markdown).contains("```kotlin")
        assertThat(markdown).contains("| Head | Center |")
        assertThat(markdown).contains("| --- | :-: |")
        assertThat(markdown).contains("---")
        assertThat(markdown).contains("${'$'}${'$'}x^2${'$'}${'$'}")
        assertThat(markdown).contains("![a caption](https://linux.do/uploads/1.png)")
        assertThat(markdown).contains("[clip.mp4](https://linux.do/uploads/clip.mp4)")
    }

    @Test
    fun theReaderKeepsListNestingQuotesAndDetails() {
        val sample = """
            - one
                - nested
            - two

            > quoted

            [details="Sum"]

            inside

            [/details]
        """.trimIndent()

        val rows = rows(sample)

        assertThat(rows[0].level).isEqualTo(1)
        assertThat(rows[1].level).isEqualTo(2)
        assertThat(rows[2].level).isEqualTo(1)
        // a one-paragraph `>` run is the leaf block, so the row itself carries the quote
        assertThat(rows[3].block).isInstanceOf(TL_iv.pageBlockBlockquote::class.java)
        assertThat(rows[4].block).isInstanceOf(TL_iv.pageBlockDetails::class.java)
        assertThat(rows.last().detailsEnd).isTrue()
        assertThat(viaEditor(sample)).contains("> quoted")
    }

    @Test
    fun theReaderUsesTheAltAsTheImageCaption() {
        val block = RichMessageMarkdown.parse("![a caption](https://linux.do/uploads/1.png)")
            .blocks.single() as TL_iv.pageBlockPhoto

        assertThat(block.url).isEqualTo("https://linux.do/uploads/1.png")
        assertThat(RichTextStyle.plainOf(block.caption!!.text)).isEqualTo("a caption")
    }
}
