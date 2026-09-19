package io.github.baba983.nativedo.core.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.html.parseCookedPostBody
import org.junit.Test
import org.telegram.tgnet.tl.TL_iv

class PostRichMessageTest {
    private val baseUrl = "https://linux.do/"

    private fun build(html: String): TL_iv.RichMessage =
        PostRichMessageBuilder.build(parseCookedPostBody(html, baseUrl), baseUrl)

    @Test
    fun paragraphsAndHeadingsBecomeTheirPageBlocksWithTheInlineModelInside() {
        val message = build("<p>First <b>bold</b> line</p><h2>Heading</h2><p>Last</p>")

        assertThat(message.blocks).hasSize(3)
        assertThat(message.blocks[0]).isInstanceOf(TL_iv.pageBlockParagraph::class.java)
        assertThat(message.blocks[1]).isInstanceOf(TL_iv.pageBlockHeading2::class.java)
        assertThat(message.blocks[2]).isInstanceOf(TL_iv.pageBlockParagraph::class.java)

        val first = message.blocks[0].text as TL_iv.textConcat
        assertThat(first.texts.filterIsInstance<TL_iv.textBold>()).hasSize(1)
    }

    @Test
    fun blockquoteCarriesTheAuthorAsItsCaptionAndKeepsNestedBlocks() {
        val message = build(
            """
            <aside class="quote" data-topic="1" data-post="2">
              <div class="title">Some Author:</div>
              <blockquote><p>Quoted body</p></blockquote>
            </aside>
            """.trimIndent()
        )

        val quote = message.blocks.single() as TL_iv.pageBlockBlockquoteBlocks
        assertThat((quote.caption as TL_iv.textPlain).text).isEqualTo("Some Author:")
        assertThat(quote.blocks).hasSize(1)
        assertThat(quote.blocks.single()).isInstanceOf(TL_iv.pageBlockParagraph::class.java)
    }

    @Test
    fun codeBlockBecomesPreformattedText() {
        val message = build("<pre><code>val x = 1\nval y = 2</code></pre>")

        val pre = message.blocks.single() as TL_iv.pageBlockPreformatted
        assertThat((pre.text as TL_iv.textPlain).text).contains("val x = 1")
    }

    @Test
    fun orderedAndPlainListsBecomeTheirListBlocks() {
        val ordered = build("<ol><li>one</li><li>two</li></ol>")
        val plain = build("<ul><li>alpha</li><li>beta</li></ul>")

        val orderedList = ordered.blocks.single() as TL_iv.pageBlockOrderedList
        assertThat(orderedList.items).hasSize(2)
        assertThat(orderedList.items[0]).isInstanceOf(TL_iv.TL_pageListOrderedItemText::class.java)

        val plainList = plain.blocks.single() as TL_iv.pageBlockList
        assertThat(plainList.items).hasSize(2)
        assertThat(plainList.items[0]).isInstanceOf(TL_iv.TL_pageListItemText::class.java)
    }

    @Test
    fun detailsAndTablesAndDividersMap() {
        val details = build("<details><summary>More</summary><p>Hidden</p></details>")
        assertThat(details.blocks.single()).isInstanceOf(TL_iv.pageBlockDetails::class.java)

        val table = build(
            "<table><thead><tr><th>H</th></tr></thead><tbody><tr><td>C</td></tr></tbody></table>"
        )
        val pageTable = table.blocks.single() as TL_iv.pageBlockTable
        assertThat(pageTable.rows).hasSize(2)
        assertThat(pageTable.rows[0].cells.single().header).isTrue()
        assertThat(pageTable.rows[1].cells.single().header).isFalse()

        val divider = build("<hr>")
        assertThat(divider.blocks.single()).isInstanceOf(TL_iv.pageBlockDivider::class.java)
    }

    @Test
    fun anImageBecomesAPhotoBlockWithItsUrlSizeAndCaption() {
        val message = build(
            """
            <p class="lightbox-wrapper">
              <a class="lightbox" href="https://cdn.example.dev/full.png">
                <img src="/uploads/photo.png" width="800" height="600" alt="alt text">
              </a>
            </p>
            """.trimIndent()
        )

        val photo = message.blocks.single() as TL_iv.pageBlockPhoto
        assertThat(photo.url).isEqualTo("https://linux.do/uploads/photo.png")
        assertThat(photo.w).isEqualTo(800)
        assertThat(photo.h).isEqualTo(600)
    }

    @Test
    fun adjacentImagesBecomeACollageOfUrlPhotos() {
        val message = build(
            """
            <p><img src="https://cdn.example.dev/one.png" width="800" height="600" alt="one"></p>
            <p><img src="https://cdn.example.dev/two.png" width="600" height="800" alt="two"></p>
            """.trimIndent()
        )

        val collage = message.blocks.single() as TL_iv.pageBlockCollage
        assertThat(collage.items).hasSize(2)
        val first = collage.items[0] as TL_iv.pageBlockPhoto
        assertThat(first.url).isEqualTo("https://cdn.example.dev/one.png")
        assertThat(first.w).isEqualTo(800)
        val second = collage.items[1] as TL_iv.pageBlockPhoto
        assertThat(second.h).isEqualTo(800)
    }

    @Test
    fun aFigureCaptionTravelsAsThePhotoCaption() {
        val message = build(
            """
            <figure>
              <img src="https://cdn.example.dev/diagram.png" width="960" height="540" alt="diagram">
              <figcaption>A <b>captioned</b> diagram.</figcaption>
            </figure>
            """.trimIndent()
        )

        val photo = message.blocks.single() as TL_iv.pageBlockPhoto
        assertThat(photo.caption).isNotNull()
        assertThat((photo.caption.text as TL_iv.textConcat).texts.filterIsInstance<TL_iv.textBold>()).hasSize(1)
    }

    @Test
    fun discourseOnlyBlocksAreLeftOutInsteadOfMisrendered() {
        val message = build("<p>Text only</p>")

        assertThat(message.blocks.map { it.javaClass }).containsExactly(TL_iv.pageBlockParagraph::class.java)
    }
}
