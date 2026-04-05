package io.github.baba983.nativedo.core.ui

import android.text.Spanned
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.StrikethroughSpan
import android.text.style.TypefaceSpan
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PostInlineTextTest {
    @Test
    fun buildPostInlineText_appliesLargeTextCodeAndInternalLinkSpans() {
        val rendered = buildPostInlineText(
            inlines = listOf(
                PostInline.Link(
                    children = listOf(PostInline.Text("帖子")),
                    rawUrl = "/t/topic/1875662/1",
                ),
                PostInline.Text(" "),
                PostInline.Styled(
                    style = InlineStyle.LargeText,
                    children = listOf(PostInline.Text("大字")),
                ),
                PostInline.Text(" "),
                PostInline.Styled(
                    style = InlineStyle.Code,
                    children = listOf(PostInline.Text("val answer = 42")),
                ),
            ),
            baseUrl = "https://linux.do/",
            onTopicClick = {},
            onUserClick = null,
            textSizePx = 16f,
        )

        val text = rendered.text as Spanned
        assertThat(text.getSpans(0, text.length, ClickableSpan::class.java)).hasLength(1)
        assertThat(text.getSpans(0, text.length, RelativeSizeSpan::class.java)).hasLength(1)
        assertThat(text.getSpans(0, text.length, TypefaceSpan::class.java)).hasLength(1)
    }

    @Test
    fun buildPostInlineText_collectsEmojiAndSiteIconForDeferredLoading() {
        val rendered = buildPostInlineText(
            inlines = listOf(
                PostInline.Emoji(
                    rawUrl = "/images/emoji/twemoji/distorted_face.png?v=15",
                    fallback = ":distorted_face:",
                ),
                PostInline.SiteIcon(
                    rawUrl = "https://github.com/favicon.ico",
                    fallback = "",
                ),
            ),
            baseUrl = "https://linux.do/",
            onTopicClick = null,
            onUserClick = null,
            textSizePx = 16f,
        )

        assertThat(rendered.pendingImages.count()).isEqualTo(2)
        assertThat(rendered.text.toString()).doesNotContain("inline_image_")
    }

    @Test
    fun buildPostInlineText_removesUnderlineAndStylesInternalUserLinkAsMention() {
        val rendered = buildPostInlineText(
            inlines = listOf(
                PostInline.Link(
                    children = listOf(PostInline.Text("@baba")),
                    rawUrl = "/u/baba/summary",
                ),
                PostInline.Text(" "),
                PostInline.Link(
                    children = listOf(PostInline.Text("帖子")),
                    rawUrl = "/t/topic/1875662/1",
                ),
            ),
            baseUrl = "https://linux.do/",
            onTopicClick = {},
            onUserClick = {},
            textSizePx = 16f,
        )

        val text = rendered.text as Spanned
        val clickableSpans = text.getSpans(0, text.length, ClickableSpan::class.java)
        val styleSpans = text.getSpans(0, text.length, StyleSpan::class.java)
        val mentionSpan = clickableSpans.first { text.getSpanStart(it) == 0 }
        val topicSpan = clickableSpans.first { text.getSpanStart(it) > 0 }
        val mentionPaint = TextPaint()
        val topicPaint = TextPaint()

        mentionSpan.updateDrawState(mentionPaint)
        topicSpan.updateDrawState(topicPaint)

        assertThat(mentionPaint.isUnderlineText).isFalse()
        assertThat(styleSpans).hasLength(1)
        assertThat(text.getSpanStart(styleSpans.single())).isEqualTo(0)
        assertThat(topicPaint.isUnderlineText).isFalse()
        assertThat(topicPaint.typeface?.isBold ?: false).isFalse()
    }

    @Test
    fun buildPostInlineText_appliesStrikeAndInlineCodeChrome() {
        val rendered = buildPostInlineText(
            inlines = listOf(
                PostInline.Styled(
                    style = InlineStyle.Strike,
                    children = listOf(PostInline.Text("旧内容")),
                ),
                PostInline.Text(" "),
                PostInline.Styled(
                    style = InlineStyle.Code,
                    children = listOf(PostInline.Text("val answer = 42")),
                ),
            ),
            baseUrl = "https://linux.do/",
            onTopicClick = null,
            onUserClick = null,
            textSizePx = 16f,
        )

        val text = rendered.text as Spanned

        assertThat(text.getSpans(0, text.length, StrikethroughSpan::class.java)).hasLength(1)
        assertThat(text.getSpans(0, text.length, TypefaceSpan::class.java)).hasLength(1)
        assertThat(text.getSpans(0, text.length, BackgroundColorSpan::class.java)).hasLength(1)
    }
}
