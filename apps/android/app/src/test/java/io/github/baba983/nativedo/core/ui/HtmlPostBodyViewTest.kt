package io.github.baba983.nativedo.core.ui

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HtmlPostBodyViewTest {
    private val baseUrl = "https://linux.do/"

    @Test
    fun parseCookedPostBody_parsesParagraphCodeLinkCardAndImage() {
        val html = resourceText("discourse/cooked-codeblock-onebox.html")

        val blocks = parseCookedPostBody(html, baseUrl)

        assertThat(blocks).contains(CookedPostBlock.Text("Public service endpoint notes:"))
        assertThat(blocks).contains(CookedPostBlock.Code("docker run --rm example/service:latest"))
        val onebox = blocks.filterIsInstance<CookedPostBlock.Onebox>().single()
        assertThat(onebox.href).isEqualTo("https://developers.example.dev/tooling/cli")
        assertThat(onebox.domain).isEqualTo("developers.example.dev")
        assertThat(onebox.title).contains("CLI")
        assertThat(onebox.thumbnailUrl).isEqualTo("https://cdn.example.dev/thumbnail.jpeg")
        assertThat(onebox.siteIconUrl).isEqualTo("https://cdn.example.dev/site-icon.png")
        assertThat(onebox.description).contains("Command-line tooling")
    }

    @Test
    fun parseCookedPostBody_parsesTableHeadersAndRows() {
        val html = resourceText("discourse/cooked-table-lightbox-blockquote.html")

        val blocks = parseCookedPostBody(html, baseUrl)

        val table = blocks.filterIsInstance<CookedPostBlock.Table>().single()
        assertThat(table.headers).containsExactly("offerId", "Description").inOrder()
        assertThat(table.rows).hasSize(2)
        assertThat(table.rows[0]).containsExactly("trial-1m", "Free for 1 month").inOrder()
        assertThat(table.rows[1]).containsExactly("trial-3d", "Free for 3 days").inOrder()
    }

    @Test
    fun parseCookedPostBody_parsesQuoteAndLightboxImage() {
        val html = resourceText("discourse/cooked-quote-lightbox-callout.html")

        val blocks = parseCookedPostBody(html, baseUrl)

        val quote = blocks.filterIsInstance<CookedPostBlock.Quote>().first()
        assertThat(quote.title).contains("Please keep discussions constructive")
        assertThat(quote.html).contains("moderation team")
        val image = blocks.filterIsInstance<CookedPostBlock.Image>().single()
        assertThat(image.src)
            .contains("https://cdn.example.dev/optimized/sample-image_2_690x254.png")
        assertThat(image.widthPx).isEqualTo(690)
        assertThat(image.heightPx).isEqualTo(254)
    }

    @Test
    fun parseCookedPostBody_parsesDetailsSummaryAndList() {
        val html = resourceText("discourse/cooked-details-emoji-spoiler.html")

        val blocks = parseCookedPostBody(html, baseUrl)

        assertThat(blocks.filterIsInstance<CookedPostBlock.Text>().map { it.html })
            .contains("<p><strong>Friendly note</strong></p>")
        val list = blocks.filterIsInstance<CookedPostBlock.ListItems>().single()
        assertThat(list.ordered).isFalse()
        assertThat(list.items).hasSize(2)
        assertThat(blocks.filterIsInstance<CookedPostBlock.Image>()).isEmpty()
    }

    @Test
    fun prepareInlineContent_extractsEmojiInlineImage() {
        val prepared = prepareInlineContent("""<p>Hello <img src="/images/emoji/twemoji/wave.png" class="emoji" title=":wave:" alt=":wave:"></p>""")

        val inlineImage = prepared.inlineImages.single()
        assertThat(inlineImage.rawUrl).isEqualTo("/images/emoji/twemoji/wave.png")
        assertThat(inlineImage.contentDescription).isEqualTo(":wave:")
        assertThat(inlineImage.kind).isEqualTo(InlineImageKind.Emoji)
        assertThat(prepared.html).contains(inlineImage.token)
        assertThat(prepared.html).doesNotContain("<img")
    }

    @Test
    fun parseCookedPostBody_preservesCodeWhitespaceAndDropsEmptyBlockquote() {
        val html = """
            <p>Before</p>
            <blockquote></blockquote>
            <pre><code>fun main() {
                println("hi")
            }
            </code></pre>
        """.trimIndent()

        val blocks = parseCookedPostBody(html, baseUrl)

        assertThat(blocks.filterIsInstance<CookedPostBlock.Quote>()).isEmpty()
        assertThat(blocks.filterIsInstance<CookedPostBlock.Code>().single().code)
            .contains("    println")
    }

    @Test
    fun calculatePostImageSize_keepsTallImagesAtIntrinsicScaleWhenTheyFit() {
        val size = calculatePostImageSize(
            availableWidth = 690.dp,
            intrinsicWidthPx = 690,
            intrinsicHeightPx = 1840,
            maxHeight = 480.dp,
        )

        assertThat(size.width).isEqualTo(690.dp)
        assertThat(size.height).isEqualTo(1840.dp)
    }

    private fun resourceText(path: String): String {
        val url = checkNotNull(javaClass.classLoader?.getResource(path)) { "Missing test resource $path" }
        return url.readText()
    }
}
