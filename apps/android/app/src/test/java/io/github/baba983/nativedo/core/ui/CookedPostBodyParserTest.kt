package io.github.baba983.nativedo.core.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.html.CookedPostBlock
import io.github.baba983.nativedo.core.html.parseCookedPostBody
import org.junit.Test

class CookedPostBodyParserTest {
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
        assertThat(quote.sourceTopicId).isEqualTo(424242L)
        assertThat(quote.sourcePostNumber).isEqualTo(1)
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
    fun parseCookedPostBody_preservesPollResultState() {
        val html = """
            <div class="poll" data-poll-name="frontend" data-poll-status="open" data-poll-type="regular">
              <div class="poll-container">
                <ul>
                  <li data-poll-option-id="option-a" class="chosen">
                    <span class="poll-option-label">Tool A</span>
                    <span class="poll-option-votes">7 votes</span>
                    <span class="poll-option-percent">70%</span>
                  </li>
                  <li data-poll-option-id="option-b">
                    <span class="poll-option-label">Tool B</span>
                    <span class="poll-option-votes">3 votes</span>
                    <span class="poll-option-percent">30%</span>
                  </li>
                </ul>
              </div>
              <div class="poll-info"><div class="poll-info_counts"><div class="poll-info_counts-count"><span class="info-number">10</span></div></div></div>
            </div>
        """.trimIndent()

        val poll = parseCookedPostBody(html, baseUrl)
            .filterIsInstance<CookedPostBlock.Poll>()
            .single()

        assertThat(poll.name).isEqualTo("frontend")
        assertThat(poll.status).isEqualTo("open")
        assertThat(poll.type).isEqualTo("regular")
        assertThat(poll.voters).isEqualTo(10)
        assertThat(poll.options.map { it.id }).containsExactly("option-a", "option-b").inOrder()
        assertThat(poll.options.map { it.label }).containsExactly("Tool A", "Tool B").inOrder()
        assertThat(poll.options.map { it.votes }).containsExactly(7, 3).inOrder()
        assertThat(poll.options.map { it.percent }).containsExactly(0.7f, 0.3f).inOrder()
        assertThat(poll.options.first().selected).isTrue()
        assertThat(poll.options.last().selected).isFalse()
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

    private fun resourceText(path: String): String {
        val url = checkNotNull(javaClass.classLoader?.getResource(path)) { "Missing test resource $path" }
        return url.readText()
    }
}
