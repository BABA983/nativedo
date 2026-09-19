package io.github.baba983.nativedo.core.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.html.parseCookedPostBody
import io.github.baba983.nativedo.screenshot.NativeDoPaparazziRule
import io.github.baba983.nativedo.ui.topic.body.RichMessageMediaAdapter
import org.junit.Rule
import org.junit.Test
import org.telegram.messenger.MessageObject
import org.telegram.tgnet.TLRPC

/**
 * The adapter behind the image tap: `PhotoViewer.openPhoto(index, PageBlocksAdapter, provider)` reads
 * the URL, the size and the caption off the page block through it, so its mapping is what a tap opens.
 */
internal class RichMessageMediaAdapterTest {
    @get:Rule
    val paparazziRule = NativeDoPaparazziRule()

    @Test
    fun theAdapterExposesEveryMediaBlockWithItsUrlAndCaption() {
        paparazziRule.verify {
            val html = """
                <p><img src="https://cdn.example.dev/diagram.png" width="960" height="540" alt="diagram"></p>
                <p>A paragraph keeps the next images out of the first image's group.</p>
                <figure>
                  <img src="https://cdn.example.dev/one.png" width="800" height="600" alt="one">
                  <figcaption>Group caption.</figcaption>
                </figure>
                <p><img src="https://cdn.example.dev/two.png" width="600" height="800" alt="two"></p>
            """.trimIndent()
            val richMessage = PostRichMessageBuilder.build(
                parseCookedPostBody(html, "https://linux.do/"),
                "https://linux.do/",
            )
            val messageObject = MessageObject(0, TLRPC.TL_message(), false, false)
            messageObject.messageOwner.rich_message = richMessage
            // The delegate feeds the adapter exactly this list: every media block of the layout.
            val layout = org.telegram.messenger.RichMessageLayout(messageObject, 400, null)
            val media = ArrayList<org.telegram.tgnet.tl.TL_iv.PageBlock>()
            layout.collectMediaBlocks(media)
            assertThat(media).hasSize(3)
            val adapter = RichMessageMediaAdapter(media, messageObject)

            assertThat(adapter.getItemsCount()).isEqualTo(3)
            assertThat(adapter.get(0).url).isEqualTo("https://cdn.example.dev/diagram.png")
            // Upstream's adapter answers null: the caption is drawn under the image in the message.
            assertThat(adapter.getCaption(0)).isNull()
            assertThat(adapter.getCaption(1)).isNull()
            assertThat(adapter.getCaption(2)).isNull()
            assertThat(adapter.getFileName(1)).isEqualTo("https://cdn.example.dev/one.png")
            assertThat(adapter.isVideo(1)).isFalse()
            assertThat(adapter.getMedia(1)).isNull()
            assertThat(adapter.getParentObject()).isSameInstanceAs(messageObject)
        }
    }
}
