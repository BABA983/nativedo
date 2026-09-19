package io.github.baba983.nativedo.core.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.html.parseCookedPostBody
import io.github.baba983.nativedo.screenshot.NativeDoPaparazziRule
import org.junit.Rule
import org.junit.Test
import org.telegram.messenger.MessageObject
import org.telegram.messenger.RichMessageLayout
import org.telegram.tgnet.TLRPC

/**
 * The layout half of S4's pipeline check: [PostRichMessageScreenshotTest] proves the renderer draws
 * *something*, this one proves it consumed *everything* the mapping produced (the renderer silently
 * drops a block it cannot lay out, so a screenshot alone cannot tell the two apart).
 */
internal class PostRichMessageLayoutProbeTest {
    @get:Rule
    val paparazziRule = NativeDoPaparazziRule()

    @Test
    fun theLayoutConsumesEveryMappedBlock() {
        paparazziRule.verify {
            val cooked = parseCookedPostBody(POST_HTML, "https://linux.do/")
            val richMessage = PostRichMessageBuilder.build(cooked)

            val messageObject = MessageObject(0, TLRPC.TL_message(), false, false)
            messageObject.messageOwner.rich_message = richMessage
            val layout = RichMessageLayout(messageObject, 945, null)
            layout.setResourcesProvider(null)
            layout.layout(null)

            println("cooked blocks: ${cooked.map { it.javaClass.simpleName }}")
            println("page blocks:   ${richMessage.blocks.map { it.javaClass.simpleName }}")
            println("layout blocks: ${layout.blocks.map { it.javaClass.simpleName }}")
            println("text blocks:   ${layout.textBlocks.size} height=${layout.height} minWidth=${layout.minWidth}")

            assertThat(richMessage.blocks).isNotEmpty()
            assertThat(layout.blocks).isNotEmpty()
            assertThat(layout.height).isGreaterThan(0)
            // No silent drops: every mapped page block becomes one laid-out block (checked for this
            // fixture; the renderer's block classes are upstream's, one per page block).
            assertThat(layout.blocks).hasSize(richMessage.blocks.size)
            assertThat(layout.textBlocks.size).isAtLeast(richMessage.blocks.size - 1)
        }
    }

    @Test
    fun aCaptionedImageProducesMediaAndCaptionBlocks() {
        paparazziRule.verify {
            val cooked = parseCookedPostBody(CAPTIONED_IMAGE_HTML, "https://linux.do/")
            val richMessage = PostRichMessageBuilder.build(cooked, "https://linux.do/")

            // The model side: the caption is on the photo block.
            val photo = richMessage.blocks.single() as org.telegram.tgnet.tl.TL_iv.pageBlockPhoto
            println("caption model: ${photo.caption?.text?.javaClass?.simpleName}")

            val messageObject = MessageObject(0, TLRPC.TL_message(), false, false)
            messageObject.messageOwner.rich_message = richMessage
            val layout = RichMessageLayout(messageObject, 945, null)
            layout.setResourcesProvider(null)
            layout.layout(null)

            println("layout blocks for captioned image: ${layout.blocks.map { it.javaClass.simpleName }}")
            val captionBlock = layout.blocks.filterIsInstance<RichMessageLayout.RichCaptionBlock>().singleOrNull()
            println("caption text drawn: '${captionBlock?.caption?.layout?.text}' height=${captionBlock?.height}")
            // The renderer emits the media block and, when a caption is there, a caption block after it.
            assertThat(layout.blocks.map { it.javaClass.simpleName })
                .containsAtLeast("RichPhotoBlock", "RichCaptionBlock")
        }
    }

    /**
     * Recorded limitation rather than an invariant: the parser coalesces a captioned figure with
     * whatever adjacent images follow it into one `Collage`, and a collage carries no per-image
     * caption in Telegram's model, so such a caption is dropped by the mapping (`docs/device-verification.md`
     * lists it). The leading image's caption survives only when the image stands alone.
     */
    @Test
    fun aCaptionedFigureFollowedByAdjacentImagesBecomesACollageWithoutTheCaption() {
        paparazziRule.verify {
            val html = """
                <figure>
                  <img src="https://cdn.example.dev/diagram.png" width="960" height="540" alt="diagram">
                  <figcaption>A captioned image.</figcaption>
                </figure>
                <p><img src="https://cdn.example.dev/one.png" width="800" height="600" alt="one"></p>
                <p><img src="https://cdn.example.dev/two.png" width="600" height="800" alt="two"></p>
            """.trimIndent()
            val cooked = parseCookedPostBody(html, "https://linux.do/")
            println("cooked: ${cooked.map { it.javaClass.simpleName }}")
            val richMessage = PostRichMessageBuilder.build(cooked, "https://linux.do/")
            println("page blocks: ${richMessage.blocks.map { it.javaClass.simpleName }}")
            println("captions: ${richMessage.blocks.map { b -> b.caption?.text?.javaClass?.simpleName }}")

            val messageObject = MessageObject(0, TLRPC.TL_message(), false, false)
            messageObject.messageOwner.rich_message = richMessage
            val layout = RichMessageLayout(messageObject, 405, null)
            layout.setResourcesProvider(null)
            layout.layout(null)
            println("layout blocks: ${layout.blocks.map { it.javaClass.simpleName }}")
            println("caption heights: ${layout.blocks.filterIsInstance<RichMessageLayout.RichCaptionBlock>().map { it.height }}")
            assertThat(richMessage.blocks.single()).isInstanceOf(org.telegram.tgnet.tl.TL_iv.pageBlockCollage::class.java)
            assertThat(richMessage.blocks.single().caption).isNull()
            assertThat(cooked.single()).isInstanceOf(io.github.baba983.nativedo.core.html.CookedPostBlock.Collage::class.java)
        }
    }

    private companion object {
        private val CAPTIONED_IMAGE_HTML = """
            <figure>
              <img src="https://cdn.example.dev/diagram.png" width="960" height="540" alt="diagram">
              <figcaption>A captioned diagram.</figcaption>
            </figure>
        """.trimIndent()

        private val POST_HTML = """
            <p>Native clients read the <b>cooked HTML</b> Discourse already renders.</p>
            <h2>What the mapping covers</h2>
            <ul>
              <li>a plain item</li>
            </ul>
            <blockquote><p>A quoted paragraph.</p></blockquote>
            <pre><code>val x = 1</code></pre>
            <hr>
            <p>Done.</p>
        """.trimIndent()
    }
}
