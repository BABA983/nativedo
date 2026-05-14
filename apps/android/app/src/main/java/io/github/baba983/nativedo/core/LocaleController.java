// PORT-MODE: Native Feature
// UPSTREAM-SCOPE: org/telegram/messenger/LocaleController.java
// UPSTREAM-COMMIT: d2be1ad5966b4606c49010bcb6312fc8256bba35
// HOST-WIRING:
//   - None.
// ADAPTED-BACKEND:
//   - Replaces Telegram's full i18n system with direct Android res/strings lookups.
//   - isRTL is hard-coded to false; NativeDo does not support RTL layouts.
// OMITTED:
//   - Telegram's locale switching, plural rules, remote language packs,
//     LocaleInfo, and the full string key registry.
/*
 * This file is part of NativeDo.
 *
 * Provides a compile-compatible shim for Telegram's LocaleController so that
 * ported UI code can call LocaleController.isRTL and LocaleController.getString()
 * without pulling in Telegram's entire i18n pipeline.
 *
 * Copyright (C) 2026 NativeDo contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under terms of GNU General Public License version 2 or later as published by
 * the Free Software Foundation.
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.baba983.nativedo.core;

import android.content.Context;

/**
 * Minimal shim matching Telegram's {@code LocaleController} API surface.
 * Ported code calls {@code LocaleController.isRTL} and
 * {@code LocaleController.getString(key, resId)}; this class satisfies those
 * calls without the full Telegram i18n system.
 */
public final class LocaleController {

    /** NativeDo does not support RTL; always {@code false}. */
    public static boolean isRTL = false;

    private static Context applicationContext;

    private LocaleController() {
    }

    public static void init(Context context) {
        applicationContext = context.getApplicationContext();
    }

    /**
     * Returns the string for the given resource ID.
     * Matches upstream {@code LocaleController.getString(String key, int resId)}.
     */
    public static String getString(String key, int resId) {
        if (applicationContext == null) {
            return key;
        }
        try {
            return applicationContext.getString(resId);
        } catch (Exception e) {
            return key;
        }
    }

    /**
     * Returns the formatted string for the given resource ID.
     * Matches upstream {@code LocaleController.formatString(String key, int resId, Object... args)}.
     */
    public static String formatString(String key, int resId, Object... args) {
        if (applicationContext == null) {
            return key;
        }
        try {
            return applicationContext.getString(resId, args);
        } catch (Exception e) {
            return key;
        }
    }

    /**
     * Returns a quantity-correct string for the given resource ID.
     * Matches upstream {@code LocaleController.formatPluralString(String key, int quantity)}.
     */
    public static String formatPluralString(String key, int quantity) {
        if (applicationContext == null) {
            return key;
        }
        try {
            return applicationContext.getResources().getQuantityString(
                    applicationContext.getResources().getIdentifier(key, "plurals", applicationContext.getPackageName()),
                    quantity, quantity);
        } catch (Exception e) {
            return key;
        }
    }

    /**
     * Returns a quantity-correct formatted string.
     * Matches upstream {@code LocaleController.formatPluralString(String key, int quantity, Object... args)}.
     */
    public static String formatPluralString(String key, int quantity, Object... args) {
        if (applicationContext == null) {
            return key;
        }
        try {
            return applicationContext.getResources().getQuantityString(
                    applicationContext.getResources().getIdentifier(key, "plurals", applicationContext.getPackageName()),
                    quantity, args);
        } catch (Exception e) {
            return key;
        }
    }
}
