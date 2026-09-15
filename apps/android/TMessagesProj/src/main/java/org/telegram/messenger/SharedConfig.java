/*
 * This file is part of NativeDo.
 *
 * Provides a compile-compatible shim for Telegram's SharedConfig so that
 * ported UI code can call SharedConfig.animationsEnabled() and
 * SharedConfig.shadowsInSections without pulling in Telegram's preference system.
 *
 * Copyright (C) 2026 NativeDo contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under terms of GNU General Public License version 2 or later as published by
 * the Free Software Foundation.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package org.telegram.messenger;

import android.content.SharedPreferences;

import java.io.File;

import org.telegram.ui.ActionBar.Theme;

/**
 * Minimal shim matching Telegram's {@code SharedConfig} API surface.
 * Returns safe defaults for animation and UI feature flags.
 */
public final class SharedConfig {

    private SharedConfig() {
    }

    /** Matches upstream {@code SharedConfig.animationsEnabled()}. */
    public static boolean animationsEnabled() {
        return !reduceAnimations;
    }

    /** Matches upstream {@code SharedConfig.shadowsInSections}. */
    public static boolean shadowsInSections = true;

    /** Matches upstream {@code SharedConfig.useThreeLinesLayout}. */
    public static boolean useThreeLinesLayout = true;

    /** Matches upstream default {@code SharedConfig.bubbleRadius}. */
    public static int bubbleRadius = 17;

    /** Matches upstream {@code SharedConfig.deviceIsLow}. */
    public static boolean deviceIsLow() {
        return false;
    }

    /** Matches upstream {@code SharedConfig.adaptableColorInBrowser}. */
    public static boolean adaptableColorInBrowser = false;
    public static boolean onlyLocalInstantView = false;
    public static int searchEngineType = 0;
    public static int fontSize = 16;
    public static int ivFontSize = 16;
    public static boolean fontSizeIsDefault = true;
    public static boolean reduceAnimations;

    public static boolean drawActionBarShadow = true;

    private static boolean browserConfigLoaded;

