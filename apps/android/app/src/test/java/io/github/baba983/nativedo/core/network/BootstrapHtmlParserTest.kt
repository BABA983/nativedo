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

    @Test
    fun parser_extractsCurrentUserFromDataPreloadedAttribute() {
        val parsed = BootstrapHtmlParser.parse(
            """
                <html>
                  <head>
                    <meta name="csrf-token" content="csrf-token-demo" />
                  </head>
                  <body>
                    <div
                      id="data-preloaded"
                      data-preloaded='{"currentUser":"{\"username\":\"baba\"}","site":"{\"title\":\"linux.do\"}"}'
                    ></div>
                  </body>
                </html>
            """.trimIndent(),
        )

        assertThat(parsed.currentUsername).isEqualTo("baba")
        assertThat(parsed.csrfToken).isEqualTo("csrf-token-demo")
    }

    @Test
    fun parser_doesNotReadOtherPreloadedUsernamesWhenCurrentUserIsMissing() {
        val parsed = BootstrapHtmlParser.parse(
            """
                <html>
                  <head>
                    <meta name="csrf-token" content="csrf-token-demo" />
                  </head>
                  <body>
                    <div
                      id="data-preloaded"
                      data-preloaded='{"topicTrackingStates":"[{\"username\":\"someone-else\"}]","site":"{\"admins\":[{\"username\":\"staff-user\"}]}"}'
                    ></div>
                  </body>
                </html>
            """.trimIndent(),
        )

        assertThat(parsed.currentUsername).isNull()
        assertThat(parsed.csrfToken).isEqualTo("csrf-token-demo")
    }

    @Test
    fun parser_fallsBackToCurrentUsernameMetaTag() {
        val parsed = BootstrapHtmlParser.parse(
            """
                <html>
                  <head>
                    <meta name="csrf-token" content="csrf-token-demo" />
                    <meta name="current-username" content="baba" />
                  </head>
                  <body></body>
                </html>
            """.trimIndent(),
        )

        assertThat(parsed.currentUsername).isEqualTo("baba")
        assertThat(parsed.csrfToken).isEqualTo("csrf-token-demo")
    }
}
