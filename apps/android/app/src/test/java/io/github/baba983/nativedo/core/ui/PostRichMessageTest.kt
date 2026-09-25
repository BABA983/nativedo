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
    fun aCaptionOnACollageMemberTravelsToTheGroupCaption() {
        val message = build(
            """
            <figure>
              <img src="https://cdn.example.dev/one.png" width="800" height="600" alt="one">
              <figcaption>One caption for the group.</figcaption>
            </figure>
            <p><img src="https://cdn.example.dev/two.png" width="600" height="800" alt="two"></p>
            """.trimIndent()
        )

        val collage = message.blocks.single() as TL_iv.pageBlockCollage
        assertThat(collage.items).hasSize(2)
        // Telegram's model keeps one caption per media group; the members carry none.
        assertThat(collage.items.map { it.caption }).containsExactly(null, null)
        assertThat((collage.caption.text as TL_iv.textPlain).text).isEqualTo("One caption for the group.")
    }

    @Test
    fun severalMemberCaptionsJoinAsOneGroupCaption() {
        val message = build(
            """
            <figure><img src="https://cdn.example.dev/one.png" width="800" height="600" alt="one"><figcaption>First.</figcaption></figure>
            <figure><img src="https://cdn.example.dev/two.png" width="600" height="800" alt="two"><figcaption>Second.</figcaption></figure>
            """.trimIndent()
        )

        // Two captioned figures still coalesce, and their captions become one caption with a break.
        val collage = message.blocks.single() as TL_iv.pageBlockCollage
        val joined = collage.caption.text as TL_iv.textConcat
        assertThat(joined.texts).hasSize(3)
        assertThat((joined.texts[0] as TL_iv.textPlain).text).isEqualTo("First.")
        assertThat((joined.texts[1] as TL_iv.textPlain).text).isEqualTo("\n")
        assertThat((joined.texts[2] as TL_iv.textPlain).text).isEqualTo("Second.")
    }
}
