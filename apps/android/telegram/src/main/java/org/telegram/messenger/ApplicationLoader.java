/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org/telegram/messenger/ApplicationLoader.java.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/messenger/ApplicationLoader.java
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