    public static void loadBrowserConfig() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        searchEngineType = preferences.getInt("searchEngineType", 0);
        adaptableColorInBrowser = preferences.getBoolean("adaptableBrowser", false);
        onlyLocalInstantView = preferences.getBoolean("onlyLocalInstantView", false);
        ivFontSize = preferences.getInt("iv_font_size", 16);
        int storedFontSize = preferences.contains("font_size")
                ? preferences.getInt("font_size", 16)
                : preferences.getInt("fons_size", 16);
        fontSize = Math.max(12, Math.min(30, storedFontSize));
        fontSizeIsDefault = !preferences.contains("font_size") && !preferences.contains("fons_size");
        reduceAnimations = preferences.getBoolean("reduce_animations", false);
        browserConfigLoaded = true;
    }

    private static SharedPreferences browserPreferences() {
        if (!browserConfigLoaded) {
            loadBrowserConfig();
        }
        return MessagesController.getGlobalMainSettings();
    }

    public static void toggleBrowserAdaptableColors() {
        adaptableColorInBrowser = !adaptableColorInBrowser;
        browserPreferences().edit().putBoolean("adaptableBrowser", adaptableColorInBrowser).apply();
    }

    public static void toggleLocalInstantView() {
        onlyLocalInstantView = !onlyLocalInstantView;
        browserPreferences().edit().putBoolean("onlyLocalInstantView", onlyLocalInstantView).apply();
    }

    public static void setSearchEngineType(int type) {
        searchEngineType = type;
        browserPreferences().edit().putInt("searchEngineType", searchEngineType).apply();
    }

    public static void setIvFontSize(int size) {
        ivFontSize = size;
        browserPreferences().edit().putInt("iv_font_size", ivFontSize).apply();
    }

    public static boolean setFontSize(int size) {
        int clamped = Math.max(12, Math.min(30, size));
        if (fontSize == clamped) {
            return false;
        }
        fontSize = clamped;
        fontSizeIsDefault = false;
        browserPreferences().edit()
                .putInt("font_size", fontSize)
                .remove("fons_size")
                .apply();
        Theme.createCommonMessageResources();
        return true;
    }

    public static void notifyFontSizeChanged() {
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.fontSizeChanged, fontSize);
    }

    public static void setReduceAnimations(boolean reduce) {
        reduceAnimations = reduce;
        browserPreferences().edit().putBoolean("reduce_animations", reduceAnimations).apply();
    }

    /** Matches upstream {@code SharedConfig.PERFORMANCE_CLASS_LOW / AVERAGE / HIGH}. */
    public static final int PERFORMANCE_CLASS_LOW = 0;
    public static final int PERFORMANCE_CLASS_AVERAGE = 1;
    public static final int PERFORMANCE_CLASS_HIGH = 2;

    /** Matches upstream {@code SharedConfig.getDevicePerformanceClass()}. */
    public static int getDevicePerformanceClass() {
        return PERFORMANCE_CLASS_HIGH;
    }

    public static boolean deviceIsHigh() {
        return getDevicePerformanceClass() == PERFORMANCE_CLASS_HIGH;
    }

    public static boolean deviceIsAboveAverage() {
        return getDevicePerformanceClass() >= PERFORMANCE_CLASS_AVERAGE;
    }

    public static boolean deviceIsAverage() {
        return getDevicePerformanceClass() == PERFORMANCE_CLASS_AVERAGE;
    }

    public static void checkSaveToGalleryFiles() {
    }

    /** Matches upstream {@code SharedConfig.storageCacheDir} — empty until NativeDo wires custom SD-card storage. */
    public static String storageCacheDir = "";
    public static int lastKeepMediaCheckTime = 0;
    /** Matches upstream {@code SharedConfig.lastLogsCheckTime}. */
    public static int lastLogsCheckTime = 0;

    /**
     * Matches upstream {@code SharedConfig.checkLogsToDelete()} — delete log files older than 10 days,
     * at most once per hour when {@link BuildVars#LOGS_ENABLED}.
     */
    public static void checkLogsToDelete() {
        if (!BuildVars.LOGS_ENABLED) {
            return;
        }
        int time = (int) (System.currentTimeMillis() / 1000);
        if (ApplicationLoader.applicationContext != null) {
            lastLogsCheckTime = MessagesController.getGlobalMainSettings().getInt("lastLogsCheckTime", lastLogsCheckTime);
        }
        if (Math.abs(time - lastLogsCheckTime) < 60 * 60) {
            return;
        }
        lastLogsCheckTime = time;
        Utilities.cacheClearQueue.postRunnable(() -> {
            long currentTime = time - 60 * 60 * 24 * 10;
            try {
                File dir = AndroidUtilities.getLogsDir();
                if (dir == null) {
                    return;
                }
                Utilities.clearDir(dir.getAbsolutePath(), 0, currentTime, false);
            } catch (Throwable e) {
                FileLog.e(e);
            }
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt("lastLogsCheckTime", lastLogsCheckTime);
            editor.apply();
        });
    }

    public static int saveToGalleryFlags = 0;
    public static final int SAVE_TO_GALLERY_FLAG_PEER = 1;
    public static final int SAVE_TO_GALLERY_FLAG_GROUP = 2;
    public static final int SAVE_TO_GALLERY_FLAG_CHANNELS = 4;

    public static boolean streamMkv = false;
    public static boolean streamMedia = true;
    public static boolean debugVideoQualities = false;
    public static boolean photoViewerBlur = true;
    public static boolean useThreeLinesLayoutChatList = false;
    public static boolean saveStreamMedia = true;
    public static int suggestStickers = 0;
    public static boolean loopStickers = true;
    public static boolean playOrderReversed = false;
    public static boolean noiseSupression = true;
    public static boolean noStatusBar = false;
    public static boolean forceRtmpStream = false;

    /** Matches upstream {@code SharedConfig.useNewBlur} — used by BaseCell caching. */
    public static boolean useNewBlur = true;
    public static int chatBubbles = 0;
    public static int distanceSystemType = 0;

    private static int lastLocalIdCounter = 0;
    public static synchronized int getLastLocalId() {
        return --lastLocalIdCounter;
    }

    public static void saveConfig() {
        MessagesController.getGlobalMainSettings().edit()
                .putInt("lastLocalId", lastLocalIdCounter)
                .putInt("font_size", fontSize)
                .putInt("iv_font_size", ivFontSize)
                .apply();
    }


    public static boolean chatBlurEnabled() {
        return true;
    }
}
