package io.github.baba983.nativedo.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NativeDoChromeStateTest {

    @Test
    fun anonymousSession_usesDefaultTitle() {
        assertThat(NativeDoChromeState.from(ShellSessionState.Anonymous))
            .isEqualTo(NativeDoChromeState())
    }

    @Test
    fun authenticatedSession_showsUsername() {
        assertThat(NativeDoChromeState.from(ShellSessionState.Authenticated(username = "baba")))
            .isEqualTo(NativeDoChromeState(title = "baba"))
    }
}
