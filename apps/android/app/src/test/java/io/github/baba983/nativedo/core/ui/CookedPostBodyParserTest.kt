package io.github.baba983.nativedo.core.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.html.CookedPostBlock
import io.github.baba983.nativedo.core.html.cookedGalleryImages
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
        assertThat(onebox.titleHtml).contains("href=\"https://developers.example.dev/tooling/cli\"")
        assertThat(onebox.thumbnailUrl).isEqualTo("https://cdn.example.dev/thumbnail.jpeg")
        assertThat(onebox.siteIconUrl).isEqualTo("https://cdn.example.dev/site-icon.png")
        assertThat(onebox.description).contains("Command-line tooling")
        assertThat(onebox.descriptionHtml).contains("Command-line tooling")
    }

    @Test
    fun parseCookedPostBody_preservesOneboxDescriptionHtmlForInlineSpoilers() {
        val html = """
            <aside class="onebox allowlistedgeneric" data-onebox-src="https://example.dev/post">
              <header class="source"><a href="https://example.dev/post">example.dev</a></header>
              <article class="onebox-body">
                <h3><a href="https://example.dev/post">Example</a></h3>
                <p>Visible <span class="spoiler">hidden</span> text</p>
              </article>
            </aside>
        """.trimIndent()

        val onebox = parseCookedPostBody(html, baseUrl)
            .filterIsInstance<CookedPostBlock.Onebox>()
            .single()

        assertThat(onebox.description).isEqualTo("Visible hidden text")
        assertThat(onebox.descriptionHtml).contains("class=\"spoiler\"")
    }

    @Test
    fun parseCookedPostBody_parsesTableHeadersAndRows() {
        val html = resourceText("discourse/cooked-table-lightbox-blockquote.html")

        val blocks = parseCookedPostBody(html, baseUrl)

        val table = blocks.filterIsInstance<CookedPostBlock.Table>().single()
        assertThat(table.headers.map { it.text }).containsExactly("offerId", "Description").inOrder()
        assertThat(table.rows).hasSize(2)
        assertThat(table.rows[0].map { it.text }).containsExactly("trial-1m", "Free for 1 month").inOrder()
        assertThat(table.rows[1].map { it.text }).containsExactly("trial-3d", "Free for 3 days").inOrder()
    }

    @Test
    fun parseCookedPostBody_parsesQuoteAndLightboxImage() {
        val html = resourceText("discourse/cooked-quote-lightbox-callout.html")

        val blocks = parseCookedPostBody(html, baseUrl)

        val quote = blocks.filterIsInstance<CookedPostBlock.Quote>().first()
        assertThat(quote.title).contains("Please keep discussions constructive")
        assertThat(quote.titleHtml).contains("href=\"https://community.example/t/topic/424242/1\"")
        assertThat(quote.html).contains("moderation team")
        assertThat(quote.children).isNotEmpty()
        assertThat(quote.sourceTopicId).isEqualTo(424242L)
        assertThat(quote.sourcePostNumber).isEqualTo(1)
        val image = blocks.filterIsInstance<CookedPostBlock.Image>().single()
        assertThat(image.src)
            .contains("https://cdn.example.dev/optimized/sample-image_2_690x254.png")
        assertThat(image.widthPx).isEqualTo(690)
        assertThat(image.heightPx).isEqualTo(254)
    }

    @Test
    fun parseCookedPostBody_dropsLightboxMetadataText() {
        val html = """
            <div class="lightbox-wrapper">
              <a class="lightbox" href="https://cdn3.ldstatic.com/original/full.jpeg" title="image">
                <img src="https://cdn3.ldstatic.com/optimized/thumb.jpeg" alt="image" width="690" height="455">
                <div class="meta">
                  <span class="filename">image</span>
                  <span class="informations">1538×1016 196 KB</span>
                </div>
              </a>
            </div>
        """.trimIndent()

        val blocks = parseCookedPostBody(html, baseUrl)

        val image = blocks.filterIsInstance<CookedPostBlock.Image>().single()
        assertThat(image.src).isEqualTo("https://cdn3.ldstatic.com/optimized/thumb.jpeg")
        assertThat(image.href).isEqualTo("https://cdn3.ldstatic.com/original/full.jpeg")
        assertThat(blocks.filterIsInstance<CookedPostBlock.Text>()).isEmpty()
        assertThat(blocks.joinToString()).doesNotContain("1538×1016")
    }

    @Test
    fun parseCookedPostBody_parsesCalloutAndDropsCodeButtons() {
        val html = """
            <blockquote dir="auto" data-callout-type="note" class="callout is-collapsible">
              <div class="callout-title">
                <span class="callout-icon"><svg class="fa d-icon svg-icon"><use href="#far-pen-to-square"></use></svg></span>
                <span class="callout-title-inner">~/.claude/settings.json</span>
                <span class="callout-fold"><svg><use href="#chevron-down"></use></svg></span>
              </div>
              <div class="callout-content">
                <pre class="codeblock-buttons"><div class="codeblock-button-wrapper"><button class="copy-cmd">copy</button></div><code>{
  "model": "opus[1m]"
}</code></pre>
              </div>
            </blockquote>
        """.trimIndent()

        val callout = parseCookedPostBody(html, baseUrl)
            .filterIsInstance<CookedPostBlock.Callout>()
            .single()

        assertThat(callout.type).isEqualTo("note")
        assertThat(callout.icon).isEqualTo("far-pen-to-square")
        assertThat(callout.color).isEqualTo("#086ddd")
        assertThat(callout.titleHtml).isEqualTo("~/.claude/settings.json")
        assertThat(callout.children.filterIsInstance<CookedPostBlock.Code>().single().code)
            .contains("\"model\"")
        assertThat(callout.html).doesNotContain("copy-cmd")
    }

    @Test
    fun parseCookedPostBody_parsesMarkdownCalloutBlockquote() {
        val html = """
            <blockquote>
              <p>[!Note]<br>
              Keep proxy settings local.</p>
            </blockquote>
        """.trimIndent()

        val callout = parseCookedPostBody(html, baseUrl)
            .filterIsInstance<CookedPostBlock.Callout>()
            .single()

        assertThat(callout.type).isEqualTo("note")
        assertThat(callout.icon).isEqualTo("far-pen-to-square")
        assertThat(callout.titleHtml).isNull()
        assertThat(callout.html).contains("Keep proxy settings local.")
        assertThat(callout.html).doesNotContain("[!Note]")
        assertThat(callout.children.filterIsInstance<CookedPostBlock.Text>().single().html)
            .doesNotContain("[!Note]")
    }

    @Test
    fun parseCookedPostBody_attachesSingleImageParagraphCaptionHtml() {
        val html = """
            <p>
              <div class="lightbox-wrapper">
                <a href="/uploads/default/original/1X/full.png">
                  <img src="/uploads/default/optimized/1X/thumb.png" width="320" height="180" alt="Alt text">
                </a>
              </div>
              <br>
              * <em>Caption <a href="/t/example/1">source</a></em>
            </p>
        """.trimIndent()

        val blocks = parseCookedPostBody(html, baseUrl)

        val image = blocks.filterIsInstance<CookedPostBlock.Image>().single()
        assertThat(blocks.filterIsInstance<CookedPostBlock.Text>()).isEmpty()
        assertThat(image.captionHtml).contains("<em>")
        assertThat(image.captionHtml).contains("href=\"/t/example/1\"")
        assertThat(cookedGalleryImages(blocks, baseUrl).single().caption)
            .isEqualTo("* Caption source")
    }

    @Test
    fun parseCookedPostBody_attachesFigureCaptionHtml() {
        val html = """
            <figure>
              <img src="/uploads/default/original/1X/full.png" width="320" height="180" alt="Alt text">
              <figcaption>Visible <span class="spoiler">hidden</span></figcaption>
            </figure>
        """.trimIndent()

        val image = parseCookedPostBody(html, baseUrl)
            .filterIsInstance<CookedPostBlock.Image>()
            .single()

        assertThat(image.captionHtml).contains("class=\"spoiler\"")
        assertThat(cookedGalleryImages(listOf(image), baseUrl).single().caption)
            .isEqualTo("Visible hidden")
    }

    @Test
    fun parseCookedPostBody_parsesDetailsSummaryAndList() {
        val html = resourceText("discourse/cooked-details-emoji-spoiler.html")

        val blocks = parseCookedPostBody(html, baseUrl)

        val details = blocks.filterIsInstance<CookedPostBlock.Details>().first()
        assertThat(details.summary).isEqualTo("Friendly note")
        assertThat(details.summaryHtml).isEqualTo("Friendly note")
        assertThat(details.children.filterIsInstance<CookedPostBlock.Heading>().single().html)
            .contains("Sample heading")
        val list = details.children.filterIsInstance<CookedPostBlock.ListItems>().single()
        assertThat(list.ordered).isFalse()
        assertThat(list.items).hasSize(2)
        assertThat(blocks.filterIsInstance<CookedPostBlock.Image>()).isEmpty()
    }

    @Test
    fun parseCookedPostBody_preservesDetailsSummaryHtmlForInlineSpoilers() {
        val html = """
            <details>
              <summary>Visible <span class="spoiler">hidden</span></summary>
              <p>Body</p>
            </details>
        """.trimIndent()

        val details = parseCookedPostBody(html, baseUrl)
            .filterIsInstance<CookedPostBlock.Details>()
            .single()

        assertThat(details.summary).isEqualTo("Visible hidden")
        assertThat(details.summaryHtml).contains("class=\"spoiler\"")
    }

    @Test
    fun parseCookedPostBody_preservesBlockSpoilerAsInlineSpoilerText() {
        val html = """<div class="spoiler"><p>Hidden block text</p></div>"""

        val blocks = parseCookedPostBody(html, baseUrl)

        assertThat(blocks.filterIsInstance<CookedPostBlock.Details>()).isEmpty()
        assertThat(blocks.filterIsInstance<CookedPostBlock.Text>().single().html)
            .contains("class=\"spoiler\"")
    }

    @Test
    fun parseCookedPostBody_preservesNestedBlocksInsideListItems() {
        val html = """
            <ul>
              <li>
                Before image
                <p><a href="/uploads/default/original/1X/full.png"><img src="/uploads/default/optimized/1X/thumb.png" width="320" height="180" alt="Nested image"></a></p>
                <blockquote><p>Nested quote</p></blockquote>
              </li>
            </ul>
        """.trimIndent()

        val blocks = parseCookedPostBody(html, baseUrl)

        val list = blocks.filterIsInstance<CookedPostBlock.ListItems>().single()
        val item = list.items.single()
        assertThat(item.blocks.filterIsInstance<CookedPostBlock.Text>().any { it.html.contains("Before image") })
            .isTrue()
        assertThat(item.blocks.filterIsInstance<CookedPostBlock.Image>().single().alt)
            .isEqualTo("Nested image")
        assertThat(item.blocks.filterIsInstance<CookedPostBlock.Quote>().single().children)
            .isNotEmpty()
        assertThat(cookedGalleryImages(blocks, baseUrl).single().url)
            .isEqualTo("https://linux.do/uploads/default/original/1X/full.png")
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
        assertThat(poll.options.first().labelHtml).isEqualTo("Tool A")
        assertThat(poll.options.map { it.votes }).containsExactly(7, 3).inOrder()
        assertThat(poll.options.map { it.percent }).containsExactly(0.7f, 0.3f).inOrder()
        assertThat(poll.options.first().selected).isTrue()
        assertThat(poll.options.last().selected).isFalse()
    }

    @Test
    fun parseCookedPostBody_preservesPollOptionLabelHtmlForInlineSpoilers() {
        val html = """
            <div class="poll" data-poll-name="frontend" data-poll-status="open" data-poll-type="regular">
              <div class="poll-container">
                <ul>
                  <li data-poll-option-id="option-a">
                    <span class="poll-option-label">Visible <span class="spoiler">hidden</span></span>
                  </li>
                </ul>
              </div>
            </div>
        """.trimIndent()

        val option = parseCookedPostBody(html, baseUrl)
            .filterIsInstance<CookedPostBlock.Poll>()
            .single()
            .options
            .single()

        assertThat(option.label).isEqualTo("Visible hidden")
        assertThat(option.labelHtml).contains("class=\"spoiler\"")
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
    fun prepareInlineContent_extractsDiscourseHashtagChip() {
        val prepared = prepareInlineContent(
            """
                <a class="hashtag-cooked" href="/tag/1436-tag/1436">
                  <span class="hashtag-tag-icon" style="--color1: #669d34; --color2: #fffd;">
                    <svg class="fa d-icon d-icon-circle-question svg-icon"><use href="#circle-question"></use></svg>
                  </span>
                  <span>快问快答</span>
                </a>
            """.trimIndent(),
        )

        val chip = prepared.inlineHashtagChips.single()
        assertThat(chip.href).isEqualTo("/tag/1436-tag/1436")
        assertThat(chip.label).isEqualTo("快问快答")
        assertThat(chip.symbolId).isEqualTo("circle-question")
        assertThat(chip.rawIconColor).isEqualTo("#669d34")
        assertThat(prepared.inlineIcons).isEmpty()
        assertThat(prepared.html).contains(chip.token)
        assertThat(prepared.html).doesNotContain("<svg")
    }

    @Test
    fun prepareInlineContent_prefersRealHashtagIconOverSquareBackgroundSymbol() {
        val prepared = prepareInlineContent(
            """
                <a class="hashtag-cooked" href="/tag/%E9%9B%86%E4%B8%AD%E5%B8%96" data-icon="tag">
                  <span class="hashtag-tag-icon">
                    <svg class="fa d-icon d-icon-people-group svg-icon">
                      <use href="#square-full"></use>
                      <use href="#people-group"></use>
                    </svg>
                  </span>
                  <span>集中帖</span>
                </a>
            """.trimIndent(),
        )

        val chip = prepared.inlineHashtagChips.single()
        assertThat(chip.label).isEqualTo("集中帖")
        assertThat(chip.symbolId).isEqualTo("people-group")
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
