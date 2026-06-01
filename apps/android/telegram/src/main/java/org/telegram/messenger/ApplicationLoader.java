/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.messenger;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/**
 * Minimal global-application-state holder matching the fields Telegram's PhotoViewer
 * and supporting UI code read from {@code ApplicationLoader}.
 *
 * <p>Telegram's {@code ApplicationLoader} extends {@code Application} and owns network
 * detection, push, location, maps, GCM, and the full service graph. NativeDo already has
 * its own {@code Application} subclass, so this class provides only the static surface
 * that ported code expects.
 */
public final class ApplicationLoader {

    @SuppressLint("StaticFieldLeak")
    public static volatile Context applicationContext;
    public static volatile Handler applicationHandler;
    public static volatile boolean mainInterfacePaused = true;
    public static volatile boolean mainInterfaceStopped = true;

    /** {@code System.currentTimeMillis()} when the process started. */
    public static long startTime;

    /** Stub of upstream {@code ApplicationLoader.isNetworkOnline()}. Always true on NativeDo for now. */
    public static boolean isNetworkOnline() {
        return true;
    }

    /** Stub of upstream {@code ApplicationLoader.isConnectionSlow()}. */
    public static boolean isConnectionSlow() {
        return false;
    }

    /** Stub of upstream {@code ApplicationLoader.getCurrentNetworkType()}. */
    public static int getCurrentNetworkType() {
        return 0;
    }

    /** Stub of upstream {@code ApplicationLoader.isRoaming()}. */
    public static boolean isRoaming() {
        return false;
    }

    /**
     * Host wiring for upstream {@code ApplicationLoader.isAndroidTestEnvironment()}.
     * Upstream delegates to the application loader instance to short-circuit
     * {@code SvgHelper.doPath()} under instrumentation; NativeDo always runs in a
     * real runtime, so this returns {@code false}.
     */
    public static boolean isAndroidTestEnvironment() {
        return false;
    }

    /** Stub of upstream {@code ApplicationLoader.getFilesDirFixed}. */
    public static java.io.File getFilesDirFixed() {
        if (applicationContext != null) {
            return applicationContext.getFilesDir();
        }
        return new java.io.File(System.getProperty("java.io.tmpdir", "/tmp"));
    }

    /** Stub of upstream {@code ApplicationLoader.getCacheDir()}. */
    public static java.io.File getCacheDir() {
        if (applicationContext != null) {
            return applicationContext.getCacheDir();
        }
        return new java.io.File(System.getProperty("java.io.tmpdir", "/tmp"));
    }

    private ApplicationLoader() {
    }

    /**
     * Must be called once from {@code NativeDoApp.onCreate()} before any ported code
     * reads {@link #applicationContext} or {@link #applicationHandler}.
     */
    public static void init(Context appContext) {
        applicationContext = appContext.getApplicationContext();
        applicationHandler = new Handler(Looper.getMainLooper());
        startTime = System.currentTimeMillis();
    }

    /**
     * Returns the application ID (package name) for use with {@code FileProvider}
     * authority strings, matching upstream {@code ApplicationLoader.getApplicationId()}.
     */
    public static String getApplicationId() {
        if (applicationContext != null) {
            return applicationContext.getPackageName();
        }
        return "io.github.baba983.nativedo";
    }
}
