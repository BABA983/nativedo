/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org.telegram.messenger.FileLog.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/messenger/FileLog.java
 *
 * Copyright (C) Telegram FZ-LLC
 * Copyright (C) 2026 NativeDo contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under terms of GNU General Public License version 2 as published by
 * Free Software Foundation.
 *
 * SPDX-License-Identifier: GPL-2.0-only
 */
package io.github.baba983.nativedo.core.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileLog {
    private const val LogDirectoryName: String = "rewrite/logs"
    private const val LogFileName: String = "tmessages.txt"
    private const val LogTag: String = "tmessages"

    private val queue = DispatchQueue("nd-file-log")
    private val timestampFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileTimestampFormatter = SimpleDateFormat("dd_MM_yyyy_HH_mm_ss", Locale.US)
    private val formatterLock = Any()

    @Volatile
    private var logFile: File? = null

    @Volatile
    private var initialized: Boolean = false

    fun init(context: Context) {
        val targetFile = File(context.filesDir, "$LogDirectoryName/$LogFileName")
        if (initialized && logFile?.absolutePath == targetFile.absolutePath) {
            return
        }
        targetFile.parentFile?.mkdirs()
        if (!targetFile.exists()) {
            targetFile.createNewFile()
        }
        logFile = targetFile
        initialized = true
        appendRawLine("-----start log ${formatFileTimestamp(Date())}-----")
    }

    fun d(message: String) {
        logToConsole(Log.DEBUG, message)
        write("D", message)
    }

    fun w(message: String) {
        logToConsole(Log.WARN, message)
        write("W", message)
    }

    fun e(message: String) {
        logToConsole(Log.ERROR, message)
        write("E", message)
    }

    fun e(
        message: String,
        error: Throwable,
    ) {
        logToConsole(Log.ERROR, message, error)
        write("E", message, error)
    }

    fun e(error: Throwable) {
        val message = error.toString()
        logToConsole(Log.ERROR, message, error)
        write("E", message, error)
    }

    private fun write(
        level: String,
        message: String,
        error: Throwable? = null,
    ) {
        val targetFile = logFile ?: return
        queue.postRunnable {
            runCatching {
                val builder = StringBuilder()
                builder.append(formatTimestamp(Date()))
                builder.append(' ')
                builder.append(level)
                builder.append('/')
                builder.append(LogTag)
                builder.append(": ")
                builder.append(message)
                if (error != null) {
                    builder.append('\n')
                    builder.append(Log.getStackTraceString(error).trimEnd())
                }
                builder.append('\n')
                targetFile.appendText(builder.toString())
            }
        }
    }

    private fun appendRawLine(line: String) {
        val targetFile = logFile ?: return
        queue.postRunnable {
            runCatching {
                targetFile.appendText("$line\n")
            }
        }
    }

    private fun logToConsole(
        priority: Int,
        message: String,
        error: Throwable? = null,
    ) {
        when (priority) {
            Log.DEBUG -> Log.d(LogTag, message)
            Log.WARN -> Log.w(LogTag, message)
            Log.ERROR -> {
                if (error == null) {
                    Log.e(LogTag, message)
                } else {
                    Log.e(LogTag, message, error)
                }
            }
            else -> Log.println(priority, LogTag, message)
        }
    }

    private fun formatTimestamp(date: Date): String = synchronized(formatterLock) {
        timestampFormatter.format(date)
    }

    private fun formatFileTimestamp(date: Date): String = synchronized(formatterLock) {
        fileTimestampFormatter.format(date)
    }
}
