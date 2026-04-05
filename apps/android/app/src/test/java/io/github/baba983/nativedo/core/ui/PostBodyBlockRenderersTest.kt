package io.github.baba983.nativedo.core.ui

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PostBodyBlockRenderersTest {
    @Test
    fun postBodyBlockSpacingAfter_keepsHeadingCloserToFollowingParagraph() {
        val spacing = postBodyBlockSpacingAfter(
            current = PostBodyBlock.RichTextBlock(
                style = RichTextBlockStyle.Heading2,
                inlines = listOf(PostInline.Text("标题")),
            ),
            next = PostBodyBlock.RichTextBlock(
                style = RichTextBlockStyle.Paragraph,
                inlines = listOf(PostInline.Text("正文")),
            ),
        )

        assertThat(spacing == 8.dp).isTrue()
    }

    @Test
    fun postBodyBlockSpacingAfter_keepsImagesAndParagraphsCompactInsidePostCard() {
        val paragraph = PostBodyBlock.RichTextBlock(
            style = RichTextBlockStyle.Paragraph,
            inlines = listOf(PostInline.Text("正文")),
        )
        val image = PostBodyBlock.ImageBlock(
            image = PostBodyImage(rawUrl = "/uploads/default/original/1X/demo.png"),
        )

        assertThat(postBodyBlockSpacingAfter(paragraph, image) == 8.dp).isTrue()
        assertThat(postBodyBlockSpacingAfter(image, paragraph) == 8.dp).isTrue()
    }
}
