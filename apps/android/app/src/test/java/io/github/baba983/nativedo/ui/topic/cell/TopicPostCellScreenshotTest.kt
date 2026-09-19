package io.github.baba983.nativedo.ui.topic.cell

import android.content.Context
import android.view.ViewGroup
import io.github.baba983.nativedo.core.html.parseCookedPostBody
import io.github.baba983.nativedo.core.model.Post
import io.github.baba983.nativedo.screenshot.NativeDoPaparazziRule
import io.github.baba983.nativedo.screenshot.SystemAppearance
import io.github.baba983.nativedo.ui.topic.body.PostBodyBlockState
import io.github.baba983.nativedo.ui.topic.body.RichMessageBody
import org.junit.Rule
import org.junit.Test

internal class TopicPostCellScreenshotTest {
    @get:Rule
    val paparazziRule = NativeDoPaparazziRule(SystemAppearance.entries)

    @Test
    fun paragraph() {
        paparazziRule.snapshot { context ->
            topicPostCell(context)
        }
    }

    @Test
    fun persistentSearchHighlight() {
        paparazziRule.snapshot { context ->
            topicPostCell(context).apply {
                setHighlightedText("linux")
            }
        }
    }

    @Test
    fun cjkAdjacentSearchHighlight() {
        paparazziRule.snapshot { context ->
            topicPostCell(context, CJK_POST_HTML).apply {
                setHighlightedText("linux")
            }
        }
    }

    @Test
    fun contentBlocks() {
        paparazziRule.snapshot { context ->
            topicPostCell(context, CONTENT_BLOCKS_HTML)
        }
    }

    /**
     * The opt-in rich-message body: same cell, same post, but the body is one
     * [RichMessageBodyBlock] (upstream's renderer) instead of our block tree. The other goldens in
     * this class are the flag-off shape and must not move.
     */
    @Test
    fun richMessageBody() {
        RichMessageBody.enabled = true
        try {
            paparazziRule.snapshot { context ->
                topicPostCell(context, RICH_MESSAGE_POST_HTML)
            }
        } finally {
            RichMessageBody.enabled = false
        }
    }

    private fun topicPostCell(
        context: Context,
        html: String = POST_HTML,
    ): TopicPostCell {
        val post = Post(
            id = 1001L,
            postNumber = 43,
            authorUsername = "alice",
            authorDisplayName = "Alice Chen",
            cookedHtml = html,
        )
        return TopicPostCell(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setPost(
                post,
                null,
                null,
                true,
                true,
                2566145L,
                "https://linux.do/",
                emptyMap(),
                emptyMap(),
                emptyList(),
                0,
                emptyList(),
                parseCookedPostBody(html, "https://linux.do/"),
                PostBodyBlockState(),
                0,
                null,
            )
        }
    }

    private companion object {
        val RICH_MESSAGE_POST_HTML = """
            <p>A post body drawn by <b>upstream's renderer</b>: paragraphs, a heading, a list,
            a <a href="https://linux.do/t/1">link</a>, a quote and a code block.</p>
            <h2>Blocks</h2>
            <ul>
              <li>plain item</li>
              <li>item with <b>bold</b></li>
            </ul>
            <blockquote><p>Quoted paragraph with <code>inline code</code>.</p></blockquote>
            <figure>
              <img src="https://cdn.example.dev/diagram.png" width="960" height="540" alt="diagram">
              <figcaption>A captioned image.</figcaption>
            </figure>
            <p>Two adjacent images, which the parser reads as a collage:</p>
            <p><img src="https://cdn.example.dev/one.png" width="800" height="600" alt="one"></p>
            <p><img src="https://cdn.example.dev/two.png" width="600" height="800" alt="two"></p>
            <pre><code>val body = RichMessageBody.enabled</code></pre>
            <hr>
            <p>Done.</p>
        """.trimIndent()

        const val POST_HTML =
            "<p>NativeDo keeps linux.do topics readable and highlights search results.</p>"
        const val CJK_POST_HTML =
            "<p>在Linux环境里，NativeDo keeps linux.do topics readable.</p>"

        /** One post body that exercises every deterministic content block we render. */
        val CONTENT_BLOCKS_HTML = """
            <h2>渲染块清单</h2>
            <p>正文段落，用来对比各内容块的配色与间距。</p>
            <ul><li>列表项 A</li><li>列表项 B</li></ul>
            <blockquote><p>引用的一段话。</p></blockquote>
            <table>
              <thead><tr><th>名称</th><th>取值</th><th>说明</th></tr></thead>
              <tbody>
                <tr><td>alpha</td><td>1</td><td>first</td></tr>
                <tr><td>beta</td><td>2</td><td>second</td></tr>
              </tbody>
            </table>
            <pre><code>val x = 42
fun main() { println(x) }</code></pre>
            <hr>
            <details>
              <summary>展开查看详情</summary>
              <p>折叠起来的内容。</p>
            </details>
        """.trimIndent()
    }
}
