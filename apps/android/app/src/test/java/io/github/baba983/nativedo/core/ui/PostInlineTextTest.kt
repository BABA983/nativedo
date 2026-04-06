package io.github.baba983.nativedo.core.ui

import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.LineBackgroundSpan
import android.text.style.RelativeSizeSpan
import android.text.style.ReplacementSpan
import android.text.style.StyleSpan
import android.text.style.StrikethroughSpan
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
        assertThat(text.getSpans(0, text.length, InlineCodeSpan::class.java)).hasLength(1)
        assertThat(text.getSpans(0, text.length, ReplacementSpan::class.java)).hasLength(1)
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
    fun buildPostInlineText_appliesStrikeAndInlineCodeStyle() {
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
        assertThat(text.getSpans(0, text.length, InlineCodeSpan::class.java)).hasLength(1)
        assertThat(text.getSpans(0, text.length, LineBackgroundSpan::class.java)).isEmpty()
        assertThat(text.getSpans(0, text.length, ReplacementSpan::class.java)).hasLength(1)
    }

    @Test
    fun buildPostInlineText_appliesPillChromeToHashtagLinkOnly() {
        val rendered = buildPostInlineText(
            inlines = listOf(
                PostInline.Link(
                    children = listOf(PostInline.Text("#集中帖")),
                    rawUrl = "/tag/%E9%9B%86%E4%B8%AD%E5%B8%96",
                ),
                PostInline.Text(" "),
                PostInline.Link(
                    children = listOf(PostInline.Text("帖子")),
                    rawUrl = "/t/topic/1875662/1",
                ),
            ),
            baseUrl = "https://linux.do/",
            onTopicClick = {},
            onUserClick = null,
            textSizePx = 16f,
        )

        val text = rendered.text as Spanned
        val hashtagSpans = text.getSpans(0, text.length, HashtagChipSpan::class.java)

        assertThat(text.toString()).isEqualTo("#集中帖 帖子")
        assertThat(text.getSpans(0, text.length, ClickableSpan::class.java)).hasLength(2)
        assertThat(hashtagSpans).hasLength(1)
        assertThat(text.getSpanStart(hashtagSpans.single())).isEqualTo(0)
        assertThat(text.getSpanEnd(hashtagSpans.single())).isEqualTo(4)
        assertThat(text.getSpans(0, text.length, LineBackgroundSpan::class.java)).isEmpty()
        assertThat(text.getSpans(0, text.length, ReplacementSpan::class.java)).hasLength(1)
    }

    @Test
    fun buildPostInlineText_insertsOuterSpacingAroundInlineChromeWhenAdjacentToText() {
        val rendered = buildPostInlineText(
            inlines = listOf(
                PostInline.Text("前"),
                PostInline.Styled(
                    style = InlineStyle.Code,
                    children = listOf(PostInline.Text("Discourse")),
                ),
                PostInline.Text("后"),
                PostInline.Link(
                    children = listOf(PostInline.Text("#原创")),
                    rawUrl = "/tag/%E5%8E%9F%E5%88%9B",
                ),
                PostInline.Text("尾"),
            ),
            baseUrl = "https://linux.do/",
            onTopicClick = null,
            onUserClick = null,
            textSizePx = 16f,
        )

        val text = rendered.text as Spanned
        val codeSpans = text.getSpans(0, text.length, InlineCodeSpan::class.java)
        val hashtagSpans = text.getSpans(0, text.length, HashtagChipSpan::class.java)

        assertThat(text.toString()).isEqualTo("前Discourse后#原创尾")
        assertThat(codeSpans).hasLength(1)
        assertThat(hashtagSpans).hasLength(1)
        assertThat(
            codeSpans.map { span ->
                text.subSequence(text.getSpanStart(span), text.getSpanEnd(span)).toString()
            },
        ).containsExactly("Discourse")
        assertThat(
            hashtagSpans.map { span ->
                text.subSequence(text.getSpanStart(span), text.getSpanEnd(span)).toString()
            },
        ).containsExactly("#原创")
    }

    @Test
    fun buildPostInlineText_insertsLeadingSpacingForHashtagAtLineStart() {
        val rendered = buildPostInlineText(
            inlines = listOf(
                PostInline.Text("说明\n"),
                PostInline.Link(
                    children = listOf(PostInline.Text("#AFF")),
                    rawUrl = "/tag/AFF",
                ),
                PostInline.Text(" 标签"),
            ),
            baseUrl = "https://linux.do/",
            onTopicClick = null,
            onUserClick = null,
            textSizePx = 16f,
        )

        val text = rendered.text as Spanned

        assertThat(text.toString()).isEqualTo("说明\n#AFF 标签")
        assertThat(text.getSpans(0, text.length, HashtagChipSpan::class.java)).hasLength(1)
    }
}
