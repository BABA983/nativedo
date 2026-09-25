package io.github.baba983.nativedo.ui.topic.body

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader

@RunWith(RobolectricTestRunner::class)
class OneboxBlockTest {
    @Before
    fun setUp() {
        ApplicationLoader.init(RuntimeEnvironment.getApplication())
        AndroidUtilities.displaySize.set(AndroidUtilities.dp(393f), AndroidUtilities.dp(852f))
    }

    @Test
    fun previewHtmlWithoutLinks_unwrapsOneboxAnchorsButKeepsInlineMarkup() {
        val html = """
            <a href="https://example.dev"><strong>Example</strong></a>
            <span class="spoiler">hidden</span>
        """.trimIndent()

        val sanitized = OneboxBlock.previewHtmlWithoutLinks(html)

        assertThat(sanitized).doesNotContain("<a ")
        assertThat(sanitized).contains("<strong>Example</strong>")
        assertThat(sanitized).contains("class=\"spoiler\"")
    }
}
