package io.github.baba983.nativedo.core.ui

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PostBodyBlockSpacingTest {
    @Test
    fun postBodyBlockSpacingAfter_keepsStructuredBlocksTightInsideReaderShell() {
        assertThat(
            postBodyBlockSpacingAfter(
                current = PostBodyBlock.QuoteBlock(children = listOf(paragraph("quoted"))),
                next = paragraph("after"),
            ),
        ).isEqualTo(10.dp)

        assertThat(
            postBodyBlockSpacingAfter(
                current = paragraph("before"),
                next = PostBodyBlock.DetailsBlock(
                    summary = paragraph("summary"),
                    children = listOf(paragraph("child")),
                    expandedByDefault = false,
                ),
            ),
        ).isEqualTo(10.dp)
    }

    private fun paragraph(text: String) = PostBodyBlock.RichTextBlock(
        style = RichTextBlockStyle.Paragraph,
        inlines = listOf(PostInline.Text(text)),
    )
}
