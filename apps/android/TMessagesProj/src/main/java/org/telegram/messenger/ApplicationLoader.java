/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.messenger;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.telephony.TelephonyManager;

import java.io.File;

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
    public static volatile boolean isScreenOn;

    /** {@code System.currentTimeMillis()} when the process started. */
    public static long startTime;

    private static volatile ConnectivityManager connectivityManager;
    private static volatile NetworkInfo currentNetworkInfo;
    private static long lastNetworkCheck;
    private static BroadcastReceiver screenReceiver;

    private static void ensureCurrentNetworkGet(boolean force) {
        if (force || currentNetworkInfo == null) {
            try {
                if (connectivityManager == null) {
                    connectivityManager = (ConnectivityManager) applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
                }
                currentNetworkInfo = connectivityManager.getActiveNetworkInfo();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    private static void ensureCurrentNetworkGet() {
        long now = System.currentTimeMillis();
        ensureCurrentNetworkGet(now - lastNetworkCheck > 5000);
        lastNetworkCheck = now;
    }

    public static boolean isNetworkOnline() {
        try {
            ensureCurrentNetworkGet();
            if (currentNetworkInfo == null) {
                return true;
            }
            if (currentNetworkInfo.isConnectedOrConnecting() || currentNetworkInfo.isAvailable()) {
                return true;
            }
            NetworkInfo netInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
            if (netInfo != null && netInfo.isConnectedOrConnecting()) {
                return true;
            } else {
                netInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                if (netInfo != null && netInfo.isConnectedOrConnecting()) {
                    return true;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
            return true;
        }
        return false;
    }

    public static boolean isConnectionSlow() {
        try {
            ensureCurrentNetworkGet();
            if (currentNetworkInfo != null && currentNetworkInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
                switch (currentNetworkInfo.getSubtype()) {
                    case TelephonyManager.NETWORK_TYPE_1xRTT:
                    case TelephonyManager.NETWORK_TYPE_CDMA:
                    case TelephonyManager.NETWORK_TYPE_EDGE:
                    case TelephonyManager.NETWORK_TYPE_GPRS:
                    case TelephonyManager.NETWORK_TYPE_IDEN:
                        return true;
                }
            }
        } catch (Throwable ignore) {
        }
        return false;
    }

    /** Matches upstream StatsController network type constants. */
    private static final int NETWORK_TYPE_MOBILE = 0;
    private static final int NETWORK_TYPE_WIFI = 1;
    private static final int NETWORK_TYPE_ROAMING = 2;

    public static int getCurrentNetworkType() {
        if (isConnectedOrConnectingToWiFi()) {
            return NETWORK_TYPE_WIFI;
        } else if (isRoaming()) {
            return NETWORK_TYPE_ROAMING;
        } else {
            return NETWORK_TYPE_MOBILE;
        }
    }

    public static boolean isRoaming() {
        try {
            ensureCurrentNetworkGet();
            return currentNetworkInfo != null && currentNetworkInfo.isRoaming();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    private static boolean isConnectedOrConnectingToWiFi() {
        try {
            ensureCurrentNetworkGet();
            if (currentNetworkInfo != null && currentNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                return currentNetworkInfo.isConnectedOrConnecting();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
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

    public static File getFilesDirFixed() {
        if (applicationContext != null) {
            return applicationContext.getFilesDir();
        }
        return new File(System.getProperty("java.io.tmpdir", "/tmp"));
    }

    public static File getCacheDir() {
        if (applicationContext != null) {
            return applicationContext.getCacheDir();
        }
        return new File(System.getProperty("java.io.tmpdir", "/tmp"));
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
        PowerManager powerManager = (PowerManager) applicationContext.getSystemService(Context.POWER_SERVICE);
        isScreenOn = powerManager == null || powerManager.isInteractive();
        if (screenReceiver == null) {
            screenReceiver = new ScreenReceiver();
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            try {
                applicationContext.registerReceiver(screenReceiver, filter);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        FileLog.d("ApplicationLoader.init packageName=" + applicationContext.getPackageName()
                + " networkOnline=" + isNetworkOnline()
                + " roaming=" + isRoaming()
                + " networkType=" + getCurrentNetworkType());
    }

    /**
     * Matches upstream {@code LaunchActivity.checkFreeDiscSpace(int)} hooks used for log retention
     * ({@link SharedConfig#checkLogsToDelete()}) and cache housekeeping ({@link AutoDeleteMediaTask#run()}).
     */
    public static void checkFreeDiscSpace(final int force) {
        AutoDeleteMediaTask.run();
        SharedConfig.checkLogsToDelete();
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
