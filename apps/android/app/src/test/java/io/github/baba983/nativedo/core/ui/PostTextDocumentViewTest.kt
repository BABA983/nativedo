package io.github.baba983.nativedo.core.ui

import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PostTextDocumentViewTest {
    @Test
    fun buildTextDocumentSpannable_appliesEntitySpans() {
        val rendered = buildTextDocumentSpannable(
            document = TextDocument(
                text = "Hello @baba topic code",
                entities = listOf(
                    TextEntity.Bold(start = 6, end = 11),
                    TextEntity.Mention(start = 6, end = 11, username = "baba"),
                    TextEntity.Link(start = 12, end = 17, rawUrl = "/t/topic/42/1"),
                    TextEntity.InlineCode(start = 18, end = 22),
                ),
            ),
            baseUrl = "https://linux.do",
            onTopicClick = {},
            onUserClick = {},
            textSizePx = 16f,
            inlineCodeBackgroundColor = 0x33000000,
        )

        val text = rendered.text as Spanned

        assertThat(text.getSpans(0, text.length, StyleSpan::class.java)).isNotEmpty()
        assertThat(text.getSpans(0, text.length, ClickableSpan::class.java)).hasLength(2)
        assertThat(text.getSpans(0, text.length, BackgroundColorSpan::class.java)).hasLength(1)
    }

    @Test
    fun buildTextDocumentSpannable_replacesInlineImagesWithSpans() {
        val rendered = buildTextDocumentSpannable(
            document = TextDocument(
                text = ":wave: docs",
                entities = listOf(
                    TextEntity.Emoji(
                        start = 0,
                        end = 6,
                        rawUrl = "/images/emoji/twemoji/wave.png",
                        fallback = ":wave:",
                    ),
                    TextEntity.SiteIcon(
                        start = 6,
                        end = 6,
                        rawUrl = "https://linux.do/favicon.ico",
                        fallback = "",
                    ),
                ),
            ),
            baseUrl = "https://linux.do",
            onTopicClick = null,
            onUserClick = null,
            textSizePx = 16f,
            inlineCodeBackgroundColor = 0x33000000,
        )

        val text = rendered.text as Spanned

        assertThat(rendered.pendingImages.count()).isEqualTo(2)
        assertThat(text.getSpans(0, text.length, CenteredInlineImageSpan::class.java)).hasLength(2)
    }
}
