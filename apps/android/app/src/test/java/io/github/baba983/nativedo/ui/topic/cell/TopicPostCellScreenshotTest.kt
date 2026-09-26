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

    /**
     * The hybrid body: paragraphs, a heading, a code block and a divider are upstream's renderer,
     * while the poll, the link card and the callout keep our own blocks and stay in place between
     * those runs - the shape a real post with Discourse-only content gets.
     */
    @Test
    fun richMessageHybridBody() {
        RichMessageBody.enabled = true
        try {
            paparazziRule.snapshot { context ->
                topicPostCell(context, RICH_MESSAGE_HYBRID_HTML)
            }
        } finally {
            RichMessageBody.enabled = false
        }
    }

    /**
     * The typography pair: the same post drawn by the block path (flag off) and by upstream's
     * renderer (flag on), so the two rhythms can be compared and any delta the product wants closed
     * is visible in one place. Paragraph gaps, a heading, a nested list and a nested quote are what
     * the pair is for.
     */
    @Test
    fun richMessageTypographyBlockPath() {
        paparazziRule.snapshot { context ->
            topicPostCell(context, RICH_MESSAGE_TYPOGRAPHY_HTML)
        }
    }

    @Test
    fun richMessageTypographyRichBody() {
        RichMessageBody.enabled = true
        try {
            paparazziRule.snapshot { context ->
                topicPostCell(context, RICH_MESSAGE_TYPOGRAPHY_HTML)
            }
        } finally {
            RichMessageBody.enabled = false
        }
    }

    /**
     * The nested shapes the rich run has to lay out on its own: a three-level bullet list, an ordered
     * list with a nested one, and a toggle holding a list. Quotes are ours in this body (they stay in
     * the block tree), so the nesting here is what upstream's renderer owns.
     */
    @Test
    fun richMessageNestingRichBody() {
        RichMessageBody.enabled = true
        try {
            paparazziRule.snapshot { context ->
                topicPostCell(context, RICH_MESSAGE_NESTING_HTML)
            }
        } finally {
            RichMessageBody.enabled = false
        }
    }

    /**
     * The inline constructs upstream's model has a run for: a custom emoji, a twemoji image, a site
     * icon, a hashtag chip, a `[size]` range, a cooked inline checkbox, plus a task list (upstream's
     * checkbox rows). Kept in its own post so the golden stays inside the snapshot's frame.
     */
    @Test
    fun richMessageInlineCases() {
        RichMessageBody.enabled = true
        try {
            paparazziRule.snapshot { context ->
                topicPostCell(context, RICH_MESSAGE_INLINE_HTML)
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
            <p>Inline images: <img src="/images/emoji/twemoji/laughing.png" class="emoji" alt=":laughing:" title=":laughing:">
            and a custom <img src="/uploads/default/original/3X/blob.png" class="emoji emoji-custom" alt=":party_blob:" title=":party_blob:">
            plus a key <svg class="fa d-icon svg-icon" aria-label="keyboard"><use href="#far-keyboard"></use></svg> in the line.</p>
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
            <pre><code class="lang-kotlin">val body = RichMessageBody.enabled</code></pre>
            <hr>
            <p>Done.</p>
        """.trimIndent()


        /**
         * The inline constructs, in a post of their own: a custom emoji, a twemoji image, a site icon
         * (its sprite is not captured in a Paparazzi run, so it falls back to its alt text), a
         * hashtag chip, a `[size]` range and a task list with one checked and one open item.
         */
        /**
         * The typography fixture: paragraph-to-paragraph spacing, a heading, a two-level list and a
         * two-level quote, drawn by both paths in the pair above.
         */
        val RICH_MESSAGE_TYPOGRAPHY_HTML = """
            <p>First paragraph.</p>
            <p>Second paragraph with <b>bold</b> in it.</p>
            <h2>A heading</h2>
            <ul>
              <li>top level item
                <ul>
                  <li>nested item</li>
                  <li>another nested</li>
                </ul>
              </li>
              <li>second top level</li>
            </ul>
            <blockquote><p>Quoted paragraph.</p><blockquote><p>Nested quote.</p></blockquote></blockquote>
            <p>Last paragraph.</p>
        """.trimIndent()

        /** Deep and mixed nesting for [richMessageNestingRichBody]. */
        val RICH_MESSAGE_NESTING_HTML = """
            <ul>
              <li>level one
                <ul>
                  <li>level two
                    <ul>
                      <li>level three</li>
                    </ul>
                  </li>
                  <li>level two again</li>
                </ul>
              </li>
            </ul>
            <ol>
              <li>ordered one
                <ol>
                  <li>ordered nested</li>
                </ol>
              </li>
            </ol>
            <details>
              <summary>A toggle with a list</summary>
              <ul>
                <li>inside the toggle</li>
                <li>and another</li>
              </ul>
            </details>
            <p>After the toggle.</p>
        """.trimIndent()

        val RICH_MESSAGE_INLINE_HTML = """
            <p>Inline: <img src="/images/emoji/twemoji/laughing.png" class="emoji" alt=":laughing:" title=":laughing:">
            and a custom <img src="/uploads/default/original/3X/blob.png" class="emoji emoji-custom" alt=":party_blob:" title=":party_blob:">
            plus a key <svg class="fa d-icon svg-icon" aria-label="keyboard"><use href="#far-keyboard"></use></svg> in the line.
            A done box <input type="checkbox" class="task-list-item-checkbox" checked> and an open one <input type="checkbox" class="task-list-item-checkbox"> inline.</p>
            <p>A <span style="font-size: 150%">bigger run</span> and a
            <a class="hashtag-cooked" href="/tag/dev" data-slug="dev"><svg class="fa d-icon svg-icon" aria-label="dev"><use href="#far-tag"></use></svg><span>dev</span></a> chip.</p>
            <ul class="task-list">
              <li class="task-list-item"><input type="checkbox" class="task-list-item-checkbox" checked> done item</li>
              <li class="task-list-item"><input type="checkbox" class="task-list-item-checkbox"> todo item</li>
            </ul>
            <p>After the task list.</p>
        """.trimIndent()

        /**
         * A body that is half upstream's renderer and half our blocks: the poll, the link card and the
         * callout have no upstream counterpart, so the hybrid body keeps them (and the containers
         * holding them) in our block tree, in the order the post wrote them.
         */
        val RICH_MESSAGE_HYBRID_HTML = """
            <p>An intro paragraph, drawn by upstream's renderer.</p>
            <h2>After the heading</h2>
            <p>Another mapped paragraph.</p>
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
            </div>
            <p>A paragraph between the poll and the link card.</p>
            <aside class="onebox allowlistedgeneric">
              <header class="source"><a href="https://developers.example.dev/tooling/cli">developers.example.dev</a></header>
              <article class="onebox-body">
                <h3><a href="https://developers.example.dev/tooling/cli">CLI tooling</a></h3>
                <p>A short description of the linked page.</p>
              </article>
            </aside>
            <div class="callout callout-note" data-callout-type="note">
              <div class="callout-title">
                <span class="callout-icon"><svg class="fa d-icon svg-icon"><use href="#far-pen-to-square"></use></svg></span>
                <span class="callout-title-inner">Note</span>
              </div>
              <div class="callout-content">
                <p>Callouts stay our own block.</p>
                <ul><li>and keep their nested blocks</li><li>in place</li></ul>
              </div>
            </div>
            <p>An outro paragraph.</p>
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
