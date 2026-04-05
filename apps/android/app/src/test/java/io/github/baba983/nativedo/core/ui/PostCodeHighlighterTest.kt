package io.github.baba983.nativedo.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PostCodeHighlighterTest {
    @Test
    fun highlightCode_mapsKotlinKeywordsAndStrings() {
        val segments = highlightCode(
            code = """fun greet() = "hi"""",
            language = "kotlin",
        )

        assertThat(segments.map { it.kind }).containsExactly(
            PostCodeTokenKind.Keyword,
            PostCodeTokenKind.Plain,
            PostCodeTokenKind.String,
        ).inOrder()
    }
}
