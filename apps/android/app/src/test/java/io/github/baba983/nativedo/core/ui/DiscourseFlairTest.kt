package io.github.baba983.nativedo.core.ui

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DiscourseFlairTest {
    @Test
    fun looksLikeFlairImageUrl_distinguishesImageAndIconFlair() {
        assertThat(looksLikeFlairImageUrl("/images/flair/admins.png")).isTrue()
        assertThat(looksLikeFlairImageUrl("https://linux.do/flair/admins.svg")).isTrue()
        assertThat(looksLikeFlairImageUrl("battery-quarter")).isFalse()
    }

    @Test
    fun normalizeFlairIconName_stripsPrefixNoise() {
        assertThat(normalizeFlairIconName("#battery-quarter")).isEqualTo("battery-quarter")
        assertThat(normalizeFlairIconName("solid battery-quarter")).isEqualTo("battery-quarter")
        assertThat(normalizeFlairIconName("/svg-sprite/battery-quarter")).isEqualTo("battery-quarter")
        assertThat(normalizeFlairIconName("/svg-sprite#battery-quarter")).isEqualTo("battery-quarter")
        assertThat(normalizeFlairIconName("far-battery-quarter")).isEqualTo("battery-quarter")
    }

    @Test
    fun buildDiscourseFlairSvgDocument_wrapsSpriteDefinitionsForUseRendering() {
        val spriteMarkup = """
            <div id="svg-sprites">
              <svg xmlns="http://www.w3.org/2000/svg" style="display: none;">
                <symbol id="table-cells" viewBox="0 0 512 512">
                  <path d="M0 0H512V512H0z"></path>
                </symbol>
                <symbol id="table-cells-plus" viewBox="0 0 512 512">
                  <use href="#table-cells"></use>
                </symbol>
              </svg>
            </div>
        """.trimIndent()

        val svgDocument = buildDiscourseFlairSvgDocument(
            spriteMarkup = spriteMarkup,
            rawFlairUrl = "#table-cells-plus",
            rawTintColor = "#00AEFF",
        )

        assertThat(svgDocument).isNotNull()
        assertThat(svgDocument).contains("viewBox=\"0 0 512 512\"")
        assertThat(svgDocument).contains("width=\"512\"")
        assertThat(svgDocument).contains("height=\"512\"")
        assertThat(svgDocument).doesNotContain("<symbol")
        assertThat(svgDocument).doesNotContain("<use href=\"#table-cells-plus\"")
        assertThat(svgDocument).contains("<path d=\"M0 0H512V512H0z\"></path>")
        assertThat(svgDocument).contains("fill=\"#00AEFF\"")
    }

    @Test
    fun buildDiscourseFlairSvgDocument_returnsNullWhenTargetSymbolMissing() {
        val svgDocument = buildDiscourseFlairSvgDocument(
            spriteMarkup = """<svg><symbol id="known" viewBox="0 0 1 1"></symbol></svg>""",
            rawFlairUrl = "unknown",
            rawTintColor = "",
        )

        assertThat(svgDocument).isNull()
    }

    @Test
    fun buildDiscourseFlairSvgDocument_supportsFarPrefixedSymbolIds() {
        val svgDocument = buildDiscourseFlairSvgDocument(
            spriteMarkup = """
                <svg>
                  <symbol id="far-pen-to-square" viewBox="0 0 16 16">
                    <path d="M0 0H16V16H0z"></path>
                  </symbol>
                </svg>
            """.trimIndent(),
            rawFlairUrl = "far-pen-to-square",
            rawTintColor = "",
        )

        assertThat(svgDocument).contains("viewBox=\"0 0 16 16\"")
        assertThat(svgDocument).contains("<path d=\"M0 0H16V16H0z\"></path>")
    }

    @Test
    fun buildDiscourseFlairSvgDocument_replacesCssVariablesWithTintColor() {
        val spriteMarkup = """
            <svg>
              <symbol id="bookmark-delete" viewBox="0 0 16 20">
                <path fill="var(--secondary)" d="M0 0H10V10H0z"></path>
              </symbol>
            </svg>
        """.trimIndent()

        val svgDocument = buildDiscourseFlairSvgDocument(
            spriteMarkup = spriteMarkup,
            rawFlairUrl = "bookmark-delete",
            rawTintColor = "#123456",
        )

        assertThat(svgDocument).isNotNull()
        assertThat(svgDocument).doesNotContain("var(--secondary)")
        assertThat(svgDocument).contains("fill=\"#123456\"")
    }

    @Test
    fun buildDiscourseFlairSvgDocument_expandsThreeDigitTintHex() {
        val svgDocument = buildDiscourseFlairSvgDocument(
            spriteMarkup = """
                <svg>
                  <symbol id="battery-quarter" viewBox="0 0 576 512">
                    <path d="M0 0H10V10H0z"></path>
                  </symbol>
                </svg>
            """.trimIndent(),
            rawFlairUrl = "#battery-quarter",
            rawTintColor = "#fff",
        )

        assertThat(svgDocument).isNotNull()
        assertThat(svgDocument).contains("fill=\"#ffffff\"")
    }

    @Test
    fun discourseFlairColor_prefersParsedHexAndFallsBackWhenMissing() {
        val fallback = Color(0xCCFFFFFF)

        assertThat(discourseFlairColor(rawHex = "#00aeff", fallback = fallback))
            .isEqualTo(Color(0xFF00AEFF))
        assertThat(discourseFlairColor(rawHex = "#fff", fallback = fallback))
            .isEqualTo(Color(0xFFFFFFFF))
        assertThat(discourseFlairColor(rawHex = "#1234", fallback = fallback))
            .isEqualTo(Color(0x44112233))
        assertThat(discourseFlairColor(rawHex = "", fallback = fallback))
            .isEqualTo(fallback)
    }
}
