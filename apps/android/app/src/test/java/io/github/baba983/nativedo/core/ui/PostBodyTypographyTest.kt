package io.github.baba983.nativedo.core.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PostBodyTypographyTest {
    @Test
    fun postBodyParagraphTextStyle_usesTighterReaderScale() {
        val textStyle = postBodyParagraphTextStyle(
            baseStyle = TextStyle(
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        )

        assertThat(textStyle.fontSize).isEqualTo(15.sp)
        assertThat(textStyle.lineHeight).isEqualTo(19.sp)
    }

    @Test
    fun postBodyBlockSpacingAfter_givesParagraphsAndHeadingsMoreBreathingRoom() {
        val paragraph = PostBodyBlock.ParagraphBlock(
            content = TextDocument(text = "正文", entities = emptyList()),
        )
        val heading = PostBodyBlock.HeadingBlock(
            level = 2,
            content = TextDocument(text = "标题", entities = emptyList()),
        )

        assertThat(postBodyBlockSpacingAfter(paragraph, paragraph)).isEqualTo(12.dp)
        assertThat(postBodyBlockSpacingAfter(heading, paragraph)).isEqualTo(12.dp)
        assertThat(postBodyBlockSpacingAfter(paragraph, heading)).isEqualTo(14.dp)
    }

    @Test
    fun listMarkerWidth_expandsForDoubleDigitOrderedLists() {
        assertThat(listMarkerWidth(ordered = false, itemCount = 10)).isEqualTo(18.dp)
        assertThat(listMarkerWidth(ordered = true, itemCount = 9)).isEqualTo(18.dp)
        assertThat(listMarkerWidth(ordered = true, itemCount = 10)).isEqualTo(26.dp)
    }
}
