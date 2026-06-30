package io.github.baba983.nativedo.ui.topic

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.model.Post
import org.junit.Test

class TopicDetailQuoteTest {
    @Test
    fun discourseQuoteMarkup_includesPostAndTopicMetadataWhenSourcePostIsKnown() {
        val post = post(postNumber = 7, username = "alice")

        val markup = discourseQuoteMarkup("selected\ntext", post, topicId = 42L)

        assertThat(markup).isEqualTo(
            "[quote=\"alice, post:7, topic:42\"]\nselected\ntext\n[/quote]",
        )
    }

    @Test
    fun discourseQuoteMarkup_fallsBackToMarkdownQuoteWhenSourcePostIsUnknown() {
        val markup = discourseQuoteMarkup("selected\ntext", sourcePost = null, topicId = 42L)

        assertThat(markup).isEqualTo("> selected\n> text")
    }

    private fun post(postNumber: Int?, username: String): Post {
        return Post(
            id = 1001L,
            postNumber = postNumber,
            authorUsername = username,
            cookedHtml = "<p>Body</p>",
        )
    }
}
