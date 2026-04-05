package io.github.baba983.nativedo.core.ui

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    @Test
    fun collectPostBodyCoverage_reportsFixtureContentInsteadOfDroppingIt() {
        val blocks = loadFixtureCookedPosts("discourse/topic-detail.json")
            .plus(loadFixtureCookedPosts("discourse/topic-posts.json"))
            .flatMap(::parsePostBodyBlocks)

        val report = collectPostBodyCoverage(blocks)

        assertThat(
            report.nativeBlockCount + report.htmlFallbackCount + report.unknownTagCounts.values.sum(),
        ).isGreaterThan(0)
        assertThat(report.nativeBlockCount).isGreaterThan(0)
    }

    private fun loadFixtureCookedPosts(resourcePath: String): List<String> {
        val payload = checkNotNull(
            javaClass.classLoader?.getResource(resourcePath),
        ).readText()

        return Json.parseToJsonElement(payload)
            .jsonObject["post_stream"]
            ?.jsonObject
            ?.get("posts")
            ?.jsonArray
            ?.mapNotNull { post ->
                post.jsonObject["cooked"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.takeIf(String::isNotBlank)
            }
            .orEmpty()
    }
}
