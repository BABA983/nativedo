package io.github.baba983.nativedo.core.network

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BootstrapHtmlParserTest {
    @Test
    fun parser_extractsCurrentUserAndCsrfToken() {
        val html = checkNotNull(
            javaClass.classLoader?.getResource("discourse/home-bootstrap.html"),
        ).readText()
        val parsed = BootstrapHtmlParser.parse(html)

        assertThat(parsed.csrfToken).isNotEmpty()
        assertThat(parsed.currentUsername).isNotEmpty()
    }
}
