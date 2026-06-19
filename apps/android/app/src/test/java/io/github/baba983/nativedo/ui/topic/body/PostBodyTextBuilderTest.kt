package io.github.baba983.nativedo.ui.topic.body

import android.graphics.Paint
import android.text.TextPaint
import android.text.style.ReplacementSpan
import android.view.View
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.ui.ChipBackgroundSpan
import io.github.baba983.nativedo.core.ui.DiscourseTagChipSpan
import io.github.baba983.nativedo.core.ui.PostBodyClickableSpan
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.Emoji
import org.telegram.ui.Cells.TextSelectionHelper
import org.telegram.ui.Components.TextStyleSpan
import kotlin.math.abs
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
class PostBodyTextBuilderTest {
    private val baseUrl = "https://linux.do/"

    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun build_emitsLinkRegionForExternalLink() {
        val builder = builder()

        val built = builder.build("""<p>Open <a href="https://example.com/a">example</a></p>""")

        assertThat(built.links.map { it.url }).containsExactly("https://example.com/a")
        assertThat(built.spannable.toString()).contains("example")
    }

    @Test
    fun build_emitsClickableSpanAndResolvedUrlForUserMention() {
        val clickedUsers = mutableListOf<String>()
        val builder = builder(onUserClick = clickedUsers::add)

        val built = builder.build("""<p>Hello <a class="mention" href="/u/baba">@baba</a></p>""")
        built.links.single().onClick()

        assertThat(built.links.map { it.url }).containsExactly("https://linux.do/u/baba")
        assertThat(clickedUsers).containsExactly("baba")
    }

    @Test
    fun build_emitsClickableSpanForTopicLink() {
        val clickedTopics = mutableListOf<Long>()
        val builder = builder(onTopicClick = clickedTopics::add)

        val built = builder.build("""<p>See <a href="/t/native-client/12345/6">topic</a></p>""")
        built.links.single().onClick()

        assertThat(built.links.map { it.url }).containsExactly("https://linux.do/t/native-client/12345/6")
        assertThat(clickedTopics).containsExactly(12345L)
    }

    @Test
    fun build_emitsClickableSpanForHashtagAsOpenLink() {
        val openedLinks = mutableListOf<String>()
        val builder = builder(onLinkClick = openedLinks::add)

        val built = builder.build("""<p><a class="hashtag" href="/tag/android">#android</a></p>""")
        built.links.single().onClick()

        assertThat(built.links.map { it.url }).containsExactly("https://linux.do/tag/android")
        assertThat(openedLinks).containsExactly("https://linux.do/tag/android")
    }

    @Test
    fun build_extractsEmojiAndSiteIconInlineImages() {
        val builder = builder()

        val built = builder.build(
            """
                <p>
                  Hi <img class="emoji" src="/images/emoji/twemoji/wave.png" title=":wave:" alt=":wave:">
                  <img class="site-icon" src="https://cdn.example.dev/site.png" alt="site">
                </p>
            """.trimIndent(),
        )

        assertThat(built.pendingImages.map { it.resolvedUrl }).containsExactly(
            "https://linux.do/images/emoji/twemoji/wave.png",
            "https://cdn.example.dev/site.png",
        ).inOrder()
        assertThat(built.spannable.toString()).contains("￼")
        assertThat(built.spannable.toString()).doesNotContain("inline_image_")
        assertThat(
            built.spannable.getSpans(0, built.spannable.length, TextSelectionHelper.IgnoreCopySpannable::class.java),
        ).hasLength(2)
    }

    @Test
    fun build_inlineSvgIconPreservesParagraphFontMetrics() {
        val builder = builder(
            svgSpriteMarkup = """
                <svg xmlns="http://www.w3.org/2000/svg">
                  <symbol id="copy" viewBox="0 0 16 16">
                    <path d="M3 1h8v2H5v8H3z"/>
                  </symbol>
                </svg>
            """.trimIndent(),
        )

        val built = builder.build(
            """<p>Copy <svg class="fa d-icon d-icon-copy svg-icon"><use href="#copy"></use></svg> now</p>""",
            textSizePx = 18f,
        )
        val span = built.spannable
            .getSpans(0, built.spannable.length, ReplacementSpan::class.java)
            .filterNot { it is TextSelectionHelper.IgnoreCopySpannable }
            .single()
        val spanStart = built.spannable.getSpanStart(span)
        val spanEnd = built.spannable.getSpanEnd(span)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f
        }
        val original = paint.fontMetricsInt
        val fm = Paint.FontMetricsInt()

        val width = span.getSize(paint, built.spannable, spanStart, spanEnd, fm)

