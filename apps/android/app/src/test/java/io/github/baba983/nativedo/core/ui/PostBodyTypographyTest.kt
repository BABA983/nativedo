package io.github.baba983.nativedo.core.ui

import androidx.compose.ui.text.TextStyle
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
        assertThat(textStyle.lineHeight).isEqualTo(20.sp)
    }
}
