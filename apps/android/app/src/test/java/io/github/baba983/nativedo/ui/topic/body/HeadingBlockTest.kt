package io.github.baba983.nativedo.ui.topic.body

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HeadingBlockTest {
    @Test
    fun headingTextSizeDp_matchesArticleViewerHeadingPaints() {
        assertThat(HeadingBlock.headingTextSizeDp(1)).isEqualTo(23f)
        assertThat(HeadingBlock.headingTextSizeDp(2)).isEqualTo(20f)
        assertThat(HeadingBlock.headingTextSizeDp(3)).isEqualTo(17f)
        assertThat(HeadingBlock.headingTextSizeDp(6)).isEqualTo(17f)
    }

    @Test
    fun headingTypefacePath_matchesArticleViewerHeadingTypeface() {
        assertThat(HeadingBlock.headingTypefacePath()).isEqualTo("fonts/mw_bold.ttf")
    }
}
