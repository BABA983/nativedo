package io.github.baba983.nativedo.core.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileLog {
    private const val LogDirectoryName: String = "rewrite/logs"
    private const val LogFileName: String = "filelog.txt"

    private val queue = DispatchQueue("nd-file-log")
    private val timestampFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var logFile: File? = null

    fun init(context: Context) {
        val targetFile = File(context.filesDir, "$LogDirectoryName/$LogFileName")
        if (logFile?.absolutePath == targetFile.absolutePath) {
            return
        }
        targetFile.parentFile?.mkdirs()
        logFile = targetFile
        write("I", "NativeDo.FileLog", "FileLog initialized")
    }

    fun d(tag: String, message: String) {
        write("D", tag, message)
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        write("W", tag, message, error)
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        write("E", tag, message, error)
    }

    private fun write(
        level: String,
        tag: String,
        message: String,
        error: Throwable? = null,
    ) {
        Log.println(
            when (level) {
                "D" -> Log.DEBUG
                "W" -> Log.WARN
                "E" -> Log.ERROR
                else -> Log.INFO
            },
            tag,
            message,
        )
        val targetFile = logFile ?: return
        queue.postRunnable {
            runCatching {
                val builder = StringBuilder()
                builder.append(timestampFormatter.format(Date()))
                builder.append(' ')
                builder.append(level)
                builder.append('/')
                builder.append(tag)
                builder.append(": ")
                builder.append(message)
                if (error != null) {
                    builder.append('\n')
                    builder.append(Log.getStackTraceString(error))
                }
                builder.append('\n')
                targetFile.appendText(builder.toString())
            }
        }
    }
}
