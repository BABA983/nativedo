// PORT-MODE: Exact Component Port (public API surface)
// UPSTREAM-SCOPE: org/telegram/messenger/FileLog.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// HOST-WIRING:
//   - All logging delegates to android.util.Log with the "NativeDo" tag.
//   - No file-backed pipeline, log rotation, or network logging.
// OMITTED:
//   - Disk log files, rotation, Gson-serialised structured logging, TL request logging,
//     DispatchQueue-backed async writes, and debug toggles.
/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/messenger/FileLog.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/messenger/FileLog.java
 *
 * Copyright (C) Telegram FZ-LLC
 * Copyright (C) 2026 NativeDo contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under terms of GNU General Public License version 2 or later as published by
 * the Free Software Foundation.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.messenger;

import android.util.Log;

/**
 * Telegram-shaped logging singleton. The public API mirrors
 * {@code org/telegram/messenger/FileLog} so ported code compiles unchanged;
 * all calls delegate to {@link android.util.Log} with the {@code "NativeDo"} tag.
 */
public final class FileLog {

    private static final String TAG = "NativeDo";

    private static volatile FileLog Instance;

    private FileLog() {
    }

    /** Singleton accessor — matches upstream {@code FileLog.getInstance()}. */
    public static FileLog getInstance() {
        FileLog localInstance = Instance;
        if (localInstance == null) {
            synchronized (FileLog.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new FileLog();
                }
            }
        }
        return localInstance;
    }

    // ── Public API matching upstream shape ─────────────────────────────

    /** Upstream: {@code FileLog.e(String message)} */
    @SuppressWarnings("unused")
    public static void e(final String message) {
        Log.e(TAG, message);
    }

    /** Upstream: {@code FileLog.e(Throwable e)} */
    public static void e(final Throwable e) {
        Log.e(TAG, "Telegram-derived failure", e);
    }

    /** Upstream: {@code FileLog.e(Throwable e, boolean reportToLogcat)} */
    @SuppressWarnings("unused")
    public static void e(final Throwable e, final boolean reportToLogcat) {
        Log.e(TAG, "Telegram-derived failure", e);
    }

    /** Upstream: {@code FileLog.e(String message, Throwable e)} */
    @SuppressWarnings("unused")
    public static void e(final String message, final Throwable e) {
        Log.e(TAG, message, e);
    }

    /** Upstream: {@code FileLog.d(String message)} */
    @SuppressWarnings("unused")
    public static void d(final String message) {
        Log.d(TAG, message);
    }

    /** Upstream: {@code FileLog.w(String message)} */
    @SuppressWarnings("unused")
    public static void w(final String message) {
        Log.w(TAG, message);
    }

    /** Upstream: {@code FileLog.fatal(Throwable e, boolean keepStacktraceInFile)} */
    @SuppressWarnings("unused")
    public static void fatal(final Throwable e, final boolean keepStacktraceInFile) {
        Log.wtf(TAG, "Fatal", e);
    }
}
