package io.github.baba983.nativedo.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RequestErrorPresentationTest {
    @Test
    fun failedToConnect_returnsConnectivityCopy() {
        assertThat(
            requestErrorPresentation(
                rawMessage = "failed to connect to linux.do/1.1.1.1 (port 443) from /127.0.0.1",
                genericTitle = "Couldn't load topics",
                genericDescription = "Pull to refresh or try again.",
            ),
        ).isEqualTo(
            RequestErrorPresentation(
                kind = RequestErrorKind.Connectivity,
                title = "Can't reach Linux.do",
                description = "The connection to Linux.do failed before a response was received.",
                recovery = "Check your network and try again.",
                rawMessage = "failed to connect to linux.do/1.1.1.1 (port 443) from /127.0.0.1",
            ),
        )
    }

    @Test
    fun blankMessage_returnsGenericCopy() {
        assertThat(
            requestErrorPresentation(
                rawMessage = null,
                genericTitle = "Couldn't load profile",
                genericDescription = "Please try again in a moment.",
            ),
        ).isEqualTo(
            RequestErrorPresentation(
                kind = RequestErrorKind.Generic,
                title = "Couldn't load profile",
                description = "Please try again in a moment.",
                recovery = null,
                rawMessage = null,
            ),
        )
    }

    @Test
    fun serverError_usesProvidedGenericCopy() {
        assertThat(
            requestErrorPresentation(
                rawMessage = "HTTP 500 Internal Server Error",
                genericTitle = "Couldn't load topic",
                genericDescription = "Please try again in a moment.",
            ),
        ).isEqualTo(
            RequestErrorPresentation(
                kind = RequestErrorKind.Generic,
                title = "Couldn't load topic",
                description = "Please try again in a moment.",
                recovery = null,
                rawMessage = "HTTP 500 Internal Server Error",
            ),
        )
    }

    @Test
    fun timeoutReason_usesSpecificConnectivityDescription() {
        assertThat(
            requestErrorPresentation(
                rawMessage = "timeout",
                genericTitle = "Couldn't load topics",
                genericDescription = "Pull to refresh or try again.",
            ),
        ).isEqualTo(
            RequestErrorPresentation(
                kind = RequestErrorKind.Connectivity,
                title = "Can't reach Linux.do",
                description = "The request timed out before Linux.do responded.",
                recovery = "Check your network and try again.",
                rawMessage = "timeout",
            ),
        )
    }

    @Test
    fun rawMessage_isTrimmedBeforeExposingIt() {
        assertThat(
            requestErrorPresentation(
                rawMessage = "  failed to connect  ",
                genericTitle = "Couldn't load topics",
                genericDescription = "Pull to refresh or try again.",
            ).rawMessage,
        ).isEqualTo("failed to connect")
    }
}
