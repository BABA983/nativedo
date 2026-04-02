package io.github.baba983.nativedo.ui

import com.google.common.truth.Truth.assertThat
import io.github.baba983.nativedo.core.session.AppSessionState
import org.junit.Test

class NativeDoChromeStateTest {

    @Test
    fun anonymousSession_usesDefaultTitle() {
        assertThat(NativeDoChromeState.from(AppSessionState.Anonymous))
            .isEqualTo(NativeDoChromeState())
    }

    @Test
    fun authenticatedSession_showsUsername() {
        assertThat(
            NativeDoChromeState.from(
                AppSessionState.Authenticated(
                    username = "baba",
                    csrfToken = "csrf-token",
                ),
            ),
        )
            .isEqualTo(NativeDoChromeState(title = "baba"))
    }
}