        assertThat(width).isGreaterThan(0)
        assertThat(fm.ascent).isEqualTo(original.ascent)
        assertThat(fm.descent).isEqualTo(original.descent)
        assertThat(fm.top).isEqualTo(original.top)
        assertThat(fm.bottom).isEqualTo(original.bottom)
        assertThat(fm.leading).isEqualTo(original.leading)
    }

    @Test
    fun build_inlineEmojiPreservesParagraphFontMetrics() {
        val builder = builder()

        val built = builder.build(
            """<p>Hi <img class="emoji" src="/images/emoji/twemoji/wave.png" title=":wave:" alt=":wave:"> now</p>""",
            textSizePx = 18f,
        )
        val span = built.spannable
            .getSpans(0, built.spannable.length, ReplacementSpan::class.java)
            .filterNot { it is TextSelectionHelper.IgnoreCopySpannable }
            .single()
        val spanStart = built.spannable.getSpanStart(span)
        val spanEnd = built.spannable.getSpanEnd(span)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f
        }
        val original = paint.fontMetricsInt
        val fm = Paint.FontMetricsInt()
        val targetSize = (maxOf(1, abs(original.ascent) + abs(original.descent)) * 0.90f).roundToInt()

        val width = span.getSize(paint, built.spannable, spanStart, spanEnd, fm)

        assertThat(width).isEqualTo(targetSize)
        assertThat(fm.ascent).isEqualTo(original.ascent)
        assertThat(fm.descent).isEqualTo(original.descent)
        assertThat(fm.top).isEqualTo(original.top)
        assertThat(fm.bottom).isEqualTo(original.bottom)
        assertThat(fm.leading).isEqualTo(original.leading)
    }

    @Test
    fun build_unicodeEmojiUsesTelegramBaselineAlignedSpanBounds() {
        val builder = builder()

        val built = builder.build("""<p>Hi 😄 now</p>""", textSizePx = 18f)
        val emojiSpan = built.spannable
            .getSpans(0, built.spannable.length, Emoji.EmojiSpan::class.java)
            .single()
        val spanStart = built.spannable.getSpanStart(emojiSpan)
        val spanEnd = built.spannable.getSpanEnd(emojiSpan)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f
        }
        val fm = Paint.FontMetricsInt()
        val size = emojiSpan.getSize(paint, built.spannable, spanStart, spanEnd, fm)
        val baselineY = 32

        val bounds = emojiSpan.getSelectionBounds(
            paint,
            built.spannable,
            spanStart,
            spanEnd,
            10f,
            0,
            baselineY,
            48,
        )

        assertThat(bounds.top).isEqualTo((baselineY - size).toFloat())
        assertThat(bounds.bottom).isEqualTo(baselineY.toFloat())
    }

    @Test
    fun build_mapsInlineSpoilerClassToTelegramSpoilerSpan() {
        val builder = builder()

        val built = builder.build(
            """<p>Visible <span class="spoiler"><span style="font-size:150%"><span class="bbcode-u">hidden text</span></span></span> after</p>""",
        )

        val spoilers = built.spannable.getSpans(0, built.spannable.length, TextStyleSpan::class.java)
        assertThat(spoilers).hasLength(1)
        val spoiler = spoilers.single()
        assertThat(spoiler.isSpoiler).isTrue()
        assertThat(spoiler.styleFlags and TextStyleSpan.FLAG_STYLE_UNDERLINE).isNotEqualTo(0)
        assertThat(
            built.spannable.subSequence(
                built.spannable.getSpanStart(spoiler),
                built.spannable.getSpanEnd(spoiler),
            ).toString(),
        ).isEqualTo("hidden text")
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = AndroidUtilities.dp(16f).toFloat()
        }
        spoiler.updateMeasureState(paint)
        assertThat(paint.textSize).isEqualTo(AndroidUtilities.dp(24f).toFloat())
        assertThat(built.spannable.toString()).doesNotContain("__nd_spoiler_")
        assertThat(built.spannable.toString()).doesNotContain("__nd_inline_style_")
    }

    @Test
    fun build_mapsInlineSpoiledClassToTelegramSpoilerSpan() {
        val builder = builder()

        val built = builder.build("""<p>Visible <span class="spoiled">hidden text</span> after</p>""")

        assertThat(
            built.spannable.getSpans(0, built.spannable.length, TextStyleSpan::class.java)
                .single()
                .isSpoiler,
        ).isTrue()
    }

    @Test
    fun build_mergesDiscourseAndHtmlTextStylesIntoTelegramSpan() {
        val builder = builder()

        val built = builder.build(
            """<strong><em><span style="font-size:150%; text-decoration: underline line-through"><span class="bbcode-u">等</span></span></em></strong>""",
        )

        val styles = built.spannable.getSpans(0, built.spannable.length, TextStyleSpan::class.java)
        val text = built.spannable.toString()
        val start = text.indexOf("等")
        assertThat(start).isAtLeast(0)
        assertThat(styles).hasLength(1)
        val style = styles.single()
        assertThat(built.spannable.getSpanStart(style)).isEqualTo(start)
        assertThat(built.spannable.getSpanEnd(style)).isEqualTo(start + 1)
        assertThat(style.styleFlags and TextStyleSpan.FLAG_STYLE_BOLD).isNotEqualTo(0)
        assertThat(style.styleFlags and TextStyleSpan.FLAG_STYLE_ITALIC).isNotEqualTo(0)
        assertThat(style.styleFlags and TextStyleSpan.FLAG_STYLE_STRIKE).isNotEqualTo(0)
        assertThat(style.styleFlags and TextStyleSpan.FLAG_STYLE_UNDERLINE).isNotEqualTo(0)

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = AndroidUtilities.dp(16f).toFloat()
        }
        style.updateMeasureState(paint)
        assertThat(paint.textSize).isEqualTo(AndroidUtilities.dp(24f).toFloat())
        assertThat(paint.flags and Paint.UNDERLINE_TEXT_FLAG).isNotEqualTo(0)
        assertThat(paint.flags and Paint.STRIKE_THRU_TEXT_FLAG).isNotEqualTo(0)
    }

    @Test
    fun build_rendersHashtagCookedAsSingleTagChipSpan() {
        val openedLinks = mutableListOf<String>()
        val builder = builder(
            onLinkClick = openedLinks::add,
            svgSpriteMarkup = """
                <svg xmlns="http://www.w3.org/2000/svg">
                  <symbol id="circle-question" viewBox="0 0 16 16">
                    <path d="M8 1a7 7 0 1 0 0 14A7 7 0 0 0 8 1z"/>
                  </symbol>
                </svg>
            """.trimIndent(),
        )

        val built = builder.build(
            """
                <a class="hashtag-cooked" href="/tag/1436-tag/1436">
                  <span class="hashtag-tag-icon" style="--color1: #669d34">
                    <svg class="fa d-icon d-icon-circle-question svg-icon"><use href="#circle-question"></use></svg>
                  </span>
                  <span>快问快答</span>
                </a>
            """.trimIndent(),
        )

        val tagChipSpan = built.spannable.getSpans(0, built.spannable.length, DiscourseTagChipSpan::class.java).single()
        assertThat(built.spannable.getSpans(0, built.spannable.length, ChipBackgroundSpan::class.java)).isEmpty()
        assertThat(built.spannable.toString()).contains("快问快答")
        assertThat(built.spannable.getSpanStart(tagChipSpan)).isEqualTo(built.spannable.toString().indexOf("快问快答"))
        assertThat(built.links.map { it.url }).containsExactly("https://linux.do/tag/1436-tag/1436")
        built.links.single().onClick()
        assertThat(openedLinks).containsExactly("https://linux.do/tag/1436-tag/1436")
        assertThat(built.spannable.toString()).doesNotContain("__nd_inline_icon_")
        assertThat(built.spannable.toString()).doesNotContain("__nd_hashtag_chip_")
    }

    private fun builder(
        onTopicClick: (Long) -> Unit = {},
        onUserClick: (String) -> Unit = {},
        onLinkClick: (String) -> Unit = {},
        svgSpriteMarkup: String = "",
    ): PostBodyTextBuilder {
        return PostBodyTextBuilder(
            PostBodyBuildContext(
                host = FakeView(),
                baseUrl = baseUrl,
                delegate = object : PostBodyDelegate {
                    override fun onLinkClick(url: String) = onLinkClick(url)
                    override fun onTopicClick(topicId: Long) = onTopicClick(topicId)
                    override fun onUserClick(username: String) = onUserClick(username)
                },
                textColor = 0xFF111111.toInt(),
                secondaryTextColor = 0xFF666666.toInt(),
                accentColor = 0xFF1677FF.toInt(),
                chipBackgroundColor = 0x221677FF,
                dividerColor = 0xFFE0E0E0.toInt(),
                svgSpriteMarkup = svgSpriteMarkup,
            ),
        )
    }

    private fun BuiltText.clickableSpans(): List<PostBodyClickableSpan> {
        return spannable.getSpans(0, spannable.length, PostBodyClickableSpan::class.java).toList()
    }

    private class FakeView : View(RuntimeEnvironment.getApplication())
}
