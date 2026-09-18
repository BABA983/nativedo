package io.github.baba983.nativedo.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.telegram.tgnet.tl.TL_iv

class InlineRichTextTest {

    /** A readable rendering of the tree, so a test can assert the model shape directly. */
    private fun describe(node: TL_iv.RichText?): String = when (node) {
        null -> ""
        is TL_iv.textEmpty -> ""
        is TL_iv.textPlain -> "\"${node.text}\""
        is TL_iv.textConcat -> node.texts.joinToString("+", transform = ::describe)
        is TL_iv.textBold -> "b(${describe(node.text)})"
        is TL_iv.textItalic -> "i(${describe(node.text)})"
        is TL_iv.textUnderline -> "u(${describe(node.text)})"
        is TL_iv.textStrike -> "s(${describe(node.text)})"
        is TL_iv.textFixed -> "code(${describe(node.text)})"
        is TL_iv.textSubscript -> "sub(${describe(node.text)})"
        is TL_iv.textSuperscript -> "sup(${describe(node.text)})"
        is TL_iv.textMarked -> "mark(${describe(node.text)})"
        is TL_iv.textSpoiler -> "spoiler(${describe(node.text)})"
        is TL_iv.textUrl -> "a(${node.url},${describe(node.text)})"
        else -> "?"
    }

    private fun cook(html: String) = describe(cookInlineHtml(html).richText)

    @Test
    fun mapsEveryDiscourseInlineStyleOntoTheModel() {
        val cases = mapOf(
            "<b>x</b>" to "b(\"x\")",
            "<strong>x</strong>" to "b(\"x\")",
            "<i>x</i>" to "i(\"x\")",
            "<em>x</em>" to "i(\"x\")",
            "<u>x</u>" to "u(\"x\")",
            "<span class=\"bbcode-u\">x</span>" to "u(\"x\")",
            "<s>x</s>" to "s(\"x\")",
            "<del>x</del>" to "s(\"x\")",
            "<code>x</code>" to "code(\"x\")",
            "<kbd>x</kbd>" to "code(\"x\")",
            "<sub>x</sub>" to "sub(\"x\")",
            "<sup>x</sup>" to "sup(\"x\")",
            "<mark>x</mark>" to "mark(\"x\")",
            "<span class=\"spoiler\">x</span>" to "spoiler(\"x\")",
            "<span class=\"spoiled\">x</span>" to "spoiler(\"x\")",
            "<span style=\"text-decoration: underline\">x</span>" to "u(\"x\")",
            "<span style=\"text-decoration: line-through\">x</span>" to "s(\"x\")",
        )
        for ((html, expected) in cases) {
            assertThat(cook(html)).isEqualTo(expected)
        }
    }

    @Test
    fun keepsNesting() {
        assertThat(cook("<b>a<i>b</i>c</b>")).isEqualTo("b(\"a\"+i(\"b\")+\"c\")")
        assertThat(cook("<b><span class=\"spoiler\">x</span></b>")).isEqualTo("b(spoiler(\"x\"))")
    }

    @Test
    fun unwrapsUnknownTagsAroundTheirContent() {
        assertThat(cook("<div><span class=\"foo\">a</span><p>b</p></div>")).isEqualTo("\"a\"+\"b\"")
    }

    @Test
    fun readsLinksAndTheirTarget() {
        assertThat(cook("<a href=\"https://linux.do/t/x/1\">x</a>"))
            .isEqualTo("a(https://linux.do/t/x/1,\"x\")")
    }

    @Test
    fun turnsBreaksIntoNewlines() {
        assertThat(cook("a<br>b")).isEqualTo("\"a\"+\"\n\"+\"b\"")
    }

    @Test
    fun keepsEmojiImagesAsTokensWithTheirSpec() {
        val cooked = cookInlineHtml("<p>hi <img src=\"/images/emoji/wave.png\" class=\"emoji\" alt=\":wave:\"></p>")
        assertThat(cooked.inlineImages).hasSize(1)
        assertThat(cooked.inlineImages[0].kind).isEqualTo(InlineImageKind.Emoji)
        assertThat(describe(cooked.richText)).contains(cooked.inlineImages[0].token)
        assertThat(cooked.plainText()).isEqualTo("hi :wave:")
    }

    @Test
    fun keepsHashtagChipsAsTokensWithTheirSpec() {
        val cooked = cookInlineHtml(
            "<a class=\"hashtag-cooked\" href=\"/tag/x\" data-slug=\"x\"><svg><use href=\"#tag\"></use></svg>x</a>",
        )
        assertThat(cooked.inlineHashtagChips).hasSize(1)
        assertThat(cooked.inlineHashtagChips[0].href).isEqualTo("/tag/x")
        assertThat(describe(cooked.richText)).isEqualTo("\"${cooked.inlineHashtagChips[0].token}\"")
        assertThat(cooked.plainText()).isEqualTo("x")
    }

    @Test
    fun marksSizesWithTokenPairsBecauseTheModelHasNoSizeCase() {
        val cooked = cookInlineHtml("<span style=\"font-size: 150%\">x</span>")
        assertThat(cooked.inlineSizes).hasSize(1)
        assertThat(cooked.inlineSizes[0].relativeSize).isEqualTo(1.5f)
        val described = describe(cooked.richText)
        assertThat(described).contains(cooked.inlineSizes[0].startToken)
        assertThat(described).contains("\"x\"")
        assertThat(cooked.plainText()).isEqualTo("x")
    }

    @Test
    fun flattensStyledTextForPreviews() {
        val cooked = cookInlineHtml("<p>Hello <b>world</b> <a href=\"/t/x/1\">topic</a></p>")
        assertThat(cooked.plainText()).isEqualTo("Hello world topic")
    }
}
