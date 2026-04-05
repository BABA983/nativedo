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
}
