package io.github.baba983.nativedo.debug

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SessionCookieImportActivityTest {
    @Test
    fun authenticatedCookieSegments_keepsOnlySessionAndCloudflareCookies() {
        val result = authenticatedCookieSegments(
            "_ga=analytics; _forum_session=forum%3D; cf_clearance=clearance; " +
                "_t=auth%2Btoken; __stripe_sid=stripe; linux_do_credit_session_id=credit",
        )

        assertThat(result).containsExactly(
            "_forum_session=forum%3D",
            "cf_clearance=clearance",
            "_t=auth%2Btoken",
        ).inOrder()
    }

    @Test
    fun authenticatedCookieSegments_dropsMalformedAndEmptyValues() {
        assertThat(
            authenticatedCookieSegments("_t=; _forum_session; cf_clearance=valid; =missing-name"),
        ).containsExactly("cf_clearance=valid")
    }
}
