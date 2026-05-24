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

/**
 * Minimal shim matching Telegram's {@code SharedConfig} API surface.
 * Returns safe defaults for animation and UI feature flags.
 */
public final class SharedConfig {

    private SharedConfig() {
    }

    /** Matches upstream {@code SharedConfig.animationsEnabled()}. */
    public static boolean animationsEnabled() {
        return true;
    }

    /** Matches upstream {@code SharedConfig.shadowsInSections}. */
    public static boolean shadowsInSections = true;

    /** Matches upstream {@code SharedConfig.useThreeLinesLayout}. */
    public static boolean useThreeLinesLayout = true;

    /** Matches upstream {@code SharedConfig.deviceIsLow}. */
    public static boolean deviceIsLow() {
        return false;
    }

    /** Matches upstream {@code SharedConfig.adaptableColorInBrowser}. */
    public static boolean adaptableColorInBrowser = false;

    public static boolean drawActionBarShadow = true;

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
    public static int chatBubbles = 0;
    public static int distanceSystemType = 0;

    private static int lastLocalIdCounter = 0;
    public static synchronized int getLastLocalId() {
        return --lastLocalIdCounter;
    }

}
