package io.github.baba983.nativedo.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PostInlineParserTest {
    @Test
    fun parseInlineHtml_mapsLinkEmojiSiteIconAndLineBreak() {
        val inlines = parseInlineHtml(
            """
                hello<a href="/t/topic/1875662/1">topic</a><br>
                <img class="emoji" src="/images/emoji/twemoji/distorted_face.png?v=15" title=":distorted_face:" alt=":distorted_face:">
                <img class="site-icon" src="https://github.com/favicon.ico" alt="">GitHub
            """.trimIndent(),
        )

        assertThat(inlines).containsExactly(
            PostInline.Text("hello"),
            PostInline.Link(
                children = listOf(PostInline.Text("topic")),
                rawUrl = "/t/topic/1875662/1",
            ),
            PostInline.LineBreak,
            PostInline.Emoji(
                rawUrl = "/images/emoji/twemoji/distorted_face.png?v=15",
                fallback = ":distorted_face:",
            ),
            PostInline.SiteIcon(
                rawUrl = "https://github.com/favicon.ico",
                fallback = "",
            ),
            PostInline.Text("GitHub"),
        ).inOrder()
    }

    @Test
    fun parseInlineHtml_mapsBoldItalicCodeAndLargeTextSpan() {
        val inlines = parseInlineHtml(
            """
                <strong>加粗</strong><em>斜体</em><code>val answer = 42</code><span style="font-size: 400%">大字</span>
            """.trimIndent(),
        )

        assertThat(inlines).containsExactly(
            PostInline.Styled(
                style = InlineStyle.Bold,
                children = listOf(PostInline.Text("加粗")),
            ),
            PostInline.Styled(
                style = InlineStyle.Italic,
                children = listOf(PostInline.Text("斜体")),
            ),
            PostInline.Styled(
                style = InlineStyle.Code,
                children = listOf(PostInline.Text("val answer = 42")),
            ),
            PostInline.Styled(
                style = InlineStyle.LargeText,
                children = listOf(PostInline.Text("大字")),
            ),
        ).inOrder()
    }

    @Test
    fun parseInlineHtml_preservesCodeDoubleSpaces() {
        val inlines = parseInlineHtml(
            """
                <code>a  b</code>
            """.trimIndent(),
        )

        assertThat(inlines).containsExactly(
            PostInline.Styled(
                style = InlineStyle.Code,
                children = listOf(PostInline.Text("a  b")),
            ),
        ).inOrder()
    }

    @Test
    fun parseInlineHtml_preservesSeparatorSpaceForIndentedNewlineSeparatedInlineTags() {
        val inlines = parseInlineHtml(
            """
                <strong>Hello</strong>
                    <em>world</em>
            """.trimIndent(),
        )

        assertThat(inlines).containsExactly(
            PostInline.Styled(
                style = InlineStyle.Bold,
                children = listOf(PostInline.Text("Hello")),
            ),
            PostInline.Text(" "),
            PostInline.Styled(
                style = InlineStyle.Italic,
                children = listOf(PostInline.Text("world")),
            ),
        ).inOrder()
    }

    @Test
    fun parseInlineHtml_normalizesWhitespaceInsideLinkChildren() {
        val inlines = parseInlineHtml(
            """
                <a href="/t/topic/1875662/1"><strong>Hello</strong><br>
                    <em>world</em></a>
            """.trimIndent(),
        )

        assertThat(inlines).containsExactly(
            PostInline.Link(
                children = listOf(
                    PostInline.Styled(
                        style = InlineStyle.Bold,
                        children = listOf(PostInline.Text("Hello")),
                    ),
                    PostInline.LineBreak,
                    PostInline.Styled(
                        style = InlineStyle.Italic,
                        children = listOf(PostInline.Text("world")),
                    ),
                ),
                rawUrl = "/t/topic/1875662/1",
            ),
        ).inOrder()
    }

    @Test
    fun parseInlineHtml_replacesDecorativeSvgIconWithAccessibleText() {
        val inlines = parseInlineHtml(
            """
                <svg class="fa d-icon d-icon-far-image svg-icon" aria-label="图片"></svg>附件
            """.trimIndent(),
        )

        assertThat(inlines).containsExactly(
            PostInline.Text("图片附件"),
        )
    }
}
