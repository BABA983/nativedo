package io.github.baba983.nativedo.core.ui

import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.LineBackgroundSpan
import android.text.style.ReplacementSpan
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
        )

        val text = rendered.text as Spanned

        assertThat(text.getSpans(0, text.length, StyleSpan::class.java)).isNotEmpty()
        assertThat(text.getSpans(0, text.length, ClickableSpan::class.java)).hasLength(2)
        assertThat(text.getSpans(0, text.length, InlineCodeSpan::class.java)).hasLength(1)
        assertThat(text.getSpans(0, text.length, ReplacementSpan::class.java)).hasLength(1)
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
        )

        val text = rendered.text as Spanned

        assertThat(rendered.pendingImages.count()).isEqualTo(2)
        assertThat(text.getSpans(0, text.length, CenteredInlineImageSpan::class.java)).hasLength(2)
    }

    @Test
    fun buildTextDocumentSpannable_keepsOuterLinkWhenInnerEmojiBecomesInlineImage() {
        val rendered = buildTextDocumentSpannable(
            document = TextDocument(
                text = "go :wave:",
                entities = listOf(
                    TextEntity.Link(start = 0, end = 9, rawUrl = "/t/topic/42/1"),
                    TextEntity.Emoji(
                        start = 3,
                        end = 9,
                        rawUrl = "/images/emoji/twemoji/wave.png",
                        fallback = ":wave:",
                    ),
                ),
            ),
            baseUrl = "https://linux.do",
            onTopicClick = {},
            onUserClick = null,
            textSizePx = 16f,
        )

        val text = rendered.text as Spanned

        assertThat(text.getSpans(0, text.length, ClickableSpan::class.java)).hasLength(1)
        assertThat(text.getSpans(0, text.length, CenteredInlineImageSpan::class.java)).hasLength(1)
    }

    @Test
    fun buildTextDocumentSpannable_keepsMentionWhenStatusEmojiBecomesInlineImage() {
        val rendered = buildTextDocumentSpannable(
            document = TextDocument(
                text = "@Neolollipop",
                entities = listOf(
                    TextEntity.Mention(start = 0, end = 12, username = "neo"),
                    TextEntity.Emoji(
                        start = 4,
                        end = 12,
                        rawUrl = "https://cdn.linux.do/images/emoji/twemoji/lollipop.png?v=15",
                        fallback = "lollipop",
                    ),
                ),
            ),
            baseUrl = "https://linux.do",
            onTopicClick = null,
            onUserClick = {},
            textSizePx = 16f,
        )

        val text = rendered.text as Spanned

        assertThat(text.getSpans(0, text.length, ClickableSpan::class.java)).hasLength(1)
        assertThat(text.getSpans(0, text.length, CenteredInlineImageSpan::class.java)).hasLength(1)
    }

    @Test
    fun buildTextDocumentSpannable_appliesPillChromeToHashtagLinksOnly() {
        val rendered = buildTextDocumentSpannable(
            document = TextDocument(
                text = "#集中帖 topic",
                entities = listOf(
                    TextEntity.Link(start = 0, end = 4, rawUrl = "/tag/%E9%9B%86%E4%B8%AD%E5%B8%96"),
                    TextEntity.Link(start = 5, end = 10, rawUrl = "/t/topic/42/1"),
                ),
            ),
            baseUrl = "https://linux.do",
            onTopicClick = null,
            onUserClick = null,
            textSizePx = 16f,
        )

        val text = rendered.text as Spanned
        val clickableSpans = text.getSpans(0, text.length, ClickableSpan::class.java)
        val hashtagSpans = text.getSpans(0, text.length, HashtagChipSpan::class.java)
        val tagSpan = clickableSpans.first { text.getSpanStart(it) == 0 }
        val topicSpan = clickableSpans.first { text.getSpanStart(it) == 5 }
        val tagPaint = TextPaint().apply { linkColor = 0xFF3388FF.toInt() }
        val topicPaint = TextPaint().apply { linkColor = 0xFF3388FF.toInt() }

        tagSpan.updateDrawState(tagPaint)
        topicSpan.updateDrawState(topicPaint)

        assertThat(text.toString()).isEqualTo("#集中帖 topic")
        assertThat(hashtagSpans).hasLength(1)
        assertThat(text.getSpanStart(hashtagSpans.single())).isEqualTo(0)
        assertThat(text.getSpanEnd(hashtagSpans.single())).isEqualTo(4)
        assertThat(tagPaint.isUnderlineText).isFalse()
        assertThat(topicPaint.isUnderlineText).isFalse()
        assertThat(text.getSpans(0, text.length, LineBackgroundSpan::class.java)).isEmpty()
        assertThat(text.getSpans(0, text.length, ReplacementSpan::class.java)).hasLength(1)
    }

    @Test
    fun buildTextDocumentSpannable_insertsOuterSpacingAroundInlineChromeWhenAdjacentToText() {
        val rendered = buildTextDocumentSpannable(
            document = TextDocument(
                text = "前code后#原创尾",
                entities = listOf(
                    TextEntity.InlineCode(start = 1, end = 5),
                    TextEntity.Link(start = 6, end = 9, rawUrl = "/tag/%E5%8E%9F%E5%88%9B"),
                ),
            ),
            baseUrl = "https://linux.do",
            onTopicClick = null,
            onUserClick = null,
            textSizePx = 16f,
        )

        val text = rendered.text as Spanned
        val codeSpans = text.getSpans(0, text.length, InlineCodeSpan::class.java)
        val hashtagSpans = text.getSpans(0, text.length, HashtagChipSpan::class.java)

        assertThat(text.toString()).isEqualTo("前code后#原创尾")
        assertThat(codeSpans).hasLength(1)
        assertThat(hashtagSpans).hasLength(1)
        assertThat(
            codeSpans.map { span ->
                text.subSequence(text.getSpanStart(span), text.getSpanEnd(span)).toString()
            },
        ).containsExactly("code")
        assertThat(
            hashtagSpans.map { span ->
                text.subSequence(text.getSpanStart(span), text.getSpanEnd(span)).toString()
            },
        ).containsExactly("#原创")
    }

    @Test
    fun buildTextDocumentSpannable_insertsLeadingSpacingForHashtagAtLineStart() {
        val rendered = buildTextDocumentSpannable(
            document = TextDocument(
                text = "说明\n#AFF 标签",
                entities = listOf(
                    TextEntity.Link(start = 3, end = 7, rawUrl = "/tag/AFF"),
                ),
            ),
            baseUrl = "https://linux.do",
            onTopicClick = null,
            onUserClick = null,
            textSizePx = 16f,
        )

        val text = rendered.text as Spanned

        assertThat(text.toString()).isEqualTo("说明\n#AFF 标签")
        assertThat(text.getSpans(0, text.length, HashtagChipSpan::class.java)).hasLength(1)
    }
}
