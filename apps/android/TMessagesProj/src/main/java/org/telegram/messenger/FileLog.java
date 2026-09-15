/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.messenger;

import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Telegram-shaped logging singleton. The public API mirrors
 * {@code org/telegram/messenger/FileLog} so ported code compiles unchanged.
 * Writes to both logcat and a rotating log file on disk (matching upstream behavior).
 */
public final class FileLog {

    private static final String TAG = "nativedo";
    private static volatile FileLog Instance;
    private static final Object sync = new Object();

    private DispatchQueue logQueue;
    private OutputStreamWriter streamWriter;
    private File currentFile;
    private File networkFile = null;
    private File tonlibFile = null;
    private SimpleDateFormat dateFormat;
    private SimpleDateFormat fileDateFormat;
    private boolean initied;
    public static boolean databaseIsMalformed;

    private FileLog() {
    }

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

    public static void ensureInitied() {
        getInstance().init();
    }

    private void init() {
        if (initied) {
            return;
        }
        dateFormat = new SimpleDateFormat("dd_MM_yyyy_HH_mm_ss.SSS", Locale.US);
        fileDateFormat = new SimpleDateFormat("dd_MM_yyyy_HH_mm_ss", Locale.US);
        String date = fileDateFormat.format(System.currentTimeMillis());
        try {
            File dir = AndroidUtilities.getLogsDir();
            if (dir == null) {
                return;
            }
            currentFile = new File(dir, date + ".txt");
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            logQueue = new DispatchQueue("logQueue");
            currentFile.createNewFile();
            FileOutputStream stream = new FileOutputStream(currentFile);
            streamWriter = new OutputStreamWriter(stream);
            streamWriter.write("-----start log " + date + "-----\n");
            streamWriter.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
        initied = true;
    }

    // ── Public API matching upstream shape ─────────────────────────────

    public static void e(final String message, final Throwable exception) {
        if (!BuildVars.LOGS_ENABLED) {
            return;
        }
        ensureInitied();
        Log.e(TAG, message, exception);
        if (getInstance().streamWriter != null) {
            getInstance().logQueue.postRunnable(() -> {
                try {
                    getInstance().streamWriter.write(getInstance().dateFormat.format(System.currentTimeMillis()) + " E/nativedo: " + message + "\n");
                    if (exception != null) {
                        getInstance().streamWriter.write(exception + "\n");
                        StackTraceElement[] stack = exception.getStackTrace();
                        for (int a = 0; a < stack.length; a++) {
                            getInstance().streamWriter.write("  at " + stack[a] + "\n");
                        }
                    }
                    getInstance().streamWriter.flush();
                } catch (Exception ignore) {
                }
            });
        }
    }

    public static void e(final String message) {
        e(message, null);
    }

    public static void e(final Throwable e) {
        e(e, true);
    }

    public static void e(final Throwable e, boolean logToAppCenter) {
        if (!BuildVars.LOGS_ENABLED) {
            return;
        }
        ensureInitied();
        e.printStackTrace();
        Log.e(TAG, "", e);
        if (getInstance().streamWriter != null) {
            getInstance().logQueue.postRunnable(() -> {
                try {
                    getInstance().streamWriter.write(getInstance().dateFormat.format(System.currentTimeMillis()) + " E/nativedo: " + e + "\n");
                    StackTraceElement[] stack = e.getStackTrace();
                    for (int a = 0; a < stack.length; a++) {
                        getInstance().streamWriter.write("  at " + stack[a] + "\n");
                    }
                    Throwable cause = e.getCause();
                    if (cause != null) {
                        getInstance().streamWriter.write("Caused by: " + cause + "\n");
                        stack = cause.getStackTrace();
                        for (int a = 0; a < stack.length; a++) {
                            getInstance().streamWriter.write("  at " + stack[a] + "\n");
                        }
                    }
                    getInstance().streamWriter.flush();
                } catch (Exception ignore) {
                }
            });
        }
    }

    public static void fatal(final Throwable e) {
        fatal(e, true);
    }

    public static void fatal(final Throwable e, boolean logToAppCenter) {
        if (!BuildVars.LOGS_ENABLED) {
            return;
        }
        ensureInitied();
        Log.wtf(TAG, "Fatal", e);
        if (getInstance().streamWriter != null) {
            getInstance().logQueue.postRunnable(() -> {
                try {
                    getInstance().streamWriter.write(getInstance().dateFormat.format(System.currentTimeMillis()) + " FATAL/nativedo: " + e + "\n");
                    StackTraceElement[] stack = e.getStackTrace();
                    for (int a = 0; a < stack.length; a++) {
                        getInstance().streamWriter.write("  at " + stack[a] + "\n");
                    }
                    getInstance().streamWriter.flush();
                } catch (Exception ignore) {
                }
            });
        }
    }

    public static void d(final String message) {
        if (!BuildVars.LOGS_ENABLED) {
            return;
        }
        ensureInitied();
        Log.d(TAG, message);
        if (getInstance().streamWriter != null) {
            getInstance().logQueue.postRunnable(() -> {
                try {
                    getInstance().streamWriter.write(getInstance().dateFormat.format(System.currentTimeMillis()) + " D/nativedo: " + message + "\n");
                    getInstance().streamWriter.flush();
                } catch (Exception ignore) {
                }
            });
        }
    }

    public static void w(final String message) {
        if (!BuildVars.LOGS_ENABLED) {
            return;
        }
        ensureInitied();
        Log.w(TAG, message);
        if (getInstance().streamWriter != null) {
            getInstance().logQueue.postRunnable(() -> {
                try {
                    getInstance().streamWriter.write(getInstance().dateFormat.format(System.currentTimeMillis()) + " W/nativedo: " + message + "\n");
                    getInstance().streamWriter.flush();
                } catch (Exception ignore) {
                }
            });
        }
    }

    public static void cleanupLogs() {
        ensureInitied();
        File dir = AndroidUtilities.getLogsDir();
        if (dir == null) {
            return;
        }
        File[] files = dir.listFiles();
        if (files != null) {
            for (int a = 0; a < files.length; a++) {
                File file = files[a];
                if (getInstance().currentFile != null && file.getAbsolutePath().equals(getInstance().currentFile.getAbsolutePath())) {
                    continue;
                }
                if (getInstance().networkFile != null && file.getAbsolutePath().equals(getInstance().networkFile.getAbsolutePath())) {
                    continue;
                }
                if (getInstance().tonlibFile != null && file.getAbsolutePath().equals(getInstance().tonlibFile.getAbsolutePath())) {
                    continue;
                }
                file.delete();
            }
        }
    }
}
