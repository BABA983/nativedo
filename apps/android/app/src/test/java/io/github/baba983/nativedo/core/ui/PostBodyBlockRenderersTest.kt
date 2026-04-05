package io.github.baba983.nativedo.core.ui

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PostBodyBlockRenderersTest {
    @Test
    fun postBodyBlockSpacingAfter_keepsHeadingCloserToFollowingParagraph() {
        val spacing = postBodyBlockSpacingAfter(
            current = PostBodyBlock.HeadingBlock(
                level = 2,
                content = TextDocument(
                    text = "标题",
                    entities = emptyList(),
                ),
            ),
            next = PostBodyBlock.ParagraphBlock(
                content = TextDocument(
                    text = "正文",
                    entities = emptyList(),
                ),
            ),
        )

        assertThat(spacing == 8.dp).isTrue()
    }

    @Test
    fun postBodyBlockSpacingAfter_keepsImagesAndParagraphsCompactInsidePostCard() {
        val paragraph = PostBodyBlock.ParagraphBlock(
            content = TextDocument(
                text = "正文",
                entities = emptyList(),
            ),
        )
        val image = PostBodyBlock.ImageBlock(
            image = PostBodyImage(rawUrl = "/uploads/default/original/1X/demo.png"),
        )

        assertThat(postBodyBlockSpacingAfter(paragraph, image) == 8.dp).isTrue()
        assertThat(postBodyBlockSpacingAfter(image, paragraph) == 8.dp).isTrue()
    }
}
