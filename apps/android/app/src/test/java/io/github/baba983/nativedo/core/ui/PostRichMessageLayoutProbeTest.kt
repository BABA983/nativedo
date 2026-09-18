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

    private companion object {
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
