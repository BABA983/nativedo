package io.github.baba983.nativedo.ui.topic

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TopicCellTitleTest {
    @Test
    fun normalizeListTitle_collapsesNewlinesAndWhitespace() {
        assertThat(TopicCell.normalizeListTitle(null).toString()).isEmpty()
        assertThat(TopicCell.normalizeListTitle("").toString()).isEmpty()
        assertThat(TopicCell.normalizeListTitle("  hello\nworld\t!  ").toString())
            .isEqualTo("hello world !")
        assertThat(TopicCell.normalizeListTitle("a\r\nb  c").toString())
            .isEqualTo("a b c")
    }
}
