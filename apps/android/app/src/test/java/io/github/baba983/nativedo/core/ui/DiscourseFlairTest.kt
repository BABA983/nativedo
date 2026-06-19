package io.github.baba983.nativedo.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DiscourseFlairTest {
    @Test
    fun buildDiscourseSvgDrawable_parsesRealFontAwesomeSymbol() {
        val svgDocument = buildDiscourseFlairSvgDocument(
            spriteMarkup = """
                <div id="svg-sprites"><svg xmlns="http://www.w3.org/2000/svg">
                  <symbol id="circle-question" viewBox="0 0 512 512">
                    <path d="M256 512A256 256 0 1 0 256 0a256 256 0 1 0 0 512zM169.8 165.3c7.9-22.3 29.1-37.3 52.8-37.3l58.3 0c34.9 0 63.1 28.3 63.1 63.1c0 22.6-12.1 43.5-31.7 54.8L280 264.4c-.2 13-10.9 23.6-24 23.6c-13.3 0-24-10.7-24-24l0-13.5c0-8.6 4.6-16.5 12.1-20.8l44.3-25.4c4.7-2.7 7.6-7.7 7.6-13.1c0-8.4-6.8-15.1-15.1-15.1l-58.3 0c-3.4 0-6.4 2.1-7.5 5.3l-.4 1.2c-4.4 12.5-18.2 19-30.6 14.6s-19-18.2-14.6-30.6l.4-1.2zM224 352a32 32 0 1 1 64 0 32 32 0 1 1 -64 0z"></path>
                  </symbol>
                </svg></div>
            """.trimIndent(),
            rawFlairUrl = "circle-question",
            rawTintColor = "#669d34",
        )

        assertThat(svgDocument).isNotNull()
        assertThat(svgDocument).contains("""fill="#669d34"""")
        val drawable = buildDiscourseSvgDrawable(svgDocument!!)
        assertThat(drawable).isNotNull()
        assertThat(drawable!!.intrinsicWidth).isEqualTo(512)
        assertThat(drawable.intrinsicHeight).isEqualTo(512)
    }

    @Test
    fun buildDiscourseSvgDrawable_parsesRealCalloutIconSymbol() {
        val svgDocument = buildDiscourseFlairSvgDocument(
            spriteMarkup = """
                <svg xmlns="http://www.w3.org/2000/svg">
                  <symbol id="far-pen-to-square" viewBox="0 0 512 512">
                    <path d="M441 58.9L453.1 71c9.4 9.4 9.4 24.6 0 33.9L424 134.1 377.9 88 407 58.9c9.4-9.4 24.6-9.4 33.9 0zM209.8 256.2L344 121.9 390.1 168 255.8 302.2c-2.9 2.9-6.5 5-10.4 6.1l-58.5 16.7 16.7-58.5c1.1-3.9 3.2-7.5 6.1-10.4zM373.1 25L175.8 222.2c-8.7 8.7-15 19.4-18.3 31.1l-28.6 100c-2.4 8.4-.1 17.4 6.1 23.6s15.2 8.5 23.6 6.1l100-28.6c11.8-3.4 22.5-9.7 31.1-18.3L487 138.9c28.1-28.1 28.1-73.7 0-101.8L474.9 25C446.8-3.1 401.2-3.1 373.1 25zM88 64C39.4 64 0 103.4 0 152L0 424c0 48.6 39.4 88 88 88l272 0c48.6 0 88-39.4 88-88l0-112c0-13.3-10.7-24-24-24s-24 10.7-24 24l0 112c0 22.1-17.9 40-40 40L88 464c-22.1 0-40-17.9-40-40l0-272c0-22.1 17.9-40 40-40l112 0c13.3 0 24-10.7 24-24s-10.7-24-24-24L88 64z"></path>
                  </symbol>
                </svg>
            """.trimIndent(),
            rawFlairUrl = "far-pen-to-square",
            rawTintColor = "#086ddd",
        )

        assertThat(svgDocument).isNotNull()
        assertThat(svgDocument).contains("""fill="#086ddd"""")
        val drawable = buildDiscourseSvgDrawable(svgDocument!!)
        assertThat(drawable).isNotNull()
        assertThat(drawable!!.intrinsicWidth).isEqualTo(512)
        assertThat(drawable.intrinsicHeight).isEqualTo(512)
    }

}
