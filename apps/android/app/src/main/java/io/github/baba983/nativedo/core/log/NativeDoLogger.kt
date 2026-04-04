package io.github.baba983.nativedo.core.log

import android.util.Log

internal class NativeDoLogger(
    private val enableDebugLogging: Boolean,
    private val sink: NativeDoLogSink = AndroidNativeDoLogSink,
) {
    fun debug(
        tag: NativeDoLogTag,
        message: String,
    ) {
        if (!enableDebugLogging) {
            return
        }
        sink.log(
            level = NativeDoLogLevel.Debug,
            tag = tag.value,
            message = message,
            error = null,
        )
    }

    fun info(
        tag: NativeDoLogTag,
        message: String,
    ) {
        if (!enableDebugLogging) {
            return
        }
        sink.log(
            level = NativeDoLogLevel.Info,
            tag = tag.value,
            message = message,
            error = null,
        )
    }

    fun warning(
        tag: NativeDoLogTag,
        message: String,
        error: Throwable? = null,
    ) {
        sink.log(
            level = NativeDoLogLevel.Warning,
            tag = tag.value,
            message = message,
            error = error,
        )
    }

    fun error(
        tag: NativeDoLogTag,
        message: String,
        error: Throwable? = null,
    ) {
        sink.log(
            level = NativeDoLogLevel.Error,
            tag = tag.value,
            message = message,
            error = error,
        )
    }
}

internal interface NativeDoLogSink {
    fun log(
        level: NativeDoLogLevel,
        tag: String,
        message: String,
        error: Throwable? = null,
    )
}

internal enum class NativeDoLogLevel {
    Debug,
    Info,
    Warning,
    Error,
}

@JvmInline
internal value class NativeDoLogTag private constructor(val value: String) {
    companion object {
        val App: NativeDoLogTag = of("App")
        val Auth: NativeDoLogTag = of("Auth")
        val Http: NativeDoLogTag = of("Http")
        val Session: NativeDoLogTag = of("Session")
        val Ui: NativeDoLogTag = of("Ui")

        fun of(suffix: String): NativeDoLogTag = NativeDoLogTag("NativeDo.$suffix")
    }
}

private object NativeDoLoggers {
    val shared: NativeDoLogger = NativeDoLogger(enableDebugLogging = true)
}

private object AndroidNativeDoLogSink : NativeDoLogSink {
    override fun log(
        level: NativeDoLogLevel,
        tag: String,
        message: String,
        error: Throwable?,
    ) {
        runCatching {
            when (level) {
                NativeDoLogLevel.Debug -> Log.d(tag, message)
                NativeDoLogLevel.Info -> Log.i(tag, message)
                NativeDoLogLevel.Warning -> {
                    if (error == null) {
                        Log.w(tag, message)
                    } else {
                        Log.w(tag, message, error)
                    }
                }

                NativeDoLogLevel.Error -> {
                    if (error == null) {
                        Log.e(tag, message)
                    } else {
                        Log.e(tag, message, error)
                    }
                }
            }
        }.getOrElse {
            println("${level.asPriorityPrefix()}/$tag: $message")
            error?.printStackTrace()
        }
    }

    private fun NativeDoLogLevel.asPriorityPrefix(): String {
        return when (this) {
            NativeDoLogLevel.Debug -> "D"
            NativeDoLogLevel.Info -> "I"
            NativeDoLogLevel.Warning -> "W"
            NativeDoLogLevel.Error -> "E"
        }
    }
}

internal fun debugLog(
    tag: NativeDoLogTag,
    message: String,
) {
    NativeDoLoggers.shared.debug(tag, message)
}

internal fun infoLog(
    tag: NativeDoLogTag,
    message: String,
) {
    NativeDoLoggers.shared.info(tag, message)
}

internal fun warnLog(
    tag: NativeDoLogTag,
    message: String,
    error: Throwable? = null,
) {
    NativeDoLoggers.shared.warning(tag, message, error)
}

internal fun errorLog(
    tag: NativeDoLogTag,
    message: String,
    error: Throwable,
) {
    NativeDoLoggers.shared.error(tag, message, error)
}
