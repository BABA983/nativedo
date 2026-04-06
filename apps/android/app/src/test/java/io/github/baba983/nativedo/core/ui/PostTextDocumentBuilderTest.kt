package io.github.baba983.nativedo.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PostTextDocumentBuilderTest {
    @Test
    fun buildTextDocument_preservesNormalizedSpaceInsideTransparentWrapper() {
        val document = buildTextDocumentFromHtml("foo<span> </span>bar")

        assertThat(document.text).isEqualTo("foo bar")
        assertThat(document.entities).isEmpty()
    }

    @Test
    fun buildTextDocument_preservesSpaceBetweenSiteIconAndTransparentWrapperText() {
        val document = buildTextDocumentFromHtml(
            """
                <img class="site-icon" src="https://linux.do/favicon.ico" alt=""><span> </span>GitHub
            """.trimIndent(),
        )

        assertThat(document.text).isEqualTo(" GitHub")
        assertThat(document.entities).containsExactly(
            TextEntity.SiteIcon(start = 0, end = 0, rawUrl = "https://linux.do/favicon.ico", fallback = ""),
        )
    }

    @Test
    fun buildTextDocument_flattensBoldInlineCodeMentionAndLink() {
        val document = buildTextDocumentFromHtml(
            """
                Hello <strong>world</strong> <code>val x = 1</code>
                <a href="/u/baba983">@baba983</a>
                <a href="/t/topic/42/1">topic</a>
            """.trimIndent(),
        )

        assertThat(document.text).isEqualTo("Hello world val x = 1 @baba983 topic")
        assertThat(document.entities).containsExactly(
            TextEntity.Bold(start = 6, end = 11),
            TextEntity.InlineCode(start = 12, end = 21),
            TextEntity.Mention(start = 22, end = 30, username = "baba983"),
            TextEntity.Link(start = 31, end = 36, rawUrl = "/t/topic/42/1"),
        ).inOrder()
    }

    @Test
    fun buildTextDocument_prefixesHashtagCookedLinks() {
        val document = buildTextDocumentFromHtml(
            """
                <a class="hashtag-cooked" href="/tag/%E9%9B%86%E4%B8%AD%E5%B8%96" data-type="tag">
                  <span class="hashtag-tag-icon"><svg class="fa d-icon d-icon-tag svg-icon"></svg></span>
                  <span>集中帖</span>
                </a>
            """.trimIndent(),
        )

        assertThat(document.text).isEqualTo("#集中帖")
        assertThat(document.entities).containsExactly(
            TextEntity.Link(
                start = 0,
                end = 4,
                rawUrl = "/tag/%E9%9B%86%E4%B8%AD%E5%B8%96",
            ),
        )
    }

    @Test
    fun buildTextDocument_flattensMentionStatusEmojiWithoutInjectedSpacing() {
        val document = buildTextDocumentFromHtml(
            """
                <a class="mention" href="/u/neo">@Neo<span class="user-status-message-wrapper">
                  <span class="fk-d-tooltip__trigger user-status-message">
                    <span class="fk-d-tooltip__trigger-container">
                      <img src="https://cdn.linux.do/images/emoji/twemoji/lollipop.png?v=15" alt="lollipop" class="emoji">
                    </span>
                  </span>
                </span></a>
            """.trimIndent(),
        )

        assertThat(document.text).isEqualTo("@Neolollipop")
        assertThat(document.entities).containsExactly(
            TextEntity.Mention(start = 0, end = 12, username = "neo"),
            TextEntity.Emoji(
                start = 4,
                end = 12,
                rawUrl = "https://cdn.linux.do/images/emoji/twemoji/lollipop.png?v=15",
                fallback = "lollipop",
            ),
        ).inOrder()
    }

    @Test
    fun buildTextDocument_preservesEmojiSiteIconAndRawInlineCodeWhitespace() {
        val document = buildTextDocumentFromHtml(
            """
                <img class="emoji" src="/emoji/smile.png" title=":smile:" alt=":smile:">
                <img class="site-icon" src="https://linux.do/favicon.ico" alt="">
                <code>  two  spaces  </code>
            """.trimIndent(),
        )

        assertThat(document.text).isEqualTo(":smile:  two  spaces  ")
        assertThat(document.entities).containsExactly(
            TextEntity.Emoji(start = 0, end = 7, rawUrl = "/emoji/smile.png", fallback = ":smile:"),
            TextEntity.SiteIcon(start = 7, end = 7, rawUrl = "https://linux.do/favicon.ico", fallback = ""),
            TextEntity.InlineCode(start = 7, end = 22),
        ).inOrder()
    }

    @Test
    fun buildTextDocument_preservesTrailingInlineCodeSpaceInEntityRange() {
        val document = buildTextDocumentFromHtml("<code>foo </code>")

        assertThat(document.text).isEqualTo("foo ")
        assertThat(document.entities).containsExactly(
            TextEntity.InlineCode(start = 0, end = 4),
        )
    }

    @Test
    fun buildTextDocument_ignoresNormalImageFallbackTextButKeepsEmojiText() {
        val document = buildTextDocumentFromHtml(
            """
                <img src="https://cdn3.linux.do/original/demo.png" alt="image">
                <br>
                <img class="emoji" src="/images/emoji/twemoji/grin.png?v=14" title=":grin:" alt=":grin:">支持一手
            """.trimIndent(),
        )

        assertThat(document.text).isEqualTo("\n:grin:支持一手")
        assertThat(document.entities).containsExactly(
            TextEntity.Emoji(
                start = 1,
                end = 7,
                rawUrl = "/images/emoji/twemoji/grin.png?v=14",
                fallback = ":grin:",
            ),
        )
    }

    @Test
    fun buildTextDocument_keepsFallbackTextForInlineLinkedImage() {
        val document = buildTextDocumentFromHtml(
            """
                <a href="https://example.com/logo">
                  <img src="https://cdn.example.com/logo.png" alt="logo">
                </a>
            """.trimIndent(),
        )

        assertThat(document.text).isEqualTo("logo")
        assertThat(document.entities).containsExactly(
            TextEntity.Link(
                start = 0,
                end = 4,
                rawUrl = "https://example.com/logo",
            ),
        )
    }
}
