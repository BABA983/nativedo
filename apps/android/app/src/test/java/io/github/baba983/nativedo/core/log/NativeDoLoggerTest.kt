package io.github.baba983.nativedo.core.log

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NativeDoLoggerTest {
    @Test
    fun debug_withLoggingEnabled_usesPrefixedTag() {
        val sink = FakeNativeDoLogSink()
        val logger = NativeDoLogger(
            enableDebugLogging = true,
            sink = sink,
        )

        logger.debug(NativeDoLogTag.Session, "restoreSession start")

        assertThat(sink.entries).containsExactly(
            FakeNativeDoLogSink.Entry(
                level = NativeDoLogLevel.Debug,
                tag = "NativeDo.Session",
                message = "restoreSession start",
                error = null,
            ),
        )
    }

    @Test
    fun debug_withLoggingDisabled_isIgnored() {
        val sink = FakeNativeDoLogSink()
        val logger = NativeDoLogger(
            enableDebugLogging = false,
            sink = sink,
        )

        logger.debug(NativeDoLogTag.Http, "request started")

        assertThat(sink.entries).isEmpty()
    }

    @Test
    fun warning_logsEvenWhenDebugLoggingDisabled() {
        val sink = FakeNativeDoLogSink()
        val logger = NativeDoLogger(
            enableDebugLogging = false,
            sink = sink,
        )

        logger.warning(NativeDoLogTag.Auth, "login expired")

        assertThat(sink.entries).containsExactly(
            FakeNativeDoLogSink.Entry(
                level = NativeDoLogLevel.Warning,
                tag = "NativeDo.Auth",
                message = "login expired",
                error = null,
            ),
        )
    }
}

private class FakeNativeDoLogSink : NativeDoLogSink {
    val entries = mutableListOf<Entry>()

    override fun log(
        level: NativeDoLogLevel,
        tag: String,
        message: String,
        error: Throwable?,
    ) {
        entries += Entry(
            level = level,
            tag = tag,
            message = message,
            error = error,
        )
    }

    data class Entry(
        val level: NativeDoLogLevel,
        val tag: String,
        val message: String,
        val error: Throwable?,
    )
}
