// PORT-MODE: Native Feature
// UPSTREAM-SCOPE: org/telegram/messenger/SharedConfig.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// HOST-WIRING:
//   - None.
// ADAPTED-BACKEND:
//   - All methods return safe defaults; NativeDo does not persist Telegram preferences.
// OMITTED:
//   - Telegram's full preference system, proxy config, passcode, etc.
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
package io.github.baba983.nativedo.telegram.messenger;

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

}
