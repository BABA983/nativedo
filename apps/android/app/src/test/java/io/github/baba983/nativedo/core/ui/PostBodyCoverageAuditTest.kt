package io.github.baba983.nativedo.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PostBodyCoverageAuditTest {
    @Test
    fun parsePostBodyBlocks_wrapsUnsupportedElementInUnknownBlock() {
        val blocks = parsePostBodyBlocks(
            """
                <section class="callout">
                  <p>keep me visible</p>
                </section>
            """.trimIndent(),
        )

        val unknown = blocks.single() as PostBodyBlock.UnknownBlock
        assertThat(unknown.tagName).isEqualTo("section")
        assertThat(unknown.classes).containsExactly("callout")
        assertThat(unknown.rawHtml).contains("<p>keep me visible</p>")
    }

    @Test
    fun collectPostBodyCoverage_countsUnknownAndNativeBlocks() {
        val blocks = listOf(
            PostBodyBlock.CodeBlock(
                code = "println(42)",
                languageHint = "kotlin",
            ),
            PostBodyBlock.UnknownBlock(
                rawHtml = """<table class="wide"></table>""",
                tagName = "table",
                classes = setOf("wide"),
            ),
        )

        val report = collectPostBodyCoverage(blocks)

        assertThat(report.nativeBlockCount).isEqualTo(1)
        assertThat(report.unknownTagCounts).containsExactly("table", 1)
        assertThat(report.unknownClassCounts).containsExactly("wide", 1)
    }
}
