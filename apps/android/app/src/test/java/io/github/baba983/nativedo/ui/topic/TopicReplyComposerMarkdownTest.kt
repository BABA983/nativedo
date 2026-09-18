package io.github.baba983.nativedo.ui.topic

import android.text.Editable
import android.text.SpannableStringBuilder
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.telegram.messenger.R

@RunWith(RobolectricTestRunner::class)
class TopicReplyComposerMarkdownTest {

    private fun editable(text: String) = SpannableStringBuilder(text)

    private fun toggle(style: (Editable, Int, Int) -> IntRange, marker: String) {
        val text = editable("x")
        val selection = style(text, 0, 1)
        assertThat(text.toString()).isEqualTo("${marker}x$marker")
        assertThat(selection).isEqualTo(marker.length until (marker.length + 1))

        val unquoted = style(text, selection.first, selection.last + 1)
        assertThat(text.toString()).isEqualTo("x")
        assertThat(unquoted).isEqualTo(0 until 1)
    }

    @Test
    fun boldTogglesWrapAndUnwrap() {
        toggle(TopicReplyComposerMarkdown::bold, "**")
    }

    @Test
    fun italicTogglesWrapAndUnwrap() {
        toggle(TopicReplyComposerMarkdown::italic, "_")
    }

    @Test
    fun monoTogglesWrapAndUnwrap() {
        toggle(TopicReplyComposerMarkdown::mono, "`")
    }

    @Test
    fun strikeTogglesWrapAndUnwrap() {
        toggle(TopicReplyComposerMarkdown::strike, "~~")
    }

    @Test
    fun underlineTogglesWrapAndUnwrapWithBbcodePair() {
        val text = editable("x")
        assertThat(TopicReplyComposerMarkdown.underline(text, 0, 1)).isEqualTo(3 until 4)
        assertThat(text.toString()).isEqualTo("[u]x[/u]")

        val unwrapped = TopicReplyComposerMarkdown.underline(text, 3, 4)
        assertThat(text.toString()).isEqualTo("x")
        assertThat(unwrapped).isEqualTo(0 until 1)
    }

    @Test
    fun spoilerTogglesWrapAndUnwrapWithDiscoursePair() {
        val text = editable("secret")
        assertThat(TopicReplyComposerMarkdown.spoiler(text, 0, 6)).isEqualTo(9 until 15)
        assertThat(text.toString()).isEqualTo("[spoiler]secret[/spoiler]")

        val unwrapped = TopicReplyComposerMarkdown.spoiler(text, 9, 15)
        assertThat(text.toString()).isEqualTo("secret")
        assertThat(unwrapped).isEqualTo(0 until 6)
    }

    @Test
    fun styleAtCaretInsertsThePairAndKeepsTheCaretInside() {
        val text = editable("ab")
        val selection = TopicReplyComposerMarkdown.bold(text, 1, 1)
        assertThat(text.toString()).isEqualTo("a****b")
        assertThat(selection.first).isEqualTo(3)
        assertThat(selection.last + 1).isEqualTo(3)
    }

    @Test
    fun quotePrefixesEverySelectedLineAndTogglesBack() {
        val text = editable("one\ntwo")
        val quoted = TopicReplyComposerMarkdown.quote(text, 0, 7)
        assertThat(text.toString()).isEqualTo("> one\n> two")
        assertThat(quoted).isEqualTo(0 until 11)

        val unquoted = TopicReplyComposerMarkdown.quote(text, quoted.first, quoted.last + 1)
        assertThat(text.toString()).isEqualTo("one\ntwo")
        assertThat(unquoted).isEqualTo(0 until 7)
    }

    @Test
    fun quoteTouchesOnlyTheLinesTheSelectionReaches() {
        val text = editable("one\ntwo\nthree")
        TopicReplyComposerMarkdown.quote(text, 5, 6)
        assertThat(text.toString()).isEqualTo("one\n> two\nthree")
    }

    @Test
    fun linkWrapsTheSelectionIntoMarkdown() {
        val text = editable("see docs")
        val selection = TopicReplyComposerMarkdown.link(text, 4, 8, "https://linux.do")
        val expected = "see [docs](https://linux.do)"
        assertThat(text.toString()).isEqualTo(expected)
        assertThat(selection).isEqualTo(4 until expected.length)
    }

    @Test
    fun linkWithoutSelectionLinksTheUrl() {
        val text = editable("")
        TopicReplyComposerMarkdown.link(text, 0, 0, "https://linux.do")
        assertThat(text.toString()).isEqualTo("[https://linux.do](https://linux.do)")
    }

    @Test
    fun clearStripsInlineMarkersInsideTheSelection() {
        val text = editable("**a** and [spoiler]b[/spoiler]")
        val selection = TopicReplyComposerMarkdown.clear(text, 0, text.length)
        assertThat(text.toString()).isEqualTo("a and b")
        assertThat(selection).isEqualTo(0 until 7)
    }

    @Test
    fun clearAlsoUnquotesLines() {
        val text = editable("> one\n> two")
        TopicReplyComposerMarkdown.clear(text, 0, text.length)
        assertThat(text.toString()).isEqualTo("one\ntwo")
    }

    @Test
    fun toolbarIdsMapToTheirMarkdownActions() {
        val expected = listOf(
            R.id.menu_bold to "**x**",
            R.id.menu_italic to "_x_",
            R.id.menu_mono to "`x`",
            R.id.menu_strike to "~~x~~",
            R.id.menu_underline to "[u]x[/u]",
            R.id.menu_spoiler to "[spoiler]x[/spoiler]",
        )
        for ((itemId, expectedText) in expected) {
            val text = editable("x")
            assertThat(TopicReplyComposerMarkdown.apply(text, itemId, 0, 1)).isNotNull()
            assertThat(text.toString()).isEqualTo(expectedText)
        }
    }

    @Test
    fun quoteAndRegularIdsRouteToTheirActions() {
        val text = editable("one")
        assertThat(TopicReplyComposerMarkdown.apply(text, R.id.menu_quote, 0, 3)).isNotNull()
        assertThat(text.toString()).isEqualTo("> one")

        val styled = editable("**one**")
        assertThat(TopicReplyComposerMarkdown.apply(styled, R.id.menu_regular, 0, styled.length)).isNotNull()
        assertThat(styled.toString()).isEqualTo("one")
    }

    @Test
    fun unknownMenuIdsStayWithTheBaseClass() {
        val text = editable("x")
        assertThat(TopicReplyComposerMarkdown.apply(text, android.R.id.copy, 0, 1)).isNull()
        assertThat(text.toString()).isEqualTo("x")
    }

    @Test
    fun clearAtCaretKeepsTheText() {
        val text = editable("**a**")
        val selection = TopicReplyComposerMarkdown.clear(text, 3, 3)
        assertThat(text.toString()).isEqualTo("**a**")
        assertThat(selection.first).isEqualTo(3)
        assertThat(selection.last).isEqualTo(2)
    }
}
