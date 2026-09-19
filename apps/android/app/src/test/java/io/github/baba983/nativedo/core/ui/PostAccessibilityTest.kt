package io.github.baba983.nativedo.core.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.html.CookedPostBlock
import org.junit.Test

class PostAccessibilityTest {

    @Test
    fun anImageSpeaksItsAltTextAndItsCaption() {
        val text = postBodyAccessibilityText(
            listOf(
                CookedPostBlock.Text("<p>Before the picture.</p>"),
                CookedPostBlock.Image(
                    src = "https://cdn.example.dev/diagram.png",
                    alt = "A diagram of the request flow",
                    href = null,
                    widthPx = 960,
                    heightPx = 540,
                    captionHtml = "<p>A captioned diagram.</p>",
                ),
            ),
        )

        assertThat(text).isEqualTo("Before the picture.。图片：A diagram of the request flow。A captioned diagram.")
    }

    @Test
    fun anImageWithoutAltFallsBackToItsCaptionThenToAPlainLabel() {
        val captioned = postBodyAccessibilityText(
            listOf(
                CookedPostBlock.Image(
                    src = "https://cdn.example.dev/one.png",
                    alt = "",
                    href = null,
                    captionHtml = "<p>图 1</p>",
                ),
            ),
        )
        assertThat(captioned).isEqualTo("图 1")

        val bare = postBodyAccessibilityText(
            listOf(CookedPostBlock.Image(src = "https://cdn.example.dev/two.png", alt = "", href = null)),
        )
        assertThat(bare).isEqualTo("图片")
    }

    @Test
    fun aCollageSpeaksEveryMember() {
        val text = postBodyAccessibilityText(
            listOf(
                CookedPostBlock.Collage(
                    images = listOf(
                        CookedPostBlock.Image(src = "one.png", alt = "first image", href = null),
                        CookedPostBlock.Image(src = "two.png", alt = "second image", href = null),
                    ),
                ),
            ),
        )

        assertThat(text).isEqualTo("图片：first image。图片：second image")
    }

    @Test
    fun aPollReadsItsQuestionAndOptions() {
        val text = postBodyAccessibilityText(
            listOf(
                CookedPostBlock.Poll(
                    name = "frontend",
                    type = "regular",
                    status = "open",
                    voters = 10,
                    options = listOf(
                        CookedPostBlock.PollOption(id = "a", label = "Tool A", votes = 7),
                        CookedPostBlock.PollOption(id = "b", label = "Tool B", votes = 3),
                    ),
                    title = "Frontend work",
                ),
            ),
        )

        assertThat(text).isEqualTo("投票：Frontend work。Tool A（7 票）。Tool B（3 票）")
    }

    @Test
    fun aLinkCardAndACalloutReadTheirOwnText() {
        val text = postBodyAccessibilityText(
            listOf(
                CookedPostBlock.Onebox(
                    href = "https://developers.example.dev/tooling/cli",
                    domain = "developers.example.dev",
                    siteIconUrl = null,
                    thumbnailUrl = null,
                    title = "CLI tooling",
                    description = null,
                ),
                CookedPostBlock.Callout(
                    type = "note",
                    titleHtml = "Note",
                    icon = "",
                    color = "",
                    html = "<p>Callouts stay our own block.</p>",
                    children = listOf(CookedPostBlock.Text("<p>Callouts stay our own block.</p>")),
                ),
            ),
        )

        assertThat(text).isEqualTo("链接：CLI tooling（developers.example.dev）。Note。Callouts stay our own block.")
    }

    @Test
    fun aCollapsedDetailsBlockDoesNotReadItsHiddenChildren() {
        val collapsed = postBodyAccessibilityText(
            listOf(
                CookedPostBlock.Details(
                    html = "<p>hidden</p>",
                    summary = "More",
                    children = listOf(CookedPostBlock.Text("<p>hidden</p>")),
                    open = false,
                ),
            ),
        )
        assertThat(collapsed).isEqualTo("More")

        val open = postBodyAccessibilityText(
            listOf(
                CookedPostBlock.Details(
                    html = "<p>visible</p>",
                    summary = "More",
                    children = listOf(CookedPostBlock.Text("<p>visible</p>")),
                    open = true,
                ),
            ),
        )
        assertThat(open).isEqualTo("More。visible")
    }

    @Test
    fun anEmptyBodyHasNoDescription() {
        assertThat(postBodyAccessibilityText(emptyList())).isEmpty()
        assertThat(postBodyAccessibilityText(listOf(CookedPostBlock.Rule))).isEmpty()
    }

    @Test
    fun aQuoteReadsItsAuthorAndChildren() {
        val text = postBodyAccessibilityText(
            listOf(
                CookedPostBlock.Quote(
                    title = "alice",
                    html = "",
                    children = listOf(CookedPostBlock.Text("<p>Quoted paragraph.</p>")),
                ),
            ),
        )

        assertThat(text).isEqualTo("alice。Quoted paragraph.")
    }
}
