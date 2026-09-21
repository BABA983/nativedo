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
        assertThat(onebox.thumbnailWidthPx).isEqualTo(690)
        assertThat(onebox.thumbnailHeightPx).isEqualTo(362)
        assertThat(onebox.previewType).isEqualTo("article")
        assertThat(onebox.forceLargeMedia).isTrue()
        assertThat(onebox.siteIconUrl).isEqualTo("https://cdn.example.dev/site-icon.png")
        assertThat(onebox.description).contains("Command-line tooling")
        assertThat(onebox.descriptionHtml).contains("Command-line tooling")
        assertThat(blocks.filterIsInstance<CookedPostBlock.Text>().joinToString(separator = "\n") { it.html })
            .doesNotContain("https://docs.example.dev/start")
    }

    @Test
    fun parseCookedPostBody_preservesOneboxPreviewTypeAndThumbnailSize() {
        val html = """
            <aside class="onebox video" data-onebox-src="https://video.example.dev/watch/1">
              <header class="source"><a href="https://video.example.dev/watch/1">video.example.dev</a></header>
              <article class="onebox-body">
                <img class="thumbnail" src="https://cdn.example.dev/video.jpg" width="1280" height="720">
                <h3><a href="https://video.example.dev/watch/1">Video preview</a></h3>
                <p>Watch this.</p>
              </article>
            </aside>
        """.trimIndent()

        val onebox = parseCookedPostBody(html, baseUrl)
            .filterIsInstance<CookedPostBlock.Onebox>()
            .single()

        assertThat(onebox.previewType).isEqualTo("video")
        assertThat(onebox.thumbnailWidthPx).isEqualTo(1280)
        assertThat(onebox.thumbnailHeightPx).isEqualTo(720)
        assertThat(onebox.forceLargeMedia).isFalse()
        assertThat(onebox.forceSmallMedia).isFalse()
    }

    @Test
    fun parseCookedPostBody_preservesOneboxForceSmallMediaMarker() {
        val html = """
            <aside class="onebox video force-small-media" data-onebox-src="https://video.example.dev/watch/1">
              <header class="source"><a href="https://video.example.dev/watch/1">video.example.dev</a></header>
              <article class="onebox-body">
                <img class="thumbnail" src="https://cdn.example.dev/video.jpg" width="1280" height="720">
                <h3><a href="https://video.example.dev/watch/1">Video preview</a></h3>
                <p>Watch this.</p>
              </article>
            </aside>
        """.trimIndent()

        val onebox = parseCookedPostBody(html, baseUrl)
            .filterIsInstance<CookedPostBlock.Onebox>()
            .single()

        assertThat(onebox.previewType).isEqualTo("video")
        assertThat(onebox.forceSmallMedia).isTrue()
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
    fun parseCookedPostBody_quoteTitle_stripsAvatarAndQuoteControls() {
        val html = """
            <aside class="quote quote-modified" data-post="1" data-topic="2239921">
              <div class="title">
                <div class="quote-controls"></div>
                <img alt="" width="24" height="24" src="https://cdn.example.dev/user_avatar/linux.do/jiuuij/48/1118922_2.png" class="avatar">
                <div class="quote-title__text-content">
                  <a href="https://linux.do/t/topic/2239921">Topic title</a>
                  <a class="badge-category__wrapper " href="/c/develop/4"><span data-category-id="4" style="--category-badge-color: #32c3c3; --category-badge-text-color: #000000;" class="badge-category --style-icon "><span class="badge-category__name">开发调优</span></span></a>
                </div>
              </div>
              <blockquote><p>Quoted body</p></blockquote>
            </aside>
        """.trimIndent()

        val quote = parseCookedPostBody(html, "https://linux.do/")
            .filterIsInstance<CookedPostBlock.Quote>()
            .single()

        assertThat(quote.titleAvatarUrl)
            .isEqualTo("https://cdn.example.dev/user_avatar/linux.do/jiuuij/48/1118922_2.png")
        assertThat(quote.titleCategoryName).isEqualTo("开发调优")
        assertThat(quote.titleCategoryColor).isEqualTo("#32c3c3")
        assertThat(quote.titleCategoryTextColor).isEqualTo("#000000")
        assertThat(quote.titleHtml).contains("Topic title")
        assertThat(quote.titleHtml).doesNotContain("badge-category")
    }

    @Test
    fun parseCookedPostBody_preservesNestedQuoteLevels() {
        val html = """
            <blockquote>
              <p>Outer quote</p>
              <blockquote>
                <p>Inner quote</p>
                <blockquote>
                  <p>Deep quote</p>
                </blockquote>
              </blockquote>
            </blockquote>
        """.trimIndent()

        val outer = parseCookedPostBody(html, baseUrl)
            .filterIsInstance<CookedPostBlock.Quote>()
            .single()
        val inner = outer.children.filterIsInstance<CookedPostBlock.Quote>().single()
        val deep = inner.children.filterIsInstance<CookedPostBlock.Quote>().single()

        assertThat(outer.level).isEqualTo(0)
        assertThat(inner.level).isEqualTo(1)
        assertThat(deep.level).isEqualTo(2)
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
        assertThat(callout.collapsible).isTrue()
        assertThat(callout.children.filterIsInstance<CookedPostBlock.Code>().single().code)
            .contains("\"model\"")
        assertThat(callout.html).doesNotContain("copy-cmd")
    }

    @Test
    fun parseCookedPostBody_parsesDivCalloutShape() {
        // The Quote Callouts theme component wraps a callout in a blockquote, but the same
        // callout/callout-title/callout-content structure is also cooked onto a div; the div shape
        // used to fall through parseDivElement into a plain run of Text.
        val html = """
            <div class="callout callout-note" data-callout-type="note">
              <div class="callout-title"><span class="callout-title-inner">Note</span></div>
              <div class="callout-content"><p>Callouts stay our own block.</p></div>
            </div>
        """.trimIndent()

        val blocks = parseCookedPostBody(html, baseUrl)

        assertThat(blocks.filterIsInstance<CookedPostBlock.Text>()).isEmpty()
        val callout = blocks.filterIsInstance<CookedPostBlock.Callout>().single()
        assertThat(callout.type).isEqualTo("note")
        assertThat(callout.icon).isEqualTo("far-pen-to-square")
        assertThat(callout.color).isEqualTo("#086ddd")
        assertThat(callout.titleHtml).isEqualTo("Note")
        assertThat(callout.collapsible).isFalse()
        assertThat(callout.children.filterIsInstance<CookedPostBlock.Text>().single().html)
            .contains("Callouts stay our own block.")
        assertThat(callout.html).contains("Callouts stay our own block.")
    }

    @Test
    fun parseCookedPostBody_parsesAsideCalloutShapeWithFoldAndNestedBlocks() {
        val html = """
            <aside class="callout callout-warning is-collapsible" data-callout-type="warning">
              <div class="callout-title">
                <span class="callout-icon"><svg class="fa d-icon svg-icon"><use href="#triangle-exclamation"></use></svg></span>
                <span class="callout-title-inner">Watch out</span>
                <span class="callout-fold"><svg><use href="#chevron-down"></use></svg></span>
              </div>
              <div class="callout-content">
                <p>First line.</p>
                <ul><li>one</li><li>two</li></ul>
              </div>
            </aside>
        """.trimIndent()

        val callout = parseCookedPostBody(html, baseUrl)
            .filterIsInstance<CookedPostBlock.Callout>()
            .single()

        assertThat(callout.type).isEqualTo("warning")
        assertThat(callout.icon).isEqualTo("triangle-exclamation")
        assertThat(callout.color).isEqualTo("#ec7500")
        assertThat(callout.titleHtml).isEqualTo("Watch out")
        assertThat(callout.collapsible).isTrue()
        assertThat(callout.children.filterIsInstance<CookedPostBlock.Text>().single().html)
            .contains("First line.")
        assertThat(callout.children.filterIsInstance<CookedPostBlock.ListItems>().single().items).hasSize(2)
        assertThat(callout.html).doesNotContain("callout-fold")
    }

    @Test
    fun parseCookedPostBody_parsesCalloutWithAlternateTitleClassesAndDataCallout() {
        val html = """
            <div class="callout" data-callout="tip">
              <div class="callout-title">
                <div class="callout-title-icon"><svg><use href="#fire-flame-curved"></use></svg></div>
                <div class="callout-title-text">Pro tip</div>
              </div>
              <div class="callout-content"><p>Body.</p></div>
            </div>
        """.trimIndent()

        val callout = parseCookedPostBody(html, baseUrl)
            .filterIsInstance<CookedPostBlock.Callout>()
            .single()

        assertThat(callout.type).isEqualTo("tip")
        assertThat(callout.icon).isEqualTo("fire-flame-curved")
        assertThat(callout.titleHtml).isEqualTo("Pro tip")
        assertThat(callout.children.filterIsInstance<CookedPostBlock.Text>().single().html).contains("Body.")
    }

    @Test
    fun parseCookedPostBody_parsesNestedCalloutInsideDivCallout() {
        val html = """
            <div class="callout callout-note" data-callout-type="note">
              <div class="callout-title"><span class="callout-title-inner">Outer</span></div>
              <div class="callout-content">
                <p>Before</p>
                <div class="callout callout-tip" data-callout-type="tip">
                  <div class="callout-title"><span class="callout-title-inner">Inner</span></div>
                  <div class="callout-content"><p>Nested body.</p></div>
                </div>
              </div>
            </div>
        """.trimIndent()

        val outer = parseCookedPostBody(html, baseUrl)
            .filterIsInstance<CookedPostBlock.Callout>()
            .single()
        val inner = outer.children.filterIsInstance<CookedPostBlock.Callout>().single()

        assertThat(outer.type).isEqualTo("note")
        assertThat(inner.type).isEqualTo("tip")
        assertThat(inner.color).isEqualTo("#00bfbc")
        assertThat(inner.children.filterIsInstance<CookedPostBlock.Text>().single().html)
            .contains("Nested body.")
    }

    @Test
    fun parseCookedPostBody_doesNotTreatQuoteAsideAsCallout() {
        val html = """
            <aside class="quote no-group" data-post="7" data-topic="424242">
              <div class="title"><a href="https://community.example/t/topic/424242/7">Quoted</a></div>
              <blockquote><p>Plain quote body.</p></blockquote>
            </aside>
        """.trimIndent()

        val blocks = parseCookedPostBody(html, baseUrl)

        assertThat(blocks.filterIsInstance<CookedPostBlock.Callout>()).isEmpty()
        val quote = blocks.filterIsInstance<CookedPostBlock.Quote>().single()
        assertThat(quote.html).contains("Plain quote body.")
        assertThat(quote.sourcePostNumber).isEqualTo(7)
        assertThat(quote.sourceTopicId).isEqualTo(424242L)
    }

    @Test
    fun parseCookedPostBody_parsesMarkdownCalloutWithPlusFoldModifier() {
        val html = """
            <blockquote>
              <p>[!Note]+ ~/.claude/settings.json</p>
              <pre><code class="lang-auto">{"model": "opus"}</code></pre>
            </blockquote>
        """.trimIndent()

        val callout = parseCookedPostBody(html, baseUrl)
            .filterIsInstance<CookedPostBlock.Callout>()
            .single()

        assertThat(callout.type).isEqualTo("note")
        assertThat(callout.titleHtml).isEqualTo("~/.claude/settings.json")
        assertThat(callout.collapsible).isTrue()
        assertThat(callout.defaultExpanded).isTrue()
        assertThat(callout.children.filterIsInstance<CookedPostBlock.Code>().single().code)
            .contains("opus")
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
    fun parseCookedPostBody_parsesDetailsWithParagraphAndCodeBlock() {
        val html = """
            <details>
              <summary>Example title</summary>
              <p>Helper text</p>
              <pre class="codeblock-buttons"><div class="codeblock-button-wrapper"><button class="copy-cmd"></button></div><code class="hljs language-json">{ "a": 1 }</code></pre>
            </details>
        """.trimIndent()

        val details = parseCookedPostBody(html, baseUrl)
            .filterIsInstance<CookedPostBlock.Details>()
            .single()

        assertThat(details.summary).isEqualTo("Example title")
        assertThat(details.children.filterIsInstance<CookedPostBlock.Text>().single().html)
            .contains("Helper text")
        val code = details.children.filterIsInstance<CookedPostBlock.Code>().single()
        assertThat(code.code).contains("\"a\"")
        assertThat(code.code).doesNotContain("copy-cmd")
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
            <div class="poll" data-poll-charttype="bar" data-poll-name="frontend" data-poll-title="Frontend work" data-poll-public="true" data-poll-results="always" data-poll-status="open" data-poll-type="regular">
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
        assertThat(poll.title).isEqualTo("Frontend work")
        assertThat(poll.status).isEqualTo("open")
        assertThat(poll.type).isEqualTo("regular")
        assertThat(poll.results).isEqualTo("always")
        assertThat(poll.publicPoll).isTrue()
        assertThat(poll.chartType).isEqualTo("bar")
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
    fun parseCookedPostBody_coalescesAdjacentHeadingIntoPollTitle() {
        val html = """
            <h2>模型订阅的方式</h2>
            <div class="poll" data-poll-name="frontend" data-poll-status="closed" data-poll-type="regular" data-poll-public="true">
              <div class="poll-container">
                <ul>
                  <li data-poll-option-id="option-a"><span class="poll-option-label">A</span></li>
                </ul>
              </div>
            </div>
        """.trimIndent()

        val blocks = parseCookedPostBody(html, baseUrl)

        assertThat(blocks).hasSize(1)
        val poll = blocks.single() as CookedPostBlock.Poll
        assertThat(poll.title).isEqualTo("模型订阅的方式")
    }

    @Test
    fun cookInlineHtml_extractsEmojiInlineImage() {
        val prepared = cookInlineHtml("""<p>Hello <img src="/images/emoji/twemoji/wave.png" class="emoji" title=":wave:" alt=":wave:"></p>""")

        val inlineImage = prepared.inlineImages.single()
        assertThat(inlineImage.rawUrl).isEqualTo("/images/emoji/twemoji/wave.png")
        assertThat(inlineImage.contentDescription).isEqualTo(":wave:")
        assertThat(inlineImage.kind).isEqualTo(InlineImageKind.Emoji)
        assertThat(prepared.plainText()).isEqualTo("Hello :wave:")
    }

    @Test
    fun cookInlineHtml_extractsDiscourseHashtagChip() {
        val prepared = cookInlineHtml(
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
        assertThat(prepared.plainText()).isEqualTo("快问快答")
    }

    @Test
    fun cookInlineHtml_prefersRealHashtagIconOverSquareBackgroundSymbol() {
        val prepared = cookInlineHtml(
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
