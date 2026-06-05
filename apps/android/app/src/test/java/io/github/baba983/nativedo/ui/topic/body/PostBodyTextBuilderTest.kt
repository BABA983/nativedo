package io.github.baba983.nativedo.ui.topic.body

import android.view.View
import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.ui.PostBodyClickableSpan
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PostBodyTextBuilderTest {
    private val baseUrl = "https://linux.do/"

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
    }

    private fun builder(
        onTopicClick: (Long) -> Unit = {},
        onUserClick: (String) -> Unit = {},
        onLinkClick: (String) -> Unit = {},
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
            ),
        )
    }

    private fun BuiltText.clickableSpans(): List<PostBodyClickableSpan> {
        return spannable.getSpans(0, spannable.length, PostBodyClickableSpan::class.java).toList()
    }

    private class FakeView : View(RuntimeEnvironment.getApplication())
}
