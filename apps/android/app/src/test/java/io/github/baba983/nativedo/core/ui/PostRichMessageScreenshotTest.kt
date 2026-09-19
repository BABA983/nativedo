package io.github.baba983.nativedo.core.ui

import android.content.Context
import io.github.baba983.nativedo.core.html.parseCookedPostBody
import io.github.baba983.nativedo.screenshot.NativeDoPaparazziRule
import io.github.baba983.nativedo.screenshot.SystemAppearance
import org.junit.Rule
import org.junit.Test
import org.telegram.messenger.RichMessageLayout

/**
 * S4's first light: a Discourse post's cooked blocks, mapped by [PostRichMessageBuilder] and drawn
 * by upstream's own renderer through its own host view. Nothing in the app calls this yet - the
 * snapshot is the pipeline check (blocks -> `TL_iv.RichMessage` -> `RichMessageLayout` -> pixels)
 * that has to hold before a post cell can be switched over.
 *
 * The post is text and structure only on purpose: the Discourse-only blocks (callout, onebox, poll,
 * image/collage) are not mapped yet, and images need media plumbing this slice does not carry.
 */
internal class PostRichMessageScreenshotTest {
    @get:Rule
    val paparazziRule = NativeDoPaparazziRule(SystemAppearance.entries)

    @Test
    fun rendersADiscoursePostThroughUpstreamsLayout() {
        paparazziRule.snapshot { context ->
            richMessagePreview(context, POST_HTML)
        }
    }

    private fun richMessagePreview(context: Context, html: String): RichMessageLayout.PreviewView =
        RichMessageLayout.PreviewView(context).apply {
            set(PostRichMessageBuilder.build(parseCookedPostBody(html, BASE_URL)))
        }

    private companion object {
        private const val BASE_URL = "https://linux.do/"

        private val POST_HTML = """
            <p>Native clients read the <b>cooked HTML</b> Discourse already renders, then hand
            it to a renderer that knows Telegram's rich-text model. A <a href="https://linux.do/t/1">link</a>
            and a <i>slant</i> travel as model nodes, not as markup.</p>
            <h2>What the mapping covers</h2>
            <p>Paragraphs, headings, quotes, lists and code:</p>
            <ul>
              <li>a plain item</li>
              <li>an item with <b>bold</b> text</li>
            </ul>
            <blockquote><p>A quoted paragraph with an <code>inline code</code> run.</p></blockquote>
            <figure>
              <img src="https://cdn.example.dev/diagram.png" width="960" height="540" alt="diagram">
              <figcaption>A captioned image.</figcaption>
            </figure>
            <p>An image with a caption, then an adjacent one - the parser reads them as a collage,
            and Telegram's model keeps one caption per group:</p>
            <figure>
              <img src="https://cdn.example.dev/one.png" width="800" height="600" alt="one">
              <figcaption>This caption travels to the group.</figcaption>
            </figure>
            <p><img src="https://cdn.example.dev/two.png" width="600" height="800" alt="two"></p>
            <p>Above: a captioned image and a collage. Their bitmaps are device-only.</p>
            <pre><code>val message = PostRichMessageBuilder.build(blocks)</code></pre>
            <hr>
            <p>Done.</p>
        """.trimIndent()
    }
}
