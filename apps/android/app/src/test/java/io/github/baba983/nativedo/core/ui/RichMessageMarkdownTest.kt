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
}
